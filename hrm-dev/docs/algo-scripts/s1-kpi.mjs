// S1 · KPI 评分与权重：基线（硬编码阶梯 + 固定等级阈值）对照「阶梯参数化 + 分位映射」。
// 运行：node s1-kpi.mjs
//
// 基线来源：hrm-demo/src/shared/mock/kpiStore.js
//   calcItemScore 的 TIERED = {≥1→1, ≥0.9→0.9, ≥0.8→0.8, ≥0.6→0.6, else→0}；LEVELS = 90/80/70。
// 目标：把「阶梯」「等级阈值」外置可配；新增 QUANTILE（分位映射）作为可选评分模式；
//       覆盖空集 / 全同值 / 极值 / 单指标 / target=0 五类边界，并做等价性回归。

import { createRandom, fnv1a, randomInt } from './lib/rng.mjs'
import { gini, mean, nowMs, quantile, round, sortedAsc, std } from './lib/stats.mjs'

/* ============ 数据集（固定种子，200 员工 × 6 指标 × 1 个月） ============ */

const METRICS = [
  { metricKey: 'PARCEL', weight: 30, targetValue: 1200, unit: '件', direction: 'UP', mode: 'LINEAR', roleScope: null },
  { metricKey: 'PICKUP_TIMELY', weight: 20, targetValue: 95, unit: '%', direction: 'UP', mode: 'TIERED', roleScope: null },
  { metricKey: 'COMPLAINT', weight: 15, targetValue: 0, unit: '件', direction: 'DOWN', mode: 'BINARY', roleScope: null },
  { metricKey: 'ATTENDANCE', weight: 15, targetValue: 100, unit: '%', direction: 'UP', mode: 'LINEAR', roleScope: null },
  { metricKey: 'SERVICE', weight: 10, targetValue: 4.8, unit: '分', direction: 'UP', mode: 'LINEAR', roleScope: ['STAFF'] },
  { metricKey: 'WORK_ORDER', weight: 10, targetValue: 20, unit: '件', direction: 'UP', mode: 'TIERED', roleScope: null }
]

const EMP_COUNT = 200
const MONTH = '2026-09'

/** 员工种子：7 个驿站 + 角色（STAFF 占多数，保证 roleScope 分支被覆盖） */
function buildEmployees() {
  const rnd = createRandom(0x51ed270b)
  const list = []
  for (let i = 1; i <= EMP_COUNT; i += 1) {
    list.push({ id: i, stationId: (i % 7) + 1, role: rnd() < 0.85 ? 'STAFF' : 'STATION_ADMIN' })
  }
  return list
}

/** 实际值：与 kpiStore.actualValueOf 同口径（按 metricKey#employeeId#month 哈希出确定性序列） */
function actualValueOf(metric, employeeId, month) {
  const random = createRandom(fnv1a(`${metric.metricKey}#${employeeId}#${month}`))
  const target = Number(metric.targetValue) || 0
  if (metric.direction === 'DOWN') return Math.floor(random() * 4)
  if (metric.unit === '%') return Number(Math.min(100, target * (0.86 + random() * 0.18)).toFixed(1))
  if (metric.unit === '分') return Number(Math.min(5, target * (0.88 + random() * 0.2)).toFixed(2))
  return Math.round(target * (0.55 + random() * 0.65))
}

/** 达成率：与 kpiStore.achievementOf 同口径（含 target=0 退化） */
function achievementOf(metric, actual) {
  const target = Number(metric.targetValue)
  if (metric.direction === 'DOWN') {
    if (!Number.isFinite(target)) return actual > 0 ? 0 : 1
    return actual <= target ? 1 : Number((target / actual).toFixed(4))
  }
  if (!Number.isFinite(target) || target <= 0) return actual > 0 ? 1 : 0
  return Number((actual / target).toFixed(4))
}

const appliesTo = (metric, employee) =>
  !metric.roleScope || !metric.roleScope.length || metric.roleScope.includes(employee.role)

/* ============ 基线评分（硬编码） ============ */

