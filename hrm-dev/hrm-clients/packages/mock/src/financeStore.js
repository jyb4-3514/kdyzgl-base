import { activeEmployees, activeStations, db, findEmployeeById, findStationById, stationName } from './db.js'
import { FINANCE_CODE, STATION_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { createPersistBucket } from './persist.js'
import { currentMonth, formatDate, formatDateTime, monthRange, monthShift, paginate, parseDate } from './util.js'
import { employeeAttendanceStat } from './attendanceStore.js'
import { currentSalary } from './hrStore.js'
import { scoreOf } from './kpiStore.js'

/**
 * 财务数据层（需求9 工资单）
 *
 * 核心约束：本模块**不硬编码任何薪资计算公式**。所有金额由 payroll_rule.items 配置驱动，
 * 每个规则项声明「来源（source）+ 计算参数（params）」，算薪只按来源分发到四类解析器：
 * - FIXED      取人事定薪项（基本工资 / 岗位工资 / 绩效基数 / 津贴合计 / 指定津贴项）
 * - ATTENDANCE 按考勤记录推算（迟到 / 早退 / 缺勤 / 异常卡，按次计扣或达标发放）
 * - KPI        按 KPI 总分与绩效基数推算
 * - MANUAL     人工填写（可编辑状态下可改）
 * 因此「换一项津贴、改一次扣款标准、调整绩效比例」都只是改配置，不动代码。
 *
 * 工资单状态机（八态完整可演示，payroll-automation-design.md v1.5 §2.1）：
 *   DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──publish──▶ PUBLISHED ──confirm──▶ CONFIRMED ──pay──▶ PAID
 *                          └──reject──▶ REJECTED ──submit──▶ PENDING_APPROVAL
 *   PUBLISHED ──objection（员工提异议）──▶ OBJECTED（异议退回，清空发布/确认信息，待管理员重新核定）
 *   OBJECTED  ──publish（再发布）──▶ PUBLISHED；也可 submit 走二次审批
 * 两份「可编辑」判据（对齐 §2.3，勿再用单一 EDITABLE_STATUS）：
 *   IS_ITEM_EDITABLE 能否改/加明细金额：DRAFT / REJECTED / PENDING_APPROVAL / OBJECTED；
 *   IS_OVERWRITABLE  能否被 generate 物理覆盖重建：DRAFT / REJECTED（不变，账期锁 = !IS_OVERWRITABLE）。
 *
 * 越权红线：员工（STAFF）只能查看自己的工资单，且仅 PUBLISHED / CONFIRMED / PAID 三个状态可见；
 * 过滤一律以登录身份（user.id）为准，不接受前端传 employeeId，避免改个参数就翻到别人工资。
 */

export const PAYROLL_ITEM_TYPE_LABEL = { ADDITION: '增项', DEDUCTION: '扣项' }
export const PAYROLL_ITEM_SOURCE_LABEL = {
  FIXED: '人事定薪项',
  ATTENDANCE: '考勤推算',
  KPI: 'KPI 考核',
  MANUAL: '人工填写'
}
export const PAYROLL_STATUS_LABEL = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  PUBLISHED: '已发布',
  CONFIRMED: '已确认',
  OBJECTED: '异议退回',
  PAID: '已发放'
}
export const PAYROLL_BILL_TYPE_LABEL = { MONTHLY: '月度工资单', SETTLEMENT: '离职结算单' }

/** 员工可见状态：未发布前工资单不能让本人看到；PAID 为已确认后的归档态，须对本人可见（C-6 / U-08） */
const EMPLOYEE_VISIBLE_STATUS = ['PUBLISHED', 'CONFIRMED', 'PAID']
/** 明细金额可编辑的状态（isItemEditable）：PENDING_APPROVAL / OBJECTED 为 Q6 / Q9 新增可改 */
const IS_ITEM_EDITABLE = ['DRAFT', 'REJECTED', 'PENDING_APPROVAL', 'OBJECTED']
/** 可被 generate 物理覆盖重建的状态（isOverwritable）：仅草稿与已驳回，账期锁 = !本集合 */
const IS_OVERWRITABLE = ['DRAFT', 'REJECTED']

const PAYROLL_ACTIONS = {
  DRAFT: ['submit'],
  REJECTED: ['submit'],
  PENDING_APPROVAL: ['approve', 'reject'],
  APPROVED: ['publish'],
  PUBLISHED: ['confirm', 'objection'],
  // CONFIRMED 可「确认发放」（I-8）；OBJECTED 可「再发布」或走二次审批（submit）；PAID 为终态无动作
  CONFIRMED: ['pay'],
  OBJECTED: ['publish', 'submit'],
  PAID: []
}

/**
 * 驿站算薪配置种子（I-1/I-2/I-3/I-9）：
 * 只为 1~4 号驿站预置配置（覆盖「已启用 / 未启用」两种列表副文案），5~8 号留空以演示 9406「尚未配置」。
 * enabled 默认 0（不自动跑数）、notifyEnabled 默认 1（生成即推管理员）——契约 §4.12.16。
 */
const SETTING_SEED = [
  { stationId: 1, enabled: 1, payrollDay: 1, payrollTime: '09:00', notifyEnabled: 1, remark: '月初统一结算' },
  { stationId: 2, enabled: 0, payrollDay: 15, payrollTime: '10:00', notifyEnabled: 1, remark: null },
  {
    stationId: 3,
    enabled: 1,
    payrollDay: 31,
    payrollTime: '18:00',
    notifyEnabled: 0,
    remark: '月末结算（当月无 31 日自动钳位）'
  },
  { stationId: 4, enabled: 0, payrollDay: 5, payrollTime: '09:30', notifyEnabled: 1, remark: null }
]

/** 留痕金额白名单：明细级 + 合计级；禁止写入 rule_snapshot / 凭据 / 证件信息（api.md §4.12 数据安全口径） */
function snapshotItems(payroll) {
  return payroll.items.map((item) => ({
    itemKey: item.key,
    itemType: item.type,
    itemName: item.name,
    amount: item.amount
  }))
}

function totalsOf(payroll) {
  return {
    additionTotal: payroll.additionTotal,
    deductionTotal: payroll.deductionTotal,
    grossAmount: payroll.grossAmount,
    netAmount: payroll.netAmount
  }
}

/** 单一重算入口：加款计入应发、扣款计入扣项、实发 = 应发 − 扣项（沿用 PayrollTotalsPolicy，不改公式） */
function recomputeTotals(payroll) {
  const additionTotal = payroll.items
    .filter((i) => i.type === 'ADDITION')
    .reduce((sum, i) => sum + Number(i.amount), 0)
  const deductionTotal = payroll.items
    .filter((i) => i.type === 'DEDUCTION')
    .reduce((sum, i) => sum + Number(i.amount), 0)
  payroll.additionTotal = additionTotal
  payroll.deductionTotal = deductionTotal
  payroll.grossAmount = additionTotal
  payroll.netAmount = additionTotal - deductionTotal
}

/**
 * 追加一条工资单操作留痕（I-7）。
 * 数据源 payroll_log 为追加型审计表（只增不改、无删除入口）；
 * employeeId / month 为冗余定位列，供 I-10 对账在「单据被覆盖重建删除」后仍能按员工+账期聚合。
 */
function pushLog({
  payrollId,
  employeeId,
  month,
  action,
  operator = null,
  operatorRole = null,
  fromStatus = null,
  toStatus = null,
  reason = null,
  before = null,
  after = null,
  time = null
}) {
  ensure()
  const log = {
    id: (state.seq.log += 1),
    payrollId: Number(payrollId),
    employeeId: Number(employeeId),
    month,
    action,
    operatorId: operator ? operator.id : null,
    operatorName: operator ? operator.real_name : '系统',
    operatorRole: operatorRole || (operator ? operator.role : 'SYSTEM'),
    time: time || formatDateTime(new Date()),
    fromStatus,
    toStatus,
    reason,
    before,
    after
  }
  state.logs.push(log)
  return log
}

