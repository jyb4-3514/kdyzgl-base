// S4 · 考勤异常检测：以历史打卡分布做「稳健 z-score 迟到频次」+「连续缺卡」双检测器。
// 运行：node s4-anomaly.mjs
//
// 基线：Mock 无异常检测能力，只有单次打卡的固定阈值（lateThresholdMin=30 / earlyLeaveThresholdMin=30），
//       只能判「这一次是否迟到」，不能回答「这个人是否异常」「是否连续缺卡」。
// 目标：把「跨时间的统计离群」做成可配阈值的检测器，并用注入式标注数据量化 precision/recall。

import { createRandom, fnv1a, gaussian } from './lib/rng.mjs'
import { mad, mean, median, nowMs, robustZ, round, std } from './lib/stats.mjs'

export const ANOMALY_DEFAULT_CONFIG = {
  employees: 200,
  days: 30,
  lateThresholdWarn: 3.5, // 稳健 z 告警阈值
  lateThresholdCritical: 5.0, // 稳健 z 严重阈值
  useRobust: true, // true=median/MAD，false=mean/std（对照用）
  consecutiveAbsentThreshold: 2, // 连续缺卡天数预警阈值
  minSamplesForDetect: 5, // 样本量下限，低于此值不出结论（失败降级：不告警）
  injectLateAnomalies: 10,
  injectAbsentAnomalies: 5
}

/* ============ 数据集（固定种子） ============ */

/** Poisson(λ) 采样（Knuth 算法，λ 小时高效）；固定种子可复现 */
function poisson(random, lambda) {
  const L = Math.exp(-lambda)
  let k = 0
  let p = 1
  do {
    k += 1
    p *= random()
  } while (p > L)
  return k - 1
}

function buildDataset(cfg) {
  const rnd = createRandom(0x9e3779b1)
  const employees = []
  for (let i = 1; i <= cfg.employees; i += 1) {
    employees.push({ id: i, stationId: (i % 7) + 1, lambda: 0.05 + rnd() * 0.35, absentProb: 0.02 + rnd() * 0.05 })
  }
  // 注入迟到异常：λ 放大 6 倍（模拟「频繁迟到」人员）
  const lateAnomalyIds = new Set()
  for (let k = 0; k < cfg.injectLateAnomalies; k += 1) {
    const idx = Math.floor((k * 17 + 3) % cfg.employees)
    employees[idx].lambda *= 6
    lateAnomalyIds.add(employees[idx].id)
  }
  // 注入连续缺卡异常：指定 5 人固定 4 天连休不打卡
  const absentAnomalyIds = new Set()
  for (let k = 0; k < cfg.injectAbsentAnomalies; k += 1) {
    const idx = Math.floor((k * 23 + 7) % cfg.employees)
    employees[idx].absentRun = 4
    absentAnomalyIds.add(employees[idx].id)
  }

  const lateCounts = []
  const absentRuns = []
  employees.forEach((e) => {
    const r = createRandom(fnv1a(`att#${e.id}`))
    let late = 0
    let run = 0
    let maxRun = 0
    for (let d = 0; d < cfg.days; d += 1) {
      late += poisson(r, e.lambda)
      const absent = r() < e.absentProb
      if (absent) {
        run += 1
        maxRun = Math.max(maxRun, run)
      } else {
        run = 0
      }
    }
    if (e.absentRun) maxRun = Math.max(maxRun, e.absentRun)
    lateCounts.push(late)
    absentRuns.push(maxRun)
  })
  return { employees, lateCounts, absentRuns, lateAnomalyIds, absentAnomalyIds }
}

/* ============ 检测器 ============ */

/**
 * 迟到频次检测：返回命中 id 集合。
 * 复杂度：中位数/MAD 只算一次（O(n log n) 排序），打分 O(n)；
 * ⚠️ 若在循环内重复调 robustZ（每次重算 median+MAD）会退化成 O(n² log n)，本实现已规避。
 */
export function detectLateAnomalies(dataset, cfg) {
  const { employees, lateCounts } = dataset
  if (lateCounts.length < cfg.minSamplesForDetect) return new Set()
  const hits = new Set()
  if (cfg.useRobust) {
    const med = median(lateCounts)
    const scale = 1.4826 * mad(lateCounts)
    if (!(scale > 0)) return hits // 全同值：无离群，回落到静态规则
    employees.forEach((e, i) => {
      if ((lateCounts[i] - med) / scale >= cfg.lateThresholdWarn) hits.add(e.id)
    })
  } else {
    const m = mean(lateCounts)
    const s = std(lateCounts)
    employees.forEach((e, i) => {
      if (s > 0 && (lateCounts[i] - m) / s >= cfg.lateThresholdWarn) hits.add(e.id)
    })
  }
  return hits
}

