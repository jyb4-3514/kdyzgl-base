<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getEmployees } from '@/api/employee.js'
import { getPayrolls, getPayrollRules, generatePayrolls } from '../../../api/finance.js'

/**
 * 生成工资单弹窗（B9.7 正常流第一步）
 *
 * 两处「先查再生成」的前置校验，都是为了不出现「点了才知道不行」：
 * 1. 该月已存在「已提交审核 / 已发布」的单据 → 服务端会整批拒绝（9405），前端提前拦下并说明原因；
 * 2. 当前筛选范围命中 0 名在职员工 → 直接禁用提交，避免生成一个空批次。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] },
  departments: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'generated'])

/** 可被 generate 覆盖重建的状态（与 financeStore 的 IS_OVERWRITABLE = isOverwritable 同口径，勿与 isItemEditable 混用） */
const OVERWRITABLE_STATUS = ['DRAFT', 'REJECTED']

const currentMonth = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const form = ref({ month: currentMonth(), stationId: undefined, deptId: undefined, ruleId: null })
const rules = ref([])
const employeeCount = ref(null)
const blockedPayroll = ref(null)
const checking = ref(false)
const submitting = ref(false)

const enabledRule = computed(() => rules.value.find((rule) => rule.status === 1) || null)

/** 前置校验：按月查已有单据，命中不可编辑状态就锁定提交 */
async function precheck() {
  if (!form.value.month) return
  checking.value = true
  blockedPayroll.value = null
  employeeCount.value = null
  try {
    const [page, employeePage] = await Promise.all([
      getPayrolls({ month: form.value.month, pageNum: 1, pageSize: 100 }),
      getEmployees({ stationId: form.value.stationId, deptId: form.value.deptId, pageNum: 1, pageSize: 1 })
    ])
    blockedPayroll.value = (page.list || []).find((row) => !OVERWRITABLE_STATUS.includes(row.status)) || null
    employeeCount.value = employeePage.total || 0
  } catch (e) {
    blockedPayroll.value = null
  } finally {
    checking.value = false
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    form.value = { month: currentMonth(), stationId: undefined, deptId: undefined, ruleId: null }
    if (!rules.value.length) {
      const list = await getPayrollRules().catch(() => ({ list: [] }))
      rules.value = list.list || []
    }
    form.value.ruleId = enabledRule.value ? enabledRule.value.id : null
    precheck()
  },
  { immediate: true }
)

const disabledReason = computed(() => {
  if (!form.value.month) return '请选择账期月份'
  if (!form.value.ruleId) return '未找到启用的计薪规则，请先在「计算规则」里配置并启用'
  if (blockedPayroll.value) return `该月工资单已提交审核或已发布（${blockedPayroll.value.payrollNo}），不可重复生成`
  if (employeeCount.value === 0) return '当前范围内没有在职员工，生成后会得到空批次'
  return ''
})

const canSubmit = computed(() => !disabledReason.value && !checking.value)

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning(disabledReason.value)
    return
  }
  submitting.value = true
  try {
    const data = await generatePayrolls({
      month: form.value.month,
      stationId: form.value.stationId || undefined,
      deptId: form.value.deptId || undefined,
      ruleId: form.value.ruleId
    })
    ElMessage.success(`已按「${data.ruleName}」生成 ${data.created} 份草稿工资单`)
    emit('generated')
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="生成工资单"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert
      v-if="blockedPayroll"
      class="gen-payroll__alert"
      type="error"
      show-icon
      :closable="false"
      title="该月已有不可覆盖的工资单"
      :description="`已存在 ${blockedPayroll.statusLabel} 的单据（${blockedPayroll.payrollNo}），同一个月的工资单不可重复生成，请换一个账期或先处理已有单据。`"
    />
    <el-alert
      v-else
      class="gen-payroll__alert"
      type="info"
      show-icon
      :closable="false"
      title="生成的是草稿"
      description="同月已有的草稿或已驳回单据会被覆盖重建，生成后可在列表里逐人调整人工项。"
    />

    <el-form label-position="top">
      <div class="gen-payroll__grid">
        <el-form-item label="账期月份" required>
          <el-date-picker
            v-model="form.month"
            type="month"
            value-format="YYYY-MM"
            :clearable="false"
            style="width: 100%"
            @change="precheck"
          />
        </el-form-item>
        <el-form-item label="计薪规则" required>
          <el-select v-model="form.ruleId" style="width: 100%" placeholder="选择启用的规则">
            <el-option
              v-for="item in rules"
              :key="item.id"
              :value="item.id"
              :label="`${item.ruleName}（${item.statusLabel}）`"
              :disabled="item.status !== 1"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="驿站（留空=全部）">
          <el-select v-model="form.stationId" clearable style="width: 100%" placeholder="全部驿站" @change="precheck">
            <el-option v-for="item in stations" :key="item.id" :value="item.id" :label="item.stationName" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门（留空=全部）">
          <el-select v-model="form.deptId" clearable style="width: 100%" placeholder="全部部门" @change="precheck">
            <el-option v-for="item in departments" :key="item.id" :value="item.id" :label="item.deptName" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>

    <p class="gen-payroll__hint">
      本次将覆盖
      <strong>{{ employeeCount === null ? '—' : employeeCount }}</strong>
      名在职员工的 {{ form.month }} 工资单草稿。
    </p>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">再想想</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        :title="disabledReason"
        @click="handleSubmit"
        >确认生成</el-button
      >
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.gen-payroll {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 var(--sp-3);
  }

  &__hint {
    margin: 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
