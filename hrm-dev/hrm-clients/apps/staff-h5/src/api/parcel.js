import http from '../utils/http.js'

/**
 * 包裹（二期）
 * 数据可见范围由 Mock 层按角色强制收敛（非 ADMIN 的 station_id 会被覆盖），前端不重复做过滤。
 */
export const getParcelSummary = (params) => http.get('/parcels/summary', { params })
export const getParcelTrend = (params) => http.get('/parcels/trend', { params })
export const getParcelRanking = (params) => http.get('/parcels/ranking', { params })
export const getParcels = (params) => http.get('/parcels', { params })
export const getParcel = (id) => http.get(`/parcels/${id}`)
export const pickupParcel = (id) => http.put(`/parcels/${id}/pickup`)
