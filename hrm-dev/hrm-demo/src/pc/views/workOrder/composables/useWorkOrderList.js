import { computed, reactive, ref } from 'vue'
import { getStations } from '@admin/api/station'
import { getWorkOrders } from '../../../api/workOrder.js'

/**
 * 工单列表数据编排（筛选 / 分页 / 状态 Tab / Tab 计数）
 *
 * 竞态守卫：筛选请求先发、首屏请求后到时，旧结果不得覆盖新结果。
 * 生效前提是「发起时自增、回来时比对」——只在筛选变化时自增会漏掉首屏与筛选之间的竞争。
 * TODO(扩展): 同类竞态在考勤/包裹/员工列表页同样存在且实现相同，等抽出公共 useLatestRequest 后统一替换，不要逐页复制这段序号逻辑。
 */
export function useWorkOrderList(isAdmin) {
  // TODO(扩展): 筛选条件写回 URL（A5-2 的 useQuerySync）
  const query = reactive({
    stationId: undefined,
    type: undefined,
    priority: undefined,
    keyword: '',
    pageNum: 1,
    pageSize: 20
  })

  const activeTab = ref('all')
  const loading = ref(false)
  const list = ref([])
  const total = ref(0)
  const listError = ref(false)
  const updatedAt = ref('')
  const stations = ref([])
  // Tab 计数与列表分开维护：列表只查当前 Tab，计数需要各状态各查一次（pageSize=1 取 total）
  const tabCounts = ref({ all: 0 })

  let listSeq = 0

  const hasFilter = computed(
    () =>
      query.stationId !== undefined ||
      query.type !== undefined ||
      query.priority !== undefined ||
      !!query.keyword ||
      activeTab.value !== 'all'
  )

  const headerSub = computed(
    () =>
      `${isAdmin.value ? '数据范围：全域' : '数据范围：本站'} · 共 ${total.value} 条 · 更新于 ${updatedAt.value || '—'}`
  )

  function stamp() {
    const now = new Date()
    return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  }

  /** 只含筛选条件的公共部分，供列表与 Tab 计数复用 */
  function baseParams() {
    return {
      stationId: query.stationId,
      type: query.type,
      priority: query.priority,
      keyword: query.keyword || undefined
    }
  }

  function buildParams() {
    const params = baseParams()
    if (activeTab.value === 'overdueUnhandled') params.overdueUnhandled = '1'
    else if (activeTab.value !== 'all') params.status = Number(activeTab.value)
    return params
  }

  async function fetchList() {
    const seq = (listSeq += 1)
    loading.value = true
    listError.value = false
    try {
      const page = await getWorkOrders({ ...buildParams(), pageNum: query.pageNum, pageSize: query.pageSize })
      // 请求期间用户又改了筛选／翻了页：本次结果已过期，直接丢弃，连 loading 也交给更新的那次收尾
      if (seq !== listSeq) return
      list.value = page.list
      total.value = page.total
      updatedAt.value = stamp()
    } catch (e) {
      if (seq !== listSeq) return
      list.value = []
      total.value = 0
      listError.value = true
    } finally {
      if (seq === listSeq) loading.value = false
    }
  }

  /**
   * Tab 计数：计数失败不阻塞列表，只让计数位不显示
   * TODO(扩展): 待后端提供 GET /work-orders/stats 聚合接口后改为单次请求（A7-3 依赖 R-1），
   * 现在 6 次 pageSize=1 的请求会随筛选维度增加而继续膨胀
   */
  async function loadCounts() {
    const base = baseParams()
    try {
      const [all, s0, s1, s2, s3, over] = await Promise.all([
        getWorkOrders({ ...base, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ ...base, status: 0, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ ...base, status: 1, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ ...base, status: 2, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ ...base, status: 3, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ ...base, overdueUnhandled: '1', pageNum: 1, pageSize: 1 })
      ])
      tabCounts.value = {
        all: all.total,
        0: s0.total,
        1: s1.total,
        2: s2.total,
        3: s3.total,
        overdueUnhandled: over.total
      }
    } catch (e) {
      tabCounts.value = {}
    }
  }

  /** 列表与计数一起刷新：两者必须同源同刻，否则会出现「计数 3、列表 2 条」 */
  function refreshPage() {
    fetchList()
    loadCounts()
  }

  async function loadStations() {
    try {
      stations.value = await getStations()
    } catch (e) {
      /* 站点下拉失败不阻塞列表筛选 */
    }
  }

  function handleTabChange() {
    query.pageNum = 1
    fetchList()
  }

  function handleSearch() {
    query.pageNum = 1
    refreshPage()
  }

  function handleReset() {
    query.stationId = undefined
    query.type = undefined
    query.priority = undefined
    query.keyword = ''
    query.pageNum = 1
    refreshPage()
  }

  function handleSizeChange() {
    query.pageNum = 1
    fetchList()
  }

  return {
    query,
    activeTab,
    loading,
    list,
    total,
    listError,
    updatedAt,
    stations,
    tabCounts,
    hasFilter,
    headerSub,
    baseParams,
    buildParams,
    fetchList,
    loadCounts,
    refreshPage,
    loadStations,
    handleTabChange,
    handleSearch,
    handleReset,
    handleSizeChange
  }
}
