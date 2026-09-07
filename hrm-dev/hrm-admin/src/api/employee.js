import request from '../utils/request'

/**
 * 员工接口（api.md 4.3，10 个，仅 ADMIN）
 */

// GET /api/v1/employees 分页查询（pageNum/pageSize/keyword/deptId/stationId/status）
export function getEmployees(params) {
  return request.get('/employees', { params })
}

// GET /api/v1/employees/{id} 员工详情
export function getEmployee(id) {
  return request.get(`/employees/${id}`)
}

// POST /api/v1/employees 新增员工
export function createEmployee(data) {
  return request.post('/employees', data)
}

// PUT /api/v1/employees/{id} 编辑员工（无 username/password 字段）
export function updateEmployee(id, data) {
  return request.put(`/employees/${id}`, data)
}

// DELETE /api/v1/employees/{id} 删除员工（逻辑删除 + 强制下线）
export function deleteEmployee(id) {
  return request.delete(`/employees/${id}`)
}

// PUT /api/v1/employees/{id}/status 启用/禁用（body: { status }）
export function updateEmployeeStatus(id, status) {
  return request.put(`/employees/${id}/status`, { status })
}

// PUT /api/v1/employees/{id}/password/reset 重置密码（body: { newPassword }）
export function resetEmployeePassword(id, newPassword) {
  return request.put(`/employees/${id}/password/reset`, { newPassword })
}

// GET /api/v1/employees/import-template 下载导入模板（xlsx 文件流）
export function downloadImportTemplate() {
  return request.get('/employees/import-template', { responseType: 'blob' })
}

// POST /api/v1/employees/import Excel 导入（multipart/form-data，字段名 file）
export function importEmployees(file) {
  const formData = new FormData()
  formData.append('file', file)
  // 导入整批校验可能较慢，单独放宽超时
  return request.post('/employees/import', formData, { timeout: 120000 })
}

// GET /api/v1/employees/export Excel 导出（同列表筛选参数，不含分页；xlsx 文件流）
export function exportEmployees(params) {
  return request.get('/employees/export', {
    params,
    responseType: 'blob',
    timeout: 120000
  })
}
