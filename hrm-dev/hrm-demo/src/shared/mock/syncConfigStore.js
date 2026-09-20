import { db, dbGeneration, stationName } from './db.js'
import { createPersistBucket } from './persist.js'
import { CODE, SYNC_CONFIG_CODE } from '../constants/errorCode.js'
import { formatDateTime } from './util.js'

/**
 * 同步配置中心数据层（同步任务自定义配置）
 *
 * 职责边界：只负责「配置项定义 / 选项集 / 全局默认值 / 驿站覆盖值」四层数据的读写与校验，
 * 以及把 db.syncConfigs 里的历史值一次性迁移到新模型。HTTP 形态由 routes/syncConfigCenter.js 组装，
 * CSV 列映射由 syncConfigCsv.js 组装，本文件不感知请求对象与文件格式。
 *
 * 为什么独立成模块而不并入 db.js：db.js 定位是「纯种子实体」，而本模块在种子之上还有
 * 一套可增删改的元数据 + 派生的「生效值/来源」两层视图，并且写操作要落 localStorage 覆盖层，
 * 与 parcelStore / attendanceStore 同属「领域 store」。
 */

/** 值类型枚举（设计 A.1.1，顺序即表单渲染与校验分发依据） */
export const VALUE_TYPES = ['SINGLE_SELECT', 'NUMBER', 'TEXT', 'TIME', 'TIME_RANGE']
/** 生效范围：仅 STATION 允许按驿站覆盖 */
export const ITEM_SCOPES = ['GLOBAL', 'STATION']
const ITEM_KEY_PATTERN = /^[a-z][a-z0-9_]{1,39}$/
const OPTION_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_-]{0,39}$/

/** 'HH:mm'（24:00 只允许出现在时段结束）——与既有采集校验口径一致，抽到 store 供路由复用 */
export const isClock = (value) => /^([01]\d|2[0-3]):[0-5]\d$/.test(String(value))
export const isEndClock = (value) => isClock(value) || String(value) === '24:00'
export const minutesOfClock = (text) => {
  const [h, m] = String(text).split(':').map(Number)
  return h * 60 + m
}

const deepClone = (value) => (value === undefined ? undefined : JSON.parse(JSON.stringify(value)))

/**
 * 内建配置项（设计 A.1.2 的 5 项）
 * data_source 的全局默认刻意留空：数据源是驿站本地事实，留空才能让「未启用采集的驿站」保持
 * 「未配置」态（设计 A.4.2「null 保持 null」），也才能保住既有兜底校验「启用采集前须先选择数据源」。
 * 因此 required 的语义是「驿站生效值必填」，不是「全局默认必须有值」——与设计 A.1
 * 「必填项在全局默认无值时，驿站进入未配置判定链」一致。
 */
const ITEM_SEED = [
  {
    itemKey: 'data_source',
    name: '数据源',
    description: '包裹采集的数据来源',
    valueType: 'SINGLE_SELECT',
    required: true,
    defaultValue: null,
    unit: '',
    constraints: null,
    optionSetKey: 'data_source',
    scope: 'STATION',
    sort: 10,
    enabled: true,
    builtin: true
  },
  {
    itemKey: 'collect_frequency',
    name: '采集频率',
    description: '两次采集的最小间隔',
    valueType: 'SINGLE_SELECT',
    required: true,
    defaultValue: 'EVERY_240M',
    unit: '',
    constraints: null,
    optionSetKey: 'collect_frequency',
    scope: 'STATION',
    sort: 20,
    enabled: true,
    builtin: true
  },
  {
    itemKey: 'time_template',
    name: '采集时段模板',
    description: '预置采集时段模板',
    valueType: 'SINGLE_SELECT',
    required: false,
    defaultValue: 'WORKDAY',
    unit: '',
    constraints: null,
    optionSetKey: 'time_template',
    scope: 'STATION',
    sort: 30,
    enabled: true,
    builtin: true
  },
  {
    itemKey: 'retry_times',
    name: '重试次数',
    description: '批次失败后的最大自动重试次数',
    valueType: 'NUMBER',
    required: true,
    defaultValue: 3,
    unit: '次',
    constraints: { min: 0, max: 10, step: 1, integerOnly: true, precision: 0 },
    optionSetKey: null,
    scope: 'STATION',
    sort: 40,
    enabled: true,
    builtin: true
  },
  {
    itemKey: 'timeout_minutes',
    name: '超时时长',
    description: '单个批次超过该时长判定为超时',
    valueType: 'NUMBER',
    required: true,
    defaultValue: 30,
    unit: '分钟',
    constraints: { min: 1, max: 120, step: 1, integerOnly: true, precision: 0 },
    optionSetKey: null,
    scope: 'STATION',
    sort: 50,
    enabled: true,
    builtin: true
  }
]

/**
 * 选项集种子（数据源 / 采集频率 / 采集时段模板）
 * legacyCodes 是迁移的兼容读取依据（设计 A.4.1）：旧码与新码的映射关系放在数据里，
 * 代码侧不硬编码任何映射表，后续增删档位只改数据。
 */
