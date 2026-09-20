import { parseTime } from '@/shared/domain/time.js'

/**
 * PC 端时间与耗时格式化（同步批次耗时、工单 SLA 与平均处理时长共用）
 * 为什么自己写：不引 dayjs 等库（demo-design.md 10.4 精简约束），Mock 与页面都用 'yyyy-MM-dd HH:mm:ss' 字符串
 */

/**
 * 'yyyy-MM-dd HH:mm:ss' → 时间戳
 * 改为委托 shared/domain/time.js 的 parseTime（P1-8）。两者实现逐字相同（都是把首个空格换成 T 再 new Date），
 * 委托后不存在行为差异，故不加兼容分支；保留本函数只为不改动 PC 侧 ~10 处调用点。
 */
export function parseDateTime(text) {
  return parseTime(text)
}

/**
 * 'HH:mm' → 当日分钟数；'24:00' 按 1440 计
 * 为什么放在 PC 工具层：打卡时段的自定义起止时间要比较先后与重叠，UI 不必为 4 行纯函数反向依赖 mock 层
 */
export function minutesOfDay(text) {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  if (!Number.isFinite(h) || !Number.isFinite(m)) return NaN
  return h * 60 + m
}

/** 当日分钟数 → 'HH:mm'，并在 00:00-24:00 处截断，避免算出 25:30 这类非法时刻 */
export function clockOfMinutes(minutes) {
  const value = Math.min(1440, Math.max(0, Math.round(Number(minutes) || 0)))
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
}

/** 分钟数 → '2 小时 13 分'（不足 1 分按 1 分显示，避免出现「剩 0 分」这类文案） */
export function formatMinutes(minutes) {
  const total = Math.max(1, Math.round(Number(minutes) || 0))
  const hour = Math.floor(total / 60)
  const minute = total % 60
  if (!hour) return `${minute} 分`
  return minute ? `${hour} 小时 ${minute} 分` : `${hour} 小时`
}

/** 两个时间字符串之间的耗时（任一时间缺失或倒序时返回 '—'） */
export function formatDuration(startText, endText) {
  const start = parseDateTime(startText)
  const end = parseDateTime(endText)
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return '—'
  return formatMinutes((end - start) / 60000)
}

/**
 * 通知时间：当天显示 HH:mm，跨天显示「N 天前」（两端统一为相对时间，避免直出完整时间串）
 */
export function formatRelativeTime(text, nowTs = Date.now()) {
  const ts = parseDateTime(text)
  if (Number.isNaN(ts)) return text || '—'
  const dayStart = new Date(nowTs)
  dayStart.setHours(0, 0, 0, 0)
  const startOfToday = dayStart.getTime()
  if (ts >= startOfToday) return String(text).slice(11, 16)
  return `${Math.floor((startOfToday - ts) / 86400000) + 1} 天前`
}

/**
 * 距 SLA 截止时间的剩余文案
 * 返回 over 供页面高亮用（超时未处理判定规则见 demo-design.md 7.4.5，终态由调用方排除）
 */
export function formatRemain(deadlineText, nowTs = Date.now()) {
  const deadline = parseDateTime(deadlineText)
  if (Number.isNaN(deadline)) return { over: false, remainMs: 0, text: '—' }
  const remainMs = deadline - nowTs
  return {
    over: remainMs < 0,
    remainMs,
    text: remainMs < 0 ? `已超 ${formatMinutes(-remainMs / 60000)}` : `剩 ${formatMinutes(remainMs / 60000)}`
  }
}

// 「单定时器共享当前时间」上移到 shared/composables（PC 与移动共用一个订阅源，P1-5）；
// 这里保留转出，既有 `from '../utils/format.js'` 的引用不会断。
export { useNow } from '@/shared/composables/useNow.js'
