import request from '../utils/request'

/**
 * 驿站接口（api.md 4.5，5 个，仅 ADMIN）
 */

// GET /api/v1/stations 驿站列表（全量不分页，可选 status 过滤）
export function getStations(params) {
  return request.get('/stations', { params })
}

// POST /api/v1/stations 新增驿站（code 全局唯一）
export function createStation(data) {
  return request.post('/stations', data)
}

// PUT /api/v1/stations/{id} 编辑驿站（一期允许修改 code，唯一校验生效）
export function updateStation(id, data) {
  return request.put(`/stations/${id}`, data)
}

// PUT /api/v1/stations/{id}/status 启用/停用（body: { status }）
export function updateStationStatus(id, status) {
  return request.put(`/stations/${id}/status`, { status })
}

// DELETE /api/v1/stations/{id} 删除驿站（无归属员工才可删）
export function deleteStation(id) {
  return request.delete(`/stations/${id}`)
}
