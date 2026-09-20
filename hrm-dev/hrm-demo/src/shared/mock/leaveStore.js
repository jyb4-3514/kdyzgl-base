import { activeEmployees, pushNotification, stationName } from './db.js'
import { createPersistBucket } from './persist.js'
import { addDays, formatDate, formatDateTime, mondayOf, paginate, parseDate } from './util.js'
import { CODE, LEAVE_CODE } from '../constants/errorCode.js'
import { HALF_DAY, LEAVE_STATUS, LEAVE_TYPE } from '../constants/dict.js'
import { scheduledDatesOf } from './attendanceStore.js'
import { findLockingPayroll } from './financeStore.js'

/**
 * 请假数据层（M11，设计规范 docs/demo-leave-design.md）
 *
 * 为什么独立成模块：请假含「两级审批状态机 + 半天粒度时长 + 逐日查排班的计薪天数 + 账期锁」四套规则，
 * 与 attendanceStore「打卡事实」的定位不同；沿用补卡（同文件 attendanceStore 的 makeup 段）的
 * 「懒构建 + 种子 + 内存写操作」写法，但状态机与留痕按设计规范 §1.3 的 T1–T9 重写。
 *
 * 持久化：请假是跨刷新要保留的业务数据（提交后刷新不能消失），落 createPersistBucket('leave')；
 * Node 校验环境无 localStorage 时自动回退进程内存态（persist.js 已处理）。
 *
 * 与 attendanceStore 互引（它查已批请假天数算缺勤、这里查排班算计薪天数），
 * 两个方向都只在函数体内调用、无顶层取值，ESM 循环导入按惰性绑定解析，不存在初始化竞态。
 */

/** 单次连续跨度上限（自然天数），超出回 9604 —— 主智能体裁决 Q2：30 天 */
const MAX_LEAVE_DAYS = 30
/** 占用时间段的状态：这三态占额度，终态（REJECTED / CANCELLED / REVOKED）不占 */
const OCCUPIED_STATUS = ['PENDING_STATION', 'PENDING_BOSS', 'APPROVED']
/** 可撤销的状态（申请人自行撤销，从未写考勤标记） */
const CANCELABLE_STATUS = ['PENDING_STATION', 'PENDING_BOSS']
/** 通知类型（dict.NOTIFICATION_TYPE）：5=请假申请，6=请假结果 */
const NOTIFY_APPLY = 5
const NOTIFY_RESULT = 6

const bucket = createPersistBucket('leave')
let state = null

/* ==================== 内存库与种子 ==================== */

function ensure() {
  if (state) return state
  state = bucket.read() || buildSeed()
  return state
}

/** 重建请假数据（与 resetAttendanceStore 同口径，供校验脚本与「重置演示数据」复用） */
export function resetLeaveStore() {
  state = null
  bucket.clear()
}

const persist = () => bucket.write(state)

/** 无可用站长时的降级：STAFF 的单直进终审（Q4），与站长「跳过初审」共用同一条状态判定路径 */
function stationAdminOf(stationId) {
  return (
    activeEmployees().find((e) => e.role === 'STATION_ADMIN' && e.station_id === Number(stationId) && e.status === 1) ||
    null
  )
}

const bossOf = () => activeEmployees().find((e) => e.role === 'ADMIN' && e.status === 1) || null

/* ==================== 半天粒度与时长计算 ==================== */

/**
 * 区间 → 半天单元序列（每天最多 AM / PM 两个），请假时长与重叠判定的最小粒度
 * 规则：起始日为下午则首日没有上午单元；结束日为上午则末日没有下午单元。
 * 由此同日 PM→AM 会得到 0 个单元，属非法组合，由表单校验与服务端校验双重拦截。
 */
function halfUnitsOf(startDate, startPeriod, endDate, endPeriod) {
  const units = []
  const last = parseDate(endDate)
  for (let day = parseDate(startDate); day <= last; day = addDays(day, 1)) {
    const date = formatDate(day)
    if (!(date === startDate && startPeriod === 'PM')) units.push(`${date}#AM`)
    if (!(date === endDate && endPeriod === 'AM')) units.push(`${date}#PM`)
  }
  return units
}

const naturalDaysOf = (form) => halfUnitsOf(form.startDate, form.startPeriod, form.endDate, form.endPeriod).length / 2

/**
 * 计薪天数：SCHEDULED 必须逐日查排班矩阵（排除当天无排班的轮休日），NATURAL 与排班无关。
 * ⚠️ 不得按 attendanceStore 的 REST_CYCLE_DAYS 推算休息日 —— 那只是种子生成规则，不是业务规则；
 * 真实轮休由 schedules 说了算（设计规范 §4.3.2）。
 * 排班是日粒度（只有 workDate，无上下午），因此「请半天」只能表达当天请了半天，无法映射到具体班次时段；
 * 某天有两个班次时请一天假仍按 1 天计。
 */
function countedDaysOf(employeeId, form) {
  if (LEAVE_TYPE[form.leaveType].countMode === 'NATURAL') return naturalDaysOf(form)
  const scheduled = new Set(scheduledDatesOf(employeeId, form.startDate, form.endDate))
  const hit = halfUnitsOf(form.startDate, form.startPeriod, form.endDate, form.endPeriod).filter((token) =>
    scheduled.has(token.slice(0, 10))
  )
  return hit.length / 2
}

