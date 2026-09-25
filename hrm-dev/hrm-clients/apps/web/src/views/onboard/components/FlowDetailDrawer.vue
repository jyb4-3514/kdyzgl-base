<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completeOffboardingStep,
  completeOnboardingStep,
  getOffboarding,
  getOnboarding,
  rejectOffboarding,
  rejectOnboarding
} from '../../../api/hr.js'
import { FLOW_STATUS, OFFBOARDING_TYPE } from '@kdyzgl/shared/constants/dict.js'
import FlowSteps from '../../../components/FlowSteps.vue'
import RejectDialog from '../../../components/RejectDialog.vue'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 入离职流程详情抽屉（C4，B10.4）
 *
 * 步骤办理的区域是本抽屉的重点：不同步骤要填的东西完全不同（建档要账号、分配要驿站岗位、定薪要薪资构成），
 * 因此待办面板按当前步骤 key 动态渲染入参表单，而不是把所有字段一次性摊在页面上。
 *
 * 两条 B10.5 的硬约定：驳回必填原因且需显式提示「不回滚已产生的数据」；
 * 离职「薪资结算」步骤完成后展示与财务工资单的引用关系（结算单号 + 金额）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  flowType: { type: String, default: 'ONBOARDING' },
  flowId: { type: Number, default: null },
  // 契约的流程读写接口均为 ADMIN；非 ADMIN 时面板降级为只读说明，不渲染按钮
  canWrite: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'changed'])

const ROLE_OPTIONS = [
  { value: 'STAFF', label: '员工' },
  { value: 'STATION_ADMIN', label: '站长' }
]

const loading = ref(false)
const error = ref(false)
const acting = ref(false)
const rejectVisible = ref(false)
const flow = ref(null)
const stepForm = ref({})

const isOnboarding = computed(() => props.flowType === 'ONBOARDING')

