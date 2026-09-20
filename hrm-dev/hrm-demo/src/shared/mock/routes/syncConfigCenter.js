import { CODE, SYNC_CONFIG_CODE } from '../../constants/errorCode.js'
import { fail, ok } from '../util.js'
import {
  buildExport,
  buildTemplate,
  buildImportPlan,
  applyImport,
  exportResponse,
  exportScopeInvalid
} from '../syncConfigCsv.js'
import * as store from '../syncConfigStore.js'

/**
 * 同步配置中心路由：配置项与选项集管理、全局默认、CSV 导入导出
 *
 * 为什么与 syncConfig.js 拆开：后者是「驿站采集配置与状态看板」（面向站长可见的运行态），
 * 本文件是「配置元数据维护」（仅 ADMIN），两者的权限、读写对象与生命周期都不同，混在一起会让
 * 「站长只读看板」的越权口径变模糊。
 *
 * 路由注册顺序：GET /sync/configs/global 与 /sync/configs/export 是静态路径，必须排在
 * syncConfig.js 的 /sync/configs/:stationId 之前，否则会被路径参数吞掉（同 workOrder.js 的踩坑说明）。
 */

const ADMIN = ['ADMIN']

/** store 的统一出参（{code,data} / {code,message}）→ HTTP 响应，避免每个 handler 重复判断 */
function respond(result) {
  return result.code === CODE.SUCCESS ? ok(result.data) : fail(result.code, result.message)
}

/** 删除确认标记：查询串传的是字符串 'true'，body 传的是布尔，统一归一（其余一律视为未确认） */
function confirmFlag(value) {
  return value === true || value === 'true' || value === 1 || value === '1'
}

/** 选项集路由以配置项 Key 定位：先把 itemKey 解析成选项集，非单选型直接拦下 */
function resolveOptionSetKey(itemKey) {
  const item = store.findItem(itemKey)
  if (!item) return { code: SYNC_CONFIG_CODE.ITEM_NOT_EXISTS }
  if (item.valueType !== 'SINGLE_SELECT' || !item.optionSetKey)
    return { code: CODE.BAD_REQUEST, message: `「${item.name}」不是单选项配置，没有候选项` }
  return { code: CODE.SUCCESS, setKey: item.optionSetKey }
}

function listConfigItems() {
  return ok(store.listConfig())
}

function createConfigItem({ body }) {
  return respond(store.createItem(body))
}

function updateConfigItem({ body, pathParams }) {
  return respond(store.updateItem(pathParams.itemKey, body))
}

/** 删除影响面：前端先查这里拿到受影响驿站，确认框里展示后再带 confirm 调删除 */
function configItemImpact({ pathParams }) {
  return respond(store.itemImpact(pathParams.itemKey))
}

function removeConfigItem({ body, params, pathParams }) {
  return respond(
    store.removeItem(pathParams.itemKey, {
      confirm: confirmFlag(params.confirm !== undefined ? params.confirm : body.confirm)
    })
  )
}

function createOption({ body, pathParams }) {
  const target = resolveOptionSetKey(pathParams.itemKey)
  if (target.code !== CODE.SUCCESS) return fail(target.code, target.message)
  return respond(store.createOption(target.setKey, body))
}

function updateOption({ body, pathParams }) {
  const target = resolveOptionSetKey(pathParams.itemKey)
  if (target.code !== CODE.SUCCESS) return fail(target.code, target.message)
  return respond(store.updateOption(target.setKey, pathParams.optionKey, body))
}

function optionImpact({ pathParams }) {
  const target = resolveOptionSetKey(pathParams.itemKey)
  if (target.code !== CODE.SUCCESS) return fail(target.code, target.message)
  return respond(store.optionImpact(target.setKey, pathParams.optionKey))
}

