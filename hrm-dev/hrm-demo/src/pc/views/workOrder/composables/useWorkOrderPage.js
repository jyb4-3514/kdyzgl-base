import { computed, reactive } from 'vue'
import { useAuthStore } from '@admin/stores/auth'
import { useWorkOrderList } from './useWorkOrderList.js'
import { useWorkOrderDetail } from './useWorkOrderDetail.js'
import { useWorkOrderAllocator } from './useWorkOrderAllocator.js'
import { useWorkOrderCreate } from './useWorkOrderCreate.js'

/**
 * 工单页编排：把列表 / 详情 / 处置 / 建单四块状态串成一份页面级状态
 *
 * 为什么用 reactive 聚合而不是让页面壳逐个解构：四块展开近 40 个绑定，逐个解构会把壳撑到 200 行，
 * 壳就失去了「只看装配」的意义（reactive 会自动脱 ref，模板里直接 page.xxx，v-model 也能写入）。
 * 四个子 composable 仍保持 plain object 返回，便于单测单独调用。
 */
export function useWorkOrderPage() {
  const authStore = useAuthStore()
  const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')
  const currentUser = computed(() => authStore.user || {})

  const list = useWorkOrderList(isAdmin)
  const detail = useWorkOrderDetail({ isAdmin, currentUser, refreshPage: list.refreshPage })
  const allocator = useWorkOrderAllocator({
    isAdmin,
    currentUser,
    refreshPage: list.refreshPage,
    loadDetail: detail.loadDetail
  })
  const create = useWorkOrderCreate({
    isAdmin,
    currentUser,
    query: list.query,
    stations: list.stations,
    activeTab: list.activeTab,
    refreshPage: list.refreshPage,
    openDetail: detail.openDetail
  })

  /** 筛选栏只回抛变更后的筛选对象，此处合并进 query，保持筛选值单一来源 */
  const filters = computed(() => ({
    stationId: list.query.stationId,
    type: list.query.type,
    priority: list.query.priority,
    keyword: list.query.keyword
  }))

  function applyFilters(next) {
    Object.assign(list.query, next)
  }

  /** 翻页/改每页条数：先落到 query 再请求，保证请求参数与控件显示一致 */
  function handlePageChange(pageNum) {
    list.query.pageNum = pageNum
    list.fetchList()
  }

  function handlePageSizeChange(pageSize) {
    list.query.pageSize = pageSize
    list.handleSizeChange()
  }

  /** 首屏：列表 + Tab 计数 + 驿站下拉（仅老板需要驿站下拉） */
  async function init() {
    list.refreshPage()
    if (isAdmin.value) await list.loadStations()
  }

  return reactive({
    isAdmin,
    currentUser,
    ...list,
    ...detail,
    ...allocator,
    ...create,
    filters,
    applyFilters,
    handlePageChange,
    handlePageSizeChange,
    init
  })
}
