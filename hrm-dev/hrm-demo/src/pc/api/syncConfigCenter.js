import request from '@admin/utils/request'

/**
 * 同步配置中心接口（配置项 / 选项集 / 全局默认 / 导入导出，仅 ADMIN）
 *
 * 为什么与 syncConfig.js 分开：后者是「驿站采集配置与看板」（站长只读可见），
 * 这里是「配置元数据维护」（ADMIN 独占），与 Mock 侧 routes/syncConfigCenter.js 的拆分理由一致。
 *
 * 默认 silent：这层接口的错误文案规范要求「原样展示」（设计 C.3），由调用组件决定展示位置，
 * 不让拦截器再弹一次通用 toast（否则同一条 95xx 会同时出现在 toast 与字段红字里）。
 */

// GET /sync/config-items 配置项定义 + 选项集（列表一次取全，页面按需渲染，不再逐项请求）
export function getConfigItems(config) {
  return request.get('/sync/config-items', { silent: true, ...config })
}

// POST /sync/config-items 新增配置项（itemKey 新增可填、编辑锁定）
export function createConfigItem(data, config) {
  return request.post('/sync/config-items', data, { silent: true, ...config })
}

// PUT /sync/config-items/{itemKey} 编辑配置项（值类型锁定，改类型会破坏存量值）
export function updateConfigItem(itemKey, data, config) {
  return request.put(`/sync/config-items/${itemKey}`, data, { silent: true, ...config })
}

// DELETE /sync/config-items/{itemKey}?confirm=true 删除（被驿站覆盖时须先查影响面再带 confirm）
export function deleteConfigItem(itemKey, config) {
  return request.delete(`/sync/config-items/${itemKey}`, { silent: true, ...config })
}

// GET /sync/config-items/{itemKey}/impact 删除影响面（受影响驿站 + 是否可删 + 阻断原因）
export function getConfigItemImpact(itemKey, config) {
  return request.get(`/sync/config-items/${itemKey}/impact`, { silent: true, ...config })
}

// POST /sync/config-items/{itemKey}/options 新增选项（选项集由配置项 Key 解析，非单选型会被后端拦下）
export function createConfigOption(itemKey, data, config) {
  return request.post(`/sync/config-items/${itemKey}/options`, data, { silent: true, ...config })
}

// PUT /sync/config-items/{itemKey}/options/{optionKey} 编辑选项（含启用/停用）
export function updateConfigOption(itemKey, optionKey, data, config) {
  return request.put(`/sync/config-items/${itemKey}/options/${optionKey}`, data, { silent: true, ...config })
}

// DELETE /sync/config-items/{itemKey}/options/{optionKey}?confirm=true 删除选项
export function deleteConfigOption(itemKey, optionKey, config) {
  return request.delete(`/sync/config-items/${itemKey}/options/${optionKey}`, { silent: true, ...config })
}

// GET /sync/config-items/{itemKey}/options/{optionKey}/impact 选项删除影响面
export function getConfigOptionImpact(itemKey, optionKey, config) {
  return request.get(`/sync/config-items/${itemKey}/options/${optionKey}/impact`, { silent: true, ...config })
}

// GET /sync/configs/global 全局默认值（{ values: { itemKey: value } }）
export function getGlobalConfig(config) {
  return request.get('/sync/configs/global', { silent: true, ...config })
}

// PUT /sync/configs/global 保存全局默认（局部更新：只提交改动项，避免把种子里的空值重新校验一遍）
export function saveGlobalConfig(values, config) {
  return request.put('/sync/configs/global', { values }, { silent: true, ...config })
}

// GET /sync/configs/export?scope=ITEMS|ITEMS_GLOBAL|ALL|TEMPLATE 导出文件流（BOM + CRLF，文件名走响应头）
export function exportSyncConfig(scope, config) {
  return request.get('/sync/configs/export', { params: { scope }, responseType: 'blob', silent: true, ...config })
}

// POST /sync/configs/import 导入（dryRun=true 只解析预览，false 才落库；两步同一套解析逻辑）
export function importSyncConfig(data, config) {
  return request.post('/sync/configs/import', data, { silent: true, ...config })
}
