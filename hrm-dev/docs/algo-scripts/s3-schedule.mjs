// S3 · 考勤排班生成：基线（Mock 朴素轮转）对照「贪心构造 + 局部搜索」。
// 运行：node s3-schedule.mjs
//
// 基线来源：hrm-demo/src/shared/mock/attendanceStore.js buildSchedules：
//   轮休 = (empIdx + dayIdx) % 6 === 5；班次 = (empIdx + dayIdx) % 3。
//   ⚠️ 该朴素法的结构性缺陷：休息条件 %6===5 蕴含 %3===2，即「轮休恒落在同一班次（第 3 班）」，
//   造成班次覆盖率系统性偏移 —— 本脚本用指标量化它。
// 目标：约束满足（每日每班最少在岗、连续工作上限、轮休均衡、班次覆盖）下的可解释排班。

import { createRandom, randomInt } from './lib/rng.mjs'
import { gini, jainIndex, nowMs, round, std } from './lib/stats.mjs'

export const SCHEDULE_DEFAULT_CONFIG = {
  employees: 8,
  days: 30,
  shifts: 3,
  minPerShift: 2, // 每班每日最少在岗人数
  maxConsecutiveWork: 5, // 单员工连续工作天数上限
  restCycleDays: 6, // 轮休周期（D/6 ≈ 每人 5 天休）
  // 目标函数权重（全部可配；违反硬约束的惩罚远大于均衡偏好）
  wMinStaff: 1000,
  wConsecutive: 1000,
  wCoverageDeficit: 10,
  wShiftBalance: 1,
  wRestSpread: 0.1,
  localSearchIterations: 6000,
  saInitialTemp: 8,
  saCooling: 0.9995
}

/* ============ 基线：朴素轮转 ============ */

export function buildNaiveSchedule(cfg) {
  const { employees, days, shifts } = cfg
  const rest = Array.from({ length: employees }, () => new Array(days).fill(false))
  const shift = Array.from({ length: employees }, () => new Array(days).fill(-1))
  for (let d = 0; d < days; d += 1) {
    for (let e = 0; e < employees; e += 1) {
      if ((e + d) % cfg.restCycleDays === cfg.restCycleDays - 1) {
        rest[e][d] = true
        continue
      }
      shift[e][d] = (e + d) % shifts
    }
  }
  return { rest, shift }
}

/* ============ 贪心构造 ============ */

/**
 * Phase1 轮休铺排：rest(d) = (d + e) % cycle == cycle-1
 *   保证每人每 cycle 休 1 天（30 天 → 5 天），最大连续工作 = cycle-1，且休息日与班次解耦。
 * Phase2 班次分配：逐日贪心 —— 优先补「当前缺口最大的班次」，并列时给「该员工最少的班次」。
 * 复杂度：O(D × E × S)。
 */
export function buildGreedySchedule(cfg) {
  const { employees, days, shifts } = cfg
  const rest = Array.from({ length: employees }, () => new Array(days).fill(false))
  const shift = Array.from({ length: employees }, () => new Array(days).fill(-1))

  for (let d = 0; d < days; d += 1) {
    const onDuty = []
    for (let e = 0; e < employees; e += 1) {
      if ((d + e) % cfg.restCycleDays === cfg.restCycleDays - 1) rest[e][d] = true
      else onDuty.push(e)
    }
    const count = new Array(shifts).fill(0)
    const empShiftCount = onDuty.map((e) => {
      const row = shift[e]
      const c = new Array(shifts).fill(0)
      for (let k = 0; k < d; k += 1) if (row[k] >= 0) c[row[k]] += 1
      return c
    })
    // 先按「最短缺」把在岗人归到班次
    const order = onDuty.slice().sort((a, b) => a - b)
    for (const e of order) {
      let best = 0
      let bestScore = -Infinity
      for (let s = 0; s < shifts; s += 1) {
        const deficit = (cfg.minPerShift - count[s]) * 100 - empShiftCount[onDuty.indexOf(e)][s]
        if (deficit > bestScore) {
          bestScore = deficit
          best = s
        }
      }
      shift[e][d] = best
      count[best] += 1
    }
  }
  return { rest, shift }
}

/* ============ 目标函数与评估 ============ */

