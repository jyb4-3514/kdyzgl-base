import { CODE } from '../../constants/errorCode.js'
import { currentMonth, fail, ok } from '../util.js'
import { isBlank, isMonth, pageSizeInvalid, textLen } from '../validate.js'
import {
  KPI_DIRECTION_LABEL,
  KPI_METRIC_TYPE_LABEL,
  KPI_SCORE_MODE_LABEL,
  calculateScores,
  createMetric,
  listMetrics,
  metricKeyExists,
  queryRanking,
  queryScores,
  removeMetric,
  saveMetricBatch,
  scoreDetail,
  updateMetric
} from '../kpiStore.js'

/**
 * KPI 考核接口（需求7）
 * 指标配置与算分只有老板（ADMIN）可操作；得分与排名站长可见（按驿站收敛），员工只能看本人明细。
 * 越权口径与考勤/工单一致：非 ADMIN 的 stationId 一律用其归属驿站覆盖，传别的驿站不报错也不生效。
 */

const METRIC_TYPES = Object.keys(KPI_METRIC_TYPE_LABEL)
const SCORE_MODES = Object.keys(KPI_SCORE_MODE_LABEL)
const DIRECTIONS = Object.keys(KPI_DIRECTION_LABEL)
const ROLE_SCOPES = ['ADMIN', 'STATION_ADMIN', 'STAFF']

/** 非 ADMIN 的数据范围：本人归属驿站 */
const scopedStationId = (user, raw) => (user.role === 'ADMIN' ? (isBlank(raw) ? null : Number(raw)) : user.station_id)

const monthOf = (value) => (isBlank(value) ? currentMonth() : String(value))

function validateMetric(body, isCreate) {
  if (body.metricKey !== undefined || isCreate) {
    if (!textLen(body.metricKey, 2, 50)) return '指标标识长度须为 2-50'
    if (!/^[A-Z][A-Z0-9_]*$/.test(String(body.metricKey))) return '指标标识须为大写字母、数字与下划线'
  }
  if (body.metricName !== undefined || isCreate) {
    if (!textLen(body.metricName, 1, 50)) return '指标名称长度须为 1-50'
  }
  if (body.metricType !== undefined || isCreate) {
    if (!METRIC_TYPES.includes(body.metricType)) return `指标类型仅支持 ${METRIC_TYPES.join(' / ')}`
  }
  if (body.weight !== undefined || isCreate) {
    const weight = Number(body.weight)
    if (!Number.isInteger(weight) || weight < 0 || weight > 100) return '权重须为 0-100 的整数'
  }
  if (body.targetValue !== undefined || isCreate) {
    if (!Number.isFinite(Number(body.targetValue)) || Number(body.targetValue) < 0) return '目标值须为不小于 0 的数字'
  }
  if (body.direction !== undefined || isCreate) {
    if (!DIRECTIONS.includes(body.direction)) return `direction 仅支持 ${DIRECTIONS.join(' / ')}`
  }
  if (body.scoreRule !== undefined || isCreate) {
    const rule = body.scoreRule
    if (!rule || !SCORE_MODES.includes(rule.mode)) return `scoreRule.mode 仅支持 ${SCORE_MODES.join(' / ')}`
    if (!Number.isFinite(Number(rule.fullScore)) || Number(rule.fullScore) <= 0 || Number(rule.fullScore) > 100)
      return 'scoreRule.fullScore 须为 0-100 的数字'
  }
  if (body.roleScope !== undefined && body.roleScope !== null) {
    if (!Array.isArray(body.roleScope) || body.roleScope.some((role) => !ROLE_SCOPES.includes(role)))
      return 'roleScope 须为角色数组'
  }
  if (body.enabled !== undefined && ![0, 1, true, false].includes(body.enabled)) return 'enabled 仅支持 0 / 1'
  if (body.sortOrder !== undefined && !Number.isInteger(Number(body.sortOrder))) return 'sortOrder 须为整数'
  return null
}

function metricList() {
  return ok(listMetrics())
}

