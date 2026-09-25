<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getPayrollRule, createPayrollRule, updatePayrollRule } from '../../../api/finance.js'
import { getEmployees } from '@/api/employee.js'
import { getHrSalaries } from '../../../api/hr.js'
import { getKpiScores } from '../../../api/kpi.js'
import {
  ATTENDANCE_METRIC,
  ATTENDANCE_MODE,
  PAYROLL_ITEM_SOURCE,
  PAYROLL_ITEM_TYPE,
  SALARY_FIELD
} from '@kdyzgl/shared/constants/dict.js'
import { buildPreview, loadAttendanceStat } from '../../../utils/payrollPreview.js'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 计薪规则编辑器（需求9 的核心，B9.4）
 *
 * 设计原则：不让管理员写公式串，只让他搭积木 —— 每个工资项目 = 方向 + 数据来源 + 计算参数，
 * 参数全是系统里真实存在的数据项（人事定薪 / 考勤指标 / KPI 得分 / 人工填写），不给自由输入。
 * 这样「换一项津贴、改一次扣款标准」都只是改配置，永远不会出现无法解析的表达式。
 *
 * 试算预览复用 utils/payrollPreview.js 的算薪镜像：改完规则立刻能看到某员工逐项拆解与实发金额，
 * 且不落库（数据来源与 financeStore 的算薪内核同口径，见该文件头的保真约定）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  ruleId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const error = ref(false)
const saving = ref(false)

const form = ref({ ruleName: '', status: 1, remark: '' })
const items = ref([])
let keySeed = 0

/** 逐个来源补齐默认参数：切换来源时也要走一遍，避免留下另一种来源的脏参数 */
function ensureParams(item) {
  const params = item.params || {}
  if (item.source === 'FIXED') {
    item.params = {
      field: params.field || 'basicSalary',
      ...(params.allowanceKey ? { allowanceKey: params.allowanceKey } : {})
    }
  } else if (item.source === 'ATTENDANCE') {
    item.params = {
      metric: params.metric || 'LATE',
      mode: params.mode || 'PER_COUNT',
      amount: params.amount === undefined ? 0 : params.amount,
      ...(params.cap === undefined ? {} : { cap: params.cap })
    }
  } else if (item.source === 'KPI') {
    item.params = {
      baseField: params.baseField || 'performanceBase',
      capRatio: params.capRatio === undefined ? 1 : params.capRatio
    }
  } else {
    item.params = { defaultValue: params.defaultValue === undefined ? 0 : params.defaultValue }
  }
}

function blankItem(index) {
  keySeed += 1
  const item = {
    key: `ITEM_${keySeed}`,
    name: '',
    type: 'ADDITION',
    source: 'FIXED',
    params: {},
    enabled: true,
    sortOrder: index + 1
  }
  ensureParams(item)
  return item
}