/** 按工资单的当前状态回放一条自洽的留痕时间线（种子用），使 I-7 一打开就有内容 */
function seedLogsFor(payroll, admin) {
  const self = { id: payroll.employeeId, real_name: payroll.employeeName, role: 'STAFF' }
  const base = { payrollId: payroll.id, employeeId: payroll.employeeId, month: payroll.month }
  const totals = totalsOf(payroll)
  pushLog({
    ...base,
    action: 'GENERATE_MANUAL',
    operator: admin,
    toStatus: 'DRAFT',
    time: payroll.createTime,
    after: { ...totals, manualKept: 0 }
  })
  const status = payroll.status
  if (status === 'DRAFT') return
  pushLog({
    ...base,
    action: 'SUBMIT',
    operator: admin,
    fromStatus: 'DRAFT',
    toStatus: 'PENDING_APPROVAL',
    time: payroll.approveTime || payroll.createTime,
    before: { ...totals },
    after: { ...totals }
  })
  if (status === 'REJECTED') {
    pushLog({
      ...base,
      action: 'REJECT',
      operator: admin,
      fromStatus: 'PENDING_APPROVAL',
      toStatus: 'REJECTED',
      reason: payroll.approveRemark,
      time: payroll.approveTime
    })
    return
  }
  pushLog({
    ...base,
    action: 'APPROVE',
    operator: admin,
    fromStatus: 'PENDING_APPROVAL',
    toStatus: 'APPROVED',
    time: payroll.approveTime
  })
  if (status === 'APPROVED') return
  pushLog({
    ...base,
    action: 'PUBLISH',
    operator: admin,
    fromStatus: 'APPROVED',
    toStatus: 'PUBLISHED',
    time: payroll.publishTime || payroll.objectionTime || payroll.approveTime
  })
  if (status === 'PUBLISHED') return
  if (status === 'OBJECTED') {
    pushLog({
      ...base,
      action: 'OBJECTION',
      operator: self,
      fromStatus: 'PUBLISHED',
      toStatus: 'OBJECTED',
      reason: payroll.objectionReason,
      time: payroll.objectionTime
    })
    return
  }
  pushLog({
    ...base,
    action: 'CONFIRM',
    operator: self,
    fromStatus: 'PUBLISHED',
    toStatus: 'CONFIRMED',
    time: payroll.confirmTime
  })
  if (status === 'CONFIRMED') return
  pushLog({
    ...base,
    action: 'PAY',
    operator: admin,
    fromStatus: 'CONFIRMED',
    toStatus: 'PAID',
    time: payroll.paidTime
  })
}

const SALARY_FIELD_LABEL = { basicSalary: '基本工资', postSalary: '岗位工资', performanceBase: '绩效基数' }
/** M11：LEAVE = 已批请假天数（计薪天数），三处镜像必须同增（dict.js / 本文件 / pc/utils/payrollPreview.js） */
const ATTENDANCE_FIELD = {
  LATE: 'lateCount',
  EARLY_LEAVE: 'earlyLeaveCount',
  ABSENT: 'absentCount',
  ABNORMAL: 'abnormalCount',
  LEAVE: 'leaveCount'
}
const ATTENDANCE_LABEL = { LATE: '迟到', EARLY_LEAVE: '早退', ABSENT: '缺勤', ABNORMAL: '异常卡', LEAVE: '请假' }

/* ==================== 规则种子 ==================== */

/**
 * 默认计薪规则：8 个规则项覆盖四类来源。
 * 权重/金额只是「配置的初始值」，管理端可随时改；算薪代码里没有任何一项的专属公式。
 */
const RULE_ITEM_SEED = [
  { key: 'BASIC', name: '基本工资', type: 'ADDITION', source: 'FIXED', params: { field: 'basicSalary' } },
  { key: 'POST', name: '岗位工资', type: 'ADDITION', source: 'FIXED', params: { field: 'postSalary' } },
  { key: 'ALLOWANCE', name: '津贴补助', type: 'ADDITION', source: 'FIXED', params: { field: 'allowances' } },
  {
    key: 'KPI_PERF',
    name: '绩效工资',
    type: 'ADDITION',
    source: 'KPI',
    params: { baseField: 'performanceBase', capRatio: 1.2 }
  },
  {
    key: 'FULL_ATTEND',
    name: '全勤奖',
    type: 'ADDITION',
    source: 'ATTENDANCE',
    params: { metric: 'ABSENT', mode: 'BONUS_IF_ZERO', amount: 200 }
  },
  {
    key: 'LATE_FINE',
    name: '迟到扣款',
    type: 'DEDUCTION',
    source: 'ATTENDANCE',
    params: { metric: 'LATE', mode: 'PER_COUNT', amount: 20, cap: 300 }
  },
  {
    key: 'ABSENT_FINE',
    name: '缺勤扣款',
    type: 'DEDUCTION',
    source: 'ATTENDANCE',
    params: { metric: 'ABSENT', mode: 'PER_COUNT', amount: 150, cap: 0 }
  },
  { key: 'OTHER', name: '其他调整', type: 'ADDITION', source: 'MANUAL', params: { defaultValue: 0 } }
]

const bucket = createPersistBucket('finance')
let state = null

/* ==================== 算薪内核（唯一的分发入口） ==================== */

function fixedAmount(params, ctx) {
  if (!ctx.salary) return { amount: 0, detail: '未设置定薪档案，按 0 计' }
  if (params.field === 'allowances') {
    const list = ctx.salary.allowances || []
    if (!params.allowanceKey)
      return { amount: ctx.salary.allowancesTotal, detail: `取人事定薪项：津贴合计（${list.length} 项）` }
    const hit = list.find((a) => a.key === params.allowanceKey)
    return { amount: hit ? Number(hit.amount) : 0, detail: `取人事定薪项：${hit ? hit.name : params.allowanceKey}` }
  }
  const amount = Number(ctx.salary[params.field]) || 0
  return { amount, detail: `取人事定薪项：${SALARY_FIELD_LABEL[params.field] || params.field}` }
}

function attendanceAmount(params, ctx) {
  const count = ctx.attendance ? Number(ctx.attendance[ATTENDANCE_FIELD[params.metric]]) || 0 : 0
  const label = ATTENDANCE_LABEL[params.metric] || params.metric
  if (params.mode === 'BONUS_IF_ZERO') {
    return {
      amount: count === 0 ? Number(params.amount) || 0 : 0,
      detail: `${label} ${count} 次，${count === 0 ? '满足发放条件' : '不满足发放条件'}`
    }
  }
  const perUnit = Number(params.amount) || 0
  let amount = count * perUnit
  let detail = `${label} ${count} 次 × ${perUnit} 元`
  const cap = Number(params.cap)
  if (Number.isFinite(cap) && cap > 0 && amount > cap) {
    amount = cap
    detail += `，封顶 ${cap} 元`
  }
  return { amount, detail }
}

function kpiAmount(params, ctx) {
  const base = ctx.salary ? Number(ctx.salary[params.baseField || 'performanceBase']) || 0 : 0
  if (ctx.kpiScore == null) return { amount: 0, detail: '该月无 KPI 评分记录，按 0 计' }
  if (!base) return { amount: 0, detail: '定薪档案缺少绩效基数，按 0 计' }
  const capRatio = params.capRatio === undefined ? 1 : Number(params.capRatio)
  const rate = Math.max(0, Math.min(Number.isFinite(capRatio) && capRatio > 0 ? capRatio : 1, ctx.kpiScore / 100))
  const amount = Math.round(base * rate)
  const capText =
    Number.isFinite(capRatio) && capRatio > 0 && capRatio !== 1 ? `（上限 ${Math.round(capRatio * 100)}%）` : ''
  return { amount, detail: `绩效基数 ${base} × KPI 得分 ${ctx.kpiScore}%${capText} = ${amount} 元` }
}

/** 规则项 → 金额与解释文案；来源未知按 0 计而不是抛错，避免一条脏配置打挂整批算薪 */
function resolveItem(item, ctx) {
  if (item.source === 'FIXED') return fixedAmount(item.params || {}, ctx)
  if (item.source === 'ATTENDANCE') return attendanceAmount(item.params || {}, ctx)
  if (item.source === 'KPI') return kpiAmount(item.params || {}, ctx)
  if (item.source === 'MANUAL')
    return { amount: Number((item.params || {}).defaultValue) || 0, detail: '人工填写项，草稿状态下可调整' }
  return { amount: 0, detail: '未知来源，按 0 计' }
}

function buildItems(rule, ctx) {
  return rule.items
    .filter((item) => item.enabled === 1)
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    .map((item) => {
      const { amount, detail } = resolveItem(item, ctx)
      return { key: item.key, name: item.name, type: item.type, source: item.source, amount, detail }
    })
}

/** 账期上下文：定薪 / 考勤 / KPI 三处取数各只调用一次，避免逐项重复查 */
function contextOf(employeeId, month) {
  const range = monthRange(month)
  return {
    salary: currentSalary(employeeId),
    attendance: employeeAttendanceStat(employeeId, range.startDate, range.endDate),
    kpiScore: scoreOf(employeeId, month)
  }
}

