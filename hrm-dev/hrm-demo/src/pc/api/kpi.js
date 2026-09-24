import request from '@admin/utils/request'

/**
 * KPI 考核接口封装（需求7，路径与入参逐条对齐 shared/mock/routes/kpi.js，即未来后端契约）
 *
 * 权限口径：指标配置与算分只有管理员（ADMIN）；得分列表 / 排名对 ADMIN 与站长开放（站长限本站）；
 * 得分明细任意角色可查本人、站长限本站、管理员全量。前端照常传参，不做本地权限推断。
 * 越权口径与考勤/工单一致：非 ADMIN 传别的 stationId 不报错也不生效，服务端一律覆盖为本人驿站。
 */

// GET /api/v1/kpi/metrics 指标列表（仅 ADMIN）→ { list, weightSum }（weightSum 为启用指标权重合计）
export function getKpiMetrics() {
  return request.get('/kpi/metrics')
}

// POST /api/v1/kpi/metrics 新增指标（仅 ADMIN）：启用指标权重合计不为 100% 时服务端回 9202
export function createKpiMetric(data) {
  return request.post('/kpi/metrics', data)
}

// PUT /api/v1/kpi/metrics/{id} 编辑指标（仅 ADMIN）；入参只传变更字段，白名单由服务端收口
export function updateKpiMetric(id, data) {
  return request.put(`/kpi/metrics/${id}`, data)
}

// DELETE /api/v1/kpi/metrics/{id} 删除指标（仅 ADMIN）；删除后剩余启用指标权重合计仍须为 100%
export function deleteKpiMetric(id) {
  return request.delete(`/kpi/metrics/${id}`)
}

/**
 * PUT /api/v1/kpi/metrics/batch 批量保存指标（权重 + 启用状态，仅 ADMIN）
 *
 * 为什么需要批量接口：契约的单指标 PUT 每次都会校验「启用指标合计 = 100%」，
 * 调权重天然要多步（如 30/20 → 40/10），停用一个非 0 权重指标同样会立刻偏离 100%，
 * 单指标接口做不出「重新配平」。该接口一次性原子校验并落地，是本次为支撑
 * 「权重合计条 + 保存」在 Mock 侧追加的最小补充，不改变任何既有接口行为。
 * items: [{ id, weight, enabled? }]
 * TODO(扩展): 后端正式定稿时并入 KPI 指标服务，或改为「模板整体提交」语义。
 */
export function saveKpiMetricBatch(items) {
  return request.put('/kpi/metrics/batch', { items })
}

// POST /api/v1/kpi/scores/calculate 按月算分（仅 ADMIN）：month 必填，按考勤/工单等既有数据自动算分
export function calculateKpiScores(data) {
  return request.post('/kpi/scores/calculate', data)
}

// GET /api/v1/kpi/scores 按月得分列表（ADMIN / STATION_ADMIN，分页）：每行含综合得分、达成率、排名
export function getKpiScores(params) {
  return request.get('/kpi/scores', { params })
}

// GET /api/v1/kpi/scores/ranking 排名榜（ADMIN / STATION_ADMIN）：额外返回 count / avgScore / topScore 汇总
export function getKpiRanking(params) {
  return request.get('/kpi/scores/ranking', { params })
}

// GET /api/v1/kpi/scores/{employeeId} 得分明细：逐指标的目标值 / 实际值 / 达成率 / 得分 / 加权分
export function getKpiScoreDetail(employeeId, params) {
  return request.get(`/kpi/scores/${employeeId}`, { params })
}