const OPTION_SET_SEED = [
  {
    setKey: 'data_source',
    name: '数据源',
    description: '采集数据来源候选项',
    builtin: true,
    enabled: true,
    options: [
      {
        optionKey: 'DUODUOCAI',
        label: '多多买菜',
        extraAttrs: null,
        sort: 10,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['多多买菜'],
        remark: null
      },
      {
        optionKey: 'CAINIAO',
        label: '菜鸟裹裹',
        extraAttrs: null,
        sort: 20,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['菜鸟裹裹'],
        remark: null
      },
      {
        optionKey: 'JD',
        label: '京东物流',
        extraAttrs: null,
        sort: 30,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['京东物流'],
        remark: null
      },
      {
        optionKey: 'SF',
        label: '顺丰速运',
        extraAttrs: null,
        sort: 40,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: [],
        remark: null
      }
    ]
  },
  {
    setKey: 'collect_frequency',
    name: '采集频率',
    description: '采集间隔档位',
    builtin: true,
    enabled: true,
    options: [
      {
        optionKey: 'EVERY_30M',
        label: '每 30 分钟',
        extraAttrs: { intervalMinutes: 30 },
        sort: 10,
        enabled: true,
        builtin: false,
        source: 'MANUAL',
        legacyCodes: [],
        remark: '由管理员新增的档位示例'
      },
      {
        optionKey: 'EVERY_60M',
        label: '每小时',
        extraAttrs: { intervalMinutes: 60 },
        sort: 20,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['HOURLY'],
        remark: null
      },
      {
        optionKey: 'EVERY_120M',
        label: '每 2 小时',
        extraAttrs: { intervalMinutes: 120 },
        sort: 30,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['EVERY_2H'],
        remark: null
      },
      {
        optionKey: 'EVERY_240M',
        label: '每 4 小时',
        extraAttrs: { intervalMinutes: 240 },
        sort: 40,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['EVERY_4H'],
        remark: null
      },
      {
        optionKey: 'EVERY_1440M',
        label: '每天',
        extraAttrs: { intervalMinutes: 1440 },
        sort: 50,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: ['DAILY'],
        remark: null
      }
    ]
  },
  {
    setKey: 'time_template',
    name: '采集时段模板',
    description: '预置采集时段',
    builtin: true,
    enabled: true,
    options: [
      {
        optionKey: 'WORKDAY',
        label: '营业时段 08:00-20:00',
        extraAttrs: { startTime: '08:00', endTime: '20:00' },
        sort: 10,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: [],
        remark: null
      },
      {
        optionKey: 'ALLDAY',
        label: '全天 08:00-24:00',
        extraAttrs: { startTime: '08:00', endTime: '24:00' },
        sort: 20,
        enabled: true,
        builtin: true,
        source: 'BUILTIN',
        legacyCodes: [],
        remark: null
      }
    ]
  }
]

/** 全局默认值：采集频率取「每 4 小时」使 7 号（EVERY_4H + 空数据源）成为完整继承样本，供覆盖矩阵演示 */
const GLOBAL_SEED = {
  data_source: null,
  collect_frequency: 'EVERY_240M',
  time_template: 'WORKDAY',
  retry_times: 3,
  timeout_minutes: 30
}
/** 额外覆盖样本：数值型覆盖（对齐设计 D.4 的「城西驿站重试 5 次」示例） */
const EXTRA_OVERRIDE_SEED = { 2: { retry_times: 5 } }

const bucket = createPersistBucket('sync_config')

/** 模块加载时的代际基线：用于识别「本次会话尚未访问过配置中心就先触发了重置」的场景 */
const initialGeneration = dbGeneration

let built = false
let building = false
let lastGeneration = null
let items = []
let optionSets = []
let globalValues = {}
let stationOverrides = {}
let optionSeq = 0

/* ==================== 构建 / 迁移 / 持久化 ==================== */

function ensureBuilt() {
  if (building) return // 迁移过程中 resolveDataSourceKey 会回调本函数，用 building 防重入
  if (built && lastGeneration === dbGeneration) return
  building = true
  try {
    // 代际号变化 = 调用方执行了「重置演示数据」：快照作废，按新种子重建（否则残留上一轮改写值）
    const isReset = lastGeneration === null ? dbGeneration !== initialGeneration : lastGeneration !== dbGeneration
    if (isReset) bucket.clear()
    const snapshot = isReset ? null : bucket.read()
    if (snapshot) hydrate(snapshot)
    else {
      seed()
      migrateLegacyValues()
      persist()
    }
    lastGeneration = dbGeneration
    built = true
  } finally {
    building = false
  }
}

function seed() {
  items = deepClone(ITEM_SEED)
  optionSets = deepClone(OPTION_SET_SEED)
  globalValues = { ...GLOBAL_SEED }
  stationOverrides = {}
  optionSeq = 0
}

function hydrate(snapshot) {
  items = snapshot.items || deepClone(ITEM_SEED)
  optionSets = snapshot.optionSets || deepClone(OPTION_SET_SEED)
  globalValues = { ...GLOBAL_SEED, ...(snapshot.globalValues || {}) }
  stationOverrides = snapshot.stationOverrides || {}
  optionSeq = snapshot.optionSeq || 0
  // 种子升级兜底：新增的内建配置项/选项集在老快照里不存在时补齐，避免「改了代码却看不到新项」
  const itemKeys = new Set(items.map((item) => item.itemKey))
  ITEM_SEED.forEach((seedItem) => {
    if (!itemKeys.has(seedItem.itemKey)) items.push(deepClone(seedItem))
  })
  const setKeys = new Set(optionSets.map((set) => set.setKey))
  OPTION_SET_SEED.forEach((seedSet) => {
    if (!setKeys.has(seedSet.setKey)) optionSets.push(deepClone(seedSet))
  })
  items.sort((a, b) => a.sort - b.sort)
}