function buildPayroll({
  employee,
  month,
  rule,
  billType = 'MONTHLY',
  status = 'DRAFT',
  offboardingId = null,
  remark = null
}) {
  const items = buildItems(rule, contextOf(employee.id, month))
  const additionTotal = items.filter((i) => i.type === 'ADDITION').reduce((sum, i) => sum + Number(i.amount), 0)
  const deductionTotal = items.filter((i) => i.type === 'DEDUCTION').reduce((sum, i) => sum + Number(i.amount), 0)
  const now = formatDateTime(new Date())
  const id = (state.seq.payroll += 1)
  const prefix = billType === 'SETTLEMENT' ? 'SET' : 'PAY'
  return {
    id,
    payrollNo: `${prefix}-${String(month).replace('-', '')}-${String(employee.id).padStart(4, '0')}`,
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId: employee.station_id,
    month,
    billType,
    ruleId: rule.id,
    ruleName: rule.ruleName,
    items,
    additionTotal,
    deductionTotal,
    grossAmount: additionTotal,
    netAmount: additionTotal - deductionTotal,
    status,
    remark,
    approveRemark: null,
    approverId: null,
    approverName: null,
    approveTime: null,
    publisherId: null,
    publisherName: null,
    publishTime: null,
    confirmTime: null,
    objectionReason: null,
    objectionTime: null,
    // PAID 归档字段（C-6 出参含 paidTime / paidByName）
    paidById: null,
    paidByName: null,
    paidTime: null,
    offboardingId,
    createTime: now,
    updateTime: now
  }
}

/* ==================== 种子 ==================== */

/** 上期账期的状态分布：覆盖八态中的已确认 / 已发布 / 已驳回 / 草稿 / 异议退回 / 已发放，供管理端与员工端同时演示 */
const PREV_MONTH_PUBLISHED = [8, 9, 10]
const PREV_MONTH_REJECTED = [11]
const PREV_MONTH_DRAFT = [12]
const PREV_MONTH_OBJECTED = [13]
const PREV_MONTH_PAID = [14]

function prevMonthStatusOf(employeeId) {
  if (PREV_MONTH_PUBLISHED.includes(employeeId)) return 'PUBLISHED'
  if (PREV_MONTH_REJECTED.includes(employeeId)) return 'REJECTED'
  if (PREV_MONTH_DRAFT.includes(employeeId)) return 'DRAFT'
  if (PREV_MONTH_OBJECTED.includes(employeeId)) return 'OBJECTED'
  if (PREV_MONTH_PAID.includes(employeeId)) return 'PAID'
  return 'CONFIRMED'
}

/** 本期账期的状态分布：驿站 1 留草稿供现场演示「提交 → 审核 → 发布」全链路 */
function currentMonthStatusOf(employee) {
  const sid = employee.station_id
  if (sid === 1 || sid === null) return 'DRAFT'
  if (sid === 2 || sid === 3) return 'PENDING_APPROVAL'
  if (sid === 4 || sid === 5) return 'APPROVED'
  return 'DRAFT'
}

function at(dateText, hour, minute = 0) {
  const date = parseDate(dateText)
  date.setHours(hour, minute, 0, 0)
  return date
}

/** 按状态补全流转时间与审核意见：时间线自洽（未审核的单子不会有审批时间，异议退回则清空发布信息） */
function applyStatusTimeline(payroll, status, baseTime, admin) {
  const step = (n) => formatDateTime(new Date(baseTime.getTime() + n * 86400000))
  payroll.createTime = formatDateTime(baseTime)
  payroll.updateTime = formatDateTime(baseTime)
  if (status === 'DRAFT' || status === 'PENDING_APPROVAL') return
  payroll.approverId = admin.id
  payroll.approverName = admin.real_name
  payroll.approveTime = step(1)
  payroll.updateTime = payroll.approveTime
  if (status === 'REJECTED') {
    payroll.approveRemark = '绩效数据与业务口径不符，请核对后重新生成'
    return
  }
  // 异议退回：员工在已发布后提异议，发布/确认信息被清空，只留异议原因与时间
  if (status === 'OBJECTED') {
    payroll.objectionReason = '本月缺勤天数与实际不符，请重新核定'
    payroll.objectionTime = step(2)
    payroll.updateTime = payroll.objectionTime
    return
  }
  payroll.publisherId = admin.id
  payroll.publisherName = admin.real_name
  payroll.publishTime = step(2)
  payroll.updateTime = payroll.publishTime
  if (status === 'CONFIRMED' || status === 'PAID') {
    payroll.confirmTime = step(3)
    payroll.updateTime = payroll.confirmTime
  }
  if (status === 'PAID') {
    payroll.paidById = admin.id
    payroll.paidByName = admin.real_name
    payroll.paidTime = step(4)
    payroll.updateTime = payroll.paidTime
  }
}

function buildSettingsSeed(admin) {
  const settings = SETTING_SEED.filter((row) => findStationById(row.stationId))
  const settingLogs = []
  const at = (dateText, hour, minute = 0) => {
    const date = parseDate(dateText)
    date.setHours(hour, minute, 0, 0)
    return formatDateTime(date)
  }
  const baseMonth = monthShift(currentMonth(), -2)
  settings.forEach((row, index) => {
    const created = at(`${baseMonth}-0${(index % 9) + 1}`, 9)
    settingLogs.push({
      id: (state.seq.settingLog += 1),
      stationId: row.stationId,
      action: 'CREATE',
      operatorId: admin.id,
      operatorName: admin.real_name,
      operatorRole: admin.role,
      time: created,
      before: null,
      after: {
        enabled: row.enabled,
        payrollDay: row.payrollDay,
        payrollTime: row.payrollTime,
        notifyEnabled: row.notifyEnabled,
        remark: row.remark
      },
      remark: row.remark
    })
    // ENABLE 必须独立成行、可追溯「启用 0→1」（M-9 硬要求）：只为已启用的驿站补一条启用记录
    if (row.enabled === 1) {
      settingLogs.push({
        id: (state.seq.settingLog += 1),
        stationId: row.stationId,
        action: 'ENABLE',
        operatorId: admin.id,
        operatorName: admin.real_name,
        operatorRole: admin.role,
        time: at(monthRange(currentMonth()).startDate, 10),
        before: { enabled: 0 },
        after: { enabled: 1 },
        remark: '首次启用自动算薪'
      })
    }
  })
  return { settings, settingLogs }
}

function buildRunsSeed() {
  const runs = []
  const prevM = monthShift(currentMonth(), -1)
  const prev2M = monthShift(currentMonth(), -2)
  const push = (row) => runs.push({ id: (state.seq.run += 1), ...row })
  const dueAt = (month, hour = 9) => {
    const date = parseDate(monthRange(month).startDate)
    date.setHours(hour, 0, 0, 0)
    return formatDateTime(date)
  }
  push({
    stationId: 1,
    targetMonth: prev2M,
    attemptDate: `${prev2M}-01`,
    triggerType: 'AUTO',
    dueAt: dueAt(prev2M),
    status: 'SUCCESS',
    skipCode: null,
    skipReason: null,
    generatedCount: 6,
    failReason: null,
    operatorName: '系统',
    startTime: dueAt(prev2M),
    finishTime: dueAt(prev2M).replace(/(\d{2}):00:00$/, '09:02:31')
  })
  push({
    stationId: 3,
    targetMonth: prev2M,
    attemptDate: `${prev2M}-28`,
    triggerType: 'CATCH_UP',
    dueAt: dueAt(prev2M, 18),
    status: 'SKIPPED',
    skipCode: 'BLOCKED_9405',
    skipReason: '该账期已存在非可覆盖工资单',
    generatedCount: null,
    failReason: null,
    operatorName: '系统',
    startTime: dueAt(prev2M, 18),
    finishTime: dueAt(prev2M, 18).replace(/(\d{2}):00:00$/, '18:00:04')
  })
  push({
    stationId: 4,
    targetMonth: prevM,
    attemptDate: `${prevM}-05`,
    triggerType: 'AUTO',
    dueAt: dueAt(prevM, 9),
    status: 'FAILED',
    skipCode: null,
    skipReason: null,
    generatedCount: null,
    failReason: 'STALE_RECLAIMED',
    operatorName: '系统',
    startTime: dueAt(prevM, 9),
    finishTime: dueAt(prevM, 9).replace(/(\d{2}):00:00$/, '09:31:12')
  })
  push({
    stationId: 1,
    targetMonth: prevM,
    attemptDate: `${prevM}-01`,
    triggerType: 'MANUAL',
    dueAt: dueAt(prevM, 9),
    status: 'SUCCESS',
    skipCode: null,
    skipReason: null,
    generatedCount: 6,
    failReason: null,
    operatorName: '系统管理员',
    startTime: formatDateTime(new Date(`${prevM}-01T09:10:00`)),
    finishTime: formatDateTime(new Date(`${prevM}-01T09:10:03`))
  })
  return runs
}

