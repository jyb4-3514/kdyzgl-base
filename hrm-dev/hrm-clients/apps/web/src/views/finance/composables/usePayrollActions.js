import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvePayroll, getPayrolls, payPayroll, publishPayrolls, submitPayrolls } from '../../../api/finance.js'

/**
 * 工资单动作（行内 / 批量提交与发布 / 审核弹窗）
 *
 * 所有不可撤回的操作（发布、驳回）都走 B0.3 的确认文案：动词标题 + 影响范围 + 具体动词按钮。
 * 审核表单由弹窗组件持有，本 composable 只收提交载荷，保证「组件不管接口、接口不管表单」。
 *
 * @param query              当前筛选（批量动作按筛选范围取数）
 * @param pendingSubmitCount 批量提交候选数（状态计数派生）
 * @param approvedCount      可发布数（状态计数派生）
 * @param fetchList          列表刷新
 * @param loadObjections     异议列表刷新（提交/审核会改变「待审核」集合）
 * @param closeDetail        关闭详情抽屉（动作成功后不再停留在过期详情上）
 */
export function usePayrollActions({ query, pendingSubmitCount, approvedCount, fetchList, loadObjections, closeDetail }) {
  const approveVisible = ref(false)
  const approving = ref(false)
  const approveRow = ref(null)

  function openApprove(row) {
    approveRow.value = row
    approveVisible.value = true
  }

  async function submitApprove(form) {
    if (!approveRow.value) return
    const remark = String(form.approveRemark || '').trim()
    approving.value = true
    try {
      await approvePayroll(approveRow.value.id, { approved: form.approved, approveRemark: remark || undefined })
      ElMessage.success(form.approved ? '审核通过，可在列表里发布' : '已驳回，单据退回草稿状态')
      approveVisible.value = false
      closeDetail()
      fetchList()
      loadObjections()
    } finally {
      approving.value = false
    }
  }

  /** 行内/抽屉动作统一入口：按动作分派，确认文案全部走 B0.3 规范 */
  function handleRowAction({ action, row }) {
    if (!row) return
    if (action === 'submit') {
      ElMessageBox.confirm(
        `将把 ${row.employeeName} ${row.month} 的工资单提交审核，提交后本人仍需管理员审核通过才能发布。`,
        '提交审核',
        { confirmButtonText: '确认提交', cancelButtonText: '再想想', type: 'warning' }
      )
        .then(() => submitPayrolls([row.id]))
        .then((data) => {
          ElMessage.success(`已提交 ${data.submitted} 份工资单待审核`)
          closeDetail()
          fetchList()
          loadObjections()
        })
        .catch(() => {})
      return
    }
    if (action === 'approve') {
      openApprove(row)
      return
    }
    if (action === 'publish') {
      // C-2：来源 OBJECTED 为「重新发布」，文案与首发区分（不可与首发混淆）
      confirmPublish([row.id], row.employeeName, row.status === 'OBJECTED')
      return
    }
    if (action === 'pay') {
      // I-8：不可逆终态，四要素确认（对象 / 影响面 / 不可逆声明 / 具体动词按钮）
      ElMessageBox.confirm(
        `对象：${row.employeeName} · ${row.month} · 实发 ${row.netAmount} 元；发放后单据进入「已发放」并归档冻结，员工可见该归档态；本期不支持撤销 / 冲正，确认后不可修改。`,
        '确认工资已发放',
        { confirmButtonText: '确认已发放', cancelButtonText: '再想想', type: 'warning' }
      )
        .then(() => payPayroll(row.id))
        .then(() => {
          ElMessage.success('已标记发放，工资单已归档')
          closeDetail()
          fetchList()
        })
        .catch(() => {})
    }
  }

  async function handleBatchSubmit() {
    try {
      await ElMessageBox.confirm(
        `将把当前范围内全部草稿与已驳回的工资单（共 ${pendingSubmitCount.value} 份）提交审核；提交后需审核通过才能发布。`,
        '批量提交审核',
        { confirmButtonText: '确认提交', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch (e) {
      return
    }
    // 契约的提交接口按 ids 收口，故先取全量范围（上限 100 条）再提交
    const page = await getPayrolls({
      month: query.month || undefined,
      stationId: query.stationId,
      pageNum: 1,
      pageSize: 100
    })
    const ids = (page.list || []).filter((row) => ['DRAFT', 'REJECTED'].includes(row.status)).map((row) => row.id)
    if (!ids.length) {
      ElMessage.warning('当前范围内没有可提交的单据')
      return
    }
    const data = await submitPayrolls(ids)
    ElMessage.success(`已提交 ${data.submitted} 份工资单待审核`)
    fetchList()
    loadObjections()
  }

  async function handleBatchPublish() {
    try {
      await ElMessageBox.confirm(
        `将发布当前范围内全部已通过的工资单（共 ${approvedCount.value} 份）。发布后员工可见并需逐人确认，发布动作不可撤回。`,
        '批量发布工资单',
        { confirmButtonText: '确认发布', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch (e) {
      return
    }
    const data = await publishPayrolls({ month: query.month || undefined, stationId: query.stationId })
    ElMessage.success(`已发布 ${data.published} 份工资单${data.skipped ? `，跳过 ${data.skipped} 份状态不符的单据` : ''}`)
    fetchList()
  }

  /** 单份发布：与批量发布共用一份文案口径；republish=true 时文案改为「重新发布」（来源 OBJECTED） */
  function confirmPublish(ids, employeeName, republish = false) {
    const title = republish ? '确认重新发布' : '发布工资单'
    const message = republish
      ? `将重新发布 ${employeeName} 的工资单。发布后员工可见并需重新确认，不可撤回。`
      : `将发布 ${employeeName} 的工资单。发布后员工可见并需确认，发布动作不可撤回。`
    ElMessageBox.confirm(message, title, {
      confirmButtonText: republish ? '确认重新发布' : '确认发布',
      cancelButtonText: '再想想',
      type: 'warning'
    })
      .then(() => publishPayrolls({ ids }))
      .then((data) => {
        ElMessage.success(`${republish ? '已重新发布' : '已发布'} ${data.published} 份工资单`)
        closeDetail()
        fetchList()
      })
      .catch(() => {})
  }

  return { approveVisible, approving, openApprove, submitApprove, handleRowAction, handleBatchSubmit, handleBatchPublish }
}
