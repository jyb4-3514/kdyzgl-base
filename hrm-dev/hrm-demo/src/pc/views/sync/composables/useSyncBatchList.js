import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStations } from '@admin/api/station'
import { getSyncTasks, retrySyncTask, triggerSyncTask } from '../../../api/syncTask.js'

/**
 * 批次流水列表（筛选 / 分页 / 触发 / 重试）
 *
 * 竞态守卫：先发后到的旧筛选结果不得覆盖新结果。生效前提是「发起时自增、回来时比对」——
 * 只在筛选变化时自增会漏掉首屏与筛选之间的竞争。
 * TODO(扩展): 与其他列表页同款序号逻辑抽成公共 useLatestRequest 后统一替换（A5-2 的 useQuerySync 一并处理）。
 *
 * @param markUpdated 取数成功后的时间戳回调（页头副信息共用一份 updatedAt，故由编排层持有）
 * @param onChanged   触发/重试成功后的联动回调（编排层用它刷新已打开的日志抽屉）
 */
export function useSyncBatchList({ markUpdated = () => {}, onChanged } = {}) {
  const query = reactive({ stationId: undefined, status: undefined, keyword: '', pageNum: 1, pageSize: 20 })
  const loading = ref(false)
  const list = ref([])
  const total = ref(0)
  const listError = ref(false)
  const stations = ref([])
  // 触发 / 重试各自独立的 loading：改前共用一个 actingId，点触发时重试也跟着转圈（A6-2）
  const triggeringId = ref(null)
  const retryingId = ref(null)

  let listSeq = 0

  const hasFilter = computed(() => query.stationId !== undefined || query.status !== undefined || !!query.keyword)

  async function fetchList() {
    const seq = (listSeq += 1)
    loading.value = true
    listError.value = false
    try {
      const page = await getSyncTasks({
        stationId: query.stationId,
        status: query.status,
        keyword: query.keyword || undefined,
        pageNum: query.pageNum,
        pageSize: query.pageSize
      })
      // 请求期间用户又改了筛选／翻了页：本次结果已过期，连 loading 也交给更新的那次收尾
      if (seq !== listSeq) return
      list.value = page.list
      total.value = page.total
      markUpdated()
    } catch (e) {
      if (seq !== listSeq) return
      list.value = []
      total.value = 0
      listError.value = true
    } finally {
      if (seq === listSeq) loading.value = false
    }
  }

  async function loadStations() {
    try {
      stations.value = await getStations()
    } catch (e) {
      /* 站点下拉失败不阻塞列表筛选 */
    }
  }

  /** 触发 / 重试共用：成功后刷新列表，再交由编排层决定日志抽屉是否要同步追加 */
  async function runAction(row, action, successText, loadingRef) {
    loadingRef.value = row.id
    try {
      await action()
      ElMessage.success(successText)
      await fetchList()
      if (onChanged) await onChanged(row)
    } catch (e) {
      /* 拦截器已统一提示（6001 等业务码） */
    } finally {
      loadingRef.value = null
    }
  }

  async function handleTrigger(row) {
    try {
      await ElMessageBox.confirm(`确定手动触发批次「${row.batchNo}」的同步吗？`, '手动触发', { type: 'warning' })
    } catch (e) {
      return
    }
    await runAction(row, () => triggerSyncTask(row.id), '已触发执行（Mock 直接跑完整个状态机）', triggeringId)
  }

  async function handleRetry(row) {
    try {
      await ElMessageBox.confirm(`确定重试批次「${row.batchNo}」吗？重试后回到待领取状态。`, '失败重试', {
        type: 'warning'
      })
    } catch (e) {
      return
    }
    await runAction(row, () => retrySyncTask(row.id), '已回到待领取队列，可继续触发', retryingId)
  }

  function handleSearch() {
    query.pageNum = 1
    fetchList()
  }

  function handleReset() {
    query.stationId = undefined
    query.status = undefined
    query.keyword = ''
    query.pageNum = 1
    fetchList()
  }

  /** 改每页条数：先落值再回到第 1 页，保证请求参数与控件显示一致 */
  function handleSizeChange(size) {
    query.pageSize = size
    query.pageNum = 1
    fetchList()
  }

  function handlePageChange(pageNum) {
    query.pageNum = pageNum
    fetchList()
  }

  return {
    query,
    loading,
    list,
    total,
    listError,
    stations,
    triggeringId,
    retryingId,
    hasFilter,
    fetchList,
    loadStations,
    handleTrigger,
    handleRetry,
    handleSearch,
    handleReset,
    handleSizeChange,
    handlePageChange
  }
}