async function load() {
  loading.value = true
  error.value = false
  try {
    if (props.ruleId) {
      const detail = await getPayrollRule(props.ruleId)
      form.value = { ruleName: detail.ruleName, status: detail.status, remark: detail.remark || '' }
      items.value = (detail.items || []).map((item) => {
        const row = {
          key: item.key,
          name: item.name,
          type: item.type,
          source: item.source,
          params: { ...item.params },
          enabled: item.enabled === 1,
          sortOrder: item.sortOrder
        }
        ensureParams(row)
        return row
      })
    } else {
      form.value = { ruleName: '', status: 1, remark: '' }
      items.value = [blankItem(0)]
    }
    preview.value = null
    previewError.value = false
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.ruleId],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

function addItem() {
  if (items.value.length >= 20) {
    ElMessage.warning('工资项目最多 20 项')
    return
  }
  items.value.push(blankItem(items.value.length))
}

function removeItem(index) {
  items.value.splice(index, 1)
}

/** 排序用上移/下移：不引入拖拽依赖，顺序即 sortOrder（服务端按 sortOrder 依次算薪） */
function moveItem(index, step) {
  const target = index + step
  if (target < 0 || target >= items.value.length) return
  const list = items.value
  const current = list[index]
  list[index] = list[target]
  list[target] = current
}

function handleSourceChange(item) {
  ensureParams(item)
}

const enabledCount = computed(() => items.value.filter((item) => item.enabled).length)

const submitDisabledReason = computed(() => {
  const name = String(form.value.ruleName || '').trim()
  if (name.length < 2 || name.length > 50) return '规则名称须为 2-50 字'
  if (!items.value.length) return '至少添加一个工资项目'
  if (!enabledCount.value) return '至少启用一个工资项目'
  if (items.value.some((item) => !String(item.name || '').trim() || String(item.name).trim().length > 20))
    return '项目名称须为 1-20 字'
  return ''
})

const canSubmit = computed(() => !submitDisabledReason.value)

function payloadItems() {
  return items.value.map((item, index) => ({
    key: item.key,
    name: String(item.name).trim(),
    type: item.type,
    source: item.source,
    params: { ...item.params },
    enabled: item.enabled ? 1 : 0,
    sortOrder: index + 1
  }))
}

async function handleSave() {
  if (!canSubmit.value) {
    ElMessage.warning(submitDisabledReason.value)
    return
  }
  saving.value = true
  try {
    const body = {
      ruleName: String(form.value.ruleName).trim(),
      status: form.value.status,
      remark: form.value.remark || null,
      items: payloadItems()
    }
    if (props.ruleId) await updatePayrollRule(props.ruleId, body)
    else await createPayrollRule(body)
    ElMessage.success('计薪规则已保存')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

/* ==================== 试算预览 ==================== */

const employees = ref([])
const previewMonth = ref('')
const previewEmployeeId = ref(null)
const previewLoading = ref(false)
const previewError = ref(false)
const preview = ref(null)

/** 默认试算上一个自然月：当月工资单多半已生成，回看上月更符合「核对我算得对不对」的场景 */
function prevMonth() {
  const now = new Date()
  const date = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

const previewEmployee = computed(() => employees.value.find((item) => item.id === previewEmployeeId.value) || null)

async function handlePreview() {
  if (!previewEmployeeId.value || !previewMonth.value) {
    ElMessage.warning('请先选择试算月份与员工')
    return
  }
  previewLoading.value = true
  previewError.value = false
  try {
    const employee = previewEmployee.value
    const [salaryPage, scorePage] = await Promise.all([
      getHrSalaries({ pageNum: 1, pageSize: 100 }),
      getKpiScores({ month: previewMonth.value, pageNum: 1, pageSize: 100 })
    ])
    const salaryRow = (salaryPage.list || []).find((row) => row.employeeId === previewEmployeeId.value) || null
    const scoreRow = (scorePage.list || []).find((row) => row.employeeId === previewEmployeeId.value) || null
    // 缺勤需要排班真源，只在规则里真的用到「缺勤」指标时才多打几周请求
    const needAbsent = items.value.some(
      (item) => item.enabled && item.source === 'ATTENDANCE' && item.params.metric === 'ABSENT'
    )
    const attendance = await loadAttendanceStat({
      employeeId: previewEmployeeId.value,
      stationId: employee ? employee.stationId : null,
      month: previewMonth.value,
      needAbsent
    })
    preview.value = buildPreview(payloadItems(), {
      salary: salaryRow,
      attendance,
      kpiScore: scoreRow ? scoreRow.totalScore : null
    })
  } catch (e) {
    previewError.value = true
  } finally {
    previewLoading.value = false
  }
}

async function loadEmployees() {
  const page = await getEmployees({ pageNum: 1, pageSize: 100 })
  employees.value = (page.list || []).map((item) => ({
    id: item.id,
    realName: item.realName,
    stationId: item.stationId,
    stationName: item.stationName
  }))
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    previewMonth.value = prevMonth()
    preview.value = null
    if (!employees.value.length) loadEmployees().catch(() => {})
  }
)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="计薪规则"
    size="min(var(--drawer-w-lg), 92vw)"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="计薪规则加载失败" @action="load" />

    <div v-else v-loading="loading" class="rule-editor">
      <el-form label-position="top">
        <section class="rule-editor__block">
          <div class="rule-editor__grid">
            <el-form-item label="规则名称" required>
              <el-input v-model="form.ruleName" maxlength="50" show-word-limit placeholder="如：一线员工月度工资" />
            </el-form-item>
            <el-form-item label="规则状态">
              <el-switch
                v-model="form.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
                inline-prompt
              />
            </el-form-item>
          </div>
          <el-form-item label="备注">
            <el-input v-model="form.remark" maxlength="200" show-word-limit placeholder="选填，说明这条规则适用于谁" />
          </el-form-item>
        </section>

        <section class="rule-editor__block">
          <div class="rule-editor__head">
            <h3 class="rule-editor__title">工资构成</h3>
            <el-button size="small" :icon="Plus" @click="addItem">添加项目</el-button>
          </div>
          <p class="rule-editor__tip">
            每个项目 = 方向 + 数据来源 +
            计算参数。数据来源全部取自系统内已有的数据，不支持自由输入，避免出现算不出来的表达式。
          </p>

          <div v-for="(item, index) in items" :key="item.key" class="rule-editor__item">
            <div class="rule-editor__item-head">
              <span class="rule-editor__item-index">{{ index + 1 }}</span>
              <el-input
                v-model="item.name"
                maxlength="20"
                placeholder="项目名称，如：全勤奖"
                class="rule-editor__item-name"
              />
              <el-switch v-model="item.enabled" active-text="启用" inline-prompt />
              <el-button-group>
                <el-button size="small" :disabled="index === 0" aria-label="上移该项目" @click="moveItem(index, -1)"
                  >上移</el-button
                >
                <el-button
                  size="small"
                  :disabled="index === items.length - 1"
                  aria-label="下移该项目"
                  @click="moveItem(index, 1)"
                  >下移</el-button
                >
              </el-button-group>
              <el-button link type="danger" @click="removeItem(index)">删除</el-button>
            </div>

            <div class="rule-editor__grid">
              <el-form-item label="方向">
                <el-select v-model="item.type" style="width: 100%">
                  <el-option v-for="(dict, key) in PAYROLL_ITEM_TYPE" :key="key" :value="key" :label="dict.label" />
                </el-select>
              </el-form-item>

              <el-form-item label="数据来源">
                <el-select v-model="item.source" style="width: 100%" @change="handleSourceChange(item)">
                  <el-option v-for="(dict, key) in PAYROLL_ITEM_SOURCE" :key="key" :value="key" :label="dict.label" />
                </el-select>
              </el-form-item>

              <!-- 计算参数按数据来源切换：只列该来源真实可取的字段 -->
              <template v-if="item.source === 'FIXED'">
                <el-form-item label="取值字段">
                  <el-select v-model="item.params.field" style="width: 100%">
                    <el-option v-for="(dict, key) in SALARY_FIELD" :key="key" :value="key" :label="dict.label" />
                  </el-select>
                </el-form-item>
              </template>

              <template v-else-if="item.source === 'ATTENDANCE'">
                <el-form-item label="考勤指标">
                  <el-select v-model="item.params.metric" style="width: 100%">
                    <el-option v-for="(dict, key) in ATTENDANCE_METRIC" :key="key" :value="key" :label="dict.label" />
                  </el-select>
                </el-form-item>
                <el-form-item label="计算方式">
                  <el-select v-model="item.params.mode" style="width: 100%">
                    <el-option v-for="(dict, key) in ATTENDANCE_MODE" :key="key" :value="key" :label="dict.label" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="item.params.mode === 'BONUS_IF_ZERO' ? '达标发放（元）' : '每次金额（元）'">
                  <el-input v-model="item.params.amount" type="number" />
                </el-form-item>
                <el-form-item v-if="item.params.mode === 'PER_COUNT'" label="封顶（元，0=不封顶）">
                  <el-input v-model="item.params.cap" type="number" :min="0" />
                </el-form-item>
              </template>

              <template v-else-if="item.source === 'KPI'">
                <el-form-item label="绩效基数取值">
                  <el-select v-model="item.params.baseField" style="width: 100%">
                    <el-option value="performanceBase" label="绩效基数" />
                  </el-select>
                </el-form-item>
                <el-form-item label="系数上限（1 = 不超过基数）">
                  <el-input v-model="item.params.capRatio" type="number" step="0.1" />
                </el-form-item>
              </template>

              <template v-else>
                <el-form-item label="默认金额（元）">
                  <el-input v-model="item.params.defaultValue" type="number" />
                </el-form-item>
              </template>
            </div>
          </div>
        </section>

        <section class="rule-editor__block">
          <h3 class="rule-editor__title">汇总口径（只读）</h3>
          <p class="rule-editor__formula">应发 = Σ(增项金额)；实发 = 应发 − Σ(扣项金额)</p>
          <p class="rule-editor__tip">汇总不使用可编辑表达式：一旦允许写公式，就会出现无法解析、无法校验的配置。</p>
        </section>

        <section class="rule-editor__block">
          <div class="rule-editor__head">
            <h3 class="rule-editor__title">试算预览</h3>
            <span class="rule-editor__tip">不落库，仅按当前草稿规则就地推算</span>
          </div>
          <div class="rule-editor__preview-bar">
            <el-date-picker
              v-model="previewMonth"
              type="month"
              value-format="YYYY-MM"
              :clearable="false"
              placeholder="试算月份"
              style="width: 132px"
            />
            <el-select v-model="previewEmployeeId" filterable placeholder="选择员工" style="width: 200px">
              <el-option
                v-for="item in employees"
                :key="item.id"
                :value="item.id"
                :label="`${item.realName}（${item.stationName || '—'}）`"
              />
            </el-select>
            <el-button
              type="primary"
              plain
              :loading="previewLoading"
              :disabled="!previewEmployeeId"
              @click="handlePreview"
              >试算</el-button
            >
          </div>

          <StateBlock
            v-if="previewError"
            variant="error"
            title="试算失败"
            description="试算依赖人事定薪、考勤与 KPI 数据，请稍后重试"
            @action="handlePreview"
          />
          <p v-else-if="!preview" class="rule-editor__tip">
            选择月份与员工后点「试算」，可看到该员工逐项金额与实发合计。
          </p>
          <template v-else>
            <el-table :data="preview.items" size="small" class="rule-editor__preview-table">
              <el-table-column prop="name" label="项目" min-width="110" />
              <el-table-column label="来源" width="96">
                <template #default="{ row }">{{
                  (PAYROLL_ITEM_SOURCE[row.source] || {}).label || row.source
                }}</template>
              </el-table-column>
              <el-table-column prop="detail" label="计算式" min-width="220" show-overflow-tooltip />
              <el-table-column label="金额" width="96" align="right">
                <template #default="{ row }">
                  <span :class="{ 'is-minus': row.type === 'DEDUCTION' }"
                    >{{ row.type === 'DEDUCTION' ? '-' : '' }}{{ Math.abs(Number(row.amount)) }}</span
                  >
                </template>
              </el-table-column>
            </el-table>
            <div class="rule-editor__preview-total">
              应发 {{ preview.grossAmount }} · 扣款 {{ preview.deductionTotal }} ·
              <span class="rule-editor__preview-net">实发 {{ preview.netAmount }} 元</span>
            </div>
          </template>
        </section>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="!canSubmit"
        :title="submitDisabledReason"
        @click="handleSave"
        >保存规则</el-button
      >
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.rule-editor {
  &__block {
    margin-bottom: var(--sp-6);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    margin-bottom: var(--sp-2);
  }

  &__title {
    margin: 0;
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

  &__item {
    margin-bottom: var(--sp-3);
    padding: var(--sp-3);
    border: 1px solid var(--border-line);
    border-radius: var(--r-md);
    background-color: var(--surface-sub);
  }

  &__item-head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-3);
  }

  &__item-index {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: var(--r-full);
    background-color: var(--color-primary-surface);
    color: var(--color-primary-strong);
    font-size: var(--fs-caption);
  }

  &__item-name {
    flex: 1;
    min-width: 0;
  }

  &__tip {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__formula {
    margin: 0 0 var(--sp-2);
    padding: var(--sp-3);
    border-radius: var(--r-md);
    background-color: var(--surface-sunken);
    font-size: var(--fs-body);
    color: var(--text-2);
  }

  &__preview-bar {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-3);
  }

  &__preview-table {
    width: 100%;
  }

  &__preview-total {
    margin-top: var(--sp-3);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__preview-net {
    font-size: var(--fs-num-sm);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  .is-minus {
    color: var(--state-danger-fg);
  }
}
</style>
