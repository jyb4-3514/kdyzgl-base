/**
 * 同步配置中心前端公共口径（设计 A.1.1 / A.2.1 / C.3 / D.1）
 *
 * 为什么单独一份：值类型标签、约束描述、值校验、附加属性字段、确认框文案在
 * 配置项表 / 选项集面板 / 全局默认 / 驿站覆盖 / 导入预览五处都要用，
 * 放在各组件里会形成五套口径（与「同一逻辑不得重复实现」相悖）。
 * 校验规则与 shared/mock/syncConfigStore.js 的 validateValue 逐条对齐——服务端仍是兜底权威（C.2）。
 */

/** 值类型中文标签（B.2.2「值类型」列） */
export const VALUE_TYPE_LABEL = {
  SINGLE_SELECT: '单选项',
  NUMBER: '数值',
  TEXT: '文本',
  TIME: '时间',
  TIME_RANGE: '时间段'
}

/** 生效范围中文标签（B.2.2「生效范围」列） */
export const SCOPE_LABEL = { GLOBAL: '全局', STATION: '可覆盖' }

/** 来源标签（B.5） */
export const SOURCE_LABEL = { INHERIT: '继承', OVERRIDE: '已覆盖' }

/** 值类型下拉选项（配置项表单用，顺序与 store.VALUE_TYPES 一致） */
export const VALUE_TYPE_OPTIONS = Object.entries(VALUE_TYPE_LABEL).map(([value, label]) => ({ value, label }))

/**
 * 时段下拉选项：与既有采集抽屉口径一致（30 分钟一档；收班允许 24:00）。
 * 提取到公共处后，采集抽屉与动态值控件共用同一份，不再各自生成（去重）。
 */
export const CLOCK_OPTIONS = (() => {
  const list = []
  for (let minutes = 0; minutes < 24 * 60; minutes += 30) {
    list.push(`${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`)
  }
  return list
})()
export const CLOCK_END_OPTIONS = [...CLOCK_OPTIONS, '24:00']

/** 附加属性字段约定（A.2.1：结构由所属选项集约定，前端按描述渲染通用表单，不写死业务键） */
export const EXTRA_ATTR_FIELDS = {
  collect_frequency: [
    { key: 'intervalMinutes', label: '间隔', type: 'NUMBER', unit: '分钟', min: 1, max: 10080, step: 1 }
  ],
  time_template: [
    { key: 'startTime', label: '开始时间', type: 'TIME' },
    { key: 'endTime', label: '结束时间', type: 'TIME', allowEnd2400: true }
  ],
  data_source: []
}

/**
 * 导入/导出 CSV 列定义（设计 D.1，16 列）
 * 与 Mock 侧 syncConfigCsv.js 的 EXPORT_HEADER 逐字一致：下载失败明细时要还原可再导入的文件。
 * TODO(扩展): 若后端新增列，此处需与 EXPORT_HEADER 同步，或由接口下发表头
 */
export const SYNC_CONFIG_CSV_HEADER = [
  '记录类型',
  '配置项Key',
  '配置项名称',
  '值类型',
  '是否必填',
  '默认值',
  '单位',
  '取值范围或正则',
  '排序',
  '是否启用',
  '选项Key',
  '选项显示名',
  '附加属性',
  '驿站',
  '配置值',
  '备注'
]

/* ==================== 格式化 ==================== */

const isBlank = (value) => value == null || (typeof value === 'string' && value.trim() === '')

/** 选项集内按 optionKey / legacyCodes 命中（兼容旧码读取，A.4.1） */
export function findOption(optionSet, value) {
  if (!optionSet || isBlank(value)) return null
  const text = String(value)
  return (
    optionSet.options.find((option) => option.optionKey === text || (option.legacyCodes || []).includes(text)) || null
  )
}

/** 选项显示名：命中选项取 label，未命中回原值（不出现裸 Key 时也便于排查） */
export function optionLabel(optionSet, value, fallback = '—') {
  if (isBlank(value)) return fallback
  const hit = findOption(optionSet, value)
  return hit ? hit.label : String(value)
}

