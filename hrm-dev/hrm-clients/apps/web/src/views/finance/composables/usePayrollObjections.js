import { ref } from 'vue'
import { getPayrolls } from '../../../api/finance.js'

/**
 * 异议处理（Tab 3）
 *
 * 契约没有独立的异议列表接口，本表按「异议退回（OBJECTED）」状态取数（C-1 后员工异议落 OBJECTED）；
 * OBJECTED 本身即异议态，无需再按 objectionReason 本地过滤。
 */
export function usePayrollObjections() {
  const objections = ref([])
  const objectionLoading = ref(false)
  const objectionError = ref(false)

  let objectionSeq = 0

  async function loadObjections() {
    const seq = (objectionSeq += 1)
    objectionLoading.value = true
    objectionError.value = false
    try {
      const page = await getPayrolls({ status: 'OBJECTED', pageNum: 1, pageSize: 100 })
      if (seq !== objectionSeq) return
      objections.value = page.list || []
    } catch (e) {
      if (seq !== objectionSeq) return
      objectionError.value = true
    } finally {
      if (seq === objectionSeq) objectionLoading.value = false
    }
  }

  return { objections, objectionLoading, objectionError, loadObjections }
}