function buildSeed() {
  const admin = db.employees.find((e) => e.role === 'ADMIN') || db.employees[0]
  const rules = [
    {
      id: 1,
      ruleName: '标准计薪规则',
      remark: '覆盖基本工资、岗位工资、津贴、绩效、考勤奖惩与人工调整，管理端可自行增删改',
      status: 1,
      items: RULE_ITEM_SEED.map((item, index) => ({
        id: index + 1,
        ...item,
        params: { ...item.params },
        enabled: 1,
        sortOrder: index + 1
      })),
      createTime: formatDateTime(at(`${monthShift(currentMonth(), -3)}-01`, 9)),
      updateTime: formatDateTime(at(`${monthShift(currentMonth(), -1)}-05`, 9))
    },
    {
      id: 2,
      ruleName: '旧版计薪规则（试用期）',
      remark: '演示已停用规则：停用后不参与算薪，历史工资单仍保留当时的规则快照',
      status: 0,
      items: [
        {
          id: 1,
          key: 'BASIC',
          name: '基本工资',
          type: 'ADDITION',
          source: 'FIXED',
          params: { field: 'basicSalary' },
          enabled: 1,
          sortOrder: 1
        }
      ],
      createTime: formatDateTime(at(`${monthShift(currentMonth(), -6)}-01`, 9)),
      updateTime: formatDateTime(at(`${monthShift(currentMonth(), -6)}-01`, 9))
    }
  ]

  const seed = {
    seq: { rule: rules.length, payroll: 0, settingLog: 0, run: 0, log: 0 },
    rules,
    payrolls: [],
    settings: [],
    settingLogs: [],
    runs: [],
    logs: []
  }
  state = seed

  const currentM = currentMonth()
  const prevM = monthShift(currentM, -1)
  const prevBase = at(monthRange(prevM).endDate, 18)
  const curBase = at(monthRange(currentM).startDate, 9)

  activeEmployees().forEach((employee) => {
    const prev = buildPayroll({ employee, month: prevM, rule: rules[0], status: prevMonthStatusOf(employee.id) })
    applyStatusTimeline(prev, prev.status, prevBase, admin)
    seed.payrolls.push(prev)
    seedLogsFor(prev, admin)

    const cur = buildPayroll({ employee, month: currentM, rule: rules[0], status: currentMonthStatusOf(employee) })
    applyStatusTimeline(cur, cur.status, curBase, admin)
    seed.payrolls.push(cur)
    seedLogsFor(cur, admin)
  })

  const settingSeed = buildSettingsSeed(admin)
  seed.settings = settingSeed.settings
  seed.settingLogs = settingSeed.settingLogs
  seed.runs = buildRunsSeed()
  return seed
}

function ensure() {
  if (state) return state
  state = bucket.read() || buildSeed()
  // 兼容上一版本已持久化的 state：补齐本批新增集合，避免升级后首次访问报 undefined
  state.seq = { settingLog: 0, run: 0, log: 0, ...(state.seq || {}) }
  state.settings = state.settings || []
  state.settingLogs = state.settingLogs || []
  state.runs = state.runs || []
  state.logs = state.logs || []
  return state
}

export function resetFinanceStore() {
  state = null
  bucket.clear()
}

/* ==================== VO ==================== */

function ruleItemVO(item) {
  return {
    id: item.id,
    key: item.key,
    name: item.name,
    type: item.type,
    typeLabel: PAYROLL_ITEM_TYPE_LABEL[item.type] || item.type,
    source: item.source,
    sourceLabel: PAYROLL_ITEM_SOURCE_LABEL[item.source] || item.source,
    params: { ...(item.params || {}) },
    enabled: item.enabled,
    sortOrder: item.sortOrder
  }
}

function toRuleVO(rule) {
  return {
    id: rule.id,
    ruleName: rule.ruleName,
    remark: rule.remark,
    status: rule.status,
    statusLabel: rule.status === 1 ? '启用' : '停用',
    itemCount: rule.items.length,
    enabledItemCount: rule.items.filter((i) => i.enabled === 1).length,
    items: rule.items
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(ruleItemVO),
    createTime: rule.createTime,
    updateTime: rule.updateTime
  }
}

function toPayrollVO(payroll) {
  return {
    ...payroll,
    stationName: stationName(payroll.stationId),
    statusLabel: PAYROLL_STATUS_LABEL[payroll.status],
    billTypeLabel: PAYROLL_BILL_TYPE_LABEL[payroll.billType],
    items: payroll.items.map((item) => ({
      ...item,
      typeLabel: PAYROLL_ITEM_TYPE_LABEL[item.type] || item.type,
      sourceLabel: PAYROLL_ITEM_SOURCE_LABEL[item.source] || item.source
    })),
    // 员工可执行的动作由服务端下发：前端不必自己维护一份状态机，避免两边口径漂移
    actions: PAYROLL_ACTIONS[payroll.status] || []
  }
}

/* ==================== 计薪规则 ==================== */

export function listRules() {
  ensure()
  return state.rules
    .slice()
    .sort((a, b) => b.status - a.status || a.id - b.id)
    .map(toRuleVO)
}

export function findRule(id) {
  ensure()
  const rule = state.rules.find((r) => r.id === Number(id))
  return rule ? toRuleVO(rule) : null
}

function pickRuleItems(list) {
  return list.map((item, index) => ({
    id: index + 1,
    key: String(item.key).trim(),
    name: String(item.name).trim(),
    type: item.type,
    source: item.source,
    params: { ...(item.params || {}) },
    enabled: item.enabled === undefined ? 1 : Number(item.enabled),
    sortOrder: item.sortOrder === undefined ? index + 1 : Number(item.sortOrder)
  }))
}

export function createRule(body) {
  ensure()
  const id = (state.seq.rule += 1)
  const rule = {
    id,
    ruleName: String(body.ruleName).trim(),
    remark: body.remark ? String(body.remark).trim() : null,
    status: body.status === undefined ? 1 : Number(body.status),
    items: pickRuleItems(body.items),
    createTime: formatDateTime(new Date()),
    updateTime: formatDateTime(new Date())
  }
  state.rules.push(rule)
  bucket.write(state)
  return { code: 200, data: toRuleVO(rule) }
}

export function updateRule(id, body) {
  ensure()
  const rule = state.rules.find((r) => r.id === Number(id))
  if (!rule) return { code: FINANCE_CODE.RULE_NOT_EXISTS }
  if (body.ruleName !== undefined) rule.ruleName = String(body.ruleName).trim()
  if (body.remark !== undefined) rule.remark = body.remark === null ? null : String(body.remark).trim()
  if (body.status !== undefined) rule.status = Number(body.status)
  if (body.items !== undefined) rule.items = pickRuleItems(body.items)
  rule.updateTime = formatDateTime(new Date())
  bucket.write(state)
  return { code: 200, data: toRuleVO(rule) }
}

export function removeRule(id) {
  ensure()
  const index = state.rules.findIndex((r) => r.id === Number(id))
  if (index < 0) return { code: FINANCE_CODE.RULE_NOT_EXISTS }
  // 已被工资单引用的规则不可删：工资单快照里有 ruleId/ruleName，删规则会让历史单据指向空规则
  if (state.payrolls.some((p) => p.ruleId === Number(id)))
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '该规则已被工资单引用，不能删除，可改为停用' }
  state.rules.splice(index, 1)
  bucket.write(state)
  return { code: 200, data: null }
}

/** 生效规则：指定则取指定，未指定取第一个启用规则 */
export const activeRule = (ruleId) => (ruleId ? findRule(ruleId) : listRules().find((r) => r.status === 1) || null)

/* ==================== 工资单生成 ==================== */

/**
 * 按月批量生成草稿。
 * 幂等口径（C-7 按驿站收敛）：同一驿站该账期已存在非可覆盖态单 → 整批拒绝（9405），
 * 避免把管理员已经审过、员工已经看过的工资单悄悄改掉；同驿站同员工的可覆盖态单（草稿/驳回）→ 覆盖重建。
 */
