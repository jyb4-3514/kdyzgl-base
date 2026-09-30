<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addPayrollItem, getPayroll, getPayrollLogs, updatePayrollItems } from '../../../api/finance.js'
import {
  PAYROLL_BILL_TYPE,
  PAYROLL_ITEM_SOURCE,
  PAYROLL_LOG_ACTION,
  PAYROLL_STATUS
} from '@kdyzgl/shared/constants/dict.js'
import PayrollStatusSteps from '../../../components/PayrollStatusSteps.vue'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 工资单详情抽屉（C4，B9.5）
 * 只读展示 + 人工项调整 + 加扣款 + 操作留痕：状态流转由 PayrollStatusSteps 可视化，
 * 操作按钮按服务端下发的 actions 渲染，前端不维护第二份状态机（PAYROLL_ACTIONS 是唯一真源）。
 *
 * 人工项可改是本页唯一可写的金额部分：只有 MANUAL 来源项、且单据处于 isItemEditable 状态时才能调
 * （DRAFT / REJECTED / PENDING_APPROVAL / OBJECTED，见 §2.3），其余金额一律由规则算出。
 * 保存金额与加扣款均须填「事由」（C-3 / I-6 必填 2–200，服务端 9412 兜底）。
 * 留痕字段由服务端按角色裁剪：本端为 ADMIN，渲染操作人 / 状态变化 / 金额快照。
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
  publish: '发布给员工',
  pay: '确认工资已发放'
}

/** 明细金额可编辑状态（镜像后端 isItemEditable，勿再退回单一 DRAFT/REJECTED 口径） */
const ITEM_EDITABLE_STATUS = ['DRAFT', 'REJECTED', 'PENDING_APPROVAL', 'OBJECTED']

const loading = ref(false)
const error = ref(false)
const savingItems = ref(false)
const detail = ref(null)
const manualDraft = ref({})

/* ---- 加款 / 扣款（I-6） ---- */
const addVisible = ref(false)
const savingAdd = ref(false)
const addError = ref('')
const addForm = ref({ itemType: 'ADDITION', itemName: '', amount: '', reason: '' })

/* ---- 操作留痕（I-7） ---- */
const logs = ref([])
const logsLoading = ref(false)
const logsError = ref(false)

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
  loadLogs()
}

async function loadLogs() {
  if (!props.payrollId) return
  logsLoading.value = true
  logsError.value = false
  try {
    logs.value = await getPayrollLogs(props.payrollId)
  } catch (e) {
    logsError.value = true
  } finally {
    logsLoading.value = false
  }
}

