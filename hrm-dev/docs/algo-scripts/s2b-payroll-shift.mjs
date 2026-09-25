// S2b · 班次制算薪（缺勤/请假粒度 天 → 班次）：基本工资按出勤班次折算 + 旷工按班次罚款。
// 运行：node s2b-payroll-shift.mjs
//
// 口径来源（用户 2026-09-25 裁定，不得更改）：
//   1) 每天 2 班次（早/晚），1 班次 = 半天，2 班次 = 1 天；
//   2) 折算后基本工资 = basicSalary × (实际出勤班次 ÷ 应出勤班次)；
//   3) 已批请假只参与折算、不罚款；
//   4) 旷工（应到未到且无已批请假覆盖）罚款 100 元/班次；
//   5) 早班 08:00–16:00、晚班 16:00–24:00，删除中班；
//   6) 验收算例：basic=1500、应出 60 班次 → 全勤 1500 / 请半天 1475 / 旷半天 1375 / 请全天 1450 / 旷全天 1250；
//   7) T1 请假算缺勤 → 请假者不发全勤奖；T2 只折算基本工资；T3 实发不低于 0；T5 迟到保持「按次」。
//
// 现状基线（before）来源：hrm-server PayrollContextProvider.java（按天 Set<LocalDate> 去重、BASIC=FIXED 不折算、
//   ABSENT_FINE 按天 150、迟到按有效 ON 卡计数 L94-98）、AttendanceStat、ApprovedLeaveDaysPortImpl（半天单元区间）；
//   演示端同口径：hrm-demo/src/shared/mock/attendanceStore.js:893。
// 原型中的常量即 hrm.algo.payroll.* 默认值；实现侧必须逐键外置，禁止内联（规则 §11.4 / 反模式 A03）。

import { createRandom, fnv1a } from './lib/rng.mjs'
import { mean, nowMs, round, std } from './lib/stats.mjs'

/* ================= 参数外置（同名常量 = hrm.algo.payroll.* 默认值） ================= */

export const SHIFT_CONFIG = {
  // 账期开关：month < 该值 → 走「按天」旧路径（保历史月不突变）；空串 = 全部启用班次制
  shiftModelFromMonth: '2026-10',
  // 班次序号判定：attendance_shift.start_time 的分钟数 < 该界值 → 早班(0)，否则晚班(1)
  // 注：恰为 12:00 起始的班次会被判为晚班（与晚班键冲突）；删中班后须校验无此类数据（建议项 S6）
  middayBoundaryMinute: 12 * 60,
  // 单班制历史记录哨兵：period_name = 该值时按「覆盖当日全部排班班次」折算（兼容升级前数据）
  legacyPeriodSentinel: '全天班',
  // 折算项：仅对列出的定薪字段按出勤班次折算（T2 已裁定仅 basicSalary；扩充须再裁定）
  proratedFields: ['basicSalary'],
  // 旷工罚款单价（元/班次）——规则项 params.amount 承载，此处为其默认
  absentFinePerShift: 100,
  // 规则项级封顶（元，绝对额）——对应 ABSENT_FINE.params.cap；<=0 按 itemCapSemantics 视为不封顶
  absentFineCap: 0,
  // 配置级比例封顶——罚款上限 = 折算后基本工资 × 该系数；<=0 = 不封顶
  absentFineCapRatio: 0,
  // T3 裁定：实发不低于 0（复用既有 allowNegativeNet；false = 净额下限置 0）
  allowNegativeNet: false,
  // 应出班次为 0 时兜底：FULL_BASIC=按全额基本工资（不因数据缺失克扣）/ ZERO
  zeroSchedulePolicy: 'FULL_BASIC',
  // T1 裁定：全勤奖指标（请假算缺勤）——ABSENT_OR_LEAVE=(旷工+请假班次)须为 0；ABSENT=仅看旷工
  fullAttendMetric: 'ABSENT_OR_LEAVE',
  // T5 裁定：迟到保持「按次」——PER_CARD=每张有效 ON 迟到卡计 1 次（= 现状源码口径）；
  //   PER_DAY=按日去重（口径变更，本轮未采纳，须用户再裁定方可启用）
  lateGranularity: 'PER_CARD'
}

/* ================= 单元编码 / 纯工具（与 Java 可 1:1 对齐） ================= */

const DAY_MS = 86400000

