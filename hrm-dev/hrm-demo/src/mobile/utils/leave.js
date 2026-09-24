import { HALF_DAY, LEAVE_STATUS, LEAVE_TYPE, dictLabel } from '@/shared/constants/dict.js'
import { LEAVE_CODE } from '@/shared/constants/errorCode.js'
import { formatDate, parseDate } from '@/shared/domain/time.js'

/**
 * 请假页面层共用逻辑（M11）
 *
 * 为什么单独一层：区间文案、双天数摘要、状态副信息、字段校验、错误话术在
 * 「我的请假 / 申请表单 / 站长初审 / 管理员终审 / 扣款设置」五个页面里反复出现，
 * 散在页面里必然出现两种说法（补卡字典两端各写一份的教训）。
 *
 * 本文件不做任何时长推算：自然天数与计薪天数一律取服务端（/leave/preview 与列表 VO），
 * 排班逐日计薪的口径只允许有服务端一份实现（设计规范 §4.3.3、Q5）。
 */

/** 驳回阶段副信息：设计规范 §1.2 规定列表副信息用「站长未通过 / 管理员未通过」，
 *  与字典 LEAVE_REJECT_STAGE 的「初审驳回 / 终审驳回」（筛选与统计口径）刻意区分 */
const REJECT_STAGE_TEXT = { STATION: '站长未通过', BOSS: '管理员未通过' }

/** 今日日期：日期控件 min-date 与过去日期护栏共用一份，避免两处各算一次「今天」 */
export const todayText = () => formatDate(new Date())

/** 'YYYY-MM-DD' → 本地 0 点 Date（van-calendar 的 min-date / default-date 要 Date 而非字符串） */
export const toDate = (text) => (text ? parseDate(text) : new Date())

/** 半天文案：时段缺失时返回空串，不把 undefined 渲染到界面上 */
export const periodText = (period) => (HALF_DAY[period] ? HALF_DAY[period].label : '')

/** 区间展示（设计规范 §4.2 三端统一）：同日压缩为「2026-10-01 上午 ~ 下午」 */
export function leaveRangeText(item) {
  const head = `${item.startDate} ${periodText(item.startPeriod)}`
  const tail =
    item.endDate === item.startDate ? periodText(item.endPeriod) : `${item.endDate} ${periodText(item.endPeriod)}`
  return `${head} ~ ${tail}`
}

/** 时长摘要文案（设计规范 §4.3.3）：自然天数与计薪天数双值同显，未知时给试算占位 */
export function daysSummary(naturalDays, countedDays) {
  if (naturalDays == null) return '选择请假类型与时间后自动试算'
  const counted = countedDays == null ? '—' : `${countedDays} 天`
  return `合计 ${naturalDays} 天（自然日） · 计薪 ${counted}`
}

/** 单条请假单的时长摘要：列表与详情用同一份文案，避免同一单两处说法不同 */
export const leaveDaysText = (item) => daysSummary(item.naturalDays, item.countedDays)

/**
 * 时长副说明：仅在需要解释口径时出现（设计规范 §4.3.3）
 * NATURAL 假别恒给说明；SCHEDULED 假别只在两值不等（有轮休日被排除）时说明。
 * TODO(扩展): 扣款开关为「扣款」且单已通过时追加「该假别按公司规定计算工资」——
 *   该开关仅 ADMIN 可读（GET /leave/settings 已收紧），员工端拿不到，故本行暂不渲染。
 */
export function leaveDaysHint(item) {
  if (!LEAVE_TYPE[item.leaveType]) return ''
  if (LEAVE_TYPE[item.leaveType].countMode === 'NATURAL') return '本假别按自然日连续计算，与排班无关'
  const excluded = item.hasRestDayExcluded != null ? item.hasRestDayExcluded : item.naturalDays > item.countedDays
  return excluded ? '已自动排除排班休息日，计薪天数以排班为准' : ''
}