async function load() {
  if (!props.flowId) return
  loading.value = true
  error.value = false
  try {
    flow.value = isOnboarding.value ? await getOnboarding(props.flowId) : await getOffboarding(props.flowId)
    stepForm.value = { remark: '' }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.flowId, props.flowType],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

const currentStep = computed(() => {
  if (!flow.value) return null
  return flow.value.steps.find((step) => step.key === flow.value.currentStepKey) || null
})

const doneSteps = computed(() => (flow.value ? flow.value.steps.filter((step) => step.status === 'DONE') : []))

/** 流程已完成 / 已驳回 / 当前用户不具写权限时，待办面板只读 */
const actionable = computed(
  () => !!flow.value && flow.value.status === 'IN_PROGRESS' && !!currentStep.value && props.canWrite
)

/** 各步骤的专属入参：与 hrStore.completeOnboardingStep 的语义一一对应 */
const NEEDS_ACCOUNT = computed(
  () => isOnboarding.value && currentStep.value && currentStep.value.key === 'CREATE_ACCOUNT'
)
const NEEDS_ASSIGN = computed(
  () => isOnboarding.value && currentStep.value && currentStep.value.key === 'ASSIGN_STATION'
)
const NEEDS_SALARY = computed(() => isOnboarding.value && currentStep.value && currentStep.value.key === 'SET_SALARY')

function stepPayload() {
  const base = { remark: stepForm.value.remark || undefined }
  if (NEEDS_ACCOUNT.value) {
    return {
      ...base,
      username: stepForm.value.username,
      password: stepForm.value.password,
      deptId: stepForm.value.deptId || undefined,
      stationId: stepForm.value.stationId || undefined
    }
  }
  if (NEEDS_ASSIGN.value) {
    return {
      ...base,
      deptId: stepForm.value.deptId || undefined,
      stationId: stepForm.value.stationId || undefined,
      position: stepForm.value.position || undefined,
      role: stepForm.value.role || undefined
    }
  }
  if (NEEDS_SALARY.value) {
    return {
      ...base,
      basicSalary: Number(stepForm.value.basicSalary || 0),
      postSalary: Number(stepForm.value.postSalary || 0),
      performanceBase: Number(stepForm.value.performanceBase || 0),
      effectiveDate: stepForm.value.effectiveDate || undefined
    }
  }
  return base
}

function validateStep() {
  if (NEEDS_ACCOUNT.value) {
    if (!/^[A-Za-z][A-Za-z0-9_]{3,29}$/.test(String(stepForm.value.username || '')))
      return '登录账号须为字母开头、4-30 位字母数字下划线'
    if (!stepForm.value.password || stepForm.value.password.length < 8)
      return '初始密码须为 8-20 位且同时包含字母和数字'
  }
  if (NEEDS_SALARY.value) {
    for (const key of ['basicSalary', 'postSalary', 'performanceBase']) {
      const value = Number(stepForm.value[key] || 0)
      if (!Number.isFinite(value) || value < 0) return '薪资项须为不小于 0 的数字'
    }
    const sum =
      Number(stepForm.value.basicSalary || 0) +
      Number(stepForm.value.postSalary || 0) +
      Number(stepForm.value.performanceBase || 0)
    if (!sum) return '请填写薪资构成，三项不可同时为 0'
  }
  return ''
}

async function handleComplete() {
  const invalid = validateStep()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }
  // 账号回收不可逆：通过后员工立即无法登录（B10.7 二次确认点）
  if (!isOnboarding.value && currentStep.value && currentStep.value.key === 'LEAVE') {
    try {
      await ElMessageBox.confirm(
        `通过后 ${flow.value.employeeName} 的账号将立即停用，员工无法再登录系统，该操作不可撤回。`,
        '确认回收账号',
        { confirmButtonText: '确认停用账号', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  acting.value = true
  try {
    const payload = stepPayload()
    const result = isOnboarding.value
      ? await completeOnboardingStep(props.flowId, currentStep.value.key, payload)
      : await completeOffboardingStep(props.flowId, currentStep.value.key, payload)
    ElMessage.success(`「${currentStep.value.name}」已办理`)
    flow.value = result
    stepForm.value = { remark: '' }
    emit('changed')
  } finally {
    acting.value = false
  }
}

async function handleReject(reason) {
  acting.value = true
  try {
    const api = isOnboarding.value ? rejectOnboarding : rejectOffboarding
    const result = await api(props.flowId, { reason })
    ElMessage.success('流程已驳回')
    rejectVisible.value = false
    flow.value = result
    emit('changed')
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="isOnboarding ? '入职流程详情' : '离职流程详情'"
    :size="`min(var(--drawer-w-lg), 92vw)`"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="流程详情加载失败" @action="load" />

    <div v-else v-loading="loading" class="flow-detail">
      <template v-if="flow">
        <FlowSteps :steps="flow.steps" :flow-status="flow.status" :current-step-key="flow.currentStepKey" />

        <div class="flow-detail__head">
          <span class="flow-detail__no">{{ flow.flowNo }}</span>
          <StatusTag :dict="FLOW_STATUS" :value="flow.status" :variant="FLOW_STATUS[flow.status].variant" />
          <span class="flow-detail__progress">进度 {{ flow.progress.done }} / {{ flow.progress.total }}</span>
        </div>

        <el-descriptions :column="2" size="small" border>
          <template v-if="isOnboarding">
            <el-descriptions-item label="候选人">{{ flow.employeeName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ flow.phone || '—' }}</el-descriptions-item>
            <el-descriptions-item label="学历">{{ flow.educationLabel || '—' }}</el-descriptions-item>
            <el-descriptions-item label="拟任岗位">{{ flow.position || '—' }}</el-descriptions-item>
            <el-descriptions-item label="归属驿站">{{ flow.stationName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="期望入职日">{{ flow.expectedEntryDate || '—' }}</el-descriptions-item>
          </template>
          <template v-else>
            <el-descriptions-item label="员工">{{ flow.employeeName }}</el-descriptions-item>
            <el-descriptions-item label="离职类型">
              <StatusTag :dict="OFFBOARDING_TYPE" :value="flow.type" variant="outline" />
            </el-descriptions-item>
            <el-descriptions-item label="归属驿站">{{ flow.stationName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="最后工作日">{{ flow.lastWorkDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="离职原因" :span="2">{{ flow.reason || '—' }}</el-descriptions-item>
          </template>
          <el-descriptions-item v-if="flow.rejectReason" label="驳回原因" :span="2">{{
            flow.rejectReason
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 薪资结算与财务工资单的引用关系（需求10 明确要求） -->
        <el-alert
          v-if="!isOnboarding && flow.settlementPayrollNo"
          class="flow-detail__alert"
          type="success"
          show-icon
          :closable="false"
          title="已生成离职结算单"
          :description="`结算单 ${flow.settlementPayrollNo}，实发 ${flow.settlementAmount} 元，可在「财务管理 → 工资单」中查看与发布。`"
        />

        <!-- 当前步骤待办区 -->
        <section class="flow-detail__todo">
          <h4 class="flow-detail__title">当前待办</h4>
          <template v-if="currentStep">
            <p class="flow-detail__step">第 {{ currentStep.order }} 步 · {{ currentStep.name }}</p>

            <template v-if="actionable">
              <el-form label-position="top">
                <div v-if="NEEDS_ACCOUNT" class="flow-detail__grid">
                  <el-form-item label="登录账号" required>
                    <el-input v-model="stepForm.username" placeholder="字母开头，4-30 位" />
                  </el-form-item>
                  <el-form-item label="初始密码" required>
                    <el-input v-model="stepForm.password" placeholder="8-20 位，含字母与数字" />
                  </el-form-item>
                </div>

                <div v-if="NEEDS_ASSIGN" class="flow-detail__grid">
                  <el-form-item label="岗位名称">
                    <el-input v-model="stepForm.position" placeholder="如：快递员" />
                  </el-form-item>
                  <el-form-item label="角色">
                    <el-select v-model="stepForm.role" clearable style="width: 100%">
                      <el-option
                        v-for="item in ROLE_OPTIONS"
                        :key="item.value"
                        :value="item.value"
                        :label="item.label"
                      />
                    </el-select>
                  </el-form-item>
                </div>

                <div v-if="NEEDS_SALARY" class="flow-detail__grid">
                  <el-form-item label="基本工资（元）">
                    <el-input v-model="stepForm.basicSalary" type="number" :min="0" />
                  </el-form-item>
                  <el-form-item label="岗位工资（元）">
                    <el-input v-model="stepForm.postSalary" type="number" :min="0" />
                  </el-form-item>
                  <el-form-item label="绩效基数（元）">
                    <el-input v-model="stepForm.performanceBase" type="number" :min="0" />
                  </el-form-item>
                  <el-form-item label="生效日期">
                    <el-date-picker
                      v-model="stepForm.effectiveDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      :clearable="false"
                      style="width: 100%"
                    />
                  </el-form-item>
                </div>

                <el-form-item label="办理备注（选填）">
                  <el-input v-model="stepForm.remark" maxlength="200" show-word-limit />
                </el-form-item>
              </el-form>

              <div class="flow-detail__actions">
                <el-button type="danger" plain :loading="acting" @click="rejectVisible = true">驳回</el-button>
                <el-button type="primary" :loading="acting" @click="handleComplete">通过并进入下一步</el-button>
              </div>
            </template>

            <p v-else class="flow-detail__hint">
              {{
                flow.status !== 'IN_PROGRESS'
                  ? `流程已${flow.statusLabel}，不可再办理`
                  : '当前账号无办理权限（流程办理仅超级管理员可操作）'
              }}
            </p>
          </template>
          <p v-else class="flow-detail__hint">该流程的所有步骤均已办理完成。</p>
        </section>

        <section class="flow-detail__timeline">
          <h4 class="flow-detail__title">办理记录</h4>
          <StateBlock
            v-if="!doneSteps.length"
            variant="empty"
            title="尚无办理记录"
            description="首个步骤通过后会在这里留下经办人与时间"
          />
          <el-timeline v-else>
            <el-timeline-item v-for="step in doneSteps" :key="step.key" :timestamp="step.operateTime">
              <p class="flow-detail__record">{{ step.name }} · {{ step.operatorName || '系统' }}</p>
              <p v-if="step.remark" class="flow-detail__hint">{{ step.remark }}</p>
            </el-timeline-item>
          </el-timeline>
          <!-- TODO(扩展): 契约无「撤销流程」接口（PUT /flows/:id/cancel 未实现），故暂不渲染撤销入口 -->
        </section>
      </template>
    </div>

    <RejectDialog
      v-model="rejectVisible"
      :current-step-name="currentStep ? currentStep.name : ''"
      :loading="acting"
      @submit="handleReject"
    />
  </el-drawer>
</template>

<style scoped lang="scss">
.flow-detail {
  min-height: 240px;

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin: var(--sp-4) 0;
  }

  &__no {
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__progress {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__alert {
    margin-top: var(--sp-4);
  }

  &__todo {
    margin-top: var(--sp-6);
    padding: var(--sp-4);
    border: 1px solid var(--border-line);
    border-radius: var(--r-md);
    background-color: var(--surface-sub);
  }

  &__title {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__step {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-body);
    color: var(--text-2);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 var(--sp-3);

    @media (max-width: 992px) {
      grid-template-columns: 1fr;
    }
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--sp-2);
  }

  &__timeline {
    margin-top: var(--sp-6);
  }

  &__record {
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
}
</style>
