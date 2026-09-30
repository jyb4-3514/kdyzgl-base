import http from '../utils/http.js'

/** 工资单（需求9）：与 PC 端 api/finance.js 同名同域，为未来两端 api 合并留路 */
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

/* ==================== 薪资结算自动化（I-1~I-10） ==================== */

// I-1 算薪配置列表：驿站列表 + 各站配置（未配置驿站 payrollDay 为空，前端按 9406 呈现）
export const getPayrollSettings = (params) => http.get('/finance/payroll-settings', { params })
// I-2 单驿站配置：尚未配置时回 9406，页面按默认值呈现，不判错误态
export const getPayrollSetting = (stationId) => http.get(`/finance/payroll-settings/${stationId}`, { silent: true })
// I-3 保存配置：算薪日非法 9407 / 时间格式非法 9408，由页面就地在字段下提示
export const savePayrollSetting = (stationId, data) =>
  http.put(`/finance/payroll-settings/${stationId}`, data, { silent: true })
// I-9 配置变更历史：ENABLE 动作须醒目标识（M-9）
export const getPayrollSettingLogs = (stationId, params) =>
  http.get(`/finance/payroll-settings/${stationId}/logs`, { params })
// I-5 运行记录 / I-4 手工触发的封装已随「自动算薪运行」页下线而移除（后端端点保留，前端不再消费）
// I-6 手工加 / 扣款：事由必填 2-200（9412）
export const addPayrollItem = (id, data) => http.post(`/finance/payrolls/${id}/items/add`, data, { silent: true })
// C-3 修改人工项金额：事由必填 2-200（9412）
export const updatePayrollItems = (id, items, reason) =>
  http.put(`/finance/payrolls/${id}/items`, { items, reason }, { silent: true })
// I-7 操作留痕：字段裁剪由服务端按角色强制
export const getPayrollLogs = (id) => http.get(`/finance/payrolls/${id}/logs`)
// I-8 确认发放归档：来源非 CONFIRMED 回 9403，已归档回 9413
export const payPayroll = (id, data) => http.post(`/finance/payrolls/${id}/pay`, data || {}, { silent: true })
