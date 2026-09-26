import { computed, reactive, ref } from 'vue'
import { getSummary } from '@/api/dashboard.js'
import { getWorkOrders } from '../../../api/workOrder.js'
import { formatMinutes, parseDateTime } from '../../../utils/format.js'
import { BASE_CARDS, buildWorkOrderMetrics } from '../model/dashboardMeta.js'

/**
 * 看板数据编排（多路并发 + 加载态聚合 + 竞态守卫）
 *
 * MVP 裁剪：包裹总览 / 包裹趋势 / 驿站排行 / 同步健康度四路取数随「包裹族」模块下架，
 * 现在只剩「一期组织规模 + 工单指标」两路，两路各自独立 loading/error（一块失败不拖垮另一块）。
 *
 * 竞态守卫：每路请求自己一个序号，发起时自增、响应回来比对，旧响应整段丢弃。
 * TODO(扩展): 与工单/考勤列表页的同类序号逻辑抽成公共 useLatestRequest 后统一替换。
 */
export function useDashboardData() {
  const loading = ref(false)
  const updatedAt = ref('')

  const summary = ref(null)
  const workOrder = ref({
    pendingCount: 0,
    processingCount: 0,
    overSlaCount: 0,
    todayNewCount: 0,
    avgHandleMinutes: null
  })

  // 每个区块独立记录失败，让"哪一块挂了"在页面上直接可见（修 P14）
  const baseError = ref(false)
  const woError = ref(false)

  const woLoading = ref(false)

  let baseSeq = 0
  let woSeq = 0

  const woMetrics = computed(() => buildWorkOrderMetrics(workOrder.value, formatMinutes))

  const orgSummaryText = computed(() =>
    summary.value ? `员工 ${summary.value.employeeTotal} · 驿站 ${summary.value.stationTotal}` : '—'
  )

  const headerSub = computed(() => {
    const parts = ['数据范围：全域']
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

  async function loadAll() {
    loading.value = true
    await Promise.all([loadBase(), loadWorkOrder()])
    updatedAt.value = stamp()
    loading.value = false
  }

  return reactive({
    loading,
    updatedAt,
    summary,
    workOrder,
    baseError,
    woError,
    woLoading,
    baseCards: BASE_CARDS,
    woMetrics,
    orgSummaryText,
    headerSub,
    loadBase,
    loadWorkOrder,
    loadAll
  })
}
