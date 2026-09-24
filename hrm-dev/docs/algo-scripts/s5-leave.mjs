// S5 · 请假计薪天数与重叠判定：半天单元「区间算法」替代逐日扫描。
// 运行：node s5-leave.mjs
//
// 基线来源：hrm-demo/src/shared/mock/leaveStore.js
//   halfUnitsOf 逐日生成 `${date}#AM/PM` 字符串集合；countedDaysOf 每次 new Set(scheduledDatesOf(...))；
//   findOverlap 对每条已有单重算 halfUnitsOf 再求交集；monthSpansOf 逐日推月份。
// 目标：把「日期区间」映射为「半天单元整数区间」→ 自然天数 O(1)、计薪天数 O(log m)、
//       重叠判定 O(log k)、账期跨度 O(1)，并用等价性回归证明结果与逐日法一致。

import { createRandom, randomInt } from './lib/rng.mjs'
import { nowMs, round } from './lib/stats.mjs'

export const LEAVE_DEFAULT_CONFIG = {
  baseDate: '2026-01-01',
  employees: 500,
  yearDays: 365,
  workRatio: 5 / 6, // 每人每 6 天休 1 天
  occupiedLeaves: 2000,
  candidateLeaves: 2000,
  countedQueries: 20000,
  maxLeaveDays: 30
}

/* ============ 日期 ↔ 单元索引（O(1)） ============ */

const DAY_MS = 86400000
const parseDay = (dateStr) => Date.parse(`${dateStr}T00:00:00Z`)

export function makeCalendar(cfg) {
  const base = parseDay(cfg.baseDate)
  const ord = (dateStr) => Math.round((parseDay(dateStr) - base) / DAY_MS)
  const dateOf = (ordinal) => new Date(base + ordinal * DAY_MS).toISOString().slice(0, 10)
  // 单元索引 = 日序 × 2 + (AM=0 / PM=1)
  const unitOf = (dateStr, period) => ord(dateStr) * 2 + (period === 'AM' ? 0 : 1)
  const monthIndexOf = (ordinal) => {
    const d = new Date(base + ordinal * DAY_MS)
    return d.getUTCFullYear() * 12 + d.getUTCMonth()
  }
  const monthLabel = (monthIndex) => `${Math.floor(monthIndex / 12)}-${String((monthIndex % 12) + 1).padStart(2, '0')}`
  return { ord, dateOf, unitOf, monthIndexOf, monthLabel, base }
}

/** 半天单元区间：[startUnit, endUnit]（含端点）；同日 PM→AM 得到 endUnit < startUnit 即非法 */
export function unitRange(cal, form) {
  const startUnit = cal.unitOf(form.startDate, form.startPeriod)
  const endUnit = cal.unitOf(form.endDate, form.endPeriod)
  return { startUnit, endUnit, valid: endUnit >= startUnit }
}

/** 自然天数 O(1) */
export function naturalDaysByRange(cal, form) {
  const r = unitRange(cal, form)
  return r.valid ? (r.endUnit - r.startUnit + 1) / 2 : 0
}

/* ============ 基线：逐日法 ============ */

/** 逐日生成半天单元 token（字符串集合）——与 Mock halfUnitsOf 同实现 */
export function halfUnitsByDay(form) {
  const units = []
  const last = parseDay(form.endDate)
  for (let ms = parseDay(form.startDate); ms <= last; ms += DAY_MS) {
    const date = new Date(ms).toISOString().slice(0, 10)
    if (!(date === form.startDate && form.startPeriod === 'PM')) units.push(`${date}#AM`)
    if (!(date === form.endDate && form.endPeriod === 'AM')) units.push(`${date}#PM`)
  }
  return units
}

/** 基线计薪天数：逐日查排班（Set 过滤整张排班表，O(S_e)） */
export function countedDaysBaseline(scheduleDays, form) {
  const scheduled = new Set(scheduleDays)
  const units = halfUnitsByDay(form).filter((token) => scheduled.has(token.slice(0, 10)))
  return units.length / 2
}

/** 基线重叠：对每条已有单重算 token 再求交集 O(k·L) */
export function findOverlapBaseline(leaves, form) {
  const target = new Set(halfUnitsByDay(form))
  for (const l of leaves) {
    const tokens = halfUnitsByDay(l.form)
    if (tokens.some((t) => target.has(t))) return l
  }
  return null
}

/** 基线账期跨度：逐日推月份 */
export function monthSpansBaseline(cal, form) {
  const months = new Set()
  const last = parseDay(form.endDate)
  for (let ms = parseDay(form.startDate); ms <= last; ms += DAY_MS) {
    const d = new Date(ms)
    months.add(cal.monthLabel(d.getUTCFullYear() * 12 + d.getUTCMonth()))
  }
  return [...months]
}

