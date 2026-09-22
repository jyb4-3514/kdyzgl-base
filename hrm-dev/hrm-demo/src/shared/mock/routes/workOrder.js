import {
  db,
  employeeName,
  findDispatchRuleById,
  findEmployeeById,
  findStationById,
  findWorkOrderById,
  listWorkOrderTransfers,
  parseHandleLog,
  pushNotification,
  pushWorkOrderTransfer,
  stationName
} from '../db.js'
import { CODE, DEMO_CODE, STATION_CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { fail, formatDate, formatDateTime, ok, paginate, parseTime } from '../util.js'
import { WORK_ORDER_SLA_HOURS } from '../../constants/dict.js'
import { isBlank, pageSizeInvalid, textLen } from '../validate.js'

/**
 * 工单接口（T08，demo-design.md 7.4.5 契约草案）
 * 状态机：待处理(0)→处理中(1)→已解决(2)→已关闭(3)；待处理可直关；已解决可驳回/重开(2→1)。
 * 权限：ADMIN 全量、STATION_ADMIN 限本驿站、STAFF 仅限本人为处理人的工单（越权返回 8002）。
 *
 * 转单（T19）：契约原文写作 POST /workorders/{id}/transfer，本工程既有 5 条工单路由统一用 /work-orders，
 * 为避免同一资源出现两套命名（也避免已交付的 PC / 移动端 api 封装分裂），转单沿用 /work-orders/{id}/transfer。
 */

const TRANSITIONS = { 0: [1, 3], 1: [2], 2: [1, 3], 3: [] }

/**
 * 超时未处理（需求6）：SLA 截止已过 且 工单仍未处理完（待处理 0 / 处理中 1）。
 * 已解决 / 已关闭不再算超时——终态工单继续计入会把「历史积压」误报成「待办超时」。
 * 口径与旧字段 overSla 完全一致，本轮只是把语义写进字段名并对外提供同义筛选参数。
 */
const isOverdueUnhandled = (order, now = Date.now()) =>
  (order.status === 0 || order.status === 1) && now > parseTime(order.sla_deadline)

function toWorkOrderVO(order) {
  const overdue = isOverdueUnhandled(order)
  return {
    id: order.id,
    orderNo: order.order_no,
    type: order.type,
    status: order.status,
    priority: order.priority,
    title: order.title,
    content: order.content,
    // 来源：MANUAL 手工新建 / AUTO_WECHAT 企微群消息自动派发（需求3）
    source: order.source || 'MANUAL',
    stationId: order.station_id,
    stationName: stationName(order.station_id),
    parcelId: order.parcel_id,
    waybillNo: order.waybill_no,
    reporterId: order.reporter_id,
    reporterName: employeeName(order.reporter_id),
    assigneeId: order.assignee_id,
    assigneeName: employeeName(order.assignee_id),
    slaDeadline: order.sla_deadline,
    overdueUnhandled: overdue,
    // 兼容旧页面的别名（与 overdueUnhandled 同值）
    // TODO(扩展): PC / 移动端工单页统一改用 overdueUnhandled 后删除 overSla
    overSla: overdue,
    resolvedTime: order.resolved_time,
    closedTime: order.closed_time,
    handleLog: parseHandleLog(order.handle_log),
    createTime: order.create_time,
    updateTime: order.update_time
  }
}

/** 详情 VO：在列表 VO 之上补转单留痕（列表不带，免得 120 条列表平白多一份嵌套数据） */
function toWorkOrderDetailVO(order) {
  // TODO(扩展): 与请假详情对齐，由服务端补下发派生操作标志（如 canAccept/canResolve/canClose/canAssign/canTransfer），
  //   口径需按「状态机 × 角色」逐条定稿并同步前端；当前前端按状态+角色本地判定，不在 Mock 单方面臆造标志
  return { ...toWorkOrderVO(order), transfers: listWorkOrderTransfers(order.id) }
}

/** 流转权限：ADMIN / 本驿站站长 / 本人为处理人 */
const canManage = (user, order) => {
  if (user.role === 'ADMIN') return true
  if (user.role === 'STATION_ADMIN') return order.station_id === user.station_id
  return order.assignee_id === user.id
}

/** 指派权限：仅 ADMIN / 本驿站站长（普通员工不可指派） */
const canAssign = (user, order) => {
  if (user.role === 'ADMIN') return true
  return user.role === 'STATION_ADMIN' && order.station_id === user.station_id
}

function list({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  let rows = db.workOrders.filter((o) => o.is_deleted === 0)
  // stationId 已在 engine 按角色收敛（非 ADMIN 强制本站），这里只做空值归一
  const stationId = params.stationId
  if (stationId != null && stationId !== '') rows = rows.filter((o) => o.station_id === Number(stationId))
  if (params.status !== undefined && params.status !== '') rows = rows.filter((o) => o.status === Number(params.status))
  if (params.type !== undefined && params.type !== '') rows = rows.filter((o) => o.type === Number(params.type))
  if (params.priority !== undefined && params.priority !== '')
    rows = rows.filter((o) => o.priority === Number(params.priority))
  if (params.assigneeId !== undefined && params.assigneeId !== '')
    rows = rows.filter((o) => o.assignee_id === Number(params.assigneeId))
  const keyword = String(params.keyword || '').trim()
  if (keyword) rows = rows.filter((o) => o.order_no.includes(keyword) || o.title.includes(keyword))

  rows = rows.slice().sort((a, b) => (a.create_time < b.create_time ? 1 : -1))
  let list = rows.map(toWorkOrderVO)
  // 超时未处理筛选（需求6）：新参数为准，overSla 作为旧参数别名继续可用
  const overdueFlag =
    params.overdueUnhandled !== undefined && params.overdueUnhandled !== '' ? params.overdueUnhandled : params.overSla
  if (overdueFlag === '1' || overdueFlag === 'true') list = list.filter((o) => o.overdueUnhandled)
  return ok(paginate(list, params.pageNum, params.pageSize))
}

function detail({ pathParams, user }) {
  const order = findWorkOrderById(pathParams.id)
  if (!order) return fail(CODE.NOT_FOUND, '工单不存在')
  if (user.role !== 'ADMIN' && order.station_id !== user.station_id) return fail(CODE.NOT_FOUND, '工单不存在')
  return ok(toWorkOrderDetailVO(order))
}

/** 新建工单（需求3）：手工新建，source 固定 MANUAL；描述兼容 content / description，运单号兼容 waybillNo / relatedWaybillNo */
function create({ body, user }) {
  if (![1, 2, 3, 4].includes(Number(body.type))) return fail(CODE.BAD_REQUEST, '工单类型非法')
  if (![0, 1, 2].includes(Number(body.priority))) return fail(CODE.BAD_REQUEST, '优先级非法')
  if (!textLen(body.title, 1, 100)) return fail(CODE.BAD_REQUEST, '标题长度须为 1-100 字符')
  const content = body.content !== undefined ? body.content : body.description
  if (content != null && !textLen(content, 0, 500)) return fail(CODE.BAD_REQUEST, '描述长度不可超过 500 字符')

  const stationId = user.role === 'ADMIN' ? (isBlank(body.stationId) ? 1 : Number(body.stationId)) : user.station_id
  let assignee = null
  if (!isBlank(body.assigneeId)) {
    assignee = findEmployeeById(body.assigneeId)
    if (!assignee || assignee.status !== 1) return fail(CODE.BAD_REQUEST, '被指派人不存在或已停用')
    // 非 ADMIN 只能指派本站员工，与转单的 8004 同口径，避免跨站指派
    if (user.role !== 'ADMIN' && assignee.station_id !== stationId)
      return fail(DEMO_CODE.WORK_ORDER_TRANSFER_TARGET_INVALID)
  }

  const id = (db.seq.workOrder += 1)
  const now = new Date()
  const priority = Number(body.priority)
  const handleLog = [{ time: formatDateTime(now), action: 'create', operatorName: user.real_name, content: '创建工单' }]
  if (assignee)
    handleLog.push({
      time: formatDateTime(now),
      action: 'assign',
      operatorName: user.real_name,
      content: `指派给 ${assignee.real_name}`
    })

  const order = {
    id,
    order_no: `WO-${formatDate(now).replace(/-/g, '')}-${String(id).padStart(4, '0')}`,
    type: Number(body.type),
    status: 0,
    priority,
    title: String(body.title).trim(),
    content: content || '',
    station_id: stationId,
    parcel_id: body.parcelId || null,
    waybill_no: body.relatedWaybillNo || body.waybillNo || null,
    reporter_id: user.id,
    assignee_id: assignee ? assignee.id : null,
    source: 'MANUAL',
    sla_deadline: formatDateTime(new Date(now.getTime() + WORK_ORDER_SLA_HOURS[priority] * 3600000)),
    resolved_time: null,
    closed_time: null,
    handle_log: JSON.stringify(handleLog),
    is_deleted: 0,
    create_time: formatDateTime(now),
    update_time: formatDateTime(now)
  }
  db.workOrders.push(order)
  // 建单即指派时给被指派人一条未读通知，与 PUT /work-orders/:id/assign 的联动口径一致
  if (assignee) {
    pushNotification({
      employeeId: assignee.id,
      type: 1,
      title: '工单指派',
      content: `您被指派处理工单 ${order.order_no}：${order.title}`,
      bizType: 'work_order',
      bizId: order.id
    })
  }
  return ok({ id, orderNo: order.order_no, source: order.source })
}

function assign({ body, pathParams, user }) {
  const order = findWorkOrderById(pathParams.id)
  if (!order) return fail(CODE.NOT_FOUND, '工单不存在')
  if (!canAssign(user, order)) return fail(DEMO_CODE.WORK_ORDER_NO_PERMISSION) // 8002
  const assignee = findEmployeeById(body.assigneeId)
  if (!assignee) return fail(CODE.BAD_REQUEST, '被指派人不存在')

  order.assignee_id = assignee.id
  order.update_time = formatDateTime(new Date())
  const log = parseHandleLog(order.handle_log)
  log.push({
    time: formatDateTime(new Date()),
    action: 'assign',
    operatorName: user.real_name,
    content: `指派给 ${assignee.real_name}`
  })
  order.handle_log = JSON.stringify(log)

  // 指派联动通知（T09：被指派人未读 +1 且可跳转 work_order）
  pushNotification({
    employeeId: assignee.id,
    type: 1,
    title: '工单指派',
    content: `您被指派处理工单 ${order.order_no}：${order.title}`,
    bizType: 'work_order',
    bizId: order.id
  })
  return ok(toWorkOrderVO(order))
}

function changeStatus({ body, pathParams, user }) {
  const order = findWorkOrderById(pathParams.id)
  if (!order) return fail(CODE.NOT_FOUND, '工单不存在')
  if (!canManage(user, order)) return fail(DEMO_CODE.WORK_ORDER_NO_PERMISSION) // 8002
  const target = Number(body.status)
  if (!(TRANSITIONS[order.status] || []).includes(target)) return fail(DEMO_CODE.WORK_ORDER_STATUS_INVALID) // 8001

  const now = new Date()
  order.status = target
  if (target === 2) order.resolved_time = formatDateTime(now)
  if (target === 3) order.closed_time = formatDateTime(now)
  order.update_time = formatDateTime(now)

  const action = target === 3 ? 'close' : target === 2 ? 'resolve' : target === 1 ? 'accept' : 'reopen'
  const log = parseHandleLog(order.handle_log)
  log.push({ time: formatDateTime(now), action, operatorName: user.real_name, content: body.remark || '' })
  order.handle_log = JSON.stringify(log)

  // 解决时通知上报人（T09 二次联动）
  if (target === 2) {
    pushNotification({
      employeeId: order.reporter_id,
      type: 2,
      title: '工单流转',
      content: `您的工单 ${order.order_no} 已解决`,
      bizType: 'work_order',
      bizId: order.id
    })
  }
  return ok(toWorkOrderVO(order))
}

/**
 * 转单（T19）：只改处理人、不改状态；留痕 / 时间线 / 通知三件事一并落地，
 * 保证详情页的「转单时间线」与工单当前处理人始终自洽。
 * 权限口径与流转一致（ADMIN / 本站站长 / 当前处理人），只是错误码区分开（8003）；
 * 对象必须是在职可接单员工，且站长与处理人只能转本站（8004）。
 */
function transfer({ body, pathParams, user }) {
  const order = findWorkOrderById(pathParams.id)
  if (!order) return fail(CODE.NOT_FOUND, '工单不存在')
  if (!canManage(user, order)) return fail(DEMO_CODE.WORK_ORDER_TRANSFER_NO_PERMISSION) // 8003
  if (!textLen(body.reason, 2, 100)) return fail(CODE.BAD_REQUEST, '转单理由长度须为 2-100 字')

  const target = findEmployeeById(body.toEmployeeId)
  // 「不能转给自己」按操作人判定：转给自己等于原地打转，留痕失去意义
  if (!target || target.status !== 1 || target.id === user.id) return fail(DEMO_CODE.WORK_ORDER_TRANSFER_TARGET_INVALID) // 8004
  // 只有老板可跨站调人；站长与处理人转单只能在本站内消化
  if (user.role !== 'ADMIN' && target.station_id !== order.station_id)
    return fail(DEMO_CODE.WORK_ORDER_TRANSFER_TARGET_INVALID)

  const now = formatDateTime(new Date())
  const reason = String(body.reason).trim()
  const fromId = order.assignee_id
  order.assignee_id = target.id
  order.update_time = now

  pushWorkOrderTransfer({
    workOrderId: order.id,
    fromEmployeeId: fromId,
    fromEmployeeName: employeeName(fromId),
    toEmployeeId: target.id,
    toEmployeeName: target.real_name,
    reason,
    operatorId: user.id,
    operatorName: user.real_name,
    transferTime: now
  })

  const log = parseHandleLog(order.handle_log)
  log.push({
    time: now,
    action: 'transfer',
    operatorName: user.real_name,
    content: `转单给 ${target.real_name}：${reason}`
  })
  order.handle_log = JSON.stringify(log)

  // 转单后新处理人要有感知，复用 T09 的通知链路（bizType 指向工单，可直接跳详情）
  pushNotification({
    employeeId: target.id,
    type: 1,
    title: '工单转单',
    content: `工单 ${order.order_no} 已转由您处理：${order.title}`,
    bizType: 'work_order',
    bizId: order.id
  })
  return ok(toWorkOrderDetailVO(order))
}

/* ==================== 企业微信采集群自动派单（需求3 预留契约） ==================== */

/** 未命中任何规则时的兜底工单类型 / 优先级：其他 + 中，保证群消息不会因为规则没配全而丢单 */
const AUTO_DISPATCH_DEFAULT = { workOrderType: 4, priority: 1 }

function dispatchRuleVO(rule) {
  return {
    id: rule.id,
    keyword: rule.keyword,
    workOrderType: rule.work_order_type,
    priority: rule.priority,
    defaultAssigneeId: rule.default_assignee_id,
    enabled: rule.enabled === 1,
    updateTime: rule.update_time
  }
}

function dispatchRuleList() {
  return ok(db.dispatchRules.map(dispatchRuleVO))
}

/** 规则维护（ADMIN）：关键词 / 类型 / 优先级可改，默认处理人只接受在职员工 */
function updateDispatchRule({ body, pathParams }) {
  const rule = findDispatchRuleById(pathParams.id)
  if (!rule) return fail(DEMO_CODE.WORK_ORDER_DISPATCH_RULE_NOT_EXISTS) // 8005
  if (body.keyword !== undefined && !textLen(body.keyword, 1, 20)) return fail(CODE.BAD_REQUEST, '关键词长度须为 1-20')
  if (body.workOrderType !== undefined && ![1, 2, 3, 4].includes(Number(body.workOrderType)))
    return fail(CODE.BAD_REQUEST, '工单类型非法')
  if (body.priority !== undefined && ![0, 1, 2].includes(Number(body.priority)))
    return fail(CODE.BAD_REQUEST, '优先级非法')
  if (body.enabled !== undefined && ![0, 1, true, false].includes(body.enabled))
    return fail(CODE.BAD_REQUEST, 'enabled 仅支持 0 / 1')

  if (body.defaultAssigneeId !== undefined) {
    if (isBlank(body.defaultAssigneeId)) {
      rule.default_assignee_id = null
    } else {
      const employee = findEmployeeById(body.defaultAssigneeId)
      if (!employee || employee.status !== 1) return fail(CODE.BAD_REQUEST, '默认处理人不存在或已停用')
      rule.default_assignee_id = employee.id
    }
  }
  if (body.keyword !== undefined) rule.keyword = String(body.keyword).trim()
  if (body.workOrderType !== undefined) rule.work_order_type = Number(body.workOrderType)
  if (body.priority !== undefined) rule.priority = Number(body.priority)
  if (body.enabled !== undefined) rule.enabled = Number(body.enabled)
  rule.update_time = formatDateTime(new Date())
  return ok(dispatchRuleVO(rule))
}

/** 群消息原文过长时截断成标题，避免标题列被整段聊天记录撑爆 */
const titleFromContent = (content) => (content.length > 40 ? `${content.slice(0, 40)}…` : content)

/**
 * 接收企业微信群消息记录并自动派发工单（预留接口）
 *
 * 面向后期企业微信对接：真实回调由企微服务器发起，携带签名而非本系统 JWT，
 * 故本路由 auth=false（不校验登录态），由接入层完成回调校验后再调用业务逻辑。
 * TODO(扩展): 接入企业微信机器人回调时替换为真实签名校验与消息解密
 *   —— 校验 msg_signature / timestamp / nonce，用 EncodingAESKey 解密 Encrypt 字段后再解析消息体；
 *   本接口当前只接受已解析的明文 { groupName, senderName, content, sendTime, stationId }。
 *
 * 派发规则：按 dispatch-rules 顺序取首个「启用且关键词命中」的规则决定类型与优先级；未命中用默认值。
 */
function autoDispatch({ body }) {
  const content = isBlank(body.content) ? '' : String(body.content).trim()
  if (!content) return fail(DEMO_CODE.WORK_ORDER_GROUP_MSG_INVALID) // 8006
  const stationId = isBlank(body.stationId) ? 1 : Number(body.stationId)
  if (!findStationById(stationId)) return fail(STATION_CODE.NOT_EXISTS)

  const rule = db.dispatchRules.find((r) => r.enabled === 1 && content.includes(r.keyword))
  const type = rule ? rule.work_order_type : AUTO_DISPATCH_DEFAULT.workOrderType
  const priority = rule ? rule.priority : AUTO_DISPATCH_DEFAULT.priority
  const assigneeId = rule && rule.default_assignee_id != null ? rule.default_assignee_id : null
  const groupName = isBlank(body.groupName) ? null : String(body.groupName).trim()
  const senderName = isBlank(body.senderName) ? null : String(body.senderName).trim()
  // 以消息发送时间作为建单基准：回调延迟到账时，SLA 倒计时仍按消息真实时间起算
  const sendTs = parseTime(body.sendTime)
  const now = Number.isFinite(sendTs) ? new Date(sendTs) : new Date()

  const id = (db.seq.workOrder += 1)
  const order = {
    id,
    order_no: `WO-${formatDate(now).replace(/-/g, '')}-${String(id).padStart(4, '0')}`,
    type,
    status: 0,
    priority,
    title: `${rule ? rule.keyword : '群消息'}：${titleFromContent(content)}`,
    content,
    station_id: stationId,
    parcel_id: null,
    waybill_no: null,
    // 群消息发送人不在系统内，不伪造上报人；来源信息完整写入时间线
    reporter_id: null,
    assignee_id: assigneeId,
    source: 'AUTO_WECHAT',
    sla_deadline: formatDateTime(new Date(now.getTime() + WORK_ORDER_SLA_HOURS[priority] * 3600000)),
    resolved_time: null,
    closed_time: null,
    handle_log: JSON.stringify([
      {
        time: formatDateTime(now),
        action: 'create',
        operatorName: '企业微信采集',
        content: `自动派发（命中规则：${rule ? rule.keyword : '无，使用默认类型与优先级'}）`
      },
      {
        time: formatDateTime(now),
        action: 'auto_dispatch',
        operatorName: '企业微信采集',
        content: `来源群：${groupName || '-'}；发送人：${senderName || '-'}；原始消息：${content}`
      }
    ]),
    is_deleted: 0,
    create_time: formatDateTime(now),
    update_time: formatDateTime(now)
  }
  db.workOrders.push(order)
  if (assigneeId) {
    pushNotification({
      employeeId: assigneeId,
      type: 1,
      title: '工单指派',
      content: `群消息自动派发工单 ${order.order_no}：${order.title}`,
      bizType: 'work_order',
      bizId: order.id
    })
  }
  return ok(toWorkOrderDetailVO(order))
}

/**
 * 路由注册顺序：engine 取首个命中，静态路径必须排在 /work-orders/:id 之前，
 * 否则 GET /work-orders/dispatch-rules 会被 :id 抢先匹配成「工单不存在」。
 */
export const workOrderRoutes = [
  { method: 'get', path: '/work-orders', roles: ALL_ROLES, handler: list },
  { method: 'post', path: '/work-orders', roles: ALL_ROLES, handler: create },
  // 规则读写口径统一为 ADMIN：规则含关键词 / 类型 / 优先级 / 默认处理人，属「企微接入配置」，
  // 站长与员工既不需要看也改不了；原先只读放行会让越权者拿到规则全貌（U6 已修正）
  { method: 'get', path: '/work-orders/dispatch-rules', roles: ['ADMIN'], handler: dispatchRuleList },
  { method: 'put', path: '/work-orders/dispatch-rules/:id', roles: ['ADMIN'], handler: updateDispatchRule },
  { method: 'post', path: '/work-orders/auto-dispatch', auth: false, handler: autoDispatch },
  { method: 'get', path: '/work-orders/:id', roles: ALL_ROLES, handler: detail },
  { method: 'put', path: '/work-orders/:id/assign', roles: ALL_ROLES, handler: assign },
  { method: 'put', path: '/work-orders/:id/status', roles: ALL_ROLES, handler: changeStatus },
  { method: 'post', path: '/work-orders/:id/transfer', roles: ALL_ROLES, handler: transfer }
]