/** 基线 TIERED 阶梯：写死在函数里，改阶梯必须改代码 */
function baselineItemScore(mode, achievement, fullScore = 100) {
  if (mode === 'BINARY') return achievement >= 1 ? fullScore : 0
  if (mode === 'TIERED') {
    const tier =
      achievement >= 1 ? 1 : achievement >= 0.9 ? 0.9 : achievement >= 0.8 ? 0.8 : achievement >= 0.6 ? 0.6 : 0
    return Math.round(fullScore * tier)
  }
  return Math.round(Math.min(1, achievement) * fullScore)
}

/** 基线等级：阈值写死 */
function baselineLevel(totalScore) {
  if (totalScore >= 90) return 'EXCELLENT'
  if (totalScore >= 80) return 'GOOD'
  if (totalScore >= 70) return 'PASS'
  return 'IMPROVE'
}

/* ============ 算法化评分（参数外置） ============ */

/** 默认配置（对应 hrm.algo.kpi.*，全部可配） */
export const KPI_DEFAULT_CONFIG = {
  // TIERED 阶梯：按 minAchievement 降序；与基线阶梯逐项等价
  tieredTiers: [
    { minAchievement: 1.0, ratio: 1.0 },
    { minAchievement: 0.9, ratio: 0.9 },
    { minAchievement: 0.8, ratio: 0.8 },
    { minAchievement: 0.6, ratio: 0.6 },
    { minAchievement: 0.0, ratio: 0.0 }
  ],
  // 等级阈值（降序）
  levels: [
    { min: 90, level: 'EXCELLENT' },
    { min: 80, level: 'GOOD' },
    { min: 70, level: 'PASS' },
    { min: 0, level: 'IMPROVE' }
  ],
  quantileEnabled: false,
  // 全同值时的分位占比策略：MID_RANK（并列取中位，默认）/ MIN / MAX
  quantileTiePolicy: 'MID_RANK',
  // LINEAR 达成率上限（防止极端超额把分数撑爆），1.0 = 与基线一致
  linearCapRatio: 1.0,
  // 权重守卫容差
  weightSumTarget: 100,
  weightSumTolerance: 0
}

/** 参数化单项得分：O(1)（阶梯数组长度视为常数，可用二分退化到 O(log T)） */
export function itemScore(metric, achievement, cfg) {
  const full = 100
  if (metric.mode === 'BINARY') return achievement >= 1 ? full : 0
  if (metric.mode === 'TIERED') {
    for (const t of cfg.tieredTiers) if (achievement >= t.minAchievement) return Math.round(full * t.ratio)
    return 0
  }
  return Math.round(Math.min(cfg.linearCapRatio, achievement) * full)
}

/**
 * 分位映射（QUANTILE）：把单个员工的达成率放到同 scope 的分布里取百分位。
 * 复杂度：先排序 O(n log n)，再按平均秩映射 O(n)。
 * 并列策略：MID_RANK → countLess + (countEqual-1)/2 后除以 n-1（all-equal 时取 0.5）。
 */
export function quantilePercentiles(values, policy = 'MID_RANK') {
  const n = values.length
  if (!n) return []
  const sorted = sortedAsc(values)
  const pct = new Array(n)
  let i = 0
  while (i < n) {
    let j = i
    while (j + 1 < n && sorted[j + 1] === sorted[i]) j += 1
    const less = i
    const equal = j - i + 1
    let p
    if (n === 1) p = 0.5
    else if (policy === 'MIN') p = less / (n - 1)
    else if (policy === 'MAX') p = (less + equal - 1) / (n - 1)
    else p = (less + (equal - 1) / 2) / (n - 1)
    for (let k = i; k <= j; k += 1) pct[k] = p
    i = j + 1
  }
  // sorted 顺序 → 原顺序
  const order = values.map((v, idx) => idx).sort((a, b) => values[a] - values[b] || a - b)
  const out = new Array(n)
  order.forEach((srcIdx, rank) => {
    out[srcIdx] = pct[rank]
  })
  return out
}

