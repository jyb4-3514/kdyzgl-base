import {
  activeEmployees,
  db,
  employeeName,
  findDepartmentById,
  findEmployeeById,
  findStationById,
  stationName
} from './db.js'
import { CODE, HR_CODE } from '../constants/errorCode.js'
import { createPersistBucket } from './persist.js'
import {
  addMonths,
  createRandom,
  formatDate,
  formatDateTime,
  maskBankAccount,
  maskPhone,
  paginate,
  parseDate,
  randomInt,
  shiftDays
} from './util.js'
import { isStrongPassword, isUsername } from './validate.js'

/**
 * 人事数据层（需求8 人事档案与定薪 + 需求10 入职/离职流程）
 *
 * 四张表：
 * - hr_profile      人事档案（合同 / 试用期 / 社保基数 / 学历 / 紧急联系人 / 银行卡）
 * - hr_salary       当前定薪（基本工资 / 岗位工资 / 绩效基数 / 津贴项）
 * - hr_salary_log   调薪留痕（含入职定薪，每次变更留一条，当前定薪 = 最后一条）
 * - hr_flow         入职与离职流程（同一张表，用 flowType 区分；steps 数组供前端直接渲染步骤条）
 *
 * 离职判定：凭 hr_profile.leave_date（离岗步骤写入）。为什么不用 employee.status：
 * status=0 同时表示「禁用账号」与「已离职」，拿它判定会让禁用账号（如 56 号）连人事档案都改不了。
 *
 * 依赖方向单向（store → db）。离职结算单由财务域创建，本模块不反向依赖 financeStore，
 * 由同时持有两侧的路由层在 SETTLEMENT 步骤完成时编排（见 routes/hr.js）。
 */

export const EDUCATION_LABEL = { MASTER: '硕士', BACHELOR: '本科', COLLEGE: '大专', HIGH_SCHOOL: '高中及以下' }
export const CONTRACT_TYPE_LABEL = {
  FIXED_TERM: '固定期限',
  NON_FIXED_TERM: '无固定期限',
  INTERN: '实习协议',
  DISPATCH: '劳务派遣'
}
export const FLOW_STATUS_LABEL = { IN_PROGRESS: '进行中', COMPLETED: '已完成', REJECTED: '已驳回' }
export const STEP_STATUS_LABEL = { PENDING: '待办理', DONE: '已完成' }
export const OFFBOARDING_TYPE_LABEL = { RESIGN: '辞职', DISMISS: '辞退', RETIRE: '退休' }

/** 入职步骤定义：顺序即流程顺序，前端按数组直接渲染步骤条 */
export const ONBOARDING_STEPS = [
  { key: 'SUBMIT_MATERIALS', name: '提交资料' },
  { key: 'HR_REVIEW', name: '人事审核' },
  { key: 'CREATE_ACCOUNT', name: '建档并生成员工与账号' },
  { key: 'ASSIGN_STATION', name: '分配驿站/岗位' },
  { key: 'SET_SALARY', name: '定薪' },
  { key: 'DONE', name: '完成' }
]

/** 离职步骤定义：SETTLEMENT 与财务工资单建立引用，LEAVE 需结算完成才可离岗（9306） */
export const OFFBOARDING_STEPS = [
  { key: 'MANAGER_APPROVE', name: '主管审批' },
  { key: 'HR_APPROVE', name: '人事审批' },
  { key: 'HANDOVER', name: '工作交接' },
  { key: 'ASSET_RETURN', name: '资产归还' },
  { key: 'SETTLEMENT', name: '薪资结算' },
  { key: 'LEAVE', name: '离岗' }
]

const bucket = createPersistBucket('hr')
let state = null

/* ==================== 种子 ==================== */

const ALLOWANCE_SEED = [
  { key: 'MEAL', name: '餐补', amount: 300 },
  { key: 'TRANSPORT', name: '交通补贴', amount: 200 },
  { key: 'HOUSING', name: '住房补贴', amount: 500 }
]
const BANKS = ['中国邮政储蓄银行', '中国工商银行', '中国建设银行', '中国农业银行']
const ADJUST_REASONS = ['年度调薪', '转正调薪', '岗位晋升调薪']
const EMERGENCY_NAMES = ['王丽', '李强', '张敏', '刘芳', '陈静', '杨勇']

/**
 * 入职流程种子：全部停在「建档」之前，一个员工都不落地。
 * 为什么：多种子模块共用 activeEmployees() 作为数据池，种子阶段凭空多出员工会让
 * KPI 评分 / 工资单的覆盖人数随「先打开哪个页面」而变，破坏「刷新数据不变」的验收口径。
 * 建档之后（生成员工/定薪/完成）的链路由演示与校验脚本运行时跑通。
 */
const ONBOARDING_SEED = [
  {
    candidateName: '顾晓晨',
    gender: 1,
    education: 'BACHELOR',
    position: '快递员',
    doneSteps: 0,
    status: 'IN_PROGRESS',
    daysAgo: 2
  },
  {
    candidateName: '莫文轩',
    gender: 1,
    education: 'COLLEGE',
    position: '分拣员',
    doneSteps: 1,
    status: 'IN_PROGRESS',
    daysAgo: 4
  },
  {
    candidateName: '谭静怡',
    gender: 2,
    education: 'BACHELOR',
    position: '客服专员',
    doneSteps: 2,
    status: 'IN_PROGRESS',
    daysAgo: 6
  },
  {
    candidateName: '韦思远',
    gender: 1,
    education: 'HIGH_SCHOOL',
    position: '快递员',
    doneSteps: 1,
    status: 'REJECTED',
    daysAgo: 9
  }
]