/** O(E × D) */
export function evaluate(sol, cfg) {
  const { employees, days, shifts } = cfg
  const perDayPerShift = Array.from({ length: days }, () => new Array(shifts).fill(0))
  const perEmpShift = Array.from({ length: employees }, () => new Array(shifts).fill(0))
  const restDays = new Array(employees).fill(0)
  let maxRun = 0
  let consecutiveViolations = 0

  for (let e = 0; e < employees; e += 1) {
    let run = 0
    for (let d = 0; d < days; d += 1) {
      if (sol.shift[e][d] >= 0) {
        perDayPerShift[d][sol.shift[e][d]] += 1
        perEmpShift[e][sol.shift[e][d]] += 1
        run += 1
        maxRun = Math.max(maxRun, run)
        if (run > cfg.maxConsecutiveWork) consecutiveViolations += 1
      } else {
        restDays[e] += 1
        run = 0
      }
    }
  }

  // 每日每班最少在岗缺口
  let minStaffViolationCells = 0
  let coverageDeficit = 0
  for (let d = 0; d < days; d += 1) {
    for (let s = 0; s < shifts; s += 1) {
      if (perDayPerShift[d][s] < cfg.minPerShift) {
        minStaffViolationCells += 1
        coverageDeficit += cfg.minPerShift - perDayPerShift[d][s]
      }
    }
  }

  // 班次分配均衡：每员工各班长度的方差之和
  let shiftBalance = 0
  for (let e = 0; e < employees; e += 1) shiftBalance += std(perEmpShift[e]) ** 2

  const restSpread = std(restDays)
  const J =
    cfg.wMinStaff * minStaffViolationCells +
    cfg.wConsecutive * consecutiveViolations +
    cfg.wCoverageDeficit * coverageDeficit +
    cfg.wShiftBalance * shiftBalance +
    cfg.wRestSpread * restSpread

  return {
    J: round(J, 3),
    minStaffViolationCells,
    consecutiveViolations,
    maxConsecutive: maxRun,
    restDaysStd: round(restSpread, 3),
    restDays: restDays.slice(),
    shiftBalanceStdSum: round(shiftBalance, 3),
    perDayPerShift,
    perEmpShift
  }
}

/* ============ 局部搜索（模拟退火，固定种子可复现） ============ */

export function localSearch(sol, cfg, seed = 0x1f2e3d4c) {
  const rnd = createRandom(seed)
  const clone = (s) => ({ rest: s.rest.map((r) => r.slice()), shift: s.shift.map((r) => r.slice()) })
  let cur = clone(sol)
  let curEval = evaluate(cur, cfg)
  let best = clone(cur)
  let bestEval = curEval
  let temp = cfg.saInitialTemp
  let accepted = 0
  const trace = [curEval.J]

  for (let it = 0; it < cfg.localSearchIterations; it += 1) {
    const cand = clone(cur)
    const move = rnd()
    if (move < 0.5) {
      // 移动 1：同日两员工互换班次（只动班次，不动轮休）
      const d = randomInt(rnd, 0, cfg.days - 1)
      const working = []
      for (let e = 0; e < cfg.employees; e += 1) if (cand.shift[e][d] >= 0) working.push(e)
      if (working.length >= 2) {
        const i = randomInt(rnd, 0, working.length - 1)
        let j = randomInt(rnd, 0, working.length - 1)
        if (i === j) j = (j + 1) % working.length
        const a = working[i]
        const b = working[j]
        const t = cand.shift[a][d]
        cand.shift[a][d] = cand.shift[b][d]
        cand.shift[b][d] = t
      }
    } else {
      // 移动 2：同日「在岗 ↔ 轮休」对调（改变轮休分布，用于修最少在岗缺口）
      const d = randomInt(rnd, 0, cfg.days - 1)
      const resting = []
      const working = []
      for (let e = 0; e < cfg.employees; e += 1) (cand.shift[e][d] >= 0 ? working : resting).push(e)
      if (resting.length && working.length) {
        const r = resting[randomInt(rnd, 0, resting.length - 1)]
        const w = working[randomInt(rnd, 0, working.length - 1)]
        cand.rest[r][d] = false
        cand.rest[w][d] = true
        cand.shift[r][d] = cand.shift[w][d]
        cand.shift[w][d] = -1
      }
    }

    const candEval = evaluate(cand, cfg)
    const delta = candEval.J - curEval.J
    if (delta <= 0 || rnd() < Math.exp(-delta / temp)) {
      cur = cand
      curEval = candEval
      accepted += 1
      if (candEval.J < bestEval.J) {
        best = clone(cand)
        bestEval = candEval
      }
    }
    temp *= cfg.saCooling
    if (it % 500 === 0) trace.push(round(bestEval.J, 3))
  }
  return { solution: best, evaluation: bestEval, accepted, trace }
}

