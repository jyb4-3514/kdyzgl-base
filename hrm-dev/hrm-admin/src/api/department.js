import request from '../utils/request'

/**
 * 部门接口（api.md 4.4，4 个，仅 ADMIN）
 */

// GET /api/v1/departments/tree 部门树（含直属员工数 employeeCount，不含子部门）
export function getDepartmentTree() {
  return request.get('/departments/tree')
}

// POST /api/v1/departments 新增部门（parentId: 0=根节点；同级不重名）
export function createDepartment(data) {
  return request.post('/departments', data)
}

// PUT /api/v1/departments/{id} 编辑部门（仅名称/排序，不允许修改 parentId）
export function updateDepartment(id, data) {
  return request.put(`/departments/${id}`, data)
}

// DELETE /api/v1/departments/{id} 删除部门（无子部门、无归属员工才可删）
export function deleteDepartment(id) {
  return request.delete(`/departments/${id}`)
}
