import http from '../utils/http.js'

/**
 * 移动端接口封装
 *
 * 接口路径与入参严格对齐 shared/mock/routes/*（即 api.md + demo-design.md 7.4 契约），
 * 数据可见范围由 Mock 层按角色强制收敛（非 ADMIN 的 station_id 会被覆盖），前端不重复做过滤。
 * TODO(扩展): 接口数量增长到需要分域时（如 PC 端 api/{parcel,syncTask,workOrder}.js），按业务域拆分本文件。
 */

/* ==================== 认证（api.md 4.1） ==================== */

/** silent：登录/改密的错误由页面就地渲染，不弹全局 Toast */
export const login = (data) => http.post('/auth/login', data, { silent: true })
export const logout = () => http.post('/auth/logout')
export const getMe = () => http.get('/auth/me')
export const updatePassword = (data) => http.put('/auth/password', data, { silent: true })

/* ==================== 看板与包裹（一期 4.2 + 二期） ==================== */

export const getDashboardSummary = () => http.get('/dashboard/summary')
export const getParcelSummary = (params) => http.get('/parcels/summary', { params })
export const getParcelTrend = (params) => http.get('/parcels/trend', { params })
export const getParcelRanking = (params) => http.get('/parcels/ranking', { params })
export const getParcels = (params) => http.get('/parcels', { params })
export const getParcel = (id) => http.get(`/parcels/${id}`)
export const pickupParcel = (id) => http.put(`/parcels/${id}/pickup`)

/* ==================== 同步任务（二期） ==================== */

export const getSyncTasks = (params) => http.get('/sync-tasks', { params })
export const getSyncTask = (id) => http.get(`/sync-tasks/${id}`)
export const getSyncLogs = (id) => http.get(`/sync-tasks/${id}/logs`)
/**
 * 采集状态总览（需求1）：ADMIN 全域、站长收敛到本站（服务端强制）。
 * 四态计数以服务端 counts 为权威，前端不再按 stations 自己聚合，避免两处口径。
 */
export const getSyncOverview = () => http.get('/sync/overview')

/* ==================== 工单（三期） ==================== */

export const getWorkOrders = (params) => http.get('/work-orders', { params })
export const getWorkOrder = (id) => http.get(`/work-orders/${id}`)
export const createWorkOrder = (data) => http.post('/work-orders', data)
/** 状态流转：0→1 接单、1→2 解决、2→3 关闭、2→1 重开、0→3 直接关闭 */
export const changeWorkOrderStatus = (id, data) => http.put(`/work-orders/${id}/status`, data)
/** 指派（仅 ADMIN / 本站站长）；silent：8002 要在指派弹层内说清原因，不叠一层通用 Toast */
export const assignWorkOrder = (id, data) => http.put(`/work-orders/${id}/assign`, data, { silent: true })
/**
 * 转单（T19）：只改处理人、不改状态；入参 { toEmployeeId, reason }
 * silent：8003/8004 要在弹层内说清「是没权限还是人不对」，不叠通用 Toast
 */
export const transferWorkOrder = (id, data) => http.post(`/work-orders/${id}/transfer`, data, { silent: true })

/* ==================== 员工（转单/指派对象候选） ==================== */

/** 仅 ADMIN 可调；站长与员工身份的候选来源见 utils/workorder.js 的 fetchTransferTargets */
export const getEmployees = (params) => http.get('/employees', { params })

/* ==================== 通知（三期） ==================== */

export const getNotifications = (params) => http.get('/notifications', { params })
export const getUnreadCount = () => http.get('/notifications/unread-count', { silent: true })
export const markNotificationRead = (id) => http.put(`/notifications/${id}/read`)
export const markAllNotificationsRead = () => http.put('/notifications/read-all')
/**
 * 发布通知（需求4，仅 ADMIN）：入参 { type, title, content, scope, stationId? }，
 * 返回实际生成的接收人数 count，发布是否二次确认以预览人数为准，不依赖本响应。
 * silent：9002（范围参数不合法）要在页面上说清「该选谁」，通用 Toast 只说「参数不合法」无法指导下一步。
 */
export const publishNotification = (data) => http.post('/notifications/publish', data, { silent: true })

/* ==================== 考勤与排班（T17） ==================== */

export const getAttendanceRule = (params) => http.get('/attendance/rule', { params })
export const getAttendanceRuleList = () => http.get('/attendance/rule/list')
export const saveAttendanceRule = (data) => http.put('/attendance/rule', data)
export const getAttendanceStatus = () => http.get('/attendance/status')
/**
 * silent：打卡失败要按 9101-9107 在页内给「失败在哪、怎么改」的针对性提示，不叠一层通用 Toast
 * 入参：{ checkType, periodIndex?, wifiSsid, longitude, latitude }
 * periodIndex 对应 /attendance/status 返回的时段序号，缺省则按历史单班次模型判定
 */
export const checkIn = (data) => http.post('/attendance/check-in', data, { silent: true })
export const getAttendanceRecords = (params) => http.get('/attendance/records', { params })
export const getAttendanceSummary = (params) => http.get('/attendance/summary', { params })
export const getMyAttendance = (params) => http.get('/attendance/my', { params })
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
/** 老板端考勤模块的驿站选择（规则 / 排班 / 记录按驿站维度查看） */
export const getStationList = (params) => http.get('/stations', { params })

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

