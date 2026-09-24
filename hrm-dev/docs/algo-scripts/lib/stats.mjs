// 统计工具：原型与未来 Java 实现共用的口径定义（纯函数、无副作用）。
// 复杂度标注：均为一趟或排序级，见各自注释。

export const sum = (arr) => arr.reduce((s, v) => s + v, 0)

export const mean = (arr) => (arr.length ? sum(arr) / arr.length : 0)

/** 样本标准差（n-1 分母）；n<2 返回 0 */
export function std(arr) {
  if (arr.length < 2) return 0
  const m = mean(arr)
  return Math.sqrt(arr.reduce((s, v) => s + (v - m) ** 2, 0) / (arr.length - 1))
}

/** 升序排序副本 */
export const sortedAsc = (arr) => arr.slice().sort((a, b) => a - b)

/** 线性插值分位数（type=7，与 Excel PERCENTILE.INC / numpy default 同法） */
export function quantile(arr, p) {
  if (!arr.length) return 0
  const a = sortedAsc(arr)
  const idx = (a.length - 1) * Math.min(1, Math.max(0, p))
  const lo = Math.floor(idx)
  const hi = Math.ceil(idx)
  if (lo === hi) return a[lo]
  return a[lo] + (a[hi] - a[lo]) * (idx - lo)
}

export const median = (arr) => quantile(arr, 0.5)

/** 中位绝对偏差 MAD（稳健离散度）；返回未乘 1.4826 的原始值 */
export const mad = (arr) => (arr.length ? median(arr.map((v) => Math.abs(v - median(arr)))) : 0)

/** 稳健 z 分数：MAD=0 时退化为 0（无法判定离群，交由调用方给规则兜底） */
export function robustZ(value, arr) {
  const med = median(arr)
  const scale = 1.4826 * mad(arr)
  if (!(scale > 0)) return 0
  return (value - med) / scale
}

/** 基尼系数（0=完全均等，1=极度集中）；用于衡量分数/负载分布是否被拉爆 */
export function gini(arr) {
  const a = sortedAsc(arr)
  const n = a.length
  const total = sum(a)
  if (!n || total <= 0) return 0
  let cum = 0
  let acc = 0
  for (let i = 0; i < n; i += 1) {
    acc += a[i]
    cum += acc
  }
  return (n + 1 - (2 * cum) / total) / n
}

/** Jain 公平指数（1/n=最差，1=完全公平）；负载均衡类指标的标准度量 */
export function jainIndex(arr) {
  const n = arr.length
  if (!n) return 0
  const s = sum(arr)
  const sq = sum(arr.map((v) => v * v))
  if (sq === 0) return 1
  return (s * s) / (n * sq)
}

export const mae = (actual, pred) =>
  actual.length ? sum(actual.map((v, i) => Math.abs(v - pred[i]))) / actual.length : 0

/** MAPE：实际值为 0 的样本按跳过处理（避免除零放大），返回跳过后的均值 */
export function mape(actual, pred) {
  let acc = 0
  let n = 0
  actual.forEach((v, i) => {
    if (Math.abs(v) < 1e-9) return
    acc += Math.abs((v - pred[i]) / v)
    n += 1
  })
  return n ? acc / n : 0
}

/** 高精度计时（毫秒，保留 3 位）；Node 全局 performance 可用 */
export function nowMs() {
  return Number(performance.now().toFixed(3))
}

export const round = (v, d = 2) => Number(Number(v).toFixed(d))
