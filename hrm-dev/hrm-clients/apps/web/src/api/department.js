import request from '../utils/http.js'

/** 部门接口薄壳（api.md 4.4）：GET /departments/tree 部门树（含直属员工数 employeeCount） */
export function getDepartmentTree() {
  return request.get('/departments/tree')
}
