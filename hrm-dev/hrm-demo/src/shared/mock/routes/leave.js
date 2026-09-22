import { CODE, LEAVE_CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { HALF_DAY, LEAVE_FILTERS, LEAVE_TYPE } from '../../constants/dict.js'
import { fail, ok } from '../util.js'
import { isBlank, isDate, pageSizeInvalid, textLen } from '../validate.js'
import {
  applyLeave,
  cancelLeave,
  finalApprove,
  findLeaveForUser,
  getLeaveSettings,
  previewLeave,
  queryLeaves,
  resubmitLeave,
  revokeLeave,
  saveLeaveSettings,
  stationApprove,
  updateLeave
} from '../leaveStore.js'

/**
 * 请假接口（M11，设计规范附录 A 的 16 个端点中的请假段）
 * 数据范围：非 ADMIN 的 stationId 由 engine 统一收敛为本人归属驿站（见 domain/applyDataScope.js），
 * 前端传别的驿站不报错也不生效；此外还要拦「不能审自己的单」（审核类端点在 store 内二次校验）。
 */

/** 可精确筛选的状态（聚合虚拟值 'PENDING' 单独处理） */
const LEAVE_STATUS_KEYS = LEAVE_FILTERS.map((item) => item.value).filter((value) => value && value !== 'PENDING')
const PENDING_AGGREGATE = ['PENDING_STATION', 'PENDING_BOSS']

/** 查询参数 stationId 归一：空视为全量（数据级收敛已由 engine 统一执行） */
const stationIdOf = (raw) => (isBlank(raw) ? null : Number(raw))

/** 'PENDING' 聚合虚拟值由服务端展开，前端只传一个值，不必自己发两次请求求和 */
const expandStatus = (raw) => (isBlank(raw) ? null : raw === 'PENDING' ? PENDING_AGGREGATE : raw)

/** 列表 / 我的共用筛选校验：状态、假别、日期格式三处都要用，抽一次避免文案分叉 */
function queryError(params) {
  if (pageSizeInvalid(params.pageSize)) return { code: CODE.BAD_REQUEST, message: '每页条数须为 1-100' }
  if (!isBlank(params.status) && !LEAVE_STATUS_KEYS.includes(params.status) && params.status !== 'PENDING')
    return { code: CODE.BAD_REQUEST, message: 'status 取值非法' }
  if (!isBlank(params.leaveType) && !LEAVE_TYPE[params.leaveType])
    return { code: CODE.BAD_REQUEST, message: 'leaveType 取值非法' }
  for (const key of ['startDate', 'endDate']) {
    if (!isBlank(params[key]) && !isDate(params[key]))
      return { code: CODE.BAD_REQUEST, message: `${key} 格式须为 YYYY-MM-DD` }
  }
  return null
}

/** 表单字段校验（提交 / 编辑 / 重提共用）：格式与枚举回 400，日期语义与重叠由 store 判业务码 */
function formError(body) {
  if (!LEAVE_TYPE[body.leaveType]) return { code: CODE.BAD_REQUEST, message: 'leaveType 取值非法' }
  for (const key of ['startDate', 'endDate']) {
    if (!isDate(body[key]) || isBlank(body[key]))
      return { code: CODE.BAD_REQUEST, message: `${key} 格式须为 YYYY-MM-DD` }
  }
  for (const key of ['startPeriod', 'endPeriod']) {
    if (!HALF_DAY[body[key]]) return { code: CODE.BAD_REQUEST, message: `${key} 仅支持 AM / PM` }
  }
  if (!textLen(body.reason, 2, 200)) return { code: CODE.BAD_REQUEST, message: '请假事由须为 2-200 字' }
  return null
}

const formOf = (body) => ({
  leaveType: String(body.leaveType),
  startDate: String(body.startDate),
  startPeriod: String(body.startPeriod),
  endDate: String(body.endDate),
  endPeriod: String(body.endPeriod),
  reason: String(body.reason).trim()
})

const respond = (result) => (result.code === 200 ? ok(result.data) : fail(result.code, result.message))

/** 提交前置守卫：ADMIN 无上级可审，不能提交（设计规范 §2.3） */
function applicantGuard(user) {
  if (user.role === 'ADMIN') return { code: LEAVE_CODE.NO_PERMISSION, message: '超级管理员无需提交请假申请' }
  if (user.station_id == null) return { code: CODE.BAD_REQUEST, message: '当前账号未归属驿站，无法提交请假' }
  return null
}

/* ==================== 申请与我的 ==================== */

function create({ body, user }) {
  const guard = applicantGuard(user)
  if (guard) return fail(guard.code, guard.message)
  const error = formError(body)
  if (error) return fail(error.code, error.message)
  return respond(applyLeave({ employee: user, form: formOf(body) }))
}

/**
 * 只算不落库的试算：计薪天数必须与算薪单点同源，不能在页面里再实现一遍。
 * 日期语义（方向 / 半天组合 / 上限）由 store 的 dateError 统一判定，这里不重复写一套。
 */
function preview({ body, user }) {
  const guard = applicantGuard(user)
  if (guard) return fail(guard.code, guard.message)
  const form = formOf({ ...body, reason: isBlank(body.reason) ? '试算' : body.reason })
  return respond(previewLeave({ employeeId: user.id, form }))
}

function mine({ params, user }) {
  const error = queryError(params)
  if (error) return fail(error.code, error.message)
  return ok(
    queryLeaves({
      employeeId: user.id,
      status: expandStatus(params.status),
      leaveType: params.leaveType,
      startDate: params.startDate,
      endDate: params.endDate,
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      user
    })
  )
}

/** 管理端列表（ADMIN 全域 / STATION_ADMIN 仅本站）：stationId 由 engine 按角色收敛，跨站不可见 */
function list({ params, user }) {
  const error = queryError(params)
  if (error) return fail(error.code, error.message)
  return ok(
    queryLeaves({
      stationId: stationIdOf(params.stationId),
      employeeId: isBlank(params.employeeId) ? null : Number(params.employeeId),
      status: expandStatus(params.status),
      leaveType: params.leaveType,
      startDate: params.startDate,
      endDate: params.endDate,
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      user
    })
  )
}

/* ==================== 详情与状态流转 ==================== */

/** 详情可见范围：ADMIN 全量 / 站长本站 / 本人；越权按 9605 处理，不暴露其他驿站数据 */
function detail({ pathParams, user }) {
  const leave = findLeaveForUser(pathParams.id, user)
  if (!leave) return fail(LEAVE_CODE.NOT_EXISTS)
  const visible =
    user.role === 'ADMIN' ||
    leave.employeeId === user.id ||
    (user.role === 'STATION_ADMIN' && leave.stationId === user.station_id)
  if (!visible) return fail(LEAVE_CODE.NO_PERMISSION)
  return ok(leave)
}

function update({ body, pathParams, user }) {
  const error = formError(body)
  if (error) return fail(error.code, error.message)
  return respond(updateLeave({ id: pathParams.id, employee: user, form: formOf(body) }))
}

function cancel({ pathParams, user }) {
  return respond(cancelLeave({ id: pathParams.id, employee: user }))
}

function resubmit({ body, pathParams, user }) {
  const guard = applicantGuard(user)
  if (guard) return fail(guard.code, guard.message)
  const error = formError(body)
  if (error) return fail(error.code, error.message)
  return respond(resubmitLeave({ id: pathParams.id, employee: user, form: formOf(body) }))
}

/** 审批意见校验：通过时选填 0-100 字，驳回时必填 2-100 字（与补卡的选填刻意不同） */
function approveRemarkError(body) {
  if (typeof body.approved !== 'boolean') return { code: CODE.BAD_REQUEST, message: 'approved 须为布尔值' }
  const remark = isBlank(body.remark) ? null : String(body.remark).trim()
  if (!body.approved && !textLen(remark, 2, 100)) return { code: CODE.BAD_REQUEST, message: '驳回原因须为 2-100 字' }
  if (body.approved && remark && !textLen(remark, 0, 100))
    return { code: CODE.BAD_REQUEST, message: '审批意见不可超过 100 字' }
  return null
}

function stationApproveHandler({ body, pathParams, user }) {
  const error = approveRemarkError(body)
  if (error) return fail(error.code, error.message)
  return respond(
    stationApprove({
      id: pathParams.id,
      approved: body.approved,
      remark: isBlank(body.remark) ? null : String(body.remark).trim(),
      operator: user
    })
  )
}

function finalApproveHandler({ body, pathParams, user }) {
  const error = approveRemarkError(body)
  if (error) return fail(error.code, error.message)
  return respond(
    finalApprove({
      id: pathParams.id,
      approved: body.approved,
      remark: isBlank(body.remark) ? null : String(body.remark).trim(),
      operator: user
    })
  )
}

/** 撤回原因必填：撤回等于撤销一次已生效的公司决定，必须留因 */
function revokeHandler({ body, pathParams, user }) {
  if (!textLen(body.reason, 2, 100)) return fail(CODE.BAD_REQUEST, '撤回原因须为 2-100 字')
  return respond(revokeLeave({ id: pathParams.id, reason: String(body.reason).trim(), operator: user }))
}

/* ==================== 扣款开关（D5） ==================== */

/** 开关只给 ADMIN：站长/员工「读都不可见」（设计规范 §2.1），读侧也收 roles，避免口径只靠前端藏 */
const getSettings = () => ok(getLeaveSettings())

function saveSettings({ body }) {
  if (typeof body.leaveDeductEnabled !== 'boolean') return fail(CODE.BAD_REQUEST, 'leaveDeductEnabled 须为布尔值')
  return ok(saveLeaveSettings({ leaveDeductEnabled: body.leaveDeductEnabled }))
}

/**
 * 路由注册顺序说明：engine 取首个命中，静态路径必须排在 /leave/:id 之前
 * （/leave/preview、/leave/my、/leave/list、/leave/settings 都会被 /leave/:id 的正则命中）
 */
export const leaveRoutes = [
  { method: 'post', path: '/leave', roles: ALL_ROLES, handler: create },
  { method: 'post', path: '/leave/preview', roles: ALL_ROLES, handler: preview },
  { method: 'get', path: '/leave/my', roles: ALL_ROLES, handler: mine },
  { method: 'get', path: '/leave/list', roles: ['ADMIN', 'STATION_ADMIN'], handler: list },
  { method: 'get', path: '/leave/settings', roles: ['ADMIN'], handler: getSettings },
  { method: 'put', path: '/leave/settings', roles: ['ADMIN'], handler: saveSettings },
  { method: 'get', path: '/leave/:id', roles: ALL_ROLES, handler: detail },
  { method: 'put', path: '/leave/:id', roles: ALL_ROLES, handler: update },
  { method: 'post', path: '/leave/:id/cancel', roles: ALL_ROLES, handler: cancel },
  { method: 'post', path: '/leave/:id/resubmit', roles: ALL_ROLES, handler: resubmit },
  { method: 'post', path: '/leave/:id/station-approve', roles: ['STATION_ADMIN'], handler: stationApproveHandler },
  { method: 'post', path: '/leave/:id/final-approve', roles: ['ADMIN'], handler: finalApproveHandler },
  { method: 'post', path: '/leave/:id/revoke', roles: ['ADMIN'], handler: revokeHandler }
]