/** 排班摘要（进快照留证）：当前区间内有排班的日期排序拼接串 */
const scheduleDigestOf = (employeeId, form) =>
  LEAVE_TYPE[form.leaveType].countMode === 'NATURAL'
    ? ''
    : scheduledDatesOf(employeeId, form.startDate, form.endDate).join(',')

/* ==================== 留痕与通知 ==================== */

function pushLog(leave, { action, operator, time, fromStatus, toStatus, before, after, remark }) {
  state.seq.log += 1
  leave.handleLog.push({
    id: state.seq.log,
    leaveId: leave.id,
    action,
    operatorId: operator ? operator.id : null,
    operatorName: operator ? operator.real_name : null,
    operatorRole: operator ? operator.role : null,
    time: time || formatDateTime(new Date()),
    fromStatus: fromStatus || null,
    toStatus: toStatus || null,
    before: before || null,
    after: after || null,
    remark: remark || null
  })
}

/** 区间展示串：{起} ~ {止}，同日压缩为「2026-10-01 上午 ~ 下午」 */
function rangeText(leave) {
  const head = `${leave.startDate} ${HALF_DAY[leave.startPeriod].label}`
  const tail =
    leave.endDate === leave.startDate
      ? HALF_DAY[leave.endPeriod].label
      : `${leave.endDate} ${HALF_DAY[leave.endPeriod].label}`
  return `${head} ~ ${tail}`
}

const typeLabel = (leaveType) => (LEAVE_TYPE[leaveType] ? LEAVE_TYPE[leaveType].label : leaveType)

/**
 * 写入站内信；目标缺失时不阻断主流程，留一条 NOTIFY_SKIP 便于排障（设计规范 §6.2 场景 10）
 * 系统内没有「按角色/驿站广播」的模型，接收人只能是单个 employeeId。
 */
function notify(leave, { target, type, title, content }) {
  if (!target) {
    pushLog(leave, { action: 'NOTIFY_SKIP', remark: `通知目标缺失，未写入通知：${title}` })
    return
  }
  pushNotification({ employeeId: target.id, type, title, content, bizType: 'leave', bizId: leave.id })
}

/* ==================== 校验 ==================== */

function findOverlap(employeeId, form, excludeId) {
  const target = new Set(halfUnitsOf(form.startDate, form.startPeriod, form.endDate, form.endPeriod))
  return (
    state.leaves.find(
      (l) =>
        l.employeeId === Number(employeeId) &&
        OCCUPIED_STATUS.includes(l.status) &&
        l.id !== excludeId &&
        halfUnitsOf(l.startDate, l.startPeriod, l.endDate, l.endPeriod).some((token) => target.has(token))
    ) || null
  )
}

const overlapMessage = (other) =>
  `该时间段与已有申请重叠：${typeLabel(other.leaveType)} ${rangeText(other)}（${
    LEAVE_STATUS[other.status] ? LEAVE_STATUS[other.status].label : other.status
  }）。请调整时间，或先撤销原申请再提交。`

/**
 * 日期与枚举语义校验（提交 / 编辑 / 重提 / 试算共用）
 * 只判「这一段区间本身合不合法」，不含重叠；重叠要排除本单，单独一步。
 * 格式校验留在路由层回 400，这里判语义（真实日期 / 方向 / 上限）回 9604。
 */
function dateError(form) {
  if (!LEAVE_TYPE[form.leaveType]) return { code: CODE.BAD_REQUEST, message: 'leaveType 取值非法' }
  if (!HALF_DAY[form.startPeriod] || !HALF_DAY[form.endPeriod])
    return { code: CODE.BAD_REQUEST, message: '半天粒度仅支持 AM / PM' }
  for (const key of ['startDate', 'endDate']) {
    if (formatDate(parseDate(form[key])) !== form[key])
      return { code: LEAVE_CODE.DATE_INVALID, message: '请假日期不合法' }
  }
  if (form.startDate < formatDate(new Date()))
    return {
      code: LEAVE_CODE.DATE_INVALID,
      message: '请假不能选择过去的日期。已发生缺勤的情况请联系站长线下确认处理。'
    }
  if (form.endDate < form.startDate) return { code: LEAVE_CODE.DATE_INVALID, message: '结束日期不能早于开始日期' }
  if (form.startDate === form.endDate && form.startPeriod === 'PM' && form.endPeriod === 'AM')
    return { code: LEAVE_CODE.DATE_INVALID, message: '同一天内，结束时段不能早于开始时段' }
  if (naturalDaysOf(form) > MAX_LEAVE_DAYS)
    return {
      code: LEAVE_CODE.DATE_INVALID,
      message: `单次请假最长 ${MAX_LEAVE_DAYS} 天，如需更长请分次申请或联系老板`
    }
  return null
}

/** 表单校验（提交 / 编辑 / 重提）：日期语义 + 与本人已有申请的重叠 */
function validateForm(form, employeeId, excludeId) {
  const error = dateError(form)
  if (error) return error
  const other = findOverlap(employeeId, form, excludeId)
  if (other) return { code: LEAVE_CODE.OVERLAP, message: overlapMessage(other) }
  return null
}