/** 'YYYY-MM-DD' → epochDay（与 Java LocalDate.toEpochDay() 同值；UTC 构造避免本地时区偏移） */
export function epochDay(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return Math.floor(Date.UTC(y, m - 1, d) / DAY_MS)
}

/** 半天单元：AM = day×2，PM = day×2+1（与 LeaveIntervalPolicy.unitOf 同构，1 单元 = 1 班次） */
export function unitOf(iso, period) {
  return epochDay(iso) * 2 + (period === 'AM' ? 0 : 1)
}

/** 单元 → 日期（'YYYY-MM-DD'） */
export function dateOfUnit(unit) {
  const dt = new Date(Math.floor(unit / 2) * DAY_MS)
  const p = (n) => String(n).padStart(2, '0')
  return `${dt.getUTCFullYear()}-${p(dt.getUTCMonth() + 1)}-${p(dt.getUTCDate())}`
}

/** 班次键：workDate + '#' + 序号（早0/晚1） */
export const shiftKey = (iso, ordinal) => `${iso}#${ordinal}`

/** 班次序号：由排班 shift.startTime 判定（早班 08:00 → 0，晚班 16:00 → 1） */
export function shiftOrdinalByStartTime(startTime, cfg = SHIFT_CONFIG) {
  const [h, m] = String(startTime).split(':').map(Number)
  return h * 60 + m < cfg.middayBoundaryMinute ? 0 : 1
}

/** 有效卡判定：复用 AttendanceConstants.isValidCard（ABNORMAL 不计入有效出勤） */
export const isValidCard = (status) => status !== 'ABNORMAL'

/** 记录侧 period_name 是否为空（null / undefined / 空白串） */
const isBlankPeriodName = (name) => name === null || name === undefined || String(name).trim() === ''

/* ================= 核心：集合运算（纯函数） ================= */

/**
 * 应出勤班次集合：attendance_schedule 行按 (workDate, shiftId→序号) 去重。
 * 复杂度 O(S)，S = 排班行数（满月 30 天双班 = 60）。
 * schedules 行结构 { workDate, startTime }
 */
export function requiredShiftSet(schedules, cfg = SHIFT_CONFIG) {
  const set = new Set()
  for (const s of schedules) set.add(shiftKey(s.workDate, shiftOrdinalByStartTime(s.startTime, cfg)))
  return set
}

/**
 * 实际出勤班次集合（schedule-driven）：逐个有效上班卡映射到班次键。
 * 有效卡 = check_type=ON 且 status ≠ ABNORMAL（与现状「有 ON 且状态有效即出勤」一致）。
 *
 * 记录 → 班次的**唯一判定优先级**（三态，B4 定死；文档 §2.1 与该实现逐字对应）：
 *   ① period_name 命中哨兵（全天班）→ 覆盖**当日全部排班班次**（历史单班制兼容）；
 *   ② period_name 为空/NULL：
 *        - 当日排班班次 > 1 → **不得判为亏勤**：按当日**全部**排班班次计入出勤，并推入告警（交人工复核）；
 *        - 当日排班班次 = 1 → 归属该唯一班次；
 *        - 当日无排班 → 不产生班次（后续与 R 取交自然丢弃）；
 *   ③ 其余（period_name 非空且非哨兵）→ 按 (workDate, period_index) 精确定位班次。
 * 复杂度 O(R + S)，R = 打卡条数。
 * records 行结构 { workDate, periodIndex, periodName, checkType, status }
 *
 * @param warn 可选：告警收集数组，空 period_name 且当日多班次时推入 {workDate, reason, shifts}
 */
export function attendedShiftSet(records, schedules, cfg = SHIFT_CONFIG, warn = null) {
  const dayKeys = new Map()
  for (const s of schedules) {
    const k = shiftKey(s.workDate, shiftOrdinalByStartTime(s.startTime, cfg))
    if (!dayKeys.has(s.workDate)) dayKeys.set(s.workDate, [])
    dayKeys.get(s.workDate).push(k)
  }
  const out = new Set()
  for (const r of records) {
    if (r.checkType === 'OFF' || !isValidCard(r.status)) continue
    const keys = dayKeys.get(r.workDate) || []
    // ① 哨兵：覆盖当日全部排班班次
    if (r.periodName === cfg.legacyPeriodSentinel) {
      for (const k of keys) out.add(k)
      continue
    }
    // ② period_name 为空 → 不克扣（多班次时按当日全部班次计出勤并告警）
    if (isBlankPeriodName(r.periodName)) {
      if (keys.length > 1) {
        for (const k of keys) out.add(k)
        if (warn) warn.push({ workDate: r.workDate, reason: 'PERIOD_NAME_MISSING', shifts: keys.length })
      } else if (keys.length === 1) {
        out.add(keys[0])
      }
      continue
    }
    // ③ 其余按 period_index 精确定位
    out.add(shiftKey(r.workDate, r.periodIndex))
  }
  return out
}

