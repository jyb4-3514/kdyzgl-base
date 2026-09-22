<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { applyLeave, getLeave, previewLeave, resubmitLeave, updateLeave } from '../../api/leave.js'
import { HALF_DAY, LEAVE_TYPE } from '@/shared/constants/dict.js'
import { formatDate } from '@/shared/domain/time.js'
import { useAuthStore } from '../../stores/auth.js'
import { daysSummary, leaveDaysHint, leaveErrorHint, leaveFormError, toDate, todayText } from '../../utils/leave.js'

/**
 * P1 请假申请（STATION_ADMIN / STAFF）
 *
 * 三个入口共用本页，避免同一份表单三处各写一遍：
 * - 新建：/staff/leave/apply
 * - 就地修改（T8）：?id=&mode=edit，仅 PENDING_STATION 可改，状态不变（§4.6）
 * - 修改并重新提交（T9）：?id=&mode=resubmit，生成新单，原单保持 REJECTED 只读
 *
 * 天数摘要一律取 POST /leave/preview（只算不落库）：计薪天数要逐日查排班真源，
 * 前端自己算就会出现第二份算薪口径（设计规范 §4.3.3 / Q5）。本页不做任何时长推算。
 */
const PREVIEW_DEBOUNCE = 300

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const targetId = computed(() => (route.query.id ? Number(route.query.id) : null))
const mode = computed(() => (!targetId.value ? 'create' : route.query.mode === 'edit' ? 'edit' : 'resubmit'))

const pageTitle = computed(() =>
  mode.value === 'edit' ? '修改请假申请' : mode.value === 'resubmit' ? '重新提交请假' : '请假申请'
)
const submitLabel = computed(() =>
  mode.value === 'edit' ? '保存修改' : mode.value === 'resubmit' ? '重新提交' : '提交申请'
)
/** 站长无上级可审（D1 跳过初审），流程说明必须与状态机一致，避免用户等错人 */
const flowNote = computed(() =>
  auth.role === 'STATION_ADMIN'
    ? '站长申请跳过初审，提交后直接进入老板终审'
    : '提交后进入站长初审，站长通过后由老板终审，终审通过才生效'
)

const form = reactive({
  leaveType: '',
  startDate: '',
  startPeriod: 'AM',
  endDate: '',
  endPeriod: 'PM',
  reason: ''
})

const loading = ref(false)
const loadError = ref('')
/** 状态护栏（非取数失败）：编辑入口但服务端判定不可改，就地说明而不是给一个点了必失败的按钮 */
const guardError = ref('')
const submitting = ref(false)
const submitError = ref('')

const showType = ref(false)
const showStart = ref(false)
const showEnd = ref(false)

const preview = ref(null)
const previewing = ref(false)
const previewError = ref('')
let previewTimer = null

const typeLabel = computed(() => (LEAVE_TYPE[form.leaveType] ? LEAVE_TYPE[form.leaveType].label : ''))
const typeColumns = computed(() => Object.entries(LEAVE_TYPE).map(([value, item]) => ({ value, text: item.label })))
const formError = computed(() => leaveFormError(form))

const summaryText = computed(() =>
  daysSummary(preview.value && preview.value.naturalDays, preview.value && preview.value.countedDays)
)
const summaryHint = computed(() =>
  preview.value ? leaveDaysHint({ ...preview.value, leaveType: form.leaveType }) : ''
)
/** 试算的前置：区间本身要成立，否则每次改动都会打一发注定 9604 的请求 */
const canPreview = computed(
  () =>
    !!form.leaveType &&
    !!form.startDate &&
    !!form.endDate &&
    form.endDate >= form.startDate &&
    !(form.startDate === form.endDate && form.startPeriod === 'PM' && form.endPeriod === 'AM')
)

/**
 * 「今天上午已开始」软提示（§4.5，不阻断提交）：急事请假要先能落地，硬拦只会让信息滞后。
 * 上午的边界取 HALF_DAY 的定义（00:00–12:00），本页不额外拉打卡规则算班次开始时间。
 */
const morningPassed = computed(
  () => form.startDate === todayText() && form.startPeriod === 'AM' && new Date().getHours() >= 12
)
/** 跨年提示（§8.2）：跨年单会分别计入 12 月与 1 月账期，提前讲清避免员工以为算错 */
const crossYear = computed(() => !!form.endDate && form.startDate.slice(0, 4) !== form.endDate.slice(0, 4))

