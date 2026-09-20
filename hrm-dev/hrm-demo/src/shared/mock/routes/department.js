import { CODE, DEPARTMENT_CODE } from '../../constants/errorCode.js'
import { activeDepartments, db, deptDirectEmployeeCount, findDepartmentById } from '../db.js'
import { fail, formatDateTime, ok } from '../util.js'
import { isBlank, textLen } from '../validate.js'

/**
 * 部门接口（api.md 4.4，4 个，仅 ADMIN）
 * 部门量级 < 200，树在内存组装，不做 path 物化（db.md 3.1 设计取舍）
 */

/** 同级重名判定：同一 parent_id 下的活跃部门（编辑时排除自身） */
function siblingNameExists(parentId, deptName, excludeId = null) {
  return activeDepartments().some(
    (d) => d.parent_id === Number(parentId) && d.dept_name === deptName && d.id !== Number(excludeId)
  )
}

function tree() {
  const nodes = activeDepartments()
    .slice()
    .sort((a, b) => a.sort_order - b.sort_order || a.id - b.id)
    .map((dept) => ({
      id: dept.id,
      parentId: dept.parent_id,
      deptName: dept.dept_name,
      sortOrder: dept.sort_order,
      // 直属员工数，不含子部门（api.md 4.4.1）
      employeeCount: deptDirectEmployeeCount(dept.id),
      children: []
    }))
  const map = new Map(nodes.map((node) => [node.id, node]))
  const roots = []
  nodes.forEach((node) => {
    const parent = map.get(node.parentId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return ok(roots)
}

function create({ body }) {
  const parentId = Number(body.parentId)
  const deptName = String(body.deptName || '').trim()

  if (body.parentId == null || Number.isNaN(parentId)) return fail(CODE.BAD_REQUEST, '请选择上级部门')
  if (isBlank(deptName)) return fail(CODE.BAD_REQUEST, '请输入部门名称')
  if (!textLen(deptName, 1, 50)) return fail(CODE.BAD_REQUEST, '部门名称长度须为 1-50 字符')
  // parentId=0 表示根节点；非 0 必须是存在的活跃部门
  if (parentId !== 0 && !findDepartmentById(parentId)) return fail(DEPARTMENT_CODE.NOT_EXISTS)
  if (siblingNameExists(parentId, deptName)) return fail(DEPARTMENT_CODE.NAME_EXISTS)

  const id = (db.seq.department += 1)
  const now = formatDateTime(new Date())
  db.departments.push({
    id,
    parent_id: parentId,
    dept_name: deptName,
    sort_order: Number(body.sortOrder) || 0,
    is_deleted: 0,
    create_time: now,
    update_time: now
  })
  return ok({ id })
}

function update({ body, pathParams }) {
  const dept = findDepartmentById(pathParams.id)
  if (!dept) return fail(CODE.NOT_FOUND, '部门不存在')

  // 一期不允许移动子树（决策 D12）：明确拒绝而非静默忽略，避免调用方误以为改成功
  if (body.parentId != null) return fail(CODE.BAD_REQUEST, '不支持修改上级部门')

  const deptName = String(body.deptName || '').trim()
  if (!textLen(deptName, 1, 50)) return fail(CODE.BAD_REQUEST, '部门名称长度须为 1-50 字符')
  if (siblingNameExists(dept.parent_id, deptName, dept.id)) return fail(DEPARTMENT_CODE.NAME_EXISTS)

  dept.dept_name = deptName
  dept.sort_order = Number(body.sortOrder) || 0
  dept.update_time = formatDateTime(new Date())
  return ok(null)
}

function remove({ pathParams }) {
  const dept = findDepartmentById(pathParams.id)
  if (!dept) return fail(CODE.NOT_FOUND, '部门不存在')
  if (activeDepartments().some((d) => d.parent_id === dept.id)) return fail(DEPARTMENT_CODE.HAS_CHILDREN)
  if (deptDirectEmployeeCount(dept.id) > 0) return fail(DEPARTMENT_CODE.HAS_EMPLOYEE)

  dept.is_deleted = 1
  dept.update_time = formatDateTime(new Date())
  return ok(null)
}

export const departmentRoutes = [
  { method: 'get', path: '/departments/tree', roles: ['ADMIN'], handler: tree },
  { method: 'post', path: '/departments', roles: ['ADMIN'], handler: create },
  { method: 'put', path: '/departments/:id', roles: ['ADMIN'], handler: update },
  { method: 'delete', path: '/departments/:id', roles: ['ADMIN'], handler: remove }
]
