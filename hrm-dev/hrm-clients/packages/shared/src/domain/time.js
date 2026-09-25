/**
 * 纯时间工具（零 mock 依赖）
 *
 * 为什么独立成层：时间解析/推算的口径在「展示端」与「假后端」之间必须完全一致，
 * 收在一处才不会出现「页面按本地 0 点算、Mock 按 UTC 算」这类漂移。
 * 时区口径：一律按运行环境本地时区（Asia/Shanghai）解析与格式化。
 */

const PAD = (n) => String(n).padStart(2, '0')
const HOUR = 60 * 60 * 1000

/** Date → yyyy-MM-dd HH:mm:ss（api.md 1.1 时间格式） */
export function formatDateTime(date) {
  const d = date instanceof Date ? date : new Date(date)
  return `${d.getFullYear()}-${PAD(d.getMonth() + 1)}-${PAD(d.getDate())} ${PAD(d.getHours())}:${PAD(
    d.getMinutes()
  )}:${PAD(d.getSeconds())}`
}

/** Date → yyyy-MM-dd */
export function formatDate(date) {
  const d = date instanceof Date ? date : new Date(date)
  return `${d.getFullYear()}-${PAD(d.getMonth() + 1)}-${PAD(d.getDate())}`
}

/** 'yyyy-MM-dd HH:mm:ss' / 'yyyy-MM-dd' → 时间戳（MySQL 字符串比较的替代，统一按时间戳比较） */
export function parseTime(text) {
  if (!text) return NaN
  return new Date(String(text).replace(' ', 'T')).getTime()
}

/** 'yyyy-MM-dd' → 本地 0 点 Date（不用 new Date('yyyy-MM-dd')：它按 UTC 解析，东八区会退回前一天） */
export function parseDate(text) {
  const [y, m, d] = String(text).split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** 当前自然月 'yyyy-MM'（KPI 考核月 / 工资单账期的默认值） */
export const currentMonth = () => formatDate(new Date()).slice(0, 7)

/** 'yyyy-MM' 上/下 delta 个月，跨年自动进位 */
export function monthShift(month, delta = -1) {
  const [y, m] = String(month).split('-').map(Number)
  const date = new Date(y, m - 1 + Number(delta), 1)
  return `${date.getFullYear()}-${PAD(date.getMonth() + 1)}`
}

/** 'yyyy-MM' → 该月起止日期（按月取考勤/评分数据用） */
export function monthRange(month) {
  const [y, m] = String(month).split('-').map(Number)
  const last = new Date(y, m, 0)
  return { startDate: `${y}-${PAD(m)}-01`, endDate: formatDate(last), days: last.getDate() }
}

/** 日期偏移 n 个月（转正日期 / 合同到期日推算用） */
export function addMonths(date, n) {
  const d = new Date(date)
  d.setMonth(d.getMonth() + Number(n))
  return d
}

/** 相对今天偏移 n 天（n 为负表示往前），可指定时分秒 */
export function shiftDays(days, hour = 0, minute = 0, second = 0) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  d.setHours(hour, minute, second, 0)
  return d
}

/** 在给定日期上偏移 n 天（返回新对象，不改原值；排班按周推算时用） */
export function addDays(date, n) {
  const d = new Date(date)
  d.setDate(d.getDate() + n)
  return d
}

/** 所在周的周一 0 点（排班以周一为周首，符合国内考勤习惯） */
export function mondayOf(date = new Date()) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
}

/** 当天 0 点时间戳（今日登录数口径：login_time >= CURDATE()） */
export function todayStart() {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

/** 从当前时间往前推 n 小时，返回接口所需的 'yyyy-MM-dd HH:mm:ss'（时间范围筛选） */
export function hoursAgoParam(hours) {
  const d = new Date(Date.now() - hours * HOUR)
  return `${d.getFullYear()}-${PAD(d.getMonth() + 1)}-${PAD(d.getDate())} ${PAD(d.getHours())}:${PAD(
    d.getMinutes()
  )}:${PAD(d.getSeconds())}`
}
