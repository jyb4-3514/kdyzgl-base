<script setup>
import { computed, onMounted, ref } from 'vue'
import { showSuccessToast } from 'vant'
import ActionBar from '@/mobile/components/ActionBar.vue'
import PageNav from '@/mobile/components/PageNav.vue'
import PageState from '@/mobile/components/PageState.vue'
import { getAttendanceRule, saveAttendanceRule } from '@/mobile/api/attendance.js'
import { getStationList } from '@/mobile/api/org.js'
import { MATCH_MODE } from '@/shared/constants/dict.js'
import { minutesOfDay } from '@/mobile/utils/attendance.js'

/**
 * B8 打卡规则（ADMIN · 查看 + 快捷调整）
 * 为什么先选驿站再改规则：规则是按驿站维度存的（半径、围栏坐标、白名单各不相同），
 * 不选驿站的话「保存」会把 A 站的规则写歪到 B 站。
 *
 * 时段是规则的唯一真源：上下班时间（workStartTime / workEndTime）由时段自动派生，
 * 页面上只读展示，避免「改了时段、上下班时间还是旧值」两套口径打架。
 * 白名单（WiFi / BSSID）本轮仍只读：改白名单要现场抓 SSID，属排障动作，不放进移动端快捷调整。
 * TODO(扩展): 需要维护白名单时，把 wifiList 的增删行内编辑补在「WiFi 白名单」卡内。
 */
const VALIDATIONS = [
  { key: 'enableWifi', title: 'WiFi 校验', label: '当前连接的 WiFi 需在白名单内' },
  { key: 'enableLocation', title: '定位校验', label: '需处于电子围栏半径内' },
  { key: 'enableTimeWindow', title: '时间窗校验', label: '需在时段对应的打卡时间窗内' }
]

/** 频次只开放 2 / 4 两档：时段数 = 频次 / 2，与服务端校验（9107）同口径 */
const FREQUENCY_OPTIONS = [
  { value: 2, label: '2 次 · 单班次' },
  { value: 4, label: '4 次 · 上下午双班次' }
]
/** 扩为双时段的默认拆分：上午收 12:00、下午 14:00 起，中间留午休，天然满足「不重叠」 */
const DUAL_DEFAULTS = { firstEnd: '12:00', secondName: '下午班', secondStart: '14:00', secondEnd: '18:00' }

const PERIOD_NAME_MAX = 20
const CLOCK_RE = /^([01]\d|2[0-3]):[0-5]\d$/
const isEndClock = (value) => CLOCK_RE.test(String(value)) || String(value) === '24:00'

/** 去掉 stationId 的规则体：脏检查只看规则内容，驿站切换不算「改动」 */
function bodyOf(data) {
  const body = { ...data }
  delete body.stationId
  return body
}

const loading = ref(true)
const error = ref('')
const saving = ref(false)
const stations = ref([])
const stationId = ref(null)
const rule = ref(null)
const form = ref(null)
/** 已保存态的快照：用于「有无改动」判断，避免用户对着没改的表单反复保存 */
const original = ref('')

const periods = computed(() => (form.value ? form.value.checkPeriods : []))
/** 派生作息：取首个时段开始与末个时段结束，与服务端 saveRule 的重算规则一致 */
const workStartText = computed(() => (periods.value.length ? periods.value[0].startTime : '-'))
const workEndText = computed(() => (periods.value.length ? periods.value[periods.value.length - 1].endTime : '-'))

const showStation = ref(false)
const showPicker = ref(false)
const pickerValue = ref(['08', '00'])
/** 时间选择目标：第几个时段的哪一端（startTime / endTime） */
const pickerTarget = ref({ index: 0, field: 'startTime' })
const pickerTitle = computed(() => {
  const period = periods.value[pickerTarget.value.index]
  const tail = pickerTarget.value.field === 'startTime' ? '上班时间' : '下班时间'
  return period ? `「${period.name || '未命名'}」${tail}` : tail
})

const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '-'
})