export function generatePayrolls({ month, stationId, deptId, employeeIds, ruleId, trigger = 'MANUAL' }, operator = null) {
  ensure()
  const rule = activeRule(ruleId)
  if (!rule) return { code: FINANCE_CODE.RULE_NOT_EXISTS }
  const idFilter = Array.isArray(employeeIds) && employeeIds.length ? new Set(employeeIds.map(Number)) : null
  const ruleEntity = state.rules.find((r) => r.id === rule.id)
  const employees = activeEmployees().filter((e) => {
    if (e.status !== 1) return false
    if (idFilter && !idFilter.has(e.id)) return false
    if (stationId != null && stationId !== '' && e.station_id !== Number(stationId)) return false
    if (deptId != null && deptId !== '' && e.dept_id !== Number(deptId)) return false
    return true
  })
  // C-7：9405 由「账期全局级」收敛为「按驿站」——只校验本次生成范围命中的驿站，
  // 否则多驿站自动算薪会被第一个已出账的站点整批阻断（其余驿站明明可生成）。
  const scopeStations = new Set(employees.map((e) => e.station_id))
  const blocked = state.payrolls.find(
    (p) =>
      p.month === month &&
      p.billType === 'MONTHLY' &&
      !IS_OVERWRITABLE.includes(p.status) &&
      scopeStations.has(p.stationId)
  )
  if (blocked) {
    return {
      code: FINANCE_CODE.PAYROLL_GENERATED,
      message: `该驿站该账期工资单已提交审核或已发布（${blocked.payrollNo}），不可重复生成`
    }
  }

  const created = []
  employees.forEach((employee) => {
    const index = state.payrolls.findIndex(
      (p) => p.employeeId === employee.id && p.month === month && p.billType === 'MONTHLY'
    )
    // 覆盖重建须保留既有 source=MANUAL 明细（C-7 ②）：手工加扣款不因重算而丢失
    const kept = index >= 0 ? state.payrolls[index].items.filter((i) => i.source === 'MANUAL') : []
    if (index >= 0) state.payrolls.splice(index, 1)
    const payroll = buildPayroll({ employee, month, rule: ruleEntity, status: 'DRAFT' })
    kept.forEach((item) => {
      if (!payroll.items.some((i) => i.key === item.key)) payroll.items.push({ ...item })
    })
    recomputeTotals(payroll)
    state.payrolls.push(payroll)
    created.push(payroll.id)
    pushLog({
      payrollId: payroll.id,
      employeeId: payroll.employeeId,
      month: payroll.month,
      action: trigger === 'AUTO' ? 'GENERATE_AUTO' : 'GENERATE_MANUAL',
      operator,
      toStatus: 'DRAFT',
      after: { ...totalsOf(payroll), manualKept: kept.length }
    })
  })
  if (created.length) bucket.write(state)
  return {
    code: 200,
    data: { month, ruleId: rule.id, ruleName: rule.ruleName, created: created.length, payrollIds: created }
  }
}

/** 离职薪资结算单：由离职流程 SETTLEMENT 步骤触发（见 routes/hr.js），同员工同月只生成一次 */
export function createSettlementPayroll({ employeeId, month, offboardingId, remark }) {
  ensure()
  const rule = activeRule(null)
  if (!rule) return { code: FINANCE_CODE.RULE_NOT_EXISTS }
  const employee = findEmployeeById(employeeId)
  if (!employee) return { code: 404, message: '员工不存在' }
  const existing = state.payrolls.find(
    (p) => p.billType === 'SETTLEMENT' && p.employeeId === employee.id && p.month === month
  )
  if (existing) return { code: 200, data: toPayrollVO(existing) }
  const ruleEntity = state.rules.find((r) => r.id === rule.id)
  const payroll = buildPayroll({
    employee,
    month,
    rule: ruleEntity,
    billType: 'SETTLEMENT',
    status: 'DRAFT',
    offboardingId,
    remark
  })
  state.payrolls.push(payroll)
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/* ==================== 查询 ==================== */

function filterPayrolls({ month, stationId, employeeId, status, keyword, billType }) {
  let rows = state.payrolls
  if (month) rows = rows.filter((p) => p.month === month)
  if (stationId != null && stationId !== '') rows = rows.filter((p) => p.stationId === Number(stationId))
  if (employeeId != null && employeeId !== '') rows = rows.filter((p) => p.employeeId === Number(employeeId))
  if (status) rows = rows.filter((p) => p.status === status)
  if (billType) rows = rows.filter((p) => p.billType === billType)
  const text = String(keyword || '').trim()
  if (text) rows = rows.filter((p) => p.employeeName.includes(text) || p.payrollNo.includes(text))
  return rows
}

/** 工资单列表（仅管理员）：附各状态计数，页面标签页可直接用，不必再拉全量自己数 */
export function listPayrolls(filters) {
  ensure()
  const rows = filterPayrolls(filters)
  const sorted = rows
    .slice()
    .sort((a, b) => (a.month < b.month ? 1 : a.month > b.month ? -1 : a.employeeId - b.employeeId))
  const page = paginate(sorted.map(toPayrollVO), filters.pageNum, filters.pageSize)
  // 状态计数按「同月同驿站」口径统计，不含 status 筛选，否则切到某个标签页后其余计数全归零
  const scope = filterPayrolls({ ...filters, status: null })
  const counts = {}
  Object.keys(PAYROLL_STATUS_LABEL).forEach((key) => {
    counts[key] = scope.filter((p) => p.status === key).length
  })
  page.counts = counts
  page.month = filters.month || null
  return page
}

/**
 * 该员工该账期是否存在「会锁住撤回」的工资单（M11 设计规范 §1.4-3）
 * 只有草稿 / 已驳回可以随撤回一起重算，其余状态都意味着数据已出账，撤回会让工资单与申请单对不上。
 */
export function findLockingPayroll(employeeId, month) {
  ensure()
  return (
    state.payrolls.find(
      (p) => p.employeeId === Number(employeeId) && p.month === month && !IS_OVERWRITABLE.includes(p.status)
    ) || null
  )
}

/** 我的工资单：只认登录身份，且只返回已发布 / 已确认 / 已发放（未发布不给本人看；归档态对本人可见 C-6） */
export function myPayrolls(user, { month, status, pageNum, pageSize }) {
  ensure()
  let rows = state.payrolls.filter((p) => p.employeeId === user.id && EMPLOYEE_VISIBLE_STATUS.includes(p.status))
  if (month) rows = rows.filter((p) => p.month === month)
  if (status) rows = rows.filter((p) => p.status === status)
  const sorted = rows.slice().sort((a, b) => (a.month < b.month ? 1 : -1))
  const page = paginate(sorted.map(toPayrollVO), pageNum, pageSize)
  page.employeeId = user.id
  return page
}

/**
 * 工资单详情（越权守卫集中在此）：
 * ADMIN 全量；其余角色必须是本人（否则 9404），且状态须在员工可见范围内（否则 9403）。
 */
export function findPayrollForUser(id, user) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (user.role !== 'ADMIN') {
    if (payroll.employeeId !== user.id) return { code: FINANCE_CODE.PAYROLL_NO_PERMISSION }
    if (!EMPLOYEE_VISIBLE_STATUS.includes(payroll.status))
      // 未发布或已提异议退回（OBJECTED 为内部态，员工端不可见）：文案须覆盖「重新核定中」场景（设计 §1.3）
      return {
        code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
        message: '工资单尚未发布或正在重新核定中，暂不可查看'
      }
  }
  return { code: 200, data: toPayrollVO(payroll) }
}

/* ==================== 状态流转 ==================== */

/** 状态守卫：读单 → 校验动作在该状态下是否允许，入口统一，避免每个动作各写一遍判断 */
function requireAction(id, action) {
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  // 终态冻结统一收口（§2.9 assertMutable）：PAID 下任何写动作一律 9413，不区分动作
  if (payroll.status === 'PAID') return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
  if (!(PAYROLL_ACTIONS[payroll.status] || []).includes(action)) {
    return {
      code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
      message: `当前状态（${PAYROLL_STATUS_LABEL[payroll.status]}）不允许该操作`
    }
  }
  return { code: 200, data: payroll }
}

export function submitPayrolls(ids, operator = null) {
  ensure()
  const list = Array.isArray(ids) ? ids : []
  if (!list.length) return { code: 400, message: 'ids 须为非空数组' }
  const submitted = []
  for (const id of list) {
    const result = requireAction(id, 'submit')
    if (result.code !== 200) return result
    const payroll = result.data
    const fromStatus = payroll.status
    payroll.status = 'PENDING_APPROVAL'
    payroll.approveRemark = null
    payroll.objectionReason = null
    payroll.objectionTime = null
    payroll.confirmTime = null
    payroll.updateTime = formatDateTime(new Date())
    submitted.push(payroll.id)
    // C-5 补写 SUBMIT 留痕（全链路留痕）
    pushLog({
      payrollId: payroll.id,
      employeeId: payroll.employeeId,
      month: payroll.month,
      action: 'SUBMIT',
      operator,
      fromStatus,
      toStatus: 'PENDING_APPROVAL',
      before: { ...totalsOf(payroll) },
      after: { ...totalsOf(payroll) }
    })
  }
  bucket.write(state)
  return { code: 200, data: { submitted: submitted.length, payrollIds: submitted } }
}

