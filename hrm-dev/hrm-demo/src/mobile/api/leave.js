import http from '../utils/http.js'

/**
 * 请假（M11）
 * 派生标志 canEdit / canCancel / canRevoke 以 GET /leave/:id 下发为权威，前端只渲染不推导。
 */

/**
 * 提交请假申请，入参 { leaveType, startDate, startPeriod, endDate, endPeriod, reason }
 * silent：9603（时间段重叠）/ 9604（日期非法）要在表单内给出重叠单信息或日期修正方向，通用 Toast 说不了这么细
 */
export const applyLeave = (data) => http.post('/leave', data, { silent: true })
/** 试算（只算不落库）：{ naturalDays, countedDays, hasRestDayExcluded }，计薪天数不在前端二次实现 */
export const previewLeave = (data) => http.post('/leave/preview', data, { silent: true })
/** 我的请假（status 支持聚合值 'PENDING'） */
export const getMyLeaves = (params) => http.get('/leave/my', { params })
/** 详情：带 handleLog[] 与 canEdit / canCancel / canRevoke 派生标志 */
export const getLeave = (id) => http.get(`/leave/${id}`)
export const updateLeave = (id, data) => http.put(`/leave/${id}`, data, { silent: true })
export const cancelLeave = (id) => http.post(`/leave/${id}/cancel`, {}, { silent: true })
export const resubmitLeave = (id, data) => http.post(`/leave/${id}/resubmit`, data, { silent: true })
/** 管理端列表（ADMIN 全域 / 站长仅本站；stationId 由服务端强制覆盖） */
export const getLeaveList = (params) => http.get('/leave/list', { params })
/** 站长初审（仅 STATION_ADMIN；驳回时 remark 必填） */
export const stationApproveLeave = (id, data) => http.post(`/leave/${id}/station-approve`, data, { silent: true })
/** 老板终审（仅 ADMIN） */
export const finalApproveLeave = (id, data) => http.post(`/leave/${id}/final-approve`, data, { silent: true })
/** 撤回已通过单（仅 ADMIN；原因必填；账期工资单非草稿时 9606） */
export const revokeLeave = (id, data) => http.post(`/leave/${id}/revoke`, data, { silent: true })
export const getLeaveSettings = () => http.get('/leave/settings', { silent: true })
export const saveLeaveSettings = (data) => http.put('/leave/settings', data)
