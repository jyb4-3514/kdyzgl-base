import { activeEmployees, db, findEmployeeById, stationName } from './db.js'
import { FINANCE_CODE } from '../constants/errorCode.js'
import { createPersistBucket } from './persist.js'
import { currentMonth, formatDateTime, monthRange, monthShift, paginate, parseDate } from './util.js'
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
 * - MANUAL     人工填写（草稿/驳回状态下可改）
 * 因此「换一项津贴、改一次扣款标准、调整绩效比例」都只是改配置，不动代码。
 *
 * 工资单状态机（六态完整可演示）：
 *   DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──publish──▶ PUBLISHED ──confirm──▶ CONFIRMED
 *                          └──reject──▶ REJECTED ──submit──▶ PENDING_APPROVAL
 *   PUBLISHED ──objection（员工提异议）──▶ PENDING_APPROVAL（回到审核，清空确认时间）
 * REJECTED 视同可编辑草稿（items 可改），改完重新提交即可，不必额外多一个状态。
 *
 * 越权红线：员工（STAFF）只能查看自己的工资单，且仅 PUBLISHED / CONFIRMED 两个状态可见；
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
  CONFIRMED: '已确认'
}
export const PAYROLL_BILL_TYPE_LABEL = { MONTHLY: '月度工资单', SETTLEMENT: '离职结算单' }

/** 员工可见状态：未发布前工资单不能让本人看到（口径写在一处，列表与详情共用） */
const EMPLOYEE_VISIBLE_STATUS = ['PUBLISHED', 'CONFIRMED']
/** 允许修改 MANUAL 规则项的状态：仅草稿与已驳回 */
const EDITABLE_STATUS = ['DRAFT', 'REJECTED']

const PAYROLL_ACTIONS = {
  DRAFT: ['submit'],
  REJECTED: ['submit'],
  PENDING_APPROVAL: ['approve', 'reject'],
  APPROVED: ['publish'],
  PUBLISHED: ['confirm', 'objection'],
  CONFIRMED: []
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
 * 权重/金额只是「配置的初始值」，老板端可随时改；算薪代码里没有任何一项的专属公式。
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
    offboardingId,
    createTime: now,
    updateTime: now
  }
}

/* ==================== 种子 ==================== */

/** 上期账期的状态分布：覆盖已确认 / 已发布 / 已驳回 / 草稿四种，供老板端与员工端同时演示 */
const PREV_MONTH_PUBLISHED = [8, 9, 10]
const PREV_MONTH_REJECTED = [11]
const PREV_MONTH_DRAFT = [12]

function prevMonthStatusOf(employeeId) {
  if (PREV_MONTH_PUBLISHED.includes(employeeId)) return 'PUBLISHED'
  if (PREV_MONTH_REJECTED.includes(employeeId)) return 'REJECTED'
  if (PREV_MONTH_DRAFT.includes(employeeId)) return 'DRAFT'
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

/** 按状态补全流转时间与审核意见：时间线自洽（未审核的单子不会有审批时间） */
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
  payroll.publisherId = admin.id
  payroll.publisherName = admin.real_name
  payroll.publishTime = step(2)
  payroll.updateTime = payroll.publishTime
  if (status === 'CONFIRMED') {
    payroll.confirmTime = step(3)
    payroll.updateTime = payroll.confirmTime
  }
}

