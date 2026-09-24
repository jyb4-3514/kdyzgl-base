// S2 · 工资试算口径引擎：把 4 类 source 解析器抽象为可扩展注册表，验证「新增计薪项不改核心代码」与「同月重复生成幂等」。
// 运行：node s2-payroll.mjs
//
// 基线来源：hrm-demo/src/shared/mock/financeStore.js（resolveItem 四分支 + kpiAmount/attendanceAmount 公式）。
// 目标：① 规则项（key/name/type/source/params/enabled/sortOrder）全配置驱动；
//       ② source 解析器做成注册表（新增来源只注册一个纯函数）；③ 同月重复生成幂等；
//       ④ cap 封顶 / capRatio / 缺档 三类边界可测。

import { createRandom, fnv1a } from './lib/rng.mjs'
import { mean, nowMs, round, std } from './lib/stats.mjs'

/* ============ 规则种子（与 financeStore.RULE_ITEM_SEED 同口径） ============ */

export const RULE_ITEM_SEED = [
  { key: 'BASIC', name: '基本工资', type: 'ADDITION', source: 'FIXED', params: { field: 'basicSalary' } },
  { key: 'POST', name: '岗位工资', type: 'ADDITION', source: 'FIXED', params: { field: 'postSalary' } },
  { key: 'ALLOWANCE', name: '津贴补助', type: 'ADDITION', source: 'FIXED', params: { field: 'allowances' } },
  {
    key: 'KPI_PERF',
    name: '绩效工资',
    type: 'ADDITION',
    source: 'KPI',
    params: { baseField: 'performanceBase', capRatio: 1.2 }
  },
  {
    key: 'FULL_ATTEND',
    name: '全勤奖',
    type: 'ADDITION',
    source: 'ATTENDANCE',
    params: { metric: 'ABSENT', mode: 'BONUS_IF_ZERO', amount: 200 }
  },
  {
    key: 'LATE_FINE',
    name: '迟到扣款',
    type: 'DEDUCTION',
    source: 'ATTENDANCE',
    params: { metric: 'LATE', mode: 'PER_COUNT', amount: 20, cap: 300 }
  },
  {
    key: 'ABSENT_FINE',
    name: '缺勤扣款',
    type: 'DEDUCTION',
    source: 'ATTENDANCE',
    params: { metric: 'ABSENT', mode: 'PER_COUNT', amount: 150, cap: 0 }
  },
  { key: 'OTHER', name: '其他调整', type: 'ADDITION', source: 'MANUAL', params: { defaultValue: 0 } }
]

/* ============ 取数字段映射（外置，替换 metric 键只改配置） ============ */

export const PAYROLL_DEFAULT_CONFIG = {
  attendanceFieldMap: { LATE: 'lateCount', EARLY_LEAVE: 'earlyLeaveCount', ABSENT: 'absentCount', ABNORMAL: 'abnormalCount', LEAVE: 'leaveCount' },
  defaultCapRatio: 1.0,
  unknownSourcePolicy: 'ZERO' // 未知来源按 0 计，不抛错（避免一条脏配置打挂整批算薪）
}

/* ============ 解析器注册表（可扩展核心） ============ */

/**
 * 四类来源解析器签名：(params, ctx, cfg) => { amount, detail }
 * 复杂度均为 O(1)（津贴列表查找为 O(A)，A=定薪项数量）。
 */
export const SOURCE_RESOLVERS = new Map()

SOURCE_RESOLVERS.set('FIXED', (params, ctx) => {
  if (!ctx.salary) return { amount: 0, detail: '未设置定薪档案，按 0 计' }
  if (params.field === 'allowances') {
    const list = ctx.salary.allowances || []
    if (!params.allowanceKey) return { amount: ctx.salary.allowancesTotal, detail: `津贴合计（${list.length} 项）` }
    const hit = list.find((a) => a.key === params.allowanceKey)
    return { amount: hit ? Number(hit.amount) : 0, detail: `指定津贴项 ${params.allowanceKey}` }
  }
  return { amount: Number(ctx.salary[params.field]) || 0, detail: `取定薪项 ${params.field}` }
})

SOURCE_RESOLVERS.set('ATTENDANCE', (params, ctx, cfg) => {
  const count = ctx.attendance ? Number(ctx.attendance[cfg.attendanceFieldMap[params.metric]]) || 0 : 0
  if (params.mode === 'BONUS_IF_ZERO') return { amount: count === 0 ? Number(params.amount) || 0 : 0, detail: `${params.metric}=${count}` }
  const perUnit = Number(params.amount) || 0
  let amount = count * perUnit
  const cap = Number(params.cap)
  if (Number.isFinite(cap) && cap > 0 && amount > cap) amount = cap
  return { amount, detail: `${params.metric}=${count} × ${perUnit}` }
})