/** 离职流程种子：同样不落在「离岗」之前，避免种子阶段改写员工在职状态影响其他模块 */
const OFFBOARDING_SEED = [
  { employeeId: 21, type: 'RESIGN', doneSteps: 0, status: 'IN_PROGRESS', reason: '个人原因回原籍发展' },
  { employeeId: 22, type: 'DISMISS', doneSteps: 1, status: 'IN_PROGRESS', reason: '多次违反考勤与操作规程' },
  { employeeId: 23, type: 'RETIRE', doneSteps: 3, status: 'IN_PROGRESS', reason: '达到法定退休年龄' },
  { employeeId: 24, type: 'RESIGN', doneSteps: 4, status: 'IN_PROGRESS', reason: '已考取事业单位，办理离职' },
  { employeeId: 25, type: 'RESIGN', doneSteps: 2, status: 'REJECTED', reason: '家庭原因申请离职' }
]

function buildStepList(defs, doneSteps, operator, times) {
  return defs.map((def, index) => ({
    key: def.key,
    name: def.name,
    order: index + 1,
    status: index < doneSteps ? 'DONE' : 'PENDING',
    operatorId: index < doneSteps ? operator.id : null,
    operatorName: index < doneSteps ? operator.real_name : null,
    operateTime: index < doneSteps ? times[index] : null,
    remark: index < doneSteps ? '演示种子数据：该步骤已办理' : null
  }))
}

function buildOnboardings(operator) {
  return ONBOARDING_SEED.map((item, index) => {
    const id = index + 1
    const createdAt = shiftDays(-item.daysAgo, 9, 10 + index, 0)
    const times = ONBOARDING_STEPS.map((_, stepIndex) =>
      formatDateTime(new Date(createdAt.getTime() + stepIndex * 3600000 * 6))
    )
    const steps = buildStepList(ONBOARDING_STEPS, item.doneSteps, operator, times)
    const flow = {
      id,
      flowType: 'ONBOARDING',
      flowNo: `ON-${formatDate(createdAt).replace(/-/g, '')}-${String(id).padStart(4, '0')}`,
      candidateName: item.candidateName,
      employeeId: null,
      phone: `139${String(20000000 + id * 2311).slice(0, 8)}`,
      gender: item.gender,
      education: item.education,
      deptId: 2,
      stationId: (id % 7) + 1,
      position: item.position,
      role: 'STAFF',
      expectedEntryDate: formatDate(shiftDays(3 + index * 2, 0, 0, 0)),
      remark: '演示种子数据：入职流程',
      status: item.status,
      rejectReason: item.status === 'REJECTED' ? '证件材料不齐，暂缓入职' : null,
      rejectedBy: item.status === 'REJECTED' ? operator.real_name : null,
      rejectedTime: item.status === 'REJECTED' ? formatDateTime(new Date(createdAt.getTime() + 12 * 3600000)) : null,
      steps,
      currentStepKey: null,
      createTime: formatDateTime(createdAt),
      updateTime: formatDateTime(new Date(createdAt.getTime() + 12 * 3600000)),
      operatorId: operator.id,
      operatorName: operator.real_name
    }
    flow.currentStepKey = (steps.find((s) => s.status === 'PENDING') || {}).key || null
    return flow
  })
}

function buildOffboardings(operator) {
  return OFFBOARDING_SEED.map((item, index) => {
    const id = index + 1
    const employee = findEmployeeById(item.employeeId)
    const createdAt = shiftDays(-(3 + index * 2), 14, 20 + index, 0)
    const times = OFFBOARDING_STEPS.map((_, stepIndex) =>
      formatDateTime(new Date(createdAt.getTime() + stepIndex * 3600000 * 20))
    )
    const steps = buildStepList(OFFBOARDING_STEPS, item.doneSteps, operator, times)
    const flow = {
      id,
      flowType: 'OFFBOARDING',
      flowNo: `OFF-${formatDate(createdAt).replace(/-/g, '')}-${String(id).padStart(4, '0')}`,
      candidateName: null,
      employeeId: item.employeeId,
      employeeName: employee ? employee.real_name : employeeName(item.employeeId),
      stationId: employee ? employee.station_id : null,
      type: item.type,
      reason: item.reason,
      lastWorkDate: formatDate(shiftDays(10 + index * 3, 0, 0, 0)),
      settlementPayrollId: null,
      settlementPayrollNo: null,
      settlementAmount: null,
      leaveDate: null,
      remark: '演示种子数据：离职流程',
      status: item.status,
      rejectReason: item.status === 'REJECTED' ? '交接事项未列全，退回补充' : null,
      rejectedBy: item.status === 'REJECTED' ? operator.real_name : null,
      rejectedTime: item.status === 'REJECTED' ? formatDateTime(new Date(createdAt.getTime() + 24 * 3600000)) : null,
      steps,
      currentStepKey: null,
      createTime: formatDateTime(createdAt),
      updateTime: formatDateTime(new Date(createdAt.getTime() + 24 * 3600000)),
      operatorId: operator.id,
      operatorName: operator.real_name
    }
    flow.currentStepKey = (steps.find((s) => s.status === 'PENDING') || {}).key || null
    return flow
  })
}