const wifiText = computed(() => {
  const list = (rule.value && rule.value.wifiList) || []
  return list.length ? list.map((item) => `${item.ssid}${item.bssid ? `（${item.bssid}）` : ''}`).join('、') : '未配置'
})

/** 提交体：字段与 Mock 的 RULE_WRITABLE 白名单一一对应，派生字段（workStartTime / workEndTime）不发 */
const payload = computed(() => {
  if (!form.value) return null
  return {
    stationId: stationId.value,
    ruleName: form.value.ruleName.trim(),
    enableWifi: !!form.value.enableWifi,
    enableLocation: !!form.value.enableLocation,
    enableTimeWindow: !!form.value.enableTimeWindow,
    matchMode: form.value.matchMode,
    longitude: Number(form.value.longitude),
    latitude: Number(form.value.latitude),
    radius: Number(form.value.radius),
    checkFrequency: Number(form.value.checkFrequency),
    checkPeriods: form.value.checkPeriods.map((p) => ({
      name: p.name.trim(),
      startTime: p.startTime,
      endTime: p.endTime
    })),
    allowEarlyMin: Number(form.value.allowEarlyMin),
    allowLateMin: Number(form.value.allowLateMin),
    lateThresholdMin: Number(form.value.lateThresholdMin),
    earlyLeaveThresholdMin: Number(form.value.earlyLeaveThresholdMin)
  }
})

/**
 * 时段校验：与服务端 periodRuleError 同一套规则，前端先拦一次，
 * 免得填错一整屏表单要等服务端 9107 才知道哪儿不对。
 */
function periodError(list) {
  for (let i = 0; i < list.length; i += 1) {
    const period = list[i]
    const name = period.name.trim()
    if (!name) return `第 ${i + 1} 个时段的名称不能为空`
    if (name.length > PERIOD_NAME_MAX) return `时段名称最长 ${PERIOD_NAME_MAX} 个字符`
    if (!CLOCK_RE.test(period.startTime) || !isEndClock(period.endTime)) return `「${name}」的起止时间格式须为 HH:mm`
    const start = minutesOfDay(period.startTime)
    const end = minutesOfDay(period.endTime)
    if (start >= end) return `「${name}」的下班时间须晚于上班时间`
    // 必须升序且不重叠：periodIndex 是打卡去重与记录归属的定位键，乱序会让「第 1 段」指向下午
    if (i > 0 && start < minutesOfDay(list[i - 1].endTime)) return `「${name}」与上一时段重叠，请按时间先后顺序配置`
  }
  return ''
}

/** 表单校验：错误文案直接作为 ActionBar 的 note，用户不用点保存才知道哪里不对 */
const formError = computed(() => {
  const data = form.value
  if (!data) return ''
  if (!data.ruleName || !data.ruleName.trim()) return '规则名称不能为空'
  if (data.ruleName.trim().length > 50) return '规则名称最长 50 个字符'
  if (![2, 4].includes(Number(data.checkFrequency))) return '打卡频次仅支持 2 次或 4 次'
  if (data.checkPeriods.length !== Number(data.checkFrequency) / 2) {
    return `打卡时段数须为 ${Number(data.checkFrequency) / 2} 个（当前 ${data.checkPeriods.length} 个）`
  }
  const periodMsg = periodError(data.checkPeriods)
  if (periodMsg) return periodMsg
  if (!(Number(data.allowEarlyMin) >= 0)) return '允许提前打卡分钟数须不小于 0'
  if (!(Number(data.allowLateMin) >= 0)) return '允许延后打卡分钟数须不小于 0'
  const lng = Number(data.longitude)
  if (data.longitude === '' || !Number.isFinite(lng) || Math.abs(lng) > 180) return '经度须为 -180 ~ 180 的数字'
  const lat = Number(data.latitude)
  if (data.latitude === '' || !Number.isFinite(lat) || Math.abs(lat) > 90) return '纬度须为 -90 ~ 90 的数字'
  if (!(Number(data.radius) > 0)) return '围栏半径须大于 0'
  if (!(Number(data.lateThresholdMin) >= 0)) return '迟到阈值须不小于 0'
  if (!(Number(data.earlyLeaveThresholdMin) >= 0)) return '早退阈值须不小于 0'
  if (!data.enableWifi && !data.enableLocation && !data.enableTimeWindow)
    return '三项校验全部关闭时为免校验打卡，确认无误后再保存'
  return ''
})