function metricCreate({ body }) {
  const error = validateMetric(body, true)
  if (error) return fail(CODE.BAD_REQUEST, error)
  if (metricKeyExists(body.metricKey)) return fail(CODE.BAD_REQUEST, '指标标识已存在')
  const result = createMetric(body)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function metricUpdate({ pathParams, body }) {
  const error = validateMetric(body, false)
  if (error) return fail(CODE.BAD_REQUEST, error)
  if (body.metricKey !== undefined && metricKeyExists(body.metricKey, pathParams.id))
    return fail(CODE.BAD_REQUEST, '指标标识已存在')
  const result = updateMetric(pathParams.id, body)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function metricDelete({ pathParams }) {
  const result = removeMetric(pathParams.id)
  return result.code === 200 ? ok(null) : fail(result.code, result.message)
}

/** 批量保存指标（权重 + 启用状态）：页面「权重合计条 + 保存」一次原子提交（见 kpiStore.saveMetricBatch 的理由） */
function metricBatchSave({ body }) {
  const result = saveMetricBatch(body.items)
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/** 按月批量算分：month 必填（算哪个月必须由调用方明确，避免默认到本月把演示数据改脏） */
function calculate({ body }) {
  if (!isMonth(body.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  if (body.employeeIds !== undefined && !Array.isArray(body.employeeIds))
    return fail(CODE.BAD_REQUEST, 'employeeIds 须为数组')
  const result = calculateScores({
    month: body.month,
    stationId: isBlank(body.stationId) ? null : Number(body.stationId),
    employeeIds: body.employeeIds
  })
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

function scoreList({ params, user }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isMonth(params.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  return ok(
    queryScores({
      month: monthOf(params.month),
      stationId: scopedStationId(user, params.stationId),
      employeeId: params.employeeId,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function ranking({ params, user }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isMonth(params.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  return ok(
    queryRanking({
      month: monthOf(params.month),
      stationId: scopedStationId(user, params.stationId),
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

/** 得分明细：员工只能查本人（越权返回 403），站长限本驿站，老板全量 */
function detail({ params, pathParams, user }) {
  // 路由为 /kpi/scores/:employeeId，员工号是路径参数；兼容查询串写法，避免旧调用方拿到 400
  const rawId = pathParams.employeeId !== undefined ? pathParams.employeeId : params.employeeId
  const employeeId = Number(rawId)
  if (!Number.isInteger(employeeId) || employeeId <= 0) return fail(CODE.BAD_REQUEST, 'employeeId 非法')
  if (!isMonth(params.month)) return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  if (user.role === 'STAFF' && user.id !== employeeId) return fail(CODE.FORBIDDEN, '无权查看他人考核结果')
  const result = scoreDetail(employeeId, monthOf(params.month))
  if (result.code !== 200) return fail(result.code, result.message)
  if (user.role === 'STATION_ADMIN' && result.data.stationId !== user.station_id)
    return fail(CODE.FORBIDDEN, '无权查看其他驿站的考核结果')
  return ok(result.data)
}

/** 路由注册顺序：/kpi/scores/ranking 必须排在 /kpi/scores/:employeeId 之前，否则会被路径参数吞掉；
 *  /kpi/metrics/batch 同理，必须排在 /kpi/metrics/:id 之前 */
export const kpiRoutes = [
  { method: 'get', path: '/kpi/metrics', roles: ['ADMIN'], handler: metricList },
  { method: 'post', path: '/kpi/metrics', roles: ['ADMIN'], handler: metricCreate },
  { method: 'put', path: '/kpi/metrics/batch', roles: ['ADMIN'], handler: metricBatchSave },
  { method: 'put', path: '/kpi/metrics/:id', roles: ['ADMIN'], handler: metricUpdate },
  { method: 'delete', path: '/kpi/metrics/:id', roles: ['ADMIN'], handler: metricDelete },
  { method: 'post', path: '/kpi/scores/calculate', roles: ['ADMIN'], handler: calculate },
  { method: 'get', path: '/kpi/scores/ranking', roles: ['ADMIN', 'STATION_ADMIN'], handler: ranking },
  { method: 'get', path: '/kpi/scores', roles: ['ADMIN', 'STATION_ADMIN'], handler: scoreList },
  { method: 'get', path: '/kpi/scores/:employeeId', handler: detail }
]
