import http from '../utils/http.js'

/** 同步任务与采集状态（二期） */
export const getSyncTasks = (params) => http.get('/sync-tasks', { params })
export const getSyncTask = (id) => http.get(`/sync-tasks/${id}`)
export const getSyncLogs = (id) => http.get(`/sync-tasks/${id}/logs`)
/**
 * 采集状态总览（需求1）：ADMIN 全域、站长收敛到本站（服务端强制）。
 * 四态计数以服务端 counts 为权威，前端不再按 stations 自己聚合，避免两处口径。
 */
export const getSyncOverview = () => http.get('/sync/overview')
