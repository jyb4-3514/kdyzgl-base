<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { MATCH_MODE } from '@kdyzgl/shared/constants/dict'
import { clockOfMinutes, minutesOfDay } from '../../../utils/format.js'

/**
 * 打卡规则配置卡片（考勤管理页 C-P8 表单规范落地）
 *
 * 为什么独立成组件：规则是「一栈一档」的完整表单（频次 + 时段 + 校验项 + 阈值），
 * 与同页的打卡记录列表没有任何依赖关系，混在一个文件里会让页面同时承担两套职责。
 *
 * 时段是唯一真源：打卡频次决定时段数量（频次 / 2），时段决定上下班时间（首段开始 / 末段结束），
 * 因此上下班时间在本卡片里只做只读派生展示，不再提供独立输入框——两处都能填必然打架。
 *
 * 校验口径与被写入字段严格对齐 shared/mock/routes/attendance.js 的 validateRule + periodRuleError：
 * 只校验服务端会拒的项，不自造更严的规则（否则会出现「前端拦下、后端其实接受」的假报错）。
 */
const props = defineProps({
  rule: { type: Object, default: null },
  stationId: { type: Number, default: null },
  stationName: { type: String, default: '' },
  // 写权限由服务端角色决定（PUT /attendance/rule 仅 ADMIN），非 ADMIN 渲染为只读表单
  canWrite: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  // 9101：该驿站尚未配置规则，此时用模板兜底，保存即创建
  missing: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'retry'])

/** 单时段名称：一个时段即全天一班，与 attendanceStore 的单时段预设同口径 */
const SINGLE_PERIOD_NAME = '全天班'
/** 单段时长 4 小时、两段之间留 2 小时午休：与 attendanceStore 的双时段预设同构 */
const PERIOD_DURATION_MIN = 240
const LUNCH_GAP_MIN = 120
const DUAL_SPAN_MIN = PERIOD_DURATION_MIN * 2 + LUNCH_GAP_MIN

/** 频次档位：2 次 = 1 段（上下班各一次），4 次 = 2 段（上午班 + 下午班各上下班） */
const FREQUENCY_OPTIONS = [
  { value: 2, label: '2 次（单班次，上下班各一次）' },
  { value: 4, label: '4 次（双班次，上午班 + 下午班各上下班）' }
]

/** 双时段兜底预设：原上班时间排不下「两段 + 午休」时退回标准排法，保证切档后不会立刻非法 */
const DUAL_PERIOD_PRESET = [
  { name: '上午班', startTime: '08:00', endTime: '12:00' },
  { name: '下午班', startTime: '14:00', endTime: '18:00' }
]

const seedPeriods = () => [{ name: SINGLE_PERIOD_NAME, startTime: '08:00', endTime: '18:00' }]

/**
 * 未配置规则时的表单模板（9101）
 * 默认值与 attendanceStore.ruleSeed 保持同口径，只有围栏坐标留空由管理员现场填——
 * 替用户编造一套坐标会导致打卡判定全部落在错误地点上。
 */
const DEFAULT_FORM = {
  ruleName: '',
  enableWifi: true,
  enableLocation: true,
  enableTimeWindow: true,
  matchMode: 'ALL',
  wifiList: [{ ssid: '', bssid: '' }],
  longitude: null,
  latitude: null,
  radius: 300,
  checkFrequency: 2,
  checkPeriods: seedPeriods(),
  allowEarlyMin: 30,
  allowLateMin: 60,
  lateThresholdMin: 30,
  earlyLeaveThresholdMin: 30,
  status: 1
}

const formRef = ref(null)
// 数组字段必须深拷贝：直接沿用模板常量的话，编辑时段会改到 DEFAULT_FORM 本体，还原与新建都会带上脏值
const form = reactive({ ...DEFAULT_FORM, wifiList: [{ ssid: '', bssid: '' }], checkPeriods: seedPeriods() })

const formRules = {
  ruleName: [
    { required: true, message: '请输入规则名称', trigger: 'blur' },
    { min: 1, max: 50, message: '规则名称长度须为 1-50', trigger: 'blur' }
  ],
  longitude: [{ required: true, message: '请输入围栏经度', trigger: 'blur' }],
  latitude: [{ required: true, message: '请输入围栏纬度', trigger: 'blur' }],
  radius: [{ required: true, validator: positiveValidator('围栏半径须大于 0'), trigger: 'blur' }],
  lateThresholdMin: [{ required: true, validator: nonNegativeValidator('迟到阈值须不小于 0'), trigger: 'blur' }],
  earlyLeaveThresholdMin: [{ required: true, validator: nonNegativeValidator('早退阈值须不小于 0'), trigger: 'blur' }]
}