function persist() {
  bucket.write({ items, optionSets, globalValues, stationOverrides, optionSeq })
}

/** 覆盖层与 db 种子不一致时以覆盖层为准：刷新页面后管理员改过的配置仍然可见 */
function findItemRaw(itemKey) {
  return items.find((item) => item.itemKey === String(itemKey))
}

function findOptionSetRaw(setKey) {
  return optionSets.find((set) => set.setKey === String(setKey))
}

function findOptionRaw(setKey, optionKey) {
  const set = findOptionSetRaw(setKey)
  return set ? set.options.find((option) => option.optionKey === String(optionKey)) : null
}

/** 按选项集约定登记的表意登记选项：optionKey / legacyCodes 二者任一命中即视为同一选项 */
function resolveLegacyOptionKey(setKey, value) {
  const set = findOptionSetRaw(setKey)
  if (!set) return null
  const text = String(value)
  const hit = set.options.find((option) => option.optionKey === text || (option.legacyCodes || []).includes(text))
  return hit ? hit.optionKey : null
}

/**
 * 历史自由文本 → 数据源选项 Key；未命中受管选项时自动纳管为 source=MIGRATED
 * 不置空的原因（设计 A.4.2）：置空会让「已启用采集」的驿站凭空失去数据源，产生矛盾配置。
 */
export function resolveDataSourceKey(text) {
  ensureBuilt()
  const hit = resolveLegacyOptionKey('data_source', text)
  if (hit) return hit
  const set = findOptionSetRaw('data_source')
  optionSeq += 1
  set.options.push({
    optionKey: `MIGRATED_${optionSeq}`,
    label: String(text).trim(),
    extraAttrs: null,
    sort: nextOptionSort(set),
    enabled: true,
    builtin: false,
    source: 'MIGRATED',
    legacyCodes: [String(text).trim()],
    remark: '由历史配置自动创建'
  })
  persist()
  return `MIGRATED_${optionSeq}`
}

function nextOptionSort(set) {
  return set.options.reduce((max, option) => Math.max(max, Number(option.sort) || 0), 0) + 10
}

/**
 * 一次性迁移（设计 A.4 三步走的第一、二步，幂等）：
 * 1) 频率旧码 → 选项集 legacyCodes 登记的等价新码，并写回 db.syncConfigs（等价改写，不改变任何驿站的采集间隔）；
 * 2) 数据源历史文本 → 选项 Key（未命中自动纳管为 MIGRATED）；
 * 3) 与新全局默认一致的项不登记覆盖（视为继承），历史时段 08:00-20:00 恰好等于 WORKDAY 模板值，天然继承（A.4.3）。
 * 第三步的移除兼容层（删除 legacyCodes 与映射）待全量写回并回归通过后执行。
 * TODO(扩展): 全量写回验证完成后删除 legacyCodes 与 resolveLegacyOptionKey，仅保留 optionKey 口径。
 */
function migrateLegacyValues() {
  db.syncConfigs.forEach((config) => {
    const overrides = {}
    const freqKey = resolveLegacyOptionKey('collect_frequency', config.frequency)
    if (freqKey) {
      config.frequency = freqKey
      if (freqKey !== globalValues.collect_frequency) overrides.collect_frequency = freqKey
    }
    if (config.data_source == null || config.data_source === '') {
      config.data_source = null
    } else {
      const dataSourceKey = resolveDataSourceKey(config.data_source)
      config.data_source = dataSourceKey
      overrides.data_source = dataSourceKey
    }
    if (Object.keys(overrides).length) stationOverrides[config.station_id] = overrides
  })
  // 演示用数值型覆盖样本：仅在对应驿站存在时登记
  Object.entries(EXTRA_OVERRIDE_SEED).forEach(([stationId, extra]) => {
    if (!db.syncConfigs.some((config) => config.station_id === Number(stationId))) return
    stationOverrides[Number(stationId)] = { ...(stationOverrides[Number(stationId)] || {}), ...extra }
  })
}

/* ==================== 值校验（设计 C.1 逐类型矩阵） ==================== */

export function validateItemKey(itemKey) {
  return ITEM_KEY_PATTERN.test(String(itemKey || ''))
    ? null
    : `配置项 Key 须以小写字母开头，仅含小写字母、数字与下划线，长度 2-40，当前为「${itemKey}」`
}

/** 校验某配置项的取值，返回人话错误文案（null = 通过）；文案口径对齐设计 C.3 */
export function validateValue(item, value) {
  if (isBlankValue(value)) return item.required ? `请填写「${item.name}」` : null
  switch (item.valueType) {
    case 'SINGLE_SELECT':
      return validateSingleSelect(item, value)
    case 'NUMBER':
      return validateNumber(item, value)
    case 'TEXT':
      return validateText(item, value)
    case 'TIME':
      return validateTime(item, value)
    case 'TIME_RANGE':
      return validateTimeRange(item, value)
    default:
      return null
  }
}

