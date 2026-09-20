import request from '@admin/utils/request'

/**
 * 考勤与排班接口封装（路径、入参逐条对齐 shared/mock/routes/attendance.js，即未来后端契约）
 *
 * 越权口径与包裹/工单一致：非 ADMIN 传别的 stationId 不报错也不生效，服务端一律覆盖为本人驿站，
 * 因此前端照常传参，不做本地权限推断（写操作的服务端角色限制见下方逐条注释）。
 */

// GET /api/v1/attendance/rule 打卡规则（ADMIN 必传 stationId，否则 400；非 ADMIN 由服务端收敛本站）
// 返回体含时段配置：checkFrequency（2/4）、checkPeriods([{name,startTime,endTime}])、allowEarlyMin、allowLateMin，
// workStartTime/workEndTime 是由时段派生的只读值
export function getAttendanceRule(stationId) {
  return request.get('/attendance/rule', { params: { stationId } })
}

// PUT /api/v1/attendance/rule 保存规则（按驿站覆盖式保存，首次保存即创建；仅 ADMIN，其他角色 403）
// 入参原样透传给服务端，由服务端白名单收口：时段非法（数量不匹配 / 起止倒置 / 相互重叠）统一返回 9107
export function saveAttendanceRule(data) {
  return request.put('/attendance/rule', data)
}

// GET /api/v1/attendance/summary 打卡概况（date 缺省为今天，字段：应到/实到/正常/迟到/早退/缺卡）
export function getAttendanceSummary(params) {
  return request.get('/attendance/summary', { params })
}

// GET /api/v1/attendance/records 打卡记录（stationId/employeeId/status/startDate/endDate + 分页）
// 每行带 periodIndex / periodName 归属到具体时段，多频次场景靠它区分「上午班 · 上班卡」
export function getAttendanceRecords(params) {
  return request.get('/attendance/records', { params })
}

// GET /api/v1/attendance/export 考勤记录导出（需求5）：返回 CSV Blob，筛选口径与 /attendance/records 完全一致
// responseType=blob 时 request 返回的是完整 axios 响应（含 content-disposition），交 saveResponseFile 落盘
export function exportAttendance(params, config) {
  return request.get('/attendance/export', { params, responseType: 'blob', ...config })
}

// GET /api/v1/schedules 周排班矩阵（stationId 必传；weekStart 缺省本周一，返回 dates + shifts + employees[].days）
export function getSchedules(params) {
  return request.get('/schedules', { params })
}

// POST /api/v1/schedules/batch 批量保存排班（唯一性 = employeeId + workDate，shiftId 传空表示清空该天；仅 ADMIN）
// config 透传 axios 配置：排班页用 silent 关掉通用 toast，改为按 9106/400 给针对性提示
export function saveSchedulesBatch(data, config) {
  return request.post('/schedules/batch', data, config)
}

// POST /api/v1/schedules/batch-by-station 按驿站批量铺排（需求2，仅 ADMIN）
// body: { stationId, shiftId, startDate, endDate, employeeIds?, weekdays?(0-6), skipExisting? }
// 返回 { created, skipped, total }；config 透传：铺排弹窗按 9106/400 给针对性文案
export function saveSchedulesByStation(data, config) {
  return request.post('/schedules/batch-by-station', data, config)
}

// GET /api/v1/shifts 班次列表（按开始时间升序，含已停用班次）
export function getShifts(stationId) {
  return request.get('/shifts', { params: { stationId } })
}

// POST /api/v1/shifts 新增班次（仅 ADMIN）
export function createShift(data) {
  return request.post('/shifts', data)
}

// PUT /api/v1/shifts/{id} 编辑班次（仅 ADMIN，班次不存在返回 404）
export function updateShift(id, data) {
  return request.put(`/shifts/${id}`, data)
}

// DELETE /api/v1/shifts/{id} 删除班次（被排班引用时返回 400「该班次已被排班引用，不能删除」）
export function deleteShift(id) {
  return request.delete(`/shifts/${id}`)
}

// POST /api/v1/attendance/makeup 提交补卡（body: { workDate, periodIndex, checkType, reason }，申请人只认登录人本人）
// 9108 = 该时段当日已有补卡申请或已正常打卡；9101/9107 = 规则或时段不成立
export function applyMakeup(data) {
  return request.post('/attendance/makeup', data)
}

// GET /api/v1/attendance/makeup/my 我的补卡申请（status/startDate/endDate + 分页），不限角色
export function getMyMakeups(params) {
  return request.get('/attendance/makeup/my', { params })
}

// GET /api/v1/attendance/makeup/list 补卡列表（仅 ADMIN；不传 stationId 即全域，非 ADMIN 调用返回 403）
export function getMakeupList(params) {
  return request.get('/attendance/makeup/list', { params })
}

// POST /api/v1/attendance/makeup/{id}/approve 审批补卡（仅 ADMIN，body: { approved, approveRemark }）
// 9109 = 申请已处理过；config 透传 axios 配置，审批页用 silent 关掉通用 toast 改给针对性文案
export function approveMakeup(id, data, config) {
  return request.post(`/attendance/makeup/${id}/approve`, data, config)
}