const dirty = computed(() => {
  if (!payload.value || !original.value) return false
  return JSON.stringify(bodyOf(payload.value)) !== original.value
})

const actions = computed(() => [
  { key: 'save', label: '保存规则', plain: false, loading: saving.value, disabled: !dirty.value || !!formError.value }
])

async function loadStations() {
  const list = await getStationList()
  stations.value = list
  if (stationId.value == null && list.length) stationId.value = list[0].id
}

async function loadRule() {
  if (stationId.value == null) return
  loading.value = true
  error.value = ''
  try {
    const data = await getAttendanceRule({ stationId: stationId.value })
    rule.value = data
    // 兜底：规则若缺时段（历史脏数据），用上下班时间合成一个全天时段，页面仍可编辑后保存回正
    const list =
      Array.isArray(data.checkPeriods) && data.checkPeriods.length
        ? data.checkPeriods
        : [{ name: '全天班', startTime: data.workStartTime, endTime: data.workEndTime }]
    form.value = {
      ruleName: data.ruleName,
      enableWifi: data.enableWifi,
      enableLocation: data.enableLocation,
      enableTimeWindow: data.enableTimeWindow,
      matchMode: data.matchMode,
      longitude: String(data.longitude),
      latitude: String(data.latitude),
      radius: data.radius,
      checkFrequency: Number(data.checkFrequency) || list.length * 2,
      checkPeriods: list.map((p) => ({ name: p.name, startTime: p.startTime, endTime: p.endTime })),
      allowEarlyMin: data.allowEarlyMin,
      allowLateMin: data.allowLateMin,
      lateThresholdMin: data.lateThresholdMin,
      earlyLeaveThresholdMin: data.earlyLeaveThresholdMin
    }
    original.value = JSON.stringify(bodyOf(payload.value))
  } catch (e) {
    error.value = e.message || '加载失败'
    rule.value = null
    form.value = null
  } finally {
    loading.value = false
  }
}

async function load() {
  try {
    await loadStations()
  } catch (e) {
    loading.value = false
    error.value = e.message || '驿站列表加载失败'
    return
  }
  await loadRule()
}

function pickStation(item) {
  showStation.value = false
  if (item.id === stationId.value) return
  stationId.value = item.id
  loadRule()
}

/**
 * 频次切换：时段数是频次的派生（N = 频次 / 2），切换时必须同步增删时段，
 * 否则保存时会被服务端以「时段数与频次不匹配」拒掉（9107）。
 * 2 → 4：保留首段名称与开始时间，首段收在 12:00，补出下午段；4 → 2：只留首段。
 */
function setFrequency(value) {
  if (!form.value || form.value.checkFrequency === value) return
  form.value.checkFrequency = value
  const list = form.value.checkPeriods
  if (value === 4) {
    const base = list[0] || { name: '上午班', startTime: '08:00', endTime: '18:00' }
    // 原开始时间已过午，拆分后无法自洽（上午段会晚于 12:00 收），退回标准上午班
    const firstStart = minutesOfDay(base.startTime) < minutesOfDay(DUAL_DEFAULTS.firstEnd) ? base.startTime : '08:00'
    const secondEnd =
      minutesOfDay(base.endTime) > minutesOfDay(DUAL_DEFAULTS.secondStart) ? base.endTime : DUAL_DEFAULTS.secondEnd
    form.value.checkPeriods = [
      { name: base.name, startTime: firstStart, endTime: DUAL_DEFAULTS.firstEnd },
      { name: DUAL_DEFAULTS.secondName, startTime: DUAL_DEFAULTS.secondStart, endTime: secondEnd }
    ]
  } else {
    form.value.checkPeriods = [{ ...list[0] }]
  }
}

