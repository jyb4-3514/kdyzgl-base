import { ref } from 'vue'
import { getParcelSummary } from '../../../api/parcel.js'

/**
 * 系统设置页的异步数据块（S3「已预置包裹总数」）
 *
 * 为什么单独收一个 composable：整页只有这一处请求。独立之后，重试只重拉这一块，
 * 而且 S1/S2/S4 这些同步分区不会跟着 loading 一起被换成骨架屏（设计规范 §2.4）。
 */
export function useSystemSettings() {
  const loading = ref(false)
  const error = ref(false)
  const total = ref(null)
  // 连点重试时会有多个请求在飞，回来顺序不定，用序号丢弃过期响应（demo-pc-refactor.md §5）
  let reqSeq = 0

  /** 取既有接口的 parcelTotal（GET /parcels/summary）；契约字段就是 parcelTotal，不要按字面猜成 total */
  async function loadTotal() {
    const seq = (reqSeq += 1)
    loading.value = true
    error.value = false
    try {
      const data = await getParcelSummary()
      if (seq !== reqSeq) return
      total.value = data ? data.parcelTotal : null
    } catch (e) {
      if (seq !== reqSeq) return
      total.value = null
      error.value = true
    } finally {
      if (seq === reqSeq) loading.value = false
    }
  }

  return { loading, error, total, loadTotal }
}
