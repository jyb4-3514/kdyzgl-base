import { computed, reactive, ref } from 'vue'
import { getSummary } from '@/api/dashboard.js'
import { getParcelRanking, getParcelSummary, getParcelTrend } from '../../../api/parcel.js'
import { getSyncTasks } from '../../../api/syncTask.js'
import { getWorkOrders } from '../../../api/workOrder.js'
import { formatMinutes, parseDateTime } from '../../../utils/format.js'
import { RANK_LABELS, BASE_CARDS, buildRankRows, buildHeroCards, buildInlineCards, buildWorkOrderMetrics } from '../model/dashboardMeta.js'

/**
 * 看板数据编排（多路并发 + 加载态聚合 + 竞态守卫）
 *
 * 为什么六路请求各自独立 loading/error：一个区块接口挂了不能把整页拖进错误态，
 * 否则「同步健康度失败」会让用户误以为包裹指标也没了（修 P14 的错误态伪装成空态）。
 *
 * 竞态守卫：每路请求自己一个序号，发起时自增、响应回来比对，旧响应整段丢弃。
 * 看板是「首屏并发 6 路 + 筛选/切档随时可能重发」的高竞争场景，旧响应盖新数据尤为明显。
 * TODO(扩展): 与工单/考勤/包裹列表页的同类序号逻辑抽成公共 useLatestRequest 后统一替换。
 */