/**
 * 数值校验：自定义 validator 会绕过 required 检查，因此空值必须自己拦下——
 * 否则清空输入框会被 Number(null) === 0 悄悄放行（围栏半径 0 会被服务端拒，阈值 0 则直接写进规则）
 */
function positiveValidator(message) {
  return (_rule, value, callback) =>
    value !== null && value !== '' && Number(value) > 0 ? callback() : callback(new Error(message))
}

function nonNegativeValidator(message) {
  return (_rule, value, callback) =>
    value !== null && value !== '' && Number(value) >= 0 ? callback() : callback(new Error(message))
}

const isTemplate = computed(() => props.missing && !props.rule)

/** 服务端值 → 表单：wifiList 与 checkPeriods 必须深拷贝，否则输入会直接改到 props 里的对象 */
function fill(data) {
  const source = data || { ...DEFAULT_FORM, ruleName: `${props.stationName || ''}默认打卡规则` }
  const rawPeriods = Array.isArray(source.checkPeriods) && source.checkPeriods.length ? source.checkPeriods : null
  Object.assign(form, {
    ruleName: source.ruleName || '',
    enableWifi: !!source.enableWifi,
    enableLocation: !!source.enableLocation,
    enableTimeWindow: !!source.enableTimeWindow,
    matchMode: source.matchMode || 'ALL',
    wifiList: (source.wifiList && source.wifiList.length ? source.wifiList : [{ ssid: '', bssid: '' }]).map((item) => ({
      ssid: item.ssid || '',
      bssid: item.bssid || ''
    })),
    longitude: source.longitude ?? null,
    latitude: source.latitude ?? null,
    radius: source.radius ?? DEFAULT_FORM.radius,
    checkFrequency: Number(source.checkFrequency) === 4 ? 4 : 2,
    // 没带时段的历史规则按上下班时间合成一段，保证表单里永远有可编辑的时段行
    checkPeriods: (
      rawPeriods || [
        { name: SINGLE_PERIOD_NAME, startTime: source.workStartTime || '08:00', endTime: source.workEndTime || '18:00' }
      ]
    ).map((item) => ({ name: item.name || '', startTime: item.startTime, endTime: item.endTime })),
    allowEarlyMin: source.allowEarlyMin ?? DEFAULT_FORM.allowEarlyMin,
    allowLateMin: source.allowLateMin ?? DEFAULT_FORM.allowLateMin,
    lateThresholdMin: source.lateThresholdMin ?? DEFAULT_FORM.lateThresholdMin,
    earlyLeaveThresholdMin: source.earlyLeaveThresholdMin ?? DEFAULT_FORM.earlyLeaveThresholdMin,
    status: source.status ?? 1
  })
  if (formRef.value) formRef.value.clearValidate()
}

watch(
  () => props.rule,
  (value) => fill(value),
  { immediate: true }
)
// 切驿站时若新驿站没有规则，props.rule 仍为 null，需要靠 missing 触发模板填充
watch(
  () => props.missing,
  (value) => value && fill(null)
)
watch(
  () => props.stationName,
  () => isTemplate.value && fill(null)
)

/**
 * 时段预校验：口径与后端 periodRuleError 逐条对齐（命中即 9107），
 * 目的是把必然被拒的请求拦在本地，不让管理员白等一次往返。
 */
function validatePeriods() {
  const need = Number(form.checkFrequency) / 2
  if (form.checkPeriods.length !== need) return `打卡频次 ${form.checkFrequency} 次须配置 ${need} 个时段`
  let prevEnd = -1
  for (const [index, item] of form.checkPeriods.entries()) {
    if (!String(item.name || '').trim()) return `第 ${index + 1} 段的时段名称不能为空`
    const label = `「${item.name.trim()}」`
    if (!item.startTime || !item.endTime) return `${label}的上下班时间不能为空`
    const start = minutesOfDay(item.startTime)
    const end = minutesOfDay(item.endTime)
    if (start >= end) return `${label}的结束时间须晚于开始时间`
    // 必须升序且互不重叠：periodIndex 是打卡去重与记录归属的定位键，乱序会让「第 1 段」指向下午
    if (start < prevEnd) return '打卡时段之间不允许重叠，且须按开始时间升序'
    prevEnd = end
  }
  return ''
}

const periodError = computed(validatePeriods)

