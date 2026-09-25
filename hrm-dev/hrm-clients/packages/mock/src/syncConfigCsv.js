import { IMPORT_CODE, SYNC_CONFIG_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { db } from './db.js'
import { CSV_TYPE, csvDisposition, formatDate, parseCsv, toCsvBlob } from './util.js'
import * as store from './syncConfigStore.js'

/**
 * 同步配置 CSV 导入导出（设计 D 章）
 *
 * 一张表用「记录类型」列表达 ITEM / OPTION / GLOBAL / STATION 四类记录，列定义固定 16 列。
 * 导出即导入的闭环：导出「全部」得到的文件改完再导入，等价于一次批量配置更新。
 * 本模块只做「列映射 + 行级校验 + 落库编排」，配置项定义与值校验规则统一复用 syncConfigStore，
 * 避免同一套校验在导入侧再写一遍（服务端兜底重跑一遍是刻意的，但不能是两份实现）。
 */

export const EXPORT_HEADER = [
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

const RECORD_TYPES = ['ITEM', 'OPTION', 'GLOBAL', 'STATION']
// TEMPLATE 是「下载导入模板」专用范围，只出表头 + 每类记录各一行示例
const EXPORT_SCOPES = ['ITEMS', 'ITEMS_GLOBAL', 'ALL', 'TEMPLATE']
const OPTION_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_-]{0,39}$/
const CONFLICT_STRATEGIES = ['OVERWRITE', 'SKIP', 'APPEND']

/* ==================== 导出 ==================== */

export function buildExport(scope = 'ITEMS_GLOBAL') {
  const { items, optionSets } = store.listConfig()
  const rows = [EXPORT_HEADER]
  items.forEach((item) => rows.push(itemRow(item)))
  optionSets.forEach((set) => set.options.forEach((option) => rows.push(optionRow(set, option))))
  if (scope !== 'ITEMS') {
    const global = store.getGlobalValues()
    // 全局默认值为空的项不导出：导出空值行再导回会触发必填校验，破坏「导出即导入」闭环
    items.forEach((item) => {
      if (!isBlank(global[item.itemKey])) rows.push(globalRow(item, global[item.itemKey]))
    })
  }
  if (scope === 'ALL') {
    db.stations.forEach((station) => {
      const { overrides } = store.getStationConfig(station.id)
      items.forEach((item) => {
        if (Object.prototype.hasOwnProperty.call(overrides, item.itemKey))
          rows.push(stationRow(item, station, overrides[item.itemKey]))
      })
    })
  }
  return rows
}

/**
 * 下载导入模板：表头 + ITEM / OPTION / GLOBAL / STATION 各一行示例（设计 B.7.3）。
 * 复用与正式导出完全相同的行构建函数，模板列序不会与导入解析器脱节；示例备注统一标「示例行，可删除」，
 * 避免用户把示例当真实数据导入。示例值取真实可用的形态，用户照着改即可。
 */
export function buildTemplate() {
  const demoItem = {
    itemKey: 'demo_sample',
    name: '示例配置项',
    valueType: 'SINGLE_SELECT',
    required: false,
    defaultValue: 'DEMO_OPT',
    unit: '',
    constraints: null,
    sort: 9999,
    enabled: true,
    description: '示例行，可删除'
  }
  const demoOption = {
    optionKey: 'DEMO_OPT',
    label: '示例选项',
    extraAttrs: null,
    enabled: true,
    remark: '示例行，可删除'
  }
  const demoStation = { station_name: db.stations.length ? db.stations[0].station_name : '城东驿站' }
  return [
    EXPORT_HEADER,
    itemRow(demoItem),
    optionRow({ setKey: demoItem.itemKey }, demoOption),
    globalRow(demoItem, demoItem.defaultValue),
    stationRow(demoItem, demoStation, demoItem.defaultValue)
  ]
}

export function exportScopeInvalid(scope) {
  return scope !== undefined && scope !== '' && !EXPORT_SCOPES.includes(scope)
}

export function exportFilename(scope) {
  if (scope === 'TEMPLATE') return '同步配置_导入模板.csv' // 模板文件名不带日期，便于反复下载固定名
  const day = formatDate(new Date()).replace(/-/g, '')
  const label = scope === 'ITEMS' ? '配置项' : scope === 'ALL' ? '全部' : '含全局默认'
  return `同步配置_${label}_${day}.csv`
}

export function exportResponse(rows, scope) {
  return {
    data: toCsvBlob(rows),
    headers: { 'content-type': CSV_TYPE, 'content-disposition': csvDisposition(exportFilename(scope)) }
  }
}

function itemRow(item) {
  return [
    'ITEM',
    item.itemKey,
    item.name,
    item.valueType,
    item.required ? '是' : '否',
    valueText(item, item.defaultValue),
    item.unit || '',
    constraintsText(item),
    String(item.sort),
    item.enabled ? '是' : '否',
    '',
    '',
    '',
    '',
    '',
    item.description || ''
  ]
}

function optionRow(set, option) {
  return [
    'OPTION',
    set.setKey,
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    option.enabled ? '是' : '否',
    option.optionKey,
    option.label,
    extraAttrsText(option.extraAttrs),
    '',
    '',
    option.remark || ''
  ]
}

function globalRow(item, value) {
  return ['GLOBAL', item.itemKey, '', '', '', '', '', '', '', '', '', '', '', '', valueText(item, value), '']
}

function stationRow(item, station, value) {
  return [
    'STATION',
    item.itemKey,
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    '',
    station.station_name,
    valueText(item, value),
    ''
  ]
}

/* ==================== 导入解析 ==================== */

/**
 * 解析 + 逐行校验，返回预览与待执行动作；dryRun 与非 dryRun 共用本函数（两步一套逻辑）。
 * 行级失败只标记该行，不让整体崩（与 employee.js 导入的 5003 模式同源）。
 */
export function buildImportPlan(content, onConflict) {
  if (!CONFLICT_STRATEGIES.includes(onConflict)) {
    return { code: SYNC_CONFIG_CODE.IMPORT_CONFLICT_INVALID, message: '冲突策略仅支持 OVERWRITE / SKIP / APPEND' }
  }
  const rows = parseCsv(content)
  if (!rows.length || rows.every((row) => row.every((cell) => String(cell).trim() === ''))) {
    return { code: SYNC_CONFIG_CODE.IMPORT_PARSE_ERROR, message: '导入内容为空，请检查文件' }
  }
  const header = rows[0].map((cell) => String(cell).trim())
  if (header.length !== EXPORT_HEADER.length || header.some((cell, index) => cell !== EXPORT_HEADER[index])) {
    return {
      code: SYNC_CONFIG_CODE.IMPORT_PARSE_ERROR,
      message: `表头与模板不一致，第 1 行应为 ${EXPORT_HEADER.length} 列：${EXPORT_HEADER.join(',')}`
    }
  }
  const dataRows = rows.slice(1)
  if (dataRows.length > 1000) return { code: IMPORT_CODE.ROW_LIMIT }

  const seen = new Map()
  // 同一文件内先声明的 ITEM 行可能被后面的 GLOBAL/STATION 行引用，先按行预生成草稿配置项，
  // 否则「一个文件即一份完整配置快照」在新增配置项时会因为「解析时配置项还不存在」而整批失败
  const pendingItems = new Map()
  dataRows.forEach((fields) => {
    // 只认严格大写的 ITEM：小写「item」本身要在校验阶段判失败，不能在此被当作合法草稿供后行引用
    if (String(fields[0] || '').trim() !== 'ITEM') return
    const itemKey = String(fields[1] || '').trim()
    if (itemKey) pendingItems.set(itemKey, fields)
  })
  const preview = []
  const actions = []
  dataRows.forEach((fields, index) => {
    const result = validateRow({ fields, rowNo: index + 2, seen, onConflict, pendingItems })
    preview.push(result.row)
    if (result.action) actions.push(result.action)
  })

  const failed = preview.filter((row) => row.level === 'FAILED').length
  const warning = preview.filter((row) => row.level === 'WARNING').length
  return {
    code: 200,
    data: {
      rows: preview,
      summary: { total: dataRows.length, ok: dataRows.length - failed, failed, warning },
      plan: {
        create: actions.filter((action) => action.kind === 'CREATE').length,
        update: actions.filter((action) => action.kind === 'UPDATE').length,
        skip: actions.filter((action) => action.kind === 'SKIP').length,
        conflict: preview.filter((row) => row.level === 'FAILED' && row.message.includes('不允许覆盖')).length
      },
      actions
    }
  }
}

/** 真正落库：按计划顺序应用，返回新增 / 更新 / 跳过计数 */
export function applyImport(actions = []) {
  const applied = { created: 0, updated: 0, skipped: 0 }
  actions.forEach((action) => {
    if (action.kind === 'SKIP') {
      applied.skipped += 1
      return
    }
    let result = null
    if (action.type === 'ITEM')
      result =
        action.kind === 'UPDATE' ? store.updateItem(action.itemKey, action.payload) : store.createItem(action.payload)
    else if (action.type === 'OPTION')
      result =
        action.kind === 'UPDATE'
          ? store.updateOption(action.setKey, action.optionKey, action.payload)
          : store.createOption(action.setKey, { optionKey: action.optionKey, ...action.payload })
    else if (action.type === 'GLOBAL') result = store.saveGlobalValues({ [action.itemKey]: action.value })
    else if (action.type === 'STATION')
      result = store.setStationOverrides(action.stationId, { [action.itemKey]: action.value })
    if (result && result.code === 200) {
      if (action.kind === 'UPDATE') applied.updated += 1
      else applied.created += 1
    }
  })
  return applied
}

/* ==================== 逐行校验 ==================== */

function validateRow({ fields, rowNo, seen, onConflict, pendingItems }) {
  if (fields.length !== EXPORT_HEADER.length) {
    return failed(rowNo, '', `第 ${rowNo} 行：列数不正确，应为 ${EXPORT_HEADER.length} 列，当前 ${fields.length} 列`)
  }
  const type = String(fields[0] || '').trim()
  // 大小写敏感（设计 D.5 第 5 点）：小写不归一化，直接行级失败并指出正确写法
  if (!RECORD_TYPES.includes(type)) {
    return failed(
      rowNo,
      '',
      `第 ${rowNo} 行：记录类型须为大写枚举 ${RECORD_TYPES.join(' / ')} 之一，当前为「${type}」${upperHint(RECORD_TYPES, type)}`
    )
  }
  const uniqueKey = judgmentKey(type, fields)
  if (uniqueKey && seen.has(uniqueKey)) {
    return failed(
      rowNo,
      type,
      `第 ${rowNo} 行：${uniqueKey} 在本文件中重复出现（第 ${seen.get(uniqueKey)} 行已定义）`,
      fields
    )
  }
  if (uniqueKey) seen.set(uniqueKey, rowNo)
  if (type === 'ITEM') return validateItemRow(fields, rowNo, onConflict)
  if (type === 'OPTION') return validateOptionRow(fields, rowNo, onConflict)
  if (type === 'GLOBAL') return validateGlobalRow(fields, rowNo, onConflict, pendingItems)
  return validateStationRow(fields, rowNo, onConflict, pendingItems)
}

/** GLOBAL/STATION 行的配置项解析：优先取已存在的，其次取本文件 ITEM 行预生成的草稿 */
function lookupItem(itemKey, pendingItems) {
  const existed = store.findItem(itemKey)
  if (existed) return existed
  const fields = pendingItems.get(itemKey)
  return fields ? draftItemOf(fields) : null
}

function draftItemOf(fields) {
  const valueType = String(fields[3] || '').trim()
  if (!store.VALUE_TYPES.includes(valueType)) return null
  const constraints = parseConstraintsText(valueType, String(fields[7] || '').trim())
  if (constraints.error) return null
  const itemKey = String(fields[1] || '').trim()
  return {
    itemKey,
    name: String(fields[2] || '').trim(),
    valueType,
    required: cnBool(fields[4]) === true,
    unit: String(fields[6] || '').trim(),
    constraints: constraints.value,
    optionSetKey: valueType === 'SINGLE_SELECT' ? itemKey : null,
    scope: 'STATION'
  }
}

function judgmentKey(type, fields) {
  if (type === 'ITEM') return `配置项 Key「${String(fields[1] || '').trim()}」`
  if (type === 'OPTION') return `选项 Key「${String(fields[10] || '').trim()}」`
  if (type === 'GLOBAL') return `配置项「${String(fields[1] || '').trim()}」的全局默认`
  return `驿站「${String(fields[13] || '').trim()}」的配置项「${String(fields[1] || '').trim()}」`
}

function validateItemRow(fields, rowNo, onConflict) {
  const itemKey = String(fields[1] || '').trim()
  const name = String(fields[2] || '').trim()
  const valueType = String(fields[3] || '').trim()
  const required = cnBool(fields[4])
  const defaultValue = String(fields[5] || '').trim()
  const unit = String(fields[6] || '').trim()
  const constraintText = String(fields[7] || '').trim()
  const sortText = String(fields[8] || '').trim()
  const enabled = cnBool(fields[9])
  const remark = String(fields[15] || '').trim()

  const keyError = store.validateItemKey(itemKey)
  if (keyError) return failed(rowNo, 'ITEM', `第 ${rowNo} 行：${keyError}`, fields)
  // 值类型同样大小写敏感（设计 D.5 第 5 点）：小写不归一化，行级失败并给出正确写法
  if (!store.VALUE_TYPES.includes(valueType)) {
    return failed(
      rowNo,
      'ITEM',
      `第 ${rowNo} 行：值类型「${valueType}」不合法，仅支持 ${store.VALUE_TYPES.join(' / ')}${upperHint(store.VALUE_TYPES, valueType)}`,
      fields
    )
  }
  if (!name || name.length > 20) return failed(rowNo, 'ITEM', `第 ${rowNo} 行：配置项名称须为 1-20 字符`, fields)
  if (required === null) return failed(rowNo, 'ITEM', `第 ${rowNo} 行：是否必填仅支持「是」「否」`, fields)
  if (enabled === null) return failed(rowNo, 'ITEM', `第 ${rowNo} 行：是否启用仅支持「是」「否」`, fields)
  const existing = store.findItem(itemKey)
  if (existing && existing.valueType !== valueType)
    return failed(
      rowNo,
      'ITEM',
      `第 ${rowNo} 行：配置项「${itemKey}」值类型为 ${existing.valueType}，不允许改为 ${valueType}`,
      fields
    )
  const sort = sortText === '' ? (existing ? existing.sort : undefined) : Number(sortText)
  if (sort !== undefined && (!Number.isInteger(sort) || sort < 0 || sort > 9999))
    return failed(rowNo, 'ITEM', `第 ${rowNo} 行：排序须为 0-9999 的整数`, fields)

  const constraints = parseConstraintsText(valueType, constraintText)
  if (constraints.error) return failed(rowNo, 'ITEM', `第 ${rowNo} 行：${constraints.error}`, fields)
  const optionSetKey = valueType === 'SINGLE_SELECT' ? (existing ? existing.optionSetKey : itemKey) : null
  if (valueType === 'SINGLE_SELECT' && !store.findOptionSet(optionSetKey)) {
    return failed(
      rowNo,
      'ITEM',
      `第 ${rowNo} 行：关联的选项集「${optionSetKey}」不存在，单选项须复用已有选项集`,
      fields
    )
  }
  const value = defaultValue === '' ? (existing ? existing.defaultValue : null) : coerceValue(valueType, defaultValue)
  const draft = {
    itemKey,
    name,
    valueType,
    required: required === true,
    unit,
    constraints: constraints.value,
    optionSetKey,
    scope: existing ? existing.scope : 'STATION'
  }
  if (!isBlank(value)) {
    const message = store.validateValue(draft, value)
    if (message) return failed(rowNo, 'ITEM', `第 ${rowNo} 行：${message}`, fields)
  }
  const payload = {
    name,
    required: required === true,
    defaultValue: value,
    unit,
    constraints: constraints.value,
    enabled: enabled !== false,
    description: remark
  }
  if (sort !== undefined) payload.sort = sort
  if (!existing) {
    Object.assign(payload, { itemKey, valueType, optionSetKey, scope: 'STATION' })
  }
  const conflict = resolveConflict(Boolean(existing), onConflict)
  if (conflict.error)
    return failed(rowNo, 'ITEM', `第 ${rowNo} 行：配置项 Key「${itemKey}」已存在，追加策略不允许覆盖`, fields)
  const skip = conflict.kind === 'SKIP'
  return {
    row: rowLine(rowNo, 'ITEM', { itemKey, level: 'OK', message: skip ? '已跳过（冲突策略：跳过）' : '通过' }),
    action: skip ? { type: 'ITEM', kind: 'SKIP', itemKey } : { type: 'ITEM', kind: conflict.kind, itemKey, payload }
  }
}

function validateOptionRow(fields, rowNo, onConflict) {
  const setKey = String(fields[1] || '').trim()
  const enabled = cnBool(fields[9])
  const optionKey = String(fields[10] || '').trim()
  const label = String(fields[11] || '').trim()
  const extraText = String(fields[12] || '').trim()
  const remark = String(fields[15] || '').trim()

  const set = store.findOptionSet(setKey)
  if (!set) return failed(rowNo, 'OPTION', `第 ${rowNo} 行：关联的选项集「${setKey}」不存在`, fields)
  if (!OPTION_KEY_PATTERN.test(optionKey))
    return failed(
      rowNo,
      'OPTION',
      `第 ${rowNo} 行：选项 Key 须以字母开头，仅含字母、数字、下划线或短横线，长度 1-40`,
      fields
    )
  if (!label || label.length > 20) return failed(rowNo, 'OPTION', `第 ${rowNo} 行：选项显示名须为 1-20 字符`, fields)
  if (enabled === null) return failed(rowNo, 'OPTION', `第 ${rowNo} 行：是否启用仅支持「是」「否」`, fields)
  const extra = parseExtraAttrs(setKey, extraText)
  if (extra.error) return failed(rowNo, 'OPTION', `第 ${rowNo} 行：${extra.error}`, fields)

  const existing = set.options.find((option) => option.optionKey === optionKey)
  const conflict = resolveConflict(Boolean(existing), onConflict)
  if (conflict.error)
    return failed(rowNo, 'OPTION', `第 ${rowNo} 行：选项 Key「${optionKey}」已存在，追加策略不允许覆盖`, fields)
  const message =
    conflict.kind === 'SKIP' ? '已跳过（冲突策略：跳过）' : extra.warnings.length ? extra.warnings.join('；') : '通过'
  return {
    row: rowLine(rowNo, 'OPTION', {
      itemKey: setKey,
      optionKey,
      level: extra.warnings.length && conflict.kind !== 'SKIP' ? 'WARNING' : 'OK',
      message
    }),
    action:
      conflict.kind === 'SKIP'
        ? { type: 'OPTION', kind: 'SKIP', setKey, optionKey }
        : {
            type: 'OPTION',
            kind: conflict.kind,
            setKey,
            optionKey,
            payload: { label, extraAttrs: extra.value, enabled: enabled !== false, remark: remark || null }
          }
  }
}

function validateGlobalRow(fields, rowNo, onConflict, pendingItems) {
  const itemKey = String(fields[1] || '').trim()
  const item = lookupItem(itemKey, pendingItems)
  if (!item) return failed(rowNo, 'GLOBAL', `第 ${rowNo} 行：配置项「${itemKey}」不存在`, fields)
  const value = coerceValue(item.valueType, String(fields[14] || '').trim())
  const error = store.firstError(item, value)
  if (error) return failed(rowNo, 'GLOBAL', `第 ${rowNo} 行：${error.message}`, fields)
  const exists = !isBlank(store.getGlobalValues()[itemKey])
  const conflict = resolveConflict(exists, onConflict)
  if (conflict.error)
    return failed(rowNo, 'GLOBAL', `第 ${rowNo} 行：配置项「${itemKey}」的全局默认已存在，追加策略不允许覆盖`, fields)
  const message = conflict.kind === 'SKIP' ? '已跳过（冲突策略：跳过）' : '通过'
  return {
    row: rowLine(rowNo, 'GLOBAL', { itemKey, level: 'OK', message }),
    action:
      conflict.kind === 'SKIP'
        ? { type: 'GLOBAL', kind: 'SKIP', itemKey }
        : { type: 'GLOBAL', kind: conflict.kind, itemKey, value }
  }
}

function validateStationRow(fields, rowNo, onConflict, pendingItems) {
  const itemKey = String(fields[1] || '').trim()
  const stationText = String(fields[13] || '').trim()
  const item = lookupItem(itemKey, pendingItems)
  if (!item) return failed(rowNo, 'STATION', `第 ${rowNo} 行：配置项「${itemKey}」不存在`, fields)
  if (item.scope !== 'STATION')
    return failed(rowNo, 'STATION', `第 ${rowNo} 行：「${item.name}」为全局配置项，不支持驿站覆盖`, fields)
  const station = db.stations.find((row) => row.station_name === stationText)
  if (!station) return failed(rowNo, 'STATION', `第 ${rowNo} 行：驿站「${stationText}」不存在，请核对名称`, fields)
  const value = coerceValue(item.valueType, String(fields[14] || '').trim())
  const error = store.firstError(item, value)
  if (error) return failed(rowNo, 'STATION', `第 ${rowNo} 行：${error.message}`, fields)
  const exists = Object.prototype.hasOwnProperty.call(store.getStationConfig(station.id).overrides, itemKey)
  const conflict = resolveConflict(exists, onConflict)
  if (conflict.error)
    return failed(
      rowNo,
      'STATION',
      `第 ${rowNo} 行：驿站「${stationText}」的「${item.name}」已覆盖，追加策略不允许覆盖`,
      fields
    )
  const message = conflict.kind === 'SKIP' ? '已跳过（冲突策略：跳过）' : '通过'
  return {
    row: rowLine(rowNo, 'STATION', { itemKey, stationName: stationText, level: 'OK', message }),
    action:
      conflict.kind === 'SKIP'
        ? { type: 'STATION', kind: 'SKIP', stationId: station.id, itemKey }
        : { type: 'STATION', kind: conflict.kind, stationId: station.id, itemKey, value }
  }
}

/* ==================== 工具 ==================== */

function resolveConflict(exists, onConflict) {
  if (!exists) return { kind: 'CREATE' }
  if (onConflict === 'OVERWRITE') return { kind: 'UPDATE' }
  if (onConflict === 'APPEND') return { error: true }
  return { kind: 'SKIP' }
}

function rowLine(rowNo, type, extra) {
  return {
    rowNo,
    type,
    itemKey: extra.itemKey || '',
    optionKey: extra.optionKey || '',
    stationName: extra.stationName || '',
    level: extra.level,
    message: extra.message
  }
}

/**
 * 失败行也要回填该行解析出的原始值（哪怕非法）：
 * 明细里「驿站」「配置项Key」「选项Key」列必须与原因文案里说的是同一个值，否则用户无从定位。
 */
function failed(rowNo, type, message, fields = []) {
  return {
    row: rowLine(rowNo, type, {
      itemKey: String(fields[1] || '').trim(),
      optionKey: String(fields[10] || '').trim(),
      stationName: String(fields[13] || '').trim(),
      level: 'FAILED',
      message
    }),
    action: null
  }
}

/** 大小写不匹配的纠正提示（设计 D.5 第 5 点）：仅当大写形式合法时才给建议，避免把非法值引向同样非法的建议 */
function upperHint(list, value) {
  const upper = String(value || '').toUpperCase()
  return list.includes(upper) && upper !== value ? `，请改为「${upper}」` : ''
}

function cnBool(text) {
  const value = String(text == null ? '' : text).trim()
  if (value === '是') return true
  if (value === '否') return false
  if (value === '') return undefined
  return null
}

function coerceValue(valueType, text) {
  if (text === '') return null
  if (valueType === 'NUMBER') {
    const num = Number(text)
    return Number.isFinite(num) ? num : text
  }
  return text
}

function valueText(item, value) {
  if (isBlank(value)) return ''
  return String(value)
}

function isBlank(value) {
  return value == null || String(value).trim() === ''
}

function extraAttrsText(extraAttrs) {
  if (!extraAttrs) return ''
  return Object.entries(extraAttrs)
    .map(([key, value]) => `${key}=${value}`)
    .join(';')
}

function constraintsText(item) {
  const c = item.constraints
  if (!c) return ''
  if (item.valueType === 'NUMBER') return c.integerOnly ? `${c.min}-${c.max} 的整数` : `${c.min}-${c.max}`
  if (item.valueType === 'TEXT')
    return c.maxLen == null || c.minLen === c.maxLen ? `${c.minLen} 字符` : `${c.minLen}-${c.maxLen} 字符`
  return ''
}

/** 面向人的约束描述 → constraints（设计 D.1 注 1；正则本期不支持 CSV 表达，登记为已知限制） */
function parseConstraintsText(valueType, text) {
  if (!text) return { value: null }
  if (valueType === 'NUMBER') {
    const hit = /^(\d+)\s*-\s*(\d+)(\s*的整数)?$/.exec(text)
    if (!hit) return { error: `取值范围「${text}」无法解析，须写成「0-10」或「0-10 的整数」` }
    return { value: { min: Number(hit[1]), max: Number(hit[2]), step: 1, integerOnly: Boolean(hit[3]), precision: 0 } }
  }
  if (valueType === 'TEXT') {
    const hit = /^(\d+)(?:\s*-\s*(\d+))?\s*字符$/.exec(text)
    if (!hit) return { error: `长度约束「${text}」无法解析，须写成「20 字符」或「1-50 字符」` }
    return {
      value: {
        minLen: Number(hit[1]),
        maxLen: hit[2] ? Number(hit[2]) : Number(hit[1]),
        pattern: null,
        patternHint: null
      }
    }
  }
  return { value: null }
}

function parseExtraAttrs(setKey, text) {
  if (!text) return { value: null, warnings: [] }
  const known =
    { collect_frequency: ['intervalMinutes'], time_template: ['startTime', 'endTime'], data_source: [] }[setKey] || []
  const attrs = {}
  const warnings = []
  text.split(';').forEach((pair) => {
    const [key, ...rest] = pair.split('=')
    const name = String(key || '').trim()
    if (!name) return
    const value = rest.join('=').trim()
    if (!known.includes(name)) {
      warnings.push(`附加属性「${name}」不属于选项集「${setKey}」，已忽略`)
      return
    }
    attrs[name] = setKey === 'collect_frequency' ? Number(value) : value
  })
  if (setKey === 'collect_frequency') {
    if (!Number.isFinite(attrs.intervalMinutes) || attrs.intervalMinutes <= 0)
      return { error: '附加属性须包含 intervalMinutes（分钟数，大于 0）', warnings }
    return { value: { intervalMinutes: attrs.intervalMinutes }, warnings }
  }
  if (setKey === 'time_template') {
    if (!store.isClock(attrs.startTime) || !store.isEndClock(attrs.endTime))
      return { error: '附加属性须为 startTime=HH:mm;endTime=HH:mm', warnings }
    if (store.minutesOfClock(attrs.startTime) >= store.minutesOfClock(attrs.endTime))
      return { error: '时段模板的结束时间须晚于开始时间', warnings }
    return { value: { startTime: attrs.startTime, endTime: attrs.endTime }, warnings }
  }
  return { value: null, warnings }
}