/** 连续缺卡检测：O(n) */
export function detectAbsentRuns(dataset, cfg) {
  const hits = new Set()
  dataset.employees.forEach((e, i) => {
    if (dataset.absentRuns[i] >= cfg.consecutiveAbsentThreshold) hits.add(e.id)
  })
  return hits
}

function prf(pred, truth, n) {
  let tp = 0
  let fp = 0
  let fn = 0
  pred.forEach((id) => (truth.has(id) ? (tp += 1) : (fp += 1)))
  truth.forEach((id) => {
    if (!pred.has(id)) fn += 1
  })
  const precision = tp + fp ? tp / (tp + fp) : 0
  const recall = tp + fn ? tp / (tp + fn) : 0
  const f1 = precision + recall ? (2 * precision * recall) / (precision + recall) : 0
  return { 预测数: pred.size, 实际数: truth.size, 命中TP: tp, 误报FP: fp, 漏报FN: fn, 精度: round(precision, 4), 召回: round(recall, 4), F1: round(f1, 4), 全体: n }
}

export function run() {
  const cfg = ANOMALY_DEFAULT_CONFIG
  const ds = buildDataset(cfg)
  const out = {}

  out.数据集概况 = {
    员工数: cfg.employees,
    天数: cfg.days,
    迟到次数_均值: round(mean(ds.lateCounts), 3),
    迟到次数_中位: median(ds.lateCounts),
    迟到次数_标准差: round(std(ds.lateCounts), 3),
    迟到次数_MAD: round(mad(ds.lateCounts), 3),
    '注入迟到异常数': ds.lateAnomalyIds.size,
    '注入连续缺卡异常数': ds.absentAnomalyIds.size,
    分布偏度提示: '迟到次数右偏 → 均值/标准差被拉高，稳健统计更合适'
  }

  const t0 = nowMs()
  const robustHits = detectLateAnomalies(ds, { ...cfg, useRobust: true })
  const t1 = nowMs()
  const plainHits = detectLateAnomalies(ds, { ...cfg, useRobust: false })
  const t2 = nowMs()
  const absentHits = detectAbsentRuns(ds, cfg)
  const t3 = nowMs()

  out.检测器对比 = {
    '稳健z(median/MAD)': { ...prf(robustHits, ds.lateAnomalyIds, cfg.employees), 耗时ms: round(t1 - t0, 3) },
    '普通z(mean/std)': { ...prf(plainHits, ds.lateAnomalyIds, cfg.employees), 耗时ms: round(t2 - t1, 3) },
    '连续缺卡(阈值2天)': { ...prf(absentHits, ds.absentAnomalyIds, cfg.employees), 耗时ms: round(t3 - t2, 3) }
  }

  // 阈值敏感性：说明阈值为何不能硬编码
  out.阈值敏感性 = [2.5, 3.0, 3.5, 4.0, 5.0].map((th) => {
    const hits = new Set()
    ds.employees.forEach((e, i) => {
      if (robustZ(ds.lateCounts[i], ds.lateCounts) >= th) hits.add(e.id)
    })
    return { 阈值: th, ...prf(hits, ds.lateAnomalyIds, cfg.employees) }
  })

  // 连续缺卡阈值敏感性：阈值越高误报越少、召回越低（口径需用户确认）
  out.连续缺卡阈值敏感性 = [2, 3, 4].map((th) => {
    const hits = detectAbsentRuns(ds, { ...cfg, consecutiveAbsentThreshold: th })
    return { 阈值天数: th, ...prf(hits, ds.absentAnomalyIds, cfg.employees) }
  })

  // 失败降级：样本量不足时不出结论
  out.失败降级 = {
    '样本量3(<5)': detectLateAnomalies({ employees: ds.employees.slice(0, 3), lateCounts: ds.lateCounts.slice(0, 3) }, cfg).size,
    '全同值(MAD=0)': (() => {
      const flat = new Array(50).fill(4)
      let n = 0
      for (let i = 0; i < 50; i += 1) if (robustZ(flat[i], flat) >= 3.5) n += 1
      return n
    })(),
    说明: '样本不足或 MAD=0 时返回空集 → 上层回落到「按静态规则判定」，不阻塞主流程'
  }

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
