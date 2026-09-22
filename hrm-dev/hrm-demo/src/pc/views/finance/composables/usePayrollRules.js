import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deletePayrollRule, getPayrollRules, updatePayrollRule } from '../../../api/finance.js'

/**
 * 计薪规则（Tab 2）：列表 + 启停 + 删除
 *
 * 规则的编辑表单在 PayrollRuleEditor 抽屉里，本 composable 只负责列表与两个不可逆动作（启停、删除）；
 * 启停/删除的确认文案带「已生成单据不受影响」的影响面说明（B0.3）。
 */
export function usePayrollRules() {
  const rules = ref([])
  const ruleLoading = ref(false)
  const ruleError = ref(false)
  const ruleVisible = ref(false)
  const editingRuleId = ref(null)

  let ruleSeq = 0

  async function loadRules() {
    const seq = (ruleSeq += 1)
    ruleLoading.value = true
    ruleError.value = false
    try {
      const data = await getPayrollRules()
      if (seq !== ruleSeq) return
      rules.value = data.list || []
    } catch (e) {
      if (seq !== ruleSeq) return
      ruleError.value = true
    } finally {
      if (seq === ruleSeq) ruleLoading.value = false
    }
  }

  /** 编辑传行，新建传 null：ruleId 为 null 时抽屉按空白规则渲染 */
  function openRule(row) {
    editingRuleId.value = row ? row.id : null
    ruleVisible.value = true
  }

  function handleToggleRule(row) {
    const next = row.status === 1 ? 0 : 1
    const confirmText =
      next === 1
        ? `启用「${row.ruleName}」后，新生成的工资单将按该规则计算；已生成的单据不受影响。`
        : `停用「${row.ruleName}」后，生成工资单时会找不到启用的规则；已生成的单据不受影响。`
    ElMessageBox.confirm(confirmText, next === 1 ? '启用计薪规则' : '停用计薪规则', {
      confirmButtonText: next === 1 ? '确认启用' : '确认停用',
      cancelButtonText: '再想想',
      type: 'warning'
    })
      .then(() => updatePayrollRule(row.id, { status: next }))
      .then(() => {
        ElMessage.success(next === 1 ? '规则已启用' : '规则已停用')
        loadRules()
      })
      .catch(() => {})
  }

  function handleDeleteRule(row) {
    ElMessageBox.confirm(
      `删除规则「${row.ruleName}」不可撤回；已被工资单引用的规则不允许删除，只能停用。`,
      '删除计薪规则',
      { confirmButtonText: '确认删除', cancelButtonText: '再想想', type: 'warning' }
    )
      .then(() => deletePayrollRule(row.id))
      .then(() => {
        ElMessage.success('规则已删除')
        loadRules()
      })
      .catch(() => {})
  }

  return { rules, ruleLoading, ruleError, ruleVisible, editingRuleId, loadRules, openRule, handleToggleRule, handleDeleteRule }
}