function buildSeed() {
  const random = createRandom(0x7c3a5e91) // 独立种子：不与 db / attendance / kpi 的随机序列互相干扰
  const operator = db.employees.find((e) => e.role === 'ADMIN') || db.employees[0]
  const profiles = []
  const salaries = []
  const salaryLogs = []
  let logId = 0

  activeEmployees().forEach((employee, index) => {
    const entryDate = employee.entry_date || formatDate(shiftDays(-400, 0, 0, 0))
    const probationMonths = index % 5 === 0 ? 0 : index % 3 === 0 ? 6 : 3
    const probationEnd = addMonths(parseDate(entryDate), probationMonths)
    const contractEnd = addMonths(parseDate(entryDate), index % 4 === 0 ? 1 : 3)
    profiles.push({
      employeeId: employee.id,
      education: ['BACHELOR', 'COLLEGE', 'MASTER', 'HIGH_SCHOOL'][index % 4],
      contractType: index % 7 === 0 ? 'NON_FIXED_TERM' : 'FIXED_TERM',
      contractStart: entryDate,
      contractEnd: formatDate(contractEnd),
      probationMonths,
      probationEnd: formatDate(probationEnd),
      regularDate: formatDate(probationEnd),
      socialSecurityBase: (40 + randomInt(random, 0, 40)) * 100,
      emergencyContactName: EMERGENCY_NAMES[index % EMERGENCY_NAMES.length],
      emergencyContactPhone: `135${String(30000000 + employee.id * 7919).slice(0, 8)}`,
      emergencyContactRelation: ['配偶', '父母', '兄弟姐妹'][index % 3],
      bankName: BANKS[index % BANKS.length],
      bankAccount: `6217${String(100000000000 + employee.id * 9876543).slice(0, 12)}`,
      leaveDate: null,
      createTime: formatDateTime(parseDate(entryDate)),
      updateTime: formatDateTime(parseDate(entryDate))
    })

    const salary = salarySeedOf(employee, random)
    const totalSalary = salary.basicSalary + salary.postSalary + salary.performanceBase + salary.allowancesTotal
    salaries.push({
      ...salary,
      employeeId: employee.id,
      totalSalary,
      effectiveDate: entryDate,
      updateTime: formatDateTime(parseDate(entryDate))
    })
    logId += 1
    salaryLogs.push({
      id: logId,
      employeeId: employee.id,
      changeType: 'ENTRY',
      ...salary,
      totalSalary,
      effectiveDate: entryDate,
      reason: '入职定薪',
      operatorId: operator ? operator.id : null,
      operatorName: operator ? operator.real_name : null,
      createTime: formatDateTime(parseDate(entryDate))
    })

    // 约三分之一员工有调薪留痕：演示「同一员工多条定薪记录 + 生效日期」的留痕口径
    if (index % 3 === 1) {
      const effective = formatDate(addMonths(parseDate(entryDate), 12))
      const raised = {
        basicSalary: salary.basicSalary + 300,
        postSalary: salary.postSalary + 200,
        performanceBase: salary.performanceBase + 200,
        allowances: salary.allowances.map((a) => ({ ...a })),
        allowancesTotal: salary.allowancesTotal
      }
      const raisedTotal = raised.basicSalary + raised.postSalary + raised.performanceBase + raised.allowancesTotal
      Object.assign(salaries[salaries.length - 1], raised, {
        totalSalary: raisedTotal,
        effectiveDate: effective,
        updateTime: formatDateTime(parseDate(effective))
      })
      logId += 1
      salaryLogs.push({
        id: logId,
        employeeId: employee.id,
        changeType: 'ADJUST',
        ...raised,
        totalSalary: raisedTotal,
        effectiveDate: effective,
        reason: ADJUST_REASONS[index % ADJUST_REASONS.length],
        operatorId: operator ? operator.id : null,
        operatorName: operator ? operator.real_name : null,
        createTime: formatDateTime(addMonths(parseDate(entryDate), 11))
      })
    }
  })

  return {
    seq: {
      profile: profiles.length,
      salary: salaries.length,
      log: logId,
      flow: ONBOARDING_SEED.length + OFFBOARDING_SEED.length
    },
    profiles,
    salaries,
    salaryLogs,
    onboardings: buildOnboardings(operator),
    offboardings: buildOffboardings(operator)
  }
}

/** 定薪种子：按角色分档，数值取整百，便于工资单演示时口算核对 */
function salarySeedOf(employee, random) {
  const isAdmin = employee.role === 'ADMIN'
  const allowanceCount = randomInt(random, 1, ALLOWANCE_SEED.length)
  const allowances = ALLOWANCE_SEED.slice(0, allowanceCount).map((a) => ({ ...a }))
  return {
    basicSalary: (isAdmin ? 60 : 35 + randomInt(random, 0, 15)) * 100,
    postSalary: (isAdmin ? 25 : 12 + randomInt(random, 0, 10)) * 100,
    performanceBase: (isAdmin ? 20 : 15 + randomInt(random, 0, 10)) * 100,
    allowances,
    allowancesTotal: allowances.reduce((sum, a) => sum + a.amount, 0)
  }
}

function ensure() {
  if (state) return state
  const snapshot = bucket.read()
  state = snapshot || buildSeed()
  return state
}

export function resetHrStore() {
  state = null
  bucket.clear()
}

/* ==================== 人事档案 ==================== */

/** 已离职判定：只有走完离职流程（离岗步骤写入 leaveDate）才算，禁用账号不算 */
export const isResigned = (employeeId) => {
  ensure()
  const profile = state.profiles.find((p) => p.employeeId === Number(employeeId))
  return !!(profile && profile.leaveDate)
}