/**
 * 已批请假班次集合（落到具体班次）：日期+上下午区间 → 班次单元区间 → 与应出集合取交。
 * 边界：非法区间（同日 PM→AM，endUnit<startUnit）→ 0 班次（对齐 S5 降级 DATE_INVALID）。
 * 说明：NATURAL/SCHEDULED 的差异在本口径下不影响结果——只有与应出班次相交的请假才可能抵扣缺勤，
 *       取交后二者等价；若未来带薪假需按计薪天数计发，才需在请假域保留该差异。
 * 复杂度 O(L·U)，L = 请假单数，U = 单月单元数（≤62）。
 * leaveRows 行结构 { startDate, startPeriod, endDate, endPeriod }
 */
export function leaveShiftSet(leaveRows, requiredSet) {
  const out = new Set()
  for (const row of leaveRows) {
    const s = unitOf(row.startDate, row.startPeriod)
    const e = unitOf(row.endDate, row.endPeriod)
    if (e < s) continue
    for (let u = s; u <= e; u += 1) {
      const k = shiftKey(dateOfUnit(u), u % 2 === 0 ? 0 : 1)
      if (requiredSet.has(k)) out.add(k)
    }
  }
  return out
}

/**
 * 迟到计数（B1/T5）：判定粒度由 lateGranularity 决定。
 *   PER_CARD（默认，= 现状源码口径 PayrollContextProvider.java:94-98）：
 *     每张「有效上班卡且 status=LATE」计 1 次 → 双班制下同日两班迟到计 2 次；
 *   PER_DAY（口径变更，本轮未采纳）：按 work_date 去重计 1 次。
 * 复杂度 O(R)。
 */
export function lateCountOf(records, cfg = SHIFT_CONFIG) {
  const lateOn = records.filter(
    (r) => r.checkType === 'ON' && isValidCard(r.status) && r.status === 'LATE'
  )
  if (cfg.lateGranularity === 'PER_DAY') return new Set(lateOn.map((r) => r.workDate)).size
  return lateOn.length
}

/**
 * 旷工罚款封顶（B6/T3）：逐级取更严（min），每级「<=0 = 本级不封顶」。
 *   级 1（规则项）：params.cap（绝对额）
 *   级 2（配置级）：absentFineCapRatio × 折算后基本工资（比例）
 *   级 3（合计层）：allowNegativeNet=false → 净额下限置 0（不改变罚款额，只钳制实发）
 * 返回 { fine, cappedBy }；cappedBy 记录实际生效的封顶级（便于明细与排障）。
 * 复杂度 O(1)。
 */
export function capAbsentFine(fineGross, basicProrated, cfg = SHIFT_CONFIG) {
  const itemCap = Number(cfg.absentFineCap) > 0 ? Number(cfg.absentFineCap) : Infinity
  const ratioCap = Number(cfg.absentFineCapRatio) > 0
    ? round(basicProrated * Number(cfg.absentFineCapRatio), 2)
    : Infinity
  const fine = round(Math.min(fineGross, itemCap, ratioCap), 2)
  let cappedBy = 'NONE'
  if (fine < fineGross) cappedBy = itemCap <= ratioCap ? 'ITEM_CAP' : 'CONFIG_CAP_RATIO'
  return { fine, cappedBy }
}

/**
 * 班次制算薪核心：
 *   required=|R|，attended=|A∩R|，leaveShifts=|L∩R|，absent=|R \ (A ∪ L)|
 *   折算后基本工资 = basic × |A∩R| / |R|（|R|=0 时按 zeroSchedulePolicy 兜底）
 *   旷工罚款 = 逐级封顶(absent × 单价)（见 capAbsentFine）
 *   基本工资部分实得 = 折算后基本工资 − 旷工罚款；allowNegativeNet=false → 下限置 0（T3）
 * 旷工采用集合差集 = R \ (A ∪ L)：先扣出勤、再扣请假天然去重，故与扣减顺序无关（A∩L>0 时也正确）。
 * 复杂度 O(S + R + U)，空间 O(S + R)。
 */
