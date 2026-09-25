<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getHrProfile, getHrSalary, updateHrSalary } from '../../../api/hr.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/** 调薪类型字典：契约的 changeType 只有 ENTRY / ADJUST 两个值，就近声明避免污染全局字典 */
const CHANGE_TYPE = {
  ENTRY: { label: '入职定薪', type: 'primary' },
  ADJUST: { label: '调薪', type: 'info' }
}

/**
 * 员工定薪抽屉（需求8 核心，B8.4）
 *
 * 三条硬约定：
 * 1. 「合计」只是标准薪资，实发以财务模块的工资单为准——两处口径不能在页面上打架（B8.4 政策说明）；
 * 2. 降薪（调整后合计 < 调整前）必须二次确认，升薪不拦（B8.6）；
 * 3. 员工已离职（人事档案 leaveDate 非空）时全表单只读，不让用户「填完才被 9302 拒」。
 *
 * 薪资标准 / 员工薪资的派生关系在抽屉里显式说明：标准是模板，员工薪资派生后可单独调整。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  employeeId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const error = ref(false)
const saving = ref(false)

const salary = ref(null)
const histories = ref([])
const profile = ref(null)

const form = ref({
  basicSalary: 0,
  postSalary: 0,
  performanceBase: 0,
  allowances: [],
  effectiveDate: '',
  reason: ''
})

/** 生效日期默认下月 1 日：调薪次月生效是行业惯例，也避免误改当月已生成的工资单 */
function nextMonthFirstDay() {
  const now = new Date()
  const next = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  return `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-01`
}

async function load() {
  if (!props.employeeId) return
  loading.value = true
  error.value = false
  try {
    const [detail, profileDetail] = await Promise.all([
      getHrSalary(props.employeeId),
      getHrProfile(props.employeeId).catch(() => null)
    ])
    salary.value = detail.current
    histories.value = detail.histories || []
    profile.value = profileDetail
    form.value = {
      basicSalary: detail.current.basicSalary,
      postSalary: detail.current.postSalary,
      performanceBase: detail.current.performanceBase,
      allowances: (detail.current.allowances || []).map((item) => ({ ...item })),
      effectiveDate: nextMonthFirstDay(),
      reason: ''
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.employeeId],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

const resigned = computed(() => !!(profile.value && profile.value.leaveDate))
const readOnly = computed(() => resigned.value)

const allowancesTotal = computed(() => form.value.allowances.reduce((sum, item) => sum + Number(item.amount || 0), 0))
const totalSalary = computed(
  () =>
    Number(form.value.basicSalary || 0) +
    Number(form.value.postSalary || 0) +
    Number(form.value.performanceBase || 0) +
    allowancesTotal.value
)
const currentTotal = computed(() => (salary.value ? Number(salary.value.totalSalary || 0) : 0))
const delta = computed(() => totalSalary.value - currentTotal.value)

/** 金额校验：非负整数，且不超过 6 位（超过 6 位基本可以断定是误输入） */
function amountError(value, label) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) return `${label}须为不小于 0 的数字`
  if (!Number.isInteger(num)) return `${label}须为整数金额`
  if (String(Math.trunc(num)).length > 6) return `${label}数额过大，请核对`
  return ''
}

const formError = computed(() => {
  const basic = amountError(form.value.basicSalary, '基本工资')
  if (basic) return basic
  const post = amountError(form.value.postSalary, '岗位工资')
  if (post) return post
  const perf = amountError(form.value.performanceBase, '绩效基数')
  if (perf) return perf
  for (const item of form.value.allowances) {
    if (!item.name || !String(item.name).trim()) return '津贴项名称不可为空'
    const err = amountError(item.amount, `津贴「${item.name}」`)
    if (err) return err
  }
  if (!form.value.effectiveDate) return '请选择生效日期'
  const reason = String(form.value.reason || '').trim()
  if (reason.length < 2 || reason.length > 50) return '调整原因须为 2-50 字'
  if (salary.value && form.value.effectiveDate < salary.value.effectiveDate)
    return `生效日期不能早于当前生效日期（${salary.value.effectiveDate}）`
  return ''
})