async function runPreview() {
  previewError.value = ''
  if (!canPreview.value) {
    preview.value = null
    return
  }
  previewing.value = true
  try {
    preview.value = await previewLeave({
      leaveType: form.leaveType,
      startDate: form.startDate,
      startPeriod: form.startPeriod,
      endDate: form.endDate,
      endPeriod: form.endPeriod
    })
  } catch (e) {
    preview.value = null
    previewError.value = leaveErrorHint(e.code, { message: e.message })
  } finally {
    previewing.value = false
  }
}

/** 编辑 / 重提：预填原值；派生标志 canEdit 由服务端给，前端不重复推导权限 */
async function loadTarget() {
  if (!targetId.value) return
  loading.value = true
  loadError.value = ''
  guardError.value = ''
  try {
    const detail = await getLeave(targetId.value)
    Object.assign(form, {
      leaveType: detail.leaveType,
      startDate: detail.startDate,
      startPeriod: detail.startPeriod,
      endDate: detail.endDate,
      endPeriod: detail.endPeriod,
      reason: detail.reason
    })
    if (mode.value === 'edit' && !detail.canEdit) {
      guardError.value = '该申请当前状态不允许修改，请返回列表刷新后查看（已通过站长初审的单需先撤销再重新申请）'
    }
    if (mode.value === 'resubmit' && detail.status !== 'REJECTED') {
      guardError.value = '该申请不是驳回状态，无法修改重提，请返回列表刷新后查看'
    }
  } catch (e) {
    loadError.value = leaveErrorHint(e.code, { message: e.message })
  } finally {
    loading.value = false
  }
}

function onPickType({ selectedValues }) {
  form.leaveType = selectedValues[0]
  showType.value = false
}

function onPickStart(date) {
  form.startDate = formatDate(date)
  // 结束日期不能早于开始日期：选完开始日期即把已失效的结束日期拉平，不留一个非法中间态
  if (!form.endDate || form.endDate < form.startDate) form.endDate = form.startDate
  showStart.value = false
}

function onPickEnd(date) {
  form.endDate = formatDate(date)
  showEnd.value = false
}

function submitToast(saved) {
  const prefix = mode.value === 'edit' ? '已保存，' : mode.value === 'resubmit' ? '已重新提交，' : '已提交，'
  return `${prefix}${saved.status === 'PENDING_BOSS' ? '等待老板终审' : '等待站长初审'}`
}

async function onSubmit() {
  if (submitting.value || formError.value || guardError.value) return
  submitting.value = true
  submitError.value = ''
  const payload = {
    leaveType: form.leaveType,
    startDate: form.startDate,
    startPeriod: form.startPeriod,
    endDate: form.endDate,
    endPeriod: form.endPeriod,
    reason: form.reason.trim()
  }
  try {
    const saved =
      mode.value === 'edit'
        ? await updateLeave(targetId.value, payload)
        : mode.value === 'resubmit'
          ? await resubmitLeave(targetId.value, payload)
          : await applyLeave(payload)
    showSuccessToast(submitToast(saved))
    // 提交后回列表：本页可能是从宫格直接进来的，用 replace 保证返回栈落到列表而不是回到已提交的表单
    router.replace('/staff/leave')
  } catch (e) {
    submitError.value = leaveErrorHint(e.code, { message: e.message })
  } finally {
    submitting.value = false
  }
}

watch(
  () => [form.leaveType, form.startDate, form.startPeriod, form.endDate, form.endPeriod],
  () => {
    clearTimeout(previewTimer)
    previewTimer = setTimeout(runPreview, PREVIEW_DEBOUNCE)
  }
)

// 预填完成后的首次试算由 watch 触发（表单值一变就重算），此处不重复发一次请求
onMounted(loadTarget)

onUnmounted(() => clearTimeout(previewTimer))
</script>

