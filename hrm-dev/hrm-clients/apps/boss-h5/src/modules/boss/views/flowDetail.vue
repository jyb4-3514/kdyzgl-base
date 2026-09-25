<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StationPicker from '@/components/StationPicker.vue'
import { bossConfirm } from '../components/bossConfirm.js'
// FlowSteps 与 PayrollStatusSteps 是同一份实现（纵向步骤条），此处按业务语义重命名引用，避免写第二份
import FlowSteps from '@/components/PayrollStatusSteps.vue'
import {
  completeOffboardingStep,
  completeOnboardingStep,
  getOffboardingFlow,
  getOnboardingFlow,
  rejectOffboardingFlow,
  rejectOnboardingFlow
} from '@/api/hr.js'
import { getStationList } from '@/api/org.js'
import { valueText } from '@/utils/format.js'

/**
 * B10 流程详情（管理端：办理当前步骤 / 驳回）
 *
 * 三个业务要点：
 * 1. 只办理「第一个待办理步骤」——契约按序校验（9303 会说「请先办理 XX」），前端不给自己造越步入口
 * 2. 部分步骤需要业务入参（建档要账号密码、分配要驿站岗位、定薪要三项金额），因此弹层按步骤 key 渲染字段
 * 3. 驳回按契约语义处理：整流程置为「已驳回」，已办步骤的记录保留，**已产生的数据不回滚**（如离职结算单）
 *    —— 界面上必须显式提示，否则管理员会以为驳回了工资单就没了
 *
 * 二次确认只在「有副作用或不可逆」的步骤弹（建档/定薪/结算/离岗/完成），普通推进步骤直接办，避免连点疲劳。
 */
const CONFIRM_STEPS = {
  CREATE_ACCOUNT: {
    action: '建档并生成账号',
    impact: '将通过本流程创建员工与登录账号，账号初始密码由你设定',
    confirmText: '确认建档'
  },
  SET_SALARY: {
    action: '确认定薪',
    impact: '定薪结果将写入员工薪资档案，并生成一条调薪留痕',
    confirmText: '确认定薪'
  },
  SETTLEMENT: {
    action: '确认薪资结算',
    impact: '办理完成后将自动生成该员工的离职结算工资单（草稿），需再走审核与发布',
    confirmText: '确认结算'
  },
  LEAVE: {
    action: '确认离岗',
    impact: '通过后该账号立即停用、员工将无法登录，档案写入离职日期',
    irreversible: true,
    confirmText: '确认离岗'
  },
  DONE: {
    action: '完成入职',
    impact: '通过后该员工转为在职状态，将进入全站统计与考核范围',
    confirmText: '确认完成'
  }
}

const route = useRoute()
const type = computed(() => (route.params.type === 'offboarding' ? 'offboarding' : 'onboarding'))
const id = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const flow = ref(null)
const submitting = ref(false)
const stepError = ref('')

const showReject = ref(false)
const rejectReason = ref('')
const rejectError = ref('')

const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const showStation = ref(false)

const form = ref({
  remark: '',
  username: '',
  password: '',
  stationId: null,
  position: '',
  role: 'STAFF',
  basicSalary: '',
  postSalary: '',
  performanceBase: ''
})

const currentStep = computed(() => {
  const data = flow.value
  if (!data) return null
  return data.steps.find((step) => step.status === 'PENDING') || null
})
const stepKey = computed(() => (currentStep.value ? currentStep.value.key : ''))
const needAccount = computed(() => stepKey.value === 'CREATE_ACCOUNT')
const needAssign = computed(() => stepKey.value === 'ASSIGN_STATION')
const needSalary = computed(() => stepKey.value === 'SET_SALARY')
const isOnboarding = computed(() => type.value === 'onboarding')

const stepItems = computed(() => {
  const data = flow.value
  if (!data) return []
  const pendingIndex = data.steps.findIndex((step) => step.status === 'PENDING')
  const rejected = data.status === 'REJECTED'
  return data.steps.map((step, index) => {
    let state = step.status === 'DONE' ? 'done' : 'pending'
    let stateLabel
    if (step.status === 'PENDING' && index === pendingIndex) {
      state = rejected ? 'danger' : 'current'
      if (rejected) stateLabel = '已驳回'
    }
    return {
      key: step.key,
      label: step.name,
      state,
      stateLabel,
      time: step.operateTime,
      desc: step.status === 'DONE' ? `经办：${step.operatorName || '-'}${step.remark ? ` · ${step.remark}` : ''}` : ''
    }
  })
})

