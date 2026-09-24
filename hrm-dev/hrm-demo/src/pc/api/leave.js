import request from '@admin/utils/request'

/**
 * 请假接口封装（M11，路径与入参逐条对齐 shared/mock/routes/leave.js，即未来后端契约）
 *
 * 越权口径与包裹 / 考勤一致：非 ADMIN 传别的 stationId 不报错也不生效，服务端一律覆盖为本人驿站，
 * 因此前端照常传参，不在本地做权限推断（写操作的角色限制以服务端 403 为准）。
 *
 * silent 用在哪：9601/9602/9603/9606/9607 要在弹层或表单内「就地说明 + 给下一步」，
 * 叠一层通用 Toast 会把用户指引从弹层里挤走（与补卡 approveMakeup 的处置约定一致）。
 */

// GET /leave/my 我的请假列表（status 支持聚合值 'PENDING' = 待初审 + 待终审；日期筛选取区间交集）
export function getMyLeaves(params) {
  return request.get('/leave/my', { params })
}

// GET /leave/list 管理端列表（ADMIN 全域 / 站长仅本站，服务端强制收敛 stationId）
export function getLeaveList(params) {
  return request.get('/leave/list', { params })
}

// GET /leave/{id} 详情：出参带 handleLog[] 与 canEdit / canCancel / canRevoke 派生标志，前端直接渲染动作
export function getLeave(id) {
  return request.get(`/leave/${id}`)
}

// POST /leave 提交申请（ADMIN 提交返回 9605；站长提交跳过初审）
export function applyLeave(data) {
  return request.post('/leave', data, { silent: true })
}

// POST /leave/preview 试算（只算不落库）：{ naturalDays, countedDays, hasRestDayExcluded }
// silent：9604 由表单前置校验拦截，服务端命中属绕过前端的异常路径，不需要额外弹层文案
export function previewLeave(data) {
  return request.post('/leave/preview', data, { silent: true })
}

// PUT /leave/{id} 就地修改（仅 PENDING_STATION，9607 = 当前状态不可改）
export function updateLeave(id, data) {
  return request.put(`/leave/${id}`, data, { silent: true })
}

// POST /leave/{id}/cancel 申请人撤销（仅两种待审态，9602 = 状态已变）
export function cancelLeave(id) {
  return request.post(`/leave/${id}/cancel`, {}, { silent: true })
}

// POST /leave/{id}/resubmit 修改后重新提交（仅 REJECTED，返回新单，原单保持只读）
export function resubmitLeave(id, data) {
  return request.post(`/leave/${id}/resubmit`, data, { silent: true })
}

// POST /leave/{id}/station-approve 站长初审（仅 STATION_ADMIN；驳回时 remark 必填 2-100 字）
export function stationApproveLeave(id, data) {
  return request.post(`/leave/${id}/station-approve`, data, { silent: true })
}

// POST /leave/{id}/final-approve 管理员终审（仅 ADMIN；通过时服务端落计薪天数快照）
export function finalApproveLeave(id, data) {
  return request.post(`/leave/${id}/final-approve`, data, { silent: true })
}

// POST /leave/{id}/revoke 撤回已通过单（仅 ADMIN；原因必填 2-100 字；账期工资单非草稿时 9606）
export function revokeLeave(id, data) {
  return request.post(`/leave/${id}/revoke`, data, { silent: true })
}

// GET /leave/settings 请假扣款开关（true = 请假按缺勤计扣款，false = 默认不扣）
export function getLeaveSettings() {
  return request.get('/leave/settings')
}

// PUT /leave/settings 保存扣款开关（仅 ADMIN）
export function saveLeaveSettings(data) {
  return request.put('/leave/settings', data)
}

/* ==================== 前端运行日志（D6） ==================== */

// POST /system/client-logs 批量上报（不限角色）；失败由调用方丢弃该批，不重试、不弹提示
export function reportClientLogs(logs) {
  return request.post('/system/client-logs', { logs }, { silent: true })
}

// GET /system/client-logs 运行日志列表（仅 ADMIN），返回分页 + counts（total/error/warn/info/sourceCount）
export function getClientLogs(params) {
  return request.get('/system/client-logs', { params })
}

// POST /system/client-logs/clear 清空日志（仅 ADMIN，二次确认后调用）
export function clearClientLogsApi() {
  return request.post('/system/client-logs/clear', {})
}
