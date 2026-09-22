import { ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 排班批量工具的预览确认状态机
 *
 * 为什么不并入矩阵 composable：批量工具的「模式 × 预置目标 × 弹窗可见」是一台独立小状态机，
 * 与矩阵取数/保存无耦合；拆开后矩阵侧不必认识 batchMode 这类纯 UI 概念。
 * 一键铺排成功后矩阵会整体重载，故进弹窗前必须先清掉本地 dirty——
 * 否则重载会把未保存改动静默冲掉（B2.5 异常流）。
 */
export function useScheduleBatch({ canWrite, dirtyCount, confirmDiscard, discardChanges, rows, markDirty }) {
  // 批量工具（需求2）：mode 由页头下拉 / 行内列头锚点决定，seed 用于把锚点选中的行或列预置进弹窗
  const batchVisible = ref(false)
  const batchMode = ref('spread')
  const batchSeed = ref({ direction: 'row', employeeId: null, workDate: null })

  async function openBatchTools(mode, seed) {
    if (!canWrite.value) return
    if (dirtyCount.value) {
      if (!(await confirmDiscard())) return
      discardChanges()
    }
    batchMode.value = mode
    batchSeed.value = seed || { direction: 'row', employeeId: null, workDate: null }
    batchVisible.value = true
  }

  /** 页头下拉：一键铺排 / 复制上一周 / 整行整列 / 清空共用同一入口 */
  function handleBatchCommand(command) {
    openBatchTools(command)
  }

  /** 行首 / 列头锚点：预置方向与目标格，直接进「整行 / 整列批量设置」 */
  function openBatch(direction, employeeId = null, workDate = null) {
    openBatchTools('batch', { direction, employeeId, workDate })
  }

  /** 复制 / 整行整列 / 清空的结果落到本地 dirty，与逐格编辑共用同一套保存链路（B2.5 正常流 B） */
  function applyBatchChanges(changes) {
    let applied = 0
    changes.forEach((change) => {
      const row = rows.value.find((item) => item.employeeId === change.employeeId)
      const day = row && row.days.find((item) => item.workDate === change.workDate)
      if (!day) return
      day.shiftId = change.shiftId
      markDirty(row, day)
      applied += 1
    })
    ElMessage.success(`已应用 ${applied} 处改动，点「保存排班」后提交`)
  }

  return { batchVisible, batchMode, batchSeed, handleBatchCommand, openBatch, applyBatchChanges }
}