/** 必填置空单独给 9507，其余约束不满足给 9506（前端可据此区分「没填」与「填错」） */
export function firstError(item, value) {
  if (isBlankValue(value) && item.required)
    return { code: SYNC_CONFIG_CODE.REQUIRED_EMPTY, message: `请填写「${item.name}」` }
  const message = validateValue(item, value)
  return message ? { code: SYNC_CONFIG_CODE.VALUE_INVALID, message } : null
}

const isBlankValue = (value) => value == null || (typeof value === 'string' && value.trim() === '')

function validateSingleSelect(item, value) {
  const set = item.optionSetKey ? findOptionSetRaw(item.optionSetKey) : null
  const selectable = set ? set.options.filter((option) => option.enabled) : []
  if (!set || !set.enabled || !selectable.length)
    return `「${item.name}」暂无可选项，请先在「配置项与选项集」中添加候选项`
  const hit = set.options.find((option) => option.optionKey === value)
  if (!hit) return `「${item.name}」选择的选项不存在，请重新选择`
  if (!hit.enabled) return `「${item.name}」选择的「${hit.label}」已停用，请重新选择`
  return null
}

function validateNumber(item, value) {
  const c = item.constraints || {}
  const num = Number(value)
  if (!Number.isFinite(num)) return `「${item.name}」须为数字，当前为 ${value}`
  if (Number.isInteger(c.max) || Number.isInteger(c.min)) {
    const min = c.min == null ? -Infinity : c.min
    const max = c.max == null ? Infinity : c.max
    if (num < min || num > max)
      return `「${item.name}」须在 ${c.min}-${c.max}${item.unit ? ` ${item.unit}` : ''} 之间，当前为 ${value}`
  }
  if (c.integerOnly && !Number.isInteger(num)) return `「${item.name}」须为整数，当前为 ${value}`
  if (c.step && c.step > 1 && Number.isInteger(num) && num % c.step !== 0)
    return `「${item.name}」须为 ${c.step} 的整数倍，当前为 ${value}`
  return null
}

function validateText(item, value) {
  const c = item.constraints || {}
  const text = String(value).trim()
  const min = c.minLen == null ? 0 : Number(c.minLen)
  const max = c.maxLen == null ? Infinity : Number(c.maxLen)
  if (text.length < min || text.length > max)
    return `「${item.name}」长度须为 ${c.minLen}-${c.maxLen} 字符，当前 ${text.length} 字符`
  if (c.pattern) {
    try {
      if (!new RegExp(c.pattern).test(text))
        return `「${item.name}」格式须为 ${c.patternHint || c.pattern}，当前为「${value}」`
    } catch (e) {
      // 非法正则按配置错误跳过：不能因为一条脏约束把整个保存流程挡死
      return null
    }
  }
  return null
}

function validateTime(item, value) {
  if (!isClock(value)) return `「${item.name}」时间格式须为 HH:mm（如 08:00），当前为「${value}」`
  const c = item.constraints || {}
  const minute = minutesOfClock(value)
  if (c.min && minute < minutesOfClock(c.min)) return `「${item.name}」不得早于 ${c.min}，当前为 ${value}`
  if (c.max && minute > minutesOfClock(c.max)) return `「${item.name}」不得晚于 ${c.max}，当前为 ${value}`
  return null
}

function validateTimeRange(item, value) {
  const allowEnd2400 = !item.constraints || item.constraints.allowEnd2400 !== false
  const list = Array.isArray(value) ? value : String(value).split('-')
  const [start, end] = list.map((part) => String(part || '').trim())
  if (!isClock(start) || !isClock(end))
    return `「${item.name}」时间格式须为 HH:mm（如 08:00），当前为「${start} - ${end}」`
  if (start >= end && end !== '24:00') return `「${item.name}」的结束时间须晚于开始时间，当前为 ${start} - ${end}`
  if (end === '24:00' && !allowEnd2400) return `「${item.name}」的结束时间不允许为 24:00`
  return null
}

/* ==================== 出参视图 ==================== */

export function listConfig() {
  ensureBuilt()
  return { items: deepClone(items), optionSets: deepClone(optionSets) }
}

export function findItem(itemKey) {
  ensureBuilt()
  const item = findItemRaw(itemKey)
  return item ? deepClone(item) : null
}

export function findOptionSet(setKey) {
  ensureBuilt()
  const set = findOptionSetRaw(setKey)
  return set ? deepClone(set) : null
}

export function getGlobalValues() {
  ensureBuilt()
  return { ...globalValues }
}

/** 选项显示名（CSV 预览与旧字段 label 回显共用） */
export function optionLabelOf(setKey, optionKey) {
  ensureBuilt()
  const option = findOptionRaw(setKey, optionKey)
  return option ? option.label : optionKey
}