/** 状态跟踪副信息：告诉申请人「现在在等谁 / 结果是什么」，不用用户理解两级审批的差异 */
export function leaveNextText(item) {
  switch (item.status) {
    case 'PENDING_STATION':
      return '等待站长初审'
    case 'PENDING_BOSS':
      return '已通过站长初审，等待管理员终审'
    case 'APPROVED':
      return '已生效，考勤与算薪按审批时的快照计入'
    case 'CANCELLED':
      return '申请人已撤销，无考勤与工资影响'
    case 'REVOKED':
      return '已被审批人撤回，考勤与算薪口径已回滚'
    default:
      return ''
  }
}

/** 驳回副信息：副信息里把「谁没通过」讲清，重提路径两级一致（设计规范 §1.2） */
export function leaveRejectText(item) {
  const stage = REJECT_STAGE_TEXT[item.rejectStage] || '审批未通过'
  return `${stage}，可修改后重新提交`
}

export const leaveStatusText = (value) => dictLabel(LEAVE_STATUS, value)

/** 假别文案：字典缺项回 '-'，不把 undefined 渲染出来 */
export const leaveTypeText = (value) => dictLabel(LEAVE_TYPE, value)

/** 列表行的状态副信息：驳回单讲「谁没通过 + 可重提」，其余讲「在等谁 / 结果是什么」 */
export const leaveStatusHint = (item) => (item.status === 'REJECTED' ? leaveRejectText(item) : leaveNextText(item))

/**
 * 表单前置校验（申请 / 编辑 / 重提共用）：只做「不依赖服务端的即时拦截」，
 * 日期方向与半天组合给本地立判，剩下的（早于今天、超 30 天、时间段重叠）交给
 * 日历控件 min-date 与 /leave/preview、提交接口，避免前端出现第二份时长规则。
 * @returns 错误文案（'' 表示可提交）
 */
export function leaveFormError(form) {
  if (!LEAVE_TYPE[form.leaveType]) return '请选择请假类型'
  if (!form.startDate) return '请选择开始日期'
  if (!HALF_DAY[form.startPeriod]) return '请选择开始时段'
  if (!form.endDate) return '请选择结束日期'
  if (!HALF_DAY[form.endPeriod]) return '请选择结束时段'
  if (form.startDate < todayText()) return '开始日期不能早于今天'
  if (form.endDate < form.startDate) return '结束日期不能早于开始日期'
  if (form.startDate === form.endDate && form.startPeriod === 'PM' && form.endPeriod === 'AM')
    return '同一天内，结束时段不能早于开始时段'
  const len = form.reason.trim().length
  if (len < 2 || len > 200) return '请假事由须为 2-200 字'
  return ''
}

/**
 * 错误码 → 页面/弹层内文案（9601–9607）
 * 为什么不直接用 codeMessage：9602/9603/9606/9607 的默认文案只说「不行」，
 * 审批人/申请人需要的是「下一步做什么」（对照 makeupErrorHint 的处置思路）。
 * 9604 与 9603 的服务端 message 已含具体原因与重叠单信息，优先原样呈现。
 */
export function leaveErrorHint(code, ctx = {}) {
  switch (code) {
    case LEAVE_CODE.NOT_EXISTS:
      return '该请假申请不存在，可能已被删除，请刷新后重试'
    case LEAVE_CODE.STATUS_INVALID:
      return '该申请已被处理，列表已刷新，请关闭弹层查看最新状态'
    case LEAVE_CODE.OVERLAP:
      return ctx.message || '该时间段与已有申请重叠，请调整时间或先撤销原申请'
    case LEAVE_CODE.DATE_INVALID:
      return ctx.message || '请假日期不合法，请检查日期与时段后重试'
    case LEAVE_CODE.NO_PERMISSION:
      return ctx.message || '无权操作该请假申请'
    case LEAVE_CODE.PAYROLL_LOCKED:
      return ctx.message || '该账期工资单已生成，撤回会导致工资数据不一致，请先在财务管理中作废该单据'
    case LEAVE_CODE.EDIT_FORBIDDEN:
      return '该申请当前状态不允许修改，请刷新后查看最新状态'
    default:
      return ctx.message || '操作失败，请稍后重试'
  }
}