/* ==================== VO ==================== */

/**
 * 出参追加派生标志，前端按角色直接渲染动作按钮，不必各自再判一遍状态与身份。
 * approver* = 终审信息，stationApprover* = 初审信息：两级分槽存放，
 * 通用字段不会在终审时覆盖初审，避免「谁初审的」永久丢失。
 */
function toLeaveVO(leave, user) {
  const isOwner = !!user && user.id === leave.employeeId
  return {
    ...leave,
    stationName: stationName(leave.stationId),
    countedDaysSnapshot: leave.countedDaysSnapshot ? { ...leave.countedDaysSnapshot } : null,
    handleLog: leave.handleLog.map((row) => ({ ...row })),
    canEdit: isOwner && leave.status === 'PENDING_STATION',
    canCancel: isOwner && CANCELABLE_STATUS.includes(leave.status),
    canRevoke: !!user && user.role === 'ADMIN' && leave.status === 'APPROVED'
  }
}

/* ==================== 查询 ==================== */

/** 请假查询：员工端（employeeId）与管理端（stationId）共用一套筛选与分页，避免两套口径各写一遍 */
export function queryLeaves({ employeeId, stationId, status, leaveType, startDate, endDate, pageNum, pageSize, user }) {
  ensure()
  let rows = state.leaves
  if (employeeId != null && employeeId !== '') rows = rows.filter((l) => l.employeeId === Number(employeeId))
  if (stationId != null && stationId !== '') rows = rows.filter((l) => l.stationId === Number(stationId))
  if (Array.isArray(status) ? status.length : status)
    rows = rows.filter((l) => (Array.isArray(status) ? status.includes(l.status) : l.status === status))
  if (leaveType) rows = rows.filter((l) => l.leaveType === leaveType)
  // 日期筛选按「区间有交集」：查 10 月的请假，落在 9 月末跨到 10 月的单也要出来
  if (startDate) rows = rows.filter((l) => l.endDate >= String(startDate))
  if (endDate) rows = rows.filter((l) => l.startDate <= String(endDate))
  const page = paginate(
    rows.slice().sort((a, b) => (a.applyTime < b.applyTime ? 1 : -1)),
    pageNum,
    pageSize
  )
  page.list = page.list.map((l) => toLeaveVO(l, user || null))
  return page
}

export function findLeave(id) {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  return leave ? toLeaveVO(leave, null) : null
}

/** 详情出参按登录人算派生标志 */
export const findLeaveForUser = (id, user) => {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  return leave ? toLeaveVO(leave, user) : null
}

/* ==================== 考勤 / 算薪口径（D3） ==================== */

/**
 * 某员工在 [startDate, endDate] 内「已批请假」的计薪天数（缺席口径扣减项）
 * 跨账期单由调用方按月区间调用天然切分（financeStore.contextOf 与 payrollPreview 都是逐月取数），
 * 不需要在一处做整单归属判断 —— 那正是设计规范 §8.4 警告的「整单落在起始月」错法。
 */
export function approvedLeaveDays(employeeId, startDate, endDate) {
  ensure()
  const eid = Number(employeeId)
  return state.leaves
    .filter((l) => l.employeeId === eid && l.status === 'APPROVED')
    .reduce((sum, l) => {
      const units = halfUnitsOf(l.startDate, l.startPeriod, l.endDate, l.endPeriod).filter((token) => {
        const day = token.slice(0, 10)
        return (!startDate || day >= String(startDate)) && (!endDate || day <= String(endDate))
      })
      if (!units.length) return sum
      if (LEAVE_TYPE[l.leaveType].countMode === 'NATURAL') return sum + units.length / 2
      const scheduled = new Set(scheduledDatesOf(eid, startDate || l.startDate, endDate || l.endDate))
      return sum + units.filter((token) => scheduled.has(token.slice(0, 10))).length / 2
    }, 0)
}

/* ==================== 扣款开关（D5 + Q6） ==================== */

export const getLeaveSettings = () => {
  ensure()
  return { ...state.settings }
}

/** 请假扣款开关：true = 请假按缺勤计（扣款），false = 默认，请假不扣 */
export const isLeaveDeductEnabled = () => ensure().settings.leaveDeductEnabled === true

export function saveLeaveSettings({ leaveDeductEnabled }) {
  ensure()
  state.settings.leaveDeductEnabled = leaveDeductEnabled === true
  persist()
  return getLeaveSettings()
}

/* ==================== 写操作（状态机 T1–T9） ==================== */

const actorError = (message) => ({ code: LEAVE_CODE.NO_PERMISSION, message })

/** 只算不落库的试算（Q5：口径单点，避免前端出现第二份算薪镜像） */
export function previewLeave({ employeeId, form }) {
  ensure()
  const error = dateError(form)
  if (error) return error
  const naturalDays = naturalDaysOf(form)
  const countedDays = countedDaysOf(employeeId, form)
  return { code: 200, data: { naturalDays, countedDays, hasRestDayExcluded: countedDays < naturalDays } }
}