/* ============ 算法化：区间 + 二分 ============ */

const lowerBound = (arr, x) => {
  let lo = 0
  let hi = arr.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (arr[mid] < x) lo = mid + 1
    else hi = mid
  }
  return lo
}
const upperBound = (arr, x) => {
  let lo = 0
  let hi = arr.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (arr[mid] <= x) lo = mid + 1
    else hi = mid
  }
  return lo
}

/**
 * 算法化计薪天数：在「已排序的排班单元数组」上做两次二分，O(log m)。
 * schedUnits：该员工有排班日的单元索引升序数组（日粒度 → 每天贡献 AM/PM 两个单元）。
 */
export function countedDaysByRange(schedUnits, cal, form) {
  const r = unitRange(cal, form)
  if (!r.valid) return 0
  const lo = lowerBound(schedUnits, r.startUnit)
  const hi = upperBound(schedUnits, r.endUnit)
  return (hi - lo) / 2
}

/**
 * 算法化重叠：在「按 startUnit 升序的占用区间数组」上二分，O(log k)。
 * occ = { starts: number[]（升序）, list: 区间数组 }，starts 预排序一次，避免每次调用重算 O(k)。
 */
export function findOverlapByRange(occ, cal, form) {
  const r = unitRange(cal, form)
  if (!r.valid) return null
  const starts = occ.starts
  const i = lowerBound(starts, r.startUnit)
  // 候选：i（首个 start >= 新起点，可能已越过）与 i-1（最后一个 start < 新起点，最可能相交）
  for (const idx of [i - 1, i]) {
    if (idx < 0 || idx >= occ.list.length) continue
    const o = occ.list[idx]
    if (o.startUnit <= r.endUnit && r.startUnit <= o.endUnit) return o
  }
  // 兜底：向前回扫处理「长区间包住新区间」的情形（长单少见，最多回扫 2 条）
  for (let k = i - 2; k >= 0 && k >= i - 3; k -= 1) {
    const o = occ.list[k]
    if (o.startUnit <= r.endUnit && r.startUnit <= o.endUnit) return o
  }
  return null
}

/** 算法化账期跨度：单元区间连续 → 月份必连续，O(1) */
export function monthSpansByRange(cal, form) {
  const r = unitRange(cal, form)
  if (!r.valid) return []
  const from = cal.monthIndexOf(Math.floor(r.startUnit / 2))
  const to = cal.monthIndexOf(Math.floor(r.endUnit / 2))
  const out = []
  for (let m = from; m <= to; m += 1) out.push(cal.monthLabel(m))
  return out
}

/** 账期锁判定：请假覆盖月份与「已出账月份集合」求交，O(月数)（≤2） */
export function findLockedMonth(cal, form, lockedMonths) {
  for (const m of monthSpansByRange(cal, form)) if (lockedMonths.has(m)) return m
  return null
}

/* ============ 演练 ============ */

function randomForm(rnd, cal, cfg) {
  const startOrd = randomInt(rnd, 0, cfg.yearDays - 1)
  const len = randomInt(rnd, 1, 8)
  const endOrd = Math.min(cfg.yearDays - 1, startOrd + len - 1)
  const startPeriod = rnd() < 0.5 ? 'AM' : 'PM'
  const endPeriod = rnd() < 0.5 ? 'AM' : 'PM'
  return { startDate: cal.dateOf(startOrd), startPeriod, endDate: cal.dateOf(endOrd), endPeriod }
}

