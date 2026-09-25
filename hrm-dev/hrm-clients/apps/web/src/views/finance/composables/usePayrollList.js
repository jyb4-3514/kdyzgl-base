import { computed, reactive, ref } from 'vue'
import { getPayrolls } from '../../../api/finance.js'
import { currentMonth } from '../model/financeMeta.js'

/**
 * 工资单列表（筛选 / 分页 / 状态计数）
 *
 * 契约边界：工资单列表是按「员工 × 账期」逐行的，没有「按驿站汇总的单批次」概念，
 * 故列表列与状态计数都按员工单据口径呈现，不额外造一层批次聚合。
 *
 * 竞态守卫：连点查询或快速换筛选时，旧响应不得覆盖新数据（发起时自增、回来时比对）。
 * TODO(扩展): 同款序号逻辑与工单/同步/考勤页重复，抽成公共 useLatestRequest 后统一替换。
 */
export function usePayrollList() {
  const query = reactive({
    month: currentMonth(),
    stationId: undefined,
    status: undefined,
    keyword: '',
    pageNum: 1,
    pageSize: 20
  })

  const list = ref([])
  const total = ref(0)
  const counts = ref(null)
  const listLoading = ref(false)
  const listError = ref(false)

  let listSeq = 0

  async function fetchList() {
    const seq = (listSeq += 1)
    listLoading.value = true
    listError.value = false
    try {
      const page = await getPayrolls({
        month: query.month || undefined,
        stationId: query.stationId,
        status: query.status,
        keyword: query.keyword || undefined,
        pageNum: query.pageNum,
        pageSize: query.pageSize
      })
      if (seq !== listSeq) return
      list.value = page.list || []
      total.value = page.total || 0
      counts.value = page.counts || null
    } catch (e) {
      if (seq !== listSeq) return
      listError.value = true
    } finally {
      if (seq === listSeq) listLoading.value = false
    }
  }

  function resetPage() {
    query.pageNum = 1
    fetchList()
  }

  /** 状态计数条点选：再点一次同一状态即取消，省掉回下拉清空的一次操作 */
  function filterByStatus(status) {
    query.status = query.status === status ? undefined : status
    resetPage()
  }

  function handlePageChange(page) {
    query.pageNum = page
    fetchList()
  }

  function handleSizeChange(size) {
    query.pageSize = size
    query.pageNum = 1
    fetchList()
  }

  /** 批量提交的候选数：以状态计数为准（同月同驿站口径，不受当前分页影响） */
  const pendingSubmitCount = computed(
    () => ((counts.value && counts.value.DRAFT) || 0) + ((counts.value && counts.value.REJECTED) || 0)
  )
  const approvedCount = computed(() => (counts.value && counts.value.APPROVED) || 0)

  return {
    query,
    list,
    total,
    counts,
    listLoading,
    listError,
    pendingSubmitCount,
    approvedCount,
    fetchList,
    resetPage,
    filterByStatus,
    handlePageChange,
    handleSizeChange
  }
}