/** T1 提交：STAFF → 待初审；STATION_ADMIN / 无可用站长 → 待终审 */
export function applyLeave({ employee, form }) {
  ensure()
  if (employee.role === 'ADMIN') return actorError('超级管理员无需提交请假申请')
  if (employee.station_id == null) return { code: CODE.BAD_REQUEST, message: '当前账号未归属驿站，无法提交请假' }
  const error = validateForm(form, employee.id, null)
  if (error) return error
  return { code: 200, data: toLeaveVO(createLeave(employee, form, null), employee) }
}

/** 建单 + 定初始状态 + 留痕 + 通知：提交与驳回重提共用（T9 生成新单，带 originId） */
function createLeave(employee, form, originId) {
  const now = formatDateTime(new Date())
  const naturalDays = naturalDaysOf(form)
  const stationApprover = employee.role === 'STATION_ADMIN' ? null : stationAdminOf(employee.station_id)
  const status = stationApprover ? 'PENDING_STATION' : 'PENDING_BOSS'
  state.seq.leave += 1
  const leave = {
    id: state.seq.leave,
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId: employee.station_id,
    leaveType: form.leaveType,
    startDate: form.startDate,
    startPeriod: form.startPeriod,
    endDate: form.endDate,
    endPeriod: form.endPeriod,
    reason: form.reason,
    status,
    rejectStage: null,
    naturalDays,
    countedDays: countedDaysOf(employee.id, form),
    countedDaysSnapshot: null,
    approverId: null,
    approverName: null,
    approveTime: null,
    approveRemark: null,
    stationApproverId: null,
    stationApproverName: null,
    stationApproveTime: null,
    stationApproveRemark: null,
    cancelById: null,
    cancelTime: null,
    revokerId: null,
    revokerName: null,
    revokeTime: null,
    revokeReason: null,
    originId: originId || null,
    applyTime: now,
    updateTime: now,
    handleLog: []
  }
  state.leaves.push(leave)
  pushLog(leave, { action: 'SUBMIT', operator: employee, toStatus: status })
  notifyApply(leave, employee, stationApprover, status)
  persist()
  return leave
}

function notifyApply(leave, employee, stationApprover, status) {
  const summary = `${typeLabel(leave.leaveType)} ${rangeText(leave)} 共 ${leave.naturalDays} 天`
  if (status === 'PENDING_STATION') {
    notify(leave, {
      target: stationApprover,
      type: NOTIFY_APPLY,
      title: `${employee.real_name} 提交了请假申请`,
      content: `${summary}，待您初审`
    })
    return
  }
  // 站长跳过初审与「无可用站长」降级走同一分支，避免出现两条不同的跳级逻辑（设计规范 §6.1）
  notify(leave, {
    target: bossOf(),
    type: NOTIFY_APPLY,
    title: `${employee.real_name}${employee.role === 'STATION_ADMIN' ? '（站长）' : ''}提交了请假申请`,
    content: `${summary}，待您终审`
  })
  if (!stationApprover && employee.role !== 'STATION_ADMIN') {
    notify(leave, {
      target: employee,
      type: NOTIFY_RESULT,
      title: '请假申请已直接提交终审',
      content: '本站暂无在职站长，您的申请已直接提交老板终审'
    })
  }
}

/** T8 编辑：仅申请人本人在待初审时可改，状态不变，重叠校验排除本单 */
export function updateLeave({ id, employee, form }) {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  if (!leave) return { code: LEAVE_CODE.NOT_EXISTS }
  if (leave.employeeId !== employee.id) return actorError()
  if (leave.status !== 'PENDING_STATION') return { code: LEAVE_CODE.EDIT_FORBIDDEN }
  const error = validateForm(form, employee.id, leave.id)
  if (error) return error
  const before = snapshotOf(leave)
  Object.assign(leave, form)
  leave.naturalDays = naturalDaysOf(form)
  leave.countedDays = countedDaysOf(employee.id, form)
  leave.updateTime = formatDateTime(new Date())
  pushLog(leave, {
    action: 'UPDATE',
    operator: employee,
    fromStatus: leave.status,
    toStatus: leave.status,
    before,
    after: snapshotOf(leave)
  })
  persist()
  return { code: 200, data: toLeaveVO(leave, employee) }
}

const snapshotOf = (leave) => ({
  leaveType: leave.leaveType,
  startDate: leave.startDate,
  startPeriod: leave.startPeriod,
  endDate: leave.endDate,
  endPeriod: leave.endPeriod,
  reason: leave.reason
})

/** T9 修改重提：原单保持 REJECTED 只读，生成新单并回 PENDING_STATION / PENDING_BOSS */
export function resubmitLeave({ id, employee, form }) {
  ensure()
  const origin = state.leaves.find((l) => l.id === Number(id))
  if (!origin) return { code: LEAVE_CODE.NOT_EXISTS }
  if (origin.employeeId !== employee.id) return actorError()
  if (origin.status !== 'REJECTED') return { code: LEAVE_CODE.STATUS_INVALID }
  const error = validateForm(form, employee.id, null)
  if (error) return error
  pushLog(origin, {
    action: 'RESUBMIT',
    operator: employee,
    fromStatus: origin.status,
    remark: '修改后重新提交，生成新单'
  })
  const created = createLeave(employee, form, origin.id)
  return { code: 200, data: toLeaveVO(created, employee) }
}

