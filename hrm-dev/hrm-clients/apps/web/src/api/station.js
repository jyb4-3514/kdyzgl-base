import request from '../utils/http.js'

/** 驿站接口薄壳（api.md 4.5，仅 ADMIN）：GET /stations 全量不分页，可选 status 过滤 */
export function getStations(params) {
  return request.get('/stations', { params })
}
