import http from '../utils/http.js'

/** 通知（三期）：未读数走 stores/notify.js 单点收敛，组件不直连本文件 */
export const getNotifications = (params) => http.get('/notifications', { params })
export const getUnreadCount = () => http.get('/notifications/unread-count', { silent: true })
export const markNotificationRead = (id) => http.put(`/notifications/${id}/read`)
export const markAllNotificationsRead = () => http.put('/notifications/read-all')
/**
 * 发布通知（需求4，仅 ADMIN）：入参 { type, title, content, scope, stationId? }，
 * 返回实际生成的接收人数 count，发布是否二次确认以预览人数为准，不依赖本响应。
 * silent：9002（范围参数不合法）要在页面上说清「该选谁」，通用 Toast 只说「参数不合法」无法指导下一步。
 */
export const publishNotification = (data) => http.post('/notifications/publish', data, { silent: true })
