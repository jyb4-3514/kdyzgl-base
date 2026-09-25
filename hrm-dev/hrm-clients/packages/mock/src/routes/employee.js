import {
  AUTH_CODE,
  CODE,
  DEPARTMENT_CODE,
  EMPLOYEE_CODE,
  IMPORT_CODE,
  STATION_CODE
} from '@kdyzgl/shared/constants/errorCode.js'
import {
  activeAdminCount,
  activeEmployees,
  db,
  deptIdWithChildren,
  deptName,
  findDepartmentById,
  findEmployeeById,
  findStationById,
  stationName,
  toEmployeeVO
} from '../db.js'
import { CSV_TYPE, csvDisposition, fail, formatDate, formatDateTime, ok, paginate, toCsvBlob } from '../util.js'
import { isBlank, isDate, isPhone, isStrongPassword, isUsername, pageSizeInvalid, textLen } from '../validate.js'

/**
 * 员工接口（api.md 4.3，10 个，仅 ADMIN）
 * 路由注册顺序有语义：`/employees/import-template`、`/employees/export` 必须排在
 * `/employees/:id` 之前，否则会被路径参数吞掉（engine 按注册顺序取首个命中）
 */

/** 一期仅两个可分配角色；STATION_ADMIN 一期由后端保留、暂不开放分配（api.md 4.3.3） */
const ASSIGNABLE_ROLES = ['ADMIN', 'STAFF']

