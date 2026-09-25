import { activeEmployees, employeeName, stationName } from './db.js'
import { CODE, KPI_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { createPersistBucket } from './persist.js'
import { createRandom, currentMonth, formatDateTime, monthShift, paginate, shiftDays } from './util.js'

/**
 * KPI 考核数据层（需求7：可配指标项 + 权重，自动算分）
 *
 * 两张表：
 * - kpi_metric 指标配置（类型 / 权重 / 目标值 / 评分规则 / 适用角色 / 启用状态），可增删改
 * - kpi_score  按月按员工按指标的评分快照（含算分所用的指标配置副本，得分明细页可逐项解释「这分怎么来的」）
 *
 * 原始业绩值不入库：由「员工 + 月份 + 指标」哈希出的确定性序列模拟业务数据源，
 * 这样「改了权重/目标值 → 重算 → 分数变化」的演示链路成立，且同参数重算结果恒定（刷新不变）。
 * TODO(扩展): 接入真实业务库后，actualValue 改为从包裹/工单/考勤明细实时统计，本模块只保留算分与排名。
 *
 * 总分口径：Σ(得分 × 权重) / Σ(适用指标权重)。按员工实际适用的指标权重归一，
 * 因此「适用角色」不同的员工总分仍可横向比较，不会因为缺少某类指标被系统性压低。
 */

export const KPI_METRIC_TYPE_LABEL = {
  PARCEL: '派件量',
  PICKUP: '取件及时率',
  COMPLAINT: '客户投诉',
  ATTENDANCE: '出勤打卡',
  SERVICE: '服务评分',
  WORK_ORDER: '工单处理',
  TRAINING: '培训完成率',
  OTHER: '其他'
}
export const KPI_SCORE_MODE_LABEL = { LINEAR: '线性折算', TIERED: '阶梯评分', BINARY: '达标即满分' }
export const KPI_DIRECTION_LABEL = { UP: '越高越好', DOWN: '越低越好' }

/** 考核等级：页面只需展示 label，等级色由前端按 level 取值映射，Mock 不掺视觉决策 */
const LEVELS = [
  { min: 90, level: 'EXCELLENT', label: '优秀' },
  { min: 80, level: 'GOOD', label: '良好' },
  { min: 70, level: 'PASS', label: '合格' },
  { min: 0, level: 'IMPROVE', label: '待改进' }
]

const bucket = createPersistBucket('kpi')
let state = null

/* ==================== 种子 ==================== */

/**
 * 指标种子：6 项启用（权重合计恰好 100）+ 1 项停用。
 * 覆盖三种评分规则、两个方向（越高越好 / 越低越好）、角色限定（服务评分只考核 STAFF）四类形态。
 */
const METRIC_SEED = [
  {
    metricKey: 'PARCEL',
    metricName: '月派件量',
    metricType: 'PARCEL',
    weight: 30,
    targetValue: 1200,
    unit: '件',
    direction: 'UP',
    scoreRule: { mode: 'LINEAR', fullScore: 100 },
    roleScope: null
  },
  {
    metricKey: 'PICKUP_TIMELY',
    metricName: '取件及时率',
    metricType: 'PICKUP',
    weight: 20,
    targetValue: 95,
    unit: '%',
    direction: 'UP',
    scoreRule: { mode: 'TIERED', fullScore: 100 },
    roleScope: null
  },
  {
    metricKey: 'COMPLAINT',
    metricName: '客户投诉件数',
    metricType: 'COMPLAINT',
    weight: 15,
    targetValue: 0,
    unit: '件',
    direction: 'DOWN',
    scoreRule: { mode: 'BINARY', fullScore: 100 },
    roleScope: null
  },
  {
    metricKey: 'ATTENDANCE',
    metricName: '出勤打卡合格率',
    metricType: 'ATTENDANCE',
    weight: 15,
    targetValue: 100,
    unit: '%',
    direction: 'UP',
    scoreRule: { mode: 'LINEAR', fullScore: 100 },
    roleScope: null
  },
  {
    metricKey: 'SERVICE',
    metricName: '服务评分',
    metricType: 'SERVICE',
    weight: 10,
    targetValue: 4.8,
    unit: '分',
    direction: 'UP',
    scoreRule: { mode: 'LINEAR', fullScore: 100 },
    roleScope: ['STAFF']
  },
  {
    metricKey: 'WORK_ORDER',
    metricName: '工单处理量',
    metricType: 'WORK_ORDER',
    weight: 10,
    targetValue: 20,
    unit: '件',
    direction: 'UP',
    scoreRule: { mode: 'TIERED', fullScore: 100 },
    roleScope: null
  },
  {
    metricKey: 'TRAINING',
    metricName: '培训完成率',
    metricType: 'TRAINING',
    weight: 5,
    targetValue: 100,
    unit: '%',
    direction: 'UP',
    scoreRule: { mode: 'LINEAR', fullScore: 100 },
    roleScope: null,
    enabled: 0,
    remark: '本期暂不纳入考核'
  }
]

/** 考核月份种子：本期 + 上一期，满足「至少覆盖 2 个月」的演示口径 */
export const SEED_MONTHS = () => [monthShift(currentMonth(), -1), currentMonth()]

function buildSeed() {
  const metrics = METRIC_SEED.map((item, index) => ({
    id: index + 1,
    metricKey: item.metricKey,
    metricName: item.metricName,
    metricType: item.metricType,
    weight: item.weight,
    targetValue: item.targetValue,
    unit: item.unit,
    direction: item.direction,
    scoreRule: { ...item.scoreRule },
    roleScope: item.roleScope ? [...item.roleScope] : null,
    enabled: item.enabled === undefined ? 1 : item.enabled,
    sortOrder: index + 1,
    remark: item.remark || null,
    createTime: formatDateTime(shiftDays(-120, 9, 0, 0)),
    updateTime: formatDateTime(shiftDays(-40, 9, 0, 0))
  }))
  const seed = { seq: { metric: metrics.length, score: 0 }, metrics, scores: [] }
  state = seed
  // 种子即「月度算分已跑过」的状态：两个考核月、全部在职员工，算分时间落在各月 1 号
  SEED_MONTHS().forEach((month) => {
    const at = month === currentMonth() ? shiftDays(-1, 8, 30, 0) : shiftDays(-31, 8, 30, 0)
    calculate(month, null, null, formatDateTime(at))
  })
  return seed
}

function ensure() {
  if (state) return state
  const snapshot = bucket.read()
  state = snapshot || buildSeed()
  return state
}

/** 清空快照与内存态，下次查询回到种子（T16「重置演示数据」复用） */
export function resetKpiStore() {
  state = null
  bucket.clear()
}

/* ==================== 算分内核 ==================== */

/** FNV-1a 字符串哈希：把「员工 + 月份 + 指标」压成一个种子，保证业绩数据可复现 */
function seedOf(text) {
  let h = 2166136261
  for (let i = 0; i < text.length; i += 1) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** 模拟业务数据源产出的实际值：按单位取值域，投诉类（越低越好）取 0-3 件 */
function actualValueOf(metric, employeeId, month) {
  const random = createRandom(seedOf(`${metric.metricKey}#${employeeId}#${month}`))
  const target = Number(metric.targetValue) || 0
  if (metric.direction === 'DOWN') return Math.floor(random() * 4)
  if (metric.unit === '%') return Number(Math.min(100, target * (0.86 + random() * 0.18)).toFixed(1))
  if (metric.unit === '分') return Number(Math.min(5, target * (0.88 + random() * 0.2)).toFixed(2))
  return Math.round(target * (0.55 + random() * 0.65))
}

/** 达成率：越低越好的指标按「目标 / 实际」折算，目标值未配置时退化为「有数据即达标」 */
function achievementOf(metric, actual) {
  const target = Number(metric.targetValue)
  if (metric.direction === 'DOWN') {
    if (!Number.isFinite(target)) return actual > 0 ? 0 : 1
    return actual <= target ? 1 : Number((target / actual).toFixed(4))
  }
  if (!Number.isFinite(target) || target <= 0) return actual > 0 ? 1 : 0
  return Number((actual / target).toFixed(4))
}

/** 评分规则 → 单项得分：规则是配置数据，算分只认 mode，不认指标名（换指标不必改代码） */
function calcItemScore(scoreRule, achievementRate) {
  const full = Number(scoreRule && scoreRule.fullScore) || 100
  const mode = (scoreRule && scoreRule.mode) || 'LINEAR'
  if (mode === 'BINARY') return achievementRate >= 1 ? full : 0
  if (mode === 'TIERED') {
    const tier =
      achievementRate >= 1
        ? 1
        : achievementRate >= 0.9
          ? 0.9
          : achievementRate >= 0.8
            ? 0.8
            : achievementRate >= 0.6
              ? 0.6
              : 0
    return Math.round(full * tier)
  }
  return Math.round(Math.min(1, achievementRate) * full)
}

/** 指标是否适用于该员工：roleScope 为空表示全员适用 */
const appliesTo = (metric, employee) =>
  !metric.roleScope || !metric.roleScope.length || metric.roleScope.includes(employee.role)

function upsertScore(employee, month, metric, calculateTime) {
  const actualValue = actualValueOf(metric, employee.id, month)
  const achievementRate = achievementOf(metric, actualValue)
  const score = calcItemScore(metric.scoreRule, achievementRate)
  const data = {
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId: employee.station_id,
    month,
    metricId: metric.id,
    metricKey: metric.metricKey,
    metricName: metric.metricName,
    metricType: metric.metricType,
    weight: metric.weight,
    targetValue: metric.targetValue,
    unit: metric.unit,
    direction: metric.direction,
    // 快照评分规则：指标后续被改动或删除，历史得分的解释口径仍然完整
    scoreRule: { ...metric.scoreRule },
    actualValue,
    achievementRate,
    score,
    weightedScore: Number(((score * metric.weight) / 100).toFixed(2)),
    calculateTime
  }
  const row = state.scores.find((s) => s.employeeId === employee.id && s.month === month && s.metricId === metric.id)
  if (row) Object.assign(row, data)
  else state.scores.push({ id: (state.seq.score += 1), ...data })
}

/**
 * 按月算分：覆盖式重算（同一员工同一月同一指标更新，不再适用的指标行删除），
 * 因此「停用某指标 → 重算」不会留下幽灵权重。
 */
function calculate(month, stationId, employeeIds, calculateTime) {
  const metrics = state.metrics.filter((m) => m.enabled === 1)
  if (!metrics.length) return { code: KPI_CODE.NO_METRIC }
  const idFilter = Array.isArray(employeeIds) && employeeIds.length ? new Set(employeeIds.map(Number)) : null
  const employees = activeEmployees().filter((e) => {
    if (e.status !== 1) return false
    if (idFilter && !idFilter.has(e.id)) return false
    return stationId == null || e.station_id === Number(stationId)
  })
  const validMetricIds = new Set(metrics.map((m) => m.id))
  let scoreCount = 0
  let employeeCount = 0

  employees.forEach((employee) => {
    const usable = metrics.filter((m) => appliesTo(m, employee))
    if (!usable.length) return
    // 先清掉本次重算范围内「已停用/已删除指标」的旧行，避免旧权重继续计入总分
    state.scores = state.scores.filter(
      (s) => !(s.employeeId === employee.id && s.month === month && !validMetricIds.has(s.metricId))
    )
    usable.forEach((metric) => {
      upsertScore(employee, month, metric, calculateTime)
      scoreCount += 1
    })
    employeeCount += 1
  })
  return { code: 200, data: { month, employeeCount, metricCount: metrics.length, scoreCount } }
}

/* ==================== 指标配置 ==================== */

/** 启用指标权重合计（page 侧展示提示用；算分本身按适用权重归一，不依赖必须等于 100） */
export const weightSum = () =>
  ensure()
    .metrics.filter((m) => m.enabled === 1)
    .reduce((sum, m) => sum + Number(m.weight), 0)

function toMetricVO(metric) {
  return {
    ...metric,
    scoreRule: { ...metric.scoreRule },
    roleScope: metric.roleScope ? [...metric.roleScope] : null,
    metricTypeLabel: KPI_METRIC_TYPE_LABEL[metric.metricType] || metric.metricType,
    directionLabel: KPI_DIRECTION_LABEL[metric.direction] || metric.direction,
    scoreModeLabel: KPI_SCORE_MODE_LABEL[(metric.scoreRule || {}).mode] || ''
  }
}

export function listMetrics() {
  ensure()
  const sorted = ensure()
    .metrics.slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
  return { list: sorted.map(toMetricVO), weightSum: weightSum() }
}

export function findMetric(id) {
  ensure()
  const metric = state.metrics.find((m) => m.id === Number(id))
  return metric ? toMetricVO(metric) : null
}

/**
 * 权重守卫：只在「启用 / 停用 / 新增启用指标 / 删除」四处校验合计 = 100。
 * 为什么不在每次改权重时也强校验：页面调权重通常要分多步改，逐条强校验会让权重无法调整；
 * 而算分按适用权重归一，合计临时偏离不会算错分，页面按 weightSum 提示即可。
 */
function weightError(metrics) {
  const enabled = metrics.filter((m) => m.enabled === 1)
  if (!enabled.length) return null
  const sum = enabled.reduce((total, m) => total + Number(m.weight), 0)
  if (sum === 100) return null
  return { code: KPI_CODE.WEIGHT_SUM_INVALID, message: `启用指标权重合计须为 100%，当前为 ${sum}%` }
}

function pickWritable(body) {
  const payload = {}
  if (body.metricKey !== undefined) payload.metricKey = String(body.metricKey).trim()
  if (body.metricName !== undefined) payload.metricName = String(body.metricName).trim()
  if (body.metricType !== undefined) payload.metricType = body.metricType
  if (body.weight !== undefined) payload.weight = Number(body.weight)
  if (body.targetValue !== undefined) payload.targetValue = Number(body.targetValue)
  if (body.unit !== undefined) payload.unit = String(body.unit).trim()
  if (body.direction !== undefined) payload.direction = body.direction
  if (body.scoreRule !== undefined)
    payload.scoreRule = { mode: body.scoreRule.mode, fullScore: Number(body.scoreRule.fullScore) }
  if (body.roleScope !== undefined) payload.roleScope = body.roleScope === null ? null : body.roleScope.map(String)
  if (body.enabled !== undefined) payload.enabled = Number(body.enabled)
  if (body.sortOrder !== undefined) payload.sortOrder = Number(body.sortOrder)
  if (body.remark !== undefined) payload.remark = body.remark === null ? null : String(body.remark).trim()
  return payload
}

export function createMetric(body) {
  ensure()
  const payload = pickWritable(body)
  const next = {
    id: (state.seq.metric += 1),
    enabled: 1,
    sortOrder: state.metrics.length + 1,
    roleScope: null,
    remark: null,
    createTime: formatDateTime(new Date()),
    updateTime: formatDateTime(new Date()),
    ...payload
  }
  const error = weightError([...state.metrics, next])
  if (error) return error
  state.metrics.push(next)
  bucket.write(state)
  return { code: 200, data: toMetricVO(next) }
}

/** 指标 key 全局唯一：评分快照按 metricKey 可读，重名会让明细页出现两行同名指标 */
export const metricKeyExists = (metricKey, excludeId = null) =>
  ensure().metrics.some((m) => m.metricKey === String(metricKey) && m.id !== Number(excludeId))

export function updateMetric(id, body) {
  ensure()
  const metric = state.metrics.find((m) => m.id === Number(id))
  if (!metric) return { code: KPI_CODE.METRIC_NOT_EXISTS }
  const payload = pickWritable(body)
  const error = weightError(state.metrics.map((m) => (m.id === metric.id ? { ...m, ...payload } : m)))
  if (error) return error
  Object.assign(metric, payload, { updateTime: formatDateTime(new Date()) })
  bucket.write(state)
  return { code: 200, data: toMetricVO(metric) }
}

export function removeMetric(id) {
  ensure()
  const index = state.metrics.findIndex((m) => m.id === Number(id))
  if (index < 0) return { code: KPI_CODE.METRIC_NOT_EXISTS }
  const error = weightError(state.metrics.filter((m) => m.id !== Number(id)))
  if (error) return error
  state.metrics.splice(index, 1)
  // 历史评分行保留（快照自洽，明细页仍可解释），但不再参与任何已重算月份的总分
  bucket.write(state)
  return { code: 200, data: null }
}

/**
 * 批量保存指标（权重 + 启用状态）—— 支撑页面「权重合计条 + 保存」的一次原子提交
 *
 * 为什么单指标接口不够：updateMetric 每次都会校验「启用指标合计 = 100%」，
 * 而调权重天然要多步（如 30/20 调成 40/10），任何中间态都不等于 100%，逐条提交必然卡死；
 * 启用/停用同理（停用一个权重非 0 的指标会立刻让合计偏离 100%）。
 * 本接口把整组变更合成一次提交、只做一次合计校验，语义与单指标接口完全一致（启用合计必须为 100%）。
 */
export function saveMetricBatch(items) {
  ensure()
  if (!Array.isArray(items) || !items.length) return { code: CODE.BAD_REQUEST, message: 'items 须为非空数组' }
  const patch = new Map()
  for (const item of items) {
    const metric = state.metrics.find((m) => m.id === Number(item.id))
    if (!metric) return { code: KPI_CODE.METRIC_NOT_EXISTS, message: `指标不存在：${item.id}` }
    const next = {}
    if (item.weight !== undefined) {
      const weight = Number(item.weight)
      if (!Number.isInteger(weight) || weight < 0 || weight > 100)
        return { code: CODE.BAD_REQUEST, message: '权重须为 0-100 的整数' }
      next.weight = weight
    }
    if (item.enabled !== undefined) {
      if (![0, 1, true, false].includes(item.enabled))
        return { code: CODE.BAD_REQUEST, message: 'enabled 仅支持 0 / 1' }
      next.enabled = Number(item.enabled)
    }
    patch.set(metric.id, next)
  }
  const error = weightError(state.metrics.map((m) => (patch.has(m.id) ? { ...m, ...patch.get(m.id) } : m)))
  if (error) return error
  const now = formatDateTime(new Date())
  patch.forEach((next, id) => {
    Object.assign(
      state.metrics.find((m) => m.id === id),
      next,
      { updateTime: now }
    )
  })
  bucket.write(state)
  return { code: 200, data: { weightSum: weightSum(), updated: patch.size } }
}

/* ==================== 算分与查询 ==================== */

export function calculateScores({ month, stationId, employeeIds }) {
  ensure()
  const result = calculate(month, stationId, employeeIds, formatDateTime(new Date()))
  if (result.code !== 200) return result
  bucket.write(state)
  return result
}

/** 按员工聚合当月总分：算分行的唯一聚合入口，列表与排名共用，避免两处各写一套加权口径 */
function summarize(month, stationId) {
  const groups = new Map()
  state.scores
    .filter((s) => s.month === month)
    .forEach((row) => {
      if (stationId != null && row.stationId !== Number(stationId)) return
      let group = groups.get(row.employeeId)
      if (!group) {
        group = {
          employeeId: row.employeeId,
          employeeName: employeeName(row.employeeId),
          stationId: row.stationId,
          month,
          weightSum: 0,
          weightedSum: 0,
          rateSum: 0,
          metricCount: 0,
          calculateTime: row.calculateTime
        }
        groups.set(row.employeeId, group)
      }
      group.weightSum += Number(row.weight)
      group.weightedSum += Number(row.score) * Number(row.weight)
      group.rateSum += Number(row.achievementRate)
      group.metricCount += 1
      if (row.calculateTime > group.calculateTime) group.calculateTime = row.calculateTime
    })

  const list = [...groups.values()].map((group) => {
    const totalScore = group.weightSum ? Number((group.weightedSum / group.weightSum).toFixed(1)) : 0
    const achievementRate = group.metricCount ? Number((group.rateSum / group.metricCount).toFixed(4)) : 0
    const level = LEVELS.find((item) => totalScore >= item.min)
    return {
      employeeId: group.employeeId,
      employeeName: group.employeeName || `员工${group.employeeId}`,
      stationId: group.stationId,
      stationName: stationName(group.stationId),
      month: group.month,
      totalScore,
      achievementRate,
      level: level.level,
      levelLabel: level.label,
      metricCount: group.metricCount,
      calculateTime: group.calculateTime
    }
  })
  // 并列同名次（竞赛排名法）：同分同达成率共享同一名次，避免同分却分出先后
  list.sort(
    (a, b) => b.totalScore - a.totalScore || b.achievementRate - a.achievementRate || a.employeeId - b.employeeId
  )
  let rank = 0
  let prevKey = null
  list.forEach((row, index) => {
    const key = `${row.totalScore}|${row.achievementRate}`
    if (key !== prevKey) rank = index + 1
    row.rank = rank
    prevKey = key
  })
  return list
}

export function queryScores({ month, stationId, employeeId, pageNum, pageSize }) {
  ensure()
  let rows = summarize(month, stationId)
  if (employeeId != null && employeeId !== '') rows = rows.filter((row) => row.employeeId === Number(employeeId))
  rows.sort((a, b) => a.employeeId - b.employeeId)
  const page = paginate(rows, pageNum, pageSize)
  page.month = month
  return page
}

export function queryRanking({ month, stationId, pageNum, pageSize }) {
  ensure()
  const rows = summarize(month, stationId)
  const page = paginate(rows, pageNum, pageSize)
  page.month = month
  // 排名榜头部的三行摘要：页面不必再拉全量数据自己算
  page.count = rows.length
  page.avgScore = rows.length
    ? Number((rows.reduce((sum, row) => sum + row.totalScore, 0) / rows.length).toFixed(1))
    : 0
  page.topScore = rows.length ? rows[0].totalScore : 0
  return page
}

/** 某员工某月得分明细：逐指标列出目标/实际/达成率/单项分/加权分 */
export function scoreDetail(employeeId, month) {
  ensure()
  const rows = state.scores
    .filter((s) => s.employeeId === Number(employeeId) && s.month === month)
    .sort((a, b) => b.weight - a.weight || a.metricId - b.metricId)
  if (!rows.length) return { code: KPI_CODE.SCORE_NOT_EXISTS }
  const summary = summarize(month, null).find((row) => row.employeeId === Number(employeeId)) || null
  return {
    code: 200,
    data: {
      employeeId: Number(employeeId),
      employeeName: rows[0].employeeName,
      stationId: rows[0].stationId,
      stationName: stationName(rows[0].stationId),
      month,
      totalScore: summary ? summary.totalScore : 0,
      achievementRate: summary ? summary.achievementRate : 0,
      rank: summary ? summary.rank : null,
      level: summary ? summary.level : null,
      levelLabel: summary ? summary.levelLabel : null,
      metricCount: rows.length,
      weightSum: rows.reduce((sum, row) => sum + Number(row.weight), 0),
      calculateTime: rows.reduce(
        (latest, row) => (row.calculateTime > latest ? row.calculateTime : latest),
        rows[0].calculateTime
      ),
      items: rows.map((row) => ({
        metricId: row.metricId,
        metricKey: row.metricKey,
        metricName: row.metricName,
        metricType: row.metricType,
        metricTypeLabel: KPI_METRIC_TYPE_LABEL[row.metricType] || row.metricType,
        weight: row.weight,
        targetValue: row.targetValue,
        unit: row.unit,
        direction: row.direction,
        actualValue: row.actualValue,
        achievementRate: row.achievementRate,
        score: row.score,
        weightedScore: row.weightedScore,
        scoreRule: { ...row.scoreRule },
        scoreModeLabel: KPI_SCORE_MODE_LABEL[(row.scoreRule || {}).mode] || ''
      }))
    }
  }
}

/** 某员工某月总分（财务域 KPI 规则项的唯一数据来源，无记录返回 null） */
export function scoreOf(employeeId, month) {
  ensure()
  const summary = summarize(month, null).find((row) => row.employeeId === Number(employeeId))
  return summary ? summary.totalScore : null
}