function buildSeed() {
  const admin = db.employees.find((e) => e.role === 'ADMIN') || db.employees[0]
  const rules = [
    {
      id: 1,
      ruleName: '标准计薪规则',
      remark: '覆盖基本工资、岗位工资、津贴、绩效、考勤奖惩与人工调整，老板端可自行增删改',
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

  const seed = { seq: { rule: rules.length, payroll: 0 }, rules, payrolls: [] }
  state = seed

  const currentM = currentMonth()
  const prevM = monthShift(currentM, -1)
  const prevBase = at(monthRange(prevM).endDate, 18)
  const curBase = at(monthRange(currentM).startDate, 9)

  activeEmployees().forEach((employee) => {
    const prev = buildPayroll({ employee, month: prevM, rule: rules[0], status: prevMonthStatusOf(employee.id) })
    applyStatusTimeline(prev, prev.status, prevBase, admin)
    seed.payrolls.push(prev)

    const cur = buildPayroll({ employee, month: currentM, rule: rules[0], status: currentMonthStatusOf(employee) })
    applyStatusTimeline(cur, cur.status, curBase, admin)
    seed.payrolls.push(cur)
  })
  return seed
}

function ensure() {
  if (state) return state
  state = bucket.read() || buildSeed()
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
 * 幂等口径：同月同员工同类型已存在草稿/驳回单 → 覆盖重建；已提交审核或已发布 → 整批拒绝（9405），
 * 避免把老板已经审过、员工已经看过的工资单悄悄改掉。
 */
export function generatePayrolls({ month, stationId, deptId, employeeIds, ruleId }) {
  ensure()
  const rule = activeRule(ruleId)
  if (!rule) return { code: FINANCE_CODE.RULE_NOT_EXISTS }
  const blocked = state.payrolls.find(
    (p) => p.month === month && p.billType === 'MONTHLY' && !EDITABLE_STATUS.includes(p.status)
  )
  if (blocked) {
    return {
      code: FINANCE_CODE.PAYROLL_GENERATED,
      message: `该月工资单已提交审核或已发布（${blocked.payrollNo}），不可重复生成`
    }
  }
  const idFilter = Array.isArray(employeeIds) && employeeIds.length ? new Set(employeeIds.map(Number)) : null
  const ruleEntity = state.rules.find((r) => r.id === rule.id)
  const employees = activeEmployees().filter((e) => {
    if (e.status !== 1) return false
    if (idFilter && !idFilter.has(e.id)) return false
    if (stationId != null && stationId !== '' && e.station_id !== Number(stationId)) return false
    if (deptId != null && deptId !== '' && e.dept_id !== Number(deptId)) return false
    return true
  })

  const created = []
  employees.forEach((employee) => {
    const index = state.payrolls.findIndex(
      (p) => p.employeeId === employee.id && p.month === month && p.billType === 'MONTHLY'
    )
    if (index >= 0) state.payrolls.splice(index, 1)
    const payroll = buildPayroll({ employee, month, rule: ruleEntity, status: 'DRAFT' })
    state.payrolls.push(payroll)
    created.push(payroll.id)
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

/** 工资单列表（仅老板）：附各状态计数，页面标签页可直接用，不必再拉全量自己数 */
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
      (p) => p.employeeId === Number(employeeId) && p.month === month && !EDITABLE_STATUS.includes(p.status)
    ) || null
  )
}

/** 我的工资单：只认登录身份，且只返回已发布 / 已确认（未发布不给本人看） */
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
      return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '工资单尚未发布，暂不可查看' }
  }
  return { code: 200, data: toPayrollVO(payroll) }
}

/* ==================== 状态流转 ==================== */

/** 状态守卫：读单 → 校验动作在该状态下是否允许，入口统一，避免每个动作各写一遍判断 */
function requireAction(id, action) {
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (!(PAYROLL_ACTIONS[payroll.status] || []).includes(action)) {
    return {
      code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
      message: `当前状态（${PAYROLL_STATUS_LABEL[payroll.status]}）不允许该操作`
    }
  }
  return { code: 200, data: payroll }
}