/** 兼容取值 → 选项 Key：optionKey 与 legacyCodes 二者任一命中即可（频率旧码读写的唯一入口） */
export function resolveOptionKey(setKey, value, { enabledOnly = true } = {}) {
  ensureBuilt()
  const set = findOptionSetRaw(setKey)
  if (!set) return null
  const text = String(value)
  const hit = set.options.find(
    (option) =>
      (option.optionKey === text || (option.legacyCodes || []).includes(text)) && (!enabledOnly || option.enabled)
  )
  return hit ? hit.optionKey : null
}

/** 频率新码 → 旧码（能反向映射时返回旧码，保证既有 PC 采集配置抽屉零改动可读；新增档位原样返回） */
export function legacyFrequencyOf(optionKey) {
  ensureBuilt()
  const option = findOptionRaw('collect_frequency', optionKey)
  return option && option.legacyCodes && option.legacyCodes.length ? option.legacyCodes[0] : optionKey
}

/** 时段模板 Key → 起止时间（旧字段 collectStartTime / collectEndTime 的兼容来源） */
export function templateTimeOf(optionKey) {
  ensureBuilt()
  const option = findOptionRaw('time_template', optionKey)
  return option && option.extraAttrs
    ? { startTime: option.extraAttrs.startTime, endTime: option.extraAttrs.endTime }
    : null
}

/** 驿站生效值 + 来源 + 覆盖明细（一次性给全，页面按需渲染，不再逐项请求） */
export function getStationConfig(stationId) {
  ensureBuilt()
  const sid = Number(stationId)
  const overrides = stationOverrides[sid] || {}
  const values = {}
  const sources = {}
  items.forEach((item) => {
    const overridden = Object.prototype.hasOwnProperty.call(overrides, item.itemKey)
    values[item.itemKey] = overridden ? overrides[item.itemKey] : globalValues[item.itemKey]
    sources[item.itemKey] = overridden ? 'OVERRIDE' : 'INHERIT'
  })
  return { values, sources, overrides: { ...overrides } }
}

/* ==================== 写操作 ==================== */

export function saveGlobalValues(values = {}) {
  ensureBuilt()
  const next = { ...globalValues }
  for (const [itemKey, value] of Object.entries(values || {})) {
    const item = findItemRaw(itemKey)
    if (!item) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS, message: `配置项「${itemKey}」不存在` }
    if (!item.enabled) return { code: CODE.BAD_REQUEST, message: `「${item.name}」已停用，不可修改` }
    const error = firstError(item, value)
    if (error) return error
    next[itemKey] = value
  }
  globalValues = next
  persist()
  return { code: 200, data: { ...globalValues } }
}

export function setStationOverrides(stationId, overrides = {}, resetKeys = []) {
  ensureBuilt()
  const sid = Number(stationId)
  const current = { ...(stationOverrides[sid] || {}) }
  ;(resetKeys || []).forEach((itemKey) => delete current[itemKey])
  for (const [itemKey, value] of Object.entries(overrides || {})) {
    const item = findItemRaw(itemKey)
    if (!item) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS, message: `配置项「${itemKey}」不存在` }
    if (item.scope !== 'STATION')
      return { code: CODE.BAD_REQUEST, message: `「${item.name}」为全局配置项，不支持驿站覆盖` }
    const error = firstError(item, value)
    if (error) return error
    current[itemKey] = value
  }
  if (Object.keys(current).length) stationOverrides[sid] = current
  else delete stationOverrides[sid]
  persist()
  return { code: 200, data: getStationConfig(sid) }
}

export function createItem(payload = {}) {
  ensureBuilt()
  const itemKey = String(payload.itemKey || '').trim()
  const keyError = validateItemKey(itemKey)
  if (keyError) return { code: CODE.BAD_REQUEST, message: keyError }
  if (findItemRaw(itemKey)) return { code: SYNC_CONFIG_CODE.ITEM_KEY_EXISTS }
  const result = normalizeItem(itemKey, payload, null)
  if (result.code !== 200) return result
  items.push(result.data)
  items.sort((a, b) => a.sort - b.sort)
  persist()
  return { code: 200, data: deepClone(result.data) }
}

export function updateItem(itemKey, patch = {}) {
  ensureBuilt()
  const base = findItemRaw(itemKey)
  if (!base) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS }
  const result = normalizeItem(base.itemKey, patch, base)
  if (result.code !== 200) return result
  Object.assign(base, result.data)
  items.sort((a, b) => a.sort - b.sort)
  persist()
  return { code: 200, data: deepClone(base) }
}

/**
 * 删除配置项（设计 B.6 / C.4：允许删除，但被引用时须前置影响面确认）
 * confirm=true 时同步清理受影响驿站的覆盖值，使其回退继承全局默认——否则驿站会残留指向已删项的悬空覆盖。
 */
export function removeItem(itemKey, { confirm = false } = {}) {
  ensureBuilt()
  const index = items.findIndex((item) => item.itemKey === String(itemKey))
  if (index < 0) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS }
  const item = items[index]
  if (item.builtin)
    return { code: SYNC_CONFIG_CODE.BUILTIN_NOT_DELETABLE, message: `「${item.name}」为系统内置配置项，只能停用` }
  const referenced = refStationsOf((overrides) => Object.prototype.hasOwnProperty.call(overrides, item.itemKey))
  if (referenced.length && !confirm) {
    return {
      code: SYNC_CONFIG_CODE.ITEM_IN_USE,
      message: `该配置项正被 ${referenced.length} 个驿站覆盖，确认删除请传 confirm=true`
    }
  }
  items.splice(index, 1)
  delete globalValues[item.itemKey]
  stripOverrides((key) => key === item.itemKey)
  persist()
  return { code: 200, data: null }
}

