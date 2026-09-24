// S6 · 工单多目标派单：基线（关键词顺序命中 + 默认处理人为空 = 不派单）对照「SLA 紧迫度 × 技能匹配 × 负载均衡」加权排序。
// 运行：node s6-dispatch.mjs
//
// 基线来源：hrm-demo/src/shared/mock/routes/workOrder.js
//   autoDispatch：按 dispatchRules 顺序取「首个启用且关键词命中」决定类型与优先级，兜底 {类型4, 优先级1}；
//   DISPATCH_RULE_SEED 的 default_assignee_id 全为 null → 现状「自动派发只定类型/优先级，不指派处理人」。
//   WORK_ORDER_SLA_HOURS = { 0:48, 1:24, 2:8 }（低/中/高，硬编码）。
// 目标：SLA 阈值外置；派单改多目标加权排序；用离散事件仿真量化「分配准确率 / 平均响应 / SLA 达成 / 负载公平」。

import { createRandom, randomInt } from './lib/rng.mjs'
import { jainIndex, mean, nowMs, round, std } from './lib/stats.mjs'

export const DISPATCH_DEFAULT_CONFIG = {
  employees: 20,
  tickets: 2000, // 10 天内 2000 单 → 人均 ~100 单，制造真实排队（否则「负载」项无区分度）
  horizonHours: 240, // 10 天
  // SLA 阈值（小时，按优先级 0低/1中/2高）—— 替代硬编码 WORK_ORDER_SLA_HOURS
  slaHours: { 0: 48, 1: 24, 2: 8 },
  // 多目标权重（全部可配）
  wUrgency: 4.0,
  wSkill: 2.5,
  wLoad: 1.5,
  wSpeed: 2.0,
  baseServiceMinutes: 40,
  skillPenaltyMinutes: 80,
  // 关键词规则（顺序敏感 → 歧义样本上会误判）
  keywordRules: [
    { keyword: '破损', type: 1, priority: 1 },
    { keyword: '丢失', type: 1, priority: 2 },
    { keyword: '故障', type: 2, priority: 1 },
    { keyword: '投诉', type: 3, priority: 2 }
  ],
  defaultType: 4,
  defaultPriority: 1
}

/* ============ 类型判定：顺序命中 vs 加权打分 ============ */

/** 基线：顺序首个命中（与 Mock 完全一致） */
export function classifyByOrder(content, cfg) {
  const rule = cfg.keywordRules.find((r) => content.includes(r.keyword))
  return rule ? { type: rule.type, priority: rule.priority, keyword: rule.keyword } : { type: cfg.defaultType, priority: cfg.defaultPriority, keyword: null }
}

/**
 * 加权打分：按关键词「长度 × 权重」累加，取最高分（长关键词更具体，天然优先）。
 * 复杂度 O(R × |content|)。权重可配 → hrm.algo.dispatch.keywordWeights。
 */
export function classifyByScore(content, cfg, keywordWeights = {}) {
  let best = null
  for (const r of cfg.keywordRules) {
    if (!content.includes(r.keyword)) continue
    const w = (keywordWeights[r.keyword] || 1) * r.keyword.length
    if (!best || w > best.w) best = { type: r.type, priority: r.priority, keyword: r.keyword, w }
  }
  return best ? { type: best.type, priority: best.priority, keyword: best.keyword } : { type: cfg.defaultType, priority: cfg.defaultPriority, keyword: null }
}

/* ============ 数据集 ============ */

function buildEmployees(cfg) {
  const rnd = createRandom(0x7a1b2c3d)
  const emps = []
  for (let i = 0; i < cfg.employees; i += 1) {
    const skills = [1, 2, 3, 4].map(() => rnd())
    const total = skills.reduce((a, b) => a + b, 0)
    emps.push({
      id: i + 1,
      stationId: 1,
      skillShare: skills.map((v) => v / total), // 各类型处理经验占比（技能画像）
      freeAt: 0, // 空闲时刻（小时）
      handled: 0,
      open: 0
    })
  }
  return emps
}

