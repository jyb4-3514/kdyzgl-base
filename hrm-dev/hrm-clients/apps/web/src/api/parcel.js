import request from '../utils/http.js'

/**
 * 包裹接口封装（demo-design.md 7.4.2，路径与 shared/mock/routes/parcel.js 逐条对应）
 * request 复用一期封装：Bearer 注入、code 分发、401 跳登录均由它兜住
 */

// GET /api/v1/parcels 组合筛选 + 分页（stationId/status/waybillNo/startTime/endTime/pageNum/pageSize）
// 非 ADMIN 传 stationId 也会被 Mock 强制覆盖为本站，前端不做权限假设
export function getParcels(params) {
  return request.get('/parcels', { params })
}

// GET /api/v1/parcels/{id} 包裹详情（越权按不存在处理，返回 404）
export function getParcelDetail(id) {
  return request.get(`/parcels/${id}`)
}

// GET /api/v1/parcels/summary 包裹指标（总数/今日入库/今日取件/在库待取/异常件/取件率）
export function getParcelSummary() {
  return request.get('/parcels/summary')
}

// GET /api/v1/parcels/trend 近 N 天入库与取件趋势（默认 7 天）
export function getParcelTrend(days) {
  return request.get('/parcels/trend', { params: { days } })
}

// GET /api/v1/parcels/ranking 驿站排行（sort: parcelTotal 默认 / pickupRate / abnormalRate）
export function getParcelRanking(sort) {
  return request.get('/parcels/ranking', { params: { sort } })
}
