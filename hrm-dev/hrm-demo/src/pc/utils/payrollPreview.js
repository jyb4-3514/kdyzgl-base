import { getAttendanceRecords, getSchedules } from '../api/attendance.js'
import { getLeaveList, getLeaveSettings } from '../api/leave.js'
import { LEAVE_TYPE } from '@/shared/constants/dict.js'

/**
 * 工资单试算（需求9 · 积木式规则编辑器的「试算预览」）
 *
 * 为什么算在前端：契约没有试算接口，而「改完规则立刻看到某人会拿多少钱」是老板配规则时的核心反馈。
 * POST /finance/payrolls/generate 会真实落库，且同月已有「已提交审核 / 已发布」的单据时整批拒绝（9405），
 * 拿它当试算会污染数据、且在演示月份上必然失败，因此这里按 financeStore 的算薪内核逐条镜像。
 *
 * 保真约定：下列三个函数与 shared/mock/financeStore.js 的 resolveItem 分支一一对应
 * （同 source 分类、同 params 语义、同取数口径），任何一侧改了算薪口径，另一侧必须同步。
 * TODO(扩展): 后端提供不落库的试算接口（如 POST /finance/payrolls/preview）后删除本文件，改为调用接口。
 */

/** 账期起止（与 mock/util.js 的 monthRange 同口径），用于向考勤接口要数 */
function monthRange(month) {
  const [year, mon] = String(month).split('-').map(Number)
  const lastDay = new Date(year, mon, 0).getDate()
  return { startDate: `${month}-01`, endDate: `${month}-${String(lastDay).padStart(2, '0')}` }
}

