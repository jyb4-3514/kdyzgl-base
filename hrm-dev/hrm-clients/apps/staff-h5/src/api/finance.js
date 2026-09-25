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