watch(
  () => [props.modelValue, props.payrollId],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

const editable = computed(() => !!detail.value && props.canWrite && ITEM_EDITABLE_STATUS.includes(detail.value.status))
const manualItems = computed(() => (detail.value ? detail.value.items.filter((item) => item.source === 'MANUAL') : []))
const actions = computed(() => (detail.value ? detail.value.actions || [] : []).filter((action) => ACTION_TEXT[action]))
const isArchived = computed(() => !!detail.value && detail.value.status === 'PAID')

/** 动作文案：来源 OBJECTED 时 publish 显示「重新发布」，与首发区分（C-2） */
function actionLabel(action) {
  if (action === 'publish' && detail.value && detail.value.status === 'OBJECTED') return '重新发布'
  return ACTION_TEXT[action] || action
}

async function saveManualItems() {
  if (!detail.value) return
  const items = manualItems.value.map((item) => ({ key: item.key, amount: Number(manualDraft.value[item.key]) }))
  if (items.some((item) => !Number.isFinite(item.amount))) {
    ElMessage.warning('人工项金额须为数字')
    return
  }
  // C-3：金额变更事由必填 2–200 字，写入服务端留痕
  let reason
  try {
    const { value } = await ElMessageBox.prompt('金额变更事由必填（2–200 字），将写入操作留痕', '保存人工项', {
      confirmButtonText: '确认保存',
      cancelButtonText: '再想想',
      inputType: 'textarea',
      inputPlaceholder: '例如：9 月绩效补偿调整'
    })
    reason = String(value == null ? '' : value).trim()
    if (reason.length < 2 || reason.length > 200) {
      ElMessage.warning('金额变更事由必填（2–200 字）')
      return
    }
  } catch (e) {
    // 用户取消：静默返回，不提示
    return
  }
  savingItems.value = true
  try {
    await updatePayrollItems(detail.value.id, items, reason)
    ElMessage.success('人工项已调整，合计已重算')
    await load()
  } finally {
    savingItems.value = false
  }
}

function openAdd() {
  addForm.value = { itemType: 'ADDITION', itemName: '', amount: '', reason: '' }
  addError.value = ''
  addVisible.value = true
}

/** 加 / 扣款（I-6）：方向、名称、金额、事由（必填 2–200）前端先校验，服务端 9411/9412/9413 兜底 */
async function submitAdd() {
  const form = addForm.value
  const name = form.itemName.trim()
  if (name.length < 2 || name.length > 20) {
    addError.value = '请填写名称（2–20 字）'
    return
  }
  const amount = Number(form.amount)
  if (!Number.isFinite(amount) || amount <= 0) {
    addError.value = '金额须为大于 0 的数字'
    return
  }
  const reason = form.reason.trim()
  if (reason.length < 2 || reason.length > 200) {
    addError.value = '加扣款事由必填（2–200 字）'
    return
  }
  savingAdd.value = true
  try {
    await addPayrollItem(detail.value.id, { itemType: form.itemType, itemName: name, amount, reason })
    ElMessage.success('已添加并重算合计')
    addVisible.value = false
    await load()
  } catch (e) {
    if (e.code === 9411) addError.value = '该明细已存在，请刷新后重试'
    else if (e.code === 9412) addError.value = '加扣款事由必填（2–200 字）'
    else if (e.code === 9413) addError.value = '该工资单已发放归档，不可修改'
    else addError.value = e.message || '添加失败'
  } finally {
    savingAdd.value = false
  }
}

/* ==================== 留痕渲染（I-7，ADMIN 视角） ==================== */

const AMOUNT_FIELDS = [
  { key: 'grossAmount', label: '应发' },
  { key: 'deductionTotal', label: '扣项' },
  { key: 'netAmount', label: '实发' }
]

function actionText(action) {
  return (PAYROLL_LOG_ACTION[action] || {}).label || action
}

function statusChange(log) {
  if (log.fromStatus && log.toStatus) {
    return `${(PAYROLL_STATUS[log.fromStatus] || {}).label || log.fromStatus} → ${(PAYROLL_STATUS[log.toStatus] || {}).label || log.toStatus}`
  }
  if (log.toStatus) return (PAYROLL_STATUS[log.toStatus] || {}).label || log.toStatus
  return ''
}

/** 金额对比：明细级逐项配对（仅变动项）+ 合计级（仅变动项）；before/after 缺失则不渲染 */
function amountLines(log) {
  const lines = []
  const before = log.before || {}
  const after = log.after || {}
  const beforeItems = before.items || []
  after.items?.forEach((item) => {
    const prev = beforeItems.find((row) => row.itemKey === item.itemKey)
    if (prev && Number(prev.amount) !== Number(item.amount)) {
      lines.push(`${item.itemName}：${prev.amount} → ${item.amount}`)
    }
  })
  AMOUNT_FIELDS.forEach((field) => {
    if (before[field.key] !== undefined && after[field.key] !== undefined && Number(before[field.key]) !== Number(after[field.key])) {
      lines.push(`${field.label}：${before[field.key]} → ${after[field.key]}`)
    }
  })
  return lines
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
        <el-alert
          v-if="isArchived"
          type="info"
          :closable="false"
          show-icon
          class="payroll-detail__frozen"
          title="该工资单已发放并归档，不可修改"
          :description="`发放人 ${detail.paidByName || '-'} · 发放时间 ${detail.paidTime || '-'}`"
        />
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
            <StatusTag
              :dict="PAYROLL_STATUS"
              :value="detail.status"
              :variant="(PAYROLL_STATUS[detail.status] || {}).variant"
            />
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

        <div v-if="editable" class="payroll-detail__manual">
          <el-button
            v-if="manualItems.length"
            size="small"
            type="primary"
            plain
            :loading="savingItems"
            @click="saveManualItems"
            >保存人工项</el-button
          >
          <el-button size="small" type="primary" plain @click="openAdd">加款 / 扣款</el-button>
          <span class="payroll-detail__hint">仅人工填写项可调整；加扣款会分别计入应发 / 扣项并自动重算合计。</span>
        </div>
        <p v-else-if="manualItems.length" class="payroll-detail__hint">
          当前状态（{{ detail.statusLabel }}）不允许修改金额，如需调整请先退回草稿。
        </p>

        <h4 class="payroll-detail__title">操作留痕</h4>
        <div class="payroll-detail__logs">
          <el-skeleton v-if="logsLoading" :rows="3" animated />
          <StateBlock v-else-if="logsError" variant="error" title="操作留痕加载失败" @action="loadLogs" />
          <el-empty v-else-if="!logs.length" description="暂无操作留痕" :image-size="72" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="log in logs"
              :key="log.id"
              :timestamp="`${log.time} · ${log.operatorName}（${log.operatorRole}）`"
              placement="top"
            >
              <span class="log-tag">{{ actionText(log.action) }}</span>
              <span v-if="statusChange(log)" class="log-status">{{ statusChange(log) }}</span>
              <ul v-if="amountLines(log).length" class="log-amounts">
                <li v-for="(line, index) in amountLines(log)" :key="index">{{ line }}</li>
              </ul>
              <p v-if="log.reason" class="log-reason">事由：{{ log.reason }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>
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
        {{ actionLabel(action) }}
      </el-button>
    </template>
  </el-drawer>

  <!-- 加款 / 扣款（I-6）：作为抽屉外兄弟节点，避免与抽屉层叠冲突 -->
  <el-dialog v-model="addVisible" title="加款 / 扣款" width="480px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      title="加款计入应发合计，扣款计入扣项合计；保存后自动重算四项合计。"
      class="add-alert"
    />
    <el-form label-width="72px" class="add-form">
      <el-form-item label="方向" required>
        <el-radio-group v-model="addForm.itemType">
          <el-radio value="ADDITION">加款</el-radio>
          <el-radio value="DEDUCTION">扣款</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="addForm.itemName" maxlength="20" placeholder="2–20 字，例如：设备赔偿" />
      </el-form-item>
      <el-form-item label="金额" required>
        <el-input v-model="addForm.amount" type="number" placeholder="大于 0，最多 2 位小数" />
      </el-form-item>
      <el-form-item label="事由" required>
        <el-input
          v-model="addForm.reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="必填，2–200 字"
        />
      </el-form-item>
      <p v-if="addError" class="add-error" role="alert">{{ addError }}</p>
    </el-form>
    <template #footer>
      <el-button @click="addVisible = false">再想想</el-button>
      <el-button type="primary" :loading="savingAdd" @click="submitAdd">确认添加</el-button>
    </template>
  </el-dialog>
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

  &__frozen {
    margin-bottom: var(--sp-4);
  }

  &__logs {
    min-height: 96px;
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

.log-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  color: var(--text-2);
  background: var(--surface-sub);
  border-radius: var(--r-full);
}

.log-status {
  margin-left: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.log-amounts {
  padding-left: var(--sp-4);
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.log-reason {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.add-alert {
  margin-bottom: var(--sp-3);
}

.add-form {
  :deep(.el-form-item) {
    margin-bottom: var(--sp-4);
  }
}

.add-error {
  margin: 0;
  font-size: var(--fs-caption);
  color: var(--color-danger);
}
</style>