<template>
  <div class="leave-apply">
    <PageNav :title="pageTitle" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="loadError" :rows="4" @retry="loadTarget">
        <p v-if="guardError" class="guard" role="alert">{{ guardError }}</p>

        <div class="card form-card">
          <van-field
            :model-value="typeLabel"
            label="请假类型"
            readonly
            is-link
            placeholder="请选择请假类型"
            @click="showType = true"
          />
          <van-field
            :model-value="form.startDate"
            label="开始日期"
            readonly
            is-link
            placeholder="请选择开始日期"
            @click="showStart = true"
          />
          <div class="period-row">
            <span class="period-row__label">开始半天</span>
            <van-radio-group v-model="form.startPeriod" direction="horizontal" class="period-group">
              <van-radio v-for="(item, key) in HALF_DAY" :key="key" :name="key">{{ item.label }}</van-radio>
            </van-radio-group>
          </div>
          <van-field
            :model-value="form.endDate"
            label="结束日期"
            readonly
            is-link
            placeholder="请选择结束日期"
            @click="showEnd = true"
          />
          <div class="period-row">
            <span class="period-row__label">结束半天</span>
            <van-radio-group v-model="form.endPeriod" direction="horizontal" class="period-group">
              <van-radio v-for="(item, key) in HALF_DAY" :key="key" :name="key">{{ item.label }}</van-radio>
            </van-radio-group>
          </div>
          <van-field
            v-model="form.reason"
            type="textarea"
            rows="3"
            maxlength="200"
            show-word-limit
            label="请假事由"
            placeholder="请说明请假原因（2-200 字）"
          />
        </div>

        <!-- 天数摘要：自然天数与计薪天数双值同显，两值不等时给出口径说明（§4.3.3） -->
        <div class="card summary">
          <p class="summary__value tabular-nums">{{ summaryText }}</p>
          <p v-if="previewing" class="summary__hint">试算中…</p>
          <p v-else-if="previewError" class="summary__error" role="alert">{{ previewError }}</p>
          <p v-else-if="summaryHint" class="summary__hint">{{ summaryHint }}</p>
          <p v-if="morningPassed" class="summary__warn" role="status">
            今天上午已开始，如需请假请从下午开始，或与站长说明情况
          </p>
          <p v-if="crossYear" class="summary__note">跨年申请将分别计入 12 月与 1 月账期</p>
        </div>

        <p v-if="submitError" class="submit-error" role="alert">{{ submitError }}</p>
        <p v-else-if="formError" class="field-hint">{{ formError }}</p>
      </PageState>
    </div>

    <ActionBar
      :actions="[
        { key: 'submit', label: submitLabel, plain: false, loading: submitting, disabled: !!formError || !!guardError }
      ]"
      :note="flowNote"
      :submitting="submitting"
      @select="onSubmit"
    />

    <van-popup v-model:show="showType" round position="bottom" safe-area-inset-bottom>
      <van-picker
        title="选择请假类型"
        :columns="typeColumns"
        :model-value="form.leaveType ? [form.leaveType] : []"
        @confirm="onPickType"
        @cancel="showType = false"
      />
    </van-popup>

    <!-- 日历 min-date 兜住「不能选过去」：手机上禁选比事后报错更省事（§4.5） -->
    <van-calendar
      v-model:show="showStart"
      :min-date="toDate(todayText())"
      :default-date="toDate(form.startDate || todayText())"
      :show-confirm="false"
      title="选择开始日期"
      @confirm="onPickStart"
    />
    <van-calendar
      v-model:show="showEnd"
      :min-date="toDate(form.startDate || todayText())"
      :default-date="toDate(form.endDate || form.startDate || todayText())"
      :show-confirm="false"
      title="选择结束日期"
      @confirm="onPickEnd"
    />
  </div>
</template>

<style scoped>
.form-card {
  margin-top: var(--sp-3);

  /* 字段自带 16px 左右内边距 + 卡片 16px 会成 32px，统一收敛到卡片内边距 */
  --van-cell-horizontal-padding: 0px;
}

.period-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: var(--sp-2) 0;
}

.period-row__label {
  font-size: var(--fs-body);
  color: var(--text-1);
}

/* 半天是表单里最易误触的控件，每个选项给足 44px 触控高（7.4） */
.period-group :deep(.van-radio) {
  min-height: 44px;
}

.period-group :deep(.van-radio + .van-radio) {
  margin-left: var(--sp-5);
}

.summary {
  margin-top: var(--sp-3);
}

.summary__value {
  font-size: var(--fs-num-md);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-md);
  color: var(--text-1);
}

.summary__hint {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.summary__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

/* 软提示用 warning 浅底：warning 文字在白底上的对比度已达标，浅底进一步留出余量 */
.summary__warn {
  padding: var(--sp-2) var(--sp-3);
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-warning);
  background: var(--color-warning-surface);
  border-radius: var(--r-sm);
}

/* 浅底块内一律用 --text-2：--text-3 在浅灰底上只有 4.23:1（§5.1 对比度禁区） */
.summary__note {
  padding: var(--sp-2) var(--sp-3);
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
  background: var(--surface-subtle);
  border-radius: var(--r-sm);
}

.guard {
  padding: var(--sp-2) var(--sp-3);
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
  background: var(--color-danger-surface);
  border-radius: var(--r-sm);
}

.submit-error {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}
</style>
