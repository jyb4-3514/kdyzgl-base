import request from '../utils/http.js'

/**
 * 驿站采集配置与采集状态（需求1）
 * 路径逐条对齐 shared/mock/routes/syncConfig.js：overview / configs 对 ADMIN 与站长开放，
 * 写操作（PUT）仅 ADMIN，站长进入只读视角（前端不渲染保存按钮，不做「点了才知道没权限」）。
 */

// GET /api/v1/sync/overview 采集状态总览（counts 为四态权威计数，页面不做前端聚合）
export function getSyncOverview() {
  return request.get('/sync/overview')
}

// GET /api/v1/sync/configs 采集配置列表（非 ADMIN 由服务端收敛为本站）
export function getSyncConfigs() {
  return request.get('/sync/configs')
}

// GET /api/v1/sync/configs/{stationId} 单站配置（未配置返回 6002，与加载失败区分）
export function getSyncConfig(stationId) {
  return request.get(`/sync/configs/${stationId}`)
}

// PUT /api/v1/sync/configs/{stationId} 保存采集配置（仅 ADMIN）
// config 透传 axios 配置：抽屉内需按 400/6002 给字段级或空态文案，silent 关掉通用 toast
export function saveSyncConfig(stationId, data, config) {
  return request.put(`/sync/configs/${stationId}`, data, config)
}