export function approvePayroll(id, approved, approveRemark, operator) {
  ensure()
  const result = requireAction(id, approved ? 'approve' : 'reject')
  if (result.code !== 200) return result
  const payroll = result.data
  payroll.status = approved ? 'APPROVED' : 'REJECTED'
  payroll.approveRemark = approveRemark || null
  payroll.approverId = operator.id
  payroll.approverName = operator.real_name
  payroll.approveTime = formatDateTime(new Date())
  payroll.updateTime = payroll.approveTime
  // C-5 补写 APPROVE / REJECT 留痕
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: approved ? 'APPROVE' : 'REJECT',
    operator,
    fromStatus: 'PENDING_APPROVAL',
    toStatus: payroll.status,
    reason: approved ? null : payroll.approveRemark
  })
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/** 发布 / 再发布共用的落库：记新的发布人与发布时间，并清空异议信息（再发布场景） */
function applyPublish(payroll, operator) {
  const fromStatus = payroll.status
  payroll.status = 'PUBLISHED'
  payroll.publisherId = operator.id
  payroll.publisherName = operator.real_name
  payroll.publishTime = formatDateTime(new Date())
  payroll.updateTime = payroll.publishTime
  payroll.objectionReason = null
  payroll.objectionTime = null
  // C-2：来源 OBJECTED 记 REPUBLISH（异议历史永久留痕），来源 APPROVED 记 PUBLISH
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: fromStatus === 'OBJECTED' ? 'REPUBLISH' : 'PUBLISH',
    operator,
    fromStatus,
    toStatus: 'PUBLISHED'
  })
}

/**
 * 批量发布 / 再发布（C-2）：
 * - ids 路径：允许来源 APPROVED（首发）或 OBJECTED（再发布）；遇非法来源（含 CONFIRMED）显式报错（不静默 skipped）；
 *   PAID 为终态冻结，统一回 9413。
 * - month(+stationId) 路径：仅 APPROVED 可发，避免误批再发布，其余计入 skipped。
 */
export function publishPayrolls({ ids, month, stationId }, operator) {
  ensure()
  const isIdsMode = Array.isArray(ids) && ids.length > 0
  if (isIdsMode) {
    const targets = ids.map((id) => state.payrolls.find((p) => p.id === Number(id))).filter(Boolean)
    const archived = targets.find((p) => p.status === 'PAID')
    if (archived) return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
    const illegal = targets.find((p) => p.status !== 'APPROVED' && p.status !== 'OBJECTED')
    if (illegal) {
      return {
        code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
        message: `当前状态（${PAYROLL_STATUS_LABEL[illegal.status]}）不允许发布`
      }
    }
    const published = []
    targets.forEach((payroll) => {
      applyPublish(payroll, operator)
      published.push(payroll.id)
    })
    if (published.length) bucket.write(state)
    return { code: 200, data: { published: published.length, skipped: 0, payrollIds: published } }
  }
  const published = []
  const skipped = []
  filterPayrolls({ month, stationId }).forEach((payroll) => {
    if (payroll.status !== 'APPROVED') {
      skipped.push(payroll.id)
      return
    }
    applyPublish(payroll, operator)
    published.push(payroll.id)
  })
  if (published.length) bucket.write(state)
  return { code: 200, data: { published: published.length, skipped: skipped.length, payrollIds: published } }
}

/**
 * 修改人工项金额（C-3）：
 * - 可编辑判据由单一 EDITABLE_STATUS 改为 isItemEditable（新增 PENDING_APPROVAL / OBJECTED）；
 * - 仅允许改 source=MANUAL 项，金额须为数字；
 * - 金额变更事由（reason）必填 2-200，违规回 9412；
 * - **不覆盖** payroll_item.detail（原实现会写成「人工填写」，抹掉规则说明与事由全文，属缺陷）；
 * - PAID 为终态冻结，统一回 9413。
 */
export function updatePayrollItems(id, items, reason, operator = null) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.status === 'PAID') return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
  if (!IS_ITEM_EDITABLE.includes(payroll.status))
    return {
      code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
      message: `当前状态（${PAYROLL_STATUS_LABEL[payroll.status]}）不允许修改金额`
    }
  // 先整体校验（结构类错误优先 400），再校验必填事由（9412），最后才落库，避免半途改动
  for (const item of items) {
    const target = payroll.items.find((i) => i.key === item.key)
    if (!target) return { code: 400, message: `工资单项不存在：${item.key}` }
    if (target.source !== 'MANUAL') return { code: 400, message: `「${target.name}」由规则计算，不可手工修改` }
    if (!Number.isFinite(Number(item.amount))) return { code: 400, message: `「${target.name}」金额须为数字` }
  }
  const reasonText = String(reason == null ? '' : reason).trim()
  if (reasonText.length < 2 || reasonText.length > 200) return { code: FINANCE_CODE.REASON_REQUIRED }
  // 留痕快照须取改动前的白名单值（before 为改前、after 为改后）
  const before = { items: snapshotItems(payroll), ...totalsOf(payroll) }
  for (const item of items) {
    const target = payroll.items.find((i) => i.key === item.key)
    target.amount = Number(item.amount)
  }
  recomputeTotals(payroll)
  payroll.updateTime = formatDateTime(new Date())
  // C-3 ④：每次改动写 ITEM_UPDATE；reason 由服务端写日志，不覆盖 payroll_item.detail
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: 'ITEM_UPDATE',
    operator,
    fromStatus: payroll.status,
    toStatus: payroll.status,
    reason: reasonText,
    before,
    after: { items: snapshotItems(payroll), ...totalsOf(payroll) }
  })
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/**
 * 手工加 / 扣款（I-6）：在目标工资单下新增一行 source=MANUAL 明细（一次性语义，不建员工级长期项）。
 * item_key 由服务端强制生成（MANUAL_ 前缀），加款计入应发、扣款计入扣项，保存后自动重算四项合计。
 */
export function addPayrollItem(id, { itemType, itemName, amount, reason, detail }, operator = null) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.status === 'PAID') return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
  if (!IS_ITEM_EDITABLE.includes(payroll.status))
    return {
      code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
      message: `当前状态（${PAYROLL_STATUS_LABEL[payroll.status]}）不允许加扣款`
    }
  const name = String(itemName == null ? '' : itemName).trim()
  if (name.length < 1 || name.length > 20) return { code: 400, message: '名称长度须为 1-20 字' }
  const value = Number(amount)
  if (!Number.isFinite(value) || value <= 0) return { code: 400, message: '金额须为大于 0 的数字' }
  const reasonText = String(reason == null ? '' : reason).trim()
  if (reasonText.length < 2 || reasonText.length > 200) return { code: FINANCE_CODE.REASON_REQUIRED }
  const key = `MANUAL_${String(payroll.month).replace('-', '')}_${payroll.items.length + 1}`
  if (payroll.items.some((i) => i.key === key)) return { code: FINANCE_CODE.ITEM_KEY_EXISTS }
  const before = { ...totalsOf(payroll) }
  const item = {
    key,
    name,
    type: itemType,
    source: 'MANUAL',
    amount: value,
    detail: detail ? String(detail).trim() : reasonText
  }
  payroll.items.push(item)
  recomputeTotals(payroll)
  payroll.updateTime = formatDateTime(new Date())
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: 'ITEM_ADD',
    operator,
    fromStatus: payroll.status,
    toStatus: payroll.status,
    reason: reasonText,
    before,
    after: { items: [{ itemKey: key, itemType, itemName: name, amount: value }], ...totalsOf(payroll) }
  })
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/** 确认发放归档（I-8）：仅 ADMIN，来源须 CONFIRMED；→ PAID 记 paid_*，随后冻结 */
export function payPayroll(id, operator, remark = null) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.status === 'PAID') return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
  if (payroll.status !== 'CONFIRMED')
    return {
      code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
      message: `当前状态（${PAYROLL_STATUS_LABEL[payroll.status]}）不允许确认发放`
    }
  payroll.status = 'PAID'
  payroll.paidById = operator.id
  payroll.paidByName = operator.real_name
  payroll.paidTime = formatDateTime(new Date())
  payroll.updateTime = payroll.paidTime
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: 'PAY',
    operator,
    fromStatus: 'CONFIRMED',
    toStatus: 'PAID',
    reason: remark
  })
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/**
 * 工资单操作留痕（I-7）：按 payroll_id 过滤、按 time 倒序。
 * 字段裁剪由服务端强制：ADMIN 返回全量；非 ADMIN 仅 action / time / reason / toStatus
 * （前端隐藏而后端照返 = 越权信息泄漏，禁止）。
 */
export function payrollLogs(id, user) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (user.role !== 'ADMIN') {
    if (payroll.employeeId !== user.id) return { code: FINANCE_CODE.PAYROLL_NO_PERMISSION }
    if (!EMPLOYEE_VISIBLE_STATUS.includes(payroll.status))
      return {
        code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
        message: '工资单尚未发布或正在重新核定中，暂不可查看'
      }
  }
  const rows = state.logs
    .filter((log) => log.payrollId === Number(id))
    .slice()
    .sort((a, b) => (a.time < b.time ? 1 : a.time > b.time ? -1 : b.id - a.id))
  const data =
    user.role === 'ADMIN'
      ? rows
      : rows.map((log) => ({
          action: log.action,
          time: log.time,
          reason: log.reason,
          toStatus: log.toStatus
        }))
  return { code: 200, data }
}