/** 值 → 可读文本（表格列 / 覆盖矩阵 / 导入预览共用） */
export function valueTextOf(item, value, optionSet) {
  if (isBlank(value)) return '—'
  switch (item.valueType) {
    case 'SINGLE_SELECT':
      return optionLabel(optionSet, value)
    case 'NUMBER':
      return `${value}${item.unit ? ` ${item.unit}` : ''}`
    case 'TIME_RANGE': {
      const list = Array.isArray(value) ? value : String(value).split('-')
      return `${String(list[0] || '').trim()} – ${String(list[1] || '').trim()}`
    }
    default:
      return String(value)
  }
}

/** 约束/单位列的可读描述（B.2.2） */
export function constraintsText(item) {
  const c = item.constraints || {}
  if (item.valueType === 'NUMBER') {
    const range = c.min == null && c.max == null ? '' : `${c.min ?? ''}-${c.max ?? ''}${c.integerOnly ? ' 的整数' : ''}`
    return [range, item.unit].filter(Boolean).join(' ') || '—'
  }
  if (item.valueType === 'TEXT')
    return c.maxLen == null ? `${c.minLen ?? 0} 字符起` : `${c.minLen ?? 0}-${c.maxLen} 字符`
  if (item.valueType === 'TIME') return c.min || c.max ? `${c.min || '00:00'}–${c.max || '23:59'}` : 'HH:mm'
  if (item.valueType === 'TIME_RANGE') return c.allowEnd2400 === false ? 'HH:mm（不含 24:00）' : 'HH:mm'
  return '—'
}

/** 附加属性 → 可读文本（选项集面板「附加属性」列） */
export function extraAttrsText(setKey, extraAttrs) {
  if (!extraAttrs) return '—'
  const fields = EXTRA_ATTR_FIELDS[setKey] || []
  if (!fields.length)
    return Object.entries(extraAttrs)
      .map(([key, value]) => `${key}=${value}`)
      .join('；')
  return fields
    .filter((field) => !isBlank(extraAttrs[field.key]))
    .map((field) => `${field.label} ${extraAttrs[field.key]}${field.unit || ''}`)
    .join('　')
}

/** 空值构造：不同值类型的初始/清空形态 */
export function emptyValueOf(valueType) {
  if (valueType === 'NUMBER') return null
  if (valueType === 'TIME_RANGE') return ['', '']
  return ''
}

/* ==================== 值校验（C.1 矩阵 / C.3 文案） ==================== */

const isClock = (value) => /^([01]\d|2[0-3]):[0-5]\d$/.test(String(value))
const isEndClock = (value) => isClock(value) || String(value) === '24:00'
const minutesOfClock = (text) => {
  const [h, m] = String(text).split(':').map(Number)
  return h * 60 + m
}

/**
 * 单值校验：返回人话错误文案（'' = 通过）。文案逐条对齐设计 C.3，与 store.validateValue 同口径。
 * optionSet 仅单选项需要（判断选项存在 + 是否启用）。
 */
export function validateValue(item, value, optionSet) {
  if (isBlank(value)) return item.required ? `请填写「${item.name}」` : ''
  if (item.valueType === 'SINGLE_SELECT') {
    const selectable = optionSet ? optionSet.options.filter((option) => option.enabled) : []
    if (!optionSet || !optionSet.enabled || !selectable.length)
      return `「${item.name}」暂无可选项，请先在「配置项与选项集」中添加候选项`
    const hit = optionSet.options.find((option) => option.optionKey === value)
    if (!hit) return `「${item.name}」选择的选项不存在，请重新选择`
    if (!hit.enabled) return `「${item.name}」选择的「${hit.label}」已停用，请重新选择`
    return ''
  }
  if (item.valueType === 'NUMBER') {
    const c = item.constraints || {}
    const num = Number(value)
    if (!Number.isFinite(num)) return `「${item.name}」须为数字，当前为 ${value}`
    const min = c.min == null ? -Infinity : c.min
    const max = c.max == null ? Infinity : c.max
    if (num < min || num > max)
      return `「${item.name}」须在 ${c.min}-${c.max}${item.unit ? ` ${item.unit}` : ''} 之间，当前为 ${value}`
    if (c.integerOnly && !Number.isInteger(num)) return `「${item.name}」须为整数，当前为 ${value}`
    if (c.step && c.step > 1 && Number.isInteger(num) && num % c.step !== 0)
      return `「${item.name}」须为 ${c.step} 的整数倍，当前为 ${value}`
    return ''
  }
  if (item.valueType === 'TEXT') {
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
        return '' // 非法正则按配置错误跳过，不把保存流程挡死（与 store 一致）
      }
    }
    return ''
  }
  if (item.valueType === 'TIME') {
    if (!isClock(value)) return `「${item.name}」时间格式须为 HH:mm（如 08:00），当前为「${value}」`
    const c = item.constraints || {}
    if (c.min && minutesOfClock(value) < minutesOfClock(c.min))
      return `「${item.name}」不得早于 ${c.min}，当前为 ${value}`
    if (c.max && minutesOfClock(value) > minutesOfClock(c.max))
      return `「${item.name}」不得晚于 ${c.max}，当前为 ${value}`
    return ''
  }
  if (item.valueType === 'TIME_RANGE') {
    const allowEnd2400 = !item.constraints || item.constraints.allowEnd2400 !== false
    const list = Array.isArray(value) ? value : String(value).split('-')
    const [start, end] = list.map((part) => String(part || '').trim())
    if (!start || !end) return `请选择「${item.name}」`
    if (!isClock(start) || !isEndClock(end))
      return `「${item.name}」时间格式须为 HH:mm（如 08:00），当前为「${start} - ${end}」`
    if (end !== '24:00' && minutesOfClock(start) >= minutesOfClock(end))
      return `「${item.name}」的结束时间须晚于开始时间，当前为 ${start} - ${end}`
    if (end === '24:00' && !allowEnd2400) return `「${item.name}」的结束时间不允许为 24:00`
    return ''
  }
  return ''
}