/** 上下班时间为派生值：首段开始 / 末段结束，与 attendanceStore.saveRule 的重算口径一致 */
const derivedRange = computed(() => {
  const list = form.checkPeriods
  if (!list.length) return '—'
  return `${list[0].startTime} ~ ${list[list.length - 1].endTime}`
})

/** 2 → 4：以现有上班时间为锚点对半铺开，直接落 14:00 默认值会与原时段重叠，等于把错误甩给用户 */
function growToDual() {
  const start = minutesOfDay(form.checkPeriods[0] && form.checkPeriods[0].startTime)
  if (!Number.isFinite(start) || start + DUAL_SPAN_MIN > 24 * 60) {
    form.checkPeriods = DUAL_PERIOD_PRESET.map((item) => ({ ...item }))
    return
  }
  form.checkPeriods = [
    { name: '上午班', startTime: form.checkPeriods[0].startTime, endTime: clockOfMinutes(start + PERIOD_DURATION_MIN) },
    {
      name: '下午班',
      startTime: clockOfMinutes(start + PERIOD_DURATION_MIN + LUNCH_GAP_MIN),
      endTime: clockOfMinutes(start + DUAL_SPAN_MIN)
    }
  ]
}

/** 4 → 2：保留「首段上班 + 末段下班」的跨度，收敛档位时不缩小已配好的工时范围 */
function mergeToSingle() {
  const list = form.checkPeriods
  if (!list.length) {
    form.checkPeriods = seedPeriods()
    return
  }
  form.checkPeriods = [
    { name: SINGLE_PERIOD_NAME, startTime: list[0].startTime, endTime: list[list.length - 1].endTime }
  ]
}

function handleFrequencyChange(value) {
  if (value === 4) growToDual()
  else mergeToSingle()
}

function addWifi() {
  form.wifiList.push({ ssid: '', bssid: '' })
}

function removeWifi(index) {
  form.wifiList.splice(index, 1)
  // 至少留一行：白名单被清空时服务端会接收空数组，但界面上会失去新增入口
  if (!form.wifiList.length) addWifi()
}

async function handleSubmit() {
  if (!props.canWrite) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return // 校验失败提示由 el-form 逐项给出
  }
  const blank = form.wifiList.findIndex((item) => !String(item.ssid).trim())
  if (blank >= 0) {
    ElMessage.warning(`WiFi 白名单第 ${blank + 1} 行缺少 SSID`)
    return
  }
  if (periodError.value) {
    ElMessage.warning(periodError.value)
    return
  }
  emit('save', {
    stationId: props.stationId,
    ruleName: form.ruleName.trim(),
    enableWifi: form.enableWifi,
    enableLocation: form.enableLocation,
    enableTimeWindow: form.enableTimeWindow,
    matchMode: form.matchMode,
    wifiList: form.wifiList.map((item) => ({
      ssid: String(item.ssid).trim(),
      bssid: String(item.bssid || '').trim() || null
    })),
    longitude: form.longitude,
    latitude: form.latitude,
    radius: form.radius,
    checkFrequency: form.checkFrequency,
    checkPeriods: form.checkPeriods.map((item) => ({
      name: String(item.name).trim(),
      startTime: item.startTime,
      endTime: item.endTime
    })),
    allowEarlyMin: form.allowEarlyMin,
    allowLateMin: form.allowLateMin,
    // 不上报上下班时间：它是时段的派生值，由服务端按 checkPeriods 重算，两个口径一起提交必然打架
    lateThresholdMin: form.lateThresholdMin,
    earlyLeaveThresholdMin: form.earlyLeaveThresholdMin,
    status: form.status
  })
}

/** 还原为服务端当前值：改一半想撤回时不必刷新整页 */
function handleReset() {
  fill(props.rule)
}
</script>