/** 工单流：类型由「真实意图」决定，内容可能含多个关键词（制造歧义） */
function buildTickets(cfg) {
  const rnd = createRandom(0x3c4d5e6f)
  const templates = {
    1: ['包裹破损待核', '包裹破损且客户投诉', '包裹丢失待查'],
    2: ['扫码枪故障', '货架损坏报修', '门禁故障'],
    3: ['客户投诉取件慢', '客户投诉服务态度', '投诉包裹丢失'],
    4: ['其他异常需人工确认', '现场情况不明']
  }
  const tickets = []
  for (let i = 0; i < cfg.tickets; i += 1) {
    const type = randomInt(rnd, 1, 4)
    const content = templates[type][randomInt(rnd, 0, templates[type].length - 1)]
    const priority = type === 4 ? 1 : type === 3 || content.includes('丢失') ? 2 : rnd() < 0.5 ? 1 : 0
    const created = rnd() * cfg.horizonHours
    tickets.push({ id: i + 1, truthType: type, content, truthPriority: priority, created })
  }
  return tickets.sort((a, b) => a.created - b.created)
}

/* ============ 仿真 ============ */

function serviceMinutes(employee, type, cfg) {
  const skill = employee.skillShare[type - 1]
  return cfg.baseServiceMinutes + (1 - skill) * cfg.skillPenaltyMinutes
}

/**
 * 多目标打分：score(e) = wUrg·紧迫度 + wSkill·技能 + wLoad·负载 + wSpeed·就绪度
 *   紧迫度 = priorityUrgency × 就绪度，高优先级工单优先交给「马上能开工」的人
 *   技能   = 该员工对该类型的经验占比（归一）
 *   负载   = 1 - 已分配量/当前最大分配量（真实参与均衡，不依赖是否拥塞）
 *   就绪度 = 1 - min(1, 预计等待/时间窗)
 * 复杂度 O(E)。无候选时返回 null → 失败降级为「不指派，待人工」。
 */
export function scoreAssign(ticket, t, employees, cfg, maxHandled) {
  const sla = cfg.slaHours[ticket.priority]
  const priorityUrgency = (ticket.priority + 1) / 3 // 0→1/3, 1→2/3, 2→1
  let best = null
  for (const e of employees) {
    const wait = Math.max(0, e.freeAt - t)
    const readiness = 1 - Math.min(1, wait / sla)
    const load = maxHandled > 0 ? 1 - e.handled / maxHandled : 1
    const score =
      cfg.wUrgency * priorityUrgency * readiness +
      cfg.wSkill * e.skillShare[ticket.type - 1] +
      cfg.wLoad * load +
      cfg.wSpeed * readiness
    if (!best || score > best.score) best = { employee: e, score, predictedWait: wait }
  }
  return best
}

/** 仿真：assignMode = 'none'(基线) | 'roundRobin'(经验补丁) | 'score'(多目标) */
export function simulate(tickets, cfg, assignMode) {
  const employees = buildEmployees(cfg)
  let rrCursor = 0
  const rows = []
  let unassigned = 0

  for (const ticket of tickets) {
    // 类型/优先级取「真实意图」，本轮只比较「处理人选择」这一变量
    const tk = { ...ticket, type: ticket.truthType, priority: ticket.truthPriority }
    const t = tk.created
    let chosen = null
    let assignedAt = t
    if (assignMode === 'none') {
      // 现状：不指派 → 人工认领（2~8h 后随机认领）
      const rnd = createRandom(0x1111 ^ tk.id)
      assignedAt = t + 2 + rnd() * 6
      chosen = employees[randomInt(rnd, 0, employees.length - 1)]
      unassigned += 1
    } else if (assignMode === 'roundRobin') {
      chosen = employees[rrCursor % employees.length]
      rrCursor += 1
    } else {
      const maxHandled = Math.max(...employees.map((e) => e.handled), 1)
      const best = scoreAssign(tk, t, employees, cfg, maxHandled)
      chosen = best ? best.employee : null
    }
    if (!chosen) {
      unassigned += 1
      rows.push({ ...tk, wait: null, completion: null, employeeId: null })
      continue
    }
    const start = Math.max(assignedAt, chosen.freeAt)
    const wait = Math.max(0, chosen.freeAt - t)
    const mins = serviceMinutes(chosen, tk.type, cfg)
    const completion = start + mins / 60
    chosen.freeAt = completion
    chosen.handled += 1
    rows.push({ ...tk, wait, completion, employeeId: chosen.id, assignedAt })
  }

  const waits = rows.filter((r) => r.wait != null).map((r) => r.wait)
  const completions = rows.filter((r) => r.completion != null)
  const slaHit = completions.filter((r) => r.completion - r.created <= cfg.slaHours[r.priority]).length
  const eMap = new Map(employees.map((e) => [e.id, e]))
  const skillHit = rows.filter((r) => {
    if (r.employeeId == null) return false
    const e = eMap.get(r.employeeId)
    return e.skillShare[r.type - 1] >= 1 / employees.length // 高于均匀分布即视为命中技能
  }).length
  const loads = employees.map((e) => e.handled)
  return {
    assignMode,
    未派单率: round(unassigned / rows.length, 4),
    技能匹配率: round(skillHit / rows.length, 4),
    平均分配等待_小时: round(mean(waits), 3),
    等待标准差_小时: round(std(waits), 3),
    平均完成时长_小时: round(mean(completions.map((r) => r.completion - r.created)), 3),
    'SLA达成率': round(completions.length ? slaHit / completions.length : 0, 4),
    负载Jain: round(jainIndex(loads), 4),
    处理量标准差: round(std(loads), 3),
    末位处理量: Math.min(...loads),
    首位处理量: Math.max(...loads)
  }
}