/** 配置项 Key 校验（与 store.validateItemKey 同口径，C.3 文案） */
export function validateItemKey(itemKey) {
  return /^[a-z][a-z0-9_]{1,39}$/.test(String(itemKey || ''))
    ? ''
    : `配置项 Key 须以小写字母开头，仅含小写字母、数字与下划线，长度 2-40，当前为「${itemKey}」`
}

/** 选项 Key 校验（与 store.OPTION_KEY_PATTERN 同口径） */
export function validateOptionKey(optionKey) {
  return /^[A-Za-z][A-Za-z0-9_-]{0,39}$/.test(String(optionKey || ''))
    ? ''
    : `选项 Key 须以字母开头，仅含字母、数字、下划线或短横线，长度 1-40，当前为「${optionKey}」`
}

/* ==================== 删除确认文案（B.6） ==================== */

/** 前 3 个驿站名 + 「等 N 个驿站」 */
function stationBrief(stations = []) {
  const names = stations.map((station) => station.stationName)
  const shown = names.slice(0, 3).join('、')
  return names.length > 3 ? `${shown} 等 ${names.length} 个驿站` : shown
}

/** 配置项删除确认（builtin 时调用方应改为阻断提示，不走这里） */
export function itemDeleteConfirm(item, impact) {
  if (impact.referencedCount > 0) {
    return {
      title: '删除配置项',
      message: `有 ${impact.referencedCount} 个驿站正在使用「${item.name}」的覆盖值（${stationBrief(impact.referencedStations)}），删除后这些驿站将恢复为继承全局默认，覆盖值将丢失。`,
      confirmText: '确认删除'
    }
  }
  return {
    title: '删除配置项',
    message: '该配置项没有任何驿站覆盖，删除后将从全局默认与所有驿站配置中移除。此操作不可撤销。',
    confirmText: '确认删除'
  }
}

/**
 * 选项删除确认
 * @param {object} ctx.sameSetItems 引用该选项集的配置项（多引用时追加影响说明）
 * @param {string} ctx.globalLabel 该选项作为全局默认时的显示名（用于正文补充）
 */
export function optionDeleteConfirm(option, impact, ctx = {}) {
  const extra =
    (ctx.sameSetItems || []).length > 1
      ? `　该选项集同时被「${ctx.sameSetItems.map((item) => item.name).join('」「')}」${ctx.sameSetItems.length} 个配置项引用，删除将一并影响。`
      : ''
  if (impact.referencedCount > 0) {
    return {
      title: '删除选项',
      message: `有 ${impact.referencedCount} 个驿站正在使用「${option.label}」，删除后这些站点的该项将回退为继承全局默认。${extra}`,
      confirmText: '确认删除'
    }
  }
  return {
    title: '删除选项',
    message: `该选项没有任何驿站覆盖，删除后将从选项集「${option.label}」所在候选项中移除。此操作不可撤销。${extra}`,
    confirmText: '确认删除'
  }
}
