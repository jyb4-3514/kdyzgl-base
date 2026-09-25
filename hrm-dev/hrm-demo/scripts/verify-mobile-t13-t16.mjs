/**
 * T13–T16 移动端页面数据链路实测（`npm run verify:mobile`）
 * 为什么单独一个脚本而不并进 verify:mock：verify:mock 校验的是 Mock 契约本身，
 * 本脚本按「各页面实际发出的请求参数」逐个调用 Mock 层，验证管理端 / 员工端的数据口径与交互结果，
 * 两者失败时的定位方向不同（契约错 vs 页面传参错），故分开放。
 */
import axios from 'axios'
import { createMockAdapter } from '../src/shared/mock/engine.js'
import { db, resetDb } from '../src/shared/mock/db.js'
import { queryParcels, resetParcelStore } from '../src/shared/mock/parcelStore.js'
import { overlay } from '../src/shared/mock/overlay.js'

const service = axios.create({ baseURL: '/api/v1' })
service.defaults.adapter = createMockAdapter({ delay: false })
service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      const err = new Error(body.message)
      err.code = body.code
      return Promise.reject(err)
    }
    return body
  },
  (error) => {
    const body = (error.response && error.response.data) || {}
    const err = new Error(body.message || error.message)
    err.code = body.code || (error.response && error.response.status)
    return Promise.reject(err)
  }
)

let pass = 0
let fail = 0
const failures = []
function check(name, condition, extra = '') {
  if (condition) pass += 1
  else {
    fail += 1
    failures.push(`${name}${extra ? ` → ${extra}` : ''}`)
  }
}
async function call(method, url, { data, params, token } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  try {
    return { ok: true, data: await service.request({ method, url, data, params, headers }) }
  } catch (error) {
    return { ok: false, code: error.code, message: error.message }
  }
}
/**
 * 登录助手：端准入 fail-closed 后，登录必须上报端类型，故按账号角色补齐
 * （ADMIN → 网页端 WEB；站长 / 员工 → 员工端 H5，与页面实际入口一致），否则一律 1110。
 */