/* ============ 演练 ============ */

export function run() {
  const cfg = DISPATCH_DEFAULT_CONFIG
  const out = {}

  // A) 类型判定准确率（歧义样本）
  //    真值口径 = 「配置中最具体的命中关键词」对应的类型（特异度权重 hrm.algo.dispatch.keywordWeights 声明）；
  //    顺序命中忽略特异度 → 在「宽泛词在前、具体词在后」的内容上会判错。
  const keywordWeights = { 破损: 1.0, 丢失: 3.0, 故障: 1.0, 投诉: 2.5 }
  const contents = [
    '包裹破损待核',
    '包裹破损且客户投诉',
    '包裹丢失待查',
    '扫码枪故障',
    '货架损坏报修',
    '客户投诉取件慢',
    '投诉包裹丢失',
    '门禁故障',
    '门禁故障引起客户投诉',
    '包裹丢失同时货架损坏',
    '其他异常需人工确认',
    '现场情况不明'
  ]
  const truthOf = (content) => {
    let best = null
    for (const r of cfg.keywordRules) {
      if (!content.includes(r.keyword)) continue
      const w = keywordWeights[r.keyword] || 1
      if (!best || w > best.w) best = { type: r.type, w }
    }
    return best ? best.type : cfg.defaultType
  }
  const ambiguous = contents.map((c) => ({ content: c, truthType: truthOf(c) }))
  const orderAcc = ambiguous.filter((s) => classifyByOrder(s.content, cfg).type === s.truthType).length / ambiguous.length
  const scoreAcc = ambiguous.filter((s) => classifyByScore(s.content, cfg, keywordWeights).type === s.truthType).length / ambiguous.length
  out.类型判定 = {
    样本数: ambiguous.length,
    '关键词顺序命中_准确率': round(orderAcc, 4),
    '关键词加权打分_准确率': round(scoreAcc, 4),
    误判样本_顺序命中: ambiguous.filter((s) => classifyByOrder(s.content, cfg).type !== s.truthType).map((s) => s.content),
    说明: '顺序命中在「破损…客户投诉」「故障…客户投诉」上被宽泛词截走；特异度权重可配'
  }

  // B) 派单仿真对比
  const tickets = buildTickets(cfg)
  const t0 = nowMs()
  const r1 = simulate(tickets, cfg, 'none')
  const t1 = nowMs()
  const r2 = simulate(tickets, cfg, 'roundRobin')
  const t2 = nowMs()
  const r3 = simulate(tickets, cfg, 'score')
  const t3 = nowMs()
  out.派单仿真 = [
    { ...r1, 运行ms: round(t1 - t0, 3), 名称: '基线·不派单(人工认领)' },
    { ...r2, 运行ms: round(t2 - t1, 3), 名称: '经验补丁·轮转指派' },
    { ...r3, 运行ms: round(t3 - t2, 3), 名称: '算法·多目标加权' }
  ]

  // C) 权重敏感性：技能 vs 负载 的取舍（口径需用户确认）
  out.权重敏感性 = [
    { 说明: '重技能', cfg: { ...cfg, wSkill: 4, wLoad: 0.5 } },
    { 说明: '重负载均衡', cfg: { ...cfg, wSkill: 1, wLoad: 4 } },
    { 说明: '默认', cfg }
  ].map((item) => ({ 说明: item.说明, ...simulate(tickets, item.cfg, 'score') }))

  // D) 规模曲线
  out.规模曲线 = [200, 1000, 5000].map((n) => {
    const big = buildTickets({ ...cfg, tickets: n })
    const s = nowMs()
    simulate(big, cfg, 'score')
    const e = nowMs()
    return { 工单数: n, 员工数: cfg.employees, '多目标仿真ms': round(e - s, 3) }
  })

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
