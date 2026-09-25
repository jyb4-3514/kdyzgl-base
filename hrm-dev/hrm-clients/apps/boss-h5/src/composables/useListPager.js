import { ref } from 'vue'
import { useLatestRequest } from './useLatestRequest.js'

/**
 * van-list 触底分页编排
 *
 * 两种保护各司其职、不可互相顶替：
 * - `busy` 单飞：van-list 触发 load 前会把自身 loading 置 true，重复触底必须靠独立标记丢弃（范本 staff/parcel.vue:41）
 * - 序号守卫：切换筛选 / 下拉刷新走整表替换，先发的慢响应不得盖住后发的快响应
 *
 * 为什么 reload 不受 busy 阻挡：若被阻挡，在途的触底请求会把新筛选挡在门外，这正是 G2 的故障形态。
 */
export function useListPager(loadPage, { pageSize = 20 } = {}) {
  const list = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  /** 绑 van-list 的 loading */
  const loading = ref(false)
  const finished = ref(false)
  const refreshing = ref(false)
  const error = ref('')
  /** 首屏是否已出结果：为空态判断提供依据（未加载完不能显示「暂无数据」） */
  const initialized = ref(false)

  let busy = false
  const guard = useLatestRequest()

  async function fetchOnce(append, token) {
    try {
      const page = await loadPage({ pageNum: pageNum.value, pageSize })
      if (!guard.isLatest(token)) return
      error.value = ''
      total.value = page.total
      list.value = append ? [...list.value, ...page.list] : page.list
      pageNum.value += 1
      finished.value = list.value.length >= page.total
    } catch (e) {
      if (!guard.isLatest(token)) return
      error.value = e.message || '加载失败'
      finished.value = true
    } finally {
      // loading 的关闭同样受序号保护：过期响应不得把新请求的 loading 关掉
      if (guard.isLatest(token)) {
        loading.value = false
        refreshing.value = false
        initialized.value = true
      }
    }
  }

  /** van-list 触底：单飞防重复请求 */
  async function loadMore() {
    if (busy || finished.value) return
    busy = true
    loading.value = true
    await guard.run((token) => fetchOnce(true, token))
    busy = false
  }

  /** 筛选变化 / 下拉刷新：整表替换，并让在途请求立即过期 */
  async function reload() {
    guard.cancel()
    refreshing.value = true
    loading.value = true
    pageNum.value = 1
    error.value = ''
    await guard.run((token) => fetchOnce(false, token))
  }

  /** 作废在途请求（组件卸载）：之后的响应不再写状态 */
  function cancel() {
    guard.cancel()
    busy = false
    loading.value = false
    refreshing.value = false
  }

  return { list, total, pageNum, loading, finished, refreshing, error, initialized, loadMore, reload, cancel }
}
