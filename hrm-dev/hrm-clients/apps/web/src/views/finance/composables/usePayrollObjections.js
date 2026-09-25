import { ref } from 'vue'
import { getPayrolls } from '../../../api/finance.js'

/**
 * 异议处理（Tab 3）
 *
 * 契约没有独立的异议列表接口，本表取「待审核」状态的单据后筛选有异议原因的行，
 * 故这里只做一次状态查询 + 本地过滤，不额外造一层接口。
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
      const page = await getPayrolls({ status: 'PENDING_APPROVAL', pageNum: 1, pageSize: 100 })
      if (seq !== objectionSeq) return
      objections.value = (page.list || []).filter((row) => !!row.objectionReason)
    } catch (e) {
      if (seq !== objectionSeq) return
      objectionError.value = true
    } finally {
      if (seq === objectionSeq) objectionLoading.value = false
    }
  }

  return { objections, objectionLoading, objectionError, loadObjections }
}