export function submitPayrolls(ids) {
  ensure()
  const list = Array.isArray(ids) ? ids : []
  if (!list.length) return { code: 400, message: 'ids 须为非空数组' }
  const submitted = []
  for (const id of list) {
    const result = requireAction(id, 'submit')
    if (result.code !== 200) return result
    const payroll = result.data
    payroll.status = 'PENDING_APPROVAL'
    payroll.approveRemark = null
    payroll.objectionReason = null
    payroll.objectionTime = null
    payroll.confirmTime = null
    payroll.updateTime = formatDateTime(new Date())
    submitted.push(payroll.id)
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
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/** 批量发布：显式给 ids 按 ids 发；否则按 month(+stationId) 把全部已通过的单子发出去 */
export function publishPayrolls({ ids, month, stationId }, operator) {
  ensure()
  const targets =
    Array.isArray(ids) && ids.length
      ? ids.map((id) => state.payrolls.find((p) => p.id === Number(id))).filter(Boolean)
      : filterPayrolls({ month, stationId }).filter((p) => p.status === 'APPROVED')
  const published = []
  const skipped = []
  targets.forEach((payroll) => {
    if (payroll.status !== 'APPROVED') {
      skipped.push(payroll.id)
      return
    }
    payroll.status = 'PUBLISHED'
    payroll.publisherId = operator.id
    payroll.publisherName = operator.real_name
    payroll.publishTime = formatDateTime(new Date())
    payroll.updateTime = payroll.publishTime
    published.push(payroll.id)
  })
  if (published.length) bucket.write(state)
  return { code: 200, data: { published: published.length, skipped: skipped.length, payrollIds: published } }
}

/** 修改人工项金额：只有 MANUAL 项可改，且必须处于草稿或已驳回（算薪结果不可手改，否则规则驱动就失去意义） */
export function updatePayrollItems(id, items) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (!EDITABLE_STATUS.includes(payroll.status))
    return {
      code: FINANCE_CODE.PAYROLL_STATUS_INVALID,
      message: `当前状态（${PAYROLL_STATUS_LABEL[payroll.status]}）不允许修改金额`
    }
  for (const item of items) {
    const target = payroll.items.find((i) => i.key === item.key)
    if (!target) return { code: 400, message: `工资单项不存在：${item.key}` }
    if (target.source !== 'MANUAL') return { code: 400, message: `「${target.name}」由规则计算，不可手工修改` }
    if (!Number.isFinite(Number(item.amount))) return { code: 400, message: `「${target.name}」金额须为数字` }
    target.amount = Number(item.amount)
    target.detail = '人工填写'
  }
  const additionTotal = payroll.items.filter((i) => i.type === 'ADDITION').reduce((sum, i) => sum + Number(i.amount), 0)
  const deductionTotal = payroll.items
    .filter((i) => i.type === 'DEDUCTION')
    .reduce((sum, i) => sum + Number(i.amount), 0)
  payroll.additionTotal = additionTotal
  payroll.deductionTotal = deductionTotal
  payroll.grossAmount = additionTotal
  payroll.netAmount = additionTotal - deductionTotal
  payroll.updateTime = formatDateTime(new Date())
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/** 员工确认：只认本人 + 已发布（越权与状态口径与详情一致） */
export function confirmPayroll(id, user) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.employeeId !== user.id) return { code: FINANCE_CODE.PAYROLL_NO_PERMISSION }
  if (payroll.status === 'CONFIRMED')
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '工资单已确认，无需重复确认' }
  if (payroll.status !== 'PUBLISHED')
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '工资单尚未发布，暂不可确认' }
  payroll.status = 'CONFIRMED'
  payroll.confirmTime = formatDateTime(new Date())
  payroll.updateTime = payroll.confirmTime
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/**
 * 员工提异议：记录异议原因并把单据退回「待审核」，由老板重新核定后再次发布。
 * 为什么复用 PENDING_APPROVAL 而不是新增第七态：异议的处理路径与审核完全一致（重新核定 → 发布），
 * 多一个状态只会让前端状态机与后端口径双双膨胀。
 */
export function objectPayroll(id, reason, user) {
  ensure()
  const payroll = state.payrolls.find((p) => p.id === Number(id))
  if (!payroll) return { code: FINANCE_CODE.PAYROLL_NOT_EXISTS }
  if (payroll.employeeId !== user.id) return { code: FINANCE_CODE.PAYROLL_NO_PERMISSION }
  if (payroll.status !== 'PUBLISHED')
    return { code: FINANCE_CODE.PAYROLL_STATUS_INVALID, message: '仅已发布的工资单可提异议' }
  payroll.status = 'PENDING_APPROVAL'
  payroll.objectionReason = reason
  payroll.objectionTime = formatDateTime(new Date())
  payroll.confirmTime = null
  payroll.publishTime = null
  payroll.publisherId = null
  payroll.publisherName = null
  payroll.updateTime = payroll.objectionTime
  bucket.write(state)
  return { code: 200, data: toPayrollVO(payroll) }
}

/** 员工端未读提示用：已发布但未确认的工资单条数（H5 首页角标） */
export const pendingConfirmCount = (employeeId) =>
  ensure().payrolls.filter((p) => p.employeeId === Number(employeeId) && p.status === 'PUBLISHED').length