function toProfileVO(profile) {
  const employee = db.employees.find((e) => e.id === profile.employeeId)
  const dept = employee ? findDepartmentById(employee.dept_id) : null
  return {
    employeeId: profile.employeeId,
    employeeName: employee ? employee.real_name : employeeName(profile.employeeId),
    username: employee ? employee.username : null,
    phone: employee ? maskPhone(employee.phone) : null,
    deptName: dept ? dept.dept_name : null,
    stationName: employee ? stationName(employee.station_id) : null,
    entryDate: employee ? employee.entry_date : null,
    education: profile.education,
    educationLabel: EDUCATION_LABEL[profile.education] || null,
    contractType: profile.contractType,
    contractTypeLabel: CONTRACT_TYPE_LABEL[profile.contractType] || null,
    contractStart: profile.contractStart,
    contractEnd: profile.contractEnd,
    probationMonths: profile.probationMonths,
    probationEnd: profile.probationEnd,
    regularDate: profile.regularDate,
    socialSecurityBase: profile.socialSecurityBase,
    emergencyContactName: profile.emergencyContactName,
    // 紧急联系人与银行卡同属个人敏感信息，出参一律脱敏（需求8 明确要求银行卡脱敏）
    emergencyContactPhone: maskPhone(profile.emergencyContactPhone),
    emergencyContactRelation: profile.emergencyContactRelation,
    bankName: profile.bankName,
    bankAccount: maskBankAccount(profile.bankAccount),
    leaveDate: profile.leaveDate,
    createTime: profile.createTime,
    updateTime: profile.updateTime
  }
}

function employeeScope({ deptId, stationId, keyword }) {
  let rows = activeEmployees()
  if (stationId != null && stationId !== '') rows = rows.filter((e) => e.station_id === Number(stationId))
  if (deptId != null && deptId !== '') rows = rows.filter((e) => e.dept_id === Number(deptId))
  const text = String(keyword || '').trim()
  if (text) rows = rows.filter((e) => e.real_name.includes(text) || e.username.includes(text))
  return rows
}

export function listProfiles(filters) {
  ensure()
  const employees = employeeScope(filters)
  const byId = new Map(state.profiles.map((p) => [p.employeeId, p]))
  const rows = employees
    .map((employee) => byId.get(employee.id))
    .filter(Boolean)
    .map(toProfileVO)
    .sort((a, b) => a.employeeId - b.employeeId)
  const page = paginate(rows, filters.pageNum, filters.pageSize)
  return page
}

/** 档案详情（含定薪摘要，人事档案页一屏看全）；档案不存在返回 9301 */
export function findProfile(employeeId) {
  ensure()
  const profile = state.profiles.find((p) => p.employeeId === Number(employeeId))
  if (!profile) return { code: HR_CODE.PROFILE_NOT_EXISTS }
  const salary = state.salaries.find((s) => s.employeeId === Number(employeeId)) || null
  return { code: 200, data: { ...toProfileVO(profile), salary: salary ? toSalaryVO(salary) : null } }
}

export function saveProfile(employeeId, payload) {
  ensure()
  const profile = state.profiles.find((p) => p.employeeId === Number(employeeId))
  if (!profile) return { code: HR_CODE.PROFILE_NOT_EXISTS }
  if (profile.leaveDate) return { code: HR_CODE.EMPLOYEE_RESIGNED }
  // 白名单写入：employeeId / leaveDate 不接受请求体，防止越权改归属与伪造离职
  const WRITABLE = [
    'education',
    'contractType',
    'contractStart',
    'contractEnd',
    'probationMonths',
    'probationEnd',
    'regularDate',
    'socialSecurityBase',
    'emergencyContactName',
    'emergencyContactPhone',
    'emergencyContactRelation',
    'bankName',
    'bankAccount'
  ]
  WRITABLE.forEach((key) => {
    if (payload[key] !== undefined) profile[key] = payload[key]
  })
  profile.updateTime = formatDateTime(new Date())
  bucket.write(state)
  return { code: 200, data: toProfileVO(profile) }
}

/* ==================== 定薪档案 ==================== */

function toSalaryVO(salary) {
  return {
    employeeId: salary.employeeId,
    employeeName: employeeName(salary.employeeId),
    basicSalary: salary.basicSalary,
    postSalary: salary.postSalary,
    performanceBase: salary.performanceBase,
    allowances: (salary.allowances || []).map((a) => ({ ...a })),
    allowancesTotal: salary.allowancesTotal,
    totalSalary: salary.totalSalary,
    effectiveDate: salary.effectiveDate,
    updateTime: salary.updateTime
  }
}

function toSalaryLogVO(log) {
  return {
    id: log.id,
    effectiveDate: log.effectiveDate,
    changeType: log.changeType,
    changeTypeLabel: log.changeType === 'ENTRY' ? '入职定薪' : '调薪',
    basicSalary: log.basicSalary,
    postSalary: log.postSalary,
    performanceBase: log.performanceBase,
    allowances: (log.allowances || []).map((a) => ({ ...a })),
    allowancesTotal: log.allowancesTotal,
    totalSalary: log.totalSalary,
    reason: log.reason,
    operatorId: log.operatorId,
    operatorName: log.operatorName,
    createTime: log.createTime
  }
}

export function listSalaries(filters) {
  ensure()
  const employees = employeeScope(filters)
  const byId = new Map(state.salaries.map((s) => [s.employeeId, s]))
  const rows = employees
    .map((employee) => byId.get(employee.id))
    .filter(Boolean)
    .map(toSalaryVO)
    .sort((a, b) => a.employeeId - b.employeeId)
  return paginate(rows, filters.pageNum, filters.pageSize)
}

