import { db, findStationById, saveSyncConfig, stationName } from '../db.js'
import { CODE, DEMO_CODE, STATION_CODE } from '../../constants/errorCode.js'
import { fail, ok } from '../util.js'
import { isBlank, textLen } from '../validate.js'
import {
  findOptionSet,
  getGlobalValues,
  getStationConfig,
  isClock,
  isEndClock,
  legacyFrequencyOf,
  minutesOfClock,
  optionLabelOf,
  resolveDataSourceKey,
  resolveOptionKey,
  setStationOverrides,
  templateTimeOf
} from '../syncConfigStore.js'

/**
 * 驿站采集配置与采集状态总览（需求1：同步任务模块化）
 *
 * 为什么独立于 syncTask.js：既有 /sync-tasks 是「批次流水」（一次同步一行），
 * 本模块是「驿站维度的采集配置 + 状态看板」，两者读写对象与查询口径都不同，
 * 混在一个文件里会让「批次状态机」与「采集配置」两套逻辑互相干扰。
 *
 * 采集状态（collectState）由配置行派生，四种状态口径见 db.js 的 SYNC_CONFIG_SEED 说明：
 * status=0 → DISABLED；enabled=0 → UNCONFIGURED；lastCollectStatus=FAILED → ABNORMAL；其余 → NORMAL。
 *
 * 配置值已迁到配置中心模型（配置项 + 全局默认 + 驿站覆盖，见 syncConfigStore.js）：
 * 本文件的旧字段 frequency / dataSource / collectStartTime / collectEndTime 一律输出「生效值」，
 * 保证既有 PC 采集配置抽屉与移动端只读状态页零改动可读。
 */

const COLLECT_STATES = { NORMAL: '正常', ABNORMAL: '异常', UNCONFIGURED: '未配置', DISABLED: '已停用' }

/** 配置行 → 采集状态（看板计数与明细共用，避免两处各写一套判定） */
function collectStateOf(config) {
  if (config.status === 0) return 'DISABLED'
  if (config.enabled === 0) return 'UNCONFIGURED'
  return config.last_collect_status === 'FAILED' ? 'ABNORMAL' : 'NORMAL'
}

/** 该站最近一个同步批次（按创建时间倒序取第一条），没有批次时为 null */
function lastBatchOf(stationId) {
  const rows = db.syncTasks.filter((t) => t.station_id === Number(stationId))
  if (!rows.length) return null
  const task = rows.reduce((latest, row) => (row.create_time > latest.create_time ? row : latest))
  return {
    batchNo: task.batch_no,
    status: task.status,
    parcelTotal: task.parcel_total,
    successCount: task.success_count,
    failCount: task.fail_count,
    createTime: task.create_time
  }
}

function toConfigVO(config) {
  const { values, sources, overrides } = getStationConfig(config.station_id)
  const template = templateTimeOf(values.time_template)
  const dataSourceKey = values.data_source
  return {
    id: config.id,
    stationId: config.station_id,
    stationName: stationName(config.station_id),
    enabled: config.enabled === 1,
    // 旧字段全部返回生效值：frequency 能反向映射时回旧码，dataSource 回中文显示名
    frequency: legacyFrequencyOf(values.collect_frequency),
    dataSource: dataSourceKey ? optionLabelOf('data_source', dataSourceKey) : null,
    collectStartTime: config.collect_start_time || (template ? template.startTime : null),
    collectEndTime: config.collect_end_time || (template ? template.endTime : null),
    lastCollectTime: config.last_collect_time,
    lastCollectStatus: config.last_collect_status,
    status: config.status,
    collectState: collectStateOf(config),
    collectStateLabel: COLLECT_STATES[collectStateOf(config)],
    lastBatch: lastBatchOf(config.station_id),
    updateTime: config.update_time,
    // 新模型：该站各配置项生效值 / 来源（继承或覆盖）/ 覆盖明细
    values,
    sources,
    overrides
  }
}

/** 数据范围：ADMIN 全量，其余角色收敛到本人驿站（与 /sync-tasks 越权口径一致） */
function scopedConfigs(user) {
  return user.role === 'ADMIN' ? db.syncConfigs : db.syncConfigs.filter((c) => c.station_id === user.station_id)
}

function list({ user }) {
  return ok(scopedConfigs(user).map(toConfigVO))
}

function detail({ pathParams, user }) {
  const stationId = Number(pathParams.stationId)
  if (user.role !== 'ADMIN' && stationId !== user.station_id) return fail(CODE.NOT_FOUND, '采集配置不存在')
  const config = db.syncConfigs.find((c) => c.station_id === stationId)
  if (!config) return fail(DEMO_CODE.SYNC_CONFIG_NOT_EXISTS) // 6002
  return ok(toConfigVO(config))
}

/**
 * 旧字段别名 → 新 overrides / resetKeys（兼容层）
 * TODO(扩展): 前端全部改用 overrides 后删除本函数与旧字段入参
 */
