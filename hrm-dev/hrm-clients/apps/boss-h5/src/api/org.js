import http from '../utils/http.js'

/**
 * 组织主数据（员工 / 驿站）：移动端只用于候选与选择器，不做组织管理。
 * 数据可见范围由 Mock 层按角色收敛，前端不做归属过滤（避免两处口径）。
 */

/** 仅 ADMIN 可调；站长与员工身份的候选来源见 utils/workorder.js 的 fetchTransferTargets */
export const getEmployees = (params) => http.get('/employees', { params })
/** 管理端考勤模块的驿站选择（规则 / 排班 / 记录按驿站维度查看） */
export const getStationList = (params) => http.get('/stations', { params })

/* ==================== 驿站管理写操作（管理能力扩展） ====================
 * 全部 silent：失败文案要落在表单/详情页的就地错误块（沿用 400/4002/4003/4004 契约文案），
 * 再叠一层通用 Toast 会盖住「到底哪一项不合法」的具体说明。
 * 权限：接口均为 ADMIN-only；本端路由 meta.roles 已 fail-closed 到 ADMIN，此处不重复推导。
 */

export const createStation = (data) => http.post('/stations', data, { silent: true })
export const updateStation = (id, data) => http.put(`/stations/${id}`, data, { silent: true })
export const updateStationStatus = (id, status) => http.put(`/stations/${id}/status`, { status }, { silent: true })
export const deleteStation = (id) => http.delete(`/stations/${id}`, { silent: true })

export const createEmployee = (data) => http.post('/employees', data, { silent: true })
/** 编辑员工：契约无 username / password（改口令走 resetEmployeePassword） */
export const updateEmployee = (id, data) => http.put(`/employees/${id}`, data, { silent: true })
export const updateEmployeeStatus = (id, status) => http.put(`/employees/${id}/status`, { status }, { silent: true })
/** 重置登录口令：服务端重置后 pwd_changed=0 并强制下线（首登须改密） */
export const resetEmployeePassword = (id, newPassword) =>
  http.put(`/employees/${id}/password/reset`, { newPassword }, { silent: true })