export function findSalary(employeeId) {
  ensure()
  const salary = state.salaries.find((s) => s.employeeId === Number(employeeId))
  if (!salary) return { code: HR_CODE.SALARY_NOT_EXISTS }
  const histories = state.salaryLogs
    .filter((log) => log.employeeId === Number(employeeId))
    .sort((a, b) => (a.effectiveDate < b.effectiveDate ? 1 : -1))
    .map(toSalaryLogVO)
  return { code: 200, data: { current: toSalaryVO(salary), histories } }
}

/** 当前定薪（财务域取 FIXED 规则项的唯一数据来源），无档案返回 null */
export function currentSalary(employeeId) {
  ensure()
  const salary = state.salaries.find((s) => s.employeeId === Number(employeeId))
  return salary ? { ...salary, allowances: (salary.allowances || []).map((a) => ({ ...a })) } : null
}

/** 保存定薪：覆盖当前档案 + 追加一条调薪留痕（调薪留痕只增不改，保证历史可追溯） */
export function saveSalary(employeeId, payload, operator, changeType = 'ADJUST') {
  ensure()
  const salary = state.salaries.find((s) => s.employeeId === Number(employeeId))
  if (!salary) return { code: HR_CODE.SALARY_NOT_EXISTS }
  if (isResigned(employeeId)) return { code: HR_CODE.EMPLOYEE_RESIGNED }

  const next = {
    basicSalary: payload.basicSalary === undefined ? salary.basicSalary : Number(payload.basicSalary),
    postSalary: payload.postSalary === undefined ? salary.postSalary : Number(payload.postSalary),
    performanceBase: payload.performanceBase === undefined ? salary.performanceBase : Number(payload.performanceBase),
    allowances:
      payload.allowances === undefined
        ? salary.allowances.map((a) => ({ ...a }))
        : payload.allowances.map((a) => ({
            name: String(a.name).trim(),
            key: a.key ? String(a.key).trim() : null,
            amount: Number(a.amount)
          }))
  }
  next.allowancesTotal = next.allowances.reduce((sum, a) => sum + Number(a.amount || 0), 0)
  const totalSalary = next.basicSalary + next.postSalary + next.performanceBase + next.allowancesTotal
  const effectiveDate = payload.effectiveDate || formatDate(new Date())

  Object.assign(salary, next, { totalSalary, effectiveDate, updateTime: formatDateTime(new Date()) })
  state.salaryLogs.push({
    id: (state.seq.log += 1),
    employeeId: Number(employeeId),
    changeType,
    ...next,
    allowances: next.allowances.map((a) => ({ ...a })),
    totalSalary,
    effectiveDate,
    reason: payload.reason || (changeType === 'ENTRY' ? '入职定薪' : '定薪调整'),
    operatorId: operator ? operator.id : null,
    operatorName: operator ? operator.real_name : null,
    createTime: formatDateTime(new Date())
  })
  bucket.write(state)
  return findSalary(employeeId)
}

/* ==================== 流程公共逻辑 ==================== */

function toFlowVO(flow) {
  const done = flow.steps.filter((s) => s.status === 'DONE').length
  const current = flow.steps.find((s) => s.status === 'PENDING') || null
  return {
    id: flow.id,
    flowType: flow.flowType,
    flowNo: flow.flowNo,
    candidateName: flow.candidateName,
    employeeId: flow.employeeId,
    employeeName: flow.flowType === 'OFFBOARDING' ? employeeName(flow.employeeId) : flow.candidateName,
    phone: flow.phone ? maskPhone(flow.phone) : null,
    gender: flow.gender === undefined ? null : flow.gender,
    education: flow.education || null,
    educationLabel: EDUCATION_LABEL[flow.education] || null,
    deptId: flow.deptId,
    stationId: flow.stationId,
    stationName: stationName(flow.stationId),
    position: flow.position || null,
    role: flow.role || null,
    expectedEntryDate: flow.expectedEntryDate || null,
    type: flow.type || null,
    typeLabel: flow.type ? OFFBOARDING_TYPE_LABEL[flow.type] : null,
    reason: flow.reason || null,
    lastWorkDate: flow.lastWorkDate || null,
    settlementPayrollId: flow.settlementPayrollId,
    settlementPayrollNo: flow.settlementPayrollNo,
    settlementAmount: flow.settlementAmount,
    leaveDate: flow.leaveDate,
    remark: flow.remark,
    status: flow.status,
    statusLabel: FLOW_STATUS_LABEL[flow.status],
    rejectReason: flow.rejectReason,
    rejectedBy: flow.rejectedBy,
    rejectedTime: flow.rejectedTime,
    currentStepKey: current ? current.key : null,
    currentStepName: current ? current.name : null,
    progress: { done, total: flow.steps.length },
    steps: flow.steps.map((step) => ({ ...step, statusLabel: STEP_STATUS_LABEL[step.status] })),
    createTime: flow.createTime,
    updateTime: flow.updateTime,
    operatorId: flow.operatorId,
    operatorName: flow.operatorName
  }
}

/** 步骤完成后推进游标：全流程办完即置 COMPLETED */
function afterStepDone(flow) {
  const next = flow.steps.find((s) => s.status === 'PENDING') || null
  flow.currentStepKey = next ? next.key : null
  if (!next && flow.status === 'IN_PROGRESS') flow.status = 'COMPLETED'
  flow.updateTime = formatDateTime(new Date())
}