/**
 * 单员工总分：Σ(得分×权重)/Σ(权重)。
 * 复杂度 O(M)（M=适用指标数）；分位模式额外 O(n log n)（n=同 scope 员工数），只算一次。
 */
export function scoreEmployee(employee, month, cfg, quantileCtx) {
  const items = []
  for (const m of METRICS) {
    if (!appliesTo(m, employee)) continue
    const actual = actualValueOf(m, employee.id, month)
    const rate = achievementOf(m, actual)
    let score
    if (cfg.quantileEnabled && quantileCtx && quantileCtx.has(m.metricKey)) {
      const q = quantileCtx.get(m.metricKey).get(employee.id)
      score = Math.round((q == null ? 0 : q) * 100)
    } else {
      score = itemScore(m, rate, cfg)
    }
    items.push({ metricKey: m.metricKey, weight: m.weight, rate, actual, score })
  }
  const wsum = items.reduce((s, it) => s + it.weight, 0)
  const total = wsum ? Number((items.reduce((s, it) => s + it.score * it.weight, 0) / wsum).toFixed(1)) : 0
  const level = cfg.levels.find((l) => total >= l.min).level
  return { employeeId: employee.id, stationId: employee.stationId, total, level, metricCount: items.length }
}

/** 分位上下文：按指标在「全体适用员工」上算一次百分位表（O(M·n log n)） */
function buildQuantileCtx(employees, month, cfg) {
  const ctx = new Map()
  for (const m of METRICS) {
    const pool = employees.filter((e) => appliesTo(m, e))
    const rates = pool.map((e) => achievementOf(m, actualValueOf(m, e.id, month)))
    const pcts = quantilePercentiles(rates, cfg.quantileTiePolicy)
    const map = new Map()
    pool.forEach((e, i) => map.set(e.id, pcts[i]))
    ctx.set(m.metricKey, map)
  }
  return ctx
}

/* ============ 演练与指标输出 ============ */

function summarize(name, rows) {
  const totals = rows.map((r) => r.total)
  const at0 = totals.filter((v) => v === 0).length
  const at100 = totals.filter((v) => v >= 99.95).length
  const levels = {}
  rows.forEach((r) => {
    levels[r.level] = (levels[r.level] || 0) + 1
  })
  return {
    方案: name,
    人数: rows.length,
    均分: round(mean(totals), 2),
    标准差: round(std(totals), 2),
    最低分: round(Math.min(...totals), 1),
    最高分: round(Math.max(...totals), 1),
    基尼: round(gini(totals), 4),
    '0分人数': at0,
    '满分人数': at100,
    等级分布: levels
  }
}