/** 员工确认：只认本人 + 已发布（越权与状态口径与详情一致；PAID 归档态统一回 9413） */
export function confirmPayroll(id, user) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.employeeId !== user.id) return { code: FINANCE_CODE.PAYROLL_NO_PERMISSION }
  if (payroll.status === 'PAID') return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
  if (payroll.status === 'CONFIRMED')
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '工资单已确认，无需重复确认' }
  if (payroll.status !== 'PUBLISHED')
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '工资单尚未发布，暂不可确认' }
  payroll.status = 'CONFIRMED'
  payroll.confirmTime = formatDateTime(new Date())
  payroll.updateTime = payroll.confirmTime
  // C-4 补写 CONFIRM 留痕（操作人为员工本人）
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: 'CONFIRM',
    operator: user,
    fromStatus: 'PUBLISHED',
    toStatus: 'CONFIRMED'
  })
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/**
 * 员工提异议（C-1）：记录异议原因并把单据落 **OBJECTED（异议退回）**，由管理员重新核定后再次发布。
 * 为什么不再落 PENDING_APPROVAL：原口径与该态 isEditable=false 相矛盾，会令「退回后可改再发布」不可达；
 * OBJECTED 与管理员驳回（REJECTED）可区分「谁退的」（U-02）。清空口径沿用：清 confirm / publish / publisher。
 */
export function objectPayroll(id, reason, user) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.employeeId !== user.id) return { code: FINANCE_CODE.PAYROLL_NO_PERMISSION }
  if (payroll.status === 'PAID') return { code: FINANCE_CODE.PAYROLL_ARCHIVED }
  if (payroll.status !== 'PUBLISHED')
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '仅已发布的工资单可提异议' }
  payroll.status = 'OBJECTED'
  payroll.objectionReason = reason
  payroll.objectionTime = formatDateTime(new Date())
  payroll.confirmTime = null
  payroll.publishTime = null
  payroll.publisherId = null
  payroll.publisherName = null
  payroll.updateTime = payroll.objectionTime
  pushLog({
    payrollId: payroll.id,
    employeeId: payroll.employeeId,
    month: payroll.month,
    action: 'OBJECTION',
    operator: user,
    fromStatus: 'PUBLISHED',
    toStatus: 'OBJECTED',
    reason
  })
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/** 员工端未读提示用：已发布但未确认的工资单条数（H5 首页角标） */
export const pendingConfirmCount = (employeeId) =>
  ensure().payrolls.filter((p) => p.employeeId === Number(employeeId) && p.status === 'PUBLISHED').length

/* ==================== 驿站算薪配置（I-1 / I-2 / I-3 / I-9） ==================== */

const round2 = (value) => Math.round((Number(value) || 0) * 100) / 100

/** 算薪日钳位：合约「当月无该日时取当月最后一天」（31 → 4/30，30 → 2/28） */
function clampDay(day, month) {
  const [year, mon] = String(month).split('-').map(Number)
  const lastDay = new Date(year, mon, 0).getDate()
  return Math.min(Math.max(1, Number(day) || 1), lastDay)
}

/** 配置变更留痕白名单：只写 5 个可变更字段，避免把内部字段带进审计 */
function settingWhitelist(setting) {
  return {
    enabled: setting.enabled,
    payrollDay: setting.payrollDay,
    payrollTime: setting.payrollTime,
    notifyEnabled: setting.notifyEnabled,
    remark: setting.remark
  }
}

function toSettingVO(station, setting) {
  // 未配置时不臆造默认值，交给前端按 9406 呈现「尚未配置」并给默认表单值
  return {
    stationId: station.id,
    stationName: station.station_name,
    enabled: setting ? setting.enabled : 0,
    payrollDay: setting ? setting.payrollDay : null,
    payrollTime: setting ? setting.payrollTime : null,
    notifyEnabled: setting ? setting.notifyEnabled : null,
    remark: setting ? setting.remark : null,
    updateTime: setting ? setting.updateTime : null
  }
}

/** 驿站列表 + 各站配置（I-1）：未配置的驿站同样返回，靠 updateTime/payrollDay 为空区分 */
export function listSettings({ stationId, enabled, pageNum, pageSize }) {
  ensure()
  let stations = activeStations()
  if (stationId != null && stationId !== '') stations = stations.filter((s) => s.id === Number(stationId))
  let rows = stations.map((station) =>
    toSettingVO(
      station,
      state.settings.find((s) => s.stationId === station.id)
    )
  )
  if (enabled != null && enabled !== '') rows = rows.filter((row) => Number(row.enabled) === Number(enabled))
  return paginate(rows, pageNum, pageSize)
}

/** 单驿站配置（I-2）：驿站不存在 4001；驿站存在但尚无配置 9406（可判定分支，交前端按默认值呈现） */
export function getSetting(stationId) {
  ensure()
  const station = findStationById(stationId)
  if (!station) return { code: STATION_CODE.NOT_EXISTS }
  const setting = state.settings.find((s) => s.stationId === Number(stationId))
  if (!setting) return { code: FINANCE_CODE.SETTING_NOT_CONFIGURED }
  return { code: 200, data: toSettingVO(station, setting) }
}

/**
 * 保存驿站算薪配置（I-3）：每次保存同事务追加一条 station_payroll_setting_log。
 * 动作判定：首次创建 → CREATE；enabled 0→1 → ENABLE（M-9 可追溯）；1→0 → DISABLE；其余 → UPDATE。
 */
export function saveSetting(stationId, body, operator) {
  ensure()
  const station = findStationById(stationId)
  if (!station) return { code: STATION_CODE.NOT_EXISTS }
  const existing = state.settings.find((s) => s.stationId === Number(stationId))
  const before = existing ? settingWhitelist(existing) : null
  const next = {
    stationId: Number(stationId),
    enabled: Number(body.enabled) === 1 ? 1 : 0,
    payrollDay: Number(body.payrollDay),
    payrollTime: String(body.payrollTime),
    notifyEnabled: Number(body.notifyEnabled) === 1 ? 1 : 0,
    remark: body.remark == null || String(body.remark).trim() === '' ? null : String(body.remark).trim(),
    updateTime: formatDateTime(new Date())
  }
  let action = 'UPDATE'
  if (!existing) action = 'CREATE'
  else if (existing.enabled === 0 && next.enabled === 1) action = 'ENABLE'
  else if (existing.enabled === 1 && next.enabled === 0) action = 'DISABLE'

  if (existing) Object.assign(existing, next)
  else state.settings.push(next)

  state.settingLogs.push({
    id: (state.seq.settingLog += 1),
    stationId: Number(stationId),
    action,
    operatorId: operator ? operator.id : null,
    operatorName: operator ? operator.real_name : '系统',
    operatorRole: operator ? operator.role : 'SYSTEM',
    time: next.updateTime,
    before,
    after: settingWhitelist(next),
    remark: next.remark
  })
  bucket.write(state)
  return { code: 200, data: toSettingVO(station, next) }
}

/** 配置变更历史（I-9）：按 time 倒序分页；不在 I-1/I-2 出参内嵌（与 I-7 单据留痕同构） */
export function listSettingLogs(stationId, { pageNum, pageSize }) {
  ensure()
  const station = findStationById(stationId)
  if (!station) return { code: STATION_CODE.NOT_EXISTS }
  const rows = state.settingLogs
    .filter((log) => log.stationId === Number(stationId))
    .slice()
    .sort((a, b) => (a.time < b.time ? 1 : a.time > b.time ? -1 : b.id - a.id))
  return { code: 200, data: paginate(rows, pageNum, pageSize) }
}

/* ==================== 自动算薪运行（I-4 / I-5） ==================== */

function toRunVO(run) {
  return { ...run, stationName: stationName(run.stationId) }
}

/** 运行记录列表（I-5）：stationId / month / status / triggerType 筛选 + 分页 */
export function listRuns({ stationId, month, status, triggerType, pageNum, pageSize }) {
  ensure()
  let rows = state.runs
  if (stationId != null && stationId !== '') rows = rows.filter((r) => r.stationId === Number(stationId))
  if (month) rows = rows.filter((r) => r.targetMonth === month)
  if (status) rows = rows.filter((r) => r.status === status)
  if (triggerType) rows = rows.filter((r) => r.triggerType === triggerType)
  const sorted = rows.slice().sort((a, b) => (a.startTime < b.startTime ? 1 : a.startTime > b.startTime ? -1 : b.id - a.id))
  return paginate(sorted.map(toRunVO), pageNum, pageSize)
}