const stepsNote = computed(() => {
  if (!flow.value) return ''
  if (flow.value.status === 'REJECTED') return '驳回只回退进度，已产生的数据（如结算工资单）不会自动撤销，请人工核对'
  if (flow.value.status === 'COMPLETED') return '全部步骤已办结'
  return ''
})

const actions = computed(() => {
  if (!flow.value || flow.value.status !== 'IN_PROGRESS') return []
  return [
    { key: 'pass', label: '通过当前步骤' },
    { key: 'reject', label: '驳回', type: 'danger' }
  ]
})

const actionNote = computed(() => {
  const data = flow.value
  if (!data) return ''
  if (data.status === 'COMPLETED') return '流程已完成，不可再操作'
  if (data.status === 'REJECTED') return '流程已驳回，需重新发起流程后再办理'
  if (!currentStep.value) return '当前没有待办理步骤'
  return `当前待办：${currentStep.value.name}`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = isOnboarding.value ? await getOnboardingFlow(id.value) : await getOffboardingFlow(id.value)
    flow.value = data
    // 步骤入参预填：分配驿站/岗位用流程上已有值，减少重复输入（定薪需人事按标准填，不预填金额）
    form.value = {
      ...form.value,
      remark: '',
      stationId: data.stationId,
      position: data.position || '',
      role: data.role || 'STAFF'
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    stations.value = await getStationList()
  } catch (e) {
    // 分配步骤的驿站选择失败必须可见：选不到驿站时用户会以为「公司没有驿站」
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

/** 按步骤组装入参并做字段级校验；返回 null 表示校验未过 */
function buildBody() {
  const data = form.value
  if (needAccount.value) {
    if (!/^[A-Za-z][A-Za-z0-9_]{3,29}$/.test(data.username.trim())) {
      stepError.value = '登录账号须为字母开头、4–30 位字母数字下划线'
      return null
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(data.password)) {
      stepError.value = '初始密码须为 8–20 位且同时包含字母和数字'
      return null
    }
    return { username: data.username.trim(), password: data.password, remark: data.remark.trim() || undefined }
  }
  if (needAssign.value) {
    if (!data.stationId) {
      stepError.value = '请选择归属驿站'
      return null
    }
    return {
      stationId: data.stationId,
      position: data.position.trim() || undefined,
      role: data.role,
      remark: data.remark.trim() || undefined
    }
  }
  if (needSalary.value) {
    for (const [key, label] of [
      ['basicSalary', '基本工资'],
      ['postSalary', '岗位工资'],
      ['performanceBase', '绩效基数']
    ]) {
      const value = Number(data[key])
      if (!Number.isFinite(value) || value < 0) {
        stepError.value = `${label}须为不小于 0 的数字`
        return null
      }
    }
    return {
      basicSalary: Number(data.basicSalary),
      postSalary: Number(data.postSalary),
      performanceBase: Number(data.performanceBase),
      effectiveDate: flow.value.expectedEntryDate,
      remark: data.remark.trim() || undefined
    }
  }
  return { remark: data.remark.trim() || undefined }
}

async function onAction(key) {
  if (submitting.value) return
  if (key === 'reject') {
    rejectReason.value = ''
    rejectError.value = ''
    showReject.value = true
    return
  }
  stepError.value = ''
  const body = buildBody()
  if (!body) return
  const confirmConfig = CONFIRM_STEPS[stepKey.value]
  if (confirmConfig) {
    // 作用对象由页面补齐（步骤配置只声明「做什么、会怎样」），四要素缺失会被 bossConfirm 直接拦下
    const ok = await bossConfirm({
      ...confirmConfig,
      target: `${flow.value.employeeName}（${flow.value.flowNo}）`
    })
    if (!ok) return
  }
  submitting.value = true
  try {
    const updated = isOnboarding.value
      ? await completeOnboardingStep(id.value, stepKey.value, body)
      : await completeOffboardingStep(id.value, stepKey.value, body)
    flow.value = updated
    form.value = {
      ...form.value,
      remark: '',
      username: '',
      password: '',
      basicSalary: '',
      postSalary: '',
      performanceBase: ''
    }
    showSuccessToast(
      updated.status === 'COMPLETED'
        ? '流程已办结'
        : `「${currentStep.value ? currentStep.value.name : '下一步'}」待办理`
    )
  } catch (e) {
    // 9303/9304：多为旁观者先办过或步骤顺序不符，页内说清并刷新到最新状态
    stepError.value = e.message || '办理失败，请稍后重试'
    await load()
  } finally {
    submitting.value = false
  }
}

async function submitReject() {
  const reason = rejectReason.value.trim()
  if (reason.length < 2 || reason.length > 200) {
    rejectError.value = '驳回原因须为 2–200 字，会写入步骤记录'
    return
  }
  submitting.value = true
  rejectError.value = ''
  try {
    const payload = { reason }
    flow.value = isOnboarding.value
      ? await rejectOnboardingFlow(id.value, payload)
      : await rejectOffboardingFlow(id.value, payload)
    showReject.value = false
    showSuccessToast('已驳回该流程')
  } catch (e) {
    rejectError.value = e.message || '驳回失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  loadStations()
})
</script>

<template>
  <div class="flow-detail">
    <PageNav :title="isOnboarding ? '入职办理' : '离职办理'" />
    <div class="page" :class="actions.length ? 'page--bar' : 'page--loose'">
      <PageState :loading="loading" :error="error" @retry="load">
        <section class="hero hero--deep flow-hero">
          <div class="flex-between">
            <span class="hero__title">{{ flow.employeeName }}</span>
            <span class="hero__chip">{{ flow.statusLabel }}</span>
          </div>
          <p class="hero__sub tabular-nums">
            {{ flow.flowNo }} · {{ flow.stationName || '总部' }} · 进度 {{ flow.progress.done }}/{{
              flow.progress.total
            }}
          </p>
          <p class="hero__sub tabular-nums">{{ flow.createTime }} 发起 · 发起人 {{ flow.operatorName || '-' }}</p>
        </section>

        <div class="section-title">流程步骤</div>
        <div class="card">
          <FlowSteps :steps="stepItems" :note="stepsNote" />
        </div>

        <div class="section-title">{{ isOnboarding ? '候选人信息' : '员工信息' }}</div>
        <van-cell-group inset>
          <template v-if="isOnboarding">
            <van-cell title="候选人" :value="flow.candidateName || '-'" />
            <van-cell title="手机号" :value="flow.phone || '-'" />
            <van-cell title="学历" :value="flow.educationLabel || '-'" />
            <van-cell title="拟任岗位" :value="flow.position || '-'" />
            <van-cell title="预计入职" :value="flow.expectedEntryDate || '-'" />
            <van-cell title="归属驿站" :value="flow.stationName || '-'" />
          </template>
          <template v-else>
            <van-cell title="离职类型" :value="flow.typeLabel || '-'" />
            <van-cell title="离职原因" :value="flow.reason || '-'" />
            <van-cell title="最后工作日" :value="flow.lastWorkDate || '-'" />
            <van-cell title="结算单号" :value="flow.settlementPayrollNo || '未生成'" />
            <van-cell
              title="结算金额"
              :value="flow.settlementAmount === null ? '未生成' : valueText(flow.settlementAmount, ' 元')"
            />
          </template>
        </van-cell-group>

        <!-- 待办区：只在有当前步骤时渲染表单，避免「看着能填、点不了」 -->
        <template v-if="currentStep && flow.status === 'IN_PROGRESS'">
          <div class="section-title">
            当前待办<span class="section-title__extra">{{ currentStep.name }}</span>
          </div>
          <div class="card">
            <template v-if="needAccount">
              <van-field v-model="form.username" label="登录账号" placeholder="字母开头，4–30 位" />
              <van-field v-model="form.password" label="初始密码" placeholder="8–20 位，含字母与数字" />
              <p class="tip">员工首登将强制改密；账号已存在时服务端会拦截</p>
            </template>
            <template v-else-if="needAssign">
              <van-cell
                title="归属驿站"
                :value="(stations.find((item) => item.id === form.stationId) || {}).stationName || '请选择'"
                is-link
                @click="showStation = true"
              />
              <van-field v-model="form.position" label="岗位" placeholder="如：快递员 / 分拣员" />
              <van-field label="角色" input-align="right">
                <template #input>
                  <van-radio-group v-model="form.role" direction="horizontal" class="role-group">
                    <van-radio name="STAFF">员工</van-radio>
                    <van-radio name="STATION_ADMIN">站长</van-radio>
                  </van-radio-group>
                </template>
              </van-field>
            </template>
            <template v-else-if="needSalary">
              <van-field
                v-model="form.basicSalary"
                type="number"
                label="基本工资"
                input-align="right"
                placeholder="元"
              />
              <van-field
                v-model="form.postSalary"
                type="number"
                label="岗位工资"
                input-align="right"
                placeholder="元"
              />
              <van-field
                v-model="form.performanceBase"
                type="number"
                label="绩效基数"
                input-align="right"
                placeholder="元"
              />
              <p class="tip">定薪生效日期取预计入职日期 {{ flow.expectedEntryDate }}；津贴项请在人事管理中另行维护</p>
            </template>
            <van-field
              v-model="form.remark"
              type="textarea"
              rows="2"
              maxlength="200"
              show-word-limit
              label="办理意见"
              placeholder="选填，会写入步骤记录"
            />
            <p v-if="stepError" class="form-error" role="alert">{{ stepError }}</p>
          </div>
        </template>

        <p v-if="stepError && (!currentStep || flow.status !== 'IN_PROGRESS')" class="form-error" role="alert">
          {{ stepError }}
        </p>

        <div class="section-title">步骤记录</div>
        <div class="card">
          <div
            v-for="step in flow.steps.filter((item) => item.status === 'DONE')"
            :key="step.key"
            class="timeline__item"
          >
            <span class="timeline__dot timeline__dot--success" aria-hidden="true" />
            <p class="timeline__time tabular-nums">
              {{ step.operateTime }} · {{ step.name }} · {{ step.operatorName || '-' }}
            </p>
            <p class="timeline__text">{{ step.remark || '无办理意见' }}</p>
          </div>
          <p v-if="!flow.steps.some((item) => item.status === 'DONE')" class="tip">暂无已办理步骤</p>
        </div>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="actionNote" :submitting="submitting" @select="onAction" />

    <van-popup v-model:show="showReject" round position="bottom" safe-area-inset-bottom>
      <div class="reject-pop">
        <div class="reject-pop__title">驳回流程</div>
        <p class="reject-pop__sub">
          驳回后流程标记为「已驳回」，已办理步骤的记录保留；已产生的数据（如离职结算工资单）不会自动撤销，请人工核对。
        </p>
        <!-- TODO(扩展): B10.5 要求「可下拉选择退回到任意前置步骤」，契约的驳回接口只收 reason、整流程置驳回，
             无目标步骤入参；待契约新增 targetStepKey 后再补选择器，不伪造无效控件 -->
        <van-field
          v-model="rejectReason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          label="驳回原因"
          placeholder="必填，2–200 字"
        />
        <p v-if="rejectError" class="reject-pop__error" role="alert">{{ rejectError }}</p>
        <div class="reject-pop__foot">
          <van-button block type="danger" :loading="submitting" @click="submitReject">确认驳回</van-button>
        </div>
      </div>
    </van-popup>

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="form.stationId"
      :allow-all="false"
      :loading="stationsLoading"
      :error="stationsError"
      title="选择归属驿站"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="loadStations"
      @select="form.stationId = $event"
    />
  </div>
</template>

<style scoped>
.flow-hero {
  margin-top: var(--sp-3);
}

.form-error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.role-group {
  justify-content: flex-end;
}

.reject-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.reject-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.reject-pop__sub {
  padding: 0 var(--sp-4);
  margin: var(--sp-1) 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.reject-pop__error {
  margin: var(--sp-2) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.reject-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