/** T6 撤销：申请人本人在两种待审态可撤销，无考勤副作用 */
export function cancelLeave({ id, employee }) {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  if (!leave) return { code: LEAVE_CODE.NOT_EXISTS }
  if (leave.employeeId !== employee.id) return actorError()
  if (!CANCELABLE_STATUS.includes(leave.status)) return { code: LEAVE_CODE.STATUS_INVALID }
  const fromStatus = leave.status
  const target = fromStatus === 'PENDING_STATION' ? stationAdminOf(leave.stationId) : bossOf()
  leave.status = 'CANCELLED'
  leave.rejectStage = null
  leave.cancelById = employee.id
  leave.cancelTime = formatDateTime(new Date())
  leave.updateTime = leave.cancelTime
  pushLog(leave, { action: 'CANCEL', operator: employee, fromStatus, toStatus: 'CANCELLED' })
  notify(leave, {
    target,
    type: NOTIFY_RESULT,
    title: '请假申请已撤销',
    content: `${leave.employeeName} 已撤销 ${typeLabel(leave.leaveType)} ${rangeText(leave)} 的申请`
  })
  persist()
  return { code: 200, data: toLeaveVO(leave, employee) }
}

/** T2 / T3 站长初审：仅本站站长、不能审自己；驳回原因必填 2-100 字 */
export function stationApprove({ id, approved, remark, operator }) {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  if (!leave) return { code: LEAVE_CODE.NOT_EXISTS }
  if (operator.station_id !== leave.stationId) return actorError()
  if (leave.employeeId === operator.id) return actorError('不能审批本人提交的请假申请')
  if (leave.status !== 'PENDING_STATION') return { code: LEAVE_CODE.STATUS_INVALID }
  if (!approved && !remark) return { code: CODE.BAD_REQUEST, message: '驳回原因须为 2-100 字' }

  const fromStatus = leave.status
  const now = formatDateTime(new Date())
  leave.stationApproverId = operator.id
  leave.stationApproverName = operator.real_name
  leave.stationApproveTime = now
  leave.stationApproveRemark = remark || null
  leave.updateTime = now
  leave.status = approved ? 'PENDING_BOSS' : 'REJECTED'
  leave.rejectStage = approved ? null : 'STATION'
  pushLog(leave, {
    action: approved ? 'STATION_APPROVE' : 'STATION_REJECT',
    operator,
    fromStatus,
    toStatus: leave.status,
    remark: remark || null
  })
  if (approved) {
    notify(leave, {
      target: bossOf(),
      type: NOTIFY_APPLY,
      title: '请假申请待终审',
      content: `${leave.employeeName} 的${typeLabel(leave.leaveType)} ${rangeText(leave)} 已通过 ${stationName(
        leave.stationId
      )} 站长初审`
    })
  } else {
    notify(leave, {
      target: applicantOf(leave),
      type: NOTIFY_RESULT,
      title: '请假申请未通过初审',
      content: `${typeLabel(leave.leaveType)} ${rangeText(leave)}，驳回原因：${remark}。可修改后重新提交`
    })
  }
  persist()
  return { code: 200, data: toLeaveVO(leave, operator) }
}

/** T4 / T5 老板终审：通过时落计薪天数快照（排班事后变更不影响已出账口径） */
export function finalApprove({ id, approved, remark, operator }) {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  if (!leave) return { code: LEAVE_CODE.NOT_EXISTS }
  if (leave.employeeId === operator.id) return actorError('不能审批本人提交的请假申请')
  if (leave.status !== 'PENDING_BOSS') return { code: LEAVE_CODE.STATUS_INVALID }
  if (!approved && !remark) return { code: CODE.BAD_REQUEST, message: '驳回原因须为 2-100 字' }

  const fromStatus = leave.status
  const now = formatDateTime(new Date())
  leave.approverId = operator.id
  leave.approverName = operator.real_name
  leave.approveTime = now
  leave.approveRemark = remark || null
  leave.updateTime = now
  leave.status = approved ? 'APPROVED' : 'REJECTED'
  leave.rejectStage = approved ? null : 'BOSS'
  if (approved) {
    // 快照取值规则见设计规范 §8.5：naturalDays / countedDays / 排班摘要
    leave.countedDaysSnapshot = {
      naturalDays: leave.naturalDays,
      countedDays: leave.countedDays,
      scheduleDigest: scheduleDigestOf(leave.employeeId, leave)
    }
  }
  pushLog(leave, {
    action: approved ? 'FINAL_APPROVE' : 'FINAL_REJECT',
    operator,
    fromStatus,
    toStatus: leave.status,
    remark: remark || null
  })
  notify(leave, {
    target: applicantOf(leave),
    type: NOTIFY_RESULT,
    title: approved ? '请假申请已通过' : '请假申请被驳回',
    content: approved
      ? `${typeLabel(leave.leaveType)} ${rangeText(leave)}，计薪 ${leave.countedDays} 天，已生效`
      : `${typeLabel(leave.leaveType)} ${rangeText(leave)}，驳回原因：${remark}。可修改后重新提交`
  })
  persist()
  return { code: 200, data: toLeaveVO(leave, operator) }
}