/** 通用的「按序办理 + 状态守卫」，入职与离职共用，避免两套流程各写一遍流转判断 */
function completeStep(flow, key, body, operator, statusCode, label) {
  if (!flow) return { code: CODE.NOT_FOUND, message: `${label}流程不存在` }
  if (flow.status !== 'IN_PROGRESS')
    return { code: statusCode, message: `流程已${FLOW_STATUS_LABEL[flow.status]}，不可继续办理` }
  const step = flow.steps.find((s) => s.key === key)
  if (!step) return { code: statusCode, message: '流程步骤不存在' }
  if (step.status === 'DONE') return { code: statusCode, message: `「${step.name}」已完成，不可重复办理` }
  const current = flow.steps.find((s) => s.status === 'PENDING')
  if (!current || current.key !== key)
    return { code: statusCode, message: `请先办理「${current ? current.name : ''}」` }
  return null
}

function markStepDone(flow, key, body, operator) {
  const step = flow.steps.find((s) => s.key === key)
  step.status = 'DONE'
  step.operatorId = operator.id
  step.operatorName = operator.real_name
  step.operateTime = formatDateTime(new Date())
  step.remark = body.remark == null || body.remark === '' ? step.remark : String(body.remark).trim()
  afterStepDone(flow)
}

/* ==================== 入职流程 ==================== */

export function listOnboardings({ status, stationId, keyword, pageNum, pageSize }) {
  ensure()
  let rows = state.onboardings
  if (status) rows = rows.filter((f) => f.status === status)
  if (stationId != null && stationId !== '') rows = rows.filter((f) => f.stationId === Number(stationId))
  const text = String(keyword || '').trim()
  if (text) rows = rows.filter((f) => f.candidateName.includes(text) || f.flowNo.includes(text))
  const sorted = rows
    .slice()
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map(toFlowVO)
  return paginate(sorted, pageNum, pageSize)
}

export function findOnboarding(id) {
  ensure()
  const flow = state.onboardings.find((f) => f.id === Number(id))
  return flow ? { code: 200, data: toFlowVO(flow) } : { code: CODE.NOT_FOUND, message: '入职流程不存在' }
}

export function createOnboarding(body, operator) {
  ensure()
  const id = (state.seq.flow += 1)
  const now = new Date()
  const steps = ONBOARDING_STEPS.map((def, index) => ({
    key: def.key,
    name: def.name,
    order: index + 1,
    status: 'PENDING',
    operatorId: null,
    operatorName: null,
    operateTime: null,
    remark: null
  }))
  const flow = {
    id,
    flowType: 'ONBOARDING',
    flowNo: `ON-${formatDate(now).replace(/-/g, '')}-${String(id).padStart(4, '0')}`,
    candidateName: String(body.candidateName).trim(),
    employeeId: null,
    phone: String(body.phone).trim(),
    gender: body.gender === undefined ? 0 : Number(body.gender),
    education: body.education || null,
    deptId: body.deptId == null ? null : Number(body.deptId),
    stationId: body.stationId == null ? null : Number(body.stationId),
    position: body.position ? String(body.position).trim() : null,
    role: 'STAFF',
    expectedEntryDate: body.expectedEntryDate || formatDate(now),
    remark: body.remark ? String(body.remark).trim() : null,
    status: 'IN_PROGRESS',
    rejectReason: null,
    rejectedBy: null,
    rejectedTime: null,
    steps,
    currentStepKey: steps[0].key,
    createTime: formatDateTime(now),
    updateTime: formatDateTime(now),
    operatorId: operator.id,
    operatorName: operator.real_name
  }
  state.onboardings.push(flow)
  bucket.write(state)
  return { code: 200, data: toFlowVO(flow) }
}

/** 建档并生成员工与账号：落库的员工先置 status=0（未生效），全部步骤办完才转为在职 */
function createEmployeeForFlow(flow, body) {
  if (flow.employeeId) return { code: HR_CODE.ONBOARDING_STATUS_INVALID, message: '该流程已生成员工，不可重复建档' }
  const username = body.username == null ? '' : String(body.username).trim()
  if (!isUsername(username)) return { code: CODE.BAD_REQUEST, message: '登录账号须为字母开头、4-30 位字母数字下划线' }
  if (db.employees.some((e) => e.username === username && e.is_deleted === 0))
    return { code: 1003, message: '登录账号已存在' }
  if (!isStrongPassword(body.password))
    return { code: CODE.BAD_REQUEST, message: '初始密码须为 8-20 位且同时包含字母和数字' }
  const deptId = body.deptId == null ? flow.deptId : Number(body.deptId)
  if (deptId == null || !findDepartmentById(deptId)) return { code: 3001, message: '指定的部门不存在' }
  const stationId = body.stationId == null ? flow.stationId : Number(body.stationId)
  const station = stationId == null ? null : findStationById(stationId)
  if (!station) return { code: 4001, message: '指定的驿站不存在' }
  if (station.status !== 1) return { code: 4004, message: '驿站已停用，不能归属员工' }

  const now = new Date()
  const id = (db.seq.employee += 1)
  db.employees.push({
    id,
    username,
    password: String(body.password),
    real_name: flow.candidateName,
    phone: flow.phone,
    gender: flow.gender,
    dept_id: deptId,
    station_id: stationId,
    role: flow.role || 'STAFF',
    status: 0,
    // 初始密码由人事设定，首登必须改密，与一期新增员工口径一致
    pwd_changed: 0,
    entry_date: flow.expectedEntryDate,
    last_login_time: null,
    remark: `入职流程 ${flow.flowNo} 转入`,
    is_deleted: 0,
    create_time: formatDateTime(now),
    update_time: formatDateTime(now)
  })
  flow.employeeId = id
  flow.deptId = deptId
  flow.stationId = stationId

  const probationMonths = body.probationMonths === undefined ? 3 : Number(body.probationMonths)
  const probationEnd = addMonths(parseDate(flow.expectedEntryDate), probationMonths)
  state.profiles.push({
    employeeId: id,
    education: flow.education,
    contractType: body.contractType || 'FIXED_TERM',
    contractStart: flow.expectedEntryDate,
    contractEnd: formatDate(addMonths(parseDate(flow.expectedEntryDate), 3)),
    probationMonths,
    probationEnd: formatDate(probationEnd),
    regularDate: formatDate(probationEnd),
    socialSecurityBase: null,
    emergencyContactName: null,
    emergencyContactPhone: null,
    emergencyContactRelation: null,
    bankName: null,
    bankAccount: null,
    leaveDate: null,
    createTime: formatDateTime(now),
    updateTime: formatDateTime(now)
  })
  // 定薪步骤要求「先有定薪档案再调整」（saveSalary 对无档案员工回 9305）：
  // 建档时同步落一条 0 值定薪行，否则入职流程会卡在「定薪」步骤，永远走不到「完成」
  state.salaries.push({
    employeeId: id,
    basicSalary: 0,
    postSalary: 0,
    performanceBase: 0,
    allowances: [],
    allowancesTotal: 0,
    totalSalary: 0,
    effectiveDate: flow.expectedEntryDate,
    updateTime: formatDateTime(now)
  })
  return { code: 200 }
}