SOURCE_RESOLVERS.set('KPI', (params, ctx, cfg) => {
  const base = ctx.salary ? Number(ctx.salary[params.baseField || 'performanceBase']) || 0 : 0
  if (ctx.kpiScore == null) return { amount: 0, detail: '该月无 KPI 评分记录' }
  if (!base) return { amount: 0, detail: '定薪档案缺少绩效基数' }
  const capRatio = params.capRatio === undefined ? cfg.defaultCapRatio : Number(params.capRatio)
  const upper = Number.isFinite(capRatio) && capRatio > 0 ? capRatio : 1
  const rate = Math.max(0, Math.min(upper, ctx.kpiScore / 100))
  return { amount: Math.round(base * rate), detail: `绩效基数 ${base} × KPI ${ctx.kpiScore}%` }
})

SOURCE_RESOLVERS.set('MANUAL', (params) => ({ amount: Number(params.defaultValue) || 0, detail: '人工填写项' }))

/** 扩展演示：注册「工龄补贴」来源，核心算薪代码零改动即生效 */
export function registerTenureSource() {
  SOURCE_RESOLVERS.set('TENURE', (params, ctx) => {
    const years = Number(ctx.salary && ctx.salary.tenureYears) || 0
    const perYear = Number(params.perYear) || 0
    const cap = Number(params.cap) || 0
    let amount = years * perYear
    if (cap > 0 && amount > cap) amount = cap
    return { amount, detail: `工龄 ${years} 年 × ${perYear}` }
  })
}

export function resolveItem(item, ctx, cfg) {
  const resolver = SOURCE_RESOLVERS.get(item.source)
  if (!resolver) return { amount: 0, detail: `未知来源 ${item.source}，按 0 计` }
  return resolver(item.params || {}, ctx, cfg)
}

/* ============ 工资单构建 ============ */

/** O(I log I)（排序 I 项）+ O(I) 解析；I=启用规则项数 */
export function buildPayroll(employee, month, rule, ctx, cfg) {
  const items = rule.items
    .filter((it) => it.enabled === 1)
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key))
    .map((it) => {
      const r = resolveItem(it, ctx, cfg)
      return { key: it.key, name: it.name, type: it.type, source: it.source, amount: r.amount, detail: r.detail }
    })
  const additionTotal = items.filter((i) => i.type === 'ADDITION').reduce((s, i) => s + i.amount, 0)
  const deductionTotal = items.filter((i) => i.type === 'DEDUCTION').reduce((s, i) => s + i.amount, 0)
  return { employeeId: employee.id, month, items, grossAmount: additionTotal, netAmount: additionTotal - deductionTotal }
}

/* ============ 数据集 ============ */

const EMP_COUNT = 500
const MONTH = '2026-09'

function buildEmployees() {
  const rnd = createRandom(0x2f4b8d16)
  const list = []
  for (let i = 1; i <= EMP_COUNT; i += 1) {
    list.push({ id: i, stationId: (i % 7) + 1, role: rnd() < 0.85 ? 'STAFF' : 'STATION_ADMIN' })
  }
  return list
}

/** 定薪档案：3% 的员工故意缺档（测「未设置定薪档案」分支） */
function salaryOf(employee) {
  const rnd = createRandom(fnv1a(`salary#${employee.id}`))
  if (rnd() < 0.03) return null
  const basic = 3000 + Math.floor(rnd() * 3000)
  const post = 500 + Math.floor(rnd() * 1500)
  const allowanceList = [
    { key: 'MEAL', name: '餐补', amount: 300 },
    { key: 'TRANSPORT', name: '交通', amount: 200 }
  ]
  return {
    basicSalary: basic,
    postSalary: post,
    performanceBase: 1000 + Math.floor(rnd() * 1000),
    tenureYears: Math.floor(rnd() * 8),
    allowances: allowanceList,
    allowancesTotal: allowanceList.reduce((s, a) => s + a.amount, 0)
  }
}

