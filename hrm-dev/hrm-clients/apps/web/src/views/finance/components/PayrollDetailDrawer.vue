<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPayroll, updatePayrollItems } from '../../../api/finance.js'
import { PAYROLL_BILL_TYPE, PAYROLL_ITEM_SOURCE, PAYROLL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import PayrollStatusSteps from '../../../components/PayrollStatusSteps.vue'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 工资单详情抽屉（C4，B9.5）
 * 只读展示 + 人工项调整：状态流转由 PayrollStatusSteps 可视化，操作按钮按服务端下发的 actions 渲染，
 * 前端不维护第二份状态机（PAYROLL_ACTIONS 是唯一真源）。
 *
 * 人工项可改是本页唯一可写的部分：只有 MANUAL 来源项、且单据处于草稿/已驳回时才能调，
 * 其余金额一律由规则算出，避免「手改一下」把规则驱动的意义抹掉。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  payrollId: { type: Number, default: null },
  canWrite: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'action'])

const ACTION_TEXT = {
  submit: '提交审核',
  approve: '审核',
  publish: '审核通过并发布'
}

const loading = ref(false)
const error = ref(false)
const savingItems = ref(false)
const detail = ref(null)
const manualDraft = ref({})

async function load() {
  if (!props.payrollId) return
  loading.value = true
  error.value = false
  try {
    const data = await getPayroll(props.payrollId)
    detail.value = data
    manualDraft.value = {}
    ;(data.items || [])
      .filter((item) => item.source === 'MANUAL')
      .forEach((item) => {
        manualDraft.value[item.key] = item.amount
      })
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.payrollId],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

const editable = computed(() => !!detail.value && props.canWrite && ['DRAFT', 'REJECTED'].includes(detail.value.status))
const manualItems = computed(() => (detail.value ? detail.value.items.filter((item) => item.source === 'MANUAL') : []))
const actions = computed(() => (detail.value ? detail.value.actions || [] : []).filter((action) => ACTION_TEXT[action]))

async function saveManualItems() {
  if (!detail.value) return
  const items = manualItems.value.map((item) => ({ key: item.key, amount: Number(manualDraft.value[item.key]) }))
  if (items.some((item) => !Number.isFinite(item.amount))) {
    ElMessage.warning('人工项金额须为数字')
    return
  }
  savingItems.value = true
  try {
    await updatePayrollItems(detail.value.id, items)
    ElMessage.success('人工项已调整，合计已重算')
    await load()
  } finally {
    savingItems.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="工资单详情"
    size="min(var(--drawer-w-lg), 92vw)"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="工资单加载失败" @action="load" />

    <div v-else v-loading="loading" class="payroll-detail">
      <template v-if="detail">
        <PayrollStatusSteps :status="detail.status" :payroll="detail" />

        <div class="payroll-detail__summary">
          <div class="payroll-detail__cell">
            <span class="payroll-detail__cell-label">应发合计</span>
            <span class="payroll-detail__cell-value">{{ detail.grossAmount }}</span>
          </div>
          <div class="payroll-detail__cell">
            <span class="payroll-detail__cell-label">扣款合计</span>
            <span class="payroll-detail__cell-value">{{ detail.deductionTotal }}</span>
          </div>
          <div class="payroll-detail__cell">
            <span class="payroll-detail__cell-label">实发合计</span>
            <span class="payroll-detail__cell-value is-strong">{{ detail.netAmount }}</span>
          </div>
          <div class="payroll-detail__cell">
            <span class="payroll-detail__cell-label">单据状态</span>
            <StatusTag :dict="PAYROLL_STATUS" :value="detail.status" :variant="PAYROLL_STATUS[detail.status].variant" />
          </div>
        </div>

        <el-descriptions :column="2" size="small" border class="payroll-detail__meta">
          <el-descriptions-item label="单号">{{ detail.payrollNo }}</el-descriptions-item>
          <el-descriptions-item label="员工"
            >{{ detail.employeeName }}（{{ detail.stationName }}）</el-descriptions-item
          >
          <el-descriptions-item label="账期">{{ detail.month }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <StatusTag :dict="PAYROLL_BILL_TYPE" :value="detail.billType" variant="outline" />
          </el-descriptions-item>
          <el-descriptions-item label="计薪规则">{{ detail.ruleName }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.approveRemark" label="审核意见" :span="2">{{
            detail.approveRemark
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.objectionReason" label="员工异议" :span="2">{{
            detail.objectionReason
          }}</el-descriptions-item>
          <!-- 离职结算单与离职流程的引用关系（需求10 的「薪资结算」步骤） -->
          <el-descriptions-item v-if="detail.offboardingId" label="来源流程" :span="2">
            离职流程 #{{ detail.offboardingId }} 的薪资结算单
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="payroll-detail__title">金额构成</h4>
        <el-table :data="detail.items" size="small">
          <el-table-column prop="name" label="项目" min-width="110" />
          <el-table-column label="来源" width="100">
            <template #default="{ row }">{{ (PAYROLL_ITEM_SOURCE[row.source] || {}).label || row.source }}</template>
          </el-table-column>
          <el-table-column prop="detail" label="计算式" min-width="240" show-overflow-tooltip />
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">
              <!-- 人工项可改时用输入框就地调整，其余项只读（金额由规则算出） -->
              <el-input
                v-if="editable && row.source === 'MANUAL'"
                v-model="manualDraft[row.key]"
                size="small"
                type="number"
              />
              <span v-else :class="{ 'is-minus': row.type === 'DEDUCTION' }">
                {{ row.type === 'DEDUCTION' ? '-' : '' }}{{ Math.abs(Number(row.amount)) }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="editable && manualItems.length" class="payroll-detail__manual">
          <el-button size="small" type="primary" plain :loading="savingItems" @click="saveManualItems"
            >保存人工项</el-button
          >
          <span class="payroll-detail__hint">仅人工填写项可调整，其余金额由计薪规则计算得出。</span>
        </div>
        <p v-else-if="manualItems.length" class="payroll-detail__hint">
          当前状态（{{ detail.statusLabel }}）不允许修改金额，如需调整请先退回草稿。
        </p>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-for="action in actions"
        :key="action"
        type="primary"
        @click="emit('action', { action, row: detail })"
      >
        {{ ACTION_TEXT[action] }}
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.payroll-detail {
  min-height: 240px;

  &__summary {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--sp-3);
    margin: var(--sp-4) 0;
  }

  &__cell {
    display: flex;
    flex-direction: column;
    gap: var(--sp-1);
    padding: var(--sp-3);
    border: 1px solid var(--border-line);
    border-radius: var(--r-md);
    background-color: var(--surface-sub);
  }

  &__cell-label {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__cell-value {
    font-size: var(--fs-num-sm);
    font-weight: var(--fw-medium);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;

    &.is-strong {
      font-weight: var(--fw-semibold);
    }
  }

  &__meta {
    margin-bottom: var(--sp-4);
  }

  &__title {
    margin: var(--sp-4) 0 var(--sp-3);
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__manual {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-top: var(--sp-3);
  }

  &__hint {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  .is-minus {
    color: var(--state-danger-fg);
  }
}
</style>