export function calcShiftPayroll(input, cfg = SHIFT_CONFIG) {
  const { basicSalary, schedules, records, leaveRows } = input
  const warnings = []
  const required = requiredShiftSet(schedules, cfg)
  const attendedRaw = attendedShiftSet(records, schedules, cfg, warnings)
  const attended = new Set([...attendedRaw].filter((k) => required.has(k)))
  const leave = leaveShiftSet(leaveRows, required)

  let absent = 0
  for (const k of required) if (!attended.has(k) && !leave.has(k)) absent += 1

  const requiredCount = required.size
  const attendedCount = attended.size
  const leaveCount = leave.size
  const basicProrated = requiredCount > 0
    ? round((basicSalary * attendedCount) / requiredCount, 2)
    : (cfg.zeroSchedulePolicy === 'FULL_BASIC' ? basicSalary : 0)

  const fineGross = round(absent * cfg.absentFinePerShift, 2)
  const { fine: absentFine, cappedBy } = capAbsentFine(fineGross, basicProrated, cfg)
  const netRaw = round(basicProrated - absentFine, 2)
  const netFloored = !cfg.allowNegativeNet && netRaw < 0
  const basicPartNet = netFloored ? 0 : netRaw

  return {
    requiredShifts: requiredCount,
    attendedShifts: attendedCount,
    leaveShifts: leaveCount,
    absentShifts: absent,
    basicProrated,
    absentFine,
    fineGross,
    fineCappedBy: cappedBy,
    basicPartNet,
    netFloored,
    warnings
  }
}

/**
 * 全勤奖判定（T1）：请假算缺勤 → 默认指标 ABSENT_OR_LEAVE（旷工+请假班次）须为 0 才发。
 * ABSENT（仅旷工）为旧口径，可配但本轮未采纳。
 */
export function fullAttendBonus(result, amount, cfg = SHIFT_CONFIG) {
  const metric = cfg.fullAttendMetric === 'ABSENT'
    ? result.absentShifts
    : result.absentShifts + result.leaveShifts
  return metric === 0 ? amount : 0
}

/* ================= 现状（before）：按天口径复刻 ================= */

/** 请假天数（0.5 粒度）——简化版，与 ApprovedLeaveDaysPort 的「自然天」口径对齐（区间长度/2） */
function leaveDaysOf(leaveRows) {
  let units = 0
  for (const row of leaveRows) {
    const s = unitOf(row.startDate, row.startPeriod)
    const e = unitOf(row.endDate, row.endPeriod)
    if (e >= s) units += e - s + 1
  }
  return units / 2
}

/**
 * 现状（按天）算薪复刻：BASIC=FIXED 不折算、ABSENT_FINE 按天 150。
 * absentDays = max(0, 应到天数 − 实到天数 − excludeLeaveDays)，excludeLeaveDays = leaveDeductEnabled ? 0 : leaveDays。
 */
export function calcDayPayroll(input) {
  const { basicSalary, schedules, records, leaveRows, leaveDeductEnabled, absentFinePerDay = 150 } = input
  const scheduledDays = new Set(schedules.map((s) => s.workDate))
  const attendedDays = new Set()
  for (const r of records) {
    if (r.checkType !== 'ON' || !isValidCard(r.status)) continue
    attendedDays.add(r.workDate)
  }
  const leaveDays = leaveDaysOf(leaveRows)
  const exclude = leaveDeductEnabled ? 0 : leaveDays
  const absent = Math.max(0, scheduledDays.size - attendedDays.size - exclude)
  const absentFine = round(absent * absentFinePerDay, 2)
  return {
    requiredDays: scheduledDays.size,
    attendedDays: attendedDays.size,
    leaveDays,
    absentDays: round(absent, 2),
    basicFixed: basicSalary,
    absentFine,
    basicPartNet: round(basicSalary - absentFine, 2),
    lateCount: lateCountOf(records, { lateGranularity: 'PER_CARD' })
  }
}

/* ================= 验收 / 对比 / 基准数据集 ================= */

const BASIC = 1500
const MONTH_DAYS = 30
const MONTH = '2026-10-'
const SHIFT_STARTS = ['08:00', '16:00']

const dayList = Array.from({ length: MONTH_DAYS }, (_, i) => `${MONTH}${String(i + 1).padStart(2, '0')}`)

