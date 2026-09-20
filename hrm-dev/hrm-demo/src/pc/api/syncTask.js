import request from '@admin/utils/request'

/**
 * 同步任务接口封装（demo-design.md 7.4.3/7.4.4，路径与 shared/mock/routes/syncTask.js 逐条对应）
 * 仅 ADMIN / STATION_ADMIN 可访问，STAFF 请求返回 403（Mock 层 roles 白名单强制）
 */

// GET /api/v1/sync-tasks 任务列表（stationId/status/keyword/pageNum/pageSize，按创建时间倒序）
export function getSyncTasks(params) {
  return request.get('/sync-tasks', { params })
}

// GET /api/v1/sync-tasks/{id} 任务详情
export function getSyncTaskDetail(id) {
  return request.get(`/sync-tasks/${id}`)
}

// GET /api/v1/sync-tasks/{id}/logs 批次日志（level: 0=info 1=warn 2=error）
export function getSyncTaskLogs(id) {
  return request.get(`/sync-tasks/${id}/logs`)
}

// POST /api/v1/sync-tasks/{id}/trigger 手动触发（仅「待领取」可用，否则 6001）
export function triggerSyncTask(id) {
  return request.post(`/sync-tasks/${id}/trigger`)
}

// POST /api/v1/sync-tasks/{id}/retry 失败重试（仅「失败」可用，回到待领取并 retry_count+1）
export function retrySyncTask(id) {
  return request.post(`/sync-tasks/${id}/retry`)
}