export function useDashboardData() {
  const loading = ref(false)
  const trendDays = ref(7)
  const rankSort = ref('parcelTotal')
  const updatedAt = ref('')

  const summary = ref(null)
  const parcelSummary = ref(null)
  const trend = ref([])
  const ranking = ref([])
  const workOrder = ref({
    pendingCount: 0,
    processingCount: 0,
    overSlaCount: 0,
    todayNewCount: 0,
    avgHandleMinutes: null
  })
  // 同步健康度用「各驿站最近一个已结束批次」聚合，避免待领取/执行中批次把分母撑大导致健康度恒为 0
  const syncHealth = ref({ latestBatchSuccessRate: 0, failedStationCount: 0, lastBatchTime: '' })

  // 每个区块独立记录失败，让"哪一块挂了"在页面上直接可见（修 P14）
  const baseError = ref(false)
  const parcelError = ref(false)
  const healthError = ref(false)
  const woError = ref(false)
  const rankError = ref(false)
  const trendError = ref(false)

  const parcelLoading = ref(false)
  const healthLoading = ref(false)
  const woLoading = ref(false)
  const rankLoading = ref(false)
  const trendLoading = ref(false)

  let baseSeq = 0
  let parcelSeq = 0
  let trendSeq = 0
  let rankSeq = 0
  let healthSeq = 0
  let woSeq = 0

  // 指标卡与工单形态都是纯映射，落在 model 便于单测；此处只把响应式值喂进去
  const heroCards = computed(() => buildHeroCards(parcelSummary.value, trend.value))

  const inlineCards = computed(() =>
    buildInlineCards({
      parcelSummary: parcelSummary.value,
      parcelLoading: parcelLoading.value,
      parcelError: parcelError.value,
      healthLoading: healthLoading.value,
      healthError: healthError.value,
      syncHealth: syncHealth.value,
      woLoading: woLoading.value,
      woError: woError.value,
      workOrder: workOrder.value
    })
  )

  const woMetrics = computed(() => buildWorkOrderMetrics(workOrder.value, formatMinutes))

  const rankLabel = computed(() => RANK_LABELS[rankSort.value])

  const rankRows = computed(() => buildRankRows(ranking.value, rankSort.value))

  const orgSummaryText = computed(() =>
    summary.value ? `员工 ${summary.value.employeeTotal} · 驿站 ${summary.value.stationTotal}` : '—'
  )

  const headerSub = computed(() => {
    const parts = ['数据范围：全域']
    if (parcelSummary.value) parts.push(`包裹 ${Number(parcelSummary.value.parcelTotal).toLocaleString('zh-CN')} 件`)
    if (updatedAt.value) parts.push(`更新于 ${updatedAt.value}`)
    return parts.join(' · ')
  })

  function stamp() {
    const now = new Date()
    return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  }

  async function loadBase() {
    const seq = (baseSeq += 1)
    baseError.value = false
    try {
      const data = await getSummary()
      if (seq !== baseSeq) return
      summary.value = data
    } catch (e) {
      if (seq !== baseSeq) return
      baseError.value = true
    }
  }

  async function loadParcel() {
    const seq = (parcelSeq += 1)
    parcelLoading.value = true
    parcelError.value = false
    try {
      const data = await getParcelSummary()
      if (seq !== parcelSeq) return
      parcelSummary.value = data
    } catch (e) {
      if (seq !== parcelSeq) return
      parcelError.value = true
    } finally {
      if (seq === parcelSeq) parcelLoading.value = false
    }
  }

  async function loadTrend() {
    const seq = (trendSeq += 1)
    trendLoading.value = true
    trendError.value = false
    const days = trendDays.value
    try {
      const data = await getParcelTrend(days)
      if (seq !== trendSeq) return
      trend.value = data
    } catch (e) {
      if (seq !== trendSeq) return
      trend.value = []
      trendError.value = true
    } finally {
      if (seq === trendSeq) trendLoading.value = false
    }
  }

  async function loadRanking() {
    const seq = (rankSeq += 1)
    rankLoading.value = true
    rankError.value = false
    try {
      const data = await getParcelRanking(rankSort.value)
      if (seq !== rankSeq) return
      ranking.value = (data || []).slice(0, 5)
    } catch (e) {
      if (seq !== rankSeq) return
      ranking.value = []
      rankError.value = true
    } finally {
      if (seq === rankSeq) rankLoading.value = false
    }
  }

  async function loadSyncHealth() {
    const seq = (healthSeq += 1)
    healthLoading.value = true
    healthError.value = false
    try {
      const page = await getSyncTasks({ pageNum: 1, pageSize: 100 })
      if (seq !== healthSeq) return
      const latestByStation = new Map()
      page.list
        .filter((task) => task.status === 2 || task.status === 3)
        .forEach((task) => {
          if (!latestByStation.has(task.stationId)) latestByStation.set(task.stationId, task)
        })
      const batches = [...latestByStation.values()]
      const parcelTotal = batches.reduce((sum, task) => sum + task.parcelTotal, 0)
      const successTotal = batches.reduce((sum, task) => sum + task.successCount, 0)
      syncHealth.value = {
        latestBatchSuccessRate: parcelTotal ? successTotal / parcelTotal : 0,
        failedStationCount: batches.filter((task) => task.status === 3).length,
        lastBatchTime: batches.reduce((latest, task) => (task.createTime > latest ? task.createTime : latest), '')
      }
    } catch (e) {
      if (seq !== healthSeq) return
      healthError.value = true
    } finally {
      if (seq === healthSeq) healthLoading.value = false
    }
  }

  /**
   * 工单指标：Mock 没有汇总接口，用列表接口的 total 与首页样本推算
   * TODO(扩展): 待后端提供 /dashboard/work-order-summary 后改为单次请求（含精确的 avgHandleMinutes）
   */
  async function loadWorkOrder() {
    const seq = (woSeq += 1)
    woLoading.value = true
    woError.value = false
    try {
      const [pending, processing, overSla, recent, resolved] = await Promise.all([
        getWorkOrders({ status: 0, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ status: 1, pageNum: 1, pageSize: 1 }),
        getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 1 }),
        getWorkOrders({ pageNum: 1, pageSize: 100 }),
        getWorkOrders({ status: 2, pageNum: 1, pageSize: 100 })
      ])
      if (seq !== woSeq) return
      const todayStart = new Date().setHours(0, 0, 0, 0)
      const durations = resolved.list
        .map((order) => parseDateTime(order.resolvedTime) - parseDateTime(order.createTime))
        .filter((ms) => ms > 0)
      workOrder.value = {
        pendingCount: pending.total,
        processingCount: processing.total,
        overSlaCount: overSla.total,
        todayNewCount: recent.list.filter((order) => parseDateTime(order.createTime) >= todayStart).length,
        avgHandleMinutes: durations.length
          ? durations.reduce((sum, ms) => sum + ms, 0) / durations.length / 60000
          : null
      }
    } catch (e) {
      if (seq !== woSeq) return
      woError.value = true
    } finally {
      if (seq === woSeq) woLoading.value = false
    }
  }

  /** 切档：先落值再请求，保证请求参数与控件显示一致（与工单页翻页口径一致） */
  function setTrendDays(value) {
    trendDays.value = value
    loadTrend()
  }

  function setRankSort(value) {
    rankSort.value = value
    loadRanking()
  }

  async function loadAll() {
    loading.value = true
    await Promise.all([loadBase(), loadParcel(), loadSyncHealth(), loadWorkOrder(), loadTrend(), loadRanking()])
    updatedAt.value = stamp()
    loading.value = false
  }

  return reactive({
    loading,
    trendDays,
    rankSort,
    updatedAt,
    summary,
    parcelSummary,
    trend,
    ranking,
    workOrder,
    syncHealth,
    baseError,
    parcelError,
    healthError,
    woError,
    rankError,
    trendError,
    parcelLoading,
    healthLoading,
    woLoading,
    rankLoading,
    trendLoading,
    baseCards: BASE_CARDS,
    heroCards,
    inlineCards,
    woMetrics,
    rankLabel,
    rankRows,
    orgSummaryText,
    headerSub,
    loadBase,
    loadParcel,
    loadTrend,
    loadRanking,
    loadSyncHealth,
    loadWorkOrder,
    setTrendDays,
    setRankSort,
    loadAll
  })
}