/** 满月双班排班（30 天 × 2 班次 = 60 行） */
const buildSchedules = (days = dayList) => {
  const rows = []
  for (const d of days) for (const st of SHIFT_STARTS) rows.push({ workDate: d, startTime: st })
  return rows
}

const onRecord = (day, ordinal, status = 'NORMAL') => ({
  workDate: day,
  periodIndex: ordinal,
  periodName: ordinal === 0 ? '早班' : '晚班',
  checkType: 'ON',
  status
})

const allShiftKeys = () => {
  const s = new Set()
  for (const d of dayList) { s.add(`${d}#0`); s.add(`${d}#1`) }
  return s
}

/** 由「出勤班次键集合」生成 ON 打卡记录 */
const recordsFromKeys = (keys) => [...keys].sort().map((k) => {
  const [d, o] = k.split('#')
  return onRecord(d, Number(o))
})

const drop = (keys, dropList) => new Set([...keys].filter((k) => !dropList.includes(k)))

const HALF_DAY_PM = [{ startDate: '2026-10-01', startPeriod: 'PM', endDate: '2026-10-01', endPeriod: 'PM' }]
const FULL_DAY = [{ startDate: '2026-10-01', startPeriod: 'AM', endDate: '2026-10-01', endPeriod: 'PM' }]
const NO_LEAVE = []

function acceptance() {
  const ALL = allShiftKeys()
  const cases = [
    { name: '全勤', attended: ALL, leave: NO_LEAVE, expect: 1500 },
    { name: '请半天假(已批)', attended: drop(ALL, ['2026-10-01#1']), leave: HALF_DAY_PM, expect: 1475 },
    { name: '旷工半天', attended: drop(ALL, ['2026-10-01#1']), leave: NO_LEAVE, expect: 1375 },
    { name: '请全天假(已批)', attended: drop(ALL, ['2026-10-01#0', '2026-10-01#1']), leave: FULL_DAY, expect: 1450 },
    { name: '旷工全天', attended: drop(ALL, ['2026-10-01#0', '2026-10-01#1']), leave: NO_LEAVE, expect: 1250 }
  ]
  const schedules = buildSchedules()
  return cases.map((c) => {
    const records = recordsFromKeys(c.attended)
    const got = calcShiftPayroll({ basicSalary: BASIC, schedules, records, leaveRows: c.leave })
    return {
      情形: c.name,
      应出班次: got.requiredShifts,
      实出班次: got.attendedShifts,
      请假班次: got.leaveShifts,
      旷工班次: got.absentShifts,
      折算后基本工资: got.basicProrated,
      旷工罚款: got.absentFine,
      实得: got.basicPartNet,
      期望: c.expect,
      一致: got.basicPartNet === c.expect
    }
  })
}

/** before/after 差异清单：同一组班次事实，分别按「天」与「班次」计算（现状库 leave_deduct_enabled=1） */
function beforeAfter() {
  const ALL = allShiftKeys()
  const schedules = buildSchedules()
  const cases = [
    { name: '全勤(60 班次有卡)', attended: ALL, leave: NO_LEAVE },
    { name: '请半天假(59 班出勤, 1 班已批假)', attended: drop(ALL, ['2026-10-01#1']), leave: HALF_DAY_PM },
    { name: '旷工半天(59 班出勤, 1 班未到无假)', attended: drop(ALL, ['2026-10-01#1']), leave: NO_LEAVE },
    { name: '请全天假(58 班出勤, 2 班已批假)', attended: drop(ALL, ['2026-10-01#0', '2026-10-01#1']), leave: FULL_DAY },
    { name: '旷工全天(58 班出勤, 1 天两班未到无假)', attended: drop(ALL, ['2026-10-01#0', '2026-10-01#1']), leave: NO_LEAVE }
  ]
  return cases.map((c) => {
    const records = recordsFromKeys(c.attended)
    const nw = calcShiftPayroll({ basicSalary: BASIC, schedules, records, leaveRows: c.leave })
    const od = calcDayPayroll({
      basicSalary: BASIC, schedules, records, leaveRows: c.leave, leaveDeductEnabled: true
    })
    return {
      情形: c.name,
      '现状_天_实得': od.basicPartNet,
      '现状_天_明细': `缺勤 ${od.absentDays} 天 × 150，基本固定 ${od.basicFixed}`,
      '新_班次_实得': nw.basicPartNet,
      '新_班次_明细': `折算 ${nw.basicProrated} − 旷工 ${nw.absentFine}`,
      差_新减现状: round(nw.basicPartNet - od.basicPartNet, 2)
    }
  })
}

