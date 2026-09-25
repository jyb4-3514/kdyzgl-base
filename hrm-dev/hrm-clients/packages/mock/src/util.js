/**
 * Mock 层公共工具：仅保留「假后端专有」的随机/响应构造
 * 为什么自带 PRNG 而不用 Math.random：演示数据必须「同参数多次查询结果稳定」，
 * 否则刷新一次看板数字就变，评审会质疑数据真实性（T03/T05 验收项）
 */

/** mulberry32：32 位种子 PRNG，返回 [0,1) 浮点；同种子序列恒定（算法公开、无依赖） */
export function createRandom(seed = 20260917) {
  let a = seed >>> 0
  return function random() {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** [min, max] 闭区间整数 */
export function randomInt(random, min, max) {
  return min + Math.floor(random() * (max - min + 1))
}

/** 按权重随机取下标（weights 与 items 等长） */
export function randomWeighted(random, weights) {
  const total = weights.reduce((sum, w) => sum + w, 0)
  let hit = random() * total
  for (let i = 0; i < weights.length; i += 1) {
    hit -= weights[i]
    if (hit <= 0) return i
  }
  return weights.length - 1
}

/** 从数组取一项 */
export function pickOne(random, list) {
  return list[randomInt(random, 0, list.length - 1)]
}

/** 浅拷贝实体，避免调用方改到种子数据 */
export function clone(row) {
  return row ? { ...row } : row
}

/** 统一成功响应 */
export function ok(data = null, message = 'success') {
  return { code: 200, message, data }
}

/** 统一失败响应（body.code 即业务码；HTTP 状态码由 engine 按 401/403/404 同步） */
export function fail(code, message) {
  return { code, message, data: null }
}

// 时间 / 分页 / CSV / 脱敏工具已上移到 shared/domain（纯工具层，零 mock 依赖），此处 re-export 保持 Mock 内部引用不破
export {
  formatDateTime,
  formatDate,
  parseTime,
  parseDate,
  currentMonth,
  monthShift,
  monthRange,
  addMonths,
  shiftDays,
  addDays,
  mondayOf,
  todayStart
} from '@kdyzgl/shared/domain/time.js'
export { paginate } from '@kdyzgl/shared/domain/pagination.js'
export { CSV_TYPE, escapeCsv, toCsvBlob, csvDisposition, parseCsv } from '@kdyzgl/shared/domain/csv.js'
export { maskPhone, maskName, maskBankAccount } from '@kdyzgl/shared/domain/mask.js'