/* ============ 演练 ============ */

function coverageStats(evalResult, cfg) {
  let cells = 0
  let satisfied = 0
  for (let d = 0; d < cfg.days; d += 1) {
    for (let s = 0; s < cfg.shifts; s += 1) {
      cells += 1
      if (evalResult.perDayPerShift[d][s] >= cfg.minPerShift) satisfied += 1
    }
  }
  const loads = evalResult.perEmpShift.map((row) => row.reduce((a, b) => a + b, 0))
  return {
    覆盖达标格数: satisfied,
    总格数: cells,
    覆盖率: round(satisfied / cells, 4),
    单格最少在岗: Math.min(...evalResult.perDayPerShift.flat()),
    单格最多在岗: Math.max(...evalResult.perDayPerShift.flat()),
    员工负载基尼: round(gini(loads.map((v) => (v > 0 ? v : 1e-9))), 4),
    负载Jain: round(jainIndex(loads), 4)
  }
}

export function run() {
  const cfg = SCHEDULE_DEFAULT_CONFIG
  const out = {}

  const t0 = nowMs()
  const naive = buildNaiveSchedule(cfg)
  const naiveEval = evaluate(naive, cfg)
  const t1 = nowMs()
  const greedy = buildGreedySchedule(cfg)
  const greedyEval = evaluate(greedy, cfg)
  const t2 = nowMs()
  const sa = localSearch(greedy, cfg)
  const t3 = nowMs()

  const series = (name, ev, ms) => ({ 方案: name, 目标函数J: ev.J, 运行ms: ms, ...coverageStats(ev, cfg) })

  out.对比例 = [
    series('基线·朴素轮转', naiveEval, round(t1 - t0, 3)),
    series('贪心构造', greedyEval, round(t2 - t1, 3)),
    series('贪心+模拟退火', sa.evaluation, round(t3 - t2, 3))
  ]

  out.约束明细 = {
    基线: {
      最少在岗违规格数: naiveEval.minStaffViolationCells,
      连续工作违规次数: naiveEval.consecutiveViolations,
      最长连续工作: naiveEval.maxConsecutive,
      轮休天数: naiveEval.restDays,
      轮休标准差: naiveEval.restDaysStd,
      各班次总人次: cfg.shifts ? naiveEval.perEmpShift.reduce((acc, row) => row.map((v, i) => (acc[i] || 0) + v), []) : []
    },
    退火后: {
      最少在岗违规格数: sa.evaluation.minStaffViolationCells,
      连续工作违规次数: sa.evaluation.consecutiveViolations,
      最长连续工作: sa.evaluation.maxConsecutive,
      轮休天数: sa.evaluation.restDays,
      轮休标准差: sa.evaluation.restDaysStd,
      各班次总人次: sa.evaluation.perEmpShift.reduce((acc, row) => acc.map((v, i) => v + row[i]), new Array(cfg.shifts).fill(0))
    }
  }

  out.退火过程 = { 接受次数: sa.accepted, 目标函数轨迹: sa.trace }

  // 复杂度实证：不同规模下的构造 + 评估耗时
  out.规模曲线 = [8, 20, 50].map((emps) => {
    const c = { ...cfg, employees: emps, localSearchIterations: 0 }
    const s0 = nowMs()
    const sol = buildGreedySchedule(c)
    const s1 = nowMs()
    evaluate(sol, c)
    const s2 = nowMs()
    return { 员工数: emps, 天数: c.days, 班次数: c.shifts, '构造ms': round(s1 - s0, 3), '评估ms': round(s2 - s1, 3) }
  })

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