/** B1/T5：迟到粒度对比（PER_CARD = 现状源码口径 vs PER_DAY = 按天去重） */
function lateGranularityCase() {
  // 单班制历史：每日 1 张 LATE ON 卡，20 天
  const singleShift = dayList.slice(0, 20).map((d) =>
    ({ ...onRecord(d, 0, 'LATE') }))
  // 双班制：每日两班均迟到，10 天
  const doubleShift = []
  for (const d of dayList.slice(0, 10)) {
    doubleShift.push(onRecord(d, 0, 'LATE'), onRecord(d, 1, 'LATE'))
  }
  const perCard = (recs) => lateCountOf(recs, { lateGranularity: 'PER_CARD' })
  const perDay = (recs) => lateCountOf(recs, { lateGranularity: 'PER_DAY' })
  const RATE = 20
  return [
    {
      场景: '单班制历史(20 天 × 1 张迟到卡)',
      '现状_PER_CARD_次': perCard(singleShift),
      'PER_DAY_次': perDay(singleShift),
      '现状_扣款': perCard(singleShift) * RATE,
      'PER_DAY_扣款': perDay(singleShift) * RATE,
      差_扣款: perDay(singleShift) * RATE - perCard(singleShift) * RATE,
      结论: '两者一致 → 单班制下「按次」与「按天」等价'
    },
    {
      场景: '双班制(10 天 × 2 班均迟到)',
      '现状_PER_CARD_次': perCard(doubleShift),
      'PER_DAY_次': perDay(doubleShift),
      '现状_扣款': perCard(doubleShift) * RATE,
      'PER_DAY_扣款': perDay(doubleShift) * RATE,
      差_扣款: perDay(doubleShift) * RATE - perCard(doubleShift) * RATE,
      结论: '默认 PER_CARD = 现状行为不变；若改 PER_DAY 则少扣 200 元=口径变更'
    }
  ]
}

/** T1：全勤奖（请假算缺勤）新旧指标对比 */
function fullAttendCases() {
  const ALL = allShiftKeys()
  const schedules = buildSchedules()
  const BONUS = 200
  const cases = [
    { name: '全勤', attended: ALL, leave: NO_LEAVE },
    { name: '请半天假(已批)', attended: drop(ALL, ['2026-10-01#1']), leave: HALF_DAY_PM },
    { name: '旷工半天', attended: drop(ALL, ['2026-10-01#1']), leave: NO_LEAVE },
    { name: '整月全假', attended: new Set(), leave: [{ startDate: '2026-10-01', startPeriod: 'AM', endDate: '2026-10-30', endPeriod: 'PM' }] }
  ]
  return cases.map((c) => {
    const r = calcShiftPayroll({ basicSalary: BASIC, schedules, records: recordsFromKeys(c.attended), leaveRows: c.leave })
    const newBonus = fullAttendBonus(r, BONUS, { fullAttendMetric: 'ABSENT_OR_LEAVE' })
    const oldBonus = fullAttendBonus(r, BONUS, { fullAttendMetric: 'ABSENT' })
    return {
      情形: c.name, 旷工班次: r.absentShifts, 请假班次: r.leaveShifts,
      '新_ABSENT_OR_LEAVE_全勤奖': newBonus,
      '旧_ABSENT_全勤奖': oldBonus,
      一致: newBonus === oldBonus
    }
  })
}

