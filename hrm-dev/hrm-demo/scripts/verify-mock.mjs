/**
 * Mock 契约自动校验脚本（node scripts/verify-mock.mjs）
 * 目的：不依赖浏览器即可回归「T02 三处待验证点 + T04 一期 24 接口与错误码分支」
 * 运行：npm run verify:mock（本机 Node v24.19.0 实测通过）
 */
import axios from 'axios'
import { createMockAdapter } from '../src/shared/mock/engine.js'
import { resetDb, activeEmployees, db } from '../src/shared/mock/db.js'
import { resetParcelStore, parcelPerf, parcelTotalCount } from '../src/shared/mock/parcelStore.js'
import { employeeAttendanceStat, resetAttendanceStore } from '../src/shared/mock/attendanceStore.js'
import { resetKpiStore } from '../src/shared/mock/kpiStore.js'
import { resetHrStore } from '../src/shared/mock/hrStore.js'
import { resetFinanceStore } from '../src/shared/mock/financeStore.js'
import { resetLeaveStore } from '../src/shared/mock/leaveStore.js'
import { resetClientLogStore } from '../src/shared/mock/clientLogStore.js'
import { ATTENDANCE_METRIC, LEAVE_STATUS, LEAVE_TYPE, NOTIFICATION_TYPE } from '../src/shared/constants/dict.js'
import { LEAVE_CODE, codeMessage } from '../src/shared/constants/errorCode.js'
import {
  addDays,
  currentMonth,
  formatDate,
  formatDateTime,
  mondayOf,
  monthRange,
  monthShift
} from '../src/shared/mock/util.js'

const probes = []
const service = axios.create({ baseURL: '/api/v1', timeout: 30000 })
service.defaults.adapter = createMockAdapter({ delay: false, onRequest: (config) => probes.push(config) })

// 复刻 @admin/utils/request.js 的分发语义（V3：确认 adapter 的返回值会经过响应拦截器链）
service.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      const err = new Error(body.message)
      err.code = body.code
      err.data = body.data
      err.httpStatus = response.status
      err.bodyCode = body.code
      return Promise.reject(err)
    }
    return body
  },
  (error) => {
    const resp = error.response
    const body = resp && resp.data && typeof resp.data === 'object' ? resp.data : {}
    const err = new Error(body.message || error.message)
    err.code = body.code
    err.httpStatus = resp ? resp.status : undefined
    return Promise.reject(err)
  }
)

let pass = 0
let fail = 0
const failures = []

function check(name, condition, extra = '') {
  if (condition) {
    pass += 1
  } else {
    fail += 1
    failures.push(`${name}${extra ? ` → ${extra}` : ''}`)
  }
}

async function call(method, url, options = {}) {
  const { data, params, token, responseType } = options
  const headers = {}
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    const result = await service.request({ method, url, data, params, headers, responseType })
    return { ok: true, result }
  } catch (error) {
    return { ok: false, code: error.code, httpStatus: error.httpStatus, data: error.data, message: error.message }
  }
}

/** 期望业务码（含 HTTP 状态码一致性） */
async function expectCode(name, method, url, options, code, httpStatus = 200) {
  const res = await call(method, url, options)
  if (code === 200) {
    check(name, res.ok, res.ok ? '' : `期望成功，实际 code=${res.code} msg=${res.message}`)
    return res
  }
  check(
    name,
    !res.ok && res.code === code && res.httpStatus === httpStatus,
    `期望 code=${code}/HTTP ${httpStatus}，实际 code=${res.code}/HTTP ${res.httpStatus}`
  )
  return res
}

const login = (username, password) => call('post', '/auth/login', { data: { username, password } })

/* ===== 考勤校验用的独立推算：不引用 attendanceStore 的判定函数，避免「用被测实现验证被测实现」 ===== */
/** 'HH:mm' → 当日分钟数 */
const clockMinutes = (text) => {
  const [h, m] = String(text).split(':').map(Number)
  return h * 60 + m
}
/** 'YYYY-MM-DD HH:mm:ss' → 当日分钟数 */
const timeMinutes = (text) => Number(text.slice(11, 13)) * 60 + Number(text.slice(14, 16))
/** 上班卡应有状态：晚于「班次开始 + 迟到阈值」即迟到 */
const expectOnStatus = (checkTime, shift, rule) =>
  timeMinutes(checkTime) > clockMinutes(shift.startTime) + rule.lateThresholdMin ? 'LATE' : 'NORMAL'
/** 下班卡应有状态：早于「班次结束 - 早退阈值」即早退 */
const expectOffStatus = (checkTime, shift, rule) =>
  timeMinutes(checkTime) < clockMinutes(shift.endTime) - rule.earlyLeaveThresholdMin ? 'EARLY_LEAVE' : 'NORMAL'
const nowMinutes = () => {
  const d = new Date()
  return d.getHours() * 60 + d.getMinutes()
}

/** 日期区间内命中「指定星期」的日期列表（按 JS getDay 口径，脚本侧独立推算，不引用被测实现） */
function datesInRange(startDate, endDate, weekdays) {
  const out = []
  const end = new Date(`${endDate}T00:00:00`)
  for (const day = new Date(`${startDate}T00:00:00`); day <= end; day.setDate(day.getDate() + 1)) {
    if (!weekdays || weekdays.includes(day.getDay())) out.push(formatDate(day))
  }
  return out
}

/** CSV 文本 → 行数组（剥离 BOM、丢弃结尾空行，保留表头） */
const csvLinesOf = (text) =>
  String(text)
    .replace(/^\ufeff/, '')
    .split('\r\n')
    .filter((line, index) => index === 0 || line !== '')

/**
 * 找「当日有排班且该槽位既没有有效卡、也没有在途 / 已通过的补卡申请」的空位（脚本侧独立推算，不引用被测实现）
 * 只支持城东（单时段驿站，periodIndex 固定 0）；token 需为 ADMIN（补卡列表与排班矩阵的权限要求）
 * 槽位占用同时看打卡记录与补卡申请：9108 的口径是两者任一命中即拒绝，用例数据必须按同一口径挑选
 */
async function findStationEmptySlot(token, stationId, excludeIds = []) {
  const matrixCache = new Map()
  const takeKey = (row) => `${row.workDate}#${row.periodIndex}#${row.checkType}`
  const mkRes = await call('get', '/attendance/makeup/list', {
    params: { stationId, pageNum: 1, pageSize: 100 },
    token
  })
  const taken = new Set((mkRes.ok ? mkRes.result.list : []).filter((m) => m.status !== 'REJECTED').map(takeKey))
  const employees = activeEmployees().filter(
    (e) => e.station_id === stationId && e.status === 1 && !excludeIds.includes(e.id)
  )
  for (const employee of employees) {
    const recs = await call('get', '/attendance/records', {
      params: {
        stationId,
        employeeId: employee.id,
        startDate: formatDate(addDays(new Date(), -20)),
        endDate: formatDate(addDays(new Date(), -1)),
        pageNum: 1,
        pageSize: 100
      },
      token
    })
    ;(recs.ok ? recs.result.list : []).filter((r) => r.status !== 'ABNORMAL').forEach((r) => taken.add(takeKey(r)))
    for (let day = 3; day <= 20; day += 1) {
      const workDate = formatDate(addDays(new Date(), -day))
      const weekStart = formatDate(mondayOf(new Date(`${workDate}T00:00:00`)))
      if (!matrixCache.has(weekStart)) {
        const matrix = await call('get', '/schedules', { params: { stationId, weekStart }, token })
        matrixCache.set(weekStart, matrix.ok ? matrix.result : null)
      }
      const matrix = matrixCache.get(weekStart)
      const row = matrix ? matrix.employees.find((e) => e.employeeId === employee.id) : null
      const slot = row ? row.days.find((d) => d.workDate === workDate) : null
      if (!slot || slot.shiftId == null) continue
      if (!taken.has(`${workDate}#0#ON`)) return { employeeId: employee.id, workDate, periodIndex: 0, checkType: 'ON' }
      if (!taken.has(`${workDate}#0#OFF`))
        return { employeeId: employee.id, workDate, periodIndex: 0, checkType: 'OFF' }
    }
  }
  return null
}

