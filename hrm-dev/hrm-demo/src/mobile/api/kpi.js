import http from '../utils/http.js'

/** KPI 考核（需求7）：权重合计与算分口径均由服务端校验，前端不做二次计算 */
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
/** 得分明细：员工只能查本人（越权 403），管理端用作「点排名进明细」 */
export const getKpiScoreDetail = (employeeId, params) => http.get(`/kpi/scores/${employeeId}`, { params })
/**
 * 得分明细（静默版）：首页宫格只需判断「本月有没有考核结果」，
 * 9204（尚未算分）是业务空态而非错误，走通用 Toast 会在首页平白弹一次失败提示。
 */
export const getKpiScoreQuiet = (employeeId, params) => http.get(`/kpi/scores/${employeeId}`, { params, silent: true })