/* ==================== 请假（M11） ==================== */

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
/** 运行日志上报（D6，不限角色）：失败丢弃该批，不重试、不弹提示 */
export const reportClientLogs = (logs) => http.post('/system/client-logs', { logs }, { silent: true })

/* ==================== KPI 考核（需求7） ==================== */

export const getKpiMetrics = () => http.get('/kpi/metrics')
/**
 * 批量保存指标权重与启用态（仅 ADMIN）：items 形如 [{ id, weight, enabled? }]
 * 为什么走批量：单指标接口每次都校验「启用指标合计 = 100%」，调权重/切启用存在中间态，逐条提交必被 9202 拒绝；
 * 该端点把整组变更合成一次原子校验，权重合计条上的「配平」才落得下去。
 * silent：9202 要在指标弹层内说清差额，不能被通用 Toast 盖掉。
 */
export const saveKpiMetricBatch = (items) => http.put('/kpi/metrics/batch', { items }, { silent: true })
/** 单指标编辑（仅 ADMIN）：用于目标值等不参与权重合计的字段；silent 理由同上 */
export const updateKpiMetric = (id, data) => http.put(`/kpi/metrics/${id}`, data, { silent: true })
export const calculateKpiScores = (data) => http.post('/kpi/scores/calculate', data)
export const getKpiRanking = (params) => http.get('/kpi/scores/ranking', { params })
export const getKpiScores = (params) => http.get('/kpi/scores', { params })
/** 得分明细：员工只能查本人（越权 403），老板端用作「点排名进明细」 */
export const getKpiScoreDetail = (employeeId, params) => http.get(`/kpi/scores/${employeeId}`, { params })
/**
 * 得分明细（静默版）：首页宫格只需判断「本月有没有考核结果」，
 * 9204（尚未算分）是业务空态而非错误，走通用 Toast 会在首页平白弹一次失败提示。
 */
export const getKpiScoreQuiet = (employeeId, params) => http.get(`/kpi/scores/${employeeId}`, { params, silent: true })

/* ==================== 人事档案与定薪（需求8） ==================== */

export const getHrProfiles = (params) => http.get('/hr/profiles', { params })
/** 档案详情：ADMIN 全量，员工仅本人（契约强制收口，前端不传 employeeId 之外的身份参数） */
export const getHrProfile = (employeeId) => http.get(`/hr/profiles/${employeeId}`)
export const getHrSalary = (employeeId) => http.get(`/hr/salary-structures/${employeeId}`)
/** 调薪：silent —— 9302（已离职）/9305（无定薪档案）要在表单内给针对性说明 */
export const updateHrSalary = (employeeId, data) =>
  http.put(`/hr/salary-structures/${employeeId}`, data, { silent: true })

/* ==================== 入离职流程（需求10） ==================== */
/* 契约口径：入离职流程接口仅 ADMIN 开放，移动端即老板端「审批」；员工端为无权限降级（见 staff/flow.vue） */

export const getOnboardingFlows = (params) => http.get('/hr/onboarding', { params })
export const getOnboardingFlow = (id) => http.get(`/hr/onboarding/${id}`)
/** 步骤办理：silent —— 9303 要说明「请先办理上一步/该步骤已完成」，通用 Toast 说不了这么细 */
export const completeOnboardingStep = (id, key, data) =>
  http.post(`/hr/onboarding/${id}/steps/${key}/complete`, data || {}, { silent: true })
export const rejectOnboardingFlow = (id, data) => http.post(`/hr/onboarding/${id}/reject`, data, { silent: true })
export const getOffboardingFlows = (params) => http.get('/hr/offboarding', { params })
export const getOffboardingFlow = (id) => http.get(`/hr/offboarding/${id}`)
export const completeOffboardingStep = (id, key, data) =>
  http.post(`/hr/offboarding/${id}/steps/${key}/complete`, data || {}, { silent: true })
export const rejectOffboardingFlow = (id, data) => http.post(`/hr/offboarding/${id}/reject`, data, { silent: true })

/* ==================== 工资单（需求9） ==================== */

/** 列表（仅 ADMIN），服务端附 counts（各状态计数，口径为「同月同驿站」） */
export const getPayrolls = (params) => http.get('/finance/payrolls', { params })
export const getPayroll = (id) => http.get(`/finance/payrolls/${id}`)
/** 我的工资单：只认登录身份，且只返回已发布/已确认（未发布不给本人看） */
export const getMyPayrolls = (params) => http.get('/finance/payrolls/my', { params })
export const submitPayrolls = (data) => http.post('/finance/payrolls/submit', data, { silent: true })
/** 审核：silent —— 9403（状态已变，多为他人先审过）要在审核弹层内说清并触发就地对齐 */
export const approvePayroll = (id, data) => http.post(`/finance/payrolls/${id}/approve`, data, { silent: true })
export const publishPayrolls = (data) => http.post('/finance/payrolls/publish', data, { silent: true })
export const confirmPayroll = (id) => http.post(`/finance/payrolls/${id}/confirm`, {}, { silent: true })
export const objectPayroll = (id, data) => http.post(`/finance/payrolls/${id}/objection`, data, { silent: true })