<template>
  <el-card shadow="never" class="rule-card">
    <template #header>
      <div class="rule-card__head">
        <div>
          <span class="rule-card__title">打卡规则配置</span>
          <span v-if="stationName" class="rule-card__station">{{ stationName }}</span>
        </div>
        <div class="rule-card__meta">
          <span class="rule-card__time">{{ rule ? `更新于 ${rule.updateTime}` : '尚未配置' }}</span>
          <span v-if="!canWrite" class="rule-card__readonly">只读：规则仅超级管理员可修改</span>
          <el-button v-if="canWrite" :icon="Refresh" text @click="handleReset">还原</el-button>
        </div>
      </div>
    </template>

    <StateBlock v-if="error" variant="error" title="打卡规则加载失败" @action="emit('retry')" />

    <div v-else v-loading="loading" class="rule-card__body">
      <p v-if="isTemplate" class="rule-card__notice">
        该驿站尚未配置打卡规则：填写并保存后即按当前内容创建（围栏坐标需现场填写，不能沿用其他驿站）
      </p>

      <el-form ref="formRef" :model="form" :rules="formRules" :disabled="!canWrite" label-width="96px">
        <section class="rule-section">
          <h3 class="rule-section__title">基础信息</h3>
          <el-form-item label="规则名称" prop="ruleName">
            <el-input
              v-model.trim="form.ruleName"
              maxlength="50"
              show-word-limit
              placeholder="如：城东驿站默认打卡规则"
              class="field-name"
            />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            <span class="field-hint">停用后本站员工无法完成打卡校验</span>
          </el-form-item>
        </section>

        <section class="rule-section">
          <h3 class="rule-section__title">打卡频次与时段</h3>
          <el-form-item label="打卡频次">
            <el-radio-group v-model="form.checkFrequency" @change="handleFrequencyChange">
              <el-radio-button v-for="item in FREQUENCY_OPTIONS" :key="item.value" :value="item.value">{{
                item.label
              }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <!-- 时段行数由频次决定（频次 / 2），故不做增删按钮，改档位即改行数 -->
          <el-form-item v-for="(item, index) in form.checkPeriods" :key="index" :label="`时段 ${index + 1}`">
            <div class="period-row">
              <el-input v-model.trim="item.name" maxlength="20" placeholder="时段名称" class="field-period-name" />
              <el-time-select
                v-model="item.startTime"
                start="00:00"
                end="23:30"
                step="00:30"
                placeholder="上班时间"
                class="field-time"
              />
              <span class="field-hint">至</span>
              <el-time-select
                v-model="item.endTime"
                start="00:00"
                end="24:00"
                step="00:30"
                include-end-time
                placeholder="下班时间"
                class="field-time"
              />
            </div>
          </el-form-item>
          <!-- 校验失败用 role="alert" 就地播报：时段是唯一真源，配错必须当场说清是哪一段不合法 -->
          <p v-if="periodError" class="period-error" role="alert">{{ periodError }}</p>
          <el-form-item v-else>
            <span class="field-hint field-hint--block">
              每个时段各需一次上班卡与一次下班卡；时段须按开始时间升序且互不重叠，跨零点班次暂不支持
            </span>
          </el-form-item>
        </section>

        <section class="rule-section">
          <h3 class="rule-section__title">校验项与组合逻辑</h3>
          <el-form-item label="校验项">
            <div class="switch-group">
              <el-switch v-model="form.enableWifi" /><span class="switch-group__label">启用 WiFi 校验</span>
              <el-switch v-model="form.enableLocation" /><span class="switch-group__label">启用定位校验</span>
              <el-switch v-model="form.enableTimeWindow" /><span class="switch-group__label">启用时间窗校验</span>
            </div>
          </el-form-item>
          <el-form-item label="组合逻辑">
            <el-radio-group v-model="form.matchMode">
              <el-radio-button v-for="(item, key) in MATCH_MODE" :key="key" :value="key">{{
                item.label
              }}</el-radio-button>
            </el-radio-group>
            <span class="field-hint">全部满足 = WiFi 与定位都命中才放行；任一满足 = 命中任意一项即放行</span>
          </el-form-item>
        </section>

        <section class="rule-section">
          <h3 class="rule-section__title">WiFi 白名单</h3>
          <el-form-item v-for="(item, index) in form.wifiList" :key="index" :label="`白名单 ${index + 1}`">
            <div class="wifi-row">
              <el-input v-model.trim="item.ssid" placeholder="SSID，如 ST001-Express" class="field-ssid" />
              <el-input
                v-model.trim="item.bssid"
                placeholder="BSSID（选填），如 AC:84:C6:00:00:01"
                class="field-bssid"
              />
              <el-button link type="danger" :disabled="!canWrite" @click="removeWifi(index)">删除</el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button :icon="Plus" :disabled="!canWrite" @click="addWifi">添加白名单</el-button>
          </el-form-item>
        </section>

        <section class="rule-section">
          <h3 class="rule-section__title">电子围栏</h3>
          <el-form-item label="围栏中心">
            <div class="fence-row">
              <el-input-number
                v-model="form.longitude"
                :precision="6"
                :step="0.001"
                :controls="false"
                placeholder="经度"
                class="field-coord"
              />
              <el-input-number
                v-model="form.latitude"
                :precision="6"
                :step="0.001"
                :controls="false"
                placeholder="纬度"
                class="field-coord"
              />
              <el-input-number v-model="form.radius" :min="1" :max="5000" :step="50" class="field-radius" />
              <span class="field-hint">半径（米）</span>
            </div>
          </el-form-item>
          <el-form-item>
            <span class="field-hint field-hint--block">
              坐标为围栏中心的经纬度；打卡时按 Haversine 球面距离判定是否落在围栏内
            </span>
          </el-form-item>
        </section>

        <section class="rule-section">
          <h3 class="rule-section__title">工时与阈值</h3>
          <el-form-item label="上下班时间">
            <span class="derived-range">{{ derivedRange }}</span>
            <span class="field-hint">由首段上班时间与末段下班时间自动派生，不再单独填写</span>
          </el-form-item>
          <el-form-item label="允许提前打卡">
            <el-input-number v-model="form.allowEarlyMin" :min="0" :max="240" :step="5" class="field-threshold" />
            <span class="field-hint">分钟，早于所在时段开始时间也放行</span>
          </el-form-item>
          <el-form-item label="允许延后打卡">
            <el-input-number v-model="form.allowLateMin" :min="0" :max="240" :step="5" class="field-threshold" />
            <span class="field-hint">分钟，晚于所在时段结束时间也放行</span>
          </el-form-item>
          <el-form-item>
            <span class="field-hint field-hint--block">
              时间窗：每个时段在「开始时间 − 允许提前」到「结束时间 + 允许延后」之间可打卡（需启用时间窗校验）
            </span>
          </el-form-item>
          <el-form-item label="迟到阈值">
            <el-input-number v-model="form.lateThresholdMin" :min="0" :max="240" :step="5" class="field-threshold" />
            <span class="field-hint">分钟，晚于所在时段开始时间超过该值记为迟到</span>
          </el-form-item>
          <el-form-item label="早退阈值">
            <el-input-number
              v-model="form.earlyLeaveThresholdMin"
              :min="0"
              :max="240"
              :step="5"
              class="field-threshold"
            />
            <span class="field-hint">分钟，早于所在时段结束时间超过该值记为早退</span>
          </el-form-item>
        </section>
      </el-form>
    </div>

    <template v-if="canWrite && !error" #footer>
      <div class="rule-card__footer">
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存规则</el-button>
        <span class="field-hint">保存后立即生效于本站后续打卡判定</span>
      </div>
    </template>
  </el-card>
</template>

<style scoped lang="scss">
.rule-card {
  margin-bottom: var(--sp-4);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);
  }

  &__title {
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__station {
    margin-left: var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }

  &__time {
    font-size: var(--fs-caption);
    color: var(--text-3);
    font-variant-numeric: tabular-nums;
  }

  &__readonly {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__body {
    min-height: 120px;
  }

  // 未配置规则时的提示条：用警告族浅底，与错误态（危险族）区分开
  &__notice {
    margin: 0 0 var(--sp-4);
    padding: var(--sp-2) var(--sp-3);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-sm);
    background-color: var(--state-warning-bg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--state-warning-fg);
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }
}