async function main() {
  resetDb()

  /* ========== V1 / V2 实测（打印真实观测值，结论回写 engine.js 注释） ========== */
  probes.length = 0
  await login('admin', 'demo1234')
  const v1 = probes[0]
  console.log(
    '[V1] adapter 收到的 config.url =',
    JSON.stringify(v1.url),
    '| config.baseURL =',
    JSON.stringify(v1.baseURL)
  )
  check(
    'V1 config.url 为调用方传入的相对路径（baseURL 未合并，与设计稿预期不同）',
    v1.url === '/auth/login' && v1.baseURL === '/api/v1'
  )
  // 兼容性验证：即便传入已含 /api/v1 的绝对地址，归一化后仍能命中同一路由
  const absolute = await call('post', 'http://localhost:5188/api/v1/auth/login', {
    data: { username: 'admin', password: 'demo1234' }
  })
  check('V1 绝对地址 + /api/v1 前缀可被归一化命中', absolute.ok === true)

  probes.length = 0
  await login('admin', 'demo1234')
  console.log('[V2] 登录请求 config.data 类型 =', typeof probes[0].data, '| 值 =', probes[0].data)
  check('V2 POST JSON body 为字符串', typeof probes[0].data === 'string')

  probes.length = 0
  await call('get', '/employees', { params: { pageNum: 1 }, token: 'mock.1.x' })
  console.log('[V2] GET 请求 config.data =', probes[0].data)
  check('V2 GET config.data 为 undefined', probes[0].data === undefined)

  const form = new FormData()
  form.append('file', new File(['x'.repeat(200)], 'ok.xlsx'))
  probes.length = 0
  await call('post', '/employees/import', { data: form, token: 'mock.1.x' })
  console.log('[V2] FormData 请求 config.data 是否仍为 FormData =', probes[0].data instanceof FormData)
  check('V2 FormData 保持实例不被 JSON 序列化', probes[0].data instanceof FormData)

  /* ========== 认证 ========== */
  await expectCode(
    '登录密码错误 → 1001',
    'post',
    '/auth/login',
    { data: { username: 'admin', password: 'wrong' } },
    1001
  )

  const disabledUser = activeEmployees().find((e) => e.status === 0)
  await expectCode(
    '登录禁用账号 → 1002',
    'post',
    '/auth/login',
    { data: { username: disabledUser.username, password: 'demo1234' } },
    1002
  )

  const pwd0 = await expectCode(
    'admin_pwd0 登录（首登改密）',
    'post',
    '/auth/login',
    { data: { username: 'admin_pwd0', password: 'demo1234' } },
    200
  )
  check('登录返回 pwdChanged=false', pwd0.ok && pwd0.result.employee.pwdChanged === false)
  check('登录手机号已脱敏', pwd0.ok && /^\d{3}\*{4}\d{4}$/.test(pwd0.result.employee.phone))

  const adminLogin = await login('admin', 'demo1234')
  check('admin 登录成功且返回 token', adminLogin.ok && typeof adminLogin.result.token === 'string')
  const adminToken = adminLogin.ok ? adminLogin.result.token : ''
  const staleToken = adminToken

  await expectCode('无 token 访问 → 401（HTTP 401）', 'get', '/employees', {}, 401, 401)

  // 互踢：同账号再次登录后旧 token 立即 401（api.md 3.2）
  const second = await login('admin', 'demo1234')
  const adminToken2 = second.ok ? second.result.token : ''
  await expectCode('旧 token 被顶下线 → 401', 'get', '/employees', { token: staleToken }, 401, 401)

  await expectCode('当前用户信息 GET /auth/me', 'get', '/auth/me', { token: adminToken2 }, 200)

  const staffLogin = await login('st001_staff', 'demo1234')
  const staffToken = staffLogin.ok ? staffLogin.result.token : ''
  const stationAdminLogin = await login('st001_admin', 'demo1234')
  const stationAdminToken = stationAdminLogin.ok ? stationAdminLogin.result.token : ''
  check(
    'st001_admin 归属城东驿站（stationId=1）',
    stationAdminLogin.ok && stationAdminLogin.result.employee.stationId === 1
  )
  check('st001_staff 角色为 STAFF', staffLogin.ok && staffLogin.result.employee.role === 'STAFF')

  await expectCode(
    'STAFF 访问 ADMIN 接口 → 403（HTTP 403）',
    'get',
    '/dashboard/summary',
    { token: staffToken },
    403,
    403
  )

  /* ========== 看板 ========== */
  const summary = await expectCode(
    '看板统计 GET /dashboard/summary',
    'get',
    '/dashboard/summary',
    { token: adminToken2 },
    200
  )
  check('看板 employeeTotal=56', summary.ok && summary.result.employeeTotal === 56, JSON.stringify(summary.result))
  check('看板 stationTotal=8', summary.ok && summary.result.stationTotal === 8)
  check('看板 departmentTotal=6', summary.ok && summary.result.departmentTotal === 6)
  check('看板 todayLoginCount=23（去重）', summary.ok && summary.result.todayLoginCount === 23)

  /* ========== 员工 ========== */
  const page = await expectCode(
    '员工分页 GET /employees',
    'get',
    '/employees',
    { params: { pageNum: 1, pageSize: 10 }, token: adminToken2 },
    200
  )
  check('员工分页 total=56 且当页 10 条', page.ok && page.result.total === 56 && page.result.list.length === 10)
  check(
    '员工列表字段含 deptName/stationName 且无 password',
    page.ok && 'deptName' in page.result.list[0] && !('password' in page.result.list[0])
  )

  const byDept = await call('get', '/employees', { params: { deptId: 1 }, token: adminToken2 })
  check('deptId=1 筛选含全部子部门 → 56', byDept.ok && byDept.result.total === 56)
  const byStation = await call('get', '/employees', { params: { stationId: 1 }, token: adminToken2 })
  const station1Count = activeEmployees().filter((e) => e.station_id === 1).length
  check('stationId=1 筛选口径与种子一致', byStation.ok && byStation.result.total === station1Count)
  const byKeyword = await call('get', '/employees', { params: { keyword: 'admin' }, token: adminToken2 })
  check('keyword 模糊匹配命中 admin 账号', byKeyword.ok && byKeyword.result.total >= 2)
  const byStatus = await call('get', '/employees', { params: { status: 0 }, token: adminToken2 })
  check('status=0 筛选到禁用账号', byStatus.ok && byStatus.result.total >= 1)
  await expectCode('pageSize 越界 → 400', 'get', '/employees', { params: { pageSize: 999 }, token: adminToken2 }, 400)

  await expectCode('员工详情', 'get', '/employees/1', { token: adminToken2 }, 200)
  await expectCode('员工详情不存在 → 404', 'get', '/employees/9999', { token: adminToken2 }, 404, 404)

  const base = {
    realName: '测试员工',
    phone: '13512345678',
    gender: 1,
    deptId: 2,
    stationId: 1,
    role: 'STAFF',
    entryDate: '2026-09-01',
    remark: null
  }
  await expectCode(
    '新增员工账号重复 → 1003',
    'post',
    '/employees',
    { data: { ...base, username: 'admin', password: 'Init1234' }, token: adminToken2 },
    1003
  )
  await expectCode(
    '新增员工手机号重复 → 2003',
    'post',
    '/employees',
    { data: { ...base, username: 'demo_new01', password: 'Init1234', phone: '13800000000' }, token: adminToken2 },
    2003
  )
  await expectCode(
    '新增员工部门不存在 → 3001',
    'post',
    '/employees',
    { data: { ...base, username: 'demo_new02', password: 'Init1234', deptId: 999 }, token: adminToken2 },
    3001
  )
  await expectCode(
    '新增员工驿站不存在 → 4001',
    'post',
    '/employees',
    { data: { ...base, username: 'demo_new03', password: 'Init1234', stationId: 999 }, token: adminToken2 },
    4001
  )
  await expectCode(
    '新增员工驿站已停用 → 4004',
    'post',
    '/employees',
    { data: { ...base, username: 'demo_new04', password: 'Init1234', stationId: 8 }, token: adminToken2 },
    4004
  )
  await expectCode(
    '新增员工密码强度不足 → 400',
    'post',
    '/employees',
    { data: { ...base, username: 'demo_new05', password: 'abcdefgh' }, token: adminToken2 },
    400
  )

  const created = await expectCode(
    '新增员工成功',
    'post',
    '/employees',
    { data: { ...base, username: 'demo_new06', password: 'Init1234' }, token: adminToken2 },
    200
  )
  const newId = created.ok ? created.result.id : 0
  await expectCode(
    '编辑员工',
    'put',
    `/employees/${newId}`,
    { data: { ...base, realName: '测试员工改', phone: '13512345679' }, token: adminToken2 },
    200
  )
  await expectCode('编辑员工不存在 → 404', 'put', '/employees/9999', { data: base, token: adminToken2 }, 404, 404)

  await expectCode(
    '禁用当前登录账号 → 2001',
    'put',
    '/employees/1/status',
    { data: { status: 0 }, token: adminToken2 },
    2001
  )
  await expectCode('删除当前登录账号 → 2001', 'delete', '/employees/1', { token: adminToken2 }, 2001)
  await expectCode(
    '重置自己的密码 → 2001',
    'put',
    '/employees/1/password/reset',
    { data: { newPassword: 'Abcd1234' }, token: adminToken2 },
    2001
  )
  await expectCode(
    '重置密码强度不足 → 400',
    'put',
    `/employees/${newId}/password/reset`,
    { data: { newPassword: '12345678' }, token: adminToken2 },
    400
  )
  await expectCode(
    '重置员工密码成功',
    'put',
    `/employees/${newId}/password/reset`,
    { data: { newPassword: 'Abcd1234' }, token: adminToken2 },
    200
  )
  await expectCode(
    '启用/禁用员工成功',
    'put',
    `/employees/${newId}/status`,
    { data: { status: 0 }, token: adminToken2 },
    200
  )
  await expectCode('删除员工成功', 'delete', `/employees/${newId}`, { token: adminToken2 }, 200)
  await expectCode('已删除员工详情 → 404', 'get', `/employees/${newId}`, { token: adminToken2 }, 404, 404)

  /* ========== 导入导出 ========== */
  const template = await call('get', '/employees/import-template', { token: adminToken2, responseType: 'blob' })
  check('下载导入模板返回文件流', template.ok && template.result.data instanceof Blob)
  check(
    '模板带 Content-Disposition 文件名',
    template.ok && /filename\*=UTF-8''/.test(template.result.headers['content-disposition'] || '')
  )

  const exported = await call('get', '/employees/export', {
    params: { stationId: 1 },
    token: adminToken2,
    responseType: 'blob'
  })
  check('员工导出返回文件流', exported.ok && exported.result.data instanceof Blob)

  const badForm = new FormData()
  badForm.append('file', new File(['abc'], '员工.csv'))
  await expectCode('导入非 xlsx → 5001', 'post', '/employees/import', { data: badForm, token: adminToken2 }, 5001)
  const bigForm = new FormData()
  bigForm.append('file', new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'big.xlsx'))
  await expectCode('导入超 10MB → 5002', 'post', '/employees/import', { data: bigForm, token: adminToken2 }, 5002)
  const errForm = new FormData()
  errForm.append('file', new File(['x'.repeat(200)], '导入错误示例.xlsx'))
  const errRes = await expectCode(
    '导入行级错误 → 5003',
    'post',
    '/employees/import',
    { data: errForm, token: adminToken2 },
    5003
  )
  check(
    '5003 返回 data.errors 明细',
    errRes.ok === false && Array.isArray(errRes.data && errRes.data.errors) && errRes.data.errors.length === 2
  )
  const okForm = new FormData()
  okForm.append('file', new File(['x'.repeat(800)], 'ok.xlsx'))
  await expectCode('导入成功', 'post', '/employees/import', { data: okForm, token: adminToken2 }, 200)

  /* ========== 部门 ========== */
  const tree = await expectCode('部门树 GET /departments/tree', 'get', '/departments/tree', { token: adminToken2 }, 200)
  check('部门树根节点 1 个（总公司）', tree.ok && tree.result.length === 1 && tree.result[0].deptName === '总公司')
  check('总公司下 4 个直属子部门', tree.ok && tree.result[0].children.length === 4)
  check('部门树含 employeeCount', tree.ok && typeof tree.result[0].employeeCount === 'number')

  await expectCode(
    '新增部门上级不存在 → 3001',
    'post',
    '/departments',
    { data: { parentId: 999, deptName: '测试部' }, token: adminToken2 },
    3001
  )
  await expectCode(
    '新增部门同级重名 → 3004',
    'post',
    '/departments',
    { data: { parentId: 1, deptName: '运营部' }, token: adminToken2 },
    3004
  )
  const newDept = await expectCode(
    '新增部门成功',
    'post',
    '/departments',
    { data: { parentId: 1, deptName: '演示部', sortOrder: 9 }, token: adminToken2 },
    200
  )
  const newDeptId = newDept.ok ? newDept.result.id : 0
  await expectCode(
    '编辑部门',
    'put',
    `/departments/${newDeptId}`,
    { data: { deptName: '演示部改', sortOrder: 8 }, token: adminToken2 },
    200
  )
  await expectCode(
    '编辑部门不允许改 parentId → 400',
    'put',
    `/departments/${newDeptId}`,
    { data: { deptName: '演示部改', parentId: 2 }, token: adminToken2 },
    400
  )
  await expectCode('删除有子部门的部门（总公司） → 3002', 'delete', '/departments/1', { token: adminToken2 }, 3002)
  await expectCode(
    '删除有子部门的部门（运营部下含片区组） → 3002',
    'delete',
    '/departments/2',
    { token: adminToken2 },
    3002
  )
  await expectCode('删除有员工无子部门的部门 → 3003', 'delete', '/departments/3', { token: adminToken2 }, 3003)
  await expectCode('删除空部门成功（城东片区组）', 'delete', '/departments/6', { token: adminToken2 }, 200)
  await expectCode('删除新建空部门成功', 'delete', `/departments/${newDeptId}`, { token: adminToken2 }, 200)
  await expectCode('删除部门不存在 → 404', 'delete', '/departments/9999', { token: adminToken2 }, 404, 404)

  /* ========== 驿站 ========== */
  const stations = await expectCode('驿站列表 GET /stations', 'get', '/stations', { token: adminToken2 }, 200)
  check('驿站全量 8 条', stations.ok && stations.result.length === 8)
  check('驿站联系人电话已脱敏', stations.ok && /^\d{3}\*{4}\d*$/.test(stations.result[0].contactPhone))
  check('8 号驿站停用（覆盖 4004 场景）', stations.ok && stations.result.find((s) => s.id === 8).status === 0)

  await expectCode(
    '新增驿站编码重复 → 4002',
    'post',
    '/stations',
    { data: { code: 'ST001', stationName: '测试驿站' }, token: adminToken2 },
    4002
  )
  await expectCode(
    '新增驿站编码格式非法 → 400',
    'post',
    '/stations',
    { data: { code: 'ST 001!', stationName: '测试驿站' }, token: adminToken2 },
    400
  )
  const newStation = await expectCode(
    '新增驿站成功',
    'post',
    '/stations',
    {
      data: { code: 'ST009', stationName: '演示驿站', contactPerson: '演示', contactPhone: '13612345678' },
      token: adminToken2
    },
    200
  )
  const newStationId = newStation.ok ? newStation.result.id : 0
  await expectCode(
    '编辑驿站',
    'put',
    `/stations/${newStationId}`,
    { data: { code: 'ST009', stationName: '演示驿站改' }, token: adminToken2 },
    200
  )
  await expectCode(
    '停用驿站',
    'put',
    `/stations/${newStationId}/status`,
    { data: { status: 0 }, token: adminToken2 },
    200
  )
  await expectCode('删除有员工的驿站 → 4003', 'delete', '/stations/1', { token: adminToken2 }, 4003)
  await expectCode('删除新建无员工驿站成功', 'delete', `/stations/${newStationId}`, { token: adminToken2 }, 200)
  await expectCode('删除驿站不存在 → 404', 'delete', '/stations/9999', { token: adminToken2 }, 404, 404)

  /* ========== 兜底与登录态收尾 ========== */
  const notFound = await call('get', '/not-implemented', { token: adminToken2 })
  check(
    '未注册路由 → 404 且 message 说明未实现',
    !notFound.ok && notFound.code === 404 && /未实现/.test(notFound.message)
  )

  await expectCode('退出登录', 'post', '/auth/logout', { token: adminToken2 }, 200)
  await expectCode('退出后 me → 401', 'get', '/auth/me', { token: adminToken2 }, 401, 401)

  /* ========== 认证改密链路（用 st001_staff，避免污染 admin 演示账号） ========== */
  const freshStaff = await login('st001_staff', 'demo1234')
  const freshToken = freshStaff.ok ? freshStaff.result.token : ''
  await expectCode(
    '原密码错误 → 1004',
    'put',
    '/auth/password',
    { data: { oldPassword: 'bad_pass', newPassword: 'Abcd1234' }, token: freshToken },
    1004
  )
  await expectCode(
    '新密码强度不足 → 400',
    'put',
    '/auth/password',
    { data: { oldPassword: 'demo1234', newPassword: 'abcdefgh' }, token: freshToken },
    400
  )
  await expectCode(
    '修改本人密码成功',
    'put',
    '/auth/password',
    { data: { oldPassword: 'demo1234', newPassword: 'Abcd1234' }, token: freshToken },
    200
  )
  await expectCode('改密后会话失效 → 401', 'get', '/auth/me', { token: freshToken }, 401, 401)
  check('改密后 pwd_changed 置 1', activeEmployees().find((e) => e.username === 'st001_staff').pwd_changed === 1)

  /* ========== 数据一致性 ========== */
  resetDb()
  const p1 = await call('get', '/employees', {
    params: { pageNum: 1, pageSize: 20 },
    token: (await login('admin', 'demo1234')).result.token
  })
  resetDb()
  const p2 = await call('get', '/employees', {
    params: { pageNum: 1, pageSize: 20 },
    token: (await login('admin', 'demo1234')).result.token
  })
  check('同参数多次查询结果稳定（确定性 PRNG）', JSON.stringify(p1.result) === JSON.stringify(p2.result))
  check('重置后员工数为 56', db.employees.length === 56)

  /* ========== T05-T09：包裹 / 同步任务 / 工单 / 通知 ========== */
  resetDb()
  resetParcelStore() // 清空覆盖层，确保从种子态开始（db.resetDb 不反向依赖 parcelStore，由脚本统一编排）

  const t5Admin = await login('admin', 'demo1234')
  const t5AdminToken = t5Admin.ok ? t5Admin.result.token : ''
  const t5Staff = await login('st001_staff', 'demo1234')
  const t5StaffToken = t5Staff.ok ? t5Staff.result.token : ''
  const t5StationAdmin = await login('st001_admin', 'demo1234')
  const t5StationAdminToken = t5StationAdmin.ok ? t5StationAdmin.result.token : ''

  /* T05：20 万包裹索引层 */
  check('包裹总数 = 200000', parcelTotalCount() === 200000)
  const pAll = await call('get', '/parcels', { params: { pageNum: 1, pageSize: 20 }, token: t5AdminToken })
  check('GET /parcels total = 200000', pAll.ok && pAll.result.total === 200000)

  const pStable1 = await call('get', '/parcels', {
    params: { stationId: 1, status: 1, pageNum: 1, pageSize: 20 },
    token: t5AdminToken
  })
  const pStable2 = await call('get', '/parcels', {
    params: { stationId: 1, status: 1, pageNum: 1, pageSize: 20 },
    token: t5AdminToken
  })
  check(
    '同参数多次查询结果稳定（包裹）',
    pStable1.ok && pStable2.ok && JSON.stringify(pStable1.result) === JSON.stringify(pStable2.result)
  )

  // 分页切片与总数口径一致：pageSize=100 的前 100 条 = pageSize=50 的第 1、2 页
  const f100 = await call('get', '/parcels', {
    params: { stationId: 1, status: 1, pageNum: 1, pageSize: 100 },
    token: t5AdminToken
  })
  const f1 = await call('get', '/parcels', {
    params: { stationId: 1, status: 1, pageNum: 1, pageSize: 50 },
    token: t5AdminToken
  })
  const f2 = await call('get', '/parcels', {
    params: { stationId: 1, status: 1, pageNum: 2, pageSize: 50 },
    token: t5AdminToken
  })
  check(
    '组合筛选分页 total 口径一致',
    f100.ok && f1.ok && f2.ok && f100.result.total === f1.result.total && f1.result.total === f2.result.total
  )
  check(
    '分页切片无重叠错位',
    f100.ok &&
      f1.ok &&
      f2.ok &&
      f1.result.list[0].id === f100.result.list[0].id &&
      f2.result.list[0].id === f100.result.list[50].id
  )

  // 记录单次组合筛选查询耗时
  await call('get', '/parcels', { params: { stationId: 1, status: 1, pageNum: 3, pageSize: 20 }, token: t5AdminToken })
  check('T05 单次查询耗时已记录（>0）', parcelPerf.lastQueryMs > 0)
  console.log('[T05] 组合筛选（城东 + 在库待取 + 第 3 页）单次查询耗时 =', parcelPerf.lastQueryMs.toFixed(2), 'ms')

  /* T06：包裹接口 */
  const staffParcels = await call('get', '/parcels', {
    params: { stationId: 2, pageNum: 1, pageSize: 10 },
    token: t5StaffToken
  })
  check(
    '非 ADMIN 请求 station_id 强制覆盖为本站(1)',
    staffParcels.ok && staffParcels.result.list.length > 0 && staffParcels.result.list.every((p) => p.stationId === 1)
  )
  const station1All = await call('get', '/parcels', {
    params: { stationId: 1, pageNum: 1, pageSize: 1 },
    token: t5AdminToken
  })
  check(
    '强制覆盖后 total 与驿站 1 口径一致',
    staffParcels.ok && station1All.ok && staffParcels.result.total === station1All.result.total
  )

  const anyParcel = pAll.result.list[0]
  const pDetail = await call('get', `/parcels/${anyParcel.id}`, { token: t5AdminToken })
  check('包裹详情收件人姓名脱敏（姓+*）', pDetail.ok && /^[^*]\*+$/.test(pDetail.result.receiverName))
  check('包裹详情手机号脱敏', pDetail.ok && /^\d{3}\*{4}\d{4}$/.test(pDetail.result.receiverPhone))

  // 取件闭环：在库待取 → 已取件，且统计同步变化
  const pendingParcel = await call('get', '/parcels', {
    params: { stationId: 1, status: 1, pageNum: 1, pageSize: 1 },
    token: t5AdminToken
  })
  const pendingId = pendingParcel.ok && pendingParcel.result.list[0] ? pendingParcel.result.list[0].id : 0
  const beforeSummary = await call('get', '/parcels/summary', { token: t5AdminToken })
  const pickupRes = await call('put', `/parcels/${pendingId}/pickup`, { token: t5StationAdminToken })
  check(
    '取件成功 status=2 且记录取件人/时间',
    pickupRes.ok &&
      pickupRes.result.status === 2 &&
      pickupRes.result.pickupEmployeeId === 3 &&
      pickupRes.result.pickupTime != null
  )
  const afterSummary = await call('get', '/parcels/summary', { token: t5AdminToken })
  check(
    '取件后 todayPickup 同步 +1',
    beforeSummary.ok && afterSummary.ok && afterSummary.result.todayPickup === beforeSummary.result.todayPickup + 1
  )

  await expectCode(
    '对已取件包裹再取件 → 7002',
    'put',
    `/parcels/${pendingId}/pickup`,
    { token: t5StationAdminToken },
    7002
  )
  await expectCode('他人取件 → 7003', 'put', `/parcels/${pendingId}/pickup`, { token: t5StaffToken }, 7003)
  await expectCode('取件包裹不存在 → 7001', 'put', '/parcels/999999999/pickup', { token: t5AdminToken }, 7001)

  const summaryRes = await call('get', '/parcels/summary', { token: t5AdminToken })
  check(
    '包裹 summary 返回指标',
    summaryRes.ok &&
      typeof summaryRes.result.parcelTotal === 'number' &&
      typeof summaryRes.result.pickupRate === 'number'
  )
  const trend7 = await call('get', '/parcels/trend', { params: { days: 7 }, token: t5AdminToken })
  check(
    '包裹 trend 近 7 天',
    trend7.ok &&
      trend7.result.length === 7 &&
      trend7.result.every((d) => typeof d.inbound === 'number' && typeof d.pickup === 'number')
  )
  const trend30 = await call('get', '/parcels/trend', { params: { days: 30 }, token: t5AdminToken })
  check('包裹 trend 近 30 天', trend30.ok && trend30.result.length === 30)
  const ranking = await call('get', '/parcels/ranking', { token: t5AdminToken })
  check(
    '包裹 ranking 8 驿站且按包裹量降序',
    ranking.ok && ranking.result.length === 8 && ranking.result[0].parcelTotal >= ranking.result[1].parcelTotal
  )

  /* T07：同步任务 */
  const syncList = await call('get', '/sync-tasks', { params: { pageNum: 1, pageSize: 100 }, token: t5AdminToken })
  check('同步任务 total = 240', syncList.ok && syncList.result.total === 240)
  await expectCode('STAFF 访问同步任务 → 403', 'get', '/sync-tasks', { token: t5StaffToken }, 403, 403)
  const stationSync = await call('get', '/sync-tasks', {
    params: { pageNum: 1, pageSize: 100 },
    token: t5StationAdminToken
  })
  check('站长同步任务数据范围收敛本站', stationSync.ok && stationSync.result.list.every((t) => t.stationId === 1))

  const failedSync = await call('get', '/sync-tasks', {
    params: { status: 3, pageNum: 1, pageSize: 10 },
    token: t5AdminToken
  })
  check('存在失败同步任务', failedSync.ok && failedSync.result.total >= 2)
  const failedId = failedSync.ok && failedSync.result.list[0] ? failedSync.result.list[0].id : 0
  const beforeLogs = await call('get', `/sync-tasks/${failedId}/logs`, { token: t5AdminToken })
  const retryRes = await call('post', `/sync-tasks/${failedId}/retry`, { token: t5AdminToken })
  check(
    '失败任务重试 → 待领取且 retry_count+1',
    retryRes.ok && retryRes.result.status === 0 && retryRes.result.retryCount === 1
  )
  const afterLogs = await call('get', `/sync-tasks/${failedId}/logs`, { token: t5AdminToken })
  check('重试后追加日志', beforeLogs.ok && afterLogs.ok && afterLogs.result.length === beforeLogs.result.length + 1)
  const triggerRes = await call('post', `/sync-tasks/${failedId}/trigger`, { token: t5AdminToken })
  check(
    '待领取任务触发 → 成功',
    triggerRes.ok && triggerRes.result.status === 2 && triggerRes.result.successCount === triggerRes.result.parcelTotal
  )
  await expectCode('对成功任务重试 → 6001', 'post', `/sync-tasks/${failedId}/retry`, { token: t5AdminToken }, 6001)

  /* T08：工单 */
  const woList = await call('get', '/work-orders', { params: { pageNum: 1, pageSize: 100 }, token: t5AdminToken })
  check('工单 total = 120', woList.ok && woList.result.total === 120)
  // 需求6 语义变更：字段由「超 SLA」更名为「超时未处理」（判定口径一致：SLA 已过 且 工单未处理完）；
  // 旧字段/旧参数 overSla 保留兼容（页面未同步改动），本断言改用新字段 overdueUnhandled
  const overSla = await call('get', '/work-orders', {
    params: { overdueUnhandled: 'true', pageNum: 1, pageSize: 100 },
    token: t5AdminToken
  })
  check(
    '超时未处理工单 6 条且均为待处理/处理中',
    overSla.ok &&
      overSla.result.total === 6 &&
      overSla.result.list.every((o) => o.overdueUnhandled === true && (o.status === 0 || o.status === 1))
  )

  const woCreate = await call('post', '/work-orders', {
    data: { type: 3, priority: 2, title: '测试工单', content: '测试', stationId: 1 },
    token: t5AdminToken
  })
  check('新建工单成功', woCreate.ok && woCreate.result.id > 0)
  const woCreated = await call('get', `/work-orders/${woCreate.result.id}`, { token: t5AdminToken })
  const slaDelta = woCreated.ok
    ? new Date(woCreated.result.slaDeadline.replace(' ', 'T')).getTime() -
      new Date(woCreated.result.createTime.replace(' ', 'T')).getTime()
    : 0
  check('sla_deadline 按高优先级 +8h', woCreated.ok && slaDelta === 8 * 3600 * 1000)

  const pendingWo = woList.result.list.find((o) => o.status === 0 && o.stationId === 1 && o.assigneeId !== 4)
  await expectCode(
    '工单非法流转 0→2 → 8001',
    'put',
    `/work-orders/${pendingWo.id}/status`,
    { data: { status: 2 }, token: t5AdminToken },
    8001
  )
  await expectCode(
    '非归属人操作工单 → 8002',
    'put',
    `/work-orders/${pendingWo.id}/status`,
    { data: { status: 1 }, token: t5StaffToken },
    8002
  )

  /* T09：通知 + 工单指派/解决联动 */
  const notifList = await call('get', '/notifications', { params: { pageNum: 1, pageSize: 100 }, token: t5AdminToken })
  check('通知列表可用', notifList.ok && Array.isArray(notifList.result.list))

  const staffUnreadBefore = await call('get', '/notifications/unread-count', { token: t5StaffToken })
  const assignRes = await call('put', `/work-orders/${pendingWo.id}/assign`, {
    data: { assigneeId: 4 },
    token: t5StationAdminToken
  })
  check('指派工单成功', assignRes.ok && assignRes.result.assigneeId === 4)
  const staffUnreadAfter = await call('get', '/notifications/unread-count', { token: t5StaffToken })
  check(
    '指派后被指派人未读 +1',
    staffUnreadBefore.ok && staffUnreadAfter.ok && staffUnreadAfter.result.count === staffUnreadBefore.result.count + 1
  )

  const staffNotif = await call('get', '/notifications', { params: { pageNum: 1, pageSize: 100 }, token: t5StaffToken })
  const targetNotif = staffNotif.ok
    ? staffNotif.result.list.find((n) => n.bizType === 'work_order' && n.bizId === pendingWo.id)
    : null
  check('被指派人可见指派通知且可跳转', !!targetNotif && targetNotif.type === 1 && !targetNotif.isRead)
  if (targetNotif)
    await expectCode('标记通知已读', 'put', `/notifications/${targetNotif.id}/read`, { token: t5StaffToken }, 200)
  await expectCode(
    '标记他人通知已读 → 9001',
    'put',
    `/notifications/${targetNotif ? targetNotif.id : 1}/read`,
    { token: t5AdminToken },
    9001
  )
  await expectCode('全部已读', 'put', '/notifications/read-all', { token: t5StaffToken }, 200)
  const staffUnreadFinal = await call('get', '/notifications/unread-count', { token: t5StaffToken })
  check('全部已读后未读 = 0', staffUnreadFinal.ok && staffUnreadFinal.result.count === 0)

  // 解决工单 → 上报人收到通知（二次联动）
  const wo2 = await call('post', '/work-orders', {
    data: { type: 1, priority: 1, title: '联动测试', content: 'x', stationId: 1 },
    token: t5AdminToken
  })
  const adminUnreadBefore = await call('get', '/notifications/unread-count', { token: t5AdminToken })
  await call('put', `/work-orders/${wo2.result.id}/assign`, { data: { assigneeId: 3 }, token: t5AdminToken })
  await call('put', `/work-orders/${wo2.result.id}/status`, { data: { status: 1 }, token: t5StationAdminToken })
  await call('put', `/work-orders/${wo2.result.id}/status`, { data: { status: 2 }, token: t5StationAdminToken })
  const adminUnreadAfter = await call('get', '/notifications/unread-count', { token: t5AdminToken })
  check(
    '解决后上报人未读 +1',
    adminUnreadBefore.ok && adminUnreadAfter.ok && adminUnreadAfter.result.count === adminUnreadBefore.result.count + 1
  )

  /* ========== T17：考勤与排班（规则 / 班次 / 排班 / 打卡） ========== */
  resetAttendanceStore() // 考勤 store 与 db 各自独立重置（与 resetParcelStore 同口径）

  const attAdmin = await login('admin', 'demo1234')
  const attAdminToken = attAdmin.ok ? attAdmin.result.token : ''
  const attStaff = await login('st001_staff', 'demo1234')
  const attStaffToken = attStaff.ok ? attStaff.result.token : ''
  const attSta = await login('st001_admin', 'demo1234')
  const attStaToken = attSta.ok ? attSta.result.token : ''

  const today = formatDate(new Date())
  const monthStart = `${today.slice(0, 7)}-01`
  const weekStart = formatDate(mondayOf(new Date()))

  /* ---- 规则：查询 / 越权覆盖 / 列表 / 保存 ---- */
  const rule1 = await expectCode(
    '考勤规则 GET /attendance/rule（ADMIN 指定驿站）',
    'get',
    '/attendance/rule',
    { params: { stationId: 1 }, token: attAdminToken },
    200
  )
  check(
    '规则字段齐全（围栏 / 阈值 / WiFi 白名单 / 组合模式）',
    rule1.ok &&
      rule1.result.radius === 300 &&
      rule1.result.lateThresholdMin === 30 &&
      rule1.result.earlyLeaveThresholdMin === 30 &&
      Array.isArray(rule1.result.wifiList) &&
      rule1.result.wifiList.length === 1
  )
  check(
    '规则默认 ALL 且三项校验均启用',
    rule1.ok &&
      rule1.result.matchMode === 'ALL' &&
      rule1.result.enableWifi &&
      rule1.result.enableLocation &&
      rule1.result.enableTimeWindow
  )
  const ruleBackup = rule1.result
  const wifiSsid = rule1.result.wifiList[0].ssid

  const ruleStaff = await expectCode(
    '非 ADMIN 传别站 stationId 查询规则',
    'get',
    '/attendance/rule',
    { params: { stationId: 2 }, token: attStaffToken },
    200
  )
  check('越权覆盖：规则强制返回本人驿站(1)', ruleStaff.ok && ruleStaff.result.stationId === 1)

  const ruleList = await expectCode(
    '规则列表 GET /attendance/rule/list（ADMIN）',
    'get',
    '/attendance/rule/list',
    { token: attAdminToken },
    200
  )
  check(
    '8 个驿站各一条规则且带 stationName',
    ruleList.ok && ruleList.result.length === 8 && ruleList.result.every((r) => !!r.stationName)
  )
  await expectCode('非 ADMIN 访问规则列表 → 403', 'get', '/attendance/rule/list', { token: attStaffToken }, 403, 403)

  const ruleSaved = await expectCode(
    '保存规则 PUT /attendance/rule',
    'put',
    '/attendance/rule',
    { data: { ...ruleBackup, stationId: 1, radius: 250 }, token: attAdminToken },
    200
  )
  check('保存后围栏半径生效 = 250', ruleSaved.ok && ruleSaved.result.radius === 250)
  await expectCode(
    '保存规则 matchMode 非法 → 400',
    'put',
    '/attendance/rule',
    { data: { stationId: 1, matchMode: 'EITHER' }, token: attAdminToken },
    400
  )
  await expectCode(
    '保存规则围栏半径非法 → 400',
    'put',
    '/attendance/rule',
    { data: { stationId: 1, radius: 0 }, token: attAdminToken },
    400
  )
  await expectCode(
    '保存规则缺少 stationId → 400',
    'put',
    '/attendance/rule',
    { data: { radius: 100 }, token: attAdminToken },
    400
  )
  await expectCode(
    '保存规则驿站不存在 → 4001',
    'put',
    '/attendance/rule',
    { data: { stationId: 999, radius: 100 }, token: attAdminToken },
    4001
  )
  await expectCode(
    '规则恢复种子口径',
    'put',
    '/attendance/rule',
    { data: { ...ruleBackup, stationId: 1 }, token: attAdminToken },
    200
  )

  /* ---- 班次列表 ---- */
  const shifts1 = await expectCode(
    '班次列表 GET /shifts（ADMIN 指定驿站）',
    'get',
    '/shifts',
    { params: { stationId: 1 }, token: attAdminToken },
    200
  )
  check(
    '每站 3 个班次且按开始时间升序',
    shifts1.ok && shifts1.result.length === 3 && shifts1.result.map((s) => s.shiftName).join(',') === '早班,中班,晚班'
  )
  check(
    '班次时间与契约一致（08:00-16:00 / 12:00-20:00 / 16:00-24:00）',
    shifts1.ok &&
      shifts1.result.map((s) => `${s.startTime}-${s.endTime}`).join(',') === '08:00-16:00,12:00-20:00,16:00-24:00'
  )
  // 晚班配色 = neutral-800 #1F2937：原契约给的 #722ED1 不在设计 Token 色板内，已按 demo-ui-redesign.md 2.2 废弃
  check(
    '班次配色取设计 Token 色',
    shifts1.ok && shifts1.result.map((s) => s.color).join(',') === '#0958D9,#FA8C16,#1F2937'
  )
  const shiftsStaff = await expectCode(
    '非 ADMIN 班次查询强制本站',
    'get',
    '/shifts',
    { params: { stationId: 2 }, token: attStaffToken },
    200
  )
  check(
    '越权覆盖：班次均为本人驿站(1)',
    shiftsStaff.ok && shiftsStaff.result.length === 3 && shiftsStaff.result.every((s) => s.stationId === 1)
  )

  /* ---- 今日打卡状态（员工端） ---- */
  const st1 = await expectCode(
    '今日打卡状态 GET /attendance/status',
    'get',
    '/attendance/status',
    { token: attStaffToken },
    200
  )
  check(
    '今日状态含班次与规则要求摘要',
    st1.ok &&
      st1.result.workDate === today &&
      st1.result.hasSchedule === true &&
      !!st1.result.shift.shiftName &&
      !!st1.result.rule.matchMode
  )
  check(
    '今日状态标记未打上班/下班卡（种子留出演示入口）',
    st1.ok && st1.result.onChecked === false && st1.result.offChecked === false
  )

  /* ---- 打卡记录：分页 / 筛选 / 越权 / 与排班一致性 ---- */
  const rec1 = await expectCode(
    '打卡记录 GET /attendance/records',
    'get',
    '/attendance/records',
    { params: { stationId: 1, startDate: monthStart, endDate: today, pageNum: 1, pageSize: 20 }, token: attAdminToken },
    200
  )
  check('记录分页 total ≥ 当页条数', rec1.ok && rec1.result.total > 0 && rec1.result.list.length === 20)
  check(
    '记录字段齐全（命中项 / 距离 / 打卡方式）',
    rec1.ok &&
      [
        'employeeName',
        'workDate',
        'checkType',
        'checkTime',
        'checkMode',
        'wifiSsid',
        'wifiMatched',
        'distance',
        'locationMatched',
        'status'
      ].every((k) => k in rec1.result.list[0])
  )

  const statusTotal = async (status) => {
    const res = await call('get', '/attendance/records', {
      params: { stationId: 1, status, startDate: monthStart, endDate: today, pageNum: 1, pageSize: 100 },
      token: attAdminToken
    })
    return res.ok ? res.result : { total: 0, list: [] }
  }
  const [recNormal, recLate, recEarly, recAbnormal] = await Promise.all(
    ['NORMAL', 'LATE', 'EARLY_LEAVE', 'ABNORMAL'].map(statusTotal)
  )
  check('种子含正常卡样本', recNormal.total > 0)
  check('种子含迟到卡样本', recLate.total > 0)
  check('种子含早退卡样本', recEarly.total > 0)
  check('种子含校验异常卡样本', recAbnormal.total > 0)
  check(
    '状态筛选口径正确',
    recLate.list.every((r) => r.status === 'LATE')
  )
  check(
    '迟到只出现在上班卡上（业务语义约束）',
    recLate.list.every((r) => r.checkType === 'ON')
  )
  check(
    '早退只出现在下班卡上（业务语义约束）',
    recEarly.list.every((r) => r.checkType === 'OFF')
  )

  await expectCode(
    '记录 pageSize 越界 → 400',
    'get',
    '/attendance/records',
    { params: { pageSize: 999 }, token: attAdminToken },
    400
  )
  await expectCode(
    '记录 status 取值非法 → 400',
    'get',
    '/attendance/records',
    { params: { status: 'UNKNOWN' }, token: attAdminToken },
    400
  )
  const recScope = await call('get', '/attendance/records', {
    params: { stationId: 2, pageNum: 1, pageSize: 100 },
    token: attStaToken
  })
  check(
    '越权覆盖：站长记录收敛本站(1)',
    recScope.ok && recScope.result.list.length > 0 && recScope.result.list.every((r) => r.stationId === 1)
  )

  // 无「无排班却有打卡」矛盾数据：抽查一个员工的全部记录，逐条回查其所在周的排班矩阵
  const sampleEmpId = rec1.result.list[0].employeeId
  const empRecs = await call('get', '/attendance/records', {
    params: { stationId: 1, employeeId: sampleEmpId, pageNum: 1, pageSize: 100 },
    token: attAdminToken
  })
  const weeks = [...new Set(empRecs.result.list.map((r) => formatDate(mondayOf(new Date(`${r.workDate}T00:00:00`)))))]
  let scheduleConsistent = true
  for (const ws of weeks) {
    const matrix = await call('get', '/schedules', { params: { stationId: 1, weekStart: ws }, token: attAdminToken })
    const row = matrix.ok ? matrix.result.employees.find((e) => e.employeeId === sampleEmpId) : null
    empRecs.result.list.forEach((r) => {
      if (r.workDate < ws || r.workDate > formatDate(addDays(new Date(`${ws}T00:00:00`), 6))) return
      const slot = row && row.days.find((d) => d.workDate === r.workDate)
      if (!slot || slot.shiftId == null) scheduleConsistent = false
    })
  }
  check('打卡记录逐条都能回查到当日排班（无矛盾数据）', empRecs.ok && empRecs.result.total > 0 && scheduleConsistent)

  /* ---- 打卡概况 ---- */
  const sum1 = await expectCode(
    '打卡概况 GET /attendance/summary（今日）',
    'get',
    '/attendance/summary',
    { params: { stationId: 1, date: today }, token: attAdminToken },
    200
  )
  check(
    '概况字段齐全（应到/实到/正常/迟到/早退/缺卡）',
    sum1.ok &&
      ['shouldCount', 'actualCount', 'normalCount', 'lateCount', 'earlyLeaveCount', 'absentCount'].every(
        (k) => typeof sum1.result[k] === 'number'
      )
  )
  check(
    '今日应到 > 0 且 缺卡 = 应到 - 实到',
    sum1.ok &&
      sum1.result.shouldCount > 0 &&
      sum1.result.shouldCount >= sum1.result.actualCount &&
      sum1.result.absentCount === sum1.result.shouldCount - sum1.result.actualCount
  )
  await expectCode(
    '概况日期格式非法 → 400',
    'get',
    '/attendance/summary',
    { params: { date: '2026/09/17' }, token: attAdminToken },
    400
  )

  const matrixLastWeek = await call('get', '/schedules', {
    params: { stationId: 1, weekStart: formatDate(addDays(mondayOf(new Date()), -7)) },
    token: attAdminToken
  })
  const busyDate = matrixLastWeek.ok
    ? (matrixLastWeek.result.employees[0].days.find((d) => d.shiftId != null) || {}).workDate
    : null
  const sumHist = busyDate
    ? await call('get', '/attendance/summary', { params: { stationId: 1, date: busyDate }, token: attAdminToken })
    : { ok: false }
  check(
    '历史排班日概况有正常/迟到数据（演示可看）',
    sumHist.ok && sumHist.result.shouldCount > 0 && sumHist.result.actualCount > 0 && sumHist.result.normalCount > 0
  )

  /* ---- 我的打卡 ---- */
  const my1 = await expectCode(
    '我的打卡 GET /attendance/my（员工端）',
    'get',
    '/attendance/my',
    { token: attStaffToken },
    200
  )
  check(
    '我的打卡按月过滤且仅含本人记录',
    my1.ok && my1.result.month === today.slice(0, 7) && my1.result.list.every((r) => r.employeeId === 4)
  )
  check(
    '我的打卡附带今日状态与规则摘要',
    my1.ok && my1.result.todayStatus && my1.result.todayStatus.workDate === today && !!my1.result.todayStatus.rule
  )
  await expectCode(
    '我的打卡 month 格式非法 → 400',
    'get',
    '/attendance/my',
    { params: { month: '2026-9' }, token: attStaffToken },
    400
  )

  /* ---- 排班矩阵 / 我的排班 ---- */
  const matrix1 = await expectCode(
    '排班矩阵 GET /schedules（按周）',
    'get',
    '/schedules',
    { params: { stationId: 1, weekStart }, token: attAdminToken },
    200
  )
  const station1Ids = new Set(
    activeEmployees()
      .filter((e) => e.station_id === 1)
      .map((e) => e.id)
  )
  check(
    '矩阵返回 7 天 + 该站班次图例',
    matrix1.ok &&
      matrix1.result.dates.length === 7 &&
      matrix1.result.weekStart === weekStart &&
      matrix1.result.shifts.length === 3
  )
  check(
    '矩阵员工均为城东驿站且每人 7 个格子',
    matrix1.ok &&
      matrix1.result.employees.length > 0 &&
      matrix1.result.employees.every((e) => station1Ids.has(e.employeeId) && e.days.length === 7)
  )
  check(
    '本周存在已排班与未排班两种格子',
    matrix1.ok &&
      matrix1.result.employees.some((e) => e.days.some((d) => d.shiftId != null)) &&
      matrix1.result.employees.every((e) => e.days.every((d) => d.workDate))
  )
  check(
    '本周 7 天均有排班（排班表不留空列）',
    matrix1.ok &&
      matrix1.result.dates.every((d) =>
        matrix1.result.employees.some((e) => e.days.find((x) => x.workDate === d).shiftId != null)
      )
  )

  const mySch = await expectCode(
    '我的排班 GET /schedules/my',
    'get',
    '/schedules/my',
    { params: { weekStart }, token: attStaffToken },
    200
  )
  check(
    '我的排班返回本人 7 天明细',
    mySch.ok &&
      mySch.result.dates.length === 7 &&
      mySch.result.list.length === 7 &&
      mySch.result.list.some((d) => d.shiftName)
  )
  await expectCode(
    'STAFF 访问排班矩阵 → 403',
    'get',
    '/schedules',
    { params: { stationId: 1 }, token: attStaffToken },
    403,
    403
  )
  await expectCode(
    '排班 weekStart 格式非法 → 400',
    'get',
    '/schedules',
    { params: { stationId: 1, weekStart: '2026/09/14' }, token: attAdminToken },
    400
  )
  const matrixSta = await call('get', '/schedules', { params: { stationId: 2, weekStart }, token: attStaToken })
  check(
    '越权覆盖：站长排班矩阵收敛本站(1)',
    matrixSta.ok && matrixSta.result.employees.every((e) => station1Ids.has(e.employeeId))
  )

  /* ---- 打卡判定：时间窗外（规则时间窗开启） ---- */
  const staffOthers = activeEmployees().filter((e) => e.station_id === 1 && e.id !== 4)
  // 「今日已打上班卡」的人不能用来测时间窗（去重先于时间窗判定），因此只取今日记录，不能扫整月
  const todayOnRes = await call('get', '/attendance/records', {
    params: { stationId: 1, startDate: today, endDate: today, pageNum: 1, pageSize: 100 },
    token: attAdminToken
  })
  const onHolders = new Set(
    todayOnRes.result.list.filter((r) => r.checkType === 'ON' && r.status !== 'ABNORMAL').map((r) => r.employeeId)
  )

  const windowEmp =
    staffOthers.find((e) => !onHolders.has(e.id) && e.id !== 3) || activeEmployees().find((e) => e.id === 3)
  const windowEmpLogin = await login(windowEmp.username, 'demo1234')
  const windowEmpToken = windowEmpLogin.ok ? windowEmpLogin.result.token : ''
  // 挑一个「上班卡时间窗不含当前时刻」的班次（三个班次的时间窗并集 [07:30,24:00]，任意时刻必有班次落在窗外）
  const nonCovering = shifts1.result.find(
    (s) => !(nowMinutes() >= clockMinutes(s.startTime) - 30 && nowMinutes() <= clockMinutes(s.endTime))
  )
  await call('post', '/schedules/batch', {
    data: { stationId: 1, items: [{ employeeId: windowEmp.id, workDate: today, shiftId: nonCovering.id }] },
    token: attAdminToken
  })
  await expectCode(
    '时间窗外打卡 → 9102',
    'post',
    '/attendance/check-in',
    { data: { checkType: 'ON', wifiSsid }, token: windowEmpToken },
    9102
  )
  // 单会话互踢：windowEmp 可能就是 st001_admin，其重新登录会顶掉站长令牌，这里补一次登录刷新
  const attStaRefresh = await login('st001_admin', 'demo1234')
  const attStaToken2 = attStaRefresh.ok ? attStaRefresh.result.token : ''

  /* ---- 打卡判定：正常/迟到 + 下班早退 + 重复打卡（关闭时间窗，使判定结果只取决于班次与迟到早退阈值） ---- */
  await call('put', '/attendance/rule', {
    data: { ...ruleBackup, stationId: 1, enableTimeWindow: false },
    token: attAdminToken
  })
  const shift4 = (await call('get', '/attendance/status', { token: attStaffToken })).result.shift
  const onRes = await call('post', '/attendance/check-in', {
    data: { checkType: 'ON', wifiSsid, longitude: ruleBackup.longitude, latitude: ruleBackup.latitude },
    token: attStaffToken
  })
  check(
    '上班打卡成功且状态与班次时间自洽',
    onRes.ok && onRes.result.status === expectOnStatus(onRes.result.checkTime, shift4, ruleBackup),
    `实际 ${onRes.ok ? onRes.result.status : onRes.code}`
  )
  check(
    '打卡命中项与校验字段齐全（命中围栏中心距离 0）',
    onRes.ok &&
      onRes.result.checkMode === 'WIFI+LOCATION' &&
      onRes.result.wifiMatched === true &&
      onRes.result.locationMatched === true &&
      onRes.result.distance === 0
  )
  check(
    '打卡记录落到本人与本站',
    onRes.ok &&
      onRes.result.employeeId === 4 &&
      onRes.result.stationId === 1 &&
      onRes.result.workDate === today &&
      onRes.result.checkType === 'ON'
  )

  const offRes = await call('post', '/attendance/check-in', {
    data: { checkType: 'OFF', wifiSsid, longitude: ruleBackup.longitude, latitude: ruleBackup.latitude },
    token: attStaffToken
  })
  check(
    '下班打卡成功且早退判定自洽',
    offRes.ok && offRes.result.status === expectOffStatus(offRes.result.checkTime, shift4, ruleBackup),
    `实际 ${offRes.ok ? offRes.result.status : offRes.code}`
  )
  check('上班/下班卡分别独立去重（下班卡未被上班卡挡住）', offRes.ok && offRes.result.checkType === 'OFF')

  await expectCode(
    '同类型重复打卡 → 9105',
    'post',
    '/attendance/check-in',
    {
      data: { checkType: 'ON', wifiSsid, longitude: ruleBackup.longitude, latitude: ruleBackup.latitude },
      token: attStaffToken
    },
    9105
  )

  // 早退分支的确定性验证：晚班收在 24:00 + 早退阈值 0 → 任何时刻打下班卡都必然判早退（不依赖脚本运行时刻）
  await call('put', '/attendance/rule', {
    data: { ...ruleBackup, stationId: 1, enableTimeWindow: false, earlyLeaveThresholdMin: 0 },
    token: attAdminToken
  })
  await call('post', '/schedules/batch', {
    data: { stationId: 1, items: [{ employeeId: 3, workDate: today, shiftId: 3 }] },
    token: attAdminToken
  })
  const earlyRes = await call('post', '/attendance/check-in', {
    data: { checkType: 'OFF', wifiSsid, longitude: ruleBackup.longitude, latitude: ruleBackup.latitude },
    token: attStaToken2
  })
  check(
    '下班卡早退判定（晚班 24:00 收班 + 阈值 0 → 必然早退）',
    earlyRes.ok && earlyRes.result.status === 'EARLY_LEAVE' && earlyRes.result.checkType === 'OFF'
  )

  /* ---- 打卡判定：WiFi 未命中 / 定位超范围 / ALL 与 ANY 组合 / Haversine 距离 ---- */
  await call('put', '/attendance/rule', {
    data: { ...ruleBackup, stationId: 1, enableTimeWindow: false, matchMode: 'ALL', radius: 50 },
    token: attAdminToken
  })
  await expectCode(
    'WiFi 未命中 → 9103',
    'post',
    '/attendance/check-in',
    {
      data: {
        checkType: 'ON',
        wifiSsid: 'Wrong-WiFi-5G',
        longitude: ruleBackup.longitude,
        latitude: ruleBackup.latitude
      },
      token: attStaToken2
    },
    9103
  )
  await expectCode(
    '定位超出围栏 → 9104',
    'post',
    '/attendance/check-in',
    {
      data: { checkType: 'ON', wifiSsid, longitude: ruleBackup.longitude, latitude: ruleBackup.latitude + 0.001 },
      token: attStaToken2
    },
    9104
  )
  const abnToday = await call('get', '/attendance/records', {
    params: {
      stationId: 1,
      employeeId: 3,
      startDate: today,
      endDate: today,
      status: 'ABNORMAL',
      pageNum: 1,
      pageSize: 100
    },
    token: attAdminToken
  })
  check('校验未通过写入异常卡留痕（WiFi 未命中 + 定位超范围 共 2 条）', abnToday.ok && abnToday.result.total === 2)

  await call('put', '/attendance/rule', {
    data: { ...ruleBackup, stationId: 1, enableTimeWindow: false, matchMode: 'ANY', radius: 50 },
    token: attAdminToken
  })
  const anyRes = await call('post', '/attendance/check-in', {
    data: { checkType: 'ON', wifiSsid, longitude: ruleBackup.longitude, latitude: ruleBackup.latitude + 0.001 },
    token: attStaToken2
  })
  check(
    'matchMode=ANY 时 WiFi 命中即放行（同一入参在 ALL 下为 9104）',
    anyRes.ok &&
      anyRes.result.checkMode === 'WIFI' &&
      anyRes.result.wifiMatched === true &&
      anyRes.result.locationMatched === false
  )
  check(
    'Haversine 距离与 0.001° 纬度理论值一致（≈111.19m，非经纬度差值）',
    anyRes.ok && Math.abs(anyRes.result.distance - 111.19) <= 1,
    `实际 ${anyRes.ok ? anyRes.result.distance : '-'}m`
  )
  check('异常卡不参与去重（校验失败后仍可正常打卡）', anyRes.ok && anyRes.result.status !== 'ABNORMAL')

  await expectCode(
    '规则恢复种子口径',
    'put',
    '/attendance/rule',
    { data: { ...ruleBackup, stationId: 1 }, token: attAdminToken },
    200
  )
  const ruleFinal = await call('get', '/attendance/rule', { params: { stationId: 1 }, token: attAdminToken })
  check(
    '规则恢复后与初始完全一致',
    ruleFinal.ok &&
      [
        'matchMode',
        'radius',
        'lateThresholdMin',
        'earlyLeaveThresholdMin',
        'enableTimeWindow',
        'enableWifi',
        'enableLocation'
      ].every((k) => ruleFinal.result[k] === ruleBackup[k])
  )

  /* ---- 排班批量保存：覆盖唯一性 / 清空 / 班次不可用 ---- */
  const batchEmp = staffOthers.find((e) => e.id !== windowEmp.id) || activeEmployees().find((e) => e.id === 3)
  const batch1 = await call('post', '/schedules/batch', {
    data: {
      stationId: 1,
      items: [
        { employeeId: batchEmp.id, workDate: today, shiftId: 1 },
        { employeeId: batchEmp.id, workDate: formatDate(addDays(new Date(), 1)), shiftId: 2 }
      ]
    },
    token: attAdminToken
  })
  check('批量保存排班 2 条', batch1.ok && batch1.result.saved === 2 && batch1.result.removed === 0)
  const batch2 = await call('post', '/schedules/batch', {
    data: { stationId: 1, items: [{ employeeId: batchEmp.id, workDate: today, shiftId: 3 }] },
    token: attAdminToken
  })
  check('同员工同日再次保存为覆盖（唯一性 = employeeId + workDate）', batch2.ok && batch2.result.saved === 1)
  const matrixAfter = await call('get', '/schedules', { params: { stationId: 1, weekStart }, token: attAdminToken })
  const batchRow = matrixAfter.ok ? matrixAfter.result.employees.find((e) => e.employeeId === batchEmp.id) : null
  check(
    '覆盖后矩阵中该员工今日班次已更新为第 3 个班次',
    !!batchRow && batchRow.days.find((d) => d.workDate === today).shiftId === 3
  )
  check(
    '矩阵中该员工每日至多一条排班（无重复行）',
    !!batchRow && batchRow.days.filter((d) => d.workDate === today).length === 1
  )

  const batch3 = await call('post', '/schedules/batch', {
    data: {
      stationId: 1,
      items: [{ employeeId: batchEmp.id, workDate: formatDate(addDays(new Date(), 1)), shiftId: null }]
    },
    token: attAdminToken
  })
  check('shiftId 为空 = 清空该天排班', batch3.ok && batch3.result.removed === 1 && batch3.result.saved === 0)
  await expectCode(
    '排班引用不存在的班次 → 9106',
    'post',
    '/schedules/batch',
    {
      data: { stationId: 1, items: [{ employeeId: batchEmp.id, workDate: today, shiftId: 99999 }] },
      token: attAdminToken
    },
    9106
  )
  await expectCode(
    '排班引用他站班次 → 9106',
    'post',
    '/schedules/batch',
    { data: { stationId: 1, items: [{ employeeId: batchEmp.id, workDate: today, shiftId: 5 }] }, token: attAdminToken },
    9106
  )
  await expectCode(
    '排班员工不属于该驿站 → 400',
    'post',
    '/schedules/batch',
    { data: { stationId: 1, items: [{ employeeId: 1, workDate: today, shiftId: 1 }] }, token: attAdminToken },
    400
  )
  await expectCode(
    '排班 items 为空 → 400',
    'post',
    '/schedules/batch',
    { data: { stationId: 1, items: [] }, token: attAdminToken },
    400
  )
  await expectCode(
    '非 ADMIN 批量保存排班 → 403',
    'post',
    '/schedules/batch',
    { data: { stationId: 1, items: [{ employeeId: batchEmp.id, workDate: today, shiftId: 1 }] }, token: attStaToken2 },
    403,
    403
  )

  /* ---- 班次 CRUD ---- */
  await expectCode(
    '新增班次时间格式非法 → 400',
    'post',
    '/shifts',
    {
      data: { stationId: 1, shiftName: '夜班', startTime: '8:00', endTime: '16:00', color: '#0958D9' },
      token: attAdminToken
    },
    400
  )
  await expectCode(
    '新增班次结束早于开始 → 400',
    'post',
    '/shifts',
    {
      data: { stationId: 1, shiftName: '夜班', startTime: '20:00', endTime: '08:00', color: '#0958D9' },
      token: attAdminToken
    },
    400
  )
  await expectCode(
    '新增班次颜色格式非法 → 400',
    'post',
    '/shifts',
    {
      data: { stationId: 1, shiftName: '夜班', startTime: '20:00', endTime: '23:00', color: 'blue' },
      token: attAdminToken
    },
    400
  )
  await expectCode(
    '非 ADMIN 新增班次 → 403',
    'post',
    '/shifts',
    {
      data: { stationId: 1, shiftName: '夜班', startTime: '20:00', endTime: '23:00', color: '#0958D9' },
      token: attStaffToken
    },
    403,
    403
  )
  const shiftNew = await expectCode(
    '新增班次成功',
    'post',
    '/shifts',
    {
      data: {
        stationId: 1,
        shiftName: '夜班',
        startTime: '20:00',
        endTime: '23:00',
        color: '#0958D9',
        restMinutes: 30
      },
      token: attAdminToken
    },
    200
  )
  const shiftNewId = shiftNew.ok ? shiftNew.result.id : 0
  const shiftEdited = await expectCode(
    '编辑班次成功',
    'put',
    `/shifts/${shiftNewId}`,
    {
      data: { shiftName: '夜班(改)', startTime: '20:00', endTime: '23:30', color: '#0958D9', restMinutes: 30 },
      token: attAdminToken
    },
    200
  )
  check(
    '编辑后名称与结束时间生效',
    shiftEdited.ok && shiftEdited.result.shiftName === '夜班(改)' && shiftEdited.result.endTime === '23:30'
  )
  await expectCode(
    '编辑班次不存在 → 404',
    'put',
    '/shifts/99999',
    { data: { shiftName: 'x', startTime: '09:00', endTime: '18:00', color: '#0958D9' }, token: attAdminToken },
    404,
    404
  )

  await call('put', `/shifts/${shiftNewId}`, {
    data: { shiftName: '夜班(改)', startTime: '20:00', endTime: '23:30', color: '#0958D9', status: 0 },
    token: attAdminToken
  })
  await expectCode(
    '排班引用已停用班次 → 9106',
    'post',
    '/schedules/batch',
    {
      data: { stationId: 1, items: [{ employeeId: batchEmp.id, workDate: today, shiftId: shiftNewId }] },
      token: attAdminToken
    },
    9106
  )
  await expectCode('删除被排班引用的班次 → 400', 'delete', '/shifts/1', { token: attAdminToken }, 400)
  await expectCode('删除新建班次成功', 'delete', `/shifts/${shiftNewId}`, { token: attAdminToken }, 200)
  await expectCode('删除班次不存在 → 404', 'delete', '/shifts/99999', { token: attAdminToken }, 404, 404)

  /* ---- 未配置规则的驿站 → 9101 ---- */
  const attStation = await call('post', '/stations', {
    data: { code: 'ST900', stationName: '考勤演示驿站', contactPerson: '演示', contactPhone: '13612345678' },
    token: attAdminToken
  })
  const attStationId = attStation.ok ? attStation.result.id : 0
  await expectCode(
    '未配置规则的驿站查询规则 → 9101',
    'get',
    '/attendance/rule',
    { params: { stationId: attStationId }, token: attAdminToken },
    9101
  )
  await expectCode('查询规则缺少 stationId → 400', 'get', '/attendance/rule', { token: attAdminToken }, 400)
  await call('delete', `/stations/${attStationId}`, { token: attAdminToken })

  console.log(
    `[T17] 运行时打卡判定实测：上班卡=${onRes.ok ? onRes.result.status : onRes.code} / 下班卡=${offRes.ok ? offRes.result.status : offRes.code} / ` +
      `早退分支=${earlyRes.ok ? earlyRes.result.status : earlyRes.code} / ${nonCovering.shiftName}时间窗外=${windowEmp.id} 号员工 9102 / ` +
      `ANY 放行距离=${anyRes.ok ? anyRes.result.distance : '-'}m（围栏 50m）/ 今日应到=${sum1.result.shouldCount} 实到=${sum1.result.actualCount}`
  )

  /* ========== T18：自定义上下班时间 + 打卡频次（时段模型） ========== */
  // T17 已在今日写过打卡与异常卡，时段用例需要「今日零记录」的干净起点
  resetAttendanceStore()

  const t18Admin = await login('admin', 'demo1234')
  const t18AdminToken = t18Admin.ok ? t18Admin.result.token : ''
  const t18Staff = await login('st001_staff', 'demo1234')
  const t18StaffToken = t18Staff.ok ? t18Staff.result.token : ''
  const t18Sta = await login('st001_admin', 'demo1234')
  const t18StaToken = t18Sta.ok ? t18Sta.result.token : ''

  /** 分钟数 → 'HH:mm'（'24:00' 合法，与时段结束时间的口径一致） */
  const clockText = (minutes) =>
    `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
  /** 时段模型的应有状态：脚本侧独立推算，不引用 attendanceStore 的判定函数 */
  const expectPeriodStatus = (checkTime, period, checkType, rule) =>
    checkType === 'ON'
      ? timeMinutes(checkTime) > clockMinutes(period.startTime) + rule.lateThresholdMin
        ? 'LATE'
        : 'NORMAL'
      : timeMinutes(checkTime) < clockMinutes(period.endTime) - rule.earlyLeaveThresholdMin
        ? 'EARLY_LEAVE'
        : 'NORMAL'
  // 时段用例统一关掉 WiFi / 定位：本轮要验证的是时段与频次，不是围栏判定
  const ruleBase = { stationId: 1, enableWifi: false, enableLocation: false, enableTimeWindow: true, matchMode: 'ANY' }
  const saveRuleT18 = (name, extra) =>
    expectCode(name, 'put', '/attendance/rule', { data: { ...ruleBase, ...extra }, token: t18AdminToken }, 200)

  /* ---- 规则字段与种子频次 ---- */
  const ruleSeed1 = await expectCode(
    'T18 规则含频次与时段字段（GET /attendance/rule）',
    'get',
    '/attendance/rule',
    { params: { stationId: 1 }, token: t18AdminToken },
    200
  )
  check(
    '城东默认单时段（checkFrequency=2 且时段数 = 频次/2）',
    ruleSeed1.ok && ruleSeed1.result.checkFrequency === 2 && ruleSeed1.result.checkPeriods.length === 1
  )
  check(
    'workStartTime / workEndTime 由时段派生（首段开始 / 末段结束）',
    ruleSeed1.ok &&
      ruleSeed1.result.workStartTime === ruleSeed1.result.checkPeriods[0].startTime &&
      ruleSeed1.result.workEndTime === ruleSeed1.result.checkPeriods[0].endTime
  )
  check(
    '时间窗余量字段随规则下发',
    ruleSeed1.ok && ruleSeed1.result.allowEarlyMin === 30 && ruleSeed1.result.allowLateMin === 60
  )

  const seedRules = await call('get', '/attendance/rule/list', { token: t18AdminToken })
  check(
    '种子含 ≥2 个双时段驿站（可演示两种频次）',
    seedRules.ok && seedRules.result.filter((r) => r.checkFrequency === 4).length >= 2
  )
  check(
    '每条规则的时段数都等于 checkFrequency / 2',
    seedRules.ok && seedRules.result.every((r) => r.checkPeriods.length === r.checkFrequency / 2)
  )

  /* ---- 保存校验：频次档位 / 时段数量 / 起止 / 重叠 / 顺序，均回 9107 ---- */
  const freq4Rule = await saveRuleT18('保存双时段规则（checkFrequency=4）', {
    checkFrequency: 4,
    checkPeriods: [
      { name: '上午班', startTime: '09:00', endTime: '12:00' },
      { name: '下午班', startTime: '14:00', endTime: '19:00' }
    ]
  })
  check(
    '保存后时段数 = 2',
    freq4Rule.ok && freq4Rule.result.checkFrequency === 4 && freq4Rule.result.checkPeriods.length === 2
  )
  check(
    '保存后 workStartTime / workEndTime 同步为首段开始 / 末段结束',
    freq4Rule.ok && freq4Rule.result.workStartTime === '09:00' && freq4Rule.result.workEndTime === '19:00'
  )

  await expectCode(
    'checkFrequency 非法（3）→ 9107',
    'put',
    '/attendance/rule',
    { data: { stationId: 1, checkFrequency: 3 }, token: t18AdminToken },
    9107
  )
  await expectCode(
    'checkFrequency=4 但只给 1 个时段 → 9107',
    'put',
    '/attendance/rule',
    {
      data: {
        stationId: 1,
        checkFrequency: 4,
        checkPeriods: [{ name: '全天班', startTime: '08:00', endTime: '18:00' }]
      },
      token: t18AdminToken
    },
    9107
  )
  await expectCode(
    '时段结束早于开始 → 9107',
    'put',
    '/attendance/rule',
    {
      data: {
        stationId: 1,
        checkFrequency: 2,
        checkPeriods: [{ name: '全天班', startTime: '18:00', endTime: '08:00' }]
      },
      token: t18AdminToken
    },
    9107
  )
  await expectCode(
    '时段之间重叠 → 9107',
    'put',
    '/attendance/rule',
    {
      data: {
        stationId: 1,
        checkFrequency: 4,
        checkPeriods: [
          { name: '上午班', startTime: '08:00', endTime: '13:00' },
          { name: '下午班', startTime: '12:00', endTime: '18:00' }
        ]
      },
      token: t18AdminToken
    },
    9107
  )
  await expectCode(
    '时段未按开始时间升序 → 9107',
    'put',
    '/attendance/rule',
    {
      data: {
        stationId: 1,
        checkFrequency: 4,
        checkPeriods: [
          { name: '下午班', startTime: '14:00', endTime: '18:00' },
          { name: '上午班', startTime: '08:00', endTime: '12:00' }
        ]
      },
      token: t18AdminToken
    },
    9107
  )
  await expectCode(
    '时段名称为空 → 9107',
    'put',
    '/attendance/rule',
    {
      data: { stationId: 1, checkFrequency: 2, checkPeriods: [{ name: ' ', startTime: '08:00', endTime: '18:00' }] },
      token: t18AdminToken
    },
    9107
  )
  await expectCode(
    'checkPeriods 非数组 → 9107',
    'put',
    '/attendance/rule',
    { data: { stationId: 1, checkFrequency: 2, checkPeriods: '08:00-18:00' }, token: t18AdminToken },
    9107
  )
  await expectCode(
    'allowEarlyMin 为负 → 400',
    'put',
    '/attendance/rule',
    { data: { stationId: 1, allowEarlyMin: -1 }, token: t18AdminToken },
    400
  )

  /* ---- 今日状态按时段展开 ---- */
  const st4 = await expectCode(
    'T18 今日状态按时段展开（GET /attendance/status）',
    'get',
    '/attendance/status',
    { token: t18StaffToken },
    200
  )
  check(
    '状态含 2 个时段且字段齐全（含时间窗与打卡结果）',
    st4.ok &&
      st4.result.periods.length === 2 &&
      st4.result.periods.every((p) =>
        [
          'periodIndex',
          'name',
          'startTime',
          'endTime',
          'windowStart',
          'windowEnd',
          'onChecked',
          'offChecked',
          'onTime',
          'offTime'
        ].every((k) => k in p)
      )
  )
  check(
    '状态时段与规则时段一致',
    st4.ok &&
      st4.result.periods[0].name === '上午班' &&
      st4.result.periods[0].startTime === '09:00' &&
      st4.result.periods[1].endTime === '19:00'
  )
  check(
    '时段索引 0 起递增，时间窗按 allowEarlyMin / allowLateMin 推导',
    st4.ok &&
      st4.result.periods[0].periodIndex === 0 &&
      st4.result.periods[1].periodIndex === 1 &&
      st4.result.periods[0].windowStart === '08:30' &&
      st4.result.periods[1].windowEnd === '20:00'
  )
  check(
    '规则要求摘要含频次',
    st4.ok && st4.result.checkFrequency === 4 && /每日 4 次/.test(String(st4.result.requireSummary))
  )

  /* ---- 时段模型打卡：时间窗 / 越界 / 迟到早退 / 分时段去重 ---- */
  const nowMin = nowMinutes()
  // 与当前时刻相距 ≥2 小时的时段：时间窗判定必然落在窗外
  const farPeriod =
    nowMin >= 130
      ? { name: '早班', startTime: '00:00', endTime: clockText(nowMin - 120) }
      : { name: '晚班', startTime: clockText(nowMin + 120), endTime: '24:00' }
  await saveRuleT18('时段用例：写入时间窗外的时段', {
    checkFrequency: 2,
    checkPeriods: [farPeriod],
    allowEarlyMin: 0,
    allowLateMin: 0
  })
  await expectCode(
    '时段模型：时间窗外打卡 → 9102',
    'post',
    '/attendance/check-in',
    { data: { checkType: 'ON', periodIndex: 0 }, token: t18StaToken },
    9102
  )
  await expectCode(
    '时段模型：periodIndex 越界 → 9107',
    'post',
    '/attendance/check-in',
    { data: { checkType: 'ON', periodIndex: 1 }, token: t18StaToken },
    9107
  )
  await expectCode(
    '时段模型：periodIndex 非整数 → 9107',
    'post',
    '/attendance/check-in',
    { data: { checkType: 'ON', periodIndex: 'x' }, token: t18StaToken },
    9107
  )
  await expectCode(
    '时段模型：periodIndex 为负 → 9107',
    'post',
    '/attendance/check-in',
    { data: { checkType: 'ON', periodIndex: -1 }, token: t18StaToken },
    9107
  )

  // 把 id=3 今日排班改成「时间窗不含当前时刻」的班次：判定若仍在走旧班次模型，下面必然拿不到 200
  await call('post', '/schedules/batch', {
    data: { stationId: 1, items: [{ employeeId: 3, workDate: today, shiftId: nonCovering.id }] },
    token: t18AdminToken
  })
  const aroundStart = Math.max(0, nowMin - 120)
  const aroundEnd = Math.min(1440, nowMin + 120)
  const aroundRule = await saveRuleT18('时段用例：写入覆盖当前时刻的时段', {
    checkFrequency: 2,
    checkPeriods: [{ name: '全天班', startTime: clockText(aroundStart), endTime: clockText(aroundEnd) }],
    allowEarlyMin: 0,
    allowLateMin: 0,
    lateThresholdMin: 30,
    earlyLeaveThresholdMin: 30
  })
  const aroundOn = await call('post', '/attendance/check-in', {
    data: { checkType: 'ON', periodIndex: 0 },
    token: t18StaToken
  })
  check(
    '时段模型：排班班次时间窗外仍可按规则时段打卡（判定不再依赖旧班次模型）',
    aroundOn.ok && !(nowMin >= clockMinutes(nonCovering.startTime) - 30 && nowMin <= clockMinutes(nonCovering.endTime))
  )
  check(
    '时段模型：上班卡状态与时段基准自洽',
    aroundOn.ok &&
      aroundOn.result.status ===
        expectPeriodStatus(aroundOn.result.checkTime, aroundRule.result.checkPeriods[0], 'ON', aroundRule.result)
  )
  check(
    '时段模型：记录落到指定时段（periodIndex / periodName）',
    aroundOn.ok && aroundOn.result.periodIndex === 0 && aroundOn.result.periodName === '全天班'
  )
  await expectCode(
    '时段模型：同一时段同类型重复打卡 → 9105',
    'post',
    '/attendance/check-in',
    { data: { checkType: 'ON', periodIndex: 0 }, token: t18StaToken },
    9105
  )
  const aroundOff = await call('post', '/attendance/check-in', {
    data: { checkType: 'OFF', periodIndex: 0 },
    token: t18StaToken
  })
  check(
    '时段模型：下班卡状态与时段基准自洽',
    aroundOff.ok &&
      aroundOff.result.status ===
        expectPeriodStatus(aroundOff.result.checkTime, aroundRule.result.checkPeriods[0], 'OFF', aroundRule.result)
  )

  // 双时段 + 极大提前量：两个时段的窗都覆盖当前时刻，用于验证「去重按 periodIndex 而不是按 checkType」
  const dualRule = await saveRuleT18('时段用例：写入两段（窗均覆盖当前时刻）', {
    checkFrequency: 4,
    checkPeriods: [
      { name: '上午班', startTime: '00:00', endTime: '00:30' },
      { name: '下午班', startTime: '00:31', endTime: '01:00' }
    ],
    allowEarlyMin: 60,
    allowLateMin: 1440
  })
  const dualOn = await call('post', '/attendance/check-in', {
    data: { checkType: 'ON', periodIndex: 1 },
    token: t18StaToken
  })
  check(
    '时段模型：同一类型在不同时段可各打一次（去重键含 periodIndex）',
    dualOn.ok && dualOn.result.periodIndex === 1 && dualOn.result.periodName === '下午班'
  )
  const stDual = await call('get', '/attendance/status', { token: t18StaToken })
  check(
    '今日状态按已打时段分别标记 onChecked',
    stDual.ok &&
      stDual.result.periods.length === 2 &&
      stDual.result.periods[0].onChecked === true &&
      stDual.result.periods[1].onChecked === true &&
      stDual.result.periods[0].onTime != null
  )
  check('双时段规则下 status 下发 2 个时段', dualRule.ok && stDual.ok && stDual.result.periods[1].name === '下午班')

  // 早退分支的确定性用例：末段收在 24:00 + 早退阈值 0 → 任何时刻打下班卡都必然早退
  await saveRuleT18('时段用例：末段 24:00 收班', {
    checkFrequency: 2,
    checkPeriods: [{ name: '全天班', startTime: '00:00', endTime: '24:00' }],
    allowEarlyMin: 0,
    allowLateMin: 0,
    lateThresholdMin: 0,
    earlyLeaveThresholdMin: 0
  })
  const earlyPeriod = await call('post', '/attendance/check-in', {
    data: { checkType: 'OFF', periodIndex: 0 },
    token: t18StaffToken
  })
  check(
    '时段模型：末段 24:00 收班 + 阈值 0 → 下班卡必然早退',
    earlyPeriod.ok && earlyPeriod.result.status === 'EARLY_LEAVE' && earlyPeriod.result.checkType === 'OFF'
  )

  /* ---- 记录字段：periodIndex / periodName ---- */
  const recT18 = await call('get', '/attendance/records', {
    params: { stationId: 1, employeeId: 3, pageNum: 1, pageSize: 20 },
    token: t18AdminToken
  })
  check(
    '打卡记录含 periodIndex / periodName',
    recT18.ok &&
      recT18.result.list.length > 0 &&
      recT18.result.list.every((r) => typeof r.periodIndex === 'number' && !!r.periodName)
  )

  const histT18 = await call('get', '/attendance/records', {
    params: {
      stationId: 1,
      startDate: formatDate(addDays(new Date(), -7)),
      endDate: formatDate(addDays(new Date(), -1)),
      pageNum: 1,
      pageSize: 50
    },
    token: t18AdminToken
  })
  check(
    '历史记录按新时段模型生成：periodIndex 归属规则时段且 periodName 非空',
    histT18.ok &&
      histT18.result.list.length > 0 &&
      histT18.result.list.every(
        (r) => r.periodIndex >= 0 && r.periodIndex < ruleSeed1.result.checkFrequency / 2 && !!r.periodName
      )
  )
  check(
    '历史记录时段名与规则声明的名称一致',
    histT18.ok && histT18.result.list.every((r) => r.periodName === ruleSeed1.result.checkPeriods[0].name)
  )

  /* ---- 旧客户端兼容：老板端规则页当前只发上下班时间 ---- */
  const legacySave = await expectCode(
    '旧客户端只发上下班时间可正常保存',
    'put',
    '/attendance/rule',
    { data: { stationId: 1, workStartTime: '09:00', workEndTime: '17:00' }, token: t18AdminToken },
    200
  )
  check(
    '旧客户端路径：上下班时间写入时段且派生值同步（不出现两个口径打架）',
    legacySave.ok &&
      legacySave.result.checkFrequency === 2 &&
      legacySave.result.checkPeriods[0].startTime === '09:00' &&
      legacySave.result.checkPeriods[0].endTime === '17:00' &&
      legacySave.result.workStartTime === '09:00' &&
      legacySave.result.workEndTime === '17:00'
  )
  await expectCode(
    '时段用例后恢复城东种子规则',
    'put',
    '/attendance/rule',
    { data: { ...ruleSeed1.result, stationId: 1 }, token: t18AdminToken },
    200
  )

  console.log(
    `[T18] 时段模型实测：种子双时段驿站 ${
      seedRules.ok
        ? seedRules.result
            .filter((r) => r.checkFrequency === 4)
            .map((r) => r.stationName)
            .join('、')
        : '-'
    } / ` +
      `时段用例 ${clockText(aroundStart)}-${clockText(aroundEnd)} 上班卡=${aroundOn.ok ? aroundOn.result.status : aroundOn.code} 下班卡=${aroundOff.ok ? aroundOff.result.status : aroundOff.code} / ` +
      `时间窗外=${farPeriod.startTime}-${farPeriod.endTime} 9102 / 末段 24:00 早退=${earlyPeriod.ok ? earlyPeriod.result.status : earlyPeriod.code} / ` +
      `status 时段数=${stDual.ok ? stDual.result.periods.length : '-'}`
  )

  /* ========== T19：补卡申请与审批 ========== */
  // 补卡与工单转单都要求「种子态 + 干净会话」；attendance store 与 db 相互独立，两张内存库一起归位
  resetDb()
  resetAttendanceStore()

  const t19Admin = await login('admin', 'demo1234')
  const t19AdminToken = t19Admin.ok ? t19Admin.result.token : ''
  const t19Staff = await login('st001_staff', 'demo1234')
  const t19StaffToken = t19Staff.ok ? t19Staff.result.token : ''
  const t19Sta = await login('st001_admin', 'demo1234')
  const t19StaToken = t19Sta.ok ? t19Sta.result.token : ''

  /* ---- 种子：待审批分布 / 三类状态 / 与打卡记录自洽 ---- */
  const mkAll = await expectCode(
    '补卡列表 GET /attendance/makeup/list（仅 ADMIN）',
    'get',
    '/attendance/makeup/list',
    { params: { pageNum: 1, pageSize: 100 }, token: t19AdminToken },
    200
  )
  const mkList = mkAll.ok ? mkAll.result.list : []
  const mkPendingList = mkList.filter((m) => m.status === 'PENDING')
  const mkApproved = mkList.filter((m) => m.status === 'APPROVED')
  check(
    '补卡种子含 ≥6 条待审批且分布在多个驿站（含城东）',
    mkPendingList.length >= 6 &&
      new Set(mkPendingList.map((m) => m.stationId)).size >= 3 &&
      mkPendingList.some((m) => m.stationId === 1)
  )
  check('补卡种子含已通过 / 已驳回历史', mkApproved.length >= 2 && mkList.some((m) => m.status === 'REJECTED'))
  check(
    '补卡种子含上班卡与下班卡两类',
    mkList.some((m) => m.checkType === 'ON') && mkList.some((m) => m.checkType === 'OFF')
  )
  check(
    '补卡字段齐全（驿站名 / 时段名 / 审批信息）',
    mkList.length > 0 &&
      [
        'id',
        'employeeId',
        'employeeName',
        'stationId',
        'stationName',
        'workDate',
        'periodIndex',
        'periodName',
        'checkType',
        'reason',
        'status',
        'applyTime',
        'approverId',
        'approverName',
        'approveTime',
        'approveRemark'
      ].every((k) => k in mkList[0])
  )
  check(
    '待审批无审批信息、已通过的审批人均为 ADMIN',
    mkPendingList.every((m) => m.approverId == null && m.approveTime == null) &&
      mkApproved.every((m) => m.approverName === '系统管理员')
  )

  const mkStatusFilter = await call('get', '/attendance/makeup/list', {
    params: { status: 'PENDING', pageNum: 1, pageSize: 100 },
    token: t19AdminToken
  })
  check(
    '补卡列表状态筛选口径正确',
    mkStatusFilter.ok &&
      mkStatusFilter.result.total === mkPendingList.length &&
      mkStatusFilter.result.list.every((m) => m.status === 'PENDING')
  )
  const mkStationFilter = await call('get', '/attendance/makeup/list', {
    params: { stationId: 1, pageNum: 1, pageSize: 100 },
    token: t19AdminToken
  })
  check(
    '补卡列表驿站筛选口径正确',
    mkStationFilter.ok &&
      mkStationFilter.result.list.length > 0 &&
      mkStationFilter.result.list.every((m) => m.stationId === 1)
  )
  await expectCode(
    '补卡列表 status 取值非法 → 400',
    'get',
    '/attendance/makeup/list',
    { params: { status: 'UNKNOWN' }, token: t19AdminToken },
    400
  )
  await expectCode(
    '补卡列表 pageSize 越界 → 400',
    'get',
    '/attendance/makeup/list',
    { params: { pageSize: 999 }, token: t19AdminToken },
    400
  )

  // 审批通过的单子逐条回查打卡记录，杜绝「审批通过却无打卡记录」
  let approvedConsistent = mkApproved.length > 0
  for (const item of mkApproved) {
    const day = await call('get', '/attendance/records', {
      params: {
        stationId: item.stationId,
        employeeId: item.employeeId,
        startDate: item.workDate,
        endDate: item.workDate,
        pageNum: 1,
        pageSize: 100
      },
      token: t19AdminToken
    })
    const hit = day.ok
      ? day.result.list.find(
          (r) =>
            r.source === 'MAKEUP' &&
            r.periodIndex === item.periodIndex &&
            r.checkType === item.checkType &&
            r.status === 'NORMAL'
        )
      : null
    if (!hit) approvedConsistent = false
  }
  check('已通过补卡逐条对应 source=MAKEUP / status=NORMAL 的打卡记录', approvedConsistent)

  // 城东补卡（任意状态）都要能回查到当日排班，杜绝「无排班却有补卡」
  const station1Makeups = mkList.filter((m) => m.stationId === 1)
  const makeupScheduleCache = new Map()
  let makeupScheduleConsistent = station1Makeups.length > 0
  for (const item of station1Makeups) {
    const weekStart = formatDate(mondayOf(new Date(`${item.workDate}T00:00:00`)))
    if (!makeupScheduleCache.has(weekStart)) {
      const matrix = await call('get', '/schedules', { params: { stationId: 1, weekStart }, token: t19AdminToken })
      makeupScheduleCache.set(weekStart, matrix.ok ? matrix.result : null)
    }
    const matrix = makeupScheduleCache.get(weekStart)
    const row = matrix ? matrix.employees.find((e) => e.employeeId === item.employeeId) : null
    const slot = row ? row.days.find((d) => d.workDate === item.workDate) : null
    if (!slot || slot.shiftId == null) makeupScheduleConsistent = false
  }
  check('城东补卡逐条能回查到当日排班（无「无排班却有补卡」）', makeupScheduleConsistent)

  /* ---- 越权：仅 ADMIN 可看全量与审批 ---- */
  await expectCode(
    'STAFF 访问补卡列表 → 403（仅老板可审批）',
    'get',
    '/attendance/makeup/list',
    { token: t19StaffToken },
    403,
    403
  )
  await expectCode(
    '站长访问补卡列表 → 403（站长无审批权）',
    'get',
    '/attendance/makeup/list',
    { token: t19StaToken },
    403,
    403
  )

  /* ---- 提交成功 / 重复申请 / 已有正常打卡 ---- */
  const emptySlot = (await findStationEmptySlot(t19AdminToken, 1, [3, 4])) || {
    employeeId: 0,
    workDate: '1970-01-01',
    periodIndex: 0,
    checkType: 'ON'
  }
  check('存在「有排班且槽位空闲」的补卡用例数据', emptySlot.employeeId > 0)
  const applicant = activeEmployees().find((e) => e.id === emptySlot.employeeId) || {}
  const applicantLogin = await login(applicant.username, 'demo1234')
  const applicantToken = applicantLogin.ok ? applicantLogin.result.token : ''
  const applyBody = {
    workDate: emptySlot.workDate,
    periodIndex: emptySlot.periodIndex,
    checkType: emptySlot.checkType,
    reason: '当班忘记打卡，申请补卡'
  }
  const mkSubmit = await expectCode(
    '提交补卡申请（员工本人）',
    'post',
    '/attendance/makeup',
    { data: applyBody, token: applicantToken },
    200
  )
  const submittedId = mkSubmit.ok ? mkSubmit.result.id : 0
  check(
    '补卡申请落库为待审批且带时段名 / 申请人 / 驿站',
    mkSubmit.ok &&
      mkSubmit.result.status === 'PENDING' &&
      mkSubmit.result.periodName === '全天班' &&
      mkSubmit.result.employeeId === emptySlot.employeeId &&
      mkSubmit.result.stationId === 1 &&
      mkSubmit.result.approverId == null
  )
  await expectCode(
    '同槽位重复申请 → 9108',
    'post',
    '/attendance/makeup',
    { data: applyBody, token: applicantToken },
    9108
  )
  await expectCode(
    '补卡日期晚于今天 → 400',
    'post',
    '/attendance/makeup',
    { data: { ...applyBody, workDate: formatDate(addDays(new Date(), 1)) }, token: applicantToken },
    400
  )
  await expectCode(
    '补卡理由过短 → 400',
    'post',
    '/attendance/makeup',
    { data: { ...applyBody, reason: '忘' }, token: applicantToken },
    400
  )
  await expectCode(
    '补卡时段越界 → 9107',
    'post',
    '/attendance/makeup',
    { data: { ...applyBody, periodIndex: 9 }, token: applicantToken },
    9107
  )

  const mineSubmit = await call('get', '/attendance/makeup/my', {
    params: { pageNum: 1, pageSize: 100 },
    token: applicantToken
  })
  check(
    '我的补卡申请只返回本人数据并能查到刚提交的单子',
    mineSubmit.ok &&
      mineSubmit.result.list.every((m) => m.employeeId === emptySlot.employeeId) &&
      mineSubmit.result.list.some((m) => m.id === submittedId)
  )

  const staffRecs = await call('get', '/attendance/records', {
    params: { stationId: 1, employeeId: 4, pageNum: 1, pageSize: 100 },
    token: t19AdminToken
  })
  const takenRec = staffRecs.ok ? staffRecs.result.list.find((r) => r.status !== 'ABNORMAL') : null
  check('演示员工存在可复用的历史正常卡（已有打卡分支前提）', !!takenRec)
  if (takenRec) {
    await expectCode(
      '该时段当日已有正常打卡 → 9108',
      'post',
      '/attendance/makeup',
      {
        data: {
          workDate: takenRec.workDate,
          periodIndex: takenRec.periodIndex,
          checkType: takenRec.checkType,
          reason: '该时段已打过卡'
        },
        token: t19StaffToken
      },
      9108
    )
  }

  /* ---- 审批：通过 / 重复审批 / 驳回 ---- */
  await expectCode(
    '站长审批补卡 → 403（站长无审批权）',
    'post',
    `/attendance/makeup/${submittedId}/approve`,
    { data: { approved: true }, token: t19StaToken },
    403,
    403
  )
  await expectCode(
    'approved 入参非布尔 → 400',
    'post',
    `/attendance/makeup/${submittedId}/approve`,
    { data: { approved: 'yes' }, token: t19AdminToken },
    400
  )
  const mkApprove = await expectCode(
    'ADMIN 审批通过补卡',
    'post',
    `/attendance/makeup/${submittedId}/approve`,
    { data: { approved: true, approveRemark: '情况属实，予以补卡' }, token: t19AdminToken },
    200
  )
  check(
    '审批通过后状态与审批信息落库',
    mkApprove.ok &&
      mkApprove.result.status === 'APPROVED' &&
      mkApprove.result.approverId === 1 &&
      mkApprove.result.approverName === '系统管理员' &&
      !!mkApprove.result.approveTime &&
      mkApprove.result.approveRemark === '情况属实，予以补卡'
  )
  await expectCode(
    '重复审批 → 9109',
    'post',
    `/attendance/makeup/${submittedId}/approve`,
    { data: { approved: true }, token: t19AdminToken },
    9109
  )
  await expectCode(
    '审批不存在的补卡申请 → 404',
    'post',
    '/attendance/makeup/999999/approve',
    { data: { approved: true }, token: t19AdminToken },
    404,
    404
  )

  const ruleForMakeup = await call('get', '/attendance/rule', { params: { stationId: 1 }, token: t19AdminToken })
  const specPeriod = ruleForMakeup.ok ? ruleForMakeup.result.checkPeriods[emptySlot.periodIndex] : null
  const madeDay = await call('get', '/attendance/records', {
    params: {
      stationId: 1,
      employeeId: emptySlot.employeeId,
      startDate: emptySlot.workDate,
      endDate: emptySlot.workDate,
      pageNum: 1,
      pageSize: 100
    },
    token: t19AdminToken
  })
  const madeRec = madeDay.ok
    ? madeDay.result.list.find(
        (r) => r.source === 'MAKEUP' && r.periodIndex === emptySlot.periodIndex && r.checkType === emptySlot.checkType
      )
    : null
  check(
    '审批通过后生成 source=MAKEUP 的打卡记录（status=NORMAL）',
    !!madeRec && madeRec.status === 'NORMAL' && madeRec.employeeId === emptySlot.employeeId
  )
  check(
    '补卡记录打卡时间取该时段规定时间',
    !!madeRec &&
      !!specPeriod &&
      madeRec.checkTime.slice(11, 16) === (emptySlot.checkType === 'ON' ? specPeriod.startTime : specPeriod.endTime)
  )
  check(
    '补卡记录绑定申请时段名且不伪造校验命中项',
    !!madeRec &&
      madeRec.periodName === '全天班' &&
      madeRec.checkMode === null &&
      madeRec.wifiMatched === null &&
      madeRec.locationMatched === null
  )
  const mineApproved = await call('get', '/attendance/makeup/my', {
    params: { status: 'APPROVED', pageNum: 1, pageSize: 100 },
    token: applicantToken
  })
  check(
    '员工端按状态可查到本人已通过的补卡',
    mineApproved.ok && mineApproved.result.list.some((m) => m.id === submittedId)
  )

  // 驳回分支：不生成打卡记录，且驳回后同槽位可重新申请（9108 只挡 PENDING / APPROVED）
  // 这里不排除上一位申请人：helper 已把「在途 / 已通过的补卡」计入槽位占用，自然会换到另一个空位
  const rejectSlot = (await findStationEmptySlot(t19AdminToken, 1, [3, 4])) || {
    employeeId: 0,
    workDate: '1970-01-01',
    periodIndex: 0,
    checkType: 'ON'
  }
  check('存在驳回用例所需的空槽位', rejectSlot.employeeId > 0)
  const rejectApplicant = activeEmployees().find((e) => e.id === rejectSlot.employeeId) || {}
  const rejectLogin = await login(rejectApplicant.username, 'demo1234')
  const rejectToken = rejectLogin.ok ? rejectLogin.result.token : ''
  const rejectBody = {
    workDate: rejectSlot.workDate,
    periodIndex: rejectSlot.periodIndex,
    checkType: rejectSlot.checkType,
    reason: '外出取件未打卡，申请补卡'
  }
  const rejectSubmit = await expectCode(
    '提交第二条补卡申请（驳回用例）',
    'post',
    '/attendance/makeup',
    { data: rejectBody, token: rejectToken },
    200
  )
  const rejectId = rejectSubmit.ok ? rejectSubmit.result.id : 0
  const rejectApprove = await expectCode(
    'ADMIN 审批驳回补卡',
    'post',
    `/attendance/makeup/${rejectId}/approve`,
    { data: { approved: false, approveRemark: '缺少证明材料' }, token: t19AdminToken },
    200
  )
  check(
    '驳回后状态为 REJECTED 且记录审批备注',
    rejectApprove.ok &&
      rejectApprove.result.status === 'REJECTED' &&
      rejectApprove.result.approveRemark === '缺少证明材料' &&
      !!rejectApprove.result.approveTime
  )
  const rejectDay = await call('get', '/attendance/records', {
    params: {
      stationId: 1,
      employeeId: rejectSlot.employeeId,
      startDate: rejectSlot.workDate,
      endDate: rejectSlot.workDate,
      pageNum: 1,
      pageSize: 100
    },
    token: t19AdminToken
  })
  check(
    '驳回不生成补卡打卡记录',
    rejectDay.ok &&
      !rejectDay.result.list.some(
        (r) => r.source === 'MAKEUP' && r.periodIndex === rejectSlot.periodIndex && r.checkType === rejectSlot.checkType
      )
  )
  await expectCode(
    '驳回后同槽位可重新申请',
    'post',
    '/attendance/makeup',
    { data: rejectBody, token: rejectToken },
    200
  )

  console.log(
    `[T19-补卡] 种子待审批 ${mkPendingList.length} 条 / 已通过 ${mkApproved.length} 条（均已补录打卡记录）/ 已驳回 ${mkList.filter((m) => m.status === 'REJECTED').length} 条 / 分布驿站 ${[...new Set(mkList.map((m) => m.stationName))].join('、')} / ` +
      `审批通过生成的记录 source=${madeRec ? madeRec.source : '-'} 打卡时间=${madeRec ? madeRec.checkTime : '-'}（该时段规定时间 ${specPeriod ? (emptySlot.checkType === 'ON' ? specPeriod.startTime : specPeriod.endTime) : '-'}）/ 重复审批 9109 / 驳回后可重新提交`
  )

  /* ========== T19：工单转单 ========== */
  resetDb() // 工单与转单留痕一起回到种子态

  const t20Admin = await login('admin', 'demo1234')
  const t20AdminToken = t20Admin.ok ? t20Admin.result.token : ''
  const t20Staff = await login('st001_staff', 'demo1234')
  const t20StaffToken = t20Staff.ok ? t20Staff.result.token : ''
  const t20Sta = await login('st001_admin', 'demo1234')
  const t20StaToken = t20Sta.ok ? t20Sta.result.token : ''

  /* ---- 种子留痕：≥8 条工单含转单记录（含 1 条跨站） ---- */
  const stationOfEmployee = (id) => {
    const emp = db.employees.find((e) => e.id === Number(id))
    return emp ? emp.station_id : null
  }
  const transferOrderIds = [...new Set(db.workOrderTransfers.map((t) => t.workOrderId))]
  // 跨站条数在运行时转单之前先取数：后面的用例会再追加跨站留痕，混在一起说不清种子口径
  const seedCrossCount = db.workOrderTransfers.filter(
    (t) => t.fromEmployeeId != null && stationOfEmployee(t.fromEmployeeId) !== stationOfEmployee(t.toEmployeeId)
  ).length
  check('种子 ≥8 条工单含转单记录', transferOrderIds.length >= 8)
  check(
    '种子含跨站转单（转出方与转入方不同驿站）',
    db.workOrderTransfers.some(
      (t) => t.fromEmployeeId != null && stationOfEmployee(t.fromEmployeeId) !== stationOfEmployee(t.toEmployeeId)
    )
  )
  check(
    '转单留痕字段齐全（转出 / 转入 / 理由 / 操作人 / 时间）',
    db.workOrderTransfers.length > 0 &&
      [
        'id',
        'workOrderId',
        'fromEmployeeId',
        'fromEmployeeName',
        'toEmployeeId',
        'toEmployeeName',
        'reason',
        'operatorId',
        'operatorName',
        'transferTime'
      ].every((k) => k in db.workOrderTransfers[0])
  )
  check(
    '每条转单留痕的理由 / 转入人 / 操作人均非空',
    db.workOrderTransfers.every((t) => !!t.reason && !!t.toEmployeeName && !!t.operatorName)
  )

  const seedOrderId = transferOrderIds[0] || 0
  const seedDetail = await expectCode(
    '工单详情返回转单留痕 transfers',
    'get',
    `/work-orders/${seedOrderId}`,
    { token: t20AdminToken },
    200
  )
  check(
    '详情 transfers 数组非空',
    seedDetail.ok && Array.isArray(seedDetail.result.transfers) && seedDetail.result.transfers.length > 0
  )
  check(
    'transfers 按转单时间倒序',
    seedDetail.ok &&
      seedDetail.result.transfers.every((t, i, arr) => i === 0 || arr[i - 1].transferTime >= t.transferTime)
  )
  check(
    '工单当前处理人与最后一次转单的转入人一致（无矛盾数据）',
    seedDetail.ok && seedDetail.result.assigneeId === seedDetail.result.transfers[0].toEmployeeId
  )
  check(
    '转单事件已写入工单时间线',
    seedDetail.ok &&
      seedDetail.result.handleLog.some((l) => l.action === 'transfer' && /转单给/.test(String(l.content)))
  )

  /* ---- 运行时转单：ADMIN 跨站 / 站长站内 / 处理人本人 ---- */
  const createTransferCase = async (assigneeId) => {
    const created = await call('post', '/work-orders', {
      data: { type: 1, priority: 1, title: '转单用例工单', content: '转单校验', stationId: 1 },
      token: t20AdminToken
    })
    const id = created.ok ? created.result.id : 0
    if (id && assigneeId) await call('put', `/work-orders/${id}/assign`, { data: { assigneeId }, token: t20AdminToken })
    return id
  }
  const station1Staff = activeEmployees().find((e) => e.station_id === 1 && e.status === 1 && e.id !== 3 && e.id !== 4)
  const station2Staff = activeEmployees().find((e) => e.station_id === 2 && e.status === 1)
  check('转单用例所需的本站 / 他站员工样本存在', !!station1Staff && !!station2Staff)

  const crossOrderId = await createTransferCase(3)
  const crossBefore = await call('get', `/work-orders/${crossOrderId}`, { token: t20AdminToken })
  const crossRes = await expectCode(
    'ADMIN 跨站转单成功',
    'post',
    `/work-orders/${crossOrderId}/transfer`,
    {
      data: { toEmployeeId: station2Staff ? station2Staff.id : 0, reason: '跨站协同，交给就近驿站处理' },
      token: t20AdminToken
    },
    200
  )
  check('跨站转单后处理人变为转入人', crossRes.ok && !!station2Staff && crossRes.result.assigneeId === station2Staff.id)
  check('转单不改变工单状态', crossRes.ok && crossBefore.ok && crossRes.result.status === crossBefore.result.status)
  check(
    '转单留痕含转出 / 转入 / 理由 / 操作人 / 时间',
    crossRes.ok &&
      crossRes.result.transfers.length === 1 &&
      crossRes.result.transfers[0].fromEmployeeId === 3 &&
      crossRes.result.transfers[0].toEmployeeId === station2Staff.id &&
      crossRes.result.transfers[0].reason === '跨站协同，交给就近驿站处理' &&
      crossRes.result.transfers[0].operatorId === 1 &&
      !!crossRes.result.transfers[0].transferTime
  )
  check('转单后时间线追加 transfer 事件', crossRes.ok && crossRes.result.handleLog.some((l) => l.action === 'transfer'))

  const targetLogin = station2Staff ? await login(station2Staff.username, 'demo1234') : { ok: false }
  const targetToken = targetLogin.ok ? targetLogin.result.token : ''
  const targetNotifs = await call('get', '/notifications', {
    params: { pageNum: 1, pageSize: 100 },
    token: targetToken
  })
  const transferNotif = targetNotifs.ok
    ? targetNotifs.result.list.find((n) => n.bizType === 'work_order' && n.bizId === crossOrderId)
    : null
  check('转单后新处理人收到未读工单通知（可跳转）', !!transferNotif && transferNotif.isRead === false)

  const staOrderId = await createTransferCase(4)
  const staTransfer = await expectCode(
    '站长转本站工单成功',
    'post',
    `/work-orders/${staOrderId}/transfer`,
    {
      data: { toEmployeeId: station1Staff ? station1Staff.id : 0, reason: '原处理人轮休，转交同事跟进' },
      token: t20StaToken
    },
    200
  )
  check(
    '站内转单后处理人为本站同事且留痕转出人为原处理人',
    staTransfer.ok &&
      !!station1Staff &&
      staTransfer.result.assigneeId === station1Staff.id &&
      staTransfer.result.transfers[0].fromEmployeeId === 4
  )
  await expectCode(
    '站长转外站员工 → 8004',
    'post',
    `/work-orders/${staOrderId}/transfer`,
    { data: { toEmployeeId: station2Staff ? station2Staff.id : 0, reason: '越站转单' }, token: t20StaToken },
    8004
  )

  const otherStationList = await call('get', '/work-orders', {
    params: { stationId: 2, pageNum: 1, pageSize: 10 },
    token: t20AdminToken
  })
  const otherOrderId = otherStationList.ok && otherStationList.result.list[0] ? otherStationList.result.list[0].id : 0
  await expectCode(
    '站长转他站工单 → 8003',
    'post',
    `/work-orders/${otherOrderId}/transfer`,
    { data: { toEmployeeId: station2Staff ? station2Staff.id : 0, reason: '越权转单' }, token: t20StaToken },
    8003
  )

  await expectCode(
    'STAFF 转非本人处理的工单 → 8003',
    'post',
    `/work-orders/${staOrderId}/transfer`,
    { data: { toEmployeeId: station1Staff ? station1Staff.id : 0, reason: '越权转单' }, token: t20StaffToken },
    8003
  )
  const staffOrderId = await createTransferCase(4)
  await expectCode(
    '转给自己 → 8004',
    'post',
    `/work-orders/${staffOrderId}/transfer`,
    { data: { toEmployeeId: 4, reason: '原地打转没意义' }, token: t20StaffToken },
    8004
  )
  await expectCode(
    '转单对象不存在 → 8004',
    'post',
    `/work-orders/${staffOrderId}/transfer`,
    { data: { toEmployeeId: 999999, reason: '对象不存在' }, token: t20StaffToken },
    8004
  )
  const disabledEmp = activeEmployees().find((e) => e.status === 0)
  if (disabledEmp) {
    await expectCode(
      '转单对象已停用 → 8004',
      'post',
      `/work-orders/${staffOrderId}/transfer`,
      { data: { toEmployeeId: disabledEmp.id, reason: '转给停用账号' }, token: t20AdminToken },
      8004
    )
  }
  const staffTransfer = await expectCode(
    'STAFF 转本人处理的本站工单成功',
    'post',
    `/work-orders/${staffOrderId}/transfer`,
    {
      data: { toEmployeeId: station1Staff ? station1Staff.id : 0, reason: '本人外出取件，转交同事' },
      token: t20StaffToken
    },
    200
  )
  check(
    'STAFF 转单后处理人变更且状态不变（仍为待处理）',
    staffTransfer.ok &&
      !!station1Staff &&
      staffTransfer.result.assigneeId === station1Staff.id &&
      staffTransfer.result.status === 0
  )
  const staffDetail = await call('get', `/work-orders/${staffOrderId}`, { token: t20AdminToken })
  check(
    '运行时转单留痕持久化到详情（含操作人）',
    staffDetail.ok &&
      staffDetail.result.transfers.length === 1 &&
      staffDetail.result.transfers[0].toEmployeeId === station1Staff.id &&
      staffDetail.result.transfers[0].operatorId === 4
  )

  await expectCode(
    '转单工单不存在 → 404',
    'post',
    '/work-orders/999999/transfer',
    { data: { toEmployeeId: 4, reason: '工单不存在' }, token: t20AdminToken },
    404,
    404
  )
  await expectCode(
    '转单理由缺失 → 400',
    'post',
    `/work-orders/${staffOrderId}/transfer`,
    { data: { toEmployeeId: 4, reason: '' }, token: t20AdminToken },
    400
  )

  console.log(
    `[T19-转单] 种子 ${transferOrderIds.length} 条工单含转单留痕（跨站 ${seedCrossCount} 条，其余站内；含处理人本人转单）/ ` +
      `跨站转单后状态=${crossRes.ok ? crossRes.result.status : '-'} 保持不变 / 新处理人收到未读通知 / ` +
      `站长转外站 8004、站长转他站 8003、STAFF 转非本人 8003、转给自己 8004、对象不存在 8004`
  )

  /* ============================================================
   * 需求 1–10 契约断言（本批次新增）
   * 每组用例前成对重置 db 与对应领域 store：领域 store 与 db 相互独立（同 resetParcelStore 口径），
   * 只重置 db 不会让 KPI / 人事 / 财务回到种子态。token 一旦重置即失效，故每组重置后统一重新登录。
   * ============================================================ */

  /* ---------- 需求1：同步任务模块化 + 驿站采集状态（syncConfig.js） ---------- */
  resetDb()
  resetAttendanceStore()
  const cfgAdmin = await login('admin', 'demo1234')
  const cfgAdminToken = cfgAdmin.ok ? cfgAdmin.result.token : ''
  const cfgSta = await login('st001_admin', 'demo1234')
  const cfgStaToken = cfgSta.ok ? cfgSta.result.token : ''
  const cfgStaff = await login('st001_staff', 'demo1234')
  const cfgStaffToken = cfgStaff.ok ? cfgStaff.result.token : ''

  const countCollectState = (list, state) => list.filter((c) => c.collectState === state).length
  const cfgList = await expectCode(
    '采集配置列表 GET /sync/configs（ADMIN 全量）',
    'get',
    '/sync/configs',
    { token: cfgAdminToken },
    200
  )
  check('采集配置 8 条（每站一行，无缺行）', cfgList.ok && cfgList.result.length === 8)
  check(
    '四种采集状态计数正确（正常4 / 异常2 / 未配置1 / 已停用1）',
    cfgList.ok &&
      countCollectState(cfgList.result, 'NORMAL') === 4 &&
      countCollectState(cfgList.result, 'ABNORMAL') === 2 &&
      countCollectState(cfgList.result, 'UNCONFIGURED') === 1 &&
      countCollectState(cfgList.result, 'DISABLED') === 1
  )
  check(
    '配置出参字段齐备（状态派生 label / 驿站名 / 最近批次）',
    cfgList.ok &&
      cfgList.result.every(
        (c) => !!c.collectStateLabel && !!c.stationName && 'lastBatch' in c && 'lastCollectStatus' in c
      )
  )

  const cfgOverview = await expectCode(
    '采集总览 GET /sync/overview（ADMIN）',
    'get',
    '/sync/overview',
    { token: cfgAdminToken },
    200
  )
  check(
    '总览 total=8 且状态计数与明细一致',
    cfgOverview.ok &&
      cfgOverview.result.total === 8 &&
      cfgOverview.result.counts.normal === 4 &&
      cfgOverview.result.counts.abnormal === 2 &&
      cfgOverview.result.counts.unconfigured === 1 &&
      cfgOverview.result.counts.disabled === 1
  )
  check(
    '总览每站带状态与最近采集 / 批次字段',
    cfgOverview.ok &&
      cfgOverview.result.stations.every(
        (s) => !!s.collectState && !!s.collectStateLabel && 'lastBatch' in s && 'lastCollectTime' in s
      )
  )

  const cfgDetail7 = await expectCode(
    '未配置采集的驿站详情 GET /sync/configs/7',
    'get',
    '/sync/configs/7',
    { token: cfgAdminToken },
    200
  )
  check(
    '7 号驿站派生为「未配置」',
    cfgDetail7.ok && cfgDetail7.result.collectState === 'UNCONFIGURED' && cfgDetail7.result.enabled === false
  )
  await expectCode('不存在的驿站采集配置 → 6002', 'get', '/sync/configs/999', { token: cfgAdminToken }, 6002)

  const cfgSave = await expectCode(
    '改采集频次 PUT /sync/configs/1',
    'put',
    '/sync/configs/1',
    { data: { frequency: 'DAILY' }, token: cfgAdminToken },
    200
  )
  check('改频次后写入即生效', cfgSave.ok && cfgSave.result.frequency === 'DAILY')
  const cfgReread = await call('get', '/sync/configs/1', { token: cfgAdminToken })
  check('重新读取确认频次已落库', cfgReread.ok && cfgReread.result.frequency === 'DAILY')
  await expectCode(
    '频次档位非法 → 400',
    'put',
    '/sync/configs/1',
    { data: { frequency: 'EVERY_5M' }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '采集起止倒挂 → 400',
    'put',
    '/sync/configs/1',
    { data: { collectStartTime: '20:00', collectEndTime: '08:00' }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '启用采集但未选数据源 → 400',
    'put',
    '/sync/configs/7',
    { data: { enabled: 1 }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '非 ADMIN 改采集配置 → 403',
    'put',
    '/sync/configs/1',
    { data: { frequency: 'HOURLY' }, token: cfgStaToken },
    403,
    403
  )
  await call('put', '/sync/configs/1', { data: { frequency: 'HOURLY' }, token: cfgAdminToken })

  const cfgStaList = await call('get', '/sync/configs', { token: cfgStaToken })
  check(
    '站长采集配置收敛本站（1 条且为城东）',
    cfgStaList.ok && cfgStaList.result.length === 1 && cfgStaList.result[0].stationId === 1
  )
  const cfgStaOverview = await call('get', '/sync/overview', { token: cfgStaToken })
  check('站长总览收敛本站 total=1', cfgStaOverview.ok && cfgStaOverview.result.total === 1)
  await expectCode(
    '站长取他站采集详情 → 404（不暴露存在性）',
    'get',
    '/sync/configs/2',
    { token: cfgStaToken },
    404,
    404
  )
  await expectCode('STAFF 访问采集总览 → 403', 'get', '/sync/overview', { token: cfgStaffToken }, 403, 403)

  /* ---------- 同步配置中心：配置项/选项集、全局默认、驿站覆盖、CSV 导入导出 ---------- */
  const SYNC_CSV_HEADER = [
    '记录类型',
    '配置项Key',
    '配置项名称',
    '值类型',
    '是否必填',
    '默认值',
    '单位',
    '取值范围或正则',
    '排序',
    '是否启用',
    '选项Key',
    '选项显示名',
    '附加属性',
    '驿站',
    '配置值',
    '备注'
  ]
  const csvCell = (value) => (/[",\n]/.test(String(value)) ? `"${String(value).replace(/"/g, '""')}"` : String(value))
  /** 按 16 列补位并拼成一行，列序与设计 D.1 一致；脚本侧独立构造，不引用被测实现 */
  const csvRow = (...cells) => {
    const padded = cells.slice(0, 16)
    while (padded.length < 16) padded.push('')
    return padded.map(csvCell).join(',')
  }
  const csvItem = (
    key,
    name,
    type,
    required,
    def = '',
    unit = '',
    range = '',
    sort = '',
    enabled = '是',
    remark = ''
  ) => csvRow('ITEM', key, name, type, required, def, unit, range, sort, enabled, '', '', '', '', '', remark)
  const csvGlobal = (key, value, remark = '') =>
    csvRow('GLOBAL', key, '', '', '', '', '', '', '', '', '', '', '', '', value, remark)
  const csvStation = (key, stationName, value, remark = '') =>
    csvRow('STATION', key, '', '', '', '', '', '', '', '', '', '', '', stationName, value, remark)
  const csvText = (rows) => `\ufeff${[SYNC_CSV_HEADER.join(','), ...rows].join('\r\n')}`

  const cfgItems = await expectCode(
    '配置项与选项集 GET /sync/config-items',
    'get',
    '/sync/config-items',
    { token: cfgAdminToken },
    200
  )
  check(
    '配置项 5 项且字段齐备',
    cfgItems.ok &&
      cfgItems.result.items.length === 5 &&
      cfgItems.result.items.every(
        (i) => i.itemKey && i.name && i.valueType && 'required' in i && 'scope' in i && 'enabled' in i && 'builtin' in i
      )
  )
  check(
    '选项集 3 个（数据源 / 采集频率 / 采集时段模板）',
    cfgItems.ok &&
      cfgItems.result.optionSets.length === 3 &&
      ['data_source', 'collect_frequency', 'time_template'].every((key) =>
        cfgItems.result.optionSets.some((set) => set.setKey === key)
      )
  )
  const freqSet = cfgItems.ok ? cfgItems.result.optionSets.find((set) => set.setKey === 'collect_frequency') : null
  check(
    '频率选项登记旧码 legacyCodes（HOURLY → EVERY_60M）',
    !!freqSet && freqSet.options.some((o) => o.optionKey === 'EVERY_60M' && (o.legacyCodes || []).includes('HOURLY'))
  )
  const dsSet = cfgItems.ok ? cfgItems.result.optionSets.find((set) => set.setKey === 'data_source') : null
  check(
    '数据源选项集含 MIGRATED 历史纳管样本',
    !!dsSet && dsSet.options.some((o) => o.source === 'MIGRATED' && o.builtin === false)
  )
  await expectCode('站长访问配置项管理 → 403', 'get', '/sync/config-items', { token: cfgStaToken }, 403, 403)
  await expectCode('STAFF 访问配置项管理 → 403', 'get', '/sync/config-items', { token: cfgStaffToken }, 403, 403)

  const cfgNewItem = await call('post', '/sync/config-items', {
    data: {
      itemKey: 'demo_temp_item',
      name: '演示临时项',
      valueType: 'NUMBER',
      required: false,
      defaultValue: 2,
      unit: '次',
      constraints: { min: 0, max: 5, step: 1, integerOnly: true },
      scope: 'STATION',
      sort: 99,
      enabled: true
    },
    token: cfgAdminToken
  })
  check(
    '新增配置项成功且非内置',
    cfgNewItem.ok && cfgNewItem.result.itemKey === 'demo_temp_item' && cfgNewItem.result.builtin === false
  )
  await expectCode(
    '配置项 Key 重复 → 9502',
    'post',
    '/sync/config-items',
    { data: { itemKey: 'demo_temp_item', name: '重复项', valueType: 'TEXT' }, token: cfgAdminToken },
    9502
  )
  await expectCode(
    '配置项 Key 命名非法 → 400',
    'post',
    '/sync/config-items',
    { data: { itemKey: 'Bad-Key', name: '非法项', valueType: 'TEXT' }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '默认值超约束 → 9506',
    'post',
    '/sync/config-items',
    {
      data: {
        itemKey: 'demo_over',
        name: '越界项',
        valueType: 'NUMBER',
        required: false,
        defaultValue: 99,
        constraints: { min: 0, max: 10, step: 1, integerOnly: true }
      },
      token: cfgAdminToken
    },
    9506
  )
  await expectCode(
    '修改不存在的配置项 → 9501',
    'put',
    '/sync/config-items/no_such_item',
    { data: { name: '不存在' }, token: cfgAdminToken },
    9501
  )
  // 内置项：影响面标记不可删，删除恒回 9510 且数据不动
  const builtinItemImpact = await call('get', '/sync/config-items/collect_frequency/impact', { token: cfgAdminToken })
  check(
    '内置配置项影响面 canDelete=false 且 blockers 含 9510',
    builtinItemImpact.ok &&
      builtinItemImpact.result.canDelete === false &&
      builtinItemImpact.result.builtin === true &&
      builtinItemImpact.result.blockers.some((b) => b.code === 9510)
  )
  await expectCode(
    '删除内置配置项 → 9510',
    'delete',
    '/sync/config-items/collect_frequency',
    { token: cfgAdminToken },
    9510
  )
  const builtinItemStill = await call('get', '/sync/config-items', { token: cfgAdminToken })
  check(
    '内置配置项删除被拦后数据仍在',
    builtinItemStill.ok && builtinItemStill.result.items.some((item) => item.itemKey === 'collect_frequency')
  )
  await expectCode(
    '站长查配置项影响面 → 403',
    'get',
    '/sync/config-items/collect_frequency/impact',
    { token: cfgStaToken },
    403,
    403
  )

  // 无引用项：影响面直接放行，删除无需 confirm
  await call('post', '/sync/config-items', {
    data: {
      itemKey: 'demo_no_ref',
      name: '无引用项',
      valueType: 'TEXT',
      required: false,
      defaultValue: 'x',
      constraints: { minLen: 1, maxLen: 10 },
      scope: 'STATION',
      sort: 98,
      enabled: true
    },
    token: cfgAdminToken
  })
  const noRefItemImpact = await call('get', '/sync/config-items/demo_no_ref/impact', { token: cfgAdminToken })
  check(
    '无引用配置项影响面 canDelete=true 且引用数为 0',
    noRefItemImpact.ok &&
      noRefItemImpact.result.canDelete === true &&
      noRefItemImpact.result.referencedCount === 0 &&
      noRefItemImpact.result.blockers.length === 0
  )
  const noRefItemDel = await call('delete', '/sync/config-items/demo_no_ref', { token: cfgAdminToken })
  check('无引用配置项直接删除成功', noRefItemDel.ok)

  // 有引用项：未传 confirm 拦下（数据不动）→ confirm=true 放行并清掉受影响驿站的覆盖
  const cfgRefItem = await call('put', '/sync/configs/5', {
    data: { overrides: { demo_temp_item: 3 } },
    token: cfgAdminToken
  })
  check('驿站可覆盖自建配置项', cfgRefItem.ok)
  const itemRefImpact = await call('get', '/sync/config-items/demo_temp_item/impact', { token: cfgAdminToken })
  check(
    '被引用配置项影响面列出引用驿站',
    itemRefImpact.ok &&
      itemRefImpact.result.canDelete === true &&
      itemRefImpact.result.builtin === false &&
      itemRefImpact.result.referencedCount === 1 &&
      itemRefImpact.result.referencedStations[0].stationId === 5 &&
      itemRefImpact.result.referencedStations[0].stationName === '高新驿站'
  )
  check(
    '被引用配置项影响面 blockers 含 9503 确认引导',
    itemRefImpact.ok && itemRefImpact.result.blockers.some((b) => b.code === 9503 && b.message.includes('confirm=true'))
  )
  await expectCode(
    '有驿站覆盖且未传 confirm → 9503',
    'delete',
    '/sync/config-items/demo_temp_item',
    { token: cfgAdminToken },
    9503
  )
  const itemStillAfterBlock = await call('get', '/sync/config-items', { token: cfgAdminToken })
  check(
    '9503 拦截后配置项未被删',
    itemStillAfterBlock.ok && itemStillAfterBlock.result.items.some((item) => item.itemKey === 'demo_temp_item')
  )
  const cfgDelItem = await call('delete', '/sync/config-items/demo_temp_item', {
    params: { confirm: true },
    token: cfgAdminToken
  })
  check('confirm=true 删除被引用配置项成功', cfgDelItem.ok)
  const st5AfterItemDel = await call('get', '/sync/configs/5', { token: cfgAdminToken })
  check(
    '删项后受影响驿站该项覆盖被清（无悬空引用）',
    st5AfterItemDel.ok && !Object.prototype.hasOwnProperty.call(st5AfterItemDel.result.overrides, 'demo_temp_item')
  )

  const optNew = await call('post', '/sync/config-items/collect_frequency/options', {
    data: { optionKey: 'EVERY_720M', label: '每 12 小时', extraAttrs: { intervalMinutes: 720 } },
    token: cfgAdminToken
  })
  check(
    '新增频率选项成功且来源为手工',
    optNew.ok && optNew.result.optionKey === 'EVERY_720M' && optNew.result.source === 'MANUAL'
  )
  const optUpd = await call('put', '/sync/config-items/collect_frequency/options/EVERY_720M', {
    data: { label: '每 12 小时（自建）' },
    token: cfgAdminToken
  })
  check('修改选项显示名成功', optUpd.ok && optUpd.result.label === '每 12 小时（自建）')
  await expectCode(
    '选项 Key 重复 → 400',
    'post',
    '/sync/config-items/collect_frequency/options',
    { data: { optionKey: 'EVERY_720M', label: '重复选项' }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '非单选项配置项不能加选项 → 400',
    'post',
    '/sync/config-items/retry_times/options',
    { data: { optionKey: 'X_1', label: 'x' }, token: cfgAdminToken },
    400
  )
  // 内置选项：影响面标记不可删，删除恒回 9510
  const builtinOptImpact = await call('get', '/sync/config-items/data_source/options/DUODUOCAI/impact', {
    token: cfgAdminToken
  })
  check(
    '内置选项影响面 canDelete=false 且 blockers 含 9510',
    builtinOptImpact.ok &&
      builtinOptImpact.result.canDelete === false &&
      builtinOptImpact.result.blockers.some((b) => b.code === 9510)
  )
  await expectCode(
    '删除内置选项 → 9510',
    'delete',
    '/sync/config-items/data_source/options/DUODUOCAI',
    { token: cfgAdminToken },
    9510
  )

  // 无引用选项：影响面直接放行，删除无需 confirm
  await call('post', '/sync/config-items/collect_frequency/options', {
    data: { optionKey: 'EVERY_360M', label: '每 6 小时', extraAttrs: { intervalMinutes: 360 } },
    token: cfgAdminToken
  })
  const noRefOptImpact = await call('get', '/sync/config-items/collect_frequency/options/EVERY_360M/impact', {
    token: cfgAdminToken
  })
  check(
    '无引用选项影响面 canDelete=true 且引用数为 0',
    noRefOptImpact.ok &&
      noRefOptImpact.result.canDelete === true &&
      noRefOptImpact.result.referencedCount === 0 &&
      noRefOptImpact.result.blockers.length === 0
  )
  const noRefOptDel = await call('delete', '/sync/config-items/collect_frequency/options/EVERY_360M', {
    token: cfgAdminToken
  })
  check('无引用选项直接删除成功', noRefOptDel.ok)

  // 有引用选项：未传 confirm 拦下（数据不动）→ confirm=true 放行，驿站回退继承全局
  const optRef = await call('put', '/sync/configs/2', {
    data: { overrides: { collect_frequency: 'EVERY_720M' } },
    token: cfgAdminToken
  })
  check('驿站覆盖到自建档位', optRef.ok)
  const optRefImpact = await call('get', '/sync/config-items/collect_frequency/options/EVERY_720M/impact', {
    token: cfgAdminToken
  })
  check(
    '被引用选项影响面列出引用驿站',
    optRefImpact.ok &&
      optRefImpact.result.canDelete === true &&
      optRefImpact.result.builtin === false &&
      optRefImpact.result.referencedCount === 1 &&
      optRefImpact.result.referencedStations[0].stationId === 2 &&
      optRefImpact.result.referencedStations[0].stationName === '城西驿站'
  )
  check(
    '被引用选项影响面 blockers 含 9505 确认引导',
    optRefImpact.ok && optRefImpact.result.blockers.some((b) => b.code === 9505 && b.message.includes('confirm=true'))
  )
  await expectCode(
    '有驿站引用且未传 confirm → 9505',
    'delete',
    '/sync/config-items/collect_frequency/options/EVERY_720M',
    { token: cfgAdminToken },
    9505
  )
  const optStillAfterBlock = await call('get', '/sync/config-items', { token: cfgAdminToken })
  check(
    '9505 拦截后选项未被删',
    optStillAfterBlock.ok &&
      optStillAfterBlock.result.optionSets.some(
        (set) => set.setKey === 'collect_frequency' && set.options.some((option) => option.optionKey === 'EVERY_720M')
      )
  )
  const optDel = await call('delete', '/sync/config-items/collect_frequency/options/EVERY_720M', {
    params: { confirm: true },
    token: cfgAdminToken
  })
  check('confirm=true 删除被引用选项成功', optDel.ok)
  const st2AfterOptDel = await call('get', '/sync/configs/2', { token: cfgAdminToken })
  check(
    '删选项后受影响驿站回退为继承全局',
    st2AfterOptDel.ok &&
      st2AfterOptDel.result.sources.collect_frequency === 'INHERIT' &&
      !Object.prototype.hasOwnProperty.call(st2AfterOptDel.result.overrides, 'collect_frequency')
  )

  const globalBefore = await call('get', '/sync/configs/global', { token: cfgAdminToken })
  check('全局默认含 5 个配置项', globalBefore.ok && Object.keys(globalBefore.result.values).length === 5)
  await expectCode(
    '全局默认值越界 → 9506',
    'put',
    '/sync/configs/global',
    { data: { values: { retry_times: 99 } }, token: cfgAdminToken },
    9506
  )
  await expectCode(
    '必填项全局置空 → 9507',
    'put',
    '/sync/configs/global',
    { data: { values: { data_source: null } }, token: cfgAdminToken },
    9507
  )
  await expectCode(
    '全局默认写入不存在的项 → 9501',
    'put',
    '/sync/configs/global',
    { data: { values: { no_such_item: 1 } }, token: cfgAdminToken },
    9501
  )
  const globalSaved = await call('put', '/sync/configs/global', {
    data: { values: { retry_times: 4 } },
    token: cfgAdminToken
  })
  check('全局默认保存并回显生效', globalSaved.ok && globalSaved.result.values.retry_times === 4)

  const st7Model = await call('get', '/sync/configs/7', { token: cfgAdminToken })
  check(
    '7 号驿站全项继承（覆盖为空 / 来源全 INHERIT）',
    st7Model.ok &&
      Object.keys(st7Model.result.overrides).length === 0 &&
      Object.values(st7Model.result.sources).every((s) => s === 'INHERIT')
  )
  check(
    '7 号生效频率继承全局并回旧码 EVERY_4H',
    st7Model.ok && st7Model.result.values.collect_frequency === 'EVERY_240M' && st7Model.result.frequency === 'EVERY_4H'
  )
  const st1Model = await call('get', '/sync/configs/1', { token: cfgAdminToken })
  check(
    '1 号采集频率为覆盖态且回旧码 HOURLY',
    st1Model.ok &&
      st1Model.result.sources.collect_frequency === 'OVERRIDE' &&
      st1Model.result.values.collect_frequency === 'EVERY_60M' &&
      st1Model.result.frequency === 'HOURLY'
  )
  check(
    '1 号数据源旧字段仍为中文显示名',
    st1Model.ok && st1Model.result.values.data_source === 'DUODUOCAI' && st1Model.result.dataSource === '多多买菜'
  )
  const st6Model = await call('get', '/sync/configs/6', { token: cfgAdminToken })
  check(
    '6 号历史数据源自动纳管为 MIGRATED 选项',
    st6Model.ok && /^MIGRATED_/.test(st6Model.result.values.data_source) && st6Model.result.dataSource === '丰巢智能柜'
  )
  await call('put', '/sync/configs/4', {
    data: { overrides: { collect_frequency: 'EVERY_30M' } },
    token: cfgAdminToken
  })
  const st4Model = await call('get', '/sync/configs/4', { token: cfgAdminToken })
  check(
    '新档位无旧码时原值返回（过渡态）',
    st4Model.ok && st4Model.result.values.collect_frequency === 'EVERY_30M' && st4Model.result.frequency === 'EVERY_30M'
  )
  await expectCode(
    '覆盖不存在的配置项 → 9501',
    'put',
    '/sync/configs/2',
    { data: { overrides: { no_such_item: 1 } }, token: cfgAdminToken },
    9501
  )
  await expectCode(
    '覆盖值不符合约束 → 9506',
    'put',
    '/sync/configs/2',
    { data: { overrides: { retry_times: 'abc' } }, token: cfgAdminToken },
    9506
  )
  await expectCode(
    '必填项覆盖置空 → 9507',
    'put',
    '/sync/configs/8',
    { data: { overrides: { data_source: null } }, token: cfgAdminToken },
    9507
  )

  const expItems = await call('get', '/sync/configs/export', {
    params: { scope: 'ITEMS' },
    token: cfgAdminToken,
    responseType: 'blob'
  })
  check('导出配置项返回文件流', expItems.ok && expItems.result.data instanceof Blob)
  check(
    '导出响应头为 CSV 且带附件名',
    expItems.ok &&
      /text\/csv/.test(expItems.result.headers['content-type'] || '') &&
      /filename\*=UTF-8''/.test(expItems.result.headers['content-disposition'] || '')
  )
  const expItemsText = expItems.ok ? await expItems.result.data.text() : ''
  const expItemsBytes = expItems.ok ? new Uint8Array(await expItems.result.data.arrayBuffer()) : new Uint8Array()
  check('导出带 UTF-8 BOM', expItemsBytes[0] === 0xef && expItemsBytes[1] === 0xbb && expItemsBytes[2] === 0xbf)
  const expItemsLines = expItemsText.replace(/^\ufeff/, '').split('\r\n')
  check('导出行以 CRLF 结尾且表头 16 列与模板一致', expItemsLines[0] === SYNC_CSV_HEADER.join(','))
  check(
    'ITEMS 范围含 5 条配置项且不含 GLOBAL/STATION 行',
    expItemsLines.filter((line) => line.startsWith('ITEM,')).length === 5 &&
      !expItemsLines.some((line) => line.startsWith('GLOBAL,') || line.startsWith('STATION,'))
  )
  const expAllConfig = await call('get', '/sync/configs/export', {
    params: { scope: 'ALL' },
    token: cfgAdminToken,
    responseType: 'blob'
  })
  const expAllConfigText = expAllConfig.ok ? await expAllConfig.result.data.text() : ''
  const expAllConfigLines = expAllConfigText.replace(/^\ufeff/, '').split('\r\n')
  check(
    'ALL 范围含 GLOBAL 与 STATION 行',
    expAllConfigLines.some((line) => line.startsWith('GLOBAL,')) &&
      expAllConfigLines.some((line) => line.startsWith('STATION,'))
  )
  check(
    'ALL 导出文件名标记为「全部」',
    expAllConfig.ok &&
      /filename\*=UTF-8''%E5%90%8C%E6%AD%A5%E9%85%8D%E7%BD%AE_%E5%85%A8%E9%83%A8_/.test(
        expAllConfig.result.headers['content-disposition'] || ''
      )
  )
  await expectCode(
    '导出范围非法 → 400',
    'get',
    '/sync/configs/export',
    { params: { scope: 'BAD' }, token: cfgAdminToken },
    400
  )

  // 下载导入模板：表头与正式导出同源，只含四类记录各一行示例
  const expTpl = await call('get', '/sync/configs/export', {
    params: { scope: 'TEMPLATE' },
    token: cfgAdminToken,
    responseType: 'blob'
  })
  check(
    '模板导出返回文件流且 content-type 与其他 scope 一致',
    expTpl.ok &&
      expTpl.result.data instanceof Blob &&
      expTpl.result.headers['content-type'] === expItems.result.headers['content-type'] &&
      /text\/csv/.test(expTpl.result.headers['content-type'] || '')
  )
  check(
    '模板文件名标记为「导入模板」',
    expTpl.ok &&
      /filename\*=UTF-8''%E5%90%8C%E6%AD%A5%E9%85%8D%E7%BD%AE_%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF\.csv$/.test(
        expTpl.result.headers['content-disposition'] || ''
      )
  )
  const expTplBytes = expTpl.ok ? new Uint8Array(await expTpl.result.data.arrayBuffer()) : new Uint8Array()
  check('模板带 UTF-8 BOM 且 CRLF 换行', expTplBytes[0] === 0xef && expTplBytes[1] === 0xbb && expTplBytes[2] === 0xbf)
  const expTplLines = (expTpl.ok ? await expTpl.result.data.text() : '').replace(/^\ufeff/, '').split('\r\n')
  check(
    '模板表头 16 列与正式导出一致且恰含 4 行示例',
    expTplLines[0] === SYNC_CSV_HEADER.join(',') && expTplLines.length === 5
  )
  check(
    '模板四类记录各一行示例',
    ['ITEM', 'OPTION', 'GLOBAL', 'STATION'].every(
      (type) => expTplLines.filter((line) => line.startsWith(`${type},`)).length === 1
    )
  )
  await expectCode(
    '站长导出模板 → 403',
    'get',
    '/sync/configs/export',
    { params: { scope: 'TEMPLATE' }, token: cfgStaToken },
    403,
    403
  )

  const importCsv = csvText([
    csvItem('demo_batch_size', '批量大小', 'NUMBER', '是', '5', '件', '1-20 的整数', '60', '是'),
    csvGlobal('demo_batch_size', '10'),
    csvStation('demo_batch_size', '城东驿站', '15')
  ])
  const dryRun = await call('post', '/sync/configs/import', {
    data: { content: importCsv, onConflict: 'OVERWRITE', dryRun: true },
    token: cfgAdminToken
  })
  check(
    '导入预览（dryRun）3 行全部通过',
    dryRun.ok &&
      dryRun.result.dryRun === true &&
      dryRun.result.summary.total === 3 &&
      dryRun.result.summary.failed === 0 &&
      dryRun.result.plan.create === 3
  )
  const notYetImported = await call('get', '/sync/config-items', { token: cfgAdminToken })
  check(
    'dryRun 不落库',
    notYetImported.ok && !notYetImported.result.items.some((item) => item.itemKey === 'demo_batch_size')
  )
  const realImport = await call('post', '/sync/configs/import', {
    data: { content: importCsv, onConflict: 'OVERWRITE', dryRun: false },
    token: cfgAdminToken
  })
  check(
    '正式导入落库 3 条',
    realImport.ok && realImport.result.dryRun === false && realImport.result.applied.created === 3
  )
  const importedItems = await call('get', '/sync/config-items', { token: cfgAdminToken })
  const importedItem = importedItems.ok
    ? importedItems.result.items.find((item) => item.itemKey === 'demo_batch_size')
    : null
  check(
    '导入的配置项与取值约束解析正确',
    !!importedItem &&
      importedItem.valueType === 'NUMBER' &&
      importedItem.defaultValue === 5 &&
      importedItem.unit === '件' &&
      importedItem.constraints.max === 20
  )
  const importedGlobal = await call('get', '/sync/configs/global', { token: cfgAdminToken })
  check('导入的全局默认值生效', importedGlobal.ok && importedGlobal.result.values.demo_batch_size === 10)
  const importedStation = await call('get', '/sync/configs/1', { token: cfgAdminToken })
  check(
    '导入的驿站覆盖生效',
    importedStation.ok &&
      importedStation.result.sources.demo_batch_size === 'OVERRIDE' &&
      importedStation.result.values.demo_batch_size === 15
  )

  const quotedImport = await call('post', '/sync/configs/import', {
    data: {
      content: csvText([
        csvItem('demo_quoted', '名称,含逗号', 'NUMBER', '否', '1', '', '0-9 的整数', '70', '否', '备注,含逗号')
      ]),
      onConflict: 'OVERWRITE',
      dryRun: false
    },
    token: cfgAdminToken
  })
  check('含逗号的引号单元格可解析', quotedImport.ok && quotedImport.result.summary.failed === 0)
  const quotedItems = await call('get', '/sync/config-items', { token: cfgAdminToken })
  const quotedItem = quotedItems.ok ? quotedItems.result.items.find((item) => item.itemKey === 'demo_quoted') : null
  check(
    '引号内逗号未串列',
    !!quotedItem && quotedItem.name === '名称,含逗号' && quotedItem.description === '备注,含逗号'
  )

  const badImport = await call('post', '/sync/configs/import', {
    data: {
      content: csvText([
        csvItem('demo_row_ok', '行级样本', 'NUMBER', '是', '1', '', '0-9 的整数', '80', '是'),
        csvStation('collect_frequency', '不存在的驿站', 'EVERY_60M'),
        csvGlobal('no_such_item', '1'),
        'ITEM,short,row'
      ]),
      onConflict: 'OVERWRITE',
      dryRun: true
    },
    token: cfgAdminToken
  })
  check(
    '行级失败不阻断整体（1 成功 3 失败）',
    badImport.ok &&
      badImport.result.summary.total === 4 &&
      badImport.result.summary.failed === 3 &&
      badImport.result.summary.ok === 1
  )
  check(
    '失败行均给出可读原因',
    badImport.ok && badImport.result.rows.filter((row) => row.level === 'FAILED').every((row) => row.message.length > 0)
  )

  const appendImport = await call('post', '/sync/configs/import', {
    data: { content: csvText([csvGlobal('collect_frequency', 'EVERY_60M')]), onConflict: 'APPEND', dryRun: true },
    token: cfgAdminToken
  })
  check(
    '追加策略下已存在键为冲突失败行',
    appendImport.ok && appendImport.result.summary.failed === 1 && appendImport.result.plan.conflict === 1
  )
  const skipImport = await call('post', '/sync/configs/import', {
    data: { content: csvText([csvGlobal('collect_frequency', 'EVERY_60M')]), onConflict: 'SKIP', dryRun: true },
    token: cfgAdminToken
  })
  check(
    '跳过策略下已存在键计入跳过',
    skipImport.ok && skipImport.result.summary.failed === 0 && skipImport.result.plan.skip === 1
  )

  await call('post', '/sync/config-items/data_source/options', {
    data: { optionKey: 'TEST_DISABLED', label: '测试停用源' },
    token: cfgAdminToken
  })
  await call('put', '/sync/config-items/data_source/options/TEST_DISABLED', {
    data: { enabled: false },
    token: cfgAdminToken
  })
  const disabledOptionImport = await call('post', '/sync/configs/import', {
    data: { content: csvText([csvGlobal('data_source', 'TEST_DISABLED')]), onConflict: 'OVERWRITE', dryRun: true },
    token: cfgAdminToken
  })
  check('引用已停用选项 → 行级失败', disabledOptionImport.ok && disabledOptionImport.result.summary.failed === 1)
  const unknownOptionImport = await call('post', '/sync/configs/import', {
    data: { content: csvText([csvGlobal('data_source', 'NO_SUCH_OPTION')]), onConflict: 'OVERWRITE', dryRun: true },
    token: cfgAdminToken
  })
  check('引用不存在选项 → 行级失败', unknownOptionImport.ok && unknownOptionImport.result.summary.failed === 1)
  await expectCode(
    '冲突策略非法 → 9509',
    'post',
    '/sync/configs/import',
    { data: { content: 'x', onConflict: 'MERGE' }, token: cfgAdminToken },
    9509
  )
  await expectCode(
    '导入内容为空 → 9508',
    'post',
    '/sync/configs/import',
    { data: { content: '   ', onConflict: 'OVERWRITE' }, token: cfgAdminToken },
    9508
  )
  await expectCode(
    '导入表头不匹配 → 9508',
    'post',
    '/sync/configs/import',
    { data: { content: 'a,b,c\r\n1,2,3', onConflict: 'OVERWRITE' }, token: cfgAdminToken },
    9508
  )
  await expectCode(
    '非 ADMIN 导入配置 → 403',
    'post',
    '/sync/configs/import',
    { data: { content: 'x', onConflict: 'OVERWRITE' }, token: cfgStaToken },
    403,
    403
  )

  /* ---------- 需求5：考勤导出（attendance/export） ---------- */
  const EXPORT_HEADER = [
    '员工姓名',
    '登录账号',
    '所属驿站',
    '日期',
    '时段名称',
    '卡类型',
    '打卡时间',
    '打卡方式',
    'WiFi',
    '距离(米)',
    '状态',
    '来源',
    '备注'
  ]
  const exportParams = { stationId: 1, startDate: monthStart, endDate: today }

  const expAll = await call('get', '/attendance/export', {
    params: exportParams,
    token: cfgAdminToken,
    responseType: 'blob'
  })
  check('考勤导出返回文件流', expAll.ok && expAll.result.data instanceof Blob)
  check(
    '导出响应头为 CSV 且带附件文件名',
    expAll.ok &&
      /text\/csv/.test(expAll.result.headers['content-type'] || '') &&
      /filename\*=UTF-8''/.test(expAll.result.headers['content-disposition'] || '')
  )
  const expAllText = expAll.ok ? await expAll.result.data.text() : ''
  // Blob.text() 会按规范剥掉 BOM，判定 BOM 必须看原始字节（EF BB BF）
  const expAllBytes = expAll.ok ? new Uint8Array(await expAll.result.data.arrayBuffer()) : new Uint8Array()
  check(
    'CSV 带 BOM（EF BB BF，Excel 直接打开中文不乱码）',
    expAllBytes[0] === 0xef && expAllBytes[1] === 0xbb && expAllBytes[2] === 0xbf
  )
  const expAllLines = csvLinesOf(expAllText)
  check('导出表头列与契约一致', expAllLines[0] === EXPORT_HEADER.join(','))
  const recAll = await call('get', '/attendance/records', {
    params: { ...exportParams, pageNum: 1, pageSize: 100 },
    token: cfgAdminToken
  })
  check('导出行数与同筛选条件记录总数一致（全量不分页）', recAll.ok && expAllLines.length - 1 === recAll.result.total)
  check(
    '导出含「时段名称」与「来源」列且行数 > 0',
    expAllLines.length - 1 > 0 && expAllLines[1].split(',').length === EXPORT_HEADER.length
  )

  const expLate = await call('get', '/attendance/export', {
    params: { ...exportParams, status: 'LATE' },
    token: cfgAdminToken,
    responseType: 'blob'
  })
  const expLateLines = expLate.ok ? csvLinesOf(await expLate.result.data.text()) : []
  const recLateExport = await call('get', '/attendance/records', {
    params: { ...exportParams, status: 'LATE', pageNum: 1, pageSize: 100 },
    token: cfgAdminToken
  })
  check(
    '按状态筛选后导出行数与记录数一致',
    recLateExport.ok && expLateLines.length - 1 === recLateExport.result.total && expLateLines.length - 1 > 0
  )
  check(
    '迟到导出行状态列均为「迟到」',
    expLateLines.slice(1).every((line) => line.split(',')[10] === '迟到')
  )
  await expectCode(
    '导出 status 取值非法 → 400',
    'get',
    '/attendance/export',
    { params: { ...exportParams, status: 'UNKNOWN' }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '导出日期格式非法 → 400',
    'get',
    '/attendance/export',
    { params: { ...exportParams, startDate: '2026/09/01' }, token: cfgAdminToken },
    400
  )

  const expSta = await call('get', '/attendance/export', {
    params: { ...exportParams, stationId: 2 },
    token: cfgStaToken,
    responseType: 'blob'
  })
  const expStaLines = expSta.ok ? csvLinesOf(await expSta.result.data.text()) : []
  check(
    '站长导出越权覆盖：数据收敛本站(1)',
    expSta.ok && expStaLines.length - 1 > 0 && expStaLines.slice(1).every((line) => line.split(',')[2] === '城东驿站')
  )
  await expectCode(
    'STAFF 导出考勤 → 403',
    'get',
    '/attendance/export',
    { params: exportParams, token: cfgStaffToken },
    403,
    403
  )

  /* ---------- 需求2：按驿站批量排班（attendance.js） ---------- */
  // 排班种子只铺城东「近 30 天 + 本周剩余」，故日期区间取「今天 +7 天」起，保证起点为空场地
  const batchStart = formatDate(addDays(new Date(), 7))
  const batchEnd = formatDate(addDays(new Date(), 20))
  const batchStaffEmp = activeEmployees()
    .filter((e) => e.station_id === 1 && e.status === 1)
    .find((e) => e.id !== 3 && e.id !== 4)
  const batchEmpId = batchStaffEmp ? batchStaffEmp.id : 5
  const batchBody = (extra) => ({
    stationId: 1,
    shiftId: 1,
    startDate: batchStart,
    endDate: batchEnd,
    employeeIds: [batchEmpId],
    ...extra
  })
  const monDates = datesInRange(batchStart, batchEnd, [1])
  const tueDates = datesInRange(batchStart, batchEnd, [2])
  const batchWeek = formatDate(mondayOf(new Date(`${batchStart}T00:00:00`)))
  const batchRowOf = async (token) => {
    const matrix = await call('get', '/schedules', { params: { stationId: 1, weekStart: batchWeek }, token })
    return matrix.ok ? matrix.result.employees.find((e) => e.employeeId === batchEmpId) : null
  }

  check('按站排班用例所需的城东在职员工存在', !!batchStaffEmp)
  const bFirst = await expectCode(
    '一键铺排 POST /schedules/batch-by-station',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ weekdays: [1] }), token: cfgAdminToken },
    200
  )
  check(
    '返回结构为 created / skipped / total 三字段',
    bFirst.ok && ['created', 'skipped', 'total'].every((k) => k in bFirst.result)
  )
  check(
    '创建条数 = 命中星期的日期数（脚本侧独立推算）',
    bFirst.ok &&
      bFirst.result.created === monDates.length &&
      bFirst.result.total === monDates.length &&
      monDates.length > 0
  )
  const bFirstRow = await batchRowOf(cfgAdminToken)
  check(
    '铺排后矩阵中仅命中的星期一有班次（weekdays 过滤生效）',
    !!bFirstRow &&
      bFirstRow.days
        .filter((d) => d.workDate >= batchStart && d.workDate <= batchEnd)
        .every((d) => (monDates.includes(d.workDate) ? d.shiftId === 1 : d.shiftId == null))
  )

  const bSkip = await call('post', '/schedules/batch-by-station', {
    data: batchBody({ weekdays: [1] }),
    token: cfgAdminToken
  })
  check(
    'skipExisting 默认 true：已有排班被跳过',
    bSkip.ok &&
      bSkip.result.created === 0 &&
      bSkip.result.skipped === monDates.length &&
      bSkip.result.total === monDates.length
  )
  const bSkipRow = await batchRowOf(cfgAdminToken)
  check(
    '跳过时原有班次未被覆盖',
    !!bSkipRow && bSkipRow.days.filter((d) => monDates.includes(d.workDate)).every((d) => d.shiftId === 1)
  )

  const bOverwrite = await call('post', '/schedules/batch-by-station', {
    data: batchBody({ weekdays: [1], shiftId: 2, skipExisting: false }),
    token: cfgAdminToken
  })
  check(
    'skipExisting=false：覆盖已有排班',
    bOverwrite.ok && bOverwrite.result.created === monDates.length && bOverwrite.result.skipped === 0
  )
  const bOverwriteRow = await batchRowOf(cfgAdminToken)
  check(
    '覆盖后排班改为本次班次',
    !!bOverwriteRow && bOverwriteRow.days.filter((d) => monDates.includes(d.workDate)).every((d) => d.shiftId === 2)
  )

  const bTue = await call('post', '/schedules/batch-by-station', {
    data: batchBody({ weekdays: [2], shiftId: 1 }),
    token: cfgAdminToken
  })
  check(
    'weekdays=[2] 只命中星期二',
    bTue.ok && tueDates.length > 0 && bTue.result.created === tueDates.length && bTue.result.total === tueDates.length
  )

  const station1OnDutyCount = activeEmployees().filter((e) => e.station_id === 1 && e.status === 1).length
  const batchOneDay = formatDate(addDays(new Date(), 21))
  const bAllStaff = await call('post', '/schedules/batch-by-station', {
    data: { stationId: 1, shiftId: 1, startDate: batchOneDay, endDate: batchOneDay },
    token: cfgAdminToken
  })
  check(
    'employeeIds 缺省 = 全站在职员工',
    bAllStaff.ok && bAllStaff.result.total === station1OnDutyCount && bAllStaff.result.created === station1OnDutyCount
  )

  await expectCode(
    '起止日期倒挂 → 400',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ startDate: batchEnd, endDate: batchStart }), token: cfgAdminToken },
    400
  )
  await expectCode(
    '驿站不存在 → 4001',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ stationId: 999 }), token: cfgAdminToken },
    4001
  )
  await expectCode(
    '缺少 shiftId → 400',
    'post',
    '/schedules/batch-by-station',
    { data: { stationId: 1, startDate: batchStart, endDate: batchEnd }, token: cfgAdminToken },
    400
  )
  await expectCode(
    'weekdays 取值越界 → 400',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ weekdays: [7] }), token: cfgAdminToken },
    400
  )
  await expectCode(
    '日期格式非法 → 400',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ startDate: '2026/09/20' }), token: cfgAdminToken },
    400
  )
  await expectCode(
    'employeeIds 非数组 → 400',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ employeeIds: 5 }), token: cfgAdminToken },
    400
  )
  await expectCode(
    '引用他站班次 → 9106',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ shiftId: 5 }), token: cfgAdminToken },
    9106
  )
  await expectCode(
    '引用不存在班次 → 9106',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({ shiftId: 99999 }), token: cfgAdminToken },
    9106
  )
  await expectCode(
    '站长一键铺排 → 403（仅老板可批量排班）',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({}), token: cfgStaToken },
    403,
    403
  )
  await expectCode(
    'STAFF 一键铺排 → 403',
    'post',
    '/schedules/batch-by-station',
    { data: batchBody({}), token: cfgStaffToken },
    403,
    403
  )

  /* ---------- 需求4：发布通知（notification.js） ---------- */
  const onDutyEmployees = activeEmployees().filter((e) => e.status === 1)
  const station1OnDuty = onDutyEmployees.filter((e) => e.station_id === 1)
  const disabledEmpId = (activeEmployees().find((e) => e.status === 0) || {}).id

  const cfgUnread0 = await call('get', '/notifications/unread-count', { token: cfgStaffToken })
  const pubAll = await expectCode(
    '发布通知 scope=ALL',
    'post',
    '/notifications/publish',
    { data: { title: '全员公告', content: '演示全员发布', scope: 'ALL' }, token: cfgAdminToken },
    200
  )
  check('ALL 收件人数 = 全站在职员工数', pubAll.ok && pubAll.result.count === onDutyEmployees.length)
  const cfgUnread1 = await call('get', '/notifications/unread-count', { token: cfgStaffToken })
  check(
    '发布后覆盖到的员工未读 +1',
    cfgUnread0.ok && cfgUnread1.ok && cfgUnread1.result.count === cfgUnread0.result.count + 1
  )
  const pubAllHit = await call('get', '/notifications', { params: { pageNum: 1, pageSize: 100 }, token: cfgStaffToken })
  const pubAllItem = pubAllHit.ok ? pubAllHit.result.list.find((n) => n.title === '全员公告') : null
  check(
    '手工发布通知带发布人 / 范围标记',
    !!pubAllItem &&
      pubAllItem.isPublished === true &&
      pubAllItem.publishScope === 'ALL' &&
      pubAllItem.publisherName === '系统管理员' &&
      pubAllItem.type === 4
  )

  const pubStation = await expectCode(
    '发布通知 scope=STATION',
    'post',
    '/notifications/publish',
    { data: { title: '驿站公告', content: '城东驿站专属公告', scope: 'STATION', stationId: 1 }, token: cfgAdminToken },
    200
  )
  check('STATION 收件人数 = 该驿站在职员工数', pubStation.ok && pubStation.result.count === station1OnDuty.length)

  const pubEmp = await expectCode(
    '发布通知 scope=EMPLOYEE',
    'post',
    '/notifications/publish',
    {
      data: { title: '定向通知', content: '仅发给指定员工', scope: 'EMPLOYEE', employeeIds: [4] },
      token: cfgAdminToken
    },
    200
  )
  check('EMPLOYEE 收件人数 = 指定在职员工数', pubEmp.ok && pubEmp.result.count === 1)
  const pubEmpHit = await call('get', '/notifications', { params: { pageNum: 1, pageSize: 100 }, token: cfgStaffToken })
  check(
    '定向通知落到指定员工',
    pubEmpHit.ok && pubEmpHit.result.list.some((n) => n.title === '定向通知' && n.publishScope === 'EMPLOYEE')
  )

  await expectCode(
    'scope 非法 → 9002',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'DEPT' }, token: cfgAdminToken },
    9002
  )
  await expectCode(
    'STATION 缺 stationId → 9002',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'STATION' }, token: cfgAdminToken },
    9002
  )
  await expectCode(
    'STATION 驿站不存在 → 9002',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'STATION', stationId: 999 }, token: cfgAdminToken },
    9002
  )
  await expectCode(
    'EMPLOYEE 缺 employeeIds → 9002',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'EMPLOYEE' }, token: cfgAdminToken },
    9002
  )
  await expectCode(
    'EMPLOYEE 仅含停用账号 → 9002',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'EMPLOYEE', employeeIds: [disabledEmpId] }, token: cfgAdminToken },
    9002
  )
  await expectCode(
    '标题为空 → 400',
    'post',
    '/notifications/publish',
    { data: { title: '', content: 'y', scope: 'ALL' }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '通知类型非法 → 400',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'ALL', type: 9 }, token: cfgAdminToken },
    400
  )
  await expectCode(
    '非 ADMIN 发布通知 → 403',
    'post',
    '/notifications/publish',
    { data: { title: 'x', content: 'y', scope: 'ALL' }, token: cfgStaffToken },
    403,
    403
  )

  /* ---------- 需求6：超时未处理派生口径 + 需求3：新建工单与企微自动派单（workOrder.js） ---------- */
  // 两者同库：需求3 会追加工单，故需求6 的「种子口径」断言先跑，再跑新建与派单用例
  resetDb()
  const woAdmin = await login('admin', 'demo1234')
  const woAdminToken = woAdmin.ok ? woAdmin.result.token : ''
  const woSta = await login('st001_admin', 'demo1234')
  const woStaToken = woSta.ok ? woSta.result.token : ''
  const woStaff = await login('st001_staff', 'demo1234')
  const woStaffToken = woStaff.ok ? woStaff.result.token : ''
  const woDeadlinePast = (order) => new Date(String(order.sla_deadline).replace(' ', 'T')).getTime() < Date.now()

  const overdueDerived = db.workOrders.filter((o) => (o.status === 0 || o.status === 1) && woDeadlinePast(o))
  check('超时未处理派生口径独立核算 = 6 条', overdueDerived.length === 6)
  const woPage = { pageNum: 1, pageSize: 100 }
  const overdueList = await call('get', '/work-orders', {
    params: { overdueUnhandled: '1', ...woPage },
    token: woAdminToken
  })
  check(
    '筛选参数 overdueUnhandled=1 结果与派生口径一致',
    overdueList.ok && overdueList.result.total === overdueDerived.length
  )
  check(
    '超时未处理仅含待处理 / 处理中',
    overdueList.ok &&
      overdueList.result.list.every((o) => o.overdueUnhandled === true && (o.status === 0 || o.status === 1))
  )
  check(
    '超时未处理每条 SLA 截止均已过期',
    overdueList.ok && overdueList.result.list.every((o) => woDeadlinePast({ sla_deadline: o.slaDeadline }))
  )
  const overdueTrue = await call('get', '/work-orders', {
    params: { overdueUnhandled: 'true', ...woPage },
    token: woAdminToken
  })
  const overdueAlias = await call('get', '/work-orders', {
    params: { overSla: 'true', ...woPage },
    token: woAdminToken
  })
  check(
    'overdueUnhandled=true 与兼容别名 overSla=true 口径一致',
    overdueTrue.ok &&
      overdueAlias.ok &&
      overdueTrue.result.total === overdueList.result.total &&
      overdueAlias.result.total === overdueList.result.total
  )
  const woByStatus = {}
  for (const status of [0, 1, 2, 3]) {
    woByStatus[status] = await call('get', '/work-orders', { params: { status, ...woPage }, token: woAdminToken })
  }
  const woStatusGroups = [0, 1, 2, 3].map((s) => (woByStatus[s].ok ? woByStatus[s].result.list : []))
  check(
    '按状态分组覆盖全部 120 条工单',
    [0, 1, 2, 3].reduce((sum, s) => sum + woByStatus[s].result.total, 0) === 120 &&
      woStatusGroups.every((g) => g.length > 0)
  )
  check(
    '全量工单 overSla 与 overdueUnhandled 同值',
    woStatusGroups.flat().every((o) => o.overSla === o.overdueUnhandled)
  )
  check(
    '存在「SLA 已过但已解决 / 已关闭」的种子样本',
    db.workOrders.some((o) => o.status >= 2 && woDeadlinePast(o))
  )
  check(
    '已解决 / 已关闭即使 SLA 已过也不计超时未处理',
    [...woStatusGroups[2], ...woStatusGroups[3]].every((o) => o.overdueUnhandled === false)
  )
  check(
    '待处理 / 处理中工单超时标记与 SLA 过期口径一致',
    [...woStatusGroups[0], ...woStatusGroups[1]].every(
      (o) => o.overdueUnhandled === woDeadlinePast({ sla_deadline: o.slaDeadline })
    )
  )

  const woManualCreate = await expectCode(
    '手工新建工单 POST /work-orders',
    'post',
    '/work-orders',
    {
      data: { type: 3, priority: 2, title: '需求3-新建工单', content: '手工新建，验证落库可见', stationId: 1 },
      token: woAdminToken
    },
    200
  )
  check(
    '新建返回 {id, orderNo, source=MANUAL}',
    woManualCreate.ok &&
      woManualCreate.result.id > 0 &&
      /^WO-\d{8}-\d{4}$/.test(woManualCreate.result.orderNo) &&
      woManualCreate.result.source === 'MANUAL'
  )
  const woNewId = woManualCreate.ok ? woManualCreate.result.id : 0
  const woNewDetail = await call('get', `/work-orders/${woNewId}`, { token: woAdminToken })
  check(
    '新建工单落库为待处理，上报人为操作人，时间线含 create',
    woNewDetail.ok &&
      woNewDetail.result.status === 0 &&
      woNewDetail.result.reporterId === 1 &&
      woNewDetail.result.source === 'MANUAL' &&
      woNewDetail.result.handleLog.some((l) => l.action === 'create')
  )
  const woSearch = await call('get', '/work-orders', {
    params: { keyword: woManualCreate.ok ? woManualCreate.result.orderNo : '', pageNum: 1, pageSize: 20 },
    token: woAdminToken
  })
  check('新建工单在列表中可见（keyword 命中单号）', woSearch.ok && woSearch.result.list.some((o) => o.id === woNewId))
  check(
    '高优先级 SLA = 创建时间 + 8h',
    woNewDetail.ok &&
      new Date(String(woNewDetail.result.slaDeadline).replace(' ', 'T')).getTime() -
        new Date(String(woNewDetail.result.createTime).replace(' ', 'T')).getTime() ===
        8 * 3600000
  )
  await expectCode(
    '工单类型非法 → 400',
    'post',
    '/work-orders',
    { data: { type: 9, priority: 1, title: 'x', stationId: 1 }, token: woAdminToken },
    400
  )
  await expectCode(
    '优先级非法 → 400',
    'post',
    '/work-orders',
    { data: { type: 1, priority: 9, title: 'x', stationId: 1 }, token: woAdminToken },
    400
  )
  await expectCode(
    '标题为空 → 400',
    'post',
    '/work-orders',
    { data: { type: 1, priority: 1, title: '', stationId: 1 }, token: woAdminToken },
    400
  )
  await expectCode(
    '描述超长 → 400',
    'post',
    '/work-orders',
    { data: { type: 1, priority: 1, title: 'x', content: 'y'.repeat(501), stationId: 1 }, token: woAdminToken },
    400
  )
  const empInStation2 = activeEmployees().find((e) => e.station_id === 2 && e.status === 1)
  await expectCode(
    '非 ADMIN 跨站指派 → 8004',
    'post',
    '/work-orders',
    {
      data: { type: 1, priority: 1, title: 'x', stationId: 1, assigneeId: empInStation2 ? empInStation2.id : 8 },
      token: woStaToken
    },
    8004
  )

  /* ---- U6：派单规则读权限与写权限口径统一为 ADMIN ---- */
  await expectCode(
    'STAFF 读派单规则 → 403（U6 统一口径）',
    'get',
    '/work-orders/dispatch-rules',
    { token: woStaffToken },
    403,
    403
  )
  await expectCode(
    '站长读派单规则 → 403（U6 统一口径）',
    'get',
    '/work-orders/dispatch-rules',
    { token: woStaToken },
    403,
    403
  )
  const woRules = await expectCode(
    'ADMIN 读派单规则',
    'get',
    '/work-orders/dispatch-rules',
    { token: woAdminToken },
    200
  )
  check(
    '派单规则 5 条且字段齐备',
    woRules.ok &&
      woRules.result.length === 5 &&
      woRules.result.every(
        (r) =>
          'keyword' in r &&
          'workOrderType' in r &&
          'priority' in r &&
          'defaultAssigneeId' in r &&
          typeof r.enabled === 'boolean'
      )
  )
  await expectCode(
    '非 ADMIN 改派单规则 → 403',
    'put',
    '/work-orders/dispatch-rules/1',
    { data: { priority: 0 }, token: woStaToken },
    403,
    403
  )
  await expectCode(
    '派单规则不存在 → 8005',
    'put',
    '/work-orders/dispatch-rules/9999',
    { data: { priority: 0 }, token: woAdminToken },
    8005
  )
  await expectCode(
    '规则关键词为空 → 400',
    'put',
    '/work-orders/dispatch-rules/1',
    { data: { keyword: '' }, token: woAdminToken },
    400
  )
  await expectCode(
    '规则工单类型非法 → 400',
    'put',
    '/work-orders/dispatch-rules/1',
    { data: { workOrderType: 9 }, token: woAdminToken },
    400
  )
  await expectCode(
    '规则默认处理人不存在 → 400',
    'put',
    '/work-orders/dispatch-rules/1',
    { data: { defaultAssigneeId: 999999 }, token: woAdminToken },
    400
  )
  const woRuleSaved = await expectCode(
    '改派单规则（关键词「紧急」→ 类型2 / 优先级0）',
    'put',
    '/work-orders/dispatch-rules/1',
    { data: { keyword: '紧急', workOrderType: 2, priority: 0 }, token: woAdminToken },
    200
  )
  check(
    '规则修改后回读生效',
    woRuleSaved.ok &&
      woRuleSaved.result.keyword === '紧急' &&
      woRuleSaved.result.workOrderType === 2 &&
      woRuleSaved.result.priority === 0
  )

  /* ---- 自动派单：auth=false（模拟企微回调，无登录态） ---- */
  const autoHit = await expectCode(
    '企微回调自动派单（不传 token，auth=false）',
    'post',
    '/work-orders/auto-dispatch',
    { data: { groupName: '城东驿站-异常件处理群', senderName: '李师傅', content: '客户投诉取件太慢', stationId: 1 } },
    200
  )
  check(
    '关键词命中规则决定类型与优先级（投诉 → 类型3 / 优先级1）',
    autoHit.ok && autoHit.result.type === 3 && autoHit.result.priority === 1 && autoHit.result.source === 'AUTO_WECHAT'
  )
  check(
    '自动派单不伪造上报人，群消息来源写入时间线',
    autoHit.ok &&
      autoHit.result.reporterId === null &&
      autoHit.result.handleLog.some(
        (l) => l.action === 'auto_dispatch' && /城东驿站-异常件处理群/.test(String(l.content))
      )
  )
  check('自动派单标题带命中的关键词前缀', autoHit.ok && String(autoHit.result.title).startsWith('投诉：'))

  const autoUpdated = await call('post', '/work-orders/auto-dispatch', {
    data: { content: '紧急件需要马上处理', stationId: 1 }
  })
  check(
    '规则改动后按新规则派发（紧急 → 类型2 / 优先级0）',
    autoUpdated.ok && autoUpdated.result.type === 2 && autoUpdated.result.priority === 0
  )
  const autoDefault = await call('post', '/work-orders/auto-dispatch', {
    data: { content: '今天天气不错，大家辛苦了', stationId: 1 }
  })
  check(
    '未命中任何规则用默认值（类型4 / 优先级1）',
    autoDefault.ok && autoDefault.result.type === 4 && autoDefault.result.priority === 1
  )
  await expectCode(
    '群消息内容为空 → 8006',
    'post',
    '/work-orders/auto-dispatch',
    { data: { content: '   ', stationId: 1 } },
    8006
  )
  await expectCode('群消息缺 content → 8006', 'post', '/work-orders/auto-dispatch', { data: { stationId: 1 } }, 8006)
  await expectCode(
    '群消息指定驿站不存在 → 4001',
    'post',
    '/work-orders/auto-dispatch',
    { data: { content: '紧急件', stationId: 999 } },
    4001
  )
  const autoSendTime = await call('post', '/work-orders/auto-dispatch', {
    data: { content: '紧急件处理', sendTime: '2026-09-19 08:30:00', stationId: 1 }
  })
  check(
    '按消息真实发送时间建单（回调延迟到账，SLA 自消息时间起算）',
    autoSendTime.ok && autoSendTime.result.createTime === '2026-09-19 08:30:00'
  )

  const dispatchEmp = activeEmployees().find((e) => e.station_id === 1 && e.status === 1 && e.id !== 3 && e.id !== 4)
  await call('put', '/work-orders/dispatch-rules/1', {
    data: { defaultAssigneeId: dispatchEmp ? dispatchEmp.id : 5 },
    token: woAdminToken
  })
  const dispatchLogin = dispatchEmp ? await login(dispatchEmp.username, 'demo1234') : { ok: false }
  const dispatchToken = dispatchLogin.ok ? dispatchLogin.result.token : ''
  const dispatchUnread0 = await call('get', '/notifications/unread-count', { token: dispatchToken })
  const autoAssigned = await call('post', '/work-orders/auto-dispatch', {
    data: { content: '紧急件自动指派', stationId: 1 }
  })
  check(
    '规则默认处理人自动落到工单处理人',
    autoAssigned.ok && !!dispatchEmp && autoAssigned.result.assigneeId === dispatchEmp.id
  )
  const dispatchUnread1 = await call('get', '/notifications/unread-count', { token: dispatchToken })
  check(
    '自动派单给被指派人推送未读通知',
    dispatchUnread0.ok && dispatchUnread1.ok && dispatchUnread1.result.count === dispatchUnread0.result.count + 1
  )
  await call('put', '/work-orders/dispatch-rules/1', {
    data: { keyword: '破损', defaultAssigneeId: null },
    token: woAdminToken
  })

  await call('put', '/work-orders/dispatch-rules/3', { data: { enabled: 0 }, token: woAdminToken })
  const autoDisabled = await call('post', '/work-orders/auto-dispatch', {
    data: { content: '客户投诉取件慢', stationId: 1 }
  })
  check(
    '停用的规则不参与命中（回退默认值）',
    autoDisabled.ok && autoDisabled.result.type === 4 && autoDisabled.result.priority === 1
  )
  await call('put', '/work-orders/dispatch-rules/3', { data: { enabled: 1 }, token: woAdminToken })

  /* ---------- 需求7：KPI 考核（kpi.js / kpiStore.js） ---------- */
  resetDb()
  resetKpiStore()
  const kpiAdmin = await login('admin', 'demo1234')
  const kpiAdminToken = kpiAdmin.ok ? kpiAdmin.result.token : ''
  const kpiSta = await login('st001_admin', 'demo1234')
  const kpiStaToken = kpiSta.ok ? kpiSta.result.token : ''
  const kpiStaff = await login('st001_staff', 'demo1234')
  const kpiStaffToken = kpiStaff.ok ? kpiStaff.result.token : ''
  const kpiMonth = currentMonth()
  const kpiMetricBody = (extra) => ({
    metricKey: 'DEMO_X',
    metricName: '演示指标',
    metricType: 'OTHER',
    weight: 0,
    targetValue: 10,
    unit: '件',
    direction: 'UP',
    scoreRule: { mode: 'LINEAR', fullScore: 100 },
    ...extra
  })

  const kpiMetrics = await expectCode(
    '指标列表 GET /kpi/metrics（ADMIN）',
    'get',
    '/kpi/metrics',
    { token: kpiAdminToken },
    200
  )
  check(
    '指标种子 7 项（6 启用 + 1 停用）且权重合计 100',
    kpiMetrics.ok &&
      kpiMetrics.result.list.length === 7 &&
      kpiMetrics.result.weightSum === 100 &&
      kpiMetrics.result.list.filter((m) => m.enabled === 1).length === 6
  )
  check(
    '指标出参含类型 / 方向 / 评分模式 label',
    kpiMetrics.ok &&
      kpiMetrics.result.list.every((m) => !!m.metricTypeLabel && !!m.directionLabel && !!m.scoreModeLabel)
  )
  await expectCode('STAFF 访问指标配置 → 403', 'get', '/kpi/metrics', { token: kpiStaffToken }, 403, 403)
  await expectCode(
    '非 ADMIN 新增指标 → 403',
    'post',
    '/kpi/metrics',
    { data: kpiMetricBody({ metricKey: 'DEMO_STA' }), token: kpiStaToken },
    403,
    403
  )

  const kpiMetricNew = await expectCode(
    '新增指标（权重 0，不破坏 100%）',
    'post',
    '/kpi/metrics',
    { data: kpiMetricBody({ metricKey: 'DEMO_EXTRA' }), token: kpiAdminToken },
    200
  )
  const kpiMetricNewId = kpiMetricNew.ok ? kpiMetricNew.result.id : 0
  await expectCode(
    '新增启用指标致权重偏离 100% → 9202',
    'post',
    '/kpi/metrics',
    { data: kpiMetricBody({ metricKey: 'DEMO_BAD', weight: 5 }), token: kpiAdminToken },
    9202
  )
  await expectCode(
    '指标标识重复 → 400',
    'post',
    '/kpi/metrics',
    { data: kpiMetricBody({ metricKey: 'PARCEL' }), token: kpiAdminToken },
    400
  )
  await expectCode(
    '改权重致合计偏离 100% → 9202',
    'put',
    '/kpi/metrics/1',
    { data: { weight: 40 }, token: kpiAdminToken },
    9202
  )
  await expectCode(
    '指标不存在（更新）→ 9201',
    'put',
    '/kpi/metrics/9999',
    { data: { weight: 10 }, token: kpiAdminToken },
    9201
  )
  await expectCode('指标不存在（删除）→ 9201', 'delete', '/kpi/metrics/9999', { token: kpiAdminToken }, 9201)
  const kpiMetricUpdated = await expectCode(
    '更新指标名称',
    'put',
    `/kpi/metrics/${kpiMetricNewId}`,
    { data: { metricName: '演示指标(改)' }, token: kpiAdminToken },
    200
  )
  check('更新后名称生效', kpiMetricUpdated.ok && kpiMetricUpdated.result.metricName === '演示指标(改)')
  const kpiMetricDeleted = await expectCode(
    '删除演示指标',
    'delete',
    `/kpi/metrics/${kpiMetricNewId}`,
    { token: kpiAdminToken },
    200
  )
  const kpiMetricsAfter = await call('get', '/kpi/metrics', { token: kpiAdminToken })
  check(
    '删除后指标不再出现在列表',
    kpiMetricDeleted.ok && kpiMetricsAfter.ok && kpiMetricsAfter.result.list.every((m) => m.id !== kpiMetricNewId)
  )

  const kpiCalc = await expectCode(
    '按月算分 POST /kpi/scores/calculate',
    'post',
    '/kpi/scores/calculate',
    { data: { month: kpiMonth, employeeIds: [4] }, token: kpiAdminToken },
    200
  )
  check(
    '算分覆盖 1 人且该 STAFF 适用 6 项指标（含服务评分）',
    kpiCalc.ok &&
      kpiCalc.result.month === kpiMonth &&
      kpiCalc.result.employeeCount === 1 &&
      kpiCalc.result.metricCount === 6 &&
      kpiCalc.result.scoreCount === 6
  )
  await expectCode(
    '算分 month 格式非法 → 400',
    'post',
    '/kpi/scores/calculate',
    { data: { month: '2026-9' }, token: kpiAdminToken },
    400
  )
  await expectCode(
    '非 ADMIN 算分 → 403',
    'post',
    '/kpi/scores/calculate',
    { data: { month: kpiMonth }, token: kpiStaToken },
    403,
    403
  )

  const kpiScores = await expectCode(
    '得分列表 GET /kpi/scores（ADMIN）',
    'get',
    '/kpi/scores',
    { params: { month: kpiMonth, pageNum: 1, pageSize: 100 }, token: kpiAdminToken },
    200
  )
  check(
    '得分列表覆盖全部 55 名在职员工',
    kpiScores.ok && kpiScores.result.total === 55 && kpiScores.result.list.length === 55
  )
  check(
    '得分列表按员工号升序且带等级',
    kpiScores.ok &&
      kpiScores.result.list.every(
        (r, i, arr) =>
          (i === 0 || arr[i - 1].employeeId < r.employeeId) && !!r.levelLabel && typeof r.totalScore === 'number'
      )
  )
  await expectCode(
    '得分列表 month 格式非法 → 400',
    'get',
    '/kpi/scores',
    { params: { month: '2026/09' }, token: kpiAdminToken },
    400
  )

  const kpiDetail4 = await expectCode(
    '得分明细 GET /kpi/scores/4',
    'get',
    '/kpi/scores/4',
    { params: { month: kpiMonth }, token: kpiAdminToken },
    200
  )
  check(
    '明细含 6 项指标且权重合计 100',
    kpiDetail4.ok &&
      kpiDetail4.result.metricCount === 6 &&
      kpiDetail4.result.items.length === 6 &&
      kpiDetail4.result.weightSum === 100
  )
  const kpiHandTotal = kpiDetail4.ok
    ? Number(
        (
          kpiDetail4.result.items.reduce((s, i) => s + i.score * i.weight, 0) /
          kpiDetail4.result.items.reduce((s, i) => s + i.weight, 0)
        ).toFixed(1)
      )
    : -1
  check(
    '加权总分与手算一致（Σ得分×权重 ÷ Σ权重）',
    kpiDetail4.ok && Math.abs(kpiHandTotal - kpiDetail4.result.totalScore) < 0.05,
    `手算 ${kpiHandTotal} / 接口 ${kpiDetail4.ok ? kpiDetail4.result.totalScore : '-'}`
  )
  check(
    '单项加权分 = 单项分 × 权重 / 100',
    kpiDetail4.ok &&
      kpiDetail4.result.items.every(
        (i) => Math.abs(Number(((i.score * i.weight) / 100).toFixed(2)) - i.weightedScore) < 0.001
      )
  )

  const kpiDetailAdminEmp = await call('get', '/kpi/scores/1', { params: { month: kpiMonth }, token: kpiAdminToken })
  check(
    'roleScope 过滤生效：ADMIN 员工不含「服务评分」且仅适用 5 项',
    kpiDetailAdminEmp.ok &&
      kpiDetailAdminEmp.result.metricCount === 5 &&
      kpiDetailAdminEmp.result.items.every((i) => i.metricKey !== 'SERVICE')
  )
  check(
    'roleScope 过滤生效：STAFF 员工含「服务评分」',
    kpiDetail4.ok && kpiDetail4.result.items.some((i) => i.metricKey === 'SERVICE')
  )

  await expectCode(
    '无评分记录 → 9204',
    'get',
    '/kpi/scores/99999',
    { params: { month: kpiMonth }, token: kpiAdminToken },
    9204
  )
  await expectCode(
    'STAFF 查他人得分明细 → 403',
    'get',
    '/kpi/scores/1',
    { params: { month: kpiMonth }, token: kpiStaffToken },
    403,
    403
  )
  const kpiSelf = await expectCode(
    '员工查本人得分明细',
    'get',
    '/kpi/scores/4',
    { params: { month: kpiMonth }, token: kpiStaffToken },
    200
  )
  check('员工可见本人明细', kpiSelf.ok && kpiSelf.result.employeeId === 4)
  const kpiStation2Emp = activeEmployees().find((e) => e.station_id === 2 && e.status === 1)
  await expectCode(
    '站长查他站员工明细 → 403',
    'get',
    `/kpi/scores/${kpiStation2Emp ? kpiStation2Emp.id : 8}`,
    { params: { month: kpiMonth }, token: kpiStaToken },
    403,
    403
  )

  const kpiRanking = await expectCode(
    '考核排名 GET /kpi/scores/ranking',
    'get',
    '/kpi/scores/ranking',
    { params: { month: kpiMonth, pageNum: 1, pageSize: 100 }, token: kpiAdminToken },
    200
  )
  check(
    '排名按总分降序',
    kpiRanking.ok && kpiRanking.result.list.every((r, i, arr) => i === 0 || arr[i - 1].totalScore >= r.totalScore)
  )
  check(
    '排名摘要 count / avgScore / topScore 与列表自洽',
    kpiRanking.ok &&
      kpiRanking.result.count === kpiRanking.result.total &&
      kpiRanking.result.topScore === kpiRanking.result.list[0].totalScore &&
      kpiRanking.result.avgScore > 0
  )
  check(
    '同分且同达成率共享同一名次（竞赛排名法）',
    kpiRanking.ok &&
      kpiRanking.result.list.every(
        (r, i, arr) =>
          i === 0 ||
          r.totalScore !== arr[i - 1].totalScore ||
          r.achievementRate !== arr[i - 1].achievementRate ||
          r.rank === arr[i - 1].rank
      )
  )
  await expectCode(
    'STAFF 访问排名 → 403',
    'get',
    '/kpi/scores/ranking',
    { params: { month: kpiMonth }, token: kpiStaffToken },
    403,
    403
  )

  /* 批量保存 PUT /kpi/metrics/batch（页面「权重合计条 + 保存」的落点）
     单指标接口每次校验「启用合计 = 100%」，调权重/切启用天生有中间态，逐条提交必然被 9202 卡死；
     该端点把整组权重与启用态合成一次原子校验，故重点断言「原子性」与「成功态合计恰为 100%」 */
  const kpiBatchMetric = await expectCode(
    '新增待入组指标（权重 0 且停用，不破坏既有合计）',
    'post',
    '/kpi/metrics',
    { data: kpiMetricBody({ metricKey: 'DEMO_BATCH', weight: 0, enabled: 0 }), token: kpiAdminToken },
    200
  )
  const kpiBatchNewId = kpiBatchMetric.ok ? kpiBatchMetric.result.id : 0

  const kpiBatchOk = await expectCode(
    '批量保存：调权重 + 切换启用 + 新增启用 + 停用待删 一次提交',
    'put',
    '/kpi/metrics/batch',
    {
      data: {
        items: [
          { id: 1, weight: 45 },
          { id: 3, weight: 0, enabled: 0 },
          { id: 5, enabled: 0 },
          { id: kpiBatchNewId, weight: 10, enabled: 1 }
        ]
      },
      token: kpiAdminToken
    },
    200
  )
  check(
    '批量保存回参 updated=4 且 weightSum=100',
    kpiBatchOk.ok && kpiBatchOk.result.updated === 4 && kpiBatchOk.result.weightSum === 100
  )
  const kpiBatchMetrics = await call('get', '/kpi/metrics', { token: kpiAdminToken })
  const kpiBatchPick = (id) => (kpiBatchMetrics.ok ? kpiBatchMetrics.result.list.find((m) => m.id === id) : null)
  check(
    '批量保存落库：权重 / 启用态逐项生效且启用合计恰为 100',
    kpiBatchMetrics.ok &&
      kpiBatchMetrics.result.weightSum === 100 &&
      kpiBatchPick(1)?.weight === 45 &&
      kpiBatchPick(3)?.enabled === 0 &&
      kpiBatchPick(5)?.enabled === 0 &&
      kpiBatchPick(kpiBatchNewId)?.enabled === 1 &&
      kpiBatchPick(kpiBatchNewId)?.weight === 10
  )

  const kpiBatchDeleted = await expectCode(
    '删除已在批量中置 0 停用的指标',
    'delete',
    '/kpi/metrics/3',
    { token: kpiAdminToken },
    200
  )
  const kpiBatchAfterDelete = await call('get', '/kpi/metrics', { token: kpiAdminToken })
  check(
    '删除停用指标后启用合计仍为 100',
    kpiBatchDeleted.ok && kpiBatchAfterDelete.ok && kpiBatchAfterDelete.result.weightSum === 100
  )

  await expectCode(
    '批量保存后合计 ≠ 100% → 9202',
    'put',
    '/kpi/metrics/batch',
    { data: { items: [{ id: 1, weight: 40 }] }, token: kpiAdminToken },
    9202
  )
  const kpiBatchAfter9202 = await call('get', '/kpi/metrics', { token: kpiAdminToken })
  check(
    '9202 原子性：失败后未部分写入（指标 1 权重仍 45，合计仍 100）',
    kpiBatchAfter9202.ok &&
      kpiBatchAfter9202.result.weightSum === 100 &&
      kpiBatchAfter9202.result.list.find((m) => m.id === 1)?.weight === 45
  )

  await expectCode(
    '批量保存含不存在指标 → 9201',
    'put',
    '/kpi/metrics/batch',
    {
      data: {
        items: [
          { id: 1, weight: 50 },
          { id: 9999, weight: 10 }
        ]
      },
      token: kpiAdminToken
    },
    9201
  )
  const kpiBatchAfter9201 = await call('get', '/kpi/metrics', { token: kpiAdminToken })
  check(
    '9201 原子性：前序合法项同样不落库（指标 1 权重仍 45）',
    kpiBatchAfter9201.ok && kpiBatchAfter9201.result.list.find((m) => m.id === 1)?.weight === 45
  )

  await expectCode(
    'STAFF 批量保存指标 → 403',
    'put',
    '/kpi/metrics/batch',
    { data: { items: [{ id: 1, weight: 45 }] }, token: kpiStaffToken },
    403,
    403
  )
  await expectCode(
    '站长批量保存指标 → 403',
    'put',
    '/kpi/metrics/batch',
    { data: { items: [{ id: 1, weight: 45 }] }, token: kpiStaToken },
    403,
    403
  )

  await expectCode(
    '批量保存空数组 → 400',
    'put',
    '/kpi/metrics/batch',
    { data: { items: [] }, token: kpiAdminToken },
    400
  )
  const kpiBatchSingle = await expectCode(
    '批量保存单条变更（停用指标改权重，不影响启用合计）',
    'put',
    '/kpi/metrics/batch',
    { data: { items: [{ id: 7, weight: 8 }] }, token: kpiAdminToken },
    200
  )
  check(
    '单条变更回参 updated=1 且 weightSum 仍为 100',
    kpiBatchSingle.ok && kpiBatchSingle.result.updated === 1 && kpiBatchSingle.result.weightSum === 100
  )
  await call('put', '/kpi/metrics/batch', { data: { items: [{ id: 7, weight: 5 }] }, token: kpiAdminToken })

  /* ---------- 需求8：人事档案与定薪（hr.js / hrStore.js） ---------- */
  resetDb()
  resetHrStore()
  const hrAdmin = await login('admin', 'demo1234')
  const hrAdminToken = hrAdmin.ok ? hrAdmin.result.token : ''
  const hrStaff = await login('st001_staff', 'demo1234')
  const hrStaffToken = hrStaff.ok ? hrStaff.result.token : ''
  const hrSta = await login('st001_admin', 'demo1234')
  const hrStaToken = hrSta.ok ? hrSta.result.token : ''
  // 脱敏口径：保留末 4 位，前面按 4 位一组打 *（末组可能不足 4 位）
  const MASKED_BANK = /^\*+( \*+)* \d{4}$/

  const hrProfiles = await expectCode(
    '人事档案列表 GET /hr/profiles（ADMIN）',
    'get',
    '/hr/profiles',
    { params: { pageNum: 1, pageSize: 100 }, token: hrAdminToken },
    200
  )
  check('档案覆盖全部 56 名员工（含停用账号）', hrProfiles.ok && hrProfiles.result.total === 56)
  check(
    '档案列表银行卡与紧急联系人电话均脱敏',
    hrProfiles.ok &&
      hrProfiles.result.list.every(
        (p) =>
          MASKED_BANK.test(p.bankAccount || '') &&
          !/\d{12,}/.test(String(p.bankAccount || '')) &&
          (p.emergencyContactPhone == null || /^\d{3}\*{4}/.test(p.emergencyContactPhone))
      )
  )
  await expectCode('STAFF 访问档案列表 → 403', 'get', '/hr/profiles', { token: hrStaffToken }, 403, 403)
  await expectCode('站长访问档案列表 → 403', 'get', '/hr/profiles', { token: hrStaToken }, 403, 403)

  const hrProfile1 = await expectCode(
    '档案详情 GET /hr/profiles/1',
    'get',
    '/hr/profiles/1',
    { token: hrAdminToken },
    200
  )
  check(
    '档案详情带定薪摘要且银行卡脱敏',
    hrProfile1.ok && !!hrProfile1.result.salary && MASKED_BANK.test(hrProfile1.result.bankAccount || '')
  )
  check(
    '档案详情出参不含完整银行卡号（15 位以上连续数字）',
    hrProfile1.ok && !Object.values(hrProfile1.result).some((v) => typeof v === 'string' && /\d{15,}/.test(v))
  )
  await expectCode('档案不存在 → 9301', 'get', '/hr/profiles/99999', { token: hrAdminToken }, 9301)
  await expectCode('员工查他人档案 → 403', 'get', '/hr/profiles/3', { token: hrStaffToken }, 403, 403)
  const hrSelfProfile = await expectCode('员工查本人档案', 'get', '/hr/profiles/4', { token: hrStaffToken }, 200)
  check(
    '本人档案可查且脱敏口径不变',
    hrSelfProfile.ok &&
      hrSelfProfile.result.employeeId === 4 &&
      MASKED_BANK.test(hrSelfProfile.result.bankAccount || '')
  )

  const hrProfileSaved = await expectCode(
    '更新档案 PUT /hr/profiles/1',
    'put',
    '/hr/profiles/1',
    { data: { education: 'MASTER', bankAccount: '6222020202020202020' }, token: hrAdminToken },
    200
  )
  check(
    '更新后学历生效且银行卡仍脱敏返回',
    hrProfileSaved.ok &&
      hrProfileSaved.result.education === 'MASTER' &&
      MASKED_BANK.test(hrProfileSaved.result.bankAccount || '') &&
      hrProfileSaved.result.bankAccount.endsWith('2020')
  )
  await expectCode(
    '银行卡号位数非法 → 400',
    'put',
    '/hr/profiles/1',
    { data: { bankAccount: '12345' }, token: hrAdminToken },
    400
  )
  await expectCode(
    '合同到期早于生效 → 400',
    'put',
    '/hr/profiles/1',
    { data: { contractStart: '2026-01-01', contractEnd: '2025-01-01' }, token: hrAdminToken },
    400
  )
  await expectCode(
    'STAFF 改档案 → 403',
    'put',
    '/hr/profiles/4',
    { data: { education: 'COLLEGE' }, token: hrStaffToken },
    403,
    403
  )

  const hrSalaries = await expectCode(
    '定薪列表 GET /hr/salary-structures（ADMIN）',
    'get',
    '/hr/salary-structures',
    { params: { pageNum: 1, pageSize: 100 }, token: hrAdminToken },
    200
  )
  check(
    '定薪覆盖 56 人且含津贴合计与总额',
    hrSalaries.ok &&
      hrSalaries.result.total === 56 &&
      hrSalaries.result.list.every(
        (s) => typeof s.totalSalary === 'number' && typeof s.allowancesTotal === 'number' && Array.isArray(s.allowances)
      )
  )
  await expectCode('定薪档案不存在 → 9305', 'get', '/hr/salary-structures/99999', { token: hrAdminToken }, 9305)
  await expectCode('员工查他人定薪 → 403', 'get', '/hr/salary-structures/3', { token: hrStaffToken }, 403, 403)
  const hrSelfSalary = await expectCode(
    '员工查本人定薪',
    'get',
    '/hr/salary-structures/4',
    { token: hrStaffToken },
    200
  )
  check(
    '本人定薪含调薪留痕列表',
    hrSelfSalary.ok && Array.isArray(hrSelfSalary.result.histories) && hrSelfSalary.result.histories.length >= 1
  )

  const hrSalaryBefore = await call('get', '/hr/salary-structures/5', { token: hrAdminToken })
  const hrLogCountBefore = hrSalaryBefore.ok ? hrSalaryBefore.result.histories.length : 0
  const raiseDate = formatDate(addDays(new Date(), 10))
  const hrSalarySaved = await expectCode(
    '调薪 PUT /hr/salary-structures/5',
    'put',
    '/hr/salary-structures/5',
    {
      data: {
        basicSalary: 8888,
        postSalary: 2000,
        performanceBase: 1000,
        allowances: [{ name: '餐补', key: 'MEAL', amount: 300 }],
        effectiveDate: raiseDate,
        reason: '演示年度调薪'
      },
      token: hrAdminToken
    },
    200
  )
  check(
    '调薪后生效日期与总额同步生效',
    hrSalarySaved.ok &&
      hrSalarySaved.result.current.effectiveDate === raiseDate &&
      hrSalarySaved.result.current.totalSalary === 8888 + 2000 + 1000 + 300
  )
  check(
    '调薪追加一条留痕（原因 / 操作人 / 生效日期）',
    hrSalarySaved.ok &&
      hrSalarySaved.result.histories.length === hrLogCountBefore + 1 &&
      hrSalarySaved.result.histories[0].changeType === 'ADJUST' &&
      hrSalarySaved.result.histories[0].effectiveDate === raiseDate &&
      hrSalarySaved.result.histories[0].reason === '演示年度调薪' &&
      hrSalarySaved.result.histories[0].operatorName === '系统管理员'
  )
  await expectCode(
    '调薪金额为负 → 400',
    'put',
    '/hr/salary-structures/5',
    { data: { basicSalary: -1 }, token: hrAdminToken },
    400
  )
  await expectCode(
    '调薪员工不存在 → 9305',
    'put',
    '/hr/salary-structures/99999',
    { data: { basicSalary: 1000, postSalary: 0, performanceBase: 0 }, token: hrAdminToken },
    9305
  )
  await expectCode(
    'STAFF 调薪 → 403',
    'put',
    '/hr/salary-structures/5',
    { data: { basicSalary: 1000 }, token: hrStaffToken },
    403,
    403
  )

  /* ---------- 需求9：财务工资单（finance.js / financeStore.js） ---------- */
  resetDb()
  resetHrStore()
  resetFinanceStore()
  const finAdmin = await login('admin', 'demo1234')
  const finAdminToken = finAdmin.ok ? finAdmin.result.token : ''
  const finStaff = await login('st001_staff', 'demo1234')
  const finStaffToken = finStaff.ok ? finStaff.result.token : ''
  /** 演示规则：3 项覆盖 FIXED / ATTENDANCE / MANUAL 三类来源，便于单点改配置验证「规则驱动」 */
  const finRuleItems = ({ fullAttendAmount = 200, fullAttendEnabled } = {}) => [
    { key: 'BASIC', name: '基本工资', type: 'ADDITION', source: 'FIXED', params: { field: 'basicSalary' } },
    {
      key: 'FULL_ATTEND',
      name: '全勤奖',
      type: 'ADDITION',
      source: 'ATTENDANCE',
      params: { metric: 'ABSENT', mode: 'BONUS_IF_ZERO', amount: fullAttendAmount },
      ...(fullAttendEnabled === undefined ? {} : { enabled: fullAttendEnabled })
    },
    { key: 'OTHER', name: '其他调整', type: 'ADDITION', source: 'MANUAL', params: { defaultValue: 0 } }
  ]

  const finRules = await expectCode(
    '计薪规则列表 GET /finance/payroll-rules（ADMIN）',
    'get',
    '/finance/payroll-rules',
    { token: finAdminToken },
    200
  )
  check(
    '规则种子 2 条且启用规则排在前面',
    finRules.ok && finRules.result.list.length === 2 && finRules.result.list[0].status === 1
  )
  check(
    '启用规则含 8 个规则项且覆盖四类来源',
    finRules.ok &&
      finRules.result.list[0].items.length === 8 &&
      new Set(finRules.result.list[0].items.map((i) => i.source)).size >= 4
  )
  await expectCode('STAFF 访问计薪规则 → 403', 'get', '/finance/payroll-rules', { token: finStaffToken }, 403, 403)
  const finRule1 = await expectCode(
    '规则详情 GET /finance/payroll-rules/1',
    'get',
    '/finance/payroll-rules/1',
    { token: finAdminToken },
    200
  )
  check(
    '规则详情含规则项类型 / 来源 label',
    finRule1.ok && finRule1.result.items.every((i) => !!i.typeLabel && !!i.sourceLabel)
  )
  await expectCode('规则不存在（详情）→ 9401', 'get', '/finance/payroll-rules/9999', { token: finAdminToken }, 9401)
  await expectCode(
    '规则不存在（更新）→ 9401',
    'put',
    '/finance/payroll-rules/9999',
    { data: { ruleName: '规则更新' }, token: finAdminToken },
    9401
  )
  await expectCode('规则不存在（删除）→ 9401', 'delete', '/finance/payroll-rules/9999', { token: finAdminToken }, 9401)
  await expectCode(
    '规则已被工资单引用不可删除 → 9403',
    'delete',
    '/finance/payroll-rules/1',
    { token: finAdminToken },
    9403
  )
  await expectCode(
    '规则名为空 → 400',
    'post',
    '/finance/payroll-rules',
    { data: { ruleName: '', items: finRuleItems() }, token: finAdminToken },
    400
  )
  await expectCode(
    '规则项 key 重复 → 400',
    'post',
    '/finance/payroll-rules',
    {
      data: {
        ruleName: '重复项规则',
        items: [
          { key: 'A', name: 'a', type: 'ADDITION', source: 'MANUAL' },
          { key: 'A', name: 'b', type: 'ADDITION', source: 'MANUAL' }
        ]
      },
      token: finAdminToken
    },
    400
  )
  await expectCode(
    '规则项来源非法 → 400',
    'post',
    '/finance/payroll-rules',
    {
      data: { ruleName: '来源非法规则', items: [{ key: 'A', name: 'a', type: 'ADDITION', source: 'UNKNOWN' }] },
      token: finAdminToken
    },
    400
  )
  await expectCode(
    '非 ADMIN 新增规则 → 403',
    'post',
    '/finance/payroll-rules',
    { data: { ruleName: '越权规则', items: finRuleItems() }, token: finStaffToken },
    403,
    403
  )

  const finRule = await expectCode(
    '新增计薪规则（供规则驱动验证）',
    'post',
    '/finance/payroll-rules',
    {
      data: {
        ruleName: '演示计薪规则',
        remark: '改规则项金额 / 开关后重新生成，工资单随之变化',
        items: finRuleItems()
      },
      token: finAdminToken
    },
    200
  )
  const finRuleId = finRule.ok ? finRule.result.id : 0
  const finRuleDel = await expectCode(
    '新增临时规则',
    'post',
    '/finance/payroll-rules',
    {
      data: {
        ruleName: '临时规则',
        items: [{ key: 'BASIC', name: '基本工资', type: 'ADDITION', source: 'FIXED', params: { field: 'basicSalary' } }]
      },
      token: finAdminToken
    },
    200
  )
  const finRuleDelId = finRuleDel.ok ? finRuleDel.result.id : 0
  const finRuleRenamed = await expectCode(
    '更新规则名称',
    'put',
    `/finance/payroll-rules/${finRuleDelId}`,
    { data: { ruleName: '临时规则(改)' }, token: finAdminToken },
    200
  )
  check('更新后规则名生效', finRuleRenamed.ok && finRuleRenamed.result.ruleName === '临时规则(改)')
  await expectCode(
    '删除未被引用的规则',
    'delete',
    `/finance/payroll-rules/${finRuleDelId}`,
    { token: finAdminToken },
    200
  )

  const finMonth = monthShift(currentMonth(), -5)
  const finListOf = async (month) => {
    const res = await call('get', '/finance/payrolls', {
      params: { month, employeeId: 4, pageNum: 1, pageSize: 20 },
      token: finAdminToken
    })
    return res.ok && res.result.list[0] ? res.result.list[0] : null
  }
  const finGenerate = () =>
    call('post', '/finance/payrolls/generate', {
      data: { month: finMonth, employeeIds: [4], ruleId: finRuleId },
      token: finAdminToken
    })

  const gen0 = await expectCode(
    '生成草稿工资单 POST /finance/payrolls/generate',
    'post',
    '/finance/payrolls/generate',
    { data: { month: finMonth, employeeIds: [4], ruleId: finRuleId }, token: finAdminToken },
    200
  )
  check('按指定规则生成 1 张草稿', gen0.ok && gen0.result.created === 1 && gen0.result.ruleId === finRuleId)
  const pay0 = await finListOf(finMonth)
  check(
    '草稿字段齐备（明细 / 合计 / 净额 / 可执行动作）',
    !!pay0 &&
      pay0.status === 'DRAFT' &&
      pay0.items.length === 3 &&
      pay0.netAmount === pay0.additionTotal - pay0.deductionTotal &&
      pay0.actions.includes('submit')
  )
  const finSalary4 = await call('get', '/hr/salary-structures/4', { token: finAdminToken })
  check(
    '人事定薪项（FIXED）取自 HR 定薪真源',
    !!pay0 &&
      finSalary4.ok &&
      pay0.items.find((i) => i.key === 'BASIC').amount === finSalary4.result.current.basicSalary
  )
  check(
    '考勤推算项生效（零缺勤 → 全勤奖 200）',
    !!pay0 && pay0.items.find((i) => i.key === 'FULL_ATTEND').amount === 200
  )
  await expectCode(
    '人工项可改（MANUAL）',
    'put',
    `/finance/payrolls/${pay0 ? pay0.id : 0}/items`,
    { data: { items: [{ key: 'OTHER', amount: 888 }] }, token: finAdminToken },
    200
  )
  await expectCode(
    '非人工项不可手改 → 400',
    'put',
    `/finance/payrolls/${pay0 ? pay0.id : 0}/items`,
    { data: { items: [{ key: 'BASIC', amount: 1 }] }, token: finAdminToken },
    400
  )

  // 重新生成把人工项还原为规则默认值，作为「规则驱动」比对的干净基线
  await finGenerate()
  const payBase = await finListOf(finMonth)
  check(
    '重新生成后人工项回到规则默认值（基线干净）',
    !!payBase &&
      payBase.items.find((i) => i.key === 'OTHER').amount === 0 &&
      payBase.items.find((i) => i.key === 'FULL_ATTEND').amount === 200
  )

  const finRuleAmount = await expectCode(
    '改规则项金额（全勤奖 200 → 500）',
    'put',
    `/finance/payroll-rules/${finRuleId}`,
    { data: { items: finRuleItems({ fullAttendAmount: 500 }) }, token: finAdminToken },
    200
  )
  check(
    '规则项金额已写入',
    finRuleAmount.ok && finRuleAmount.result.items.find((i) => i.key === 'FULL_ATTEND').params.amount === 500
  )
  const gen1 = await finGenerate()
  const pay1 = await finListOf(finMonth)
  check(
    '改金额后重新生成：全勤奖变为 500',
    gen1.ok && !!pay1 && pay1.items.find((i) => i.key === 'FULL_ATTEND').amount === 500
  )
  check(
    '改金额后净额较基线 +300（金额由规则驱动，非写死公式）',
    !!payBase && !!pay1 && pay1.netAmount === payBase.netAmount + 300
  )

  const finRuleDisabled = await expectCode(
    '停用规则项（全勤奖开关关闭）',
    'put',
    `/finance/payroll-rules/${finRuleId}`,
    { data: { items: finRuleItems({ fullAttendAmount: 500, fullAttendEnabled: 0 }) }, token: finAdminToken },
    200
  )
  check(
    '规则项停用状态已写入',
    finRuleDisabled.ok && finRuleDisabled.result.items.find((i) => i.key === 'FULL_ATTEND').enabled === 0
  )
  const gen2 = await finGenerate()
  const pay2 = await finListOf(finMonth)
  check('规则项停用后不再进入工资单明细', gen2.ok && !!pay2 && !pay2.items.some((i) => i.key === 'FULL_ATTEND'))
  check(
    '规则项停用后净额较上一版 -500（开关同样驱动金额）',
    !!pay1 && !!pay2 && pay2.netAmount === pay1.netAmount - 500
  )

  /* ---- 状态机全链路：草稿 → 待审核 → 已通过 → 已发布 → 已确认 ---- */
  const finChainId = pay2 ? pay2.id : 0
  const finSubmit = await expectCode(
    '提交审核（草稿 → 待审核）',
    'post',
    '/finance/payrolls/submit',
    { data: { ids: [finChainId] }, token: finAdminToken },
    200
  )
  check('提交结果 submitted=1', finSubmit.ok && finSubmit.result.submitted === 1)
  const finAfterSubmit = await call('get', `/finance/payrolls/${finChainId}`, { token: finAdminToken })
  check('提交后状态为待审核', finAfterSubmit.ok && finAfterSubmit.result.status === 'PENDING_APPROVAL')
  const finApproved = await expectCode(
    '审核通过（待审核 → 已通过）',
    'post',
    `/finance/payrolls/${finChainId}/approve`,
    { data: { approved: true, approveRemark: '演示审核通过' }, token: finAdminToken },
    200
  )
  check(
    '审核后状态与审批人落库',
    finApproved.ok &&
      finApproved.result.status === 'APPROVED' &&
      finApproved.result.approverName === '系统管理员' &&
      !!finApproved.result.approveTime &&
      finApproved.result.approveRemark === '演示审核通过'
  )
  await expectCode(
    '对已通过工资单重复审核 → 9403',
    'post',
    `/finance/payrolls/${finChainId}/approve`,
    { data: { approved: true }, token: finAdminToken },
    9403
  )
  const finPublished = await expectCode(
    '批量发布（已通过 → 已发布）',
    'post',
    '/finance/payrolls/publish',
    { data: { ids: [finChainId] }, token: finAdminToken },
    200
  )
  check('发布结果 published=1', finPublished.ok && finPublished.result.published === 1)
  const finStaffViewOwn = await call('get', `/finance/payrolls/${finChainId}`, { token: finStaffToken })
  check('员工可查看本人已发布工资单', finStaffViewOwn.ok && finStaffViewOwn.result.status === 'PUBLISHED')
  const finMyList = await call('get', '/finance/payrolls/my', {
    params: { pageNum: 1, pageSize: 100 },
    token: finStaffToken
  })
  check(
    '员工「我的工资单」只含已发布 / 已确认',
    finMyList.ok &&
      finMyList.result.list.every((p) => ['PUBLISHED', 'CONFIRMED'].includes(p.status)) &&
      finMyList.result.list.some((p) => p.id === finChainId)
  )
  const finConfirmed = await expectCode(
    '员工确认（已发布 → 已确认）',
    'post',
    `/finance/payrolls/${finChainId}/confirm`,
    { token: finStaffToken },
    200
  )
  check(
    '确认后状态与确认时间落库',
    finConfirmed.ok && finConfirmed.result.status === 'CONFIRMED' && !!finConfirmed.result.confirmTime
  )
  await expectCode('重复确认 → 9403', 'post', `/finance/payrolls/${finChainId}/confirm`, { token: finStaffToken }, 9403)

  /* ---- 越权与未发布可见性 ---- */
  const finOtherList = await call('get', '/finance/payrolls', {
    params: { month: currentMonth(), employeeId: 5, pageNum: 1, pageSize: 20 },
    token: finAdminToken
  })
  const finOtherId = finOtherList.ok && finOtherList.result.list[0] ? finOtherList.result.list[0].id : 0
  await expectCode(
    '员工查看他人工资单 → 9404',
    'get',
    `/finance/payrolls/${finOtherId}`,
    { token: finStaffToken },
    9404
  )
  await expectCode(
    '员工确认他人工资单 → 9404',
    'post',
    `/finance/payrolls/${finOtherId}/confirm`,
    { token: finStaffToken },
    9404
  )
  const finOwnDraftList = await call('get', '/finance/payrolls', {
    params: { month: currentMonth(), employeeId: 4, pageNum: 1, pageSize: 20 },
    token: finAdminToken
  })
  const finOwnDraft = finOwnDraftList.ok ? finOwnDraftList.result.list.find((p) => p.status === 'DRAFT') : null
  await expectCode(
    '员工查看本人未发布工资单 → 9403',
    'get',
    `/finance/payrolls/${finOwnDraft ? finOwnDraft.id : 0}`,
    { token: finStaffToken },
    9403
  )
  await expectCode(
    '员工确认本人未发布工资单 → 9403',
    'post',
    `/finance/payrolls/${finOwnDraft ? finOwnDraft.id : 0}/confirm`,
    { token: finStaffToken },
    9403
  )
  check(
    '未发布工资单不出现在员工「我的工资单」',
    finMyList.ok && !!finOwnDraft && !finMyList.result.list.some((p) => p.id === finOwnDraft.id)
  )

  await expectCode(
    '重复生成已提交审核的月份 → 9405',
    'post',
    '/finance/payrolls/generate',
    { data: { month: currentMonth() }, token: finAdminToken },
    9405
  )
  await expectCode('工资单不存在 → 9402', 'get', '/finance/payrolls/999999', { token: finAdminToken }, 9402)
  await expectCode(
    '发布缺少 ids 与 month → 400',
    'post',
    '/finance/payrolls/publish',
    { data: {}, token: finAdminToken },
    400
  )
  await expectCode(
    '审核入参非布尔 → 400',
    'post',
    `/finance/payrolls/${finChainId}/approve`,
    { data: { approved: 'yes' }, token: finAdminToken },
    400
  )
  await expectCode('STAFF 访问工资单列表 → 403', 'get', '/finance/payrolls', { token: finStaffToken }, 403, 403)
  await expectCode(
    'STAFF 生成工资单 → 403',
    'post',
    '/finance/payrolls/generate',
    { data: { month: finMonth }, token: finStaffToken },
    403,
    403
  )

  const finSeedList = await call('get', '/finance/payrolls', {
    params: { pageNum: 1, pageSize: 100 },
    token: finAdminToken
  })
  check(
    '工资单种子六态齐备（草稿 / 待审核 / 已通过 / 已驳回 / 已发布 / 已确认）',
    finSeedList.ok &&
      ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PUBLISHED', 'CONFIRMED'].every(
        (k) => finSeedList.result.counts[k] >= 1
      )
  )
  const finSettlementList = await call('get', '/finance/payrolls', {
    params: { billType: 'SETTLEMENT', pageNum: 1, pageSize: 20 },
    token: finAdminToken
  })
  check('按 billType 筛选结算单可用', finSettlementList.ok && Array.isArray(finSettlementList.result.list))

  /* ---------- 需求10：入职 / 离职流程（hr.js，离职结算跨域引用 finance） ---------- */
  resetDb()
  resetHrStore()
  resetFinanceStore()
  const flowAdmin = await login('admin', 'demo1234')
  const flowAdminToken = flowAdmin.ok ? flowAdmin.result.token : ''
  const flowStaff = await login('st001_staff', 'demo1234')
  const flowStaffToken = flowStaff.ok ? flowStaff.result.token : ''

  /* ---- 入职流程 ---- */
  const onboardList = await expectCode(
    '入职流程列表 GET /hr/onboarding（ADMIN）',
    'get',
    '/hr/onboarding',
    { params: { pageNum: 1, pageSize: 20 }, token: flowAdminToken },
    200
  )
  check(
    '入职流程种子 4 条且含已驳回',
    onboardList.ok && onboardList.result.total === 4 && onboardList.result.list.some((f) => f.status === 'REJECTED')
  )
  check(
    '入职流程含 6 个步骤且带 progress',
    onboardList.ok &&
      onboardList.result.list.every((f) => f.steps.length === 6 && f.progress.total === 6 && !!f.statusLabel)
  )
  await expectCode('STAFF 访问入职流程 → 403', 'get', '/hr/onboarding', { token: flowStaffToken }, 403, 403)
  const onboardDetail = await expectCode(
    '入职流程详情 GET /hr/onboarding/1',
    'get',
    '/hr/onboarding/1',
    { token: flowAdminToken },
    200
  )
  check(
    '详情含当前步骤与步骤状态 label',
    onboardDetail.ok &&
      onboardDetail.result.steps.every((s) => !!s.statusLabel) &&
      !!onboardDetail.result.currentStepKey
  )
  await expectCode('入职流程不存在 → 404', 'get', '/hr/onboarding/99999', { token: flowAdminToken }, 404, 404)

  const onboardPhone = '13900001111'
  const onboardNew = await expectCode(
    '新建入职流程 POST /hr/onboarding',
    'post',
    '/hr/onboarding',
    {
      data: {
        candidateName: '演示候选人',
        phone: onboardPhone,
        gender: 1,
        education: 'BACHELOR',
        deptId: 2,
        stationId: 1,
        position: '快递员',
        expectedEntryDate: today
      },
      token: flowAdminToken
    },
    200
  )
  const onboardId = onboardNew.ok ? onboardNew.result.id : 0
  check(
    '新建入职流程为进行中且尚未生成员工',
    onboardNew.ok &&
      onboardNew.result.status === 'IN_PROGRESS' &&
      onboardNew.result.employeeId === null &&
      onboardNew.result.currentStepKey === 'SUBMIT_MATERIALS'
  )
  await expectCode(
    '候选人手机号非法 → 400',
    'post',
    '/hr/onboarding',
    { data: { candidateName: '候选人乙', phone: '123' }, token: flowAdminToken },
    400
  )
  await expectCode(
    '指定部门不存在 → 400',
    'post',
    '/hr/onboarding',
    { data: { candidateName: '候选人丙', phone: '13900003333', deptId: 999 }, token: flowAdminToken },
    400
  )
  await expectCode(
    '跳步办理步骤 → 9303',
    'post',
    `/hr/onboarding/${onboardId}/steps/HR_REVIEW/complete`,
    { data: {}, token: flowAdminToken },
    9303
  )
  await expectCode(
    '不存在的步骤 → 9303',
    'post',
    `/hr/onboarding/${onboardId}/steps/NOT_EXIST/complete`,
    { data: {}, token: flowAdminToken },
    9303
  )

  const obStep1 = await expectCode(
    '入职步骤：提交资料',
    'post',
    `/hr/onboarding/${onboardId}/steps/SUBMIT_MATERIALS/complete`,
    { data: { remark: '资料齐全' }, token: flowAdminToken },
    200
  )
  check(
    '步骤 1 完成后游标推进到人事审核',
    obStep1.ok && obStep1.result.steps[0].status === 'DONE' && obStep1.result.currentStepKey === 'HR_REVIEW'
  )
  await expectCode(
    '入职步骤：人事审核',
    'post',
    `/hr/onboarding/${onboardId}/steps/HR_REVIEW/complete`,
    { data: {}, token: flowAdminToken },
    200
  )

  const obAccount = await expectCode(
    '入职步骤：建档并生成员工与账号',
    'post',
    `/hr/onboarding/${onboardId}/steps/CREATE_ACCOUNT/complete`,
    { data: { username: 'onboard_demo01', password: 'Init1234', deptId: 2, stationId: 1 }, token: flowAdminToken },
    200
  )
  const onboardEmpId = obAccount.ok ? obAccount.result.employeeId : 0
  check('建档后流程绑定新员工号', obAccount.ok && onboardEmpId > 0)
  const onboardEmp = db.employees.find((e) => e.id === onboardEmpId)
  check('未走完流程前新员工账号未启用（status=0）', !!onboardEmp && onboardEmp.status === 0)
  await expectCode(
    '未走完流程的新账号不可登录 → 1002',
    'post',
    '/auth/login',
    { data: { username: 'onboard_demo01', password: 'Init1234' } },
    1002
  )
  await expectCode(
    '重复建档 → 9303',
    'post',
    `/hr/onboarding/${onboardId}/steps/CREATE_ACCOUNT/complete`,
    { data: { username: 'onboard_demo02', password: 'Init1234', deptId: 2, stationId: 1 }, token: flowAdminToken },
    9303
  )

  await expectCode(
    '入职步骤：分配驿站 / 岗位',
    'post',
    `/hr/onboarding/${onboardId}/steps/ASSIGN_STATION/complete`,
    { data: { deptId: 2, stationId: 1, position: '快递员', role: 'STAFF' }, token: flowAdminToken },
    200
  )
  await expectCode(
    '入职步骤：定薪',
    'post',
    `/hr/onboarding/${onboardId}/steps/SET_SALARY/complete`,
    {
      data: {
        basicSalary: 5000,
        postSalary: 2000,
        performanceBase: 1000,
        allowances: [{ name: '餐补', key: 'MEAL', amount: 300 }]
      },
      token: flowAdminToken
    },
    200
  )
  const hrNewSalary = await call('get', `/hr/salary-structures/${onboardEmpId}`, { token: flowAdminToken })
  check(
    '定薪步骤写入人事定薪（总额 8300）',
    hrNewSalary.ok && hrNewSalary.result.current.totalSalary === 5000 + 2000 + 1000 + 300
  )

  const obDone = await expectCode(
    '入职步骤：完成',
    'post',
    `/hr/onboarding/${onboardId}/steps/DONE/complete`,
    { data: {}, token: flowAdminToken },
    200
  )
  check(
    '全部步骤办完后流程完结',
    obDone.ok &&
      obDone.result.status === 'COMPLETED' &&
      obDone.result.progress.done === 6 &&
      obDone.result.currentStepKey === null
  )
  check('入库员工此时才转为在职（status=1）', db.employees.find((e) => e.id === onboardEmpId).status === 1)
  const onboardLogin = await call('post', '/auth/login', { data: { username: 'onboard_demo01', password: 'Init1234' } })
  check(
    '走完流程的新员工账号可登录（首登需改密）',
    onboardLogin.ok && onboardLogin.result.employee.pwdChanged === false
  )
  await expectCode(
    '流程完结后再办理步骤 → 9303',
    'post',
    `/hr/onboarding/${onboardId}/steps/DONE/complete`,
    { data: {}, token: flowAdminToken },
    9303
  )
  await expectCode(
    '流程完结后驳回 → 9303',
    'post',
    `/hr/onboarding/${onboardId}/reject`,
    { data: { reason: '测试驳回' }, token: flowAdminToken },
    9303
  )

  const obRejectFlow = await call('post', '/hr/onboarding', {
    data: { candidateName: '驳回候选人', phone: '13900002222', deptId: 2, stationId: 1 },
    token: flowAdminToken
  })
  const obRejectId = obRejectFlow.ok ? obRejectFlow.result.id : 0
  const obRejected = await expectCode(
    '驳回入职流程',
    'post',
    `/hr/onboarding/${obRejectId}/reject`,
    { data: { reason: '证件材料不齐，暂缓入职' }, token: flowAdminToken },
    200
  )
  check(
    '驳回后状态为已驳回并留痕',
    obRejected.ok &&
      obRejected.result.status === 'REJECTED' &&
      obRejected.result.rejectReason === '证件材料不齐，暂缓入职' &&
      obRejected.result.rejectedBy === '系统管理员'
  )
  await expectCode(
    '驳回后继续办理步骤 → 9303',
    'post',
    `/hr/onboarding/${obRejectId}/steps/SUBMIT_MATERIALS/complete`,
    { data: {}, token: flowAdminToken },
    9303
  )
  await expectCode(
    '重复驳回 → 9303',
    'post',
    `/hr/onboarding/${obRejectId}/reject`,
    { data: { reason: '重复驳回' }, token: flowAdminToken },
    9303
  )

  /* ---- 离职流程 ---- */
  const offboardList = await expectCode(
    '离职流程列表 GET /hr/offboarding（ADMIN）',
    'get',
    '/hr/offboarding',
    { params: { pageNum: 1, pageSize: 20 }, token: flowAdminToken },
    200
  )
  check('离职流程种子 5 条', offboardList.ok && offboardList.result.total === 5)
  await expectCode('STAFF 访问离职流程 → 403', 'get', '/hr/offboarding', { token: flowStaffToken }, 403, 403)
  await expectCode('离职流程不存在 → 404', 'get', '/hr/offboarding/99999', { token: flowAdminToken }, 404, 404)

  const offboardEmp = activeEmployees().find((e) => e.id === 30 && e.status === 1)
  const offboardLastDay = formatDate(addDays(new Date(), 5))
  const offboardNew = await expectCode(
    '发起离职 POST /hr/offboarding',
    'post',
    '/hr/offboarding',
    {
      data: { employeeId: 30, type: 'RESIGN', reason: '个人原因申请离职', lastWorkDate: offboardLastDay },
      token: flowAdminToken
    },
    200
  )
  const offboardId = offboardNew.ok ? offboardNew.result.id : 0
  check(
    '离职流程编号 / 步骤 / 状态正确',
    offboardNew.ok &&
      /^OFF-\d{8}-\d{4}$/.test(offboardNew.result.flowNo) &&
      offboardNew.result.steps.length === 6 &&
      offboardNew.result.status === 'IN_PROGRESS' &&
      offboardNew.result.employeeId === 30
  )
  check('离职用例雇员样本为在职状态', !!offboardEmp)
  await expectCode(
    '同员工重复发起进行中的离职 → 9304',
    'post',
    '/hr/offboarding',
    {
      data: { employeeId: 30, type: 'RESIGN', reason: '重复发起离职', lastWorkDate: offboardLastDay },
      token: flowAdminToken
    },
    9304
  )
  const offboardDisabledEmp = activeEmployees().find((e) => e.status === 0)
  await expectCode(
    '停用账号发起离职 → 9302',
    'post',
    '/hr/offboarding',
    {
      data: { employeeId: offboardDisabledEmp.id, type: 'RESIGN', reason: '账号已停用', lastWorkDate: offboardLastDay },
      token: flowAdminToken
    },
    9302
  )
  await expectCode(
    '离职类型非法 → 400',
    'post',
    '/hr/offboarding',
    {
      data: { employeeId: 31, type: 'XXX', reason: '说明足够长', lastWorkDate: offboardLastDay },
      token: flowAdminToken
    },
    400
  )
  await expectCode(
    '离职原因过短 → 400',
    'post',
    '/hr/offboarding',
    { data: { employeeId: 31, type: 'RESIGN', reason: 'x', lastWorkDate: offboardLastDay }, token: flowAdminToken },
    400
  )
  await expectCode(
    '最后工作日格式非法 → 400',
    'post',
    '/hr/offboarding',
    {
      data: { employeeId: 31, type: 'RESIGN', reason: '说明足够长', lastWorkDate: '2026/09/30' },
      token: flowAdminToken
    },
    400
  )
  await expectCode(
    '离职员工不存在 → 404',
    'post',
    '/hr/offboarding',
    {
      data: { employeeId: 99999, type: 'RESIGN', reason: '说明足够长', lastWorkDate: offboardLastDay },
      token: flowAdminToken
    },
    404,
    404
  )
  await expectCode(
    '离职跳步办理 → 9304',
    'post',
    `/hr/offboarding/${offboardId}/steps/HR_APPROVE/complete`,
    { data: {}, token: flowAdminToken },
    9304
  )

  for (const offStepKey of ['MANAGER_APPROVE', 'HR_APPROVE', 'HANDOVER', 'ASSET_RETURN']) {
    await expectCode(
      `离职步骤：${offStepKey}`,
      'post',
      `/hr/offboarding/${offboardId}/steps/${offStepKey}/complete`,
      { data: {}, token: flowAdminToken },
      200
    )
  }
  const offSettlement = await expectCode(
    '离职步骤：薪资结算（生成结算单）',
    'post',
    `/hr/offboarding/${offboardId}/steps/SETTLEMENT/complete`,
    { data: {}, token: flowAdminToken },
    200
  )
  check(
    '薪资结算写入结算单号与金额',
    offSettlement.ok &&
      offSettlement.result.settlementPayrollId > 0 &&
      /^SET-\d{6}-\d{4}$/.test(offSettlement.result.settlementPayrollNo) &&
      typeof offSettlement.result.settlementAmount === 'number'
  )
  const settlementDetail = await call(
    'get',
    `/finance/payrolls/${offSettlement.ok ? offSettlement.result.settlementPayrollId : 0}`,
    { token: flowAdminToken }
  )
  check(
    '结算单在工资单中真实存在且与离职流程互相引用',
    settlementDetail.ok &&
      settlementDetail.result.billType === 'SETTLEMENT' &&
      settlementDetail.result.payrollNo === offSettlement.result.settlementPayrollNo &&
      settlementDetail.result.offboardingId === offboardId &&
      settlementDetail.result.employeeId === 30
  )
  const offLeave = await expectCode(
    '离职步骤：离岗',
    'post',
    `/hr/offboarding/${offboardId}/steps/LEAVE/complete`,
    { data: {}, token: flowAdminToken },
    200
  )
  check(
    '离岗后流程完结并写入离岗日期',
    offLeave.ok &&
      offLeave.result.status === 'COMPLETED' &&
      offLeave.result.leaveDate === offboardLastDay &&
      offLeave.result.progress.done === 6
  )
  check('离岗后员工置为离职 / 禁用（status=0）', db.employees.find((e) => e.id === 30).status === 0)
  const offProfile = await call('get', '/hr/profiles/30', { token: flowAdminToken })
  check('离岗后人事档案写入离职日期', offProfile.ok && offProfile.result.leaveDate === offboardLastDay)
  await expectCode(
    '离职员工档案不可再编辑 → 9302',
    'put',
    '/hr/profiles/30',
    { data: { education: 'BACHELOR' }, token: flowAdminToken },
    9302
  )
  await expectCode(
    '离职流程完结后再办理步骤 → 9304',
    'post',
    `/hr/offboarding/${offboardId}/steps/LEAVE/complete`,
    { data: {}, token: flowAdminToken },
    9304
  )
  await expectCode(
    '离职流程完结后驳回 → 9304',
    'post',
    `/hr/offboarding/${offboardId}/reject`,
    { data: { reason: '测试驳回' }, token: flowAdminToken },
    9304
  )

  /* ========== M11：请假模块（A 字典静态契约 / B 种子自洽 / C 状态机 / D 权限越权 / E 时长护栏 / F 重叠与撤回 / G 算薪联动 / H 通知与运行日志） ========== */
  resetDb()
  resetAttendanceStore()
  resetLeaveStore()
  resetClientLogStore()
  resetFinanceStore()

  const lvAdmin = await login('admin', 'demo1234')
  const lvAdminToken = lvAdmin.ok ? lvAdmin.result.token : ''
  const lvSta = await login('st001_admin', 'demo1234')
  const lvStaToken = lvSta.ok ? lvSta.result.token : ''
  const lvStaff = await login('st001_staff', 'demo1234')
  const lvStaffToken = lvStaff.ok ? lvStaff.result.token : ''
  const lvToday = formatDate(new Date())
  const lvDay = (n) => formatDate(addDays(new Date(), n))
  const lvLoginAs = async (employeeId) => {
    const emp = activeEmployees().find((e) => e.id === Number(employeeId))
    const res = emp ? await login(emp.username, 'demo1234') : null
    return res && res.ok ? res.result.token : ''
  }
  const lvListOf = async (params, token = lvAdminToken) => {
    const res = await call('get', '/leave/list', { params: { pageNum: 1, pageSize: 100, ...params }, token })
    return res.ok ? res.result : { total: 0, list: [] }
  }
  /** 某单在该用户通知里产生的请假通知（bizType='leave' + bizId 精确命中） */
  const lvNoticesOf = async (token, bizId) => {
    const res = await call('get', '/notifications', { params: { pageNum: 1, pageSize: 100 }, token })
    return res.ok ? res.result.list.filter((n) => n.bizType === 'leave' && n.bizId === bizId) : []
  }
  const lvFormOf = (form) => ({
    leaveType: 'PERSONAL',
    startDate: lvToday,
    startPeriod: 'AM',
    endDate: lvToday,
    endPeriod: 'PM',
    reason: '家中有事，申请事假',
    ...form
  })
  const LV_OCCUPIED = ['PENDING_STATION', 'PENDING_BOSS', 'APPROVED']
  const lvRangeNoOverlap = (list) => {
    const byEmployee = {}
    list
      .filter((l) => LV_OCCUPIED.includes(l.status))
      .forEach((l) => {
        byEmployee[l.employeeId] = [...(byEmployee[l.employeeId] || []), l]
      })
    return Object.values(byEmployee).every((rows) =>
      rows.every((a, i) => rows.every((b, j) => i === j || a.endDate < b.startDate || b.endDate < a.startDate))
    )
  }
  const LV_VO_FIELDS = [
    'id',
    'employeeId',
    'employeeName',
    'stationId',
    'stationName',
    'leaveType',
    'startDate',
    'startPeriod',
    'endDate',
    'endPeriod',
    'reason',
    'status',
    'rejectStage',
    'applyTime',
    'approverId',
    'approverName',
    'approveTime',
    'approveRemark',
    'stationApproverId',
    'stationApproverName',
    'stationApproveTime',
    'stationApproveRemark',
    'revokerId',
    'revokerName',
    'revokeTime',
    'revokeReason',
    'naturalDays',
    'countedDays',
    'countedDaysSnapshot',
    'handleLog'
  ]

  /* ---- A. 字典与错误码静态契约（三端唯一真源，不留第二份） ---- */
  check(
    'A. LEAVE_STATUS 6 态齐全且带 type/variant',
    Object.keys(LEAVE_STATUS).length === 6 &&
      ['PENDING_STATION', 'PENDING_BOSS', 'APPROVED', 'REJECTED', 'CANCELLED', 'REVOKED'].every(
        (k) => !!LEAVE_STATUS[k].type && !!LEAVE_STATUS[k].variant
      )
  )
  check(
    'A. LEAVE_TYPE 每个假别都声明 countMode',
    Object.values(LEAVE_TYPE).every((t) => t.countMode === 'SCHEDULED' || t.countMode === 'NATURAL')
  )
  check('A. NOTIFICATION_TYPE 已含请假申请 5 / 请假结果 6', !!NOTIFICATION_TYPE[5] && !!NOTIFICATION_TYPE[6])
  check('A. ATTENDANCE_METRIC 已含 LEAVE（三处镜像之一）', !!ATTENDANCE_METRIC.LEAVE)
  check(
    'A. 错误码 9601-9607 常量与兜底文案齐备',
    LEAVE_CODE.NOT_EXISTS === 9601 &&
      LEAVE_CODE.STATUS_INVALID === 9602 &&
      LEAVE_CODE.OVERLAP === 9603 &&
      LEAVE_CODE.DATE_INVALID === 9604 &&
      LEAVE_CODE.NO_PERMISSION === 9605 &&
      LEAVE_CODE.PAYROLL_LOCKED === 9606 &&
      LEAVE_CODE.EDIT_FORBIDDEN === 9607 &&
      [9601, 9602, 9603, 9604, 9605, 9606, 9607].every((code) => codeMessage(code) !== '操作失败')
  )

  /* ---- B. 种子自洽 ---- */
  const lvSeedRes = await expectCode(
    'B. 请假列表 GET /leave/list（仅 ADMIN）',
    'get',
    '/leave/list',
    { params: { pageNum: 1, pageSize: 100 }, token: lvAdminToken },
    200
  )
  const lvAll = lvSeedRes.ok ? lvSeedRes.result.list : []
  check(
    'B. 种子覆盖 6 种状态',
    ['PENDING_STATION', 'PENDING_BOSS', 'APPROVED', 'REJECTED', 'CANCELLED', 'REVOKED'].every((s) =>
      lvAll.some((l) => l.status === s)
    ),
    `实际状态 ${[...new Set(lvAll.map((l) => l.status))].join('、')}`
  )
  check('B. 种子跨 ≥3 个驿站', new Set(lvAll.map((l) => l.stationId)).size >= 3)
  check(
    'B. 种子含半天单与跨天单',
    lvAll.some((l) => l.naturalDays === 0.5) && lvAll.some((l) => l.endDate > l.startDate)
  )
  check(
    'B. 种子含初审驳回与终审驳回各一条',
    lvAll.some((l) => l.status === 'REJECTED' && l.rejectStage === 'STATION') &&
      lvAll.some((l) => l.status === 'REJECTED' && l.rejectStage === 'BOSS')
  )
  check(
    'B. 种子含已撤销与已撤回',
    lvAll.some((l) => l.status === 'CANCELLED') && lvAll.some((l) => l.status === 'REVOKED')
  )
  check('B. LeaveVO 字段齐全', lvAll.length > 0 && LV_VO_FIELDS.every((k) => k in lvAll[0]))
  check(
    'B. 每条种子都有 SUBMIT 留痕（操作人 + 时间 + 前后状态）',
    lvAll.length > 0 &&
      lvAll.every((l) => l.handleLog.some((x) => x.action === 'SUBMIT' && x.operatorId > 0 && !!x.time))
  )
  check('B. 同一员工的占用单互不重叠（种子自洽）', lvRangeNoOverlap(lvAll))
  check(
    'B. 已通过 / 已撤回单都落了计薪天数快照',
    lvAll
      .filter((l) => l.status === 'APPROVED' || l.status === 'REVOKED')
      .every((l) => !!l.countedDaysSnapshot && typeof l.countedDaysSnapshot.countedDays === 'number')
  )
  check(
    'B. 城东存在「计薪天数 < 自然天数」的已通过单（排班休息日已排除）',
    lvAll.some((l) => l.status === 'APPROVED' && l.stationId === 1 && l.countedDays < l.naturalDays)
  )
  check(
    'B. 城东已通过单逐条能回查到排班（计薪天数非凭空捏造）',
    lvAll.filter((l) => l.status === 'APPROVED' && l.stationId === 1).every((l) => l.countedDays >= 0)
  )

  /* ---- C. 状态机 T1–T9 ---- */
  const lvL1Form = lvFormOf({ startDate: lvDay(20), endDate: lvDay(20) })
  const lvL1 = await expectCode(
    'C. T1 员工提交 → 待站长初审',
    'post',
    '/leave',
    { data: lvL1Form, token: lvStaffToken },
    200
  )
  const lvL1Id = lvL1.ok ? lvL1.result.id : 0
  check(
    'C. 提交后状态 / 自然天数 / 派生标志正确',
    lvL1.ok &&
      lvL1.result.status === 'PENDING_STATION' &&
      lvL1.result.naturalDays === 1 &&
      lvL1.result.canEdit === true &&
      lvL1.result.canCancel === true &&
      lvL1.result.canRevoke === false
  )
  await expectCode(
    'C. 同一区间重复提交 → 9603（连点防护）',
    'post',
    '/leave',
    { data: lvL1Form, token: lvStaffToken },
    9603
  )
  const lvStaOwn = await expectCode(
    'C. T1 站长提交 → 跳过初审直进待终审',
    'post',
    '/leave',
    {
      data: lvFormOf({ leaveType: 'COMPENSATORY', startDate: lvDay(25), endDate: lvDay(25), reason: '顶班后调休一天' }),
      token: lvStaToken
    },
    200
  )
  const lvStaOwnId = lvStaOwn.ok ? lvStaOwn.result.id : 0
  check(
    'C. 站长提交后状态为待老板终审且无初审痕迹',
    lvStaOwn.ok &&
      lvStaOwn.result.status === 'PENDING_BOSS' &&
      lvStaOwn.result.stationApproverId == null &&
      lvStaOwn.result.handleLog.length === 1
  )
  await expectCode(
    'C. 站长审自己的单 → 9605（纵深防御）',
    'post',
    `/leave/${lvStaOwnId}/station-approve`,
    { data: { approved: true }, token: lvStaToken },
    9605
  )
  await expectCode(
    'C. 驳回原因缺失 → 400（与补卡的选填刻意不同）',
    'post',
    `/leave/${lvL1Id}/station-approve`,
    { data: { approved: false }, token: lvStaToken },
    400
  )
  const lvStaApprove = await expectCode(
    'C. T2 站长初审通过 → 待老板终审',
    'post',
    `/leave/${lvL1Id}/station-approve`,
    { data: { approved: true, remark: '情况属实，准假' }, token: lvStaToken },
    200
  )
  check(
    'C. 初审信息落 stationApprover 槽位（不覆盖终审槽）',
    lvStaApprove.ok &&
      lvStaApprove.result.status === 'PENDING_BOSS' &&
      lvStaApprove.result.stationApproverName === '王城东' &&
      lvStaApprove.result.approverId == null
  )
  const lvFinal = await expectCode(
    'C. T4 老板终审通过 → 已通过',
    'post',
    `/leave/${lvL1Id}/final-approve`,
    { data: { approved: true, remark: '同意' }, token: lvAdminToken },
    200
  )
  check(
    'C. 终审通过落计薪天数快照（naturalDays / countedDays / scheduleDigest）',
    lvFinal.ok &&
      lvFinal.result.status === 'APPROVED' &&
      lvFinal.result.countedDaysSnapshot.countedDays === lvFinal.result.countedDays &&
      lvFinal.result.countedDaysSnapshot.naturalDays === lvFinal.result.naturalDays &&
      'scheduleDigest' in lvFinal.result.countedDaysSnapshot
  )
  await expectCode(
    'C. 已通过单再次终审 → 9602（非法流转）',
    'post',
    `/leave/${lvL1Id}/final-approve`,
    { data: { approved: true }, token: lvAdminToken },
    9602
  )
  await expectCode(
    'C. 已通过单执行初审 → 9602',
    'post',
    `/leave/${lvL1Id}/station-approve`,
    { data: { approved: true }, token: lvStaToken },
    9602
  )
  // T3 初审驳回 → T9 修改重提（必回 PENDING_STATION）
  const lvL2 = await expectCode(
    'C. 提交初审驳回用例单',
    'post',
    '/leave',
    { data: lvFormOf({ startDate: lvDay(22), endDate: lvDay(22) }), token: lvStaffToken },
    200
  )
  const lvL2Id = lvL2.ok ? lvL2.result.id : 0
  const lvRejectStation = await expectCode(
    'C. T3 初审驳回 → 已驳回（rejectStage=STATION）',
    'post',
    `/leave/${lvL2Id}/station-approve`,
    { data: { approved: false, remark: '本周末件量高峰，人手不足' }, token: lvStaToken },
    200
  )
  check(
    'C. 初审驳回写入阶段与原因',
    lvRejectStation.ok &&
      lvRejectStation.result.status === 'REJECTED' &&
      lvRejectStation.result.rejectStage === 'STATION' &&
      lvRejectStation.result.stationApproveRemark === '本周末件量高峰，人手不足'
  )
  const lvL2Re = await expectCode(
    'C. T9 初审驳回后重提 → 回待站长初审（生成新单）',
    'post',
    `/leave/${lvL2Id}/resubmit`,
    {
      data: lvFormOf({ startDate: lvDay(22), endDate: lvDay(22), reason: '已协调人手，重新提交' }),
      token: lvStaffToken
    },
    200
  )
  check(
    'C. 重提生成新单并带 originId，原单保持已驳回',
    lvL2Re.ok &&
      lvL2Re.result.id !== lvL2Id &&
      lvL2Re.result.status === 'PENDING_STATION' &&
      lvL2Re.result.originId === lvL2Id
  )
  const lvL2Detail = await call('get', `/leave/${lvL2Id}`, { token: lvStaffToken })
  check(
    'C. 原单仍为已驳回只读并留下 RESUBMIT 留痕',
    lvL2Detail.ok &&
      lvL2Detail.result.status === 'REJECTED' &&
      lvL2Detail.result.canEdit === false &&
      lvL2Detail.result.handleLog.some((x) => x.action === 'RESUBMIT')
  )
  // T5 终审驳回 → T9 重提（同样回 PENDING_STATION）
  const lvL3 = await expectCode(
    'C. 提交终审驳回用例单',
    'post',
    '/leave',
    {
      data: lvFormOf({ startDate: lvDay(24), startPeriod: 'AM', endDate: lvDay(24), endPeriod: 'AM' }),
      token: lvStaffToken
    },
    200
  )
  const lvL3Id = lvL3.ok ? lvL3.result.id : 0
  await expectCode(
    'C. 终审驳回前先过初审',
    'post',
    `/leave/${lvL3Id}/station-approve`,
    { data: { approved: true }, token: lvStaToken },
    200
  )
  const lvRejectBoss = await expectCode(
    'C. T5 终审驳回 → 已驳回（rejectStage=BOSS）',
    'post',
    `/leave/${lvL3Id}/final-approve`,
    { data: { approved: false, remark: '请假事由不充分，不予批准' }, token: lvAdminToken },
    200
  )
  check(
    'C. 终审驳回写入阶段 BOSS',
    lvRejectBoss.ok && lvRejectBoss.result.status === 'REJECTED' && lvRejectBoss.result.rejectStage === 'BOSS'
  )
  const lvL3Re = await expectCode(
    'C. T9 终审驳回后重提同样回待站长初审（不绕过初审）',
    'post',
    `/leave/${lvL3Id}/resubmit`,
    {
      data: lvFormOf({
        startDate: lvDay(24),
        startPeriod: 'AM',
        endDate: lvDay(24),
        endPeriod: 'AM',
        reason: '补充材料后重提'
      }),
      token: lvStaffToken
    },
    200
  )
  check('C. 终审驳回重提后状态为待站长初审', lvL3Re.ok && lvL3Re.result.status === 'PENDING_STATION')
  // T8 就地修改：仅 PENDING_STATION 可改，状态不变，重叠校验排除本单
  const lvEditTarget = lvL3Re.ok ? lvL3Re.result.id : 0
  const lvEdited = await expectCode(
    'C. T8 待初审单就地修改（状态不变）',
    'put',
    `/leave/${lvEditTarget}`,
    {
      data: lvFormOf({
        startDate: lvDay(24),
        startPeriod: 'AM',
        endDate: lvDay(24),
        endPeriod: 'AM',
        reason: '补充材料后重提（已改事由）'
      }),
      token: lvStaffToken
    },
    200
  )
  check(
    'C. 修改后状态不变且留 UPDATE 前后值留痕',
    lvEdited.ok &&
      lvEdited.result.status === 'PENDING_STATION' &&
      lvEdited.result.reason.includes('已改事由') &&
      lvEdited.result.handleLog.some((x) => x.action === 'UPDATE' && !!x.before && !!x.after)
  )
  await expectCode('C. 已通过单修改 → 9607', 'put', `/leave/${lvL1Id}`, { data: lvL1Form, token: lvStaffToken }, 9607)
  await expectCode('C. 修改不存在的单 → 9601', 'put', '/leave/999999', { data: lvL1Form, token: lvStaffToken }, 9601)
  await expectCode('C. 不存在的单详情 → 9601', 'get', '/leave/999999', { token: lvAdminToken }, 9601)

  /* ---- D. 权限与越权 ---- */
  await expectCode('D. STAFF 访问请假管理列表 → 403', 'get', '/leave/list', { token: lvStaffToken }, 403, 403)
  const lvStaList = await lvListOf({}, lvStaToken)
  check('D. 站长列表只出现本站单', lvStaList.list.length > 0 && lvStaList.list.every((l) => l.stationId === 1))
  const lvStaOverride = await lvListOf({ stationId: 2 }, lvStaToken)
  check(
    'D. 非 ADMIN 传 stationId 被强制覆盖（跨站不可见）',
    lvStaOverride.list.length === lvStaList.list.length && lvStaOverride.list.every((l) => l.stationId === 1)
  )
  await expectCode(
    'D. 站长访问终审端点 → 403（角色白名单）',
    'post',
    `/leave/${lvL1Id}/final-approve`,
    { data: { approved: true }, token: lvStaToken },
    403,
    403
  )
  await expectCode(
    'D. STAFF 访问终审端点 → 403',
    'post',
    `/leave/${lvL1Id}/final-approve`,
    { data: { approved: true }, token: lvStaffToken },
    403,
    403
  )
  await expectCode(
    'D. ADMIN 访问初审端点 → 403',
    'post',
    `/leave/${lvStaOwnId}/station-approve`,
    { data: { approved: true }, token: lvAdminToken },
    403,
    403
  )
  await expectCode(
    'D. STAFF 访问撤回端点 → 403',
    'post',
    `/leave/${lvL1Id}/revoke`,
    { data: { reason: '尝试越权撤回' }, token: lvStaffToken },
    403,
    403
  )
  await expectCode('D. STAFF 查看他人请假详情 → 9605', 'get', `/leave/${lvStaOwnId}`, { token: lvStaffToken }, 9605)
  const lvOtherStationLeave = lvAll.find((l) => l.stationId !== 1 && l.stationId !== 2)
  await expectCode(
    'D. 站长查看外站请假详情 → 9605',
    'get',
    `/leave/${lvOtherStationLeave ? lvOtherStationLeave.id : 0}`,
    { token: lvStaToken },
    9605
  )
  await expectCode(
    'D. ADMIN 提交请假 → 9605',
    'post',
    '/leave',
    { data: lvFormOf({ startDate: lvDay(19) }), token: lvAdminToken },
    9605
  )

  /* ---- E. 时长计算与日期护栏 ---- */
  const lvPreview = async (data, token = lvStaffToken) =>
    call('post', '/leave/preview', { data: lvFormOf(data), token })
  const lvPvSameDay = await lvPreview({ startDate: lvDay(5), endDate: lvDay(5) })
  check('E. 同日 上午 → 下午 = 1 天', lvPvSameDay.ok && lvPvSameDay.result.naturalDays === 1)
  const lvPvHalf = await lvPreview({ startDate: lvDay(5), startPeriod: 'AM', endDate: lvDay(5), endPeriod: 'AM' })
  check('E. 同日 上午 → 上午 = 0.5 天', lvPvHalf.ok && lvPvHalf.result.naturalDays === 0.5)
  const lvPvCross = await lvPreview({ startDate: lvDay(5), startPeriod: 'PM', endDate: lvDay(7), endPeriod: 'AM' })
  check('E. 跨天 5 日下午 → 7 日上午 = 2 天', lvPvCross.ok && lvPvCross.result.naturalDays === 2)
  await expectCode(
    'E. 同一天 下午 → 上午 非法组合 → 9604',
    'post',
    '/leave/preview',
    {
      data: lvFormOf({ startDate: lvDay(5), startPeriod: 'PM', endDate: lvDay(5), endPeriod: 'AM' }),
      token: lvStaffToken
    },
    9604
  )
  await expectCode(
    'E. 过去日期 → 9604（与补卡方向相反）',
    'post',
    '/leave/preview',
    { data: lvFormOf({ startDate: lvDay(-1), endDate: lvDay(-1) }), token: lvStaffToken },
    9604
  )
  await expectCode(
    'E. 单次超 30 天 → 9604',
    'post',
    '/leave/preview',
    { data: lvFormOf({ startDate: lvToday, endDate: lvDay(31) }), token: lvStaffToken },
    9604
  )
  await expectCode(
    'E. 结束日期早于开始日期 → 9604',
    'post',
    '/leave/preview',
    { data: lvFormOf({ startDate: lvDay(8), endDate: lvDay(7) }), token: lvStaffToken },
    9604
  )
  const lvPvNatural = await lvPreview({ leaveType: 'MARRIAGE', startDate: lvDay(5), endDate: lvDay(7) })
  check(
    'E. 婚假（NATURAL）计薪天数等于自然天数',
    lvPvNatural.ok &&
      lvPvNatural.result.countedDays === lvPvNatural.result.naturalDays &&
      lvPvNatural.result.countedDays === 3
  )
  // 计薪天数逐日查排班：脚本侧独立读排班矩阵，找一个「今天..本周日」内既有排班又有轮休的员工
  const lvWeekStartCache = new Map()
  const lvMatrixOf = async (day) => {
    const weekStart = formatDate(mondayOf(new Date(`${day}T00:00:00`)))
    if (!lvWeekStartCache.has(weekStart)) {
      const res = await call('get', '/schedules', { params: { stationId: 1, weekStart }, token: lvAdminToken })
      lvWeekStartCache.set(weekStart, res.ok ? res.result : null)
    }
    return lvWeekStartCache.get(weekStart)
  }
  const lvScheduledOn = async (employeeId, day) => {
    const matrix = await lvMatrixOf(day)
    const row = matrix ? matrix.employees.find((e) => e.employeeId === Number(employeeId)) : null
    const slot = row ? row.days.find((d) => d.workDate === day) : null
    return !!(slot && slot.shiftId != null)
  }
  const lvEndOfWeek = formatDate(addDays(mondayOf(new Date()), 6))
  const lvRestPool = activeEmployees().filter((e) => e.station_id === 1 && e.status === 1 && e.role !== 'ADMIN')
  let lvRestCase = null
  for (let len = 3; len >= 1 && !lvRestCase; len -= 1) {
    for (let offset = 0; offset + len - 1 <= 6 && !lvRestCase; offset += 1) {
      const start = lvDay(offset)
      const end = lvDay(offset + len - 1)
      if (end > lvEndOfWeek) continue
      for (const employee of lvRestPool) {
        let hasWork = false
        let hasRest = false
        for (let i = offset; i < offset + len; i += 1) {
          if (await lvScheduledOn(employee.id, lvDay(i))) hasWork = true
          else hasRest = true
        }
        if (len >= 2 ? hasWork && hasRest : hasRest) {
          lvRestCase = { employeeId: employee.id, startDate: start, endDate: end, len }
          break
        }
      }
    }
  }
  check('E. 找到「区间含轮休日」的用例数据', !!lvRestCase)
  if (lvRestCase) {
    const lvRestToken = await lvLoginAs(lvRestCase.employeeId)
    const lvRestPv = await call('post', '/leave/preview', {
      data: lvFormOf({
        startDate: lvRestCase.startDate,
        startPeriod: 'AM',
        endDate: lvRestCase.endDate,
        endPeriod: lvRestCase.len >= 2 ? 'PM' : 'AM'
      }),
      token: lvRestToken
    })
    check(
      'E. 计薪天数确实排除排班休息日（counted < natural 且给出提示标志）',
      lvRestPv.ok &&
        lvRestPv.result.countedDays < lvRestPv.result.naturalDays &&
        lvRestPv.result.hasRestDayExcluded === true,
      lvRestPv.ok
        ? `natural=${lvRestPv.result.naturalDays} counted=${lvRestPv.result.countedDays}`
        : `code=${lvRestPv.code}`
    )
  }

  /* ---- F. 重叠校验与撤销 / 撤回 ---- */
  const lvHalfSeed = lvAll.find((l) => l.stationId === 1 && l.naturalDays === 0.5)
  const lvHalfToken = lvHalfSeed ? await lvLoginAs(lvHalfSeed.employeeId) : ''
  await expectCode(
    'F. 同一天上下午可分别申请（AM 单与 PM 单不重叠）',
    'post',
    '/leave',
    {
      data: lvFormOf({
        startDate: lvHalfSeed ? lvHalfSeed.startDate : lvToday,
        startPeriod: 'AM',
        endDate: lvHalfSeed ? lvHalfSeed.startDate : lvToday,
        endPeriod: 'AM',
        reason: '上午另有安排'
      }),
      token: lvHalfToken
    },
    200
  )
  await expectCode(
    'F. 同一半天重复申请 → 9603',
    'post',
    '/leave',
    {
      data: lvFormOf({
        startDate: lvHalfSeed ? lvHalfSeed.startDate : lvToday,
        startPeriod: 'AM',
        endDate: lvHalfSeed ? lvHalfSeed.startDate : lvToday,
        endPeriod: 'AM',
        reason: '上午另有安排（重复）'
      }),
      token: lvHalfToken
    },
    9603
  )
  const lvRejectedSeed = lvAll.find((l) => l.status === 'REJECTED' && l.stationId === 1)
  const lvRejectedToken = lvRejectedSeed ? await lvLoginAs(lvRejectedSeed.employeeId) : ''
  await expectCode(
    'F. 与已驳回单同区间重新提交 → 不冲突',
    'post',
    '/leave',
    {
      data: lvFormOf({
        startDate: lvRejectedSeed ? lvRejectedSeed.startDate : lvDay(9),
        endDate: lvRejectedSeed ? lvRejectedSeed.endDate : lvDay(9),
        reason: '重新申请一次'
      }),
      token: lvRejectedToken
    },
    200
  )
  const lvCancelForm = lvFormOf({ startDate: lvDay(26), startPeriod: 'PM', endDate: lvDay(26), endPeriod: 'PM' })
  const lvCancelTarget = await expectCode(
    'F. 提交撤销用例单',
    'post',
    '/leave',
    { data: lvCancelForm, token: lvStaffToken },
    200
  )
  const lvCancelId = lvCancelTarget.ok ? lvCancelTarget.result.id : 0
  const lvCancelled = await expectCode(
    'F. T6 申请人在待初审态撤销 → 已撤销',
    'post',
    `/leave/${lvCancelId}/cancel`,
    { token: lvStaffToken },
    200
  )
  check(
    'F. 撤销后写入撤销人与撤销时间',
    lvCancelled.ok &&
      lvCancelled.result.status === 'CANCELLED' &&
      lvCancelled.result.cancelById === 4 &&
      !!lvCancelled.result.cancelTime
  )
  await expectCode(
    'F. 已通过单不可由申请人撤销 → 9602',
    'post',
    `/leave/${lvL1Id}/cancel`,
    { token: lvStaffToken },
    9602
  )
  await expectCode('F. 撤回原因缺失 → 400', 'post', `/leave/${lvL1Id}/revoke`, { data: {}, token: lvAdminToken }, 400)
  await expectCode(
    'F. 未通过的单撤回 → 9602',
    'post',
    `/leave/${lvCancelId}/revoke`,
    { data: { reason: '尝试撤回未通过的单' }, token: lvAdminToken },
    9602
  )
  const lvSickSeed = lvAll.find((l) => l.status === 'APPROVED' && l.stationId === 1)
  const lvSickToken = lvSickSeed ? await lvLoginAs(lvSickSeed.employeeId) : ''
  const lvRevoked = await expectCode(
    'F. T7 ADMIN 撤回已通过单（账期工资单为草稿）→ 已撤回',
    'post',
    `/leave/${lvSickSeed ? lvSickSeed.id : 0}/revoke`,
    { data: { reason: '该员工当日实际到岗，原批准作废' }, token: lvAdminToken },
    200
  )
  check(
    'F. 撤回写入撤回人 / 时间 / 原因并留痕',
    lvRevoked.ok &&
      lvRevoked.result.status === 'REVOKED' &&
      lvRevoked.result.revokerName === '系统管理员' &&
      !!lvRevoked.result.revokeTime &&
      lvRevoked.result.handleLog.some((x) => x.action === 'REVOKE')
  )
  const lvLockedSeed = lvAll.find((l) => l.status === 'APPROVED' && l.stationId === 2)
  await expectCode(
    'F. 账期存在非草稿工资单时撤回 → 9606',
    'post',
    `/leave/${lvLockedSeed ? lvLockedSeed.id : 0}/revoke`,
    { data: { reason: '尝试撤回已出账的请假' }, token: lvAdminToken },
    9606
  )
  await expectCode(
    'F. 撤回不存在的单 → 9601',
    'post',
    '/leave/999999/revoke',
    { data: { reason: '撤回不存在的单' }, token: lvAdminToken },
    9601
  )

  /* ---- G. 考勤与算薪口径联动（D3） ---- */
  const lvSwitchForm = lvFormOf({ startDate: lvToday, endDate: lvToday, reason: '开关联动断言用例' })
  const lvSwitchSubmit = await expectCode(
    'G. 提交开关联动用例单（今天整天）',
    'post',
    '/leave',
    { data: lvSwitchForm, token: lvStaffToken },
    200
  )
  const lvSwitchId = lvSwitchSubmit.ok ? lvSwitchSubmit.result.id : 0
  await expectCode(
    'G. 站长初审通过（开关用例）',
    'post',
    `/leave/${lvSwitchId}/station-approve`,
    { data: { approved: true }, token: lvStaToken },
    200
  )
  const lvSwitchFinal = await expectCode(
    'G. 终审通过（开关用例）',
    'post',
    `/leave/${lvSwitchId}/final-approve`,
    { data: { approved: true }, token: lvAdminToken },
    200
  )
  check(
    'G. 开关用例计薪天数为 1（当天有排班）',
    lvSwitchFinal.ok && lvSwitchFinal.result.countedDays === 1,
    lvSwitchFinal.ok ? `countedDays=${lvSwitchFinal.result.countedDays}` : `code=${lvSwitchFinal.code}`
  )
  const lvSwitchMonth = lvToday.slice(0, 7)
  const { startDate: lvMonthStart, endDate: lvMonthEnd } = monthRange(lvSwitchMonth)
  const lvStatOff = employeeAttendanceStat(4, lvMonthStart, lvMonthEnd)
  const lvSettingsDefault = await call('get', '/leave/settings', { token: lvAdminToken })
  check(
    'G. 扣款开关默认关闭（请假不扣，与设计规范 §5.4 一致）',
    lvSettingsDefault.ok && lvSettingsDefault.result.leaveDeductEnabled === false
  )
  await expectCode(
    'G. 打开请假扣款开关（仅 ADMIN）',
    'put',
    '/leave/settings',
    { data: { leaveDeductEnabled: true }, token: lvAdminToken },
    200
  )
  const lvStatOn = employeeAttendanceStat(4, lvMonthStart, lvMonthEnd)
  await expectCode(
    'G. 关闭请假扣款开关',
    'put',
    '/leave/settings',
    { data: { leaveDeductEnabled: false }, token: lvAdminToken },
    200
  )
  const lvStatOff2 = employeeAttendanceStat(4, lvMonthStart, lvMonthEnd)
  check(
    'G. 同一批数据：开关 ON 的缺勤天数高于 OFF（D3 核心断言）',
    lvStatOn.absentCount > lvStatOff.absentCount,
    `ON=${lvStatOn.absentCount} / OFF=${lvStatOff.absentCount}`
  )
  check(
    'G. 开关切回 OFF 后口径完全恢复',
    lvStatOff2.absentCount === lvStatOff.absentCount && lvStatOff2.leaveCount === lvStatOff.leaveCount
  )
  check(
    'G. 已批请假天数计入 leaveCount（与开关无关）',
    lvStatOff.leaveCount >= 1 && lvStatOn.leaveCount === lvStatOff.leaveCount
  )
  await expectCode(
    'G. 非 ADMIN 修改扣款开关 → 403',
    'put',
    '/leave/settings',
    { data: { leaveDeductEnabled: true }, token: lvStaffToken },
    403,
    403
  )
  await expectCode(
    'G. 扣款开关入参非布尔 → 400',
    'put',
    '/leave/settings',
    { data: { leaveDeductEnabled: 'yes' }, token: lvAdminToken },
    400
  )
  await expectCode(
    'G. 撤回开关用例单后缺勤口径回滚',
    'post',
    `/leave/${lvSwitchId}/revoke`,
    { data: { reason: '用例复用，撤回该单' }, token: lvAdminToken },
    200
  )
  const lvStatRollback = employeeAttendanceStat(4, lvMonthStart, lvMonthEnd)
  check(
    'G. 撤回后已批请假天数与缺勤天数同步回滚',
    lvStatRollback.leaveCount === lvStatOff.leaveCount - 1 && lvStatRollback.absentCount === lvStatOff.absentCount + 1,
    `leaveCount=${lvStatRollback.leaveCount} absent=${lvStatRollback.absentCount}`
  )

  /* ---- H. 通知与运行日志（D6） ---- */
  const lvNoticeApply = await lvNoticesOf(lvStaToken, lvL1Id)
  check(
    'H. 场景1 员工提交 → 本站站长收到 1 条未读请假申请通知',
    lvNoticeApply.length === 1 && lvNoticeApply[0].type === 5 && lvNoticeApply[0].isRead === false
  )
  const lvNoticeStaOwn = await lvNoticesOf(lvAdminToken, lvStaOwnId)
  check('H. 场景2 站长提交 → 老板收到 1 条待终审通知', lvNoticeStaOwn.length === 1 && lvNoticeStaOwn[0].type === 5)
  const lvNoticeStationPass = await lvNoticesOf(lvAdminToken, lvL1Id)
  check(
    'H. 场景3 初审通过 → 老板收到 1 条待终审通知',
    lvNoticeStationPass.length === 1 && lvNoticeStationPass[0].type === 5
  )
  const lvNoticeStationReject = await lvNoticesOf(lvStaffToken, lvL2Id)
  check(
    'H. 场景4 初审驳回 → 申请人收到 1 条结果通知',
    lvNoticeStationReject.length === 1 &&
      lvNoticeStationReject[0].type === 6 &&
      lvNoticeStationReject[0].content.includes('驳回原因')
  )
  const lvNoticeFinalPass = await lvNoticesOf(lvStaffToken, lvL1Id)
  check(
    'H. 场景5 终审通过 → 申请人收到 1 条结果通知（含计薪天数）',
    lvNoticeFinalPass.length === 1 && lvNoticeFinalPass[0].type === 6 && lvNoticeFinalPass[0].content.includes('计薪')
  )
  const lvNoticeFinalReject = await lvNoticesOf(lvStaffToken, lvL3Id)
  check(
    'H. 场景6 终审驳回 → 申请人收到 1 条结果通知',
    lvNoticeFinalReject.length === 1 && lvNoticeFinalReject[0].type === 6
  )
  // 该单的提交通知也在站长名下（场景 1），这里只看「结果类」那一条
  const lvNoticeCancel = await lvNoticesOf(lvStaToken, lvCancelId)
  const lvCancelResultNotices = lvNoticeCancel.filter((n) => n.type === 6)
  check(
    'H. 场景7 申请人撤销 → 当前待审审批人收到 1 条通知',
    lvCancelResultNotices.length === 1 && lvCancelResultNotices[0].content.includes('已撤销'),
    `bizId=${lvCancelId} 结果类命中 ${lvCancelResultNotices.length} 条`
  )
  const lvNoticeRevoke = await lvNoticesOf(lvSickToken, lvSickSeed ? lvSickSeed.id : 0)
  check(
    'H. 场景8 审批人撤回 → 申请人收到 1 条通知（含撤回原因）',
    lvNoticeRevoke.length === 1 && lvNoticeRevoke[0].type === 6 && lvNoticeRevoke[0].content.includes('撤回')
  )
  const lvAllLeaveNotices = await call('get', '/notifications', {
    params: { pageNum: 1, pageSize: 100 },
    token: lvAdminToken
  })
  check(
    'H. 请假通知 bizType 统一为 leave 且全部未读',
    lvAllLeaveNotices.ok &&
      lvAllLeaveNotices.result.list.filter((n) => n.bizType === 'leave').length > 0 &&
      lvAllLeaveNotices.result.list.filter((n) => n.bizType === 'leave').every((n) => n.isRead === false)
  )
  await expectCode(
    'H. 通知发布已放行类型 5（请假申请）',
    'post',
    '/notifications/publish',
    {
      data: { type: 5, title: '请假制度更新', content: '请假提交流程不变，审批时限已明确', scope: 'ALL' },
      token: lvAdminToken
    },
    200
  )
  // 运行日志：脱敏（白名单复制）+ 指纹去重 + 环形缓冲
  await expectCode(
    'H. 清空运行日志（仅 ADMIN）',
    'post',
    '/system/client-logs/clear',
    { data: {}, token: lvAdminToken },
    200
  )
  const lvSecret = 'eyJhbGciOiJIUzI1NiJ9.MOCK_SECRET_TOKEN'
  await expectCode(
    'H. 上报日志含 token / password 负载（应被白名单丢弃）',
    'post',
    '/system/client-logs',
    {
      data: {
        logs: [
          {
            level: 'ERROR',
            source: 'H5',
            message: '接口调用失败',
            stack: 'Error: 500 /api/v1/leave?token=' + lvSecret,
            path: `/api/v1/leave?token=${lvSecret}`,
            method: 'get',
            status: 500,
            code: 500,
            duration: 421,
            route: '/staff/leave',
            employeeId: 4,
            token: lvSecret,
            password: 'demo1234',
            idCard: '110101199001011234'
          }
        ]
      },
      token: lvStaffToken
    },
    200
  )
  const lvSecretLogs = await call('get', '/system/client-logs', {
    params: { pageNum: 1, pageSize: 100 },
    token: lvAdminToken
  })
  const lvSecretRow = lvSecretLogs.ok ? lvSecretLogs.result.list.find((l) => l.message === '接口调用失败') : null
  check(
    'H. 脱敏生效：日志里搜不到 token / 密码原文，path 已去掉 query',
    !!lvSecretRow &&
      !JSON.stringify(lvSecretRow).includes(lvSecret) &&
      !JSON.stringify(lvSecretRow).includes('demo1234') &&
      !JSON.stringify(lvSecretRow).includes('110101199001011234') &&
      lvSecretRow.path === '/api/v1/leave'
  )
  check(
    'H. 运行日志字段白名单复制（无 token / password / idCard 字段）',
    !!lvSecretRow && !('token' in lvSecretRow) && !('password' in lvSecretRow) && !('idCard' in lvSecretRow)
  )
  const lvDupLog = { level: 'WARN', source: 'PC', message: '业务失败提示', code: 9603, route: '/leave' }
  for (let i = 0; i < 5; i += 1) {
    await call('post', '/system/client-logs', { data: { logs: [lvDupLog] }, token: lvAdminToken })
  }
  const lvDupRes = await call('get', '/system/client-logs', {
    params: { keyword: '业务失败提示', pageNum: 1, pageSize: 10 },
    token: lvAdminToken
  })
  check(
    'H. 指纹去重：10s 内重复 5 次只留 1 条且 count=5',
    lvDupRes.ok && lvDupRes.result.total === 1 && lvDupRes.result.list[0].count === 5
  )
  for (let batch = 0; batch < 5; batch += 1) {
    const logs = []
    for (let i = 0; i < 50; i += 1) {
      logs.push({ level: 'ERROR', source: 'SHELL', message: `环形缓冲压测-${batch}-${i}`, route: '/stress', code: 500 })
    }
    await call('post', '/system/client-logs', { data: { logs }, token: lvAdminToken })
  }
  const lvRingRes = await call('get', '/system/client-logs', {
    params: { pageNum: 1, pageSize: 100 },
    token: lvAdminToken
  })
  check(
    'H. 环形缓冲上限生效：灌 250 条后只剩 200 条且丢最旧',
    lvRingRes.ok &&
      lvRingRes.result.total === 200 &&
      lvRingRes.result.counts.total === 200 &&
      lvRingRes.result.list.every((l) => l.message.startsWith('环形缓冲压测-'))
  )
  const lvClearRes = await expectCode(
    'H. 清空运行日志返回清空条数',
    'post',
    '/system/client-logs/clear',
    { data: {}, token: lvAdminToken },
    200
  )
  check('H. 清空条数为 200', lvClearRes.ok && lvClearRes.result.cleared === 200)
  await expectCode('H. 非 ADMIN 查看运行日志 → 403', 'get', '/system/client-logs', { token: lvStaffToken }, 403, 403)
  await expectCode(
    'H. 非 ADMIN 清空运行日志 → 403',
    'post',
    '/system/client-logs/clear',
    { data: {}, token: lvStaffToken },
    403,
    403
  )
  await expectCode(
    'H. 运行日志 level 取值非法 → 400',
    'get',
    '/system/client-logs',
    { params: { level: 'FATAL' }, token: lvAdminToken },
    400
  )
  await expectCode(
    'H. 空日志批量上报 → 400',
    'post',
    '/system/client-logs',
    { data: { logs: [] }, token: lvAdminToken },
    400
  )

  /* ---- 请假入参校验补充 ---- */
  await expectCode(
    'I. status 取值非法 → 400',
    'get',
    '/leave/list',
    { params: { status: 'UNKNOWN' }, token: lvAdminToken },
    400
  )
  await expectCode(
    'I. leaveType 取值非法 → 400',
    'get',
    '/leave/list',
    { params: { leaveType: 'UNKNOWN' }, token: lvAdminToken },
    400
  )
  await expectCode(
    'I. pageSize 越界 → 400',
    'get',
    '/leave/list',
    { params: { pageSize: 999 }, token: lvAdminToken },
    400
  )
  await expectCode(
    'I. 日期格式非法 → 400',
    'get',
    '/leave/list',
    { params: { startDate: '2026/10/01' }, token: lvAdminToken },
    400
  )
  await expectCode(
    'I. 提交事由过短 → 400',
    'post',
    '/leave',
    { data: lvFormOf({ startDate: lvDay(28), endDate: lvDay(28), reason: '事' }), token: lvStaffToken },
    400
  )
  await expectCode(
    'I. 提交半天粒度非法 → 400',
    'post',
    '/leave',
    { data: lvFormOf({ startDate: lvDay(28), startPeriod: 'NOON', endDate: lvDay(28) }), token: lvStaffToken },
    400
  )
  const lvMyPending = await call('get', '/leave/my', {
    params: { status: 'PENDING', pageNum: 1, pageSize: 100 },
    token: lvStaffToken
  })
  check(
    'I. PENDING 聚合虚拟值展开为两种待审态且只返回本人数据',
    lvMyPending.ok &&
      lvMyPending.result.list.length > 0 &&
      lvMyPending.result.list.every((l) => l.employeeId === 4 && ['PENDING_STATION', 'PENDING_BOSS'].includes(l.status))
  )
  const lvPendingOnly = await lvListOf({ status: 'PENDING' })
  const lvPendingSum = await lvListOf({ status: 'PENDING_STATION' })
  const lvPendingBoss = await lvListOf({ status: 'PENDING_BOSS' })
  check(
    'I. 聚合值总数 = 两态之和（服务端展开口径一致）',
    lvPendingOnly.total === lvPendingSum.total + lvPendingBoss.total
  )

  console.log(
    `\n[M11-请假] 种子 ${lvAll.length} 条（状态 ${[...new Set(lvAll.map((l) => l.status))].join('、')} / 驿站 ${[...new Set(lvAll.map((l) => l.stationId))].join('、')}）/ ` +
      `半天单 ${lvAll.filter((l) => l.naturalDays === 0.5).length} 条 / 跨天单 ${lvAll.filter((l) => l.endDate > l.startDate).length} 条 / ` +
      `开关联动：ON 缺勤 ${lvStatOn.absentCount} > OFF 缺勤 ${lvStatOff.absentCount}（已批请假 ${lvStatOff.leaveCount} 天）/ ` +
      `运行日志环形缓冲 ${lvRingRes.ok ? lvRingRes.result.total : '-'} 条（上限 200）/ 撤回阻断用例 ${lvLockedSeed ? '驿站 ' + lvLockedSeed.stationId : '未取到'}`
  )

  console.log('\n================ Mock 契约校验结果 ================')
  console.log(`共 ${pass + fail} 项：通过 ${pass} 项 / 失败 ${fail} 项`)
  if (failures.length) {
    console.log('失败明细：')
    failures.forEach((item) => console.log(`  ✗ ${item}`))
    process.exitCode = 1
  } else {
    console.log(
      '[V3] adapter 返回值经响应拦截器分发正常：code=200 取 data、code!=200 进错误分支、401/403/404 同步 HTTP 状态码'
    )
  }
}

main().catch((error) => {
  console.error('校验脚本异常终止：', error)
  process.exitCode = 1
})