function assignForFlow(flow, body) {
  const employee = findEmployeeById(flow.employeeId)
  if (!employee) return { code: HR_CODE.ONBOARDING_STATUS_INVALID, message: '尚未建档生成员工，无法分配驿站/岗位' }
  if (body.deptId !== undefined) {
    if (!findDepartmentById(body.deptId)) return { code: 3001, message: '指定的部门不存在' }
    employee.dept_id = Number(body.deptId)
  }
  if (body.stationId !== undefined) {
    const station = findStationById(body.stationId)
    if (!station) return { code: 4001, message: '指定的驿站不存在' }
    if (station.status !== 1) return { code: 4004, message: '驿站已停用，不能归属员工' }
    employee.station_id = Number(body.stationId)
  }
  if (body.position !== undefined) flow.position = String(body.position).trim()
  if (body.role !== undefined) {
    if (!['STATION_ADMIN', 'STAFF'].includes(body.role))
      return { code: CODE.BAD_REQUEST, message: 'role 仅支持 STATION_ADMIN / STAFF' }
    employee.role = body.role
    flow.role = body.role
  }
  employee.update_time = formatDateTime(new Date())
  return { code: 200 }
}

function salaryForFlow(flow, body) {
  if (!flow.employeeId) return { code: HR_CODE.ONBOARDING_STATUS_INVALID, message: '尚未建档生成员工，无法定薪' }
  const operator = { id: flow.operatorId, real_name: flow.operatorName }
  const result = saveSalary(
    flow.employeeId,
    {
      basicSalary: body.basicSalary,
      postSalary: body.postSalary,
      performanceBase: body.performanceBase,
      allowances: body.allowances,
      effectiveDate: body.effectiveDate || flow.expectedEntryDate,
      reason: body.reason || '入职定薪'
    },
    operator,
    'ENTRY'
  )
  return result.code === 200 ? { code: 200 } : result
}

/** 完成入职归档：员工转在职（这是「全部办完才真正落库为在职员工」的最后一环） */
function finishOnboarding(flow) {
  if (!flow.employeeId) return { code: HR_CODE.ONBOARDING_STATUS_INVALID, message: '尚未建档生成员工，无法完成入职' }
  const employee = findEmployeeById(flow.employeeId)
  if (!employee) return { code: CODE.NOT_FOUND, message: '员工不存在' }
  employee.status = 1
  employee.update_time = formatDateTime(new Date())
  return { code: 200 }
}

export function completeOnboardingStep(id, key, body, operator) {
  ensure()
  const flow = state.onboardings.find((f) => f.id === Number(id))
  const error = completeStep(flow, key, body, operator, HR_CODE.ONBOARDING_STATUS_INVALID, '入职')
  if (error) return error

  let result = { code: 200 }
  if (key === 'CREATE_ACCOUNT') result = createEmployeeForFlow(flow, body)
  else if (key === 'ASSIGN_STATION') result = assignForFlow(flow, body)
  else if (key === 'SET_SALARY') result = salaryForFlow(flow, body)
  else if (key === 'DONE') result = finishOnboarding(flow)
  if (result.code !== 200) return result

  markStepDone(flow, key, body, operator)
  bucket.write(state)
  return { code: 200, data: toFlowVO(flow) }
}

export function rejectOnboarding(id, reason, operator) {
  ensure()
  const flow = state.onboardings.find((f) => f.id === Number(id))
  if (!flow) return { code: CODE.NOT_FOUND, message: '入职流程不存在' }
  if (flow.status !== 'IN_PROGRESS')
    return { code: HR_CODE.ONBOARDING_STATUS_INVALID, message: `流程已${FLOW_STATUS_LABEL[flow.status]}，不可驳回` }
  flow.status = 'REJECTED'
  flow.rejectReason = reason
  flow.rejectedBy = operator.real_name
  flow.rejectedTime = formatDateTime(new Date())
  flow.updateTime = flow.rejectedTime
  // 已建档的员工一并禁用：流程被驳回却留着可登录账号，属于自相矛盾的数据
  if (flow.employeeId) {
    const employee = findEmployeeById(flow.employeeId)
    if (employee) {
      employee.status = 0
      employee.remark = `入职流程 ${flow.flowNo} 已驳回`
      employee.update_time = flow.rejectedTime
    }
  }
  bucket.write(state)
  return { code: 200, data: toFlowVO(flow) }
}

/* ==================== 离职流程 ==================== */