/** T7 撤回：仅 ADMIN；任一所跨账期已存在非草稿工资单则 9606，撤回原因必填 */
export function revokeLeave({ id, reason, operator }) {
  ensure()
  const leave = state.leaves.find((l) => l.id === Number(id))
  if (!leave) return { code: LEAVE_CODE.NOT_EXISTS }
  if (leave.status !== 'APPROVED') return { code: LEAVE_CODE.STATUS_INVALID }
  const locked = monthSpansOf(leave).find((month) => findLockingPayroll(leave.employeeId, month))
  if (locked)
    return {
      code: LEAVE_CODE.PAYROLL_LOCKED,
      message: `${locked} 工资单已生成，撤回会导致工资数据不一致。请先在财务管理中作废该单据。`
    }
  const now = formatDateTime(new Date())
  leave.status = 'REVOKED'
  leave.rejectStage = null
  leave.revokerId = operator.id
  leave.revokerName = operator.real_name
  leave.revokeTime = now
  leave.revokeReason = reason
  leave.updateTime = now
  pushLog(leave, { action: 'REVOKE', operator, fromStatus: 'APPROVED', toStatus: 'REVOKED', remark: reason })
  notify(leave, {
    target: applicantOf(leave),
    type: NOTIFY_RESULT,
    title: '已批准的请假被撤回',
    content: `${typeLabel(leave.leaveType)} ${rangeText(leave)} 已被 ${operator.real_name} 撤回，原因：${reason}。如有疑问请联系站长`
  })
  persist()
  return { code: 200, data: toLeaveVO(leave, operator) }
}

const applicantOf = (leave) => activeEmployees().find((e) => e.id === leave.employeeId) || null

/** 请假区间覆盖到的账期集合（跨月单会得到 2 个月，撤回要逐月检查） */
function monthSpansOf(leave) {
  const months = new Set()
  const last = parseDate(leave.endDate)
  for (let day = parseDate(leave.startDate); day <= last; day = addDays(day, 1)) {
    months.add(formatDate(day).slice(0, 7))
  }
  return [...months]
}

/* ==================== 种子 ==================== */

/**
 * 种子方案：6 种状态全覆盖、跨 4 个驿站、含半天单与跨天单、含两级驳回与已撤销 / 已撤回。
 * 为什么非城东驿站用 NATURAL 假别：排班种子只铺城东（attendanceStore 的 STATION_ID=1），
 * 其他驿站若用 SCHEDULED 假别，计薪天数会因查不到排班而恒为 0，演示数据自相矛盾；
 * 用「婚假/产假」这类按自然日计的假别反而自洽（设计规范 §4.3.2 的 NATURAL 分支）。
 */
const SEED_PLAN = [
  // 城东：半天单（同日下午）+ 跨天单（3 天），走站长初审
  {
    stationId: 1,
    role: 'STAFF',
    leaveType: 'PERSONAL',
    offset: 2,
    startPeriod: 'PM',
    endPeriod: 'PM',
    status: 'PENDING_STATION',
    reason: '下午家中有事，申请事假半天'
  },
  {
    stationId: 1,
    role: 'STAFF',
    leaveType: 'ANNUAL',
    offset: 3,
    endOffset: 5,
    status: 'PENDING_STATION',
    reason: '年假出行，已与副站协调好班次'
  },
  // 站长提的单跳过初审，直达终审（D1）；用 2 号站的站长，把 1 号站演示账号留给现场演示
  {
    stationId: 2,
    role: 'STATION_ADMIN',
    leaveType: 'COMPENSATORY',
    offset: 1,
    endOffset: 1,
    status: 'PENDING_BOSS',
    reason: '上周末顶班，调休一天'
  },
  // 其他驿站：按自然日计的假别，跨站演示审批范围
  {
    stationId: 2,
    role: 'STAFF',
    leaveType: 'MARRIAGE',
    offset: 4,
    endOffset: 6,
    status: 'PENDING_BOSS',
    reason: '婚假，已报备排班'
  },
  {
    stationId: 3,
    role: 'STAFF',
    leaveType: 'BEREAVEMENT',
    offset: 2,
    endOffset: 2,
    status: 'APPROVED',
    reason: '家中长辈丧事，申请丧假'
  },
  // 城东已通过单（必含轮休日：计薪天数 < 自然天数，供算薪联动与撤回演示）
  {
    stationId: 1,
    role: 'STAFF',
    leaveType: 'SICK',
    offset: 1,
    endOffset: 2,
    status: 'APPROVED',
    restGap: true,
    reason: '感冒发热需休息，已提交就诊记录'
  },
  // 其他驿站已通过单：该驿站当期工资单非草稿，用于演示 9606 撤回阻断
  {
    stationId: 2,
    role: 'STAFF',
    leaveType: 'PATERNITY',
    offset: 0,
    endOffset: 0,
    status: 'APPROVED',
    reason: '配偶生产，申请陪产假'
  },
  // 两级驳回各一条
  {
    stationId: 1,
    role: 'STAFF',
    leaveType: 'PERSONAL',
    offset: 7,
    endOffset: 7,
    status: 'REJECTED',
    rejectStage: 'STATION',
    remark: '本周末件量高峰，人手不足，暂不批假',
    reason: '朋友聚会，想请一天假'
  },
  {
    stationId: 2,
    role: 'STAFF',
    leaveType: 'MATERNITY',
    offset: 8,
    endOffset: 9,
    status: 'REJECTED',
    rejectStage: 'BOSS',
    remark: '产假需提供生育证明，请补齐后重提',
    reason: '产假，材料稍后补充'
  },
  // 申请人自行撤销（两种待审态各一条）
  {
    stationId: 1,
    role: 'STAFF',
    leaveType: 'PERSONAL',
    offset: 10,
    startPeriod: 'AM',
    endPeriod: 'AM',
    status: 'CANCELLED',
    reason: '临时取消出行安排'
  },
  {
    stationId: 3,
    role: 'STAFF',
    leaveType: 'ANNUAL',
    offset: 11,
    endOffset: 12,
    status: 'CANCELLED',
    reason: '行程有变，先撤销'
  },
  // 审批人撤回已通过单
  {
    stationId: 3,
    role: 'STAFF',
    leaveType: 'OTHER',
    offset: 13,
    endOffset: 13,
    status: 'REVOKED',
    remark: '该员工当日实际到岗，原批准作废',
    reason: '家中有事，申请一天事假'
  }
]

