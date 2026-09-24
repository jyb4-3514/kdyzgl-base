import http from '../utils/http.js'

/**
 * 考勤与排班（T17）
 * 规则与打卡的错误码分段为 91xx，页面级文案由 utils/attendance.js 的 checkErrorHint 提供。
 */

/* ==================== 打卡规则 ==================== */

export const getAttendanceRule = (params) => http.get('/attendance/rule', { params })
export const getAttendanceRuleList = () => http.get('/attendance/rule/list')
export const saveAttendanceRule = (data) => http.put('/attendance/rule', data)

/* ==================== 打卡与记录 ==================== */

export const getAttendanceStatus = () => http.get('/attendance/status')
/**
 * silent：打卡失败要按 9101-9107 在页内给「失败在哪、怎么改」的针对性提示，不叠一层通用 Toast
 * 入参：{ checkType, periodIndex?, wifiSsid, longitude, latitude }
 * periodIndex 对应 /attendance/status 返回的时段序号，缺省则按历史单班次模型判定
 */
export const checkIn = (data) => http.post('/attendance/check-in', data, { silent: true })
export const getAttendanceRecords = (params) => http.get('/attendance/records', { params })
export const getAttendanceSummary = (params) => http.get('/attendance/summary', { params })
/** 考勤明细（管理端）：入参 { dim, stationId?, date? }，dim 为六维度白名单，含时应到/缺卡这类「无打卡记录」的人 */
export const getAttendanceDetail = (params) => http.get('/attendance/detail', { params })
export const getMyAttendance = (params) => http.get('/attendance/my', { params })

/* ==================== 排班与班次 ==================== */

export const getSchedules = (params) => http.get('/schedules', { params })
/**
 * 站内员工名册：转单/指派的对象候选复用了 /schedules 的人员列（见 utils/workorder.js）
 * silent：员工身份对该接口返回 403，调用方会降级取数，不该给用户弹一个他无法处理的报错
 */
export const getStationRoster = (params) => http.get('/schedules', { params, silent: true })
export const getMySchedules = (params) => http.get('/schedules/my', { params })
export const saveSchedules = (data) => http.post('/schedules/batch', data)
/**
 * 按驿站批量铺排（需求2）：一次把「整站员工 × 日期范围（weekdays 可只排指定星期）」铺上同一班次。
 * skipExisting 缺省 true 表示不覆盖已有排班；返回 { created, skipped, total } 作为结果反馈。
 */
export const batchSchedulesByStation = (data) => http.post('/schedules/batch-by-station', data)
export const getShifts = (params) => http.get('/shifts', { params })

/* ==================== 补卡申请与审批（T19） ==================== */

/**
 * 提交补卡申请，入参 { workDate, periodIndex, checkType, reason }
 * silent：9108（已有申请/已正常打卡）、9107（时段不存在）要落在弹层里就近说清原因，
 * 避免弹层还开着却在背后飘一个 Toast
 */
export const applyMakeup = (data) => http.post('/attendance/makeup', data, { silent: true })
/** 我的补卡申请（登录人本人，不受角色限制） */
export const getMyMakeups = (params) => http.get('/attendance/makeup/my', { params })
/** 补卡申请列表（仅 ADMIN，全驿站；stationId 缺省即全量） */
export const getMakeupList = (params) => http.get('/attendance/makeup/list', { params })
/**
 * 补卡审批（仅 ADMIN）：approved 为布尔值，approveRemark 可空
 * silent：9109（该单已被处理）要在审批弹层里说清「刷新看看是谁处理的」，弹层外飘 Toast 会被忽略
 */
export const approveMakeup = (id, data) => http.post(`/attendance/makeup/${id}/approve`, data, { silent: true })