const login = async (username) => {
  const employee = db.employees.find((e) => e.is_deleted === 0 && e.username === username)
  const endFields = employee && employee.role === 'ADMIN' ? { clientType: 'WEB' } : { clientType: 'H5', as: 'station' }
  const res = await call('post', '/auth/login', { data: { username, password: 'demo1234', ...endFields } })
  return res.ok ? res.data.token : null
}
const pad = (n) => String(n).padStart(2, '0')
const hoursAgo = (h) => {
  const d = new Date(Date.now() - h * 3600000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/* ==================== 剧本数据（T16 验收口径） ==================== */
const overdue = queryParcels({ stationId: null, status: 1, endTime: hoursAgo(48), pageNum: 1, pageSize: 100 })
const demoPick = queryParcels({ stationId: 1, status: 1, pageNum: 1, pageSize: 1 })
const failedSync = db.syncTasks.filter((t) => t.status === 3)
const overSlaSeed = db.workOrders.filter(
  (o) =>
    o.is_deleted === 0 &&
    (o.status === 0 || o.status === 1) &&
    new Date(o.sla_deadline.replace(' ', 'T')).getTime() < Date.now()
)
check('剧本·失败同步任务 ≥1', failedSync.length >= 1, `${failedSync.length} 条`)
check(
  '剧本·城东驿站存在失败批次（S5 演示点）',
  failedSync.some((t) => t.station_id === 1)
)
check('剧本·超 SLA 工单 6 条', overSlaSeed.length === 6, `${overSlaSeed.length} 条`)
check('剧本·超 48h 未取件 ≥10', overdue.total >= 10, `${overdue.total} 件`)
check(
  '剧本·指定演示运单号（城东驿站 + 在库待取）',
  !!demoPick.list[0] && demoPick.list[0].stationId === 1 && demoPick.list[0].status === 1,
  demoPick.list[0] && demoPick.list[0].waybillNo
)

/* ==================== 管理端（ADMIN） ==================== */
const adminToken = await login('admin')
check('管理端·登录', !!adminToken)

const globalSummary = await call('get', '/parcels/summary', { token: adminToken })
check(
  '管理端·全局包裹指标',
  globalSummary.ok && globalSummary.data.parcelTotal > 100000,
  globalSummary.ok ? JSON.stringify(globalSummary.data) : globalSummary.message
)

const trend7 = await call('get', '/parcels/trend', { token: adminToken, params: { days: 7 } })
const trend30 = await call('get', '/parcels/trend', { token: adminToken, params: { days: 30 } })
check('趋势·7 天返回 7 个点', trend7.ok && trend7.data.length === 7, `${trend7.ok && trend7.data.length}`)
check('趋势·30 天返回 30 个点', trend30.ok && trend30.data.length === 30, `${trend30.ok && trend30.data.length}`)
check(
  '趋势·点位含入库与取件字段',
  trend7.ok && trend7.data.every((p) => p.date && Number.isFinite(p.inbound) && Number.isFinite(p.pickup)),
  trend7.ok && trend7.data[0] && JSON.stringify(trend7.data[0])
)

const rankDefault = await call('get', '/parcels/ranking', { token: adminToken })
const rankPickup = await call('get', '/parcels/ranking', { token: adminToken, params: { sort: 'pickupRate' } })
const rankAbnormal = await call('get', '/parcels/ranking', { token: adminToken, params: { sort: 'abnormalRate' } })
const desc = (list, key) => list.every((item, i) => i === 0 || list[i - 1][key] >= item[key])
check(
  '排行·默认按包裹量降序（8 个驿站）',
  rankDefault.ok && rankDefault.data.length === 8 && desc(rankDefault.data, 'parcelTotal')
)
check(
  '排行·按取件率重排生效',
  rankPickup.ok &&
    desc(rankPickup.data, 'pickupRate') &&
    rankPickup.data[0].stationId !== rankDefault.data[0].stationId,
  rankPickup.ok ? rankPickup.data[0].stationName : rankPickup.message
)
check('排行·按异常率重排生效', rankAbnormal.ok && desc(rankAbnormal.data, 'abnormalRate'))

const slaPage = await call('get', '/work-orders', {
  token: adminToken,
  params: { overSla: '1', pageNum: 1, pageSize: 1 }
})
check(
  '预警·超 SLA 工单总数 6',
  slaPage.ok && slaPage.data.total === 6,
  slaPage.ok ? `${slaPage.data.total}` : slaPage.message
)
const failedPage = await call('get', '/sync-tasks', {
  token: adminToken,
  params: { status: 3, pageNum: 1, pageSize: 100 }
})
check(
  '预警·同步失败批次可查且带驿站名',
  failedPage.ok && failedPage.data.list.length >= 1 && !!failedPage.data.list[0].stationName,
  failedPage.ok ? `${failedPage.data.total} 条` : failedPage.message
)
const alertOverdue = await call('get', '/parcels', {
  token: adminToken,
  params: { status: 1, endTime: hoursAgo(48), pageNum: 1, pageSize: 100 }
})
check(
  '预警·超 48h 未取件接口可下钻（有 id 可跳详情）',
  alertOverdue.ok && alertOverdue.data.list.length === 100 && !!alertOverdue.data.list[0].id
)

/* ==================== 员工端（STATION_ADMIN） ==================== */
const stationToken = await login('st001_admin')
check('员工端·站长登录且归属城东驿站', !!stationToken)

const stationSummary = await call('get', '/parcels/summary', { token: stationToken })
check(
  '员工端·指标收敛为本站（小于全局）',
  stationSummary.ok &&
    stationSummary.data.parcelTotal > 0 &&
    stationSummary.data.parcelTotal < globalSummary.data.parcelTotal,
  stationSummary.ok
    ? `${stationSummary.data.parcelTotal} / 全局 ${globalSummary.data.parcelTotal}`
    : stationSummary.message
)

const stationParcels = await call('get', '/parcels', {
  token: stationToken,
  params: { status: 1, pageNum: 1, pageSize: 20 }
})
check(
  '员工端·包裹列表限定本站',
  stationParcels.ok &&
    stationParcels.data.list.length === 20 &&
    stationParcels.data.list.every((p) => p.stationId === 1)
)
const crossStation = await call('get', '/parcels', {
  token: stationToken,
  params: { stationId: 5, pageNum: 1, pageSize: 20 }
})
check(
  '员工端·传其它驿站 stationId 被强制覆盖',
  crossStation.ok && crossStation.data.list.every((p) => p.stationId === 1)
)
const searchParcel = await call('get', '/parcels', {
  token: stationToken,
  params: { waybillNo: demoPick.list[0].waybillNo, pageNum: 1, pageSize: 1 }
})
check(
  '员工端·运单号精确搜索命中 1 条',
  searchParcel.ok && searchParcel.data.total === 1,
  searchParcel.ok ? `${searchParcel.data.total}` : searchParcel.message
)
const searchMiss = await call('get', '/parcels', {
  token: stationToken,
  params: { waybillNo: 'SF0000000000', pageNum: 1, pageSize: 1 }
})
check('员工端·不存在的运单号返回 0 条（页面提示）', searchMiss.ok && searchMiss.data.total === 0)

const stationRank = await call('get', '/parcels/ranking', { token: stationToken })
check(
  '员工端·排行收敛为 1 个驿站',
  stationRank.ok && stationRank.data.length === 1 && stationRank.data[0].stationId === 1
)
const stationOrders = await call('get', '/work-orders', { token: stationToken, params: { pageNum: 1, pageSize: 20 } })
check(
  '员工端·工单限定本站',
  stationOrders.ok && stationOrders.data.list.every((o) => o.stationId === 1),
  stationOrders.ok ? `total=${stationOrders.data.total}` : stationOrders.message
)
const stationSync = await call('get', '/sync-tasks', { token: stationToken, params: { pageNum: 1, pageSize: 30 } })
check(
  '员工端·同步批次限定本站',
  stationSync.ok && stationSync.data.list.every((t) => t.stationId === 1),
  stationSync.ok ? `total=${stationSync.data.total}` : stationSync.message
)
const syncLogs = await call('get', `/sync-tasks/${stationSync.data.list[0].id}/logs`, { token: stationToken })
check(
  '员工端·批次日志可查（只读弹层）',
  syncLogs.ok && syncLogs.data.length >= 3,
  syncLogs.ok ? `${syncLogs.data.length} 条` : syncLogs.message
)

/* ==================== 取件核销闭环 ==================== */
const demoParcel = demoPick.list[0]
const beforeSummary = stationSummary.data
const pickResult = await call('put', `/parcels/${demoParcel.id}/pickup`, { token: stationToken })
check(
  '取件核销·成功并返回已取件包裹',
  pickResult.ok && pickResult.data.status === 2 && !!pickResult.data.pickupTime,
  pickResult.ok ? `${pickResult.data.waybillNo} ${pickResult.data.pickupTime}` : pickResult.message
)
check(
  '取件核销·取件员工留痕（st001_admin id=3）',
  pickResult.ok && pickResult.data.pickupEmployeeId === 3,
  pickResult.ok ? `${pickResult.data.pickupEmployeeId}` : ''
)
const afterSummary = (await call('get', '/parcels/summary', { token: stationToken })).data
check(
  '取件核销·今日取件 +1',
  afterSummary.todayPickup === beforeSummary.todayPickup + 1,
  `${beforeSummary.todayPickup} → ${afterSummary.todayPickup}`
)
const pickAgain = await call('put', `/parcels/${demoParcel.id}/pickup`, { token: stationToken })
check('取件核销·重复取件返回 7002', !pickAgain.ok && pickAgain.code === 7002, `code=${pickAgain.code}`)

/* ==================== 工单四态流转 ==================== */
const openOrder = stationOrders.data.list.find((o) => o.status === 0 && !o.assigneeId)
check('工单·存在待处理工单可接单', !!openOrder, openOrder && openOrder.orderNo)
const accept = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 1, remark: '接单处理' }
})
check(
  '工单流转·待处理 → 处理中',
  accept.ok && accept.data.status === 1,
  accept.ok ? `${accept.data.status}` : `code=${accept.code}`
)
const resolve = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 2, remark: '已联系客户并补偿' }
})
check(
  '工单流转·处理中 → 已解决（含处理记录）',
  resolve.ok &&
    resolve.data.status === 2 &&
    !!resolve.data.resolvedTime &&
    resolve.data.handleLog.some((l) => l.action === 'resolve'),
  resolve.ok ? `${resolve.data.resolvedTime}` : `code=${resolve.code}`
)
const close = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 3, remark: '客户确认无误' }
})
check(
  '工单流转·已解决 → 已关闭',
  close.ok && close.data.status === 3 && !!close.data.closedTime,
  close.ok ? `${close.data.closedTime}` : `code=${close.code}`
)
const illegal = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 1, remark: '重开' }
})
check('工单流转·已关闭再流转返回 8001', !illegal.ok && illegal.code === 8001, `code=${illegal.code}`)
const assign = await call('put', `/work-orders/${openOrder.id}/assign`, { token: adminToken, data: { assigneeId: 4 } })
check(
  '工单指派·ADMIN 指派给 st001_staff',
  assign.ok && assign.data.assigneeId === 4,
  assign.ok ? assign.data.assigneeName : `code=${assign.code}`
)