/** 种子不占用的账号：1 号站的演示账号留给现场演示（自己提一单才不会撞上已有区间） */
const SEED_EXCLUDE_IDS = [3, 4]

/** 取种子申请人：按角色 + 归属驿站轮转取，保证同一驿站的多条样本落在不同人身上（不互相占用时段） */
function pickApplicant(stationId, role, index) {
  const pool = activeEmployees().filter(
    (e) => e.role === role && e.station_id === Number(stationId) && e.status === 1 && !SEED_EXCLUDE_IDS.includes(e.id)
  )
  return pool.length ? pool[index % pool.length] : null
}

/** 'yyyy-MM-dd' 相差天数 */
const dayGap = (a, b) => Math.round((parseDate(b) - parseDate(a)) / 86400000)

/**
 * 挑一条「区间内含轮休日」的城东请假样本：逐日查排班找出「有排班 + 无排班」混合的窗口。
 * 为什么必须动态挑：排班相对「今天」生成，写死日期在有些星期下会整段落在排班窗口之外，
 * 那样「排除轮休日」这条设计要点在演示数据里就看不出来了。
 * 排班种子的未来窗口只到本周日，首选窗口截到本周日，截不到就退化为「今天单日」找当天轮休的人。
 */
function pickRestGapSample(offset, endOffset, excludeIds) {
  const today = formatDate(new Date())
  const endOfWeek = formatDate(addDays(mondayOf(new Date()), 6))
  const from = formatDate(addDays(parseDate(today), offset))
  const to = formatDate(addDays(parseDate(today), endOffset))
  const pool = activeEmployees().filter(
    (e) => e.station_id === 1 && e.status === 1 && !excludeIds.includes(e.id) && !SEED_EXCLUDE_IDS.includes(e.id)
  )
  const ranges = [
    { first: from, last: to > endOfWeek ? endOfWeek : to },
    { first: today, last: today }
  ]
  for (const range of ranges) {
    const maxLen = dayGap(range.first, range.last) + 1
    for (let len = maxLen; len >= 1; len -= 1) {
      const end = formatDate(addDays(parseDate(range.first), len - 1))
      for (const employee of pool) {
        const scheduled = new Set(scheduledDatesOf(employee.id, range.first, end))
        let hasWork = false
        let hasRest = false
        for (let day = parseDate(range.first); formatDate(day) <= end; day = addDays(day, 1)) {
          if (scheduled.has(formatDate(day))) hasWork = true
          else hasRest = true
        }
        // 多日窗口要「有排班也有轮休」；单日窗口只要当天是轮休（计薪 0 < 自然 0.5）即成立
        if (len >= 2 ? hasWork && hasRest : hasRest)
          return {
            employee,
            form: {
              startDate: range.first,
              startPeriod: 'AM',
              endDate: end,
              endPeriod: len >= 2 ? 'PM' : 'AM'
            }
          }
      }
    }
  }
  return null
}

function buildSeed() {
  state = { seq: { leave: 0, log: 0 }, leaves: [], settings: { leaveDeductEnabled: false } }
  const today = parseDate(formatDate(new Date()))
  const at = (dayOffset, hour) => {
    const d = addDays(today, dayOffset)
    d.setHours(hour, 20, 0, 0)
    return formatDateTime(d)
  }
  const usedIds = []

  SEED_PLAN.forEach((plan, index) => {
    const endOffset = plan.endOffset === undefined ? plan.offset : plan.endOffset
    const form = {
      leaveType: plan.leaveType,
      startDate: formatDate(addDays(today, plan.offset)),
      startPeriod: plan.startPeriod || 'AM',
      endDate: formatDate(addDays(today, endOffset)),
      endPeriod: plan.endPeriod || 'PM',
      reason: plan.reason
    }
    let owner = pickApplicant(plan.stationId, plan.role, index)
    if (plan.restGap) {
      const hit = pickRestGapSample(plan.offset, endOffset, usedIds)
      if (hit) {
        owner = hit.employee
        Object.assign(form, hit.form)
      }
    }
    if (!owner) return
    usedIds.push(owner.id)
    seedLeave(owner, form, plan, index, at)
  })
  persist()
  return state
}