function removeOption({ body, params, pathParams }) {
  const target = resolveOptionSetKey(pathParams.itemKey)
  if (target.code !== CODE.SUCCESS) return fail(target.code, target.message)
  return respond(
    store.removeOption(target.setKey, pathParams.optionKey, {
      confirm: confirmFlag(params.confirm !== undefined ? params.confirm : body.confirm)
    })
  )
}

function getGlobalConfig() {
  return ok({ values: store.getGlobalValues() })
}

function saveGlobalConfig({ body }) {
  const result = store.saveGlobalValues(body && body.values)
  return result.code === CODE.SUCCESS ? ok({ values: result.data }) : fail(result.code, result.message)
}

/**
 * 导出：scope=ITEMS 仅定义 / ITEMS_GLOBAL 含全局默认 / ALL 含驿站覆盖 / TEMPLATE 下载导入模板。
 * 四种范围共用同一套列构建与文件流封装，模板不另写表头。
 */
function exportConfig({ params }) {
  const scope = params.scope || 'ITEMS_GLOBAL'
  if (exportScopeInvalid(scope)) return fail(CODE.BAD_REQUEST, 'scope 仅支持 ITEMS / ITEMS_GLOBAL / ALL / TEMPLATE')
  const { data, headers } = exportResponse(scope === 'TEMPLATE' ? buildTemplate() : buildExport(scope), scope)
  return { code: CODE.SUCCESS, message: 'success', data, headers }
}

/**
 * 导入：dryRun=true 只解析校验给预览，false 才落库；两步共用 buildImportPlan，
 * 保证「预览看到的行级结论」与「真正导入的结果」不会因为两套解析逻辑而打架。
 */
function importConfig({ body }) {
  const content = body && body.content
  if (typeof content !== 'string') return fail(SYNC_CONFIG_CODE.IMPORT_PARSE_ERROR, '导入内容须为 CSV 文本')
  const plan = buildImportPlan(content, (body.onConflict || 'OVERWRITE').toUpperCase())
  if (plan.code !== CODE.SUCCESS) return fail(plan.code, plan.message)
  const { rows, summary, plan: planCount, actions } = plan.data
  if (body.dryRun) return ok({ dryRun: true, summary, rows, plan: planCount })
  const applied = applyImport(actions)
  return ok({ dryRun: false, summary, rows, plan: planCount, applied })
}

export const syncConfigCenterRoutes = [
  { method: 'get', path: '/sync/config-items', roles: ADMIN, handler: listConfigItems },
  { method: 'post', path: '/sync/config-items', roles: ADMIN, handler: createConfigItem },
  { method: 'put', path: '/sync/config-items/:itemKey', roles: ADMIN, handler: updateConfigItem },
  { method: 'delete', path: '/sync/config-items/:itemKey', roles: ADMIN, handler: removeConfigItem },
  // 影响面查询：段数与选项路由不同，无路径吞并风险；ADMIN 独占，站长不可见
  { method: 'get', path: '/sync/config-items/:itemKey/impact', roles: ADMIN, handler: configItemImpact },
  { method: 'post', path: '/sync/config-items/:itemKey/options', roles: ADMIN, handler: createOption },
  { method: 'put', path: '/sync/config-items/:itemKey/options/:optionKey', roles: ADMIN, handler: updateOption },
  { method: 'delete', path: '/sync/config-items/:itemKey/options/:optionKey', roles: ADMIN, handler: removeOption },
  { method: 'get', path: '/sync/config-items/:itemKey/options/:optionKey/impact', roles: ADMIN, handler: optionImpact },
  // 静态路径必须早于 syncConfig.js 的 /sync/configs/:stationId 注册
  { method: 'get', path: '/sync/configs/global', roles: ADMIN, handler: getGlobalConfig },
  { method: 'put', path: '/sync/configs/global', roles: ADMIN, handler: saveGlobalConfig },
  { method: 'get', path: '/sync/configs/export', roles: ADMIN, handler: exportConfig },
  { method: 'post', path: '/sync/configs/import', roles: ADMIN, handler: importConfig }
]