/* ==================== STAFF 视角（权限与数据范围） ==================== */
const staffToken = await login('st001_staff')
const staffSync = await call('get', '/sync-tasks', { token: staffToken, params: { pageNum: 1, pageSize: 10 } })
check('STAFF·同步状态接口 403（看不到同步页）', !staffSync.ok && staffSync.code === 403, `code=${staffSync.code}`)
const staffParcels = await call('get', '/parcels', { token: staffToken, params: { pageNum: 1, pageSize: 20 } })
check('STAFF·包裹列表可读且限本站', staffParcels.ok && staffParcels.data.list.every((p) => p.stationId === 1))
const staffOrders = await call('get', '/work-orders', { token: staffToken, params: { pageNum: 1, pageSize: 20 } })
check(
  'STAFF·工单列表可读',
  staffOrders.ok && staffOrders.data.total > 0,
  staffOrders.ok ? `total=${staffOrders.data.total}` : staffOrders.message
)
const notMine = staffOrders.data.list.find((o) => o.assigneeId && o.assigneeId !== 4)
if (notMine) {
  const denied = await call('put', `/work-orders/${notMine.id}/status`, { token: staffToken, data: { status: 1 } })
  check('STAFF·非本人处理人工单流转 8002', !denied.ok && denied.code === 8002, `code=${denied.code}`)
} else {
  check('STAFF·非本人处理人工单流转 8002', false, '种子数据中未找到可用样本')
}

