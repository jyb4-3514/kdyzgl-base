import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { CHECK_TYPE, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import { addDays, formatDate, mondayOf } from '@kdyzgl/shared/domain/time.js'

/**
 * 考勤与排班的页面层共用逻辑（打卡页 / 排班页 / 记录页 / 管理端考勤）
 *
 * 打卡时间窗不再由前端推算：/attendance/status 已按时段下发 windowStart / windowEnd
 * （= 时段开始 - allowEarlyMin、时段结束 + allowLateMin），页面直接展示接口值，
 * 避免出现「页面说能打、服务端说超窗」两套口径。
 */

/** 地球平均半径（米），与服务端 Haversine 同单位同取值 */
const EARTH_RADIUS = 6371000

/** 日期偏移复用 shared/domain 同一份实现，避免第二套「周一为周首」算法漂移 */
export { addDays, formatDate, mondayOf }

export const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const pad = (n) => String(n).padStart(2, '0')

/** 'YYYY-MM-DD' → 'MM-DD 周三'（移动端屏幕窄，不带年份更省一行） */
export function dayText(dateText) {
  const d = new Date(`${dateText}T00:00:00`)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${WEEKDAYS[d.getDay()]}`
}

/** 'YYYY-MM-DD HH:mm:ss' → 'HH:mm'（列表只展示时刻） */
export function clockOf(datetime) {
  return datetime ? String(datetime).slice(11, 16) : ''
}

/** 'HH:mm' → 当日分钟数；'24:00' 按 1440 处理（收班时间的常见写法） */
export function minutesOfDay(text) {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  if (!Number.isFinite(h) || !Number.isFinite(m)) return NaN
  return h * 60 + m
}

/**
 * 今日打卡进度：已完成卡数 / 应打总数（时段数 × 2）
 * 首页状态条与工作台宫格都要这一句「还剩几次没打」，收在一处避免两个页面各算一遍。
 */
export function attendanceProgress(status) {
  const periods = (status && status.periods) || []
  const done = periods.filter((p) => p.onChecked).length + periods.filter((p) => p.offChecked).length
  return { done, total: periods.length * 2 }
}

/** 分钟数 → 'HH:mm'，跨日显式标注（时间窗可能落到次日凌晨） */
export function clockOfMinutes(minutes) {
  if (minutes < 0) return `前日 ${clockOfMinutes(minutes + 1440).replace('次日 ', '')}`
  return `${minutes >= 1440 ? '次日 ' : ''}${pad(Math.floor((minutes % 1440) / 60))}:${pad(minutes % 60)}`
}

/**
 * 时段打卡时间窗文案
 * 优先取接口下发的 windowStart / windowEnd（与服务端判定同一口径）；接口未下发时按规则余量兜底推导，
 * 保证页面在任何数据形态下都不出现空白。
 */
export function periodWindowText(period, rule) {
  if (!period || !period.startTime) return '-'
  if (period.windowStart && period.windowEnd) return `${period.windowStart} - ${period.windowEnd}`
  const early = Number((rule && rule.allowEarlyMin) || 0)
  const late = Number((rule && rule.allowLateMin) || 0)
  return `${clockOfMinutes(minutesOfDay(period.startTime) - early)} - ${clockOfMinutes(minutesOfDay(period.endTime) + late)}`
}

/**
 * 打卡项名称：多时段场景必须带时段名，否则记录里的「上班卡」分不清是上午还是下午；
 * 无时段名的历史数据退回卡片类型文案，不显示空占位。
 */
export function periodLabel(periodName, checkType) {
  const typeLabel = dictLabel(CHECK_TYPE, checkType)
  const name = periodName ? String(periodName).trim() : ''
  return name ? `${name} · ${typeLabel}` : typeLabel
}

/**
 * Haversine 大圆距离（米）
 * 与服务端判定同公式，这里只做「按下按钮前」的预判展示，最终以服务端判定为准。
 */
export function haversine(lng1, lat1, lng2, lat2) {
  const toRad = (deg) => (Number(deg) * Math.PI) / 180
  const dLat = toRad(lat2) - toRad(lat1)
  const dLng = toRad(lng2) - toRad(lng1)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  const distance = 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(a)))
  return Number.isFinite(distance) ? distance : null
}

/** 距离文案：围栏内看米，超出后看公里更直观 */
export function distanceText(meters) {
  if (meters == null || !Number.isFinite(meters)) return '未知'
  return meters >= 1000 ? `${(meters / 1000).toFixed(2)} 公里` : `${Math.round(meters)} 米`
}

/** 坐标展示：统一 6 位小数，与规则存储精度一致 */
export function coordText(value) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(6) : '-'
}

/**
 * 打卡错误码 → 页面级提示
 * 为什么不用 codeMessage：9103/9104 只说「校验未通过」，现场员工需要的是
 * 「是不是没连驿站 WiFi / 定位飘到哪儿去了」，故按码补上下文与下一步动作。
 */
export function checkErrorHint(code, ctx = {}) {
  switch (code) {
    case ATTENDANCE_CODE.RULE_NOT_CONFIGURED:
      return '该驿站尚未配置打卡规则，请联系站长或管理员配置后再打卡'
    case ATTENDANCE_CODE.OUT_OF_TIME_WINDOW:
      return `当前不在打卡时间窗内（${ctx.checkTypeLabel || ''}可打时间 ${ctx.window || '-'}），请到时间后再试`
    case ATTENDANCE_CODE.WIFI_MISMATCH:
      // 未取到 SSID（提交 null）不是「连了别的网」，不能笼统说「当前网络不在白名单」，否则误导员工
      return ctx.ssid
        ? `WiFi 校验未通过：当前「${ctx.ssid}」不在驿站白名单内，请连接驿站 WiFi 后重试`
        : 'WiFi 校验未通过：未获取到当前 WiFi，需安装客户端（安卓壳）才能完成 WiFi 校验打卡'
    case ATTENDANCE_CODE.LOCATION_MISMATCH:
      return `定位校验未通过：距围栏中心 ${ctx.distanceText || '未知'}，超出允许范围${ctx.demoHint ? '；可打开上方「演示辅助」开关跑通演示' : ''}`
    case ATTENDANCE_CODE.DUPLICATE_CHECK:
      return ctx.periodName
        ? `「${ctx.periodName}」的${ctx.checkTypeLabel || ''}已完成，无需重复打卡`
        : '今日该类型打卡已完成，无需重复打卡'
    case ATTENDANCE_CODE.SHIFT_UNAVAILABLE:
      return '今日班次不存在或已停用，请联系站长确认排班'
    case ATTENDANCE_CODE.PERIOD_NOT_FOUND:
      return ctx.message || '打卡时段不存在或规则已变更，请下拉刷新后按当前时段重试'
    default:
      return ctx.message || '打卡失败，请稍后重试'
  }
}

/**
 * 补卡错误码 → 弹层内提示
 * 与 checkErrorHint 分开：打卡失败要「当场改」，补卡失败要「知道下一步找谁」，两者话术导向不同；
 * 且 9108 一句话承载「已有申请 / 已正常打卡」两义（数据层刻意不拆码），故一律带上查看进度指引。
 */
export function makeupErrorHint(code, ctx = {}) {
  switch (code) {
    case ATTENDANCE_CODE.MAKEUP_DUPLICATE:
      return `${ctx.message || '该时段当日已有补卡申请或已正常打卡'}；可到「我的补卡申请」查看审批进度`
    case ATTENDANCE_CODE.PERIOD_NOT_FOUND:
      return ctx.message || '打卡时段不存在或规则已变更，请刷新后按当前时段重新申请'
    case ATTENDANCE_CODE.RULE_NOT_CONFIGURED:
      return '该驿站尚未配置打卡规则，无法提交补卡申请，请联系站长配置'
    case ATTENDANCE_CODE.MAKEUP_STATUS_INVALID:
      return '该补卡申请已被处理，请刷新后查看最新状态'
    default:
      return ctx.message || '补卡申请提交失败，请稍后重试'
  }
}

/**
 * 一天打卡记录的合并状态（记录接口按 checkType 分行返回，列表要按天看）
 * 判定优先级：异常（校验未通过）> 迟到 > 早退 > 正常；无上班卡返回空串，由页面按「缺卡」文案提示。
 */
export function dayStatusOf({ on, off }) {
  if ((on && on.status === 'ABNORMAL') || (off && off.status === 'ABNORMAL')) return 'ABNORMAL'
  if (on && on.status === 'LATE') return 'LATE'
  if (off && off.status === 'EARLY_LEAVE') return 'EARLY_LEAVE'
  return on ? 'NORMAL' : ''
}

/**
 * 按月取「日期 → 班次」映射
 * 为什么在页面层做：打卡记录不含班次字段（数据层已冻结），而记录列表要展示班次，
 * 故用「我的排班」按周并取补齐（一个月最多 6 周）。
 * TODO(扩展): /attendance/my 与 /attendance/records 返回 shiftName 后移除本函数。
 *
 * @param {string} monthText 'YYYY-MM'
 * @param {(params: object) => Promise<object>} fetchWeek 按周取排班（员工端 /schedules/my）
 * @param {string} listKey 排班明细在响应里的字段名（我的排班为 list）
 */
export async function monthShiftMap(monthText, fetchWeek, listKey = 'list') {
  const [year, month] = monthText.split('-').map(Number)
  const weeks = []
  // 从「当月 1 号所在周的周一」起逐周推进，直到跨出当月；首周可能落在上月，属正常（跨月周）
  for (
    let day = mondayOf(new Date(year, month - 1, 1));
    formatDate(day).slice(0, 7) <= monthText;
    day = addDays(day, 7)
  ) {
    weeks.push(formatDate(day))
  }
  const pages = await Promise.all(weeks.map((weekStart) => fetchWeek({ weekStart })))
  const map = {}
  pages.forEach((page) => {
    ;(page[listKey] || []).forEach((item) => {
      map[item.workDate] = item
    })
  })
  return map
}
