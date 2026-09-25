import http from '../utils/http.js'

/** 工单（三期） */
export const getWorkOrders = (params) => http.get('/work-orders', { params })
export const getWorkOrder = (id) => http.get(`/work-orders/${id}`)
export const createWorkOrder = (data) => http.post('/work-orders', data)
/** 状态流转：0→1 接单、1→2 解决、2→3 关闭、2→1 重开、0→3 直接关闭 */
export const changeWorkOrderStatus = (id, data) => http.put(`/work-orders/${id}/status`, data)
/** 指派（仅 ADMIN / 本站站长）；silent：8002 要在指派弹层内说清原因，不叠一层通用 Toast */
export const assignWorkOrder = (id, data) => http.put(`/work-orders/${id}/assign`, data, { silent: true })
/**
 * 转单（T19）：只改处理人、不改状态；入参 { toEmployeeId, reason }
 * silent：8003/8004 要在弹层内说清「是没权限还是人不对」，不叠通用 Toast
 */
export const transferWorkOrder = (id, data) => http.post(`/work-orders/${id}/transfer`, data, { silent: true })