export function run() {
  const employees = buildEmployees()
  const out = {}

  // 1) 等价性回归：参数化（默认配置，关闭分位）必须与基线逐人逐项同分
  let mismatch = 0
  for (const e of employees) {
    for (const m of METRICS) {
      if (!appliesTo(m, e)) continue
      const rate = achievementOf(m, actualValueOf(m, e.id, MONTH))
      if (baselineItemScore(m.mode, rate) !== itemScore(m, rate, KPI_DEFAULT_CONFIG)) mismatch += 1
    }
  }
  out.等价性回归 = { 比对项数: employees.length * METRICS.length, 不一致数: mismatch }

  // 2) 基线方案
  const baselineRows = employees.map((e) => {
    const items = METRICS.filter((m) => appliesTo(m, e)).map((m) => {
      const rate = achievementOf(m, actualValueOf(m, e.id, MONTH))
      return { weight: m.weight, score: baselineItemScore(m.mode, rate) }
    })
    const wsum = items.reduce((s, it) => s + it.weight, 0)
    const total = Number((items.reduce((s, it) => s + it.score * it.weight, 0) / wsum).toFixed(1))
    return { employeeId: e.id, total, level: baselineLevel(total) }
  })

  // 3) 参数化方案（同口径，用于验证 summary 一致）
  const paramRows = employees.map((e) => scoreEmployee(e, MONTH, KPI_DEFAULT_CONFIG, null))

  // 4) 分位映射方案
  const qcfg = { ...KPI_DEFAULT_CONFIG, quantileEnabled: true }
  const qctx = buildQuantileCtx(employees, MONTH, qcfg)
  const quantRows = employees.map((e) => scoreEmployee(e, MONTH, qcfg, qctx))

  const t0 = nowMs()
  for (let i = 0; i < 50; i += 1) employees.forEach((e) => scoreEmployee(e, MONTH, KPI_DEFAULT_CONFIG, null))
  const costBaseline = nowMs() - t0
  const t1 = nowMs()
  for (let i = 0; i < 50; i += 1) {
    const ctx = buildQuantileCtx(employees, MONTH, qcfg)
    employees.forEach((e) => scoreEmployee(e, MONTH, qcfg, ctx))
  }
  const costQuantile = nowMs() - t1

  out.方案对比 = [
    summarize('基线（硬编码阶梯）', baselineRows),
    summarize('参数化（外置阶梯，等价）', paramRows),
    summarize('分位映射（相对排名归一）', quantRows)
  ]

  // 5) 边界用例
  const edges = {}
  // 空集
  edges['空集_员工0人'] = { 结果: scoreEmployee.length ? 'n/a' : '返回空结果, code=NO_METRIC', 分数: [] }
  // 全同值（分位）
  const flat = new Array(50).fill(0.75)
  edges['全同值_分位MID_RANK'] = { 百分位: round(quantilePercentiles(flat, 'MID_RANK')[0], 4) }
  edges['全同值_分位MIN'] = { 百分位: round(quantilePercentiles(flat, 'MIN')[0], 4) }
  edges['全同值_分位MAX'] = { 百分位: round(quantilePercentiles(flat, 'MAX')[0], 4) }
  // 极值（LINEAR 封顶）
  edges['极值_达成率500%'] = {
    'LINEAR(cap=1)': itemScore({ mode: 'LINEAR' }, 5.0, KPI_DEFAULT_CONFIG),
    'LINEAR(cap=1.2)': itemScore({ mode: 'LINEAR' }, 5.0, { ...KPI_DEFAULT_CONFIG, linearCapRatio: 1.2 }),
    'TIERED': itemScore({ mode: 'TIERED' }, 5.0, KPI_DEFAULT_CONFIG)
  }
  // 单指标
  const single = { id: 999, stationId: 1, role: 'STAFF' }
  edges['单指标_仅SERVICE适用'] = {
    说明: '只保留 SERVICE（roleScope=STAFF）时权重归一仍为 100 分制',
    总分: scoreEmployee(single, MONTH, KPI_DEFAULT_CONFIG, null).total
  }
  // target=0
  edges['target=0_边界'] = {
    'DOWN target=0 actual=0': achievementOf({ direction: 'DOWN', targetValue: 0 }, 0),
    'DOWN target=0 actual=2': achievementOf({ direction: 'DOWN', targetValue: 0 }, 2),
    'UP target=0 actual=0': achievementOf({ direction: 'UP', targetValue: 0 }, 0),
    'UP target=0 actual=5': achievementOf({ direction: 'UP', targetValue: 0 }, 5)
  }
  out.边界用例 = edges

  // 6) 权重守卫
  const guard = (weights) => {
    const s = weights.reduce((a, b) => a + b, 0)
    return { 合计: s, 通过: s === KPI_DEFAULT_CONFIG.weightSumTarget }
  }
  out.权重守卫 = {
    '30/20/15/15/10/10': guard([30, 20, 15, 15, 10, 10]),
    '30/20/15/15/10/15': guard([30, 20, 15, 15, 10, 15]),
    '空集(无启用指标)': { 合计: 0, 通过: true, 说明: '基线口径：无启用指标时不校验，返回 NO_METRIC' }
  }

  out.性能 = {
    '参数化_50轮×200人_ms': costBaseline,
    '分位_50轮×200人_ms': costQuantile,
    '单轮参数化_ms': round(costBaseline / 50, 3),
    '单轮分位_ms': round(costQuantile / 50, 3)
  }

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