export function createOption(setKey, payload = {}) {
  ensureBuilt()
  const set = findOptionSetRaw(setKey)
  if (!set) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS, message: `选项集「${setKey}」不存在` }
  const optionKey = String(payload.optionKey || '').trim()
  if (!OPTION_KEY_PATTERN.test(optionKey))
    return {
      code: CODE.BAD_REQUEST,
      message: `选项 Key 须以字母开头，仅含字母、数字、下划线或短横线，长度 1-40，当前为「${optionKey}」`
    }
  if (findOptionRaw(set.setKey, optionKey))
    return { code: CODE.BAD_REQUEST, message: `选项 Key「${optionKey}」在选项集「${set.name}」中已存在` }
  const result = normalizeOption(set, optionKey, payload, null)
  if (result.code !== 200) return result
  set.options.push(result.data)
  set.options.sort((a, b) => a.sort - b.sort)
  persist()
  return { code: 200, data: deepClone(result.data) }
}

export function updateOption(setKey, optionKey, patch = {}) {
  ensureBuilt()
  const set = findOptionSetRaw(setKey)
  if (!set) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS, message: `选项集「${setKey}」不存在` }
  const base = set.options.find((option) => option.optionKey === String(optionKey))
  if (!base) return { code: SYNC_CONFIG_CODE.OPTION_NOT_EXISTS }
  const result = normalizeOption(set, base.optionKey, patch, base)
  if (result.code !== 200) return result
  Object.assign(base, result.data)
  set.options.sort((a, b) => a.sort - b.sort)
  persist()
  return { code: 200, data: deepClone(base) }
}

/**
 * 删除选项（设计 B.6 / C.4）
 * - 内置选项恒不可删（9510）；
 * - 全局默认正在引用的选项阻断删除（删掉会让未覆盖驿站落到「未配置」），引导改为停用；
 * - 仅被驿站覆盖引用时允许确认后删除，confirm=true 同步清理覆盖值使其回退继承。
 */
export function removeOption(setKey, optionKey, { confirm = false } = {}) {
  ensureBuilt()
  const set = findOptionSetRaw(setKey)
  if (!set) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS, message: `选项集「${setKey}」不存在` }
  const index = set.options.findIndex((option) => option.optionKey === String(optionKey))
  if (index < 0) return { code: SYNC_CONFIG_CODE.OPTION_NOT_EXISTS }
  const option = set.options[index]
  if (option.builtin)
    return { code: SYNC_CONFIG_CODE.BUILTIN_NOT_DELETABLE, message: `「${option.label}」为系统内置选项，只能停用` }
  const owners = itemsOfSet(set.setKey)
  if (owners.some((item) => globalValues[item.itemKey] === option.optionKey)) {
    const names = owners
      .filter((item) => globalValues[item.itemKey] === option.optionKey)
      .map((item) => item.name)
      .join('、')
    return {
      code: SYNC_CONFIG_CODE.OPTION_IN_USE,
      message: `「${option.label}」是全局默认值，删除后将导致「${names}」缺少全局默认值，建议改为停用`
    }
  }
  const referenced = optionRefStations(owners, option.optionKey)
  if (referenced.length && !confirm) {
    return {
      code: SYNC_CONFIG_CODE.OPTION_IN_USE,
      message: `「${option.label}」正被 ${referenced.length} 个驿站覆盖，确认删除请传 confirm=true`
    }
  }
  set.options.splice(index, 1)
  // 仅清理「值等于被删选项」的覆盖键：同站其它配置项的覆盖不受影响
  stripOverrides((key, value) => value === option.optionKey && owners.some((item) => item.itemKey === key))
  persist()
  return { code: 200, data: null }
}

/* ==================== 删除影响面（设计 B.6：前端据此展示确认文案） ==================== */

/** 配置项影响面：被哪些驿站覆盖、是否可删、阻断原因 */
export function itemImpact(itemKey) {
  ensureBuilt()
  const item = findItemRaw(itemKey)
  if (!item) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS }
  const referencedStations = refStationsOf((overrides) => Object.prototype.hasOwnProperty.call(overrides, item.itemKey))
  const blockers = []
  if (item.builtin)
    blockers.push({
      code: SYNC_CONFIG_CODE.BUILTIN_NOT_DELETABLE,
      message: `「${item.name}」为系统内置配置项，只能停用`
    })
  else if (referencedStations.length)
    blockers.push({
      code: SYNC_CONFIG_CODE.ITEM_IN_USE,
      message: `该配置项正被 ${referencedStations.length} 个驿站覆盖，确认删除请传 confirm=true`
    })
  return {
    code: 200,
    data: {
      itemKey: item.itemKey,
      canDelete: !item.builtin,
      builtin: item.builtin,
      referencedStations,
      referencedCount: referencedStations.length,
      blockers
    }
  }
}

