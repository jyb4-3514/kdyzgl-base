import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useSyncConfigMeta } from './useSyncConfigMeta.js'
import { useSyncBatchList } from './useSyncBatchList.js'
import { useSyncBatchLogs } from './useSyncBatchLogs.js'
import { useSyncCollect } from './useSyncCollect.js'
import { resolveInitialTab } from '../model/syncMeta.js'

/**
 * 同步任务页编排：把批次流水 / 采集配置 / 批次日志三块状态串成一份页面级状态
 *
 * 为什么用 reactive 聚合而不是让页面壳逐个解构：三块展开近 30 个绑定，逐个解构会把壳撑爆，
 * 壳就失去了「只看装配」的意义（reactive 会自动脱 ref，模板里直接 page.xxx，v-model 也能写入）。
 * 三个子 composable 仍保持 plain object 返回，便于单测单独调用。
 *
 * 页内三视图而不新开路由：`sync` 键已在菜单白名单，新路由要动 shared 冻结层；
 * 且「批次流水」与「采集配置」本就是同一业务的两种视图（一次同步 vs 一个驿站的采集策略）。
 */
export function useSyncPage() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')

  // 配置项定义 + 选项集：采集配置表/抽屉与配置管理都要用（单例，只拉一次），仅 ADMIN 有权限读取
  const meta = useSyncConfigMeta()

  // 页头副信息与三个 Tab 共用一份时间戳，故由编排层持有、各取数成功后回写
  const updatedAt = ref('')
  const markUpdated = () => {
    const now = new Date()
    updatedAt.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  }

  const logs = useSyncBatchLogs()
  const batch = useSyncBatchList({ markUpdated, onChanged: (row) => logs.reloadIfOpen(row) })
  const collect = useSyncCollect({ markUpdated })

  const activeTab = ref(resolveInitialTab(route.query.tab, isAdmin.value))

  const filters = computed(() => ({
    stationId: batch.query.stationId,
    status: batch.query.status,
    keyword: batch.query.keyword
  }))

  /** 筛选栏只回抛变更后的整份筛选对象，此处合并进 query，保持筛选值单一来源 */
  function applyFilters(next) {
    Object.assign(batch.query, next)
  }

  const headerLoading = computed(() => {
    if (activeTab.value === 'batch') return batch.loading.value
    if (activeTab.value === 'config') return meta.loading.value
    return collect.collectLoading.value
  })

  const headerSub = computed(() => {
    const scope = isAdmin.value ? '数据范围：全域' : '数据范围：本站'
    if (activeTab.value === 'config') {
      return `${scope} · 共 ${meta.items.value.length} 个配置项 · 更新于 ${updatedAt.value || '—'}`
    }
    if (activeTab.value === 'collect') {
      return `${scope} · 共 ${collect.overview.value.total} 个驿站 · 更新于 ${updatedAt.value || '—'}`
    }
    return `${scope} · 共 ${batch.total.value} 个批次 · 更新于 ${updatedAt.value || '—'}`
  })

  /** Tab 状态写 URL（?tab=collect / ?tab=config）：演示时可直达；其它业务参数（taskId）原样保留 */
  function handleTabChange(name) {
    const nextQuery = { ...route.query }
    if (name === 'collect' || name === 'config') nextQuery.tab = name
    else delete nextQuery.tab
    router.replace({ query: nextQuery })
    if (name === 'collect') {
      if (!collect.configs.value.length && !collect.collectLoading.value) collect.fetchCollect()
    } else if (name === 'batch') {
      if (!batch.list.value.length && !batch.loading.value) batch.fetchList()
    } else if (!meta.loaded.value) {
      // 配置管理容器自身会在挂载时拉数据，这里只保证采集配置表的选项集标签也已就绪
      meta.loadMeta()
    }
  }

  async function init() {
    // 元数据是采集配置表/抽屉与配置管理的共同依赖，ADMIN 一进页就拉一次（站长无权限，不请求）
    if (isAdmin.value) meta.loadMeta()
    if (activeTab.value === 'collect') {
      await collect.fetchCollect()
    } else if (activeTab.value === 'batch') {
      await batch.fetchList()
    }
    if (isAdmin.value) batch.loadStations()
    if (route.query.taskId) {
      activeTab.value = 'batch'
      logs.openLogsById(route.query.taskId)
    }
  }

  return reactive({
    isAdmin,
    activeTab,
    filters,
    headerLoading,
    headerSub,
    applyFilters,
    handleTabChange,
    init,
    ...batch,
    ...collect,
    ...logs
  })
}