export function listOffboardings({ status, stationId, keyword, pageNum, pageSize }) {
  ensure()
  let rows = state.offboardings
  if (status) rows = rows.filter((f) => f.status === status)
  if (stationId != null && stationId !== '') rows = rows.filter((f) => f.stationId === Number(stationId))
  const text = String(keyword || '').trim()
  if (text) rows = rows.filter((f) => (f.employeeName || '').includes(text) || f.flowNo.includes(text))
  const sorted = rows
    .slice()
    .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
    .map(toFlowVO)
  return paginate(sorted, pageNum, pageSize)
}

export function findOffboarding(id) {
  ensure()
  const flow = state.offboardings.find((f) => f.id === Number(id))
  return flow ? { code: 200, data: toFlowVO(flow) } : { code: CODE.NOT_FOUND, message: '离职流程不存在' }
}

export function createOffboarding(body, operator) {
  ensure()
  const employee = findEmployeeById(body.employeeId)
  if (!employee) return { code: CODE.NOT_FOUND, message: '员工不存在' }
  if (employee.status !== 1)
    return { code: HR_CODE.EMPLOYEE_RESIGNED, message: '该员工已离职或账号已禁用，不可发起离职' }
  if (state.offboardings.some((f) => f.employeeId === employee.id && f.status === 'IN_PROGRESS')) {
    return { code: HR_CODE.OFFBOARDING_STATUS_INVALID, message: '该员工已有进行中的离职流程' }
  }
  const id = (state.seq.flow += 1)
  const now = new Date()
  const steps = OFFBOARDING_STEPS.map((def, index) => ({
    key: def.key,
    name: def.name,
    order: index + 1,
    status: 'PENDING',
    operatorId: null,
    operatorName: null,
    operateTime: null,
    remark: null
  }))
  const flow = {
    id,
    flowType: 'OFFBOARDING',
    flowNo: `OFF-${formatDate(now).replace(/-/g, '')}-${String(id).padStart(4, '0')}`,
    candidateName: null,
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId: employee.station_id,
    type: body.type,
    reason: String(body.reason).trim(),
    lastWorkDate: body.lastWorkDate,
    settlementPayrollId: null,
    settlementPayrollNo: null,
    settlementAmount: null,
    leaveDate: null,
    remark: null,
    status: 'IN_PROGRESS',
    rejectReason: null,
    rejectedBy: null,
    rejectedTime: null,
    steps,
    currentStepKey: steps[0].key,
    createTime: formatDateTime(now),
    updateTime: formatDateTime(now),
    operatorId: operator.id,
    operatorName: operator.real_name
  }
  state.offboardings.push(flow)
  bucket.write(state)
  return { code: 200, data: toFlowVO(flow) }
}

/**
 * 离岗：必须已有薪资结算单（9306），离岗后员工置离职（status=0）+ 档案写 leave_date。
 * 不用 is_deleted：离职员工的历史工资单与考勤记录仍需可追溯，软删会让详情页查不到人。
 */
function leaveForFlow(flow) {
  if (!flow.settlementPayrollId) return { code: HR_CODE.SETTLEMENT_UNFINISHED }
  const employee = findEmployeeById(flow.employeeId)
  if (!employee) return { code: CODE.NOT_FOUND, message: '员工不存在' }
  const now = formatDateTime(new Date())
  employee.status = 0
  employee.remark = `已离职（${OFFBOARDING_TYPE_LABEL[flow.type] || ''}）`
  employee.update_time = now
  flow.leaveDate = flow.lastWorkDate
  const profile = state.profiles.find((p) => p.employeeId === flow.employeeId)
  if (profile) {
    profile.leaveDate = flow.lastWorkDate
    profile.updateTime = now
  }
  return { code: 200 }
}

export function completeOffboardingStep(id, key, body, operator) {
  ensure()
  const flow = state.offboardings.find((f) => f.id === Number(id))
  const error = completeStep(flow, key, body, operator, HR_CODE.OFFBOARDING_STATUS_INVALID, '离职')
  if (error) return error

  let result = { code: 200 }
  if (key === 'SETTLEMENT') {
    // 结算单由路由层经财务域创建后回传，本模块只落引用（不反向依赖 financeStore，避免模块成环）
    if (!body.settlementRef || !body.settlementRef.payrollId)
      return { code: HR_CODE.SETTLEMENT_UNFINISHED, message: '薪资结算单创建失败' }
    flow.settlementPayrollId = body.settlementRef.payrollId
    flow.settlementPayrollNo = body.settlementRef.payrollNo
    flow.settlementAmount = body.settlementRef.amount
  } else if (key === 'LEAVE') {
    result = leaveForFlow(flow)
  }
  if (result.code !== 200) return result

  markStepDone(flow, key, body, operator)
  bucket.write(state)
  return { code: 200, data: toFlowVO(flow) }
}

export function rejectOffboarding(id, reason, operator) {
  ensure()
  const flow = state.offboardings.find((f) => f.id === Number(id))
  if (!flow) return { code: CODE.NOT_FOUND, message: '离职流程不存在' }
  if (flow.status !== 'IN_PROGRESS')
    return { code: HR_CODE.OFFBOARDING_STATUS_INVALID, message: `流程已${FLOW_STATUS_LABEL[flow.status]}，不可驳回` }
  flow.status = 'REJECTED'
  flow.rejectReason = reason
  flow.rejectedBy = operator.real_name
  flow.rejectedTime = formatDateTime(new Date())
  flow.updateTime = flow.rejectedTime
  bucket.write(state)
  return { code: 200, data: toFlowVO(flow) }
}
