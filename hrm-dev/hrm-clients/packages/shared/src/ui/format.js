import { parseTime } from '@kdyzgl/shared/domain/time.js'

/**
 * 移动端展示层格式化工具
 * 时间解析复用 shared/domain 同一份实现（避免第二份 'yyyy-MM-dd HH:mm:ss' 解析逻辑漂移）
 */
export { formatDate, formatDateTime, hoursAgoParam, parseTime } from '@kdyzgl/shared/domain/time.js'

const MINUTE = 60000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const pad = (n) => String(n).padStart(2, '0')

/** 'MM-DD 周三'：H5 屏幕窄，报头与筛选行用紧凑日期而不是完整时间串 */
export function shortDateText(date = new Date()) {
  const d = date instanceof Date ? date : new Date(date)
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${WEEKDAYS[d.getDay()]}`
}

/** 'HH:mm'：数据截止时间、批次时刻等只需时刻的场景 */
export function clockText(date = new Date()) {
  const d = date instanceof Date ? date : new Date(date)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 列表用相对时间：移动端屏幕窄，绝对时间串塞不进一行 */
export function relativeTime(text) {
  const ts = parseTime(text)
  if (!ts) return '-'
  const diff = Date.now() - ts
  if (diff < MINUTE) return '刚刚'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} 分钟前`
  if (diff < DAY) return `${Math.floor(diff / HOUR)} 小时前`
  if (diff < 30 * DAY) return `${Math.floor(diff / DAY)} 天前`
  return String(text).slice(0, 10)
}

/** 时长文本：用于 SLA 剩余/超时、处理耗时展示 */
export function durationText(ms) {
  const abs = Math.abs(ms)
  if (abs >= DAY) return `${Math.floor(abs / DAY)}天${Math.floor((abs % DAY) / HOUR)}小时`
  if (abs >= HOUR) return `${Math.floor(abs / HOUR)}小时${Math.floor((abs % HOUR) / MINUTE)}分`
  return `${Math.max(1, Math.floor(abs / MINUTE))}分钟`
}

/**
 * SLA 倒计时状态（2.7 三端统一四态，PC 与移动同一口径）
 * 阈值由调用方传入 = 该优先级 SLA 总时长的 25%（低=12h / 中=6h / 高=2h），
 * 统一阈值后 PC 的「剩余 ≤1h」与移动端「无临近态」两套判定收敛为一套。
 * active=false（已解决/已关闭）时不做超时判定，与后端 overdueUnhandled 口径保持一致。
 * @returns {{ state: 'hidden'|'normal'|'warning'|'over', text: string }}
 */
export function slaState(deadline, active = true, now = Date.now(), thresholdMs = 6 * HOUR) {
  const ts = parseTime(deadline)
  if (!active || !ts) return { state: 'hidden', text: '' }
  const diff = ts - now
  const state = diff < 0 ? 'over' : diff <= thresholdMs ? 'warning' : 'normal'
  return { state, text: `${diff < 0 ? '已超时' : '剩余'} ${durationText(diff)}` }
}

/** 小数 → 百分比文案；取件率为 0 时也显示 0%（不显示 NaN） */
export function percent(value, digits = 1) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `${(num * 100).toFixed(digits)}%`
}

/** 大数字千分位，看板数字可读性更好 */
export function numberText(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : '-'
}

/**
 * 角标数值文案（C4 口径）
 * `0` 是「没有待办」这一确定结论，与「取数失败（null）」是两件事：
 * 前者不渲染角标，后者同样不渲染 —— 绝不能用 `0` 冒充未知（B4-2 硬规则 2），故统一返回空串。
 */
export function badgeText(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return ''
  return num > 99 ? '99+' : String(num)
}

/** 入库时间距今是否超过 n 小时（在库待取超时预警用） */
export function olderThanHours(text, hours) {
  const ts = parseTime(text)
  return !!ts && Date.now() - ts > hours * HOUR
}

/**
 * 金额文本：负数带 `-` 号而不是只靠颜色（C6 SC 1.4.1），千分位改善可读性。
 * 工资单、调薪、我的档案三处都要用，口径写在一处。
 */
export function moneyText(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `${num < 0 ? '-' : ''}￥${Math.abs(num).toLocaleString('zh-CN')}`
}

/** 带单位的数值（KPI 目标值/实际值：件 / % / 分），不做四舍五入以免改口径 */
export function valueText(value, unit = '') {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  return `${num}${unit}`
}

/** 达成率（0–1 小数）→ 百分数：环封顶 100%，但数字显示真实值（C4 KpiGauge 边界态） */
export function rateText(rate) {
  const num = Number(rate)
  if (!Number.isFinite(num)) return '—'
  return `${Math.round(num * 100)}%`
}

/** 近 n 个月（含本月）的 'YYYY-MM' 选项，KPI 与工资单的账期筛选共用 */
export function recentMonths(count = 6, base = new Date()) {
  const list = []
  for (let i = 0; i < count; i += 1) {
    const d = new Date(base.getFullYear(), base.getMonth() - i, 1)
    list.push(`${d.getFullYear()}-${pad(d.getMonth() + 1)}`)
  }
  return list
}

/** 下月 1 日：调薪生效日期的默认值（不早于当前生效日期，避免 9303 口径的前置错误） */
export function nextMonthFirstDay(base = new Date()) {
  const d = new Date(base.getFullYear(), base.getMonth() + 1, 1)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`
}