/** 时间串 → 选择器数组；24:00 收班超出选择器的 0-23 小时上限，退回 23:59 供用户重选 */
function toPickerValue(text) {
  const [h, m] = String(text || '').split(':')
  return Number(h) > 23 ? ['23', '59'] : [h || '00', m || '00']
}

function openPicker(index, field) {
  pickerTarget.value = { index, field }
  pickerValue.value = toPickerValue(form.value.checkPeriods[index][field])
  showPicker.value = true
}

function onPickerConfirm({ selectedValues }) {
  const { index, field } = pickerTarget.value
  form.value.checkPeriods[index][field] = selectedValues.join(':')
  showPicker.value = false
}

function toggle(key) {
  form.value[key] = !form.value[key]
}

async function onSave() {
  if (saving.value || formError.value || !dirty.value) return
  saving.value = true
  try {
    const vo = await saveAttendanceRule(payload.value)
    rule.value = vo
    original.value = JSON.stringify(bodyOf(payload.value))
    showSuccessToast('打卡规则已保存')
  } catch (e) {
    // 错误提示由 http 层统一弹出（9107 的时段原因在 message 里），页内不重复
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="rule-page">
    <PageNav title="打卡规则" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="8" @retry="load">
        <div v-if="form">
          <button type="button" class="station-pick card" @click="showStation = true">
            <span class="muted">当前驿站</span>
            <span class="station-pick__value"
              >{{ currentStationName }}<van-icon name="arrow" aria-hidden="true"
            /></span>
          </button>

          <div class="section-title">基础信息</div>
          <van-cell-group inset>
            <van-field v-model="form.ruleName" label="规则名称" placeholder="请输入规则名称" maxlength="50" />
          </van-cell-group>

          <div class="section-title">打卡频次<span class="section-title__extra">决定每日打卡几次</span></div>
          <div class="chip-row" role="radiogroup" aria-label="打卡频次">
            <button
              v-for="opt in FREQUENCY_OPTIONS"
              :key="opt.value"
              type="button"
              class="chip"
              :class="{ 'chip--active': form.checkFrequency === opt.value }"
              role="radio"
              :aria-checked="form.checkFrequency === opt.value"
              @click="setFrequency(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>

          <div class="section-title">
            <span>打卡时段</span>
            <span class="section-title__extra tabular-nums">{{ periods.length }} 个时段</span>
          </div>
          <van-cell-group inset>
            <template v-for="(period, index) in periods" :key="index">
              <van-field
                v-model="period.name"
                :label="`时段 ${index + 1}`"
                placeholder="如 上午班"
                :maxlength="PERIOD_NAME_MAX"
              />
              <van-cell title="上班时间" is-link :value="period.startTime" @click="openPicker(index, 'startTime')" />
              <van-cell title="下班时间" is-link :value="period.endTime" @click="openPicker(index, 'endTime')" />
            </template>
          </van-cell-group>
          <div class="card derived">
            <div class="derived__row">
              <span>作息时间（自动派生）</span>
              <span class="derived__value tabular-nums">{{ workStartText }} - {{ workEndText }}</span>
            </div>
            <p class="tip">作息取首个时段的开始时间与末个时段的结束时间，改时段即改作息，无需单独填写。</p>
          </div>

          <div class="section-title">打卡时间窗</div>
          <van-cell-group inset>
            <van-cell title="提前可打卡（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.allowEarlyMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
            <van-cell title="延后可打卡（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.allowLateMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
          </van-cell-group>
          <p class="tip">
            每个时段的打卡窗口 = 上班时间提前 {{ form.allowEarlyMin }} 分钟开放，下班时间延后
            {{ form.allowLateMin }} 分钟关闭； 各项校验全开时超出窗口会被拒绝（9102）。
          </p>

          <div class="section-title">迟到 / 早退阈值</div>
          <van-cell-group inset>
            <van-cell title="迟到阈值（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.lateThresholdMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
            <van-cell title="早退阈值（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.earlyLeaveThresholdMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
          </van-cell-group>

          <div class="section-title">校验项<span class="section-title__extra">关闭即不参与判定</span></div>
          <van-cell-group inset>
            <van-cell v-for="item in VALIDATIONS" :key="item.key" :title="item.title" :label="item.label" center>
              <template #right-icon>
                <van-switch
                  :model-value="form[item.key]"
                  size="24px"
                  :aria-label="`${item.title}开关`"
                  @click="toggle(item.key)"
                  @keydown.enter.prevent="toggle(item.key)"
                  @keydown.space.prevent="toggle(item.key)"
                />
              </template>
            </van-cell>
          </van-cell-group>

          <div class="chip-row" role="radiogroup" aria-label="校验项组合关系">
            <button
              v-for="mode in Object.keys(MATCH_MODE)"
              :key="mode"
              type="button"
              class="chip"
              :class="{ 'chip--active': form.matchMode === mode }"
              role="radio"
              :aria-checked="form.matchMode === mode"
              @click="form.matchMode = mode"
            >
              {{ MATCH_MODE[mode].label }}
            </button>
            <span class="chip-row__hint">多项校验之间的组合关系</span>
          </div>

          <div class="section-title">WiFi 白名单<span class="section-title__extra">只读</span></div>
          <div class="card">
            <p class="rule-text">{{ wifiText }}</p>
            <p class="tip">白名单需现场抓取 SSID 后维护，移动端仅查看；改白名单请走 PC 端。</p>
          </div>

          <div class="section-title">电子围栏</div>
          <van-cell-group inset>
            <van-field v-model="form.longitude" label="中心经度" type="number" placeholder="如 117.201000" />
            <van-field v-model="form.latitude" label="中心纬度" type="number" placeholder="如 31.821000" />
            <van-cell title="围栏半径（米）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.radius"
                  :min="50"
                  :max="5000"
                  :step="50"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>
          </van-cell-group>

          <p class="tip">规则更新时间：{{ rule.updateTime }}</p>
        </div>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="formError" :submitting="saving" @select="onSave" />

    <van-popup v-model:show="showStation" round position="bottom" safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet__title">选择驿站</div>
        <button
          v-for="item in stations"
          :key="item.id"
          type="button"
          class="sheet__item"
          :aria-pressed="item.id === stationId"
          @click="pickStation(item)"
        >
          <span>{{ item.stationName }}</span>
          <van-icon v-if="item.id === stationId" name="passed" aria-hidden="true" />
        </button>
      </div>
    </van-popup>

    <!-- 时间选择统一一个底部弹层：多时段下不复用两套 picker，改由 pickerTarget 定位写入 -->
    <van-popup v-model:show="showPicker" round position="bottom" safe-area-inset-bottom>
      <van-time-picker
        v-model="pickerValue"
        :title="pickerTitle"
        :columns-type="['hour', 'minute']"
        @confirm="onPickerConfirm"
        @cancel="showPicker = false"
      />
    </van-popup>
  </div>
</template>

<style scoped>
.station-pick {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 52px;
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--text-1);
}

.station-pick__value {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  color: var(--color-primary);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  align-items: center;
  margin-top: var(--sp-3);
}

/* chip 主触控目标 ≥44（7.4），样式复用全局 .chip / .chip--active */
.chip-row__hint {
  flex: 1;
  min-width: 120px;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.derived {
  margin-top: var(--sp-3);
}

.derived__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  font-size: var(--fs-body);
  color: var(--text-2);
}

.derived__value {
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.rule-text {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-1);
  word-break: break-all;
}

.sheet {
  padding: var(--sp-5) 0 var(--sp-6);
}

.sheet__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.sheet__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-body-strong);
  color: var(--text-1);
  background: none;
  border: none;
  border-top: 1px solid var(--border-line);
}
</style>