function pushRun({ stationId, targetMonth, triggerType, status, operator, dueAt = null }) {
  const now = formatDateTime(new Date())
  const run = {
    id: (state.seq.run += 1),
    stationId: Number(stationId),
    targetMonth,
    attemptDate: formatDate(new Date()),
    triggerType,
    dueAt: dueAt || now,
    status,
    skipCode: null,
    skipReason: null,
    generatedCount: null,
    failReason: null,
    operatorName: operator ? operator.real_name : '系统',
    startTime: now,
    finishTime: status === 'RUNNING' ? null : now
  }
  state.runs.push(run)
  return run
}

/**
 * 手工触发算薪（I-4）：无 force 参数。
 * 闸门顺序：驿站存在 → 已配置且启用（9405/9415）→ 占位（SUCCESS/SKIPPED/RUNNING → 9410）→ 当日已尝试（9410）。
 * 生成命中 9405 映射为 SKIPPED 结果（非错误码），与契约 §4.12.18 一致。
 */
export function triggerRun({ stationId, month }, operator) {
  ensure()
  const station = findStationById(stationId)
  if (!station) return { code: STATION_CODE.NOT_EXISTS }
  const setting = state.settings.find((s) => s.stationId === Number(stationId))
  if (!setting) {
    const run = pushRun({ stationId, targetMonth: month, triggerType: 'MANUAL', status: 'SKIPPED', operator })
    run.skipCode = 'CONFIG_INVALID'
    run.skipReason = '算薪配置非法（该驿站尚未配置算薪设置）'
    bucket.write(state)
    return {
      code: 200,
      data: {
        runId: run.id,
        status: 'SKIPPED',
        skipCode: run.skipCode,
        skipReason: run.skipReason,
        generatedCount: 0,
        submittedCount: 0,
        skippedCount: 0
      }
    }
  }
  if (setting.enabled !== 1) return { code: FINANCE_CODE.SETTING_DISABLED }
  const conflict = state.runs.find(
    (r) =>
      r.stationId === Number(stationId) &&
      r.targetMonth === month &&
      ['RUNNING', 'SUCCESS', 'SKIPPED'].includes(r.status)
  )
  if (conflict) return { code: FINANCE_CODE.RUN_CONFLICT }
  const today = formatDate(new Date())
  const attemptedToday = state.runs.find(
    (r) => r.stationId === Number(stationId) && r.targetMonth === month && r.attemptDate === today
  )
  if (attemptedToday) return { code: FINANCE_CODE.RUN_CONFLICT }

  const [year, mon] = String(month).split('-').map(Number)
  const dueAt = formatDateTime(new Date(year, mon - 1, clampDay(setting.payrollDay, month), ...String(setting.payrollTime).split(':').map(Number), 0))
  const run = pushRun({ stationId, targetMonth: month, triggerType: 'MANUAL', status: 'RUNNING', operator, dueAt })
  const gen = generatePayrolls({ month, stationId: Number(stationId), trigger: 'AUTO' }, operator)
  if (gen.code === FINANCE_CODE.PAYROLL_GENERATED) {
    run.status = 'SKIPPED'
    run.skipCode = 'BLOCKED_9405'
    run.skipReason = '该账期已存在非可覆盖工资单'
    run.finishTime = formatDateTime(new Date())
    bucket.write(state)
    return {
      code: 200,
      data: {
        runId: run.id,
        status: 'SKIPPED',
        skipCode: run.skipCode,
        skipReason: run.skipReason,
        generatedCount: 0,
        submittedCount: 0,
        skippedCount: 0
      }
    }
  }
  if (gen.code !== 200) {
    run.status = 'FAILED'
    run.failReason = gen.message || '生成失败'
    run.finishTime = formatDateTime(new Date())
    bucket.write(state)
    return { code: 200, data: { runId: run.id, status: 'FAILED', generatedCount: 0, submittedCount: 0, skippedCount: 0 } }
  }
  // 自动算薪生成后逐单自动提交（v1.4/B4a，Q6）：失败保持 DRAFT，计入 submittedCount / skippedCount
  let submittedCount = 0
  let skippedCount = 0
  for (const payrollId of gen.data.payrollIds) {
    const submitted = submitPayrolls([payrollId], operator)
    if (submitted.code === 200) submittedCount += 1
    else skippedCount += 1
  }
  run.status = 'SUCCESS'
  run.generatedCount = gen.data.created
  run.finishTime = formatDateTime(new Date())
  bucket.write(state)
  return {
    code: 200,
    data: {
      runId: run.id,
      status: 'SUCCESS',
      generatedCount: gen.data.created,
      submittedCount,
      skippedCount
    }
  }
}

/* ==================== 手工调整对账（I-10） ==================== */

function emptySummaryRow(employeeId, employeeName) {
  return {
    employeeId,
    employeeName,
    additionCount: 0,
    additionTotal: 0,
    deductionCount: 0,
    deductionTotal: 0,
    netImpact: 0,
    addCount: 0,
    updateCount: 0,
    updateIncreaseTotal: 0,
    updateDecreaseTotal: 0,
    totalNetImpact: 0
  }
}

/**
 * 手工调整对账汇总（I-10）：真源为 payroll_log 的 employee_id + month 冗余列
 * （generate 覆盖重建会物理删除旧单，按 payroll_id 关联会漏「已删单」上的加扣款留痕）。
 * ITEM_ADD 计加/扣款与净影响；ITEM_UPDATE 按 itemKey 配对前后金额，折算对实发的正负影响。
 */
export function manualAdjustmentSummary({ month, stationId }) {
  ensure()
  const logs = state.logs.filter(
    (log) => log.month === month && (log.action === 'ITEM_ADD' || log.action === 'ITEM_UPDATE')
  )
  const rows = new Map()
  const rowOf = (employeeId) => {
    if (!rows.has(employeeId)) {
      const employee = findEmployeeById(employeeId)
      rows.set(employeeId, emptySummaryRow(employeeId, employee ? employee.real_name : `员工${employeeId}`))
    }
    return rows.get(employeeId)
  }

  logs.forEach((log) => {
    const row = rowOf(log.employeeId)
    if (log.action === 'ITEM_ADD') {
      row.addCount += 1
      const items = (log.after && log.after.items) || []
      items.forEach((item) => {
        if (item.itemType === 'ADDITION') {
          row.additionCount += 1
          row.additionTotal += Number(item.amount) || 0
        } else if (item.itemType === 'DEDUCTION') {
          row.deductionCount += 1
          row.deductionTotal += Number(item.amount) || 0
        }
      })
      return
    }
    // ITEM_UPDATE：按 itemKey 配对，Δ = after − before，再折算对实发的方向（加款项 +Δ、扣款项 −Δ）
    row.updateCount += 1
    const beforeItems = (log.before && log.before.items) || []
    const afterItems = (log.after && log.after.items) || []
    afterItems.forEach((after) => {
      const before = beforeItems.find((b) => b.itemKey === after.itemKey)
      if (!before) return
      const delta = (Number(after.amount) || 0) - (Number(before.amount) || 0)
      const signed = after.itemType === 'ADDITION' ? delta : -delta
      if (signed >= 0) row.updateIncreaseTotal += signed
      else row.updateDecreaseTotal += -signed
    })
  })

  const list = Array.from(rows.values())
    .filter((row) => {
      if (stationId == null || stationId === '') return true
      const employee = findEmployeeById(row.employeeId)
      return employee && employee.station_id === Number(stationId)
    })
    .map((row) => {
      row.netImpact = round2(row.additionTotal - row.deductionTotal)
      row.totalNetImpact = round2(row.netImpact + row.updateIncreaseTotal - row.updateDecreaseTotal)
      row.additionTotal = round2(row.additionTotal)
      row.deductionTotal = round2(row.deductionTotal)
      row.updateIncreaseTotal = round2(row.updateIncreaseTotal)
      row.updateDecreaseTotal = round2(row.updateDecreaseTotal)
      return row
    })
    .sort((a, b) => a.employeeId - b.employeeId)

  const total = emptySummaryRow(null, '合计')
  list.forEach((row) => {
    total.additionCount += row.additionCount
    total.additionTotal = round2(total.additionTotal + row.additionTotal)
    total.deductionCount += row.deductionCount
    total.deductionTotal = round2(total.deductionTotal + row.deductionTotal)
    total.netImpact = round2(total.netImpact + row.netImpact)
    total.addCount += row.addCount
    total.updateCount += row.updateCount
    total.updateIncreaseTotal = round2(total.updateIncreaseTotal + row.updateIncreaseTotal)
    total.updateDecreaseTotal = round2(total.updateDecreaseTotal + row.updateDecreaseTotal)
    total.totalNetImpact = round2(total.totalNetImpact + row.totalNetImpact)
  })

  return { code: 200, data: { month, stationId: stationId == null ? null : Number(stationId), list, total } }
}