.rule-section {
  padding-bottom: var(--sp-2);

  & + & {
    padding-top: var(--sp-4);
    border-top: 1px solid var(--border-line);
  }

  &__title {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-caption);
    font-weight: var(--fw-semibold);
    color: var(--text-2);
  }

  :deep(.el-form-item) {
    margin-bottom: var(--sp-3);
  }
}

.field-name {
  width: 280px;
}

.field-hint {
  margin-left: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--text-3);

  // 独占一行的说明（不跟在控件后面）
  &--block {
    margin-left: 0;
    line-height: var(--lh-caption);
  }
}

.switch-group {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  &__label {
    margin-right: var(--sp-4);
    font-size: var(--fs-body);
    color: var(--text-2);
  }
}

.wifi-row,
.fence-row,
.period-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

// 一段里要放「名称 + 上班时间 + 至 + 下班时间」，窄屏换行而不是撑破卡片
.period-row {
  flex-wrap: wrap;
}

// 时段校验失败：就地红字提示，位置紧跟时段行，与 9107 的服务端文案同源
.period-error {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--state-danger-fg);
}

// 上下班时间是派生值，用正文色 + 等宽数字，避免被误认成可编辑控件
.derived-range {
  font-size: var(--fs-body);
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
}

.field-period-name {
  width: 160px;
}

.field-ssid {
  width: 240px;
}

.field-bssid {
  width: 240px;
}

.field-coord {
  width: 140px;
}

.field-radius {
  width: 120px;
}

.field-time {
  width: 140px;
}

.field-threshold {
  width: 120px;
}
</style>