/** 批量基准数据集：500 员工 × 满月，固定种子；含轮休/半天迟到/半天请假/半天旷工 */
function benchmark() {
  const N = 500
  const rnd = createRandom(0x5d1f2a7c)
  const employees = []
  for (let id = 1; id <= N; id += 1) {
    const r = createRandom(fnv1a(`shift#${id}`))
    // 排班：每天 2 班，轮休概率 1/6 整天休
    const schedules = []
    for (const d of dayList) {
      if (r() < 1 / 6) continue
      for (const st of SHIFT_STARTS) schedules.push({ workDate: d, startTime: st })
    }
    // 事实：每班次 4% 未到；其中 40% 有已批请假覆盖（半天粒度）
    const records = []
    const leaveRows = []
    for (const s of schedules) {
      const ord = shiftOrdinalByStartTime(s.startTime)
      const roll = r()
      if (roll < 0.04) {
        if (r() < 0.4) {
          leaveRows.push({
            startDate: s.workDate,
            startPeriod: ord === 0 ? 'AM' : 'PM',
            endDate: s.workDate,
            endPeriod: ord === 0 ? 'AM' : 'PM'
          })
        }
        continue
      }
      records.push(onRecord(s.workDate, ord))
    }
    employees.push({ id, schedules, records, leaveRows })
  }

  const t0 = nowMs()
  const results = employees.map((e) => calcShiftPayroll({
    basicSalary: 4000, schedules: e.schedules, records: e.records, leaveRows: e.leaveRows
  }))
  const t1 = nowMs()

  // 恒等式校验：|A ∪ L| + absent = required，且 attended ≤ required、absent ≥ 0
  let bad = 0
  employees.forEach((e, i) => {
    const r = results[i]
    if (r.absentShifts < 0 || r.attendedShifts > r.requiredShifts) bad += 1
    const req = requiredShiftSet(e.schedules)
    const att = new Set([...attendedShiftSet(e.records, e.schedules)].filter((k) => req.has(k)))
    const lv = leaveShiftSet(e.leaveRows, req)
    const union = new Set([...att, ...lv])
    if (union.size + r.absentShifts !== req.size) bad += 1
  })

  const nets = results.map((r) => r.basicPartNet)
  const totals = results.map((r) => r.requiredShifts)
  return {
    员工数: N,
    账期: '2026-10',
    平均应出班次: round(mean(totals), 2),
    实得均值: round(mean(nets), 2),
    实得标准差: round(std(nets), 2),
    实得最小: Math.min(...nets),
    实得最大: Math.max(...nets),
    恒等式违例数: bad,
    负净额被钳制数: results.filter((r) => r.netFloored).length,
    总耗时ms: round(t1 - t0, 3),
    单人耗时ms: round((t1 - t0) / N, 4)
  }
}

