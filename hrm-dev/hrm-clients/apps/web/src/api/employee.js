import request from '../utils/http.js'

/**
 * 员工接口薄壳（api.md 4.3，仅 ADMIN）
 * 本端自建（不再经 @admin/api），使 PC 侧取数统一走 @kdyzgl/api-client 的 createHttp。
 */
export function getEmployees(params) {
  return request.get('/employees', { params })
}

export function getEmployee(id) {
  return request.get(`/employees/${id}`)
}