/** 选项影响面：与配置项同构，额外区分「被全局默认引用」这一硬阻断（设计 C.4） */
export function optionImpact(setKey, optionKey) {
  ensureBuilt()
  const set = findOptionSetRaw(setKey)
  if (!set) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS, message: `选项集「${setKey}」不存在` }
  const option = set.options.find((row) => row.optionKey === String(optionKey))
  if (!option) return { code: SYNC_CONFIG_CODE.OPTION_NOT_EXISTS }
  const owners = itemsOfSet(set.setKey)
  const referencedStations = optionRefStations(owners, option.optionKey)
  const inGlobal = owners.some((item) => globalValues[item.itemKey] === option.optionKey)
  const blockers = []
  if (option.builtin)
    blockers.push({
      code: SYNC_CONFIG_CODE.BUILTIN_NOT_DELETABLE,
      message: `「${option.label}」为系统内置选项，只能停用`
    })
  else if (inGlobal)
    blockers.push({
      code: SYNC_CONFIG_CODE.OPTION_IN_USE,
      message: `「${option.label}」是全局默认值，删除后将导致「${owners.map((item) => item.name).join('、')}」缺少全局默认值，建议改为停用`
    })
  else if (referencedStations.length)
    blockers.push({
      code: SYNC_CONFIG_CODE.OPTION_IN_USE,
      message: `「${option.label}」正被 ${referencedStations.length} 个驿站覆盖，确认删除请传 confirm=true`
    })
  return {
    code: 200,
    data: {
      optionKey: option.optionKey,
      canDelete: !option.builtin && !inGlobal,
      builtin: option.builtin,
      referencedStations,
      referencedCount: referencedStations.length,
      blockers
    }
  }
}

/** 引用该选项集的配置项（选项的「归属方」，判断全局默认与驿站覆盖都要用） */
function itemsOfSet(setKey) {
  return items.filter((item) => item.optionSetKey === setKey)
}

/** 覆盖值指向该选项的驿站列表 */
function optionRefStations(owners, optionKey) {
  return refStationsOf((overrides) => owners.some((item) => overrides[item.itemKey] === optionKey))
}

/** 命中条件的驿站（含名称，供确认框展示），按站号稳定排序 */
function refStationsOf(hit) {
  return Object.keys(stationOverrides)
    .filter((sid) => hit(stationOverrides[sid] || {}))
    .map(Number)
    .sort((a, b) => a - b)
    .map((stationId) => ({ stationId, stationName: stationName(stationId) }))
}

/**
 * 清理命中的驿站覆盖键（谓词按「键 + 值」判定）；驿站覆盖被清空后整条删除，避免残留空对象。
 * 删除配置项 / 选项后必须调用，否则驿站会残留指向已删对象的悬空覆盖。
 */
function stripOverrides(match) {
  Object.keys(stationOverrides).forEach((sid) => {
    const overrides = stationOverrides[sid]
    Object.keys(overrides).forEach((key) => {
      if (match(key, overrides[key])) delete overrides[key]
    })
    if (!Object.keys(overrides).length) delete stationOverrides[sid]
  })
}

/* ==================== 组装与规范化 ==================== */

function normalizeItem(itemKey, payload, base) {
  const name = payload.name !== undefined ? String(payload.name).trim() : base ? base.name : ''
  if (!name || name.length > 20) return { code: CODE.BAD_REQUEST, message: '配置项显示名须为 1-20 字符' }
  const valueType = base ? base.valueType : String(payload.valueType || '')
  if (!VALUE_TYPES.includes(valueType))
    return { code: CODE.BAD_REQUEST, message: `值类型仅支持 ${VALUE_TYPES.join(' / ')}` }
  const scope = payload.scope !== undefined ? String(payload.scope) : base ? base.scope : 'STATION'
  if (!ITEM_SCOPES.includes(scope)) return { code: CODE.BAD_REQUEST, message: '生效范围仅支持 GLOBAL / STATION' }
  const sort = payload.sort !== undefined ? Number(payload.sort) : base ? base.sort : items.length * 10 + 10
  if (!Number.isInteger(sort) || sort < 0 || sort > 9999)
    return { code: CODE.BAD_REQUEST, message: '排序须为 0-9999 的整数' }
  const required = payload.required !== undefined ? Boolean(payload.required) : base ? base.required : false
  const enabled = payload.enabled !== undefined ? Boolean(payload.enabled) : base ? base.enabled : true
  const unit = payload.unit !== undefined ? String(payload.unit || '') : base ? base.unit : ''
  if (unit.length > 8) return { code: CODE.BAD_REQUEST, message: '单位长度不超过 8 字符' }
  const description =
    payload.description !== undefined ? String(payload.description || '') : base ? String(base.description || '') : ''
  if (description.length > 100) return { code: CODE.BAD_REQUEST, message: '说明长度不超过 100 字符' }
  const constraints =
    payload.constraints !== undefined
      ? normalizeConstraints(valueType, payload.constraints)
      : base
        ? base.constraints
        : null
  if (constraints && constraints.code) return constraints

  let optionSetKey = payload.optionSetKey !== undefined ? payload.optionSetKey : base ? base.optionSetKey : null
  if (valueType === 'SINGLE_SELECT') {
    // 与内建约定一致：单选型不指定选项集时默认与 itemKey 同名，避免多一个必填参数
    if (!optionSetKey) optionSetKey = itemKey
    if (!findOptionSetRaw(optionSetKey))
      return { code: CODE.BAD_REQUEST, message: `关联的选项集「${optionSetKey}」不存在` }
  } else {
    optionSetKey = null
  }

  const defaultValue = payload.defaultValue !== undefined ? payload.defaultValue : base ? base.defaultValue : null
  const draft = {
    itemKey,
    name,
    valueType,
    required,
    unit,
    constraints,
    optionSetKey,
    scope,
    sort,
    enabled,
    builtin: base ? base.builtin : false
  }
  // 默认值可为空：必填项在全局无默认时走「未配置」判定链（设计 A.1），非空则必须通过本项校验器
  if (!isBlankValue(defaultValue)) {
    const message = validateValue(draft, defaultValue)
    if (message) return { code: SYNC_CONFIG_CODE.VALUE_INVALID, message }
  }
  return {
    code: 200,
    data: { ...draft, defaultValue, description, updateTime: formatDateTime(new Date()) }
  }
}