/** 单条种子建单：状态与审批信息直接落好，留痕按动作链补齐 */
function seedLeave(employee, form, plan, index, at) {
  const stationApprover = stationAdminOf(employee.station_id)
  const boss = bossOf()
  state.seq.leave += 1
  const applyTime = at(-(3 + (index % 3)), 9)
  const leave = {
    id: state.seq.leave,
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId: employee.station_id,
    leaveType: form.leaveType,
    startDate: form.startDate,
    startPeriod: form.startPeriod,
    endDate: form.endDate,
    endPeriod: form.endPeriod,
    reason: form.reason,
    status: plan.status,
    rejectStage: plan.rejectStage || null,
    naturalDays: 0,
    countedDays: 0,
    countedDaysSnapshot: null,
    approverId: null,
    approverName: null,
    approveTime: null,
    approveRemark: null,
    stationApproverId: null,
    stationApproverName: null,
    stationApproveTime: null,
    stationApproveRemark: null,
    cancelById: null,
    cancelTime: null,
    revokerId: null,
    revokerName: null,
    revokeTime: null,
    revokeReason: null,
    originId: null,
    applyTime,
    updateTime: applyTime,
    handleLog: []
  }
  leave.naturalDays = naturalDaysOf(form)
  leave.countedDays = countedDaysOf(employee.id, form)
  state.leaves.push(leave)
  // 提交后的初始态：站长本人跳过初审，与无可用站长时共用同一判定（D1 + Q4）
  const initialStatus = employee.role === 'STATION_ADMIN' || !stationApprover ? 'PENDING_BOSS' : 'PENDING_STATION'
  pushLog(leave, { action: 'SUBMIT', operator: employee, time: applyTime, toStatus: initialStatus })

  const stationTime = at(-(2 + (index % 3)), 10)
  // 站长自己的单跳过初审，种子也不落初审痕迹，否则与「待终审」状态自相矛盾
  const needStation = employee.role !== 'STATION_ADMIN'
  if (needStation && ['PENDING_BOSS', 'APPROVED', 'REJECTED', 'REVOKED'].includes(plan.status)) {
    leave.stationApproverId = stationApprover ? stationApprover.id : null
    leave.stationApproverName = stationApprover ? stationApprover.real_name : null
    leave.stationApproveTime = stationTime
    leave.stationApproveRemark =
      plan.status === 'REJECTED' && plan.rejectStage === 'STATION' ? plan.remark : '情况属实，准假'
    pushLog(leave, {
      action: plan.status === 'REJECTED' && plan.rejectStage === 'STATION' ? 'STATION_REJECT' : 'STATION_APPROVE',
      operator: stationApprover,
      time: stationTime,
      fromStatus: 'PENDING_STATION',
      toStatus: plan.status === 'REJECTED' && plan.rejectStage === 'STATION' ? 'REJECTED' : 'PENDING_BOSS',
      remark: leave.stationApproveRemark
    })
  }
  if (
    ['APPROVED', 'REVOKED', 'REJECTED'].includes(plan.status) &&
    !(plan.status === 'REJECTED' && plan.rejectStage === 'STATION')
  ) {
    const finalTime = at(-(1 + (index % 2)), 15)
    leave.approverId = boss ? boss.id : null
    leave.approverName = boss ? boss.real_name : null
    leave.approveTime = finalTime
    leave.approveRemark = plan.status === 'REJECTED' ? plan.remark : '同意，注意交接好手头工作'
    leave.updateTime = finalTime
    if (plan.status === 'APPROVED' || plan.status === 'REVOKED') {
      leave.countedDaysSnapshot = {
        naturalDays: leave.naturalDays,
        countedDays: leave.countedDays,
        scheduleDigest: scheduleDigestOf(employee.id, form)
      }
    }
    pushLog(leave, {
      action: plan.status === 'REJECTED' ? 'FINAL_REJECT' : 'FINAL_APPROVE',
      operator: boss,
      time: finalTime,
      fromStatus: 'PENDING_BOSS',
      toStatus: plan.status === 'REJECTED' ? 'REJECTED' : 'APPROVED',
      remark: leave.approveRemark
    })
  }
  if (plan.status === 'CANCELLED') {
    const cancelTime = at(-1, 11)
    leave.cancelById = employee.id
    leave.cancelTime = cancelTime
    leave.updateTime = cancelTime
    pushLog(leave, {
      action: 'CANCEL',
      operator: employee,
      time: cancelTime,
      fromStatus: 'PENDING_STATION',
      toStatus: 'CANCELLED'
    })
  }
  if (plan.status === 'REVOKED') {
    const revokeTime = at(-1, 16)
    leave.revokerId = boss ? boss.id : null
    leave.revokerName = boss ? boss.real_name : null
    leave.revokeTime = revokeTime
    leave.revokeReason = plan.remark
    leave.updateTime = revokeTime
    pushLog(leave, {
      action: 'REVOKE',
      operator: boss,
      time: revokeTime,
      fromStatus: 'APPROVED',
      toStatus: 'REVOKED',
      remark: plan.remark
    })
  }
}