/* ==================== 通知角标与跳转联动 ==================== */
const unreadBefore = await call('get', '/notifications/unread-count', { token: staffToken })
const staffNotifs = await call('get', '/notifications', { token: staffToken, params: { pageNum: 1, pageSize: 50 } })
const newestAssign = staffNotifs.data.list.find(
  (n) => n.type === 1 && n.bizType === 'work_order' && n.bizId === openOrder.id
)
check(
  '通知·指派联动生成通知且未读',
  !!newestAssign && !newestAssign.isRead,
  newestAssign && `${newestAssign.title} ${newestAssign.content}`
)
check(
  '通知·未读数与列表一致',
  staffNotifs.data.list.filter((n) => !n.isRead).length === unreadBefore.data.count,
  `${unreadBefore.data.count}`
)
const readOne = await call('put', `/notifications/${newestAssign.id}/read`, { token: staffToken })
const unreadAfter = await call('get', '/notifications/unread-count', { token: staffToken })
check(
  '通知·标记已读后未读 -1',
  readOne.ok && readOne.data.isRead && unreadAfter.data.count === unreadBefore.data.count - 1,
  `${unreadBefore.data.count} → ${unreadAfter.data.count}`
)
const unreadFilter = await call('get', '/notifications', {
  token: staffToken,
  params: { isRead: 0, pageNum: 1, pageSize: 50 }
})
check('通知·未读 Tab 过滤生效', unreadFilter.ok && unreadFilter.data.list.every((n) => !n.isRead))
const readAll = await call('put', '/notifications/read-all', { token: staffToken })
const unreadZero = await call('get', '/notifications/unread-count', { token: staffToken })
check('通知·全部已读后角标为 0', readAll.ok && unreadZero.data.count === 0, `${unreadZero.data.count}`)

/* ==================== 重置演示数据 ==================== */
resetParcelStore()
resetDb()
check('重置·覆盖层已清空', overlay.get(demoParcel.id) === null)
check(
  '重置·包裹回到种子状态（在库待取）',
  queryParcels({ stationId: 1, status: 1, waybillNo: demoParcel.waybillNo, pageNum: 1, pageSize: 1 }).total === 1
)
const staleToken = await call('get', '/auth/me', { token: stationToken })
check('重置·旧 token 失效（强制重新登录）', !staleToken.ok && staleToken.code === 401, `code=${staleToken.code}`)

console.log(`\nT13–T16 数据链路实测：通过 ${pass} / 失败 ${fail}`)
if (failures.length) console.log(`失败项：\n- ${failures.join('\n- ')}`)
process.exitCode = fail ? 1 : 0