function normalizeOption(set, optionKey, payload, base) {
  const label = payload.label !== undefined ? String(payload.label).trim() : base ? base.label : ''
  if (!label || label.length > 20) return { code: CODE.BAD_REQUEST, message: '选项显示名须为 1-20 字符' }
  const remark = payload.remark !== undefined ? String(payload.remark || '') : base ? String(base.remark || '') : ''
  if (remark.length > 100) return { code: CODE.BAD_REQUEST, message: '备注长度不超过 100 字符' }
  const sort = payload.sort !== undefined ? Number(payload.sort) : base ? base.sort : nextOptionSort(set)
  if (!Number.isInteger(sort) || sort < 0 || sort > 9999)
    return { code: CODE.BAD_REQUEST, message: '排序须为 0-9999 的整数' }
  const enabled = payload.enabled !== undefined ? Boolean(payload.enabled) : base ? base.enabled : true
  const extra =
    payload.extraAttrs !== undefined
      ? normalizeExtraAttrs(set.setKey, payload.extraAttrs)
      : { value: base ? base.extraAttrs : null }
  if (extra.code) return extra
  return {
    code: 200,
    data: {
      optionKey,
      label,
      extraAttrs: extra.value,
      sort,
      enabled,
      builtin: base ? base.builtin : false,
      source: base ? base.source : 'MANUAL',
      legacyCodes: base ? base.legacyCodes || [] : [],
      remark: remark || null
    }
  }
}

/** 取值约束只保留本值类型认识的键，未知键忽略（向前兼容前端多传字段） */
function normalizeConstraints(valueType, raw) {
  if (raw == null) return null
  const c = typeof raw === 'object' ? raw : {}
  if (valueType === 'NUMBER') {
    const num = (key) => (c[key] === undefined || c[key] === null || c[key] === '' ? null : Number(c[key]))
    const result = {
      min: num('min'),
      max: num('max'),
      step: num('step') || 1,
      integerOnly: Boolean(c.integerOnly),
      precision: c.precision != null ? Number(c.precision) : 0
    }
    if (result.min != null && result.max != null && result.min > result.max)
      return { code: CODE.BAD_REQUEST, message: '数值约束的最小值不得大于最大值' }
    return result
  }
  if (valueType === 'TEXT') {
    const result = {
      minLen: c.minLen != null ? Number(c.minLen) : 0,
      maxLen: c.maxLen != null ? Number(c.maxLen) : null,
      pattern: c.pattern || null,
      patternHint: c.patternHint || null
    }
    if (result.maxLen != null && result.minLen > result.maxLen)
      return { code: CODE.BAD_REQUEST, message: '文本长度约束的最小值不得大于最大值' }
    return result
  }
  if (valueType === 'TIME') return { min: c.min || null, max: c.max || null }
  if (valueType === 'TIME_RANGE') return { allowEnd2400: c.allowEnd2400 !== false }
  return null
}

/** 附加属性结构由所属选项集约定（设计 A.2.1），不认识的键忽略 */
function normalizeExtraAttrs(setKey, raw) {
  if (raw == null) return { value: null }
  const attrs = typeof raw === 'object' ? raw : {}
  if (setKey === 'collect_frequency') {
    const minutes = Number(attrs.intervalMinutes)
    if (!Number.isFinite(minutes) || minutes <= 0)
      return { code: CODE.BAD_REQUEST, message: '采集间隔须为大于 0 的分钟数' }
    return { value: { intervalMinutes: minutes } }
  }
  if (setKey === 'time_template') {
    const startTime = String(attrs.startTime || '')
    const endTime = String(attrs.endTime || '')
    if (!isClock(startTime)) return { code: CODE.BAD_REQUEST, message: '时段模板的开始时间格式须为 HH:mm' }
    if (!isEndClock(endTime)) return { code: CODE.BAD_REQUEST, message: '时段模板的结束时间格式须为 HH:mm' }
    if (minutesOfClock(startTime) >= minutesOfClock(endTime))
      return { code: CODE.BAD_REQUEST, message: '时段模板的结束时间须晚于开始时间' }
    return { value: { startTime, endTime } }
  }
  return { value: null }
}