const canSubmit = computed(() => !readOnly.value && !formError.value)

function addAllowance() {
  form.value.allowances.push({ key: '', name: '', amount: 0 })
}

function removeAllowance(index) {
  form.value.allowances.splice(index, 1)
}

/** 降薪必须二次确认；文案按 B0.3：动词标题 + 影响范围 + 具体动词按钮 */
async function confirmIfCut() {
  if (delta.value >= 0) return true
  try {
    await ElMessageBox.confirm(
      `本次为降薪调整（${delta.value} 元），调整后标准薪资合计 ${totalSalary.value} 元，将于 ${form.value.effectiveDate} 生效；调薪留痕不可撤回。`,
      '确认降薪调整',
      { confirmButtonText: '确认降薪', cancelButtonText: '再想想', type: 'warning' }
    )
    return true
  } catch (e) {
    return false
  }
}

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning(formError.value)
    return
  }
  if (!(await confirmIfCut())) return
  saving.value = true
  try {
    await updateHrSalary(props.employeeId, {
      basicSalary: Number(form.value.basicSalary),
      postSalary: Number(form.value.postSalary),
      performanceBase: Number(form.value.performanceBase),
      allowances: form.value.allowances.map((item) => ({
        key: item.key || null,
        name: String(item.name).trim(),
        amount: Number(item.amount)
      })),
      effectiveDate: form.value.effectiveDate,
      reason: String(form.value.reason).trim()
    })
    ElMessage.success(`薪资已保存，${form.value.effectiveDate} 生效`)
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="员工薪资"
    size="min(var(--drawer-w-lg), 92vw)"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="薪资档案加载失败" @action="load" />

    <div v-else v-loading="loading" class="salary-editor">
      <template v-if="salary">
        <el-alert
          v-if="resigned"
          class="salary-editor__alert"
          type="warning"
          show-icon
          :closable="false"
          title="员工已离职，不可调整薪资"
          description="档案处于只读状态；如需调整请先处理离职流程的归档。"
        />

        <section class="salary-editor__block">
          <h3 class="salary-editor__title">基本信息</h3>
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="姓名">{{ salary.employeeName }}</el-descriptions-item>
            <el-descriptions-item label="驿站">{{ (profile && profile.stationName) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ (profile && profile.deptName) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="当前生效日">{{ salary.effectiveDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="当前合计">{{ currentTotal }} 元</el-descriptions-item>
            <el-descriptions-item label="最近更新">{{ salary.updateTime }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="salary-editor__block">
          <h3 class="salary-editor__title">薪资构成</h3>
          <el-form label-position="top" :disabled="readOnly">
            <div class="salary-editor__grid">
              <el-form-item label="基本工资（元）" required>
                <el-input v-model="form.basicSalary" type="number" :min="0" />
              </el-form-item>
              <el-form-item label="岗位工资（元）" required>
                <el-input v-model="form.postSalary" type="number" :min="0" />
              </el-form-item>
              <el-form-item label="绩效基数（元）" required>
                <el-input v-model="form.performanceBase" type="number" :min="0" />
                <p class="salary-editor__hint">绩效工资 = 绩效基数 × KPI 得分（财务模块的计薪规则决定）</p>
              </el-form-item>
            </div>

            <div class="salary-editor__allowance-head">
              <span class="salary-editor__label">津贴项</span>
              <el-button size="small" :icon="Plus" :disabled="readOnly" @click="addAllowance">添加津贴</el-button>
            </div>
            <p v-if="!form.allowances.length" class="salary-editor__hint">
              暂无津贴项，合计 = 基本工资 + 岗位工资 + 绩效基数
            </p>
            <div v-for="(item, index) in form.allowances" :key="index" class="salary-editor__allowance">
              <el-input v-model="item.name" maxlength="20" placeholder="津贴名称" />
              <el-input v-model="item.amount" type="number" :min="0" placeholder="金额" />
              <el-button link type="danger" :disabled="readOnly" @click="removeAllowance(index)">删除</el-button>
            </div>

            <div class="salary-editor__total">
              <span>标准薪资合计</span>
              <span class="salary-editor__total-value">{{ totalSalary }} 元</span>
              <span v-if="delta !== 0" class="salary-editor__delta" :class="delta < 0 ? 'is-down' : 'is-up'">
                {{ delta > 0 ? '+' : '' }}{{ delta }} 元
              </span>
            </div>
            <p class="salary-editor__hint">此处合计仅为标准薪资，实发金额以「财务管理 → 工资单」的计算结果为准。</p>
          </el-form>
        </section>

        <section class="salary-editor__block">
          <h3 class="salary-editor__title">生效设置</h3>
          <el-form label-position="top" :disabled="readOnly">
            <div class="salary-editor__grid">
              <el-form-item label="生效日期" required>
                <el-date-picker
                  v-model="form.effectiveDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :clearable="false"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="调整原因" required>
                <el-input v-model="form.reason" maxlength="50" show-word-limit placeholder="如：转正调薪 / 年度调薪" />
              </el-form-item>
            </div>
          </el-form>
        </section>

        <section class="salary-editor__block">
          <h3 class="salary-editor__title">调薪留痕（最近 3 条）</h3>
          <StateBlock
            v-if="!histories.length"
            variant="empty"
            title="暂无调薪记录"
            description="本次调整将是第一条留痕"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="item in histories.slice(0, 3)"
              :key="item.id"
              :timestamp="`${item.effectiveDate} · ${item.operatorName || '系统'}`"
              :type="item.changeType === 'ENTRY' ? 'primary' : 'success'"
            >
              <p class="salary-editor__history">
                <StatusTag :dict="CHANGE_TYPE" :value="item.changeType" variant="outline" />
                <span
                  >{{ item.totalSalary }} 元（基本 {{ item.basicSalary }} / 岗位 {{ item.postSalary }} / 绩效
                  {{ item.performanceBase }} / 津贴 {{ item.allowancesTotal }}）</span
                >
              </p>
              <p class="salary-editor__hint">{{ item.reason }}</p>
            </el-timeline-item>
          </el-timeline>
          <p class="salary-editor__hint">调薪留痕只增不改，历史金额与生效日期不可编辑。</p>
        </section>

        <p class="salary-editor__note">
          薪资标准是派生模板：修改标准不会自动改变已建档员工的薪资，需在员工薪资里单独调整。
        </p>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="!readOnly"
        type="primary"
        :loading="saving"
        :disabled="!canSubmit"
        :title="formError || '保存后生成一条调薪留痕'"
        @click="handleSubmit"
      >
        保存薪资
      </el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.salary-editor {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__block {
    margin-bottom: var(--sp-6);
  }

  &__title {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-h3);
    color: var(--text-1);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0 var(--sp-3);

    @media (max-width: 992px) {
      grid-template-columns: 1fr;
    }
  }

  &__allowance-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--sp-2);
  }

  &__label {
    font-size: var(--fs-body);
    color: var(--text-2);
  }

  &__allowance {
    display: grid;
    grid-template-columns: 2fr 1fr 56px;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-2);
  }

  &__total {
    display: flex;
    align-items: baseline;
    gap: var(--sp-3);
    margin-top: var(--sp-3);
    padding: var(--sp-3);
    border-radius: var(--r-md);
    background-color: var(--surface-sub);
    font-size: var(--fs-body);
    color: var(--text-2);
  }

  &__total-value {
    font-size: var(--fs-num-md);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__delta {
    font-size: var(--fs-caption);

    &.is-down {
      color: var(--state-danger-fg);
    }

    &.is-up {
      color: var(--state-success-fg);
    }
  }

  &__history {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin: 0;
    font-size: var(--fs-body);
    color: var(--text-2);
  }

  &__hint {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__note {
    margin: 0;
    padding: var(--sp-3);
    border-left: 3px solid var(--state-warning-border);
    border-radius: var(--r-xs);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
  }
}
</style>