/** 边界与降级 */
function edges() {
  const out = {}
  const ALL = allShiftKeys()
  const sched = buildSchedules()

  // ① 空排班 → 不因数据缺失克扣（FULL_BASIC）
  out['空排班_FULL_BASIC'] = calcShiftPayloadSafe({ basicSalary: BASIC, schedules: [], records: [], leaveRows: [] })

  // ② T3 整月无打卡 → 折算 0、罚款 6000，封顶后实发 0（不再出现 −6000）
  out['整月无打卡_T3封顶'] = calcShiftPayloadSafe({ basicSalary: BASIC, schedules: sched, records: [], leaveRows: [] })

  // ③ T3 部分缺勤触发封顶（折算 250、旷工 50 班次 = 罚款 5000 → 实发 0）
  const halfRecords = recordsFromKeys(new Set([...ALL].filter((k) => k.startsWith('2026-10-01') || k.startsWith('2026-10-02') || k.startsWith('2026-10-03') || k.startsWith('2026-10-04') || k.startsWith('2026-10-05'))))
  out['部分缺勤_封顶触发'] = calcShiftPayloadSafe({ basicSalary: BASIC, schedules: sched, records: halfRecords, leaveRows: [] })

  // ④ ABNORMAL 卡不计出勤 → 计旷工
  const recAbnormal = recordsFromKeys(drop(ALL, ['2026-10-01#0']))
  recAbnormal.push({ ...onRecord('2026-10-01', 0), status: 'ABNORMAL' })
  out['ABNORMAL卡不计出勤'] = calcShiftPayloadSafe({ basicSalary: BASIC, schedules: sched, records: recAbnormal, leaveRows: [] })

  // ⑤ 请假超出应到（整月全假）→ 折算 0、旷工 0
  const fullMonthLeave = [{ startDate: '2026-10-01', startPeriod: 'AM', endDate: '2026-10-30', endPeriod: 'PM' }]
  out['整月全假'] = calcShiftPayloadSafe({ basicSalary: BASIC, schedules: sched, records: [], leaveRows: fullMonthLeave })

  // ⑥ 请假与出勤重叠（已批假当天仍打了卡）→ 不双扣（集合去重）
  out['请假与出勤重叠_不双扣'] = calcShiftPayloadSafe({
    basicSalary: BASIC, schedules: sched, records: recordsFromKeys(ALL), leaveRows: HALF_DAY_PM
  })

  // ⑦ 跨日单（startDate PM → endDate AM）→ 连续单元覆盖中间整日
  const crossDay = [{ startDate: '2026-10-01', startPeriod: 'PM', endDate: '2026-10-03', endPeriod: 'AM' }]
  const rec = recordsFromKeys(drop(ALL, ['2026-10-01#1', '2026-10-02#0', '2026-10-02#1', '2026-10-03#0']))
  out['跨日单_PM到AM'] = calcShiftPayloadSafe({ basicSalary: BASIC, schedules: sched, records: rec, leaveRows: crossDay })

  // ⑧ 非法区间（同日 PM→AM）→ 0 班次
  out['非法区间_PM到AM同日'] = calcShiftPayloadSafe({
    basicSalary: BASIC, schedules: sched, records: recordsFromKeys(drop(ALL, ['2026-10-01#1'])),
    leaveRows: [{ startDate: '2026-10-01', startPeriod: 'PM', endDate: '2026-10-01', endPeriod: 'AM' }]
  })

  // ⑨ B4：空 period_name + 双班排班 → 三态优先级② 按当日全部班次计出勤（不克扣），并告警
  const blankNameRecords = dayList.flatMap((d) => [
    { workDate: d, periodIndex: 0, periodName: null, checkType: 'ON', status: 'NORMAL' }
  ])
  out['空period_name_双班排班_B4'] = calcShiftPayloadSafe({
    basicSalary: BASIC, schedules: sched, records: blankNameRecords, leaveRows: []
  })

  // ⑩ B4：空 period_name + 单班排班 → 归属该唯一班次（正常，不告警）
  const singleSched = dayList.map((d) => ({ workDate: d, startTime: '08:00' }))
  out['空period_name_单班排班'] = calcShiftPayloadSafe({
    basicSalary: BASIC, schedules: singleSched,
    records: dayList.map((d) => ({ workDate: d, periodIndex: 0, periodName: '', checkType: 'ON', status: 'NORMAL' })),
    leaveRows: []
  })

  // ⑪ 单班制历史数据（386 条 period_index=0 + period_name='全天班'）→ ratio 恒为 1，不突变
  const legacyDays = dayList.slice(0, 20)
  const legacySched = legacyDays.map((d) => ({ workDate: d, startTime: '08:00' }))
  const legacyRec = legacyDays.map((d) => ({
    workDate: d, periodIndex: 0, periodName: '全天班', checkType: 'ON', status: 'NORMAL'
  }))
  const legacyGap = legacyDays.slice(0, 19).map((d) => ({
    workDate: d, periodIndex: 0, periodName: '全天班', checkType: 'ON', status: 'NORMAL'
  }))
  out['单班制历史_全勤不突变'] = calcShiftPayloadSafe({
    basicSalary: BASIC, schedules: legacySched, records: legacyRec, leaveRows: []
  })
  out['单班制历史_缺一天'] = calcShiftPayloadSafe({
    basicSalary: BASIC, schedules: legacySched, records: legacyGap, leaveRows: []
  })

  return out
}

/** 只输出基本工资部分的裁剪视图（边界用例可读性） */
function calcShiftPayloadSafe(input) {
  const r = calcShiftPayroll(input)
  return {
    应出: r.requiredShifts, 实出: r.attendedShifts, 请假: r.leaveShifts, 旷工: r.absentShifts,
    折算基本: r.basicProrated, 罚款: r.absentFine, 实得: r.basicPartNet,
    负净额已钳制: r.netFloored, 封顶级: r.fineCappedBy, 告警数: r.warnings.length
  }
}

/* ================= 输出 ================= */

export function run() {
  const acc = acceptance()
  const diff = beforeAfter()
  const late = lateGranularityCase()
  const attend = fullAttendCases()
  const bench = benchmark()
  const edge = edges()

  const out = {
    验收算例_逐位: acc,
    验收全部通过: acc.every((c) => c.一致),
    'before_after_差异清单': diff,
    'B1_T5_迟到粒度对比': late,
    'T1_全勤奖_请假算缺勤': attend,
    批量基准: bench,
    边界与降级: edge
  }
  console.log(JSON.stringify(out, null, 2))

  const accLine = acc.map((c) => `${c.情形}=${c.实得}`).join(' / ')
  console.log(`\n[验收] ${accLine} → ${out.验收全部通过 ? '全部逐位一致' : '存在不一致'}`)
  return out
}

run()
