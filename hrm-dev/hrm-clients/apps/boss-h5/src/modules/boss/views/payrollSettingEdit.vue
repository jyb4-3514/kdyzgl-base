<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import { PAYROLL_SETTING_LOG_ACTION } from '@kdyzgl/shared/constants/dict.js'
import { FINANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { getPayrollSetting, getPayrollSettingLogs, savePayrollSetting } from '@/api/finance.js'

/**
 * 算薪日设置（编辑 + 变更历史）· I-2 读 / I-3 写 / I-9 历史（设计规范 §2.2–§2.4）
 *
 * 沿用 leaveSettings 的「草稿 + 二次确认 + ActionBar 保存」先例：
 * 只有「启用开关」改动先弹二次确认（它直接决定是否自动跑数），其余字段攒到保存时统一提交。
 * 9406（尚未配置）不是错误态：以默认值呈现，保存一次即创建——这是契约明确的可判定分支。
 */
const route = useRoute()
const stationId = computed(() => Number(route.params.stationId))

const loading = ref(true)
const error = ref('')
const notConfigured = ref(false)
const stationName = ref('')
const saving = ref(false)

const DEFAULT_FORM = { enabled: 0, payrollDay: 1, payrollTime: '09:00', notifyEnabled: 1, remark: '' }
const saved = ref({ ...DEFAULT_FORM })
const draft = ref({ ...DEFAULT_FORM })

const dayError = ref('')
const timeError = ref('')
const remarkError = ref('')
const saveError = ref('')

const showDayPicker = ref(false)
const showTimePicker = ref(false)
const dayValue = ref(['1'])
const timeValue = ref(['09', '00'])

const dayOptions = Array.from({ length: 31 }, (_, i) => ({ text: String(i + 1), value: String(i + 1) }))

const logs = ref([])
const logsLoading = ref(true)
const logsError = ref('')
const LOG_PAGE_SIZE = 20
const logsTotal = ref(0)
const logsPageNum = ref(1)
const logsFinished = ref(false)
const logsLoadingMore = ref(false)

/** 草稿与已保存值一致时禁用保存（键序固定，直接 JSON 比较即可） */
const dirty = computed(() => JSON.stringify(draft.value) !== JSON.stringify(saved.value))

const barNote = computed(() => {
  if (loading.value || error.value) return ''
  if (notConfigured.value) return '该驿站尚未配置，保存一次即可创建'
  return dirty.value ? '有改动，点「保存设置」后生效' : '当前配置已保存'
})

const planHint = computed(() => {
  const day = Number(draft.value.payrollDay)
  return `每月 ${day || '-'} 日 ${draft.value.payrollTime || '-'}`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getPayrollSetting(stationId.value)
    stationName.value = data.stationName || ''
    notConfigured.value = false
    saved.value = {
      enabled: Number(data.enabled) === 1 ? 1 : 0,
      payrollDay: Number(data.payrollDay) || 1,
      payrollTime: data.payrollTime || '09:00',
      notifyEnabled: Number(data.notifyEnabled) === 1 ? 1 : 0,
      remark: data.remark || ''
    }
    draft.value = { ...saved.value }
  } catch (e) {
    if (e.code === FINANCE_CODE.SETTING_NOT_CONFIGURED) {
      // 尚未配置：给默认值，不判错误态
      notConfigured.value = true
      saved.value = { ...DEFAULT_FORM }
      draft.value = { ...DEFAULT_FORM }
    } else {
      error.value = e.message || '算薪设置加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function loadLogs(page) {
  if (page === 1) {
    logsLoading.value = true
    logsError.value = ''
  } else {
    logsLoadingMore.value = true
  }
  try {
    const result = await getPayrollSettingLogs(stationId.value, { pageNum: page, pageSize: LOG_PAGE_SIZE })
    const rows = result.list || []
    logs.value = page === 1 ? rows : logs.value.concat(rows)
    logsTotal.value = result.total || 0
    logsPageNum.value = page
    logsFinished.value = logs.value.length >= logsTotal.value
  } catch (e) {
    if (page === 1) logsError.value = e.message || '变更历史加载失败'
    else logsFinished.value = true
  } finally {
    logsLoading.value = false
    logsLoadingMore.value = false
  }
}

function onLoadMoreLogs() {
  if (logsFinished.value || logsLoadingMore.value) return
  loadLogs(logsPageNum.value + 1)
}

/** 启用开关：0→1 直接影响是否自动跑数，先二次确认再落草稿（leaveSettings 同类先例） */
async function onToggleEnabled(next) {
  if (saving.value || next === draft.value.enabled) return
  try {
    await showConfirmDialog({
      title: next ? '启用自动算薪' : '停用自动算薪',
      message: next
        ? '启用后，系统在每驿站各自的算薪日自动生成工资单草稿并提交审核，生成后可按设置推送管理员。确认启用？'
        : '停用后，该驿站不再自动跑数，已有工资单不受影响。确认停用？',
      confirmButtonText: next ? '确认启用' : '确认停用',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return // 用户取消：开关视觉保持原值
  }
  draft.value.enabled = next
}

function onToggleNotify(next) {
  draft.value.notifyEnabled = next
}

/** 算薪日 / 时间：失焦即校验，文案与契约 9407 / 9408 同义 */
function validateDay() {
  const day = Number(draft.value.payrollDay)
  dayError.value = Number.isInteger(day) && day >= 1 && day <= 31 ? '' : '算薪日须为 1–31 的整数，月末自动钳位到当月最后一天'
  return !dayError.value
}

function validateTime() {
  timeError.value = /^([01]\d|2[0-3]):[0-5]\d$/.test(draft.value.payrollTime || '') ? '' : '时间格式须为 HH:mm（如 09:00）'
  return !timeError.value
}

function validateRemark() {
  remarkError.value = String(draft.value.remark || '').length > 255 ? '备注不可超过 255 字' : ''
  return !remarkError.value
}

function openDayPicker() {
  dayValue.value = [String(draft.value.payrollDay || 1)]
  showDayPicker.value = true
}

function onDayConfirm({ selectedValues }) {
  draft.value.payrollDay = Number(selectedValues[0])
  validateDay()
  showDayPicker.value = false
}

function openTimePicker() {
  const [h, m] = String(draft.value.payrollTime || '09:00').split(':')
  timeValue.value = [h || '09', m || '00']
  showTimePicker.value = true
}

function onTimeConfirm({ selectedValues }) {
  draft.value.payrollTime = selectedValues.join(':')
  validateTime()
  showTimePicker.value = false
}

function onPickDay(value) {
  draft.value.payrollDay = Number(value)
  validateDay()
}

async function onSave() {
  if (saving.value) return
  const dayOk = validateDay()
  const timeOk = validateTime()
  const remarkOk = validateRemark()
  if (!dayOk || !timeOk || !remarkOk) return
  saving.value = true
  saveError.value = ''
  try {
    const data = await savePayrollSetting(stationId.value, {
      enabled: draft.value.enabled,
      payrollDay: Number(draft.value.payrollDay),
      payrollTime: draft.value.payrollTime,
      notifyEnabled: draft.value.notifyEnabled,
      remark: draft.value.remark
    })
    notConfigured.value = false
    stationName.value = data.stationName || stationName.value
    saved.value = {
      enabled: Number(data.enabled) === 1 ? 1 : 0,
      payrollDay: Number(data.payrollDay) || 1,
      payrollTime: data.payrollTime || '09:00',
      notifyEnabled: Number(data.notifyEnabled) === 1 ? 1 : 0,
      remark: data.remark || ''
    }
    draft.value = { ...saved.value }
    showSuccessToast('算薪设置已保存')
    loadLogs(1)
  } catch (e) {
    // 9407 / 9408 就地提示到字段；其余错误落到页面级 alert，避免静默失败
    if (e.code === FINANCE_CODE.SETTING_DAY_INVALID) dayError.value = e.message
    else if (e.code === FINANCE_CODE.SETTING_TIME_INVALID) timeError.value = e.message
    else saveError.value = e.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/* ==================== 变更历史渲染（I-9） ==================== */

const BOOL_LABEL = { 1: '开', 0: '关' }
const FIELD_LABEL = { payrollDay: '算薪日', payrollTime: '执行时间', enabled: '启用', notifyEnabled: '推送', remark: '备注' }

function fieldValue(key, value) {
  if (key === 'enabled' || key === 'notifyEnabled') return BOOL_LABEL[Number(value)] || '关'
  if (value == null || value === '') return '—'
  return String(value)
}

/** 由 before → after 白名单键逐字段生成变更文案；CREATE 无 before，只列 after 初值 */
function changeLines(log) {
  const after = log.after || {}
  const before = log.before || {}
  if (log.action === 'CREATE' || !log.before) {
    return Object.keys(FIELD_LABEL)
      .filter((key) => after[key] !== undefined)
      .map((key) => `${FIELD_LABEL[key]} ${fieldValue(key, after[key])}`)
  }
  return Object.keys(FIELD_LABEL)
    .filter((key) => after[key] !== undefined && before[key] !== after[key])
    .map((key) => `${FIELD_LABEL[key]} ${fieldValue(key, before[key])} → ${fieldValue(key, after[key])}`)
}

function actionLabel(action) {
  return (PAYROLL_SETTING_LOG_ACTION[action] || {}).label || action
}

onMounted(() => {
  load()
  loadLogs(1)
})
</script>

<template>
  <div class="payroll-setting-edit">
    <PageNav :title="notConfigured ? '配置员工工资设置' : '员工工资设置'" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="3" @retry="load">
        <van-notice-bar
          v-if="notConfigured"
          class="notice"
          left-icon="info-o"
          text="该驿站尚未配置算薪设置，保存一次即可创建"
          wrapable
          color="var(--color-warning)"
          background="var(--color-warning-surface)"
        />

        <p v-if="saveError" class="field-error" role="alert">{{ saveError }}</p>

        <div class="section-title">自动算薪</div>
        <div class="card">
          <div class="switch-row">
            <div class="switch-row__text">
              <p class="switch-row__label">启用自动算薪</p>
              <p class="switch-row__hint">未启用时该驿站不自动跑数</p>
            </div>
            <van-switch
              :model-value="draft.enabled === 1"
              :loading="saving"
              aria-label="启用自动算薪"
              @update:model-value="(v) => onToggleEnabled(v ? 1 : 0)"
            />
          </div>
        </div>

        <div class="section-title">算薪设置</div>
        <div class="card">
          <van-field
            :model-value="String(draft.payrollDay)"
            type="digit"
            label="算薪日"
            inputmode="numeric"
            placeholder="1–31"
            readonly
            is-link
            aria-label="算薪日，点选 1 到 31"
            @click="openDayPicker"
            @blur="validateDay"
          />
          <p class="field-hint">当月无该日时，自动取当月最后一天（如 31 → 4 月 30 日）</p>
          <p v-if="dayError" class="field-error" role="alert">{{ dayError }}</p>

          <van-field
            :model-value="draft.payrollTime"
            label="执行时间"
            readonly
            is-link
            aria-label="执行时间，点选小时与分钟"
            @click="openTimePicker"
          />
          <p v-if="timeError" class="field-error" role="alert">{{ timeError }}</p>
        </div>

        <div class="section-title">通知</div>
        <div class="card">
          <div class="switch-row">
            <div class="switch-row__text">
              <p class="switch-row__label">生成后推送管理员</p>
              <p class="switch-row__hint">生成工资单草稿并提交审核后通知管理员</p>
            </div>
            <van-switch
              :model-value="draft.notifyEnabled === 1"
              aria-label="生成后推送管理员"
              @update:model-value="(v) => onToggleNotify(v ? 1 : 0)"
            />
          </div>
        </div>

        <div class="section-title">备注</div>
        <div class="card">
          <van-field
            v-model="draft.remark"
            type="textarea"
            rows="2"
            maxlength="255"
            show-word-limit
            label="备注"
            placeholder="可空，例如：月末结算"
            @blur="validateRemark"
          />
          <p v-if="remarkError" class="field-error" role="alert">{{ remarkError }}</p>
        </div>

        <div class="section-title">
          <span>变更历史</span>
          <span class="section-title__extra">{{ planHint }}</span>
        </div>
        <div class="card">
          <div v-if="logsLoading" class="sk-row skeleton-block" aria-busy="true" />
          <div v-else-if="logsError" class="logs-error" role="alert">
            <p class="logs-error__text">{{ logsError }}</p>
            <button type="button" class="logs-error__retry" @click="loadLogs(1)">重新加载</button>
          </div>
          <p v-else-if="!logs.length" class="tip">暂无变更记录</p>
          <template v-else>
            <van-list
              v-model:loading="logsLoadingMore"
              :finished="logsFinished"
              finished-text="没有更多了"
              @load="onLoadMoreLogs"
            >
              <div v-for="log in logs" :key="log.id" class="log-row">
                <div class="log-row__head">
                  <span
                    class="log-tag"
                    :class="log.action === 'ENABLE' ? 'log-tag--enable' : 'log-tag--normal'"
                  >
                    {{ actionLabel(log.action) }}
                  </span>
                  <span class="log-row__time tabular-nums">{{ log.time }}</span>
                </div>
                <p class="log-row__operator">{{ log.operatorName }}（{{ log.operatorRole }}）</p>
                <ul class="log-row__changes">
                  <li v-for="(line, index) in changeLines(log)" :key="index" class="tabular-nums">{{ line }}</li>
                </ul>
                <p v-if="log.remark" class="log-row__remark">备注：{{ log.remark }}</p>
              </div>
            </van-list>
          </template>
        </div>
      </PageState>
    </div>

    <ActionBar
      :actions="[{ key: 'save', label: '保存设置', plain: false, loading: saving, disabled: !dirty || saving }]"
      :note="barNote"
      :submitting="saving"
      @select="onSave"
    />

    <van-popup v-model:show="showDayPicker" round position="bottom" safe-area-inset-bottom>
      <van-picker
        :columns="dayOptions"
        :model-value="dayValue"
        title="选择算薪日"
        @confirm="onDayConfirm"
        @cancel="showDayPicker = false"
        @change="({ selectedValues }) => onPickDay(selectedValues[0])"
      />
    </van-popup>

    <van-popup v-model:show="showTimePicker" round position="bottom" safe-area-inset-bottom>
      <van-time-picker
        v-model="timeValue"
        title="选择执行时间"
        :columns-type="['hour', 'minute']"
        @confirm="onTimeConfirm"
        @cancel="showTimePicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.field-hint {
  margin: 0;
  padding: var(--sp-1) var(--sp-4) var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.field-error {
  margin: 0;
  padding: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.switch-row {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  justify-content: space-between;
  min-height: var(--touch-min);
}

.switch-row__text {
  flex: 1;
  min-width: 0;
}

.switch-row__label {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.switch-row__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.sk-row {
  height: 96px;
}

.logs-error {
  padding: var(--sp-4) 0;
  text-align: center;
}

.logs-error__text {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--color-danger);
}

.logs-error__retry {
  min-height: var(--touch-min);
  padding: 0 var(--sp-5);
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

.log-row {
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--border-line);
}

.log-row:last-child {
  border-bottom: none;
}

.log-row__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.log-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  border-radius: var(--r-full);
}

/* ENABLE 必须醒目（M-9 硬要求）：warning 实底 + 白字，且不折叠、首屏可见 */
.log-tag--enable {
  color: var(--text-on-dark);
  background: var(--color-warning);
}

.log-tag--normal {
  color: var(--text-2);
  background: var(--surface-subtle);
}

.log-row__time {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.log-row__operator {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.log-row__changes {
  padding-left: var(--sp-4);
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.log-row__remark {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
