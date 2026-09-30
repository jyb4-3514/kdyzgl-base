<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { MATCH_MODE } from '@kdyzgl/shared/constants/dict'

/**
 * 打卡规则配置卡片（考勤管理页 C-P8 表单规范落地）
 *
 * 为什么独立成组件：规则是「一栈一档」的完整表单（校验项 + 白名单 + 围栏 + 阈值），
 * 与同页的打卡记录列表没有任何依赖关系，混在一个文件里会让页面同时承担两套职责。
 *
 * 打卡时间真源统一（本次变更）：打卡时段与上下班时间**不再在规则里单独配置**，而是读取时由
 * 「该驿站班次」实时派生（`checkPeriods` / `workStartTime` / `workEndTime` / `checkFrequency` 均为只读出参）。
 * 因此本卡片移除时段编辑与频次选择，改为只读展示 + 「去维护班次」引导，提交也不再上报这些字段（U-4/U-5）。
 * 校验口径与被写入字段严格对齐 shared/mock/routes/attendance.js 的 validateRule：
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

const router = useRouter()

/** 跳转排班页班次管理区维护班次：班次是打卡时段的唯一来源，改时段请在此维护 */
function goShiftPage() {
  router.push({ name: 'Schedule' })
}

/**
 * 未配置规则时的表单模板（9101）
 * 默认值与被写入字段同口径；时段/频次为派生只读值，不进模板，避免出现「能填但其实不生效」的假入口。
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
  allowEarlyMin: 30,
  allowLateMin: 60,
  lateThresholdMin: 30,
  earlyLeaveThresholdMin: 30,
  status: 1
}

const formRef = ref(null)
// 数组字段必须深拷贝：直接沿用模板常量的话，编辑白名单会改到 DEFAULT_FORM 本体，还原与新建都会带上脏值
const form = reactive({ ...DEFAULT_FORM, wifiList: [{ ssid: '', bssid: '' }] })

/** 派生只读：打卡时段列表（由该驿站班次决定，后端 checkPeriods 出参） */
const periods = computed(() => {
  const list = props.rule && Array.isArray(props.rule.checkPeriods) ? props.rule.checkPeriods : []
  return list.map((item) => ({ name: item.name || '未命名班次', startTime: item.startTime, endTime: item.endTime }))
})

/** 派生只读：打卡频次（= 启用班次数 × 2），按「2 次（1 个班次）/ 4 次（2 个班次）」展示 */
const frequencyText = computed(() => {
  const count = periods.value.length
  if (!count) return '—'
  return `${count * 2} 次（${count} 个班次）`
})

/** 派生只读：上下班时间（首段开始 / 末段结束），来自后端 workStartTime / workEndTime 出参 */
const derivedRange = computed(() => {
  const rule = props.rule
  if (rule && rule.workStartTime && rule.workEndTime) return `${rule.workStartTime} ~ ${rule.workEndTime}`
  const list = periods.value
  if (!list.length) return '—'
  return `${list[0].startTime} ~ ${list[list.length - 1].endTime}`
})

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

/** 服务端值 → 表单：wifiList 必须深拷贝，否则输入会直接改到 props 里的对象 */
function fill(data) {
  const source = data || { ...DEFAULT_FORM, ruleName: `${props.stationName || ''}默认打卡规则` }
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
    allowEarlyMin: form.allowEarlyMin,
    allowLateMin: form.allowLateMin,
    lateThresholdMin: form.lateThresholdMin,
    earlyLeaveThresholdMin: form.earlyLeaveThresholdMin,
    status: form.status
    // 不上报 checkPeriods / checkFrequency / workStartTime / workEndTime：它们由该驿站班次派生（U-4/U-5），
    // 提交会被服务端以 400 拒绝；时段维护入口统一在「排班管理 → 班次管理」。
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
            <span class="derived-range">{{ frequencyText }}</span>
            <span class="field-hint">由该驿站启用班次派生（每个班次上下班各一次），不可在此修改</span>
          </el-form-item>
          <el-form-item label="打卡时段">
            <div class="period-readonly">
              <!-- 只读展示：时段名称与起止全部取自该驿站班次（时段真源统一后规则侧不再可编辑） -->
              <template v-if="periods.length">
                <div v-for="(item, index) in periods" :key="index" class="period-readonly__row">
                  <span class="period-readonly__name">{{ item.name }}</span>
                  <span class="period-readonly__time tabular-nums">{{ item.startTime }} - {{ item.endTime }}</span>
                </div>
              </template>
              <span v-else class="field-hint">该驿站尚未配置启用班次，暂无打卡时段</span>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button v-if="canWrite" link type="primary" @click="goShiftPage">去维护班次</el-button>
            <span class="field-hint field-hint--block">
              打卡时段与上下班时间由该驿站班次决定（唯一时间真源）：在「排班管理 → 班次管理」新增/调整班次后，本站打卡时间即时跟随。
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
            <span class="field-hint">由该驿站班次自动派生（首段开始 / 末段结束），不再单独填写</span>
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
.fence-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

// 打卡时段只读展示：一行一班的「班次名 + 起止」，用正文色 + 等宽数字，避免被误认成可编辑控件
.period-readonly {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);

  &__row {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }

  &__name {
    min-width: 96px;
    font-size: var(--fs-body);
    color: var(--text-1);
  }

  &__time {
    font-size: var(--fs-body);
    color: var(--text-2);
  }
}

// 上下班时间是派生值，用正文色 + 等宽数字，避免被误认成可编辑控件
.derived-range {
  font-size: var(--fs-body);
  color: var(--text-1);
  font-variant-numeric: tabular-nums;
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

.field-threshold {
  width: 120px;
}
</style>