function list({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')

  let rows = activeEmployees()
  const keyword = String(params.keyword || '').trim()
  if (keyword) {
    // 姓名/登录账号/手机号三字段模糊匹配，任一命中（手机号按完整值匹配，与后端一致）
    rows = rows.filter(
      (e) => e.real_name.includes(keyword) || e.username.includes(keyword) || String(e.phone).includes(keyword)
    )
  }
  if (params.deptId !== undefined && params.deptId !== '') {
    const deptIds = deptIdWithChildren(params.deptId)
    rows = rows.filter((e) => e.dept_id != null && deptIds.includes(e.dept_id))
  }
  if (params.stationId !== undefined && params.stationId !== '') {
    rows = rows.filter((e) => e.station_id === Number(params.stationId))
  }
  if (params.status !== undefined && params.status !== '') {
    rows = rows.filter((e) => e.status === Number(params.status))
  }

  // 排序固定 create_time DESC（api.md 4.3.1）：时间串格式统一，字符串倒序等价于时间倒序
  rows = rows
    .slice()
    .sort((a, b) => (a.create_time < b.create_time ? 1 : a.create_time > b.create_time ? -1 : b.id - a.id))
  const page = paginate(rows, params.pageNum, params.pageSize)
  page.list = page.list.map(toEmployeeVO)
  return ok(page)
}

function detail({ pathParams }) {
  const employee = findEmployeeById(pathParams.id)
  if (!employee) return fail(CODE.NOT_FOUND, '员工不存在')
  return ok(toEmployeeVO(employee))
}

/** 新增/编辑共用的业务校验：返回 { code, message } 或 null */
function validateEmployeeBody(body, { isCreate, targetId = null }) {
  if (isCreate) {
    if (!isUsername(body.username))
      return { code: CODE.BAD_REQUEST, message: '登录账号须为 4-30 位，以字母开头，仅含字母/数字/下划线' }
    if (!isStrongPassword(body.password))
      return { code: CODE.BAD_REQUEST, message: '初始密码须为 8-20 位且同时包含字母和数字' }
    if (activeEmployees().some((e) => e.username === String(body.username).trim()))
      return { code: AUTH_CODE.USERNAME_EXISTS }
  }
  if (!textLen(body.realName, 1, 50)) return { code: CODE.BAD_REQUEST, message: '姓名长度须为 1-50 字符' }
  if (!isPhone(body.phone)) return { code: CODE.BAD_REQUEST, message: '手机号格式不正确' }
  if (activeEmployees().some((e) => e.phone === String(body.phone) && e.id !== Number(targetId))) {
    return { code: EMPLOYEE_CODE.PHONE_EXISTS }
  }
  if (body.gender != null && ![0, 1, 2].includes(Number(body.gender)))
    return { code: CODE.BAD_REQUEST, message: '性别值非法' }
  if (!isBlank(body.deptId) && !findDepartmentById(body.deptId)) return { code: DEPARTMENT_CODE.NOT_EXISTS }
  if (!isBlank(body.stationId)) {
    const station = findStationById(body.stationId)
    if (!station) return { code: STATION_CODE.NOT_EXISTS }
    // 停用驿站不可归属（存量归属保留，仅拦截新增/编辑）
    if (station.status !== 1) return { code: STATION_CODE.DISABLED }
  }
  if (!ASSIGNABLE_ROLES.includes(body.role)) return { code: CODE.BAD_REQUEST, message: '角色仅支持 ADMIN / STAFF' }
  if (!isDate(body.entryDate)) return { code: CODE.BAD_REQUEST, message: '入职日期格式须为 yyyy-MM-dd' }
  if (body.remark != null && !textLen(body.remark, 0, 255))
    return { code: CODE.BAD_REQUEST, message: '备注长度不可超过 255 字符' }
  return null
}

function create({ body }) {
  const invalid = validateEmployeeBody(body, { isCreate: true })
  if (invalid) return fail(invalid.code, invalid.message)

  const id = (db.seq.employee += 1)
  const now = formatDateTime(new Date())
  db.employees.push({
    id,
    username: String(body.username).trim(),
    password: String(body.password),
    real_name: String(body.realName).trim(),
    phone: String(body.phone),
    gender: Number(body.gender) || 0,
    dept_id: isBlank(body.deptId) ? null : Number(body.deptId),
    station_id: isBlank(body.stationId) ? null : Number(body.stationId),
    role: body.role,
    status: 1,
    pwd_changed: 0, // 首登强制改密（api.md 4.3.3）
    entry_date: body.entryDate || null,
    last_login_time: null,
    remark: body.remark || null,
    is_deleted: 0,
    create_time: now,
    update_time: now
  })
  return ok({ id })
}

function update({ body, pathParams, user }) {
  const employee = findEmployeeById(pathParams.id)
  if (!employee) return fail(CODE.NOT_FOUND, '员工不存在')
  // 自我保护优先于管理员保护，与 api.md 2001 的语义一致
  if (employee.id === user.id && body.role != null && body.role !== user.role) return fail(EMPLOYEE_CODE.SELF_OPERATION)

  const invalid = validateEmployeeBody(body, { isCreate: false, targetId: employee.id })
  if (invalid) return fail(invalid.code, invalid.message)

  // 管理员降级保护：变更后活跃管理员数为 0 时拒绝。
  // 可复现路径：种子含 3 个管理员，先删除/禁用其余两个，再对最后一个其他管理员做降级。
  // 说明：单会话流程下调用方自身即活跃管理员，故该分支实际是并发场景的防御性校验，保留以对齐后端实现。
  if (employee.role === 'ADMIN' && body.role !== 'ADMIN' && activeAdminCount(employee.id) === 0) {
    return fail(EMPLOYEE_CODE.LAST_ADMIN)
  }

  employee.real_name = String(body.realName).trim()
  employee.phone = String(body.phone)
  employee.gender = Number(body.gender) || 0
  employee.dept_id = isBlank(body.deptId) ? null : Number(body.deptId)
  employee.station_id = isBlank(body.stationId) ? null : Number(body.stationId)
  employee.role = body.role
  employee.entry_date = body.entryDate || null
  employee.remark = body.remark || null
  employee.update_time = formatDateTime(new Date())
  return ok(null)
}

function remove({ pathParams, user }) {
  const employee = findEmployeeById(pathParams.id)
  if (!employee) return fail(CODE.NOT_FOUND, '员工不存在')
  if (employee.id === user.id) return fail(EMPLOYEE_CODE.SELF_OPERATION)
  if (employee.role === 'ADMIN' && activeAdminCount(employee.id) === 0) return fail(EMPLOYEE_CODE.LAST_ADMIN)

  employee.is_deleted = 1
  employee.update_time = formatDateTime(new Date())
  db.sessions.delete(employee.id) // 逻辑删除 + 强制下线（api.md 4.3.5）
  return ok(null)
}

function updateStatus({ body, pathParams, user }) {
  const employee = findEmployeeById(pathParams.id)
  if (!employee) return fail(CODE.NOT_FOUND, '员工不存在')
  const status = Number(body.status)
  if (status !== 0 && status !== 1) return fail(CODE.BAD_REQUEST, '状态值非法')
  if (employee.id === user.id) return fail(EMPLOYEE_CODE.SELF_OPERATION)
  if (status === 0 && employee.role === 'ADMIN' && activeAdminCount(employee.id) === 0)
    return fail(EMPLOYEE_CODE.LAST_ADMIN)

  employee.status = status
  employee.update_time = formatDateTime(new Date())
  if (status === 0) db.sessions.delete(employee.id) // 禁用即强制下线（api.md 4.3.6）
  return ok(null)
}

function resetPassword({ body, pathParams, user }) {
  const employee = findEmployeeById(pathParams.id)
  if (!employee) return fail(CODE.NOT_FOUND, '员工不存在')
  // 重置自己请走 PUT /auth/password（api.md 4.3.7）
  if (employee.id === user.id) return fail(EMPLOYEE_CODE.SELF_OPERATION)
  if (!isStrongPassword(body.newPassword)) return fail(CODE.BAD_REQUEST, '新密码须为 8-20 位且同时包含字母和数字')

  employee.password = String(body.newPassword)
  employee.pwd_changed = 0
  employee.update_time = formatDateTime(new Date())
  db.sessions.delete(employee.id)
  return ok(null)
}

/* ==================== 导入 / 导出（文件流） ==================== */

const IMPORT_TEMPLATE_HEADER = ['姓名', '登录账号', '手机号', '性别', '部门名称', '驿站名称', '入职日期', '备注']

/** 导入模板：列定义与 api.md 5.1 一致；模板不含示例数据行（避免示例被误导入） */
function importTemplate() {
  return {
    code: CODE.SUCCESS,
    message: 'success',
    data: toCsvBlob([IMPORT_TEMPLATE_HEADER]),
    headers: { 'content-type': CSV_TYPE, 'content-disposition': csvDisposition('员工导入模板.csv') }
  }
}

/**
 * Excel 导入。Mock 无法解析 xlsx 二进制，用可复现的代理规则覆盖错误分支：
 * - 非 .xlsx / 空文件 → 5001
 * - 文件 > 10MB → 5002（真实行数无法从二进制得出，用体积代理；行级上限由后端实现）
 * - 文件名含「错误」或 error → 5003 + 两条示例行级错误（供页面演示错误明细渲染）
 * - 其余 → 成功，total/successCount 由文件体积推导，保证同一文件多次导入结果一致
 * TODO(扩展): 演示若需真实解析，引入 SheetJS 读取行数与列值后按 api.md 5.1/5.2 逐行校验
 */
function importEmployees({ body }) {
  const file = typeof body.get === 'function' ? body.get('file') : null
  if (!file || !file.size || !/\.xlsx$/i.test(file.name || '')) return fail(IMPORT_CODE.FILE_INVALID)
  if (file.size > 10 * 1024 * 1024) return fail(IMPORT_CODE.ROW_LIMIT, '导入数据超过单次上限（1000 行）')

  // 演示触发约定：文件名带「错误 / error」时返回 5003 明细，方便验证页面错误表格
  if (/错误|error/i.test(file.name)) {
    return {
      code: IMPORT_CODE.ROW_ERRORS,
      message: '导入数据存在校验错误，共 2 行失败，全部数据未入库',
      data: {
        total: 3,
        failCount: 2,
        errors: [
          { row: 3, field: '登录账号', message: '已被使用' },
          { row: 7, field: '手机号', message: '格式不正确，须为 11 位有效手机号' }
        ]
      }
    }
  }

  const total = Math.max(1, Math.min(1000, Math.round(file.size / 80)))
  return ok({ total, successCount: total, failCount: 0 }, '导入成功')
}

/** 导出：列定义与 api.md 第 6 章一致，手机号完整输出（决策 D5） */
function exportEmployees({ params }) {
  let rows = activeEmployees()
  const keyword = String(params.keyword || '').trim()
  if (keyword) {
    rows = rows.filter(
      (e) => e.real_name.includes(keyword) || e.username.includes(keyword) || String(e.phone).includes(keyword)
    )
  }
  if (params.deptId !== undefined && params.deptId !== '') {
    const deptIds = deptIdWithChildren(params.deptId)
    rows = rows.filter((e) => e.dept_id != null && deptIds.includes(e.dept_id))
  }
  if (params.stationId !== undefined && params.stationId !== '') {
    rows = rows.filter((e) => e.station_id === Number(params.stationId))
  }
  if (params.status !== undefined && params.status !== '') {
    rows = rows.filter((e) => e.status === Number(params.status))
  }

  const header = [
    '登录账号',
    '姓名',
    '手机号',
    '性别',
    '部门',
    '驿站',
    '角色',
    '状态',
    '入职日期',
    '最后登录时间',
    '创建时间',
    '备注'
  ]
  const genderText = { 0: '未知', 1: '男', 2: '女' }
  const roleText = { ADMIN: '管理员', STATION_ADMIN: '站长', STAFF: '员工' }
  const bodyRows = rows
    .slice()
    .sort((a, b) => b.id - a.id)
    .map((e) => [
      e.username,
      e.real_name,
      e.phone,
      genderText[e.gender] || '未知',
      deptName(e.dept_id) || '',
      stationName(e.station_id) || '',
      roleText[e.role] || e.role,
      e.status === 1 ? '启用' : '禁用',
      e.entry_date,
      e.last_login_time,
      e.create_time,
      e.remark
    ])

  const filename = `员工数据_${formatDate(new Date()).replace(/-/g, '')}.csv`
  return {
    code: CODE.SUCCESS,
    message: 'success',
    data: toCsvBlob([header, ...bodyRows]),
    headers: { 'content-type': CSV_TYPE, 'content-disposition': csvDisposition(filename) }
  }
}

export const employeeRoutes = [
  { method: 'get', path: '/employees', roles: ['ADMIN'], handler: list },
  { method: 'get', path: '/employees/import-template', roles: ['ADMIN'], handler: importTemplate },
  { method: 'get', path: '/employees/export', roles: ['ADMIN'], handler: exportEmployees },
  { method: 'post', path: '/employees/import', roles: ['ADMIN'], handler: importEmployees },
  { method: 'post', path: '/employees', roles: ['ADMIN'], handler: create },
  { method: 'put', path: '/employees/:id/status', roles: ['ADMIN'], handler: updateStatus },
  { method: 'put', path: '/employees/:id/password/reset', roles: ['ADMIN'], handler: resetPassword },
  { method: 'put', path: '/employees/:id', roles: ['ADMIN'], handler: update },
  { method: 'delete', path: '/employees/:id', roles: ['ADMIN'], handler: remove },
  { method: 'get', path: '/employees/:id', roles: ['ADMIN'], handler: detail }
]
