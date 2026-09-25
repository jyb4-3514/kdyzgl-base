import request from '../utils/http.js'

/**
 * 通知（站内信）接口封装（demo-design.md 7.4.6，路径与 shared/mock/routes/notification.js 逐条对应）
 * 只返回当前登录人的通知；工单指派/解决由 Mock 的工单 handler 联动写入
 */

// GET /api/v1/notifications 列表（isRead 0/1 未读筛选，分页）
export function getNotifications(params) {
  return request.get('/notifications', { params })
}

// GET /api/v1/notifications/unread-count 未读数（列表页「未读」角标）
export function getUnreadCount() {
  return request.get('/notifications/unread-count')
}

// PUT /api/v1/notifications/{id}/read 标记单条已读（非本人通知返回 9001）
export function markNotificationRead(id) {
  return request.put(`/notifications/${id}/read`)
}

// PUT /api/v1/notifications/read-all 全部标记已读（幂等）
export function markAllNotificationsRead() {
  return request.put('/notifications/read-all')
}

// POST /api/v1/notifications/publish 手工发布通知（需求4，仅 ADMIN）
// body: { type?, title(1-100), content(1-500), scope: ALL|STATION|EMPLOYEE, stationId?, employeeIds? }
// 返回 { count } 实际收件人数（服务端只取在职且启用员工）；范围不合法返回 9002
// config 透传：9002 需给可操作文案，silent 关掉通用 toast
export function publishNotification(data, config) {
  return request.post('/notifications/publish', data, config)
}