function planLegacyAliases(body) {
  const overrides = {}
  const resetKeys = []
  const dbPatch = {}
  if (body.frequency !== undefined) {
    const key = resolveOptionKey('collect_frequency', body.frequency)
    if (!key) return { error: 'frequency 仅支持选项集内已启用的档位（兼容旧码 HOURLY / EVERY_2H / EVERY_4H / DAILY）' }
    overrides.collect_frequency = key
  }
  if (body.dataSource !== undefined) {
    if (isBlank(body.dataSource)) resetKeys.push('data_source')
    else overrides.data_source = resolveDataSourceKey(body.dataSource)
  }
  if (body.collectStartTime !== undefined) {
    if (!isClock(body.collectStartTime)) return { error: '采集开始时间格式须为 HH:mm' }
    dbPatch.collect_start_time = body.collectStartTime
  }
  if (body.collectEndTime !== undefined) {
    if (!isEndClock(body.collectEndTime)) return { error: '采集结束时间格式须为 HH:mm' }
    dbPatch.collect_end_time = body.collectEndTime
  }
  // 起止恰好等于某个时段模板时同步登记模板覆盖，避免「模板」与「起止」两个口径各说各话
  if (dbPatch.collect_start_time && dbPatch.collect_end_time) {
    const set = findOptionSet('time_template')
    const hit =
      set &&
      set.options.find(
        (option) =>
          option.extraAttrs &&
          option.extraAttrs.startTime === dbPatch.collect_start_time &&
          option.extraAttrs.endTime === dbPatch.collect_end_time
      )
    if (hit) overrides.time_template = hit.optionKey
  }
  return { overrides, resetKeys, dbPatch }
}

/** 本次请求落库后，该站的覆盖集合（用于写前做跨字段校验，不落库也能算出生效值） */
function plannedOverridesOf(stationId, overrides, resetKeys) {
  const planned = { ...getStationConfig(stationId).overrides }
  resetKeys.forEach((itemKey) => delete planned[itemKey])
  Object.assign(planned, overrides)
  return planned
}

const effectiveOf = (planned, itemKey) => {
  const global = getGlobalValues()
  return Object.prototype.hasOwnProperty.call(planned, itemKey) ? planned[itemKey] : global[itemKey]
}

/** 驿站配置保存：布尔位 + 旧字段别名 + 新 overrides / resetKeys 三类入参统一落到同一处 */
function save({ body, pathParams }) {
  const stationId = Number(pathParams.stationId)
  if (!findStationById(stationId)) return fail(STATION_CODE.NOT_EXISTS) // 4001
  const current = db.syncConfigs.find((c) => c.station_id === stationId)

  for (const key of ['enabled', 'status']) {
    if (body[key] !== undefined && ![0, 1, true, false].includes(body[key]))
      return fail(CODE.BAD_REQUEST, `${key} 仅支持 0 / 1`)
  }
  if (body.dataSource !== undefined && !isBlank(body.dataSource) && !textLen(body.dataSource, 1, 50))
    return fail(CODE.BAD_REQUEST, '数据源名称长度须为 1-50')

  const legacy = planLegacyAliases(body)
  if (legacy.error) return fail(CODE.BAD_REQUEST, legacy.error)
  const overrides = {
    ...legacy.overrides,
    ...(body.overrides && typeof body.overrides === 'object' ? body.overrides : {})
  }
  const resetKeys = [...legacy.resetKeys, ...(Array.isArray(body.resetKeys) ? body.resetKeys : [])]

  const planned = plannedOverridesOf(stationId, overrides, resetKeys)
  const nextEnabled = body.enabled === undefined ? (current ? current.enabled : 0) : Number(body.enabled)
  // 启用采集前必须先有数据源，否则会落下「开关开着却没有采集来源」的矛盾配置（沿用既有口径）
  if (nextEnabled === 1 && isBlank(effectiveOf(planned, 'data_source')))
    return fail(CODE.BAD_REQUEST, '启用采集前须先选择数据源')

  const start = body.collectStartTime !== undefined ? body.collectStartTime : current && current.collect_start_time
  const end = body.collectEndTime !== undefined ? body.collectEndTime : current && current.collect_end_time
  if (start && end && minutesOfClock(start) >= minutesOfClock(end))
    return fail(CODE.BAD_REQUEST, '采集结束时间须晚于开始时间')

  if (Object.keys(overrides).length || resetKeys.length) {
    const result = setStationOverrides(stationId, overrides, resetKeys)
    if (result.code !== CODE.SUCCESS) return fail(result.code, result.message)
  }

  const patch = { ...legacy.dbPatch }
  if (body.enabled !== undefined) patch.enabled = Number(body.enabled)
  if (body.status !== undefined) patch.status = Number(body.status)
  if (body.frequency !== undefined) patch.frequency = planned.collect_frequency || null
  if (body.dataSource !== undefined) patch.data_source = planned.data_source || null
  const config = Object.keys(patch).length ? saveSyncConfig(stationId, patch) : current
  return ok(toConfigVO(config))
}

function overview({ user }) {
  const configs = scopedConfigs(user)
  const counts = { normal: 0, abnormal: 0, unconfigured: 0, disabled: 0 }
  const stations = configs.map((config) => {
    const state = collectStateOf(config)
    counts[
      state === 'NORMAL'
        ? 'normal'
        : state === 'ABNORMAL'
          ? 'abnormal'
          : state === 'UNCONFIGURED'
            ? 'unconfigured'
            : 'disabled'
    ] += 1
    const { values } = getStationConfig(config.station_id)
    return {
      stationId: config.station_id,
      stationName: stationName(config.station_id),
      collectState: state,
      collectStateLabel: COLLECT_STATES[state],
      enabled: config.enabled === 1,
      dataSource: values.data_source ? optionLabelOf('data_source', values.data_source) : null,
      lastCollectStatus: config.last_collect_status,
      lastCollectTime: config.last_collect_time,
      lastBatch: lastBatchOf(config.station_id)
    }
  })
  return ok({ total: configs.length, counts, stations })
}

export const syncConfigRoutes = [
  { method: 'get', path: '/sync/overview', roles: ['ADMIN', 'STATION_ADMIN'], handler: overview },
  { method: 'get', path: '/sync/configs', roles: ['ADMIN', 'STATION_ADMIN'], handler: list },
  { method: 'get', path: '/sync/configs/:stationId', roles: ['ADMIN', 'STATION_ADMIN'], handler: detail },
  { method: 'put', path: '/sync/configs/:stationId', roles: ['ADMIN'], handler: save }
]