/** 考勤统计：迟到 0-3 次、缺勤 0-2 次（覆盖 BONUS_IF_ZERO 与 PER_COUNT 两条分支） */
function attendanceOf(employee) {
  const rnd = createRandom(fnv1a(`att#${employee.id}`))
  return { lateCount: Math.floor(rnd() * 4), absentCount: Math.floor(rnd() * 3), earlyLeaveCount: Math.floor(rnd() * 3) }
}

/** KPI 总分：8% 的员工当月无评分（测「无 KPI 记录」分支） */
function kpiOf(employee) {
  const rnd = createRandom(fnv1a(`kpi#${employee.id}`))
  if (rnd() < 0.08) return null
  return Number((55 + rnd() * 45).toFixed(1))
}

const contextOf = (e) => ({ salary: salaryOf(e), attendance: attendanceOf(e), kpiScore: kpiOf(e) })

/* ============ 演练 ============ */

export function run() {
  const employees = buildEmployees()
  const rule = { id: 1, ruleName: '标准计薪规则', items: RULE_ITEM_SEED.map((it, i) => ({ ...it, enabled: 1, sortOrder: i + 1 })) }
  const cfg = PAYROLL_DEFAULT_CONFIG
  const out = {}

  // 1) 全量生成 ×2，验证幂等
  const t0 = nowMs()
  const first = employees.map((e) => buildPayroll(e, MONTH, rule, contextOf(e), cfg))
  const t1 = nowMs()
  const second = employees.map((e) => buildPayroll(e, MONTH, rule, contextOf(e), cfg))
  const t2 = nowMs()
  const diff = first.filter((p, i) => p.netAmount !== second[i].netAmount).length

  const nets = first.map((p) => p.netAmount)
  out.幂等性 = { 员工数: EMP_COUNT, 两次生成净额不一致数: diff, 说明: '同输入同输出，重复生成不产生漂移' }
  out.金额分布 = {
    净额均值: round(mean(nets), 2),
    净额标准差: round(std(nets), 2),
    净额最小: Math.min(...nets),
    净额最大: Math.max(...nets)
  }
  out.性能 = {
    '首次生成_500人_ms': round(t1 - t0, 3),
    '第二次生成_500人_ms': round(t2 - t1, 3),
    '单人_ms': round((t1 - t0) / EMP_COUNT, 4)
  }

  // 2) 边界用例
  const base = { salary: { basicSalary: 5000, postSalary: 1000, performanceBase: 2000, allowances: [], allowancesTotal: 0 }, attendance: { lateCount: 0, absentCount: 0 }, kpiScore: 100 }
  const pick = (key, p) => resolveItem(rule.items.find((it) => it.key === key), p, cfg)
  out.边界用例 = {
    'KPI_capRatio1.2_满分': pick('KPI_PERF', { ...base, kpiScore: 100 }),
    'KPI_capRatio1.2_得分130超上限': pick('KPI_PERF', { ...base, kpiScore: 130 }),
    'KPI_无评分记录': pick('KPI_PERF', { ...base, kpiScore: null }),
    'KPI_缺绩效基数': pick('KPI_PERF', { ...base, salary: { ...base.salary, performanceBase: 0 } }),
    '全勤奖_缺勤0次': pick('FULL_ATTEND', { ...base, attendance: { absentCount: 0 } }),
    '全勤奖_缺勤1次': pick('FULL_ATTEND', { ...base, attendance: { absentCount: 1 } }),
    '迟到扣款_封顶300': pick('LATE_FINE', { ...base, attendance: { lateCount: 20 } }),
    '缺勤扣款_无封顶(cap=0)': pick('ABSENT_FINE', { ...base, attendance: { absentCount: 10 } }),
    '未知来源_按0计': resolveItem({ source: 'GHOST', params: {} }, base, cfg)
  }

  // 3) 可扩展性：注册新来源后不改核心即生效
  const beforeCount = SOURCE_RESOLVERS.size
  registerTenureSource()
  const rule2 = {
    ...rule,
    items: [...rule.items, { key: 'TENURE_PAY', name: '工龄补贴', type: 'ADDITION', source: 'TENURE', params: { perYear: 100, cap: 500 }, enabled: 1, sortOrder: 99 }]
  }
  const withTenure = buildPayroll(employees[0], MONTH, rule2, contextOf(employees[0]), cfg)
  out.可扩展性 = {
    注册前来源数: beforeCount,
    注册后来源数: SOURCE_RESOLVERS.size,
    '新来源项': withTenure.items.find((i) => i.key === 'TENURE_PAY'),
    说明: '核心 buildPayroll 与 resolveItem 未改动，仅注册解析器即生效'
  }

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
