import request from '../utils/http.js'

/**
 * 工单接口封装（demo-design.md 7.4.5，路径与 shared/mock/routes/workOrder.js 逐条对应）
 * 可见范围：ADMIN 全量 / STATION_ADMIN 限本站 / STAFF 仅本人处理的工单（PC 端 STAFF 不开放工单页）
 */

// GET /api/v1/work-orders 列表（stationId/status/type/priority/assigneeId/keyword/overdueUnhandled/pageNum/pageSize）
// 需求6：筛选参数统一用 overdueUnhandled（旧名 overSla 契约仍兼容，新代码不再使用）
export function getWorkOrders(params) {
  return request.get('/work-orders', { params })
}

// GET /api/v1/work-orders/{id} 详情（含 handleLog 时间线、overdueUnhandled 展示判定）
export function getWorkOrderDetail(id) {
  return request.get(`/work-orders/${id}`)
}

// POST /api/v1/work-orders 新建工单（需求3，source 固定 MANUAL）
// body: { stationId?, type, priority, title, content?, waybillNo?, assigneeId? } → { id, orderNo, source }
// 校验口径：title 1-100、content ≤500、assigneeId 须为在职员工、非 ADMIN 不可跨站指派
export function createWorkOrder(data) {
  return request.post('/work-orders', data)
}

// GET /api/v1/work-orders/dispatch-rules 企微自动派单规则列表
export function getDispatchRules() {
  return request.get('/work-orders/dispatch-rules')
}

// PUT /api/v1/work-orders/dispatch-rules/{id} 维护派单规则（仅 ADMIN，规则不存在返回 8005）
// config 透传：行内编辑需按 400/8005 给字段级文案，silent 关掉通用 toast
export function updateDispatchRule(id, data, config) {
  return request.put(`/work-orders/dispatch-rules/${id}`, data, config)
}

// POST /api/v1/work-orders/auto-dispatch 群消息自动派单（需求3 模拟入口，auth=false 的预留回调接口）
// body: { groupName?, senderName?, content, sendTime?, stationId? }，内容为空返回 8006
// TODO(扩展): 接入企业微信机器人回调时替换为真实签名校验与消息解密
export function autoDispatch(data, config) {
  return request.post('/work-orders/auto-dispatch', data, config)
}

// PUT /api/v1/work-orders/{id}/assign 指派（body: { assigneeId }，仅 ADMIN / 本站站长，否则 8002）
export function assignWorkOrder(id, assigneeId) {
  return request.put(`/work-orders/${id}/assign`, { assigneeId })
}

// PUT /api/v1/work-orders/{id}/status 状态流转（body: { status, remark }，非法跳转返回 8001）
export function changeWorkOrderStatus(id, status, remark) {
  return request.put(`/work-orders/${id}/status`, { status, remark })
}

// POST /api/v1/work-orders/{id}/transfer 转单（body: { toEmployeeId, reason }）
// 只改处理人、不改状态；8003 无权转单 / 8004 对象不合法（非在职、本人、站长转外站）
// config 透传 axios 配置：转单弹窗用 silent 关掉通用 toast，按 8003/8004 给可操作文案
export function transferWorkOrder(id, data, config) {
  return request.post(`/work-orders/${id}/transfer`, data, config)
}