/** 某日期所在周的周一（与 mock 的 mondayOf 同口径：JS 的 0 是周日，故 +6 取模） */
function mondayOf(dateText) {
  const date = new Date(`${dateText}T00:00:00`)
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7))
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const addDays = (dateText, days) => {
  const date = new Date(`${dateText}T00:00:00`)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/**
 * 拉全某员工某月的打卡记录
 * 为什么要翻页：单日多时段（双时段驿站 × 上/下班卡）可达 4 条，一个月会超过单页 100 条上限，
 * 只取第一页会让迟到/早退次数少算，试算值对不上真实工资单。
 */
async function fetchMonthRecords(employeeId, startDate, endDate) {
  const rows = []
  for (let pageNum = 1; pageNum <= 5; pageNum += 1) {
    const page = await getAttendanceRecords({ employeeId, startDate, endDate, pageNum, pageSize: 100 })
    rows.push(...(page.list || []))
    if (rows.length >= (page.total || 0) || !(page.list || []).length) break
  }
  return rows
}

/**
 * 拉全某员工某月的已批请假单（M11 D3）
 * 为什么也翻页：跨月单整单返回，一个月可能有多条；只取第一页会让缺勤少扣。
 * 日期筛选取区间交集，因此跨月单在两个月都会被返回，交集由 leaveHalfUnits 按天裁剪。
 */
async function fetchApprovedLeaves(employeeId, startDate, endDate) {
  const rows = []
  for (let pageNum = 1; pageNum <= 3; pageNum += 1) {
    const page = await getLeaveList({ employeeId, status: 'APPROVED', startDate, endDate, pageNum, pageSize: 100 })
    rows.push(...(page.list || []))
    if (rows.length >= (page.total || 0) || !(page.list || []).length) break
  }
  return rows
}

/**
 * 请假单落在账期内的半天单元（按天裁剪，与 Mock 侧 approvedLeaveDays 的单元规则逐字同版）：
 * 起始日为下午则首日没有上午单元；结束日为上午则末日没有下午单元。
 */
function leaveHalfUnits(leave, startDate, endDate) {
  const units = []
  for (let day = leave.startDate; day <= leave.endDate; day = addDays(day, 1)) {
    if (day < startDate || day > endDate) continue
    if (!(day === leave.startDate && leave.startPeriod === 'PM')) units.push(day)
    if (!(day === leave.endDate && leave.endPeriod === 'AM')) units.push(day)
  }
  return units
}

/** 已批请假天数：SCHEDULED 假别只算当天有排班的日子（轮休日不计薪），NATURAL 假别与排班无关 */
function approvedLeaveDays(leaves, startDate, endDate, scheduledDates) {
  return leaves.reduce((sum, leave) => {
    const units = leaveHalfUnits(leave, startDate, endDate)
    if (!units.length) return sum
    const type = LEAVE_TYPE[leave.leaveType]
    if (!type || type.countMode === 'NATURAL') return sum + units.length / 2
    return sum + units.filter((day) => scheduledDates.has(day)).length / 2
  }, 0)
}

/**
 * 考勤口径（与 financeStore 调用的 employeeAttendanceStat 一致）：
 * 迟到 = 有效上班卡中 LATE；早退 = 有效下班卡中 EARLY_LEAVE；异常卡 = ABNORMAL；请假 = 已批请假天数；
 * 缺勤 = 当月排班天数 − 有有效上班卡的天数 −（开关关闭时）已批请假天数。
 * 请假扣款开关（D5）语义：true = 请假按缺勤计（扣款），此时不扣减；false = 默认，请假不扣。
 * 改动本函数必须同步 shared/mock/attendanceStore.js 的 employeeAttendanceStat（该文件头的保真约定）。
 */
export async function loadAttendanceStat({ employeeId, stationId, month, needAbsent }) {
  const { startDate, endDate } = monthRange(month)
  const rows = await fetchMonthRecords(employeeId, startDate, endDate)
  const valid = rows.filter((row) => row.status !== 'ABNORMAL')
  const attendedDates = new Set(valid.filter((row) => row.checkType === 'ON').map((row) => row.workDate))
  const stat = {
    lateCount: valid.filter((row) => row.checkType === 'ON' && row.status === 'LATE').length,
    earlyLeaveCount: valid.filter((row) => row.checkType === 'OFF' && row.status === 'EARLY_LEAVE').length,
    abnormalCount: rows.filter((row) => row.status === 'ABNORMAL').length,
    attendedDays: attendedDates.size,
    scheduledDays: 0,
    leaveCount: 0,
    absentCount: 0
  }
  // 缺勤需要排班真源，只有规则里真的用到「缺勤」指标时才多打这几周请求
  if (!needAbsent || !stationId) return stat

  const scheduledDates = new Set()
  let cursor = mondayOf(startDate)
  while (cursor <= endDate) {
    const week = await getSchedules({ stationId, weekStart: cursor })
    const row = (week.employees || []).find((item) => item.employeeId === Number(employeeId))
    ;(row ? row.days : []).forEach((day) => {
      if (day.shiftId != null && day.workDate >= startDate && day.workDate <= endDate) scheduledDates.add(day.workDate)
    })
    cursor = addDays(cursor, 7)
  }
  stat.scheduledDays = scheduledDates.size
  const [leaves, settings] = await Promise.all([
    fetchApprovedLeaves(employeeId, startDate, endDate),
    getLeaveSettings()
  ])
  stat.leaveCount = approvedLeaveDays(leaves, startDate, endDate, scheduledDates)
  const excludeLeaveDays = settings.leaveDeductEnabled ? 0 : stat.leaveCount
  stat.absentCount = Math.max(0, scheduledDates.size - attendedDates.size - excludeLeaveDays)
  return stat
}

/* ==================== 规则项金额（镜像 financeStore.resolveItem） ==================== */

const SALARY_FIELD_LABEL = { basicSalary: '基本工资', postSalary: '岗位工资', performanceBase: '绩效基数' }
/** 与 dict.ATTENDANCE_METRIC / financeStore.ATTENDANCE_FIELD 三处同版（M11 新增 LEAVE） */
const ATTENDANCE_FIELD = {
  LATE: 'lateCount',
  EARLY_LEAVE: 'earlyLeaveCount',
  ABSENT: 'absentCount',
  ABNORMAL: 'abnormalCount',
  LEAVE: 'leaveCount'
}
const ATTENDANCE_LABEL = { LATE: '迟到', EARLY_LEAVE: '早退', ABSENT: '缺勤', ABNORMAL: '异常卡', LEAVE: '请假' }

function fixedAmount(params, ctx) {
  if (!ctx.salary) return { amount: 0, detail: '未设置定薪档案，按 0 计' }
  if (params.field === 'allowances') {
    const list = ctx.salary.allowances || []
    if (!params.allowanceKey)
      return { amount: ctx.salary.allowancesTotal || 0, detail: `取人事定薪项：津贴合计（${list.length} 项）` }
    const hit = list.find((item) => item.key === params.allowanceKey)
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

function resolveItem(item, ctx) {
  const params = item.params || {}
  if (item.source === 'FIXED') return fixedAmount(params, ctx)
  if (item.source === 'ATTENDANCE') return attendanceAmount(params, ctx)
  if (item.source === 'KPI') return kpiAmount(params, ctx)
  if (item.source === 'MANUAL')
    return { amount: Number(params.defaultValue) || 0, detail: '人工填写项，草稿状态下可调整' }
  return { amount: 0, detail: '未知来源，按 0 计' }
}

/** 规则项 → 明细与合计（镜像 financeStore.buildItems + buildPayroll 的金额口径） */
export function buildPreview(ruleItems, ctx) {
  const items = ruleItems
    .filter((item) => item.enabled !== 0)
    .slice()
    .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0))
    .map((item) => {
      const { amount, detail } = resolveItem(item, ctx)
      return { key: item.key, name: item.name, type: item.type, source: item.source, amount, detail }
    })
  const additionTotal = items
    .filter((item) => item.type === 'ADDITION')
    .reduce((sum, item) => sum + Number(item.amount), 0)
  const deductionTotal = items
    .filter((item) => item.type === 'DEDUCTION')
    .reduce((sum, item) => sum + Number(item.amount), 0)
  return { items, additionTotal, deductionTotal, grossAmount: additionTotal, netAmount: additionTotal - deductionTotal }
}