export function run() {
  const cfg = LEAVE_DEFAULT_CONFIG
  const cal = makeCalendar(cfg)
  const rnd = createRandom(0x4d3c2b1a)
  const out = {}

  // 员工排班：每人 365 天里按 5/6 出勤；预生成「日集合」与「单元数组」
  const daySets = []
  const unitArrs = []
  for (let e = 0; e < cfg.employees; e += 1) {
    const days = []
    const units = []
    for (let d = 0; d < cfg.yearDays; d += 1) {
      if ((d + e) % 6 === 5) continue
      days.push(cal.dateOf(d))
      units.push(d * 2, d * 2 + 1)
    }
    daySets.push(days)
    unitArrs.push(units) // 天然升序
  }
  out.数据集概况 = {
    员工数: cfg.employees,
    年天数: cfg.yearDays,
    '人均排班天数': round(daySets[0].length, 1),
    '人均排班单元数': unitArrs[0].length,
    占用请假单数: cfg.occupiedLeaves,
    候选请假单数: cfg.candidateLeaves
  }

  // 1) 计薪天数：等价性 + 耗时
  const queries = Array.from({ length: cfg.countedQueries }, () => randomForm(rnd, cal, cfg))
  const t0 = nowMs()
  const baseCounted = queries.map((q) => countedDaysBaseline(daySets[0], q))
  const t1 = nowMs()
  const fastCounted = queries.map((q) => countedDaysByRange(unitArrs[0], cal, q))
  const t2 = nowMs()
  let mismatch = 0
  for (let i = 0; i < queries.length; i += 1) if (baseCounted[i] !== fastCounted[i]) mismatch += 1
  out.计薪天数 = {
    查询次数: cfg.countedQueries,
    '逐日法_总ms': round(t1 - t0, 3),
    '区间法_总ms': round(t2 - t1, 3),
    加速比: round((t1 - t0) / Math.max(0.001, t2 - t1), 2),
    结果不一致数: mismatch,
    说明: '区间法 2 次二分 O(log m)；逐日法每次重建整张排班的 Set O(S_e)'
  }

  // 2) 重叠判定：k 规模敏感性（k=已有单数）—— 等价性 + 耗时
  const ovProbe = queries.slice(0, 5000)
  const buildOcc = (k) => {
    const list = []
    for (let i = 0; i < k; i += 1) {
      const st = randomInt(rnd, 0, cfg.yearDays - 1)
      const r = unitRange(cal, { startDate: cal.dateOf(st), startPeriod: 'AM', endDate: cal.dateOf(Math.min(cfg.yearDays - 1, st + 2)), endPeriod: 'PM' })
      list.push({ startUnit: r.startUnit, endUnit: r.endUnit })
    }
    list.sort((a, b) => a.startUnit - b.startUnit)
    return { starts: list.map((o) => o.startUnit), list }
  }
  out.重叠判定规模曲线 = [10, 100, 500, 2000].map((k) => {
    const occ = buildOcc(k)
    // 基线：对每条已有单重算 token 求交，O(k·L)
    let baseCount = 0
    const baseForms = occ.list.map((o) => ({
      startUnit: o.startUnit,
      endUnit: o.endUnit,
      form: { startDate: cal.dateOf(Math.floor(o.startUnit / 2)), startPeriod: o.startUnit % 2 ? 'PM' : 'AM', endDate: cal.dateOf(Math.floor(o.endUnit / 2)), endPeriod: o.endUnit % 2 ? 'PM' : 'AM' }
    }))
    const t3 = nowMs()
    ovProbe.forEach((q) => {
      if (findOverlapBaseline(baseForms, q)) baseCount += 1
    })
    const t4 = nowMs()
    let fastCount = 0
    ovProbe.forEach((q) => {
      if (findOverlapByRange(occ, cal, q)) fastCount += 1
    })
    const t5 = nowMs()
    return {
      已有单k: k,
      '逐日法_5000次_ms': round(t4 - t3, 3),
      '区间法_5000次_ms': round(t5 - t4, 3),
      加速比: round((t4 - t3) / Math.max(0.001, t5 - t4), 2),
      命中数一致: baseCount === fastCount,
      命中数: fastCount
    }
  })

  // 3) 账期跨度与账期锁
  const crossMonth = { startDate: '2026-01-28', startPeriod: 'PM', endDate: '2026-02-03', endPeriod: 'AM' }
  const sameDay = { startDate: '2026-01-05', startPeriod: 'AM', endDate: '2026-01-05', endPeriod: 'PM' }
  const illegal = { startDate: '2026-01-05', startPeriod: 'PM', endDate: '2026-01-05', endPeriod: 'AM' }
  const locked = new Set(['2026-02'])
  out.账期 = {
    '跨月单_基线': monthSpansBaseline(cal, crossMonth),
    '跨月单_区间': monthSpansByRange(cal, crossMonth),
    '同日单_自然天数': naturalDaysByRange(cal, sameDay),
    '同日PM→AM_非法': { valid: unitRange(cal, illegal).valid, 自然天数: naturalDaysByRange(cal, illegal) },
    '账期锁_命中2026-02': findLockedMonth(cal, crossMonth, locked),
    '账期锁_未命中': findLockedMonth(cal, crossMonth, new Set(['2026-05']))
  }

  // 4) 极端规模：30 天上限单
  const longForm = { startDate: '2026-03-01', startPeriod: 'AM', endDate: '2026-03-30', endPeriod: 'PM' }
  out.极端规模 = {
    '30天单_自然天数': naturalDaysByRange(cal, longForm),
    '30天单_计薪天数': countedDaysByRange(unitArrs[3], cal, longForm),
    '逐日法token数': halfUnitsByDay(longForm).length
  }

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
