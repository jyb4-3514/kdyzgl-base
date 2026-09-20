import { computed, ref } from 'vue'
import { getConfigItems } from '../../../api/syncConfigCenter.js'
import { findOption, optionLabel } from '../utils/configCenter.js'

/**
 * 同步配置元数据单例（配置项定义 + 选项集）
 *
 * 为什么用模块级单例而不是逐组件请求：采集配置抽屉、采集配置表、配置管理三个子区域
 * 都要读同一份元数据（选项集渲染下拉、旧码映射显示名）。各请求各的会出现
 * 「抽屉里有新数据源、表格里没有」的口径分裂，而且每次开抽屉都重复请求一次。
 * 站长为只读角色，无 ADMIN 权限，不触发本请求（由调用方按 isAdmin 判定）。
 */

const items = ref([])
const optionSets = ref([])
const loading = ref(false)
const error = ref(false)
let loaded = false
let inflight = null

/** 拉取元数据；force=true 用于增删改后的刷新 */
export function loadMeta(force = false) {
  if (loading.value && inflight) return inflight
  if (loaded && !force) return Promise.resolve()
  loading.value = true
  error.value = false
  inflight = getConfigItems()
    .then((data) => {
      items.value = (data && data.items) || []
      optionSets.value = (data && data.optionSets) || []
      loaded = true
    })
    .catch(() => {
      error.value = true
    })
    .finally(() => {
      loading.value = false
      inflight = null
    })
  return inflight
}

/** 重置演示数据后清空，避免残留上一轮改写的定义
 * TODO(扩展): 接入「重置演示数据」事件的统一总线后自动调用（当前仅在代际切换时由 store 自愈）
 */
export function resetMeta() {
  loaded = false
  items.value = []
  optionSets.value = []
  error.value = false
}

export function getOptionSet(setKey) {
  return optionSets.value.find((set) => set.setKey === setKey) || null
}

/** 启用中的配置项，按 sort 升序（列表与动态表单的渲染顺序同源） */
export function enabledItems() {
  return items.value.filter((item) => item.enabled).sort((a, b) => a.sort - b.sort)
}

/** 兼容读取显示名：optionKey 优先，其次 legacyCodes（旧码 HOURLY 等），保证迁移期页面不出「—」 */
export function labelOf(setKey, value, fallback) {
  return optionLabel(getOptionSet(setKey), value, fallback)
}

export function findOptionOf(setKey, value) {
  return findOption(getOptionSet(setKey), value)
}

/** 可用选项（默认只返回启用项，按 sort 升序） */
export function optionsOf(setKey, { enabledOnly = true } = {}) {
  const set = getOptionSet(setKey)
  if (!set) return []
  return [...set.options].filter((option) => !enabledOnly || option.enabled).sort((a, b) => a.sort - b.sort)
}

export function useSyncConfigMeta() {
  return {
    items: computed(() => items.value),
    optionSets: computed(() => optionSets.value),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    loaded: computed(() => loaded),
    loadMeta,
    resetMeta,
    getOptionSet,
    enabledItems,
    labelOf,
    findOptionOf,
    optionsOf
  }
}
