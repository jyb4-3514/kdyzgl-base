<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StationPicker from '@kdyzgl/shared/ui/StationPicker.vue'
import { createShift, getShifts, updateShift } from '@/api/attendance.js'
import { getStationList } from '@/api/org.js'

/**
 * B12 班次管理 · 新增 / 编辑（共用一页，按 route.params.id 判模式；设计 ⑭.3）
 *
 * 校验分三层，口径与 PC ShiftManager 逐条一致：
 *  A 字段级 → 前置阻断（名称 / 保留名 / 起止 / 结束晚于开始 / 休息时长）；
 *  B 站点级 → 前置阻断（保存后启用数 >2 / 一早一晚冲突）；
 *  C 站点级 → **仅提示不阻断**（同站启用班次时间重叠，属排班侧 9110 语义，定义侧不额外收紧）。
 * 以上均为前置体验，**真实拦截以后端 9114 / 400 为准**；保存失败时就地落 note，不清空表单。
 */
const route = useRoute()
const router = useRouter()
const shiftId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => shiftId.value != null)

/** 班次名保留字：= 后端 legacyPeriodSentinel / DEFAULT_PERIOD_NAME「全天班」 */
const RESERVED_SHIFT_NAMES = ['全天班']
/** 启用班次上限：打卡时段最多 2 段（一早一晚），超限服务端回 9114 */
const MAX_ENABLED_SHIFTS = 2
/** 午前 / 午后界值（分钟）：与后端 middayBoundaryMinute 默认值同口径；界面不出现该常量字面量 */
const MIDDAY_BOUNDARY_MIN = 12 * 60

/**
 * 班次配色只允许设计 Token 色板（⑭.3-(4)）：早班蓝 blue-700、中班橙 orange-500、晚班深灰 neutral-800。
 * value 是接口要的 #RRGGBB 字面量，token 是渲染色块用的 CSS 变量，模板里不写死色值。
 */
const SHIFT_COLORS = [
  { value: '#0958D9', token: '--c-blue-700', label: '早班蓝' },
  { value: '#FA8C16', token: '--c-orange-500', label: '中班橙' },
  { value: '#1F2937', token: '--c-neutral-800', label: '晚班深灰' }
]

const loading = ref(false)
const error = ref('')
const submitting = ref(false)
/** 服务端口径失败（400 / 9114）：就地展示，不清空表单（设计 ⑭.3-(7)） */
const serverError = ref('')

const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const stationId = ref(null)
const showStation = ref(false)

/** 该驿站全部班次（含停用）：B / C 组站点级预算的基准 */
const siblings = ref([])

const form = ref({
  shiftName: '',
  startTime: '08:00',
  endTime: '16:00',
  color: SHIFT_COLORS[0].value,
  restMinutes: 60,
  status: 1
})

const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '-'
})

const minutesOfDay = (text) => {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  return Number.isFinite(h) && Number.isFinite(m) ? h * 60 + m : NaN
}

/** 班次开始时间落点：午前(0) / 午后(1)，与后端 ordinal 口径一致 */
const ordinalOf = (startTime) => (minutesOfDay(startTime) < MIDDAY_BOUNDARY_MIN ? 0 : 1)

/* ---------- A 字段级校验（前置阻断） ---------- */

const nameError = computed(() => {
  const name = form.value.shiftName.trim()
  if (!name) return '请输入班次名称'
  if (name.length > 20) return '班次名称长度须为 1-20'
  if (RESERVED_SHIFT_NAMES.includes(name)) return '班次名不得使用保留名「全天班」'
  return ''
})

const timeError = computed(() => {
  if (!form.value.startTime) return '请选择开始时间'
  if (!form.value.endTime) return '请选择结束时间'
  if (minutesOfDay(form.value.startTime) >= minutesOfDay(form.value.endTime)) return '结束时间须晚于开始时间'
  return ''
})

const restError = computed(() => (Number(form.value.restMinutes) >= 0 ? '' : '休息时长须不小于 0'))

/* ---------- B / C 站点级校验 ---------- */

/** 保存后的班次全集：新增追加 / 编辑替换当前行，用于站点级预算 */
const rowsAfterSave = computed(() => {
  const row = { id: shiftId.value == null ? 0 : shiftId.value, ...form.value }
  if (!isEdit.value) return [...siblings.value, row]
  return siblings.value.map((item) => (item.id === shiftId.value ? row : item))
})

/** B 组前置阻断：启用数 >2 或启用班次 ordinal 冲突（一早一晚），命中即不提交 */
const stationError = computed(() => {
  const enabled = rowsAfterSave.value.filter((item) => item.status === 1)
  if (enabled.length > MAX_ENABLED_SHIFTS) {
    return `启用班次最多 ${MAX_ENABLED_SHIFTS} 个（当前将达 ${enabled.length} 个），请先停用多余班次`
  }
  const ordinals = enabled.map((item) => ordinalOf(item.startTime))
  if (new Set(ordinals).size !== ordinals.length) {
    return '启用班次须一早一晚：新班次与已有班次时段归属相同（请调整开始时间，或停用其一）'
  }
  return ''
})

/** 半开区间重叠判定 [s,e)：与排班侧 9110 同口径 */
function hasOverlap(rows) {
  const enabled = rows
    .filter((item) => item.status === 1)
    .map((item) => ({ s: minutesOfDay(item.startTime), e: minutesOfDay(item.endTime) }))
  for (let i = 0; i < enabled.length; i += 1) {
    for (let j = i + 1; j < enabled.length; j += 1) {
      if (enabled[i].s < enabled[j].e && enabled[j].s < enabled[i].e) return true
    }
  }
  return false
}

/** C 组仅提示：同站启用班次时间重叠，仍可保存 */
const overlapHint = computed(() =>
  hasOverlap(rowsAfterSave.value) ? '存在启用班次时间重叠，可能影响打卡时段与排班判定，请确认后再保存' : ''
)

/* ---------- 时间选择（van-time-picker，30 分钟粒度，结束允许 24:00） ---------- */

const startValue = ref(['08', '00'])
const endValue = ref(['16', '00'])
const showStartPicker = ref(false)
const showEndPicker = ref(false)

/**
 * Vant 4 TimePicker 的 filter：(columnType, options, values) => options。
 * 分钟列限为 00 / 30；结束时间小时列追加 24:00（且 24 点的分钟只留 00），与 PC step=00:30 同口径。
 */
function makeTimeFilter(allowEnd) {
  return (columnType, options, values) => {
    if (columnType === 'hour') {
      return allowEnd ? options.concat({ text: '24', value: '24' }) : options
    }
    if (columnType === 'minute') {
      if (allowEnd && values && values[0] === '24') return [{ text: '00', value: '00' }]
      return options.filter((option) => option.value === '00' || option.value === '30')
    }
    return options
  }
}
const startFilter = makeTimeFilter(false)
const endFilter = makeTimeFilter(true)

function openStartPicker() {
  const [h, m] = String(form.value.startTime || '08:00').split(':')
  startValue.value = [h || '08', m || '00']
  showStartPicker.value = true
}

function openEndPicker() {
  const [h, m] = String(form.value.endTime || '16:00').split(':')
  endValue.value = [h || '16', m || '00']
  showEndPicker.value = true
}

/** 拼接 'HH:mm'：24 点收班统一显示「24:00」，不写「00:00」以免与次日零点混淆 */
function joinTime(values) {
  const [h, m] = values
  return h === '24' ? '24:00' : `${h}:${m}`
}

function onStartConfirm({ selectedValues }) {
  form.value.startTime = joinTime(selectedValues)
  showStartPicker.value = false
}

function onEndConfirm({ selectedValues }) {
  form.value.endTime = joinTime(selectedValues)
  showEndPicker.value = false
}

/* ---------- 取数 ---------- */

/** 拉该驿站「保存后启用集合」预算所需的班次全集；失败不阻断表单（真实拦截以服务端为准） */
async function loadSiblings() {
  if (stationId.value == null) return
  try {
    siblings.value = await getShifts({ stationId: stationId.value })
  } catch (e) {
    siblings.value = []
  }
}

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    const data = await getStationList()
    stations.value = data
    if (stationId.value == null && data.length) stationId.value = data[0].id
  } finally {
    stationsLoading.value = false
  }
}

async function retryStations() {
  try {
    await loadStations()
  } catch (e) {
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  }
}

/** 编辑态：由列表页带 stationId（或深链兜底扫站）定位该班次，回填表单 */
async function loadEdit() {
  loading.value = true
  error.value = ''
  try {
    await loadStations()
    let sid = route.query.stationId ? Number(route.query.stationId) : null
    if (sid == null) {
      // 深链无 stationId：逐站查找（驿站数量 <100，代价可接受），保证编辑页可直接刷新打开
      for (const station of stations.value) {
        const rows = await getShifts({ stationId: station.id })
        if (rows.some((item) => item.id === shiftId.value)) {
          sid = station.id
          siblings.value = rows
          break
        }
      }
    }
    if (sid == null) {
      error.value = '班次不存在，请返回列表重试'
      return
    }
    stationId.value = sid
    if (!siblings.value.length) siblings.value = await getShifts({ stationId: sid })
    const row = siblings.value.find((item) => item.id === shiftId.value)
    if (!row) {
      error.value = '班次不存在，请返回列表重试'
      return
    }
    form.value = {
      shiftName: row.shiftName,
      startTime: row.startTime,
      endTime: row.endTime,
      color: row.color,
      restMinutes: row.restMinutes,
      status: row.status
    }
  } catch (e) {
    error.value = e.message || '班次信息加载失败'
  } finally {
    loading.value = false
  }
}

function pickStation(id) {
  if (id === stationId.value) return
  stationId.value = id
  loadSiblings()
}

/* ---------- 保存 ---------- */

/** ActionBar note：A（字段）→ B（站点阻断）→ C（重叠提示）→ 服务端错误，取第一条 */
const note = computed(() => nameError.value || timeError.value || restError.value || stationError.value || overlapHint.value || serverError.value)

const saveDisabled = computed(() => !!nameError.value || !!timeError.value || !!restError.value || !!stationError.value)

const actions = computed(() => [
  { key: 'save', label: '保存班次', plain: false, loading: submitting.value, disabled: saveDisabled.value }
])

async function onSave() {
  if (submitting.value || saveDisabled.value) return
  if (stationId.value == null) {
    serverError.value = '请先选择驿站'
    return
  }
  submitting.value = true
  serverError.value = ''
  const payload = {
    shiftName: form.value.shiftName.trim(),
    startTime: form.value.startTime,
    endTime: form.value.endTime,
    color: form.value.color,
    restMinutes: Number(form.value.restMinutes),
    status: form.value.status
  }
  try {
    if (isEdit.value) await updateShift(shiftId.value, payload)
    else await createShift({ ...payload, stationId: stationId.value })
    showSuccessToast(isEdit.value ? '班次已更新' : '班次已新增')
    // 返回列表并刷新：保证打卡时段与列表立刻反映（设计 ⑭.3-(7)）
    router.replace('/boss/shifts')
  } catch (e) {
    // 400 / 9114 原样落 note（silent 已避免通用 Toast），表单保留输入
    serverError.value = e.message || '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    await loadEdit()
    return
  }
  // 新增态：表单不阻塞（无取数骨架），驿站与兄弟班次后台加载，用于站点级前置校验
  try {
    await loadStations()
  } catch (e) {
    stationsError.value = e.message || '驿站列表加载失败'
  }
  await loadSiblings()
})
</script>

<template>
  <div class="shift-form">
    <PageNav :title="isEdit ? '编辑班次' : '新增班次'" />
    <div class="page page--bar">
      <PageState :loading="isEdit && loading" :error="error" :rows="6" @retry="loadEdit">
        <div v-if="form">
          <!-- 站点上下文：新增可选（提交带 stationId）；编辑只读（PUT 不含 stationId，班次不跨站迁移） -->
          <button v-if="!isEdit" type="button" class="station-pick card" @click="showStation = true">
            <span class="muted">所属驿站</span>
            <span class="station-pick__value">{{ currentStationName }}<van-icon name="arrow" aria-hidden="true" /></span>
          </button>
          <div v-else class="station-pick card">
            <span class="muted">所属驿站</span>
            <span class="station-pick__value station-pick__value--readonly">{{ currentStationName }}</span>
          </div>

          <div class="section-title">班次信息</div>
          <div class="card">
            <van-field
              v-model="form.shiftName"
              label="班次名称"
              placeholder="如：早班"
              maxlength="20"
              show-word-limit
              aria-label="班次名称"
            />

            <van-cell
              title="开始时间"
              is-link
              :value="form.startTime"
              :aria-label="`开始时间，当前 ${form.startTime}`"
              @click="openStartPicker"
            />
            <van-cell
              title="结束时间"
              is-link
              :value="form.endTime"
              :aria-label="`结束时间，当前 ${form.endTime}`"
              @click="openEndPicker"
            />

            <div class="field-block">
              <p class="field-block__label">班次配色</p>
              <div class="color-row" role="radiogroup" aria-label="班次配色">
                <button
                  v-for="item in SHIFT_COLORS"
                  :key="item.value"
                  type="button"
                  class="color-chip"
                  :class="{ 'color-chip--active': form.color === item.value }"
                  role="radio"
                  :aria-checked="form.color === item.value"
                  @click="form.color = item.value"
                >
                  <i class="color-chip__dot" :style="{ background: `var(${item.token})` }" aria-hidden="true" />
                  {{ item.label }}
                </button>
              </div>
            </div>

            <van-cell title="休息时长（分钟）" center>
              <template #right-icon>
                <van-stepper
                  v-model="form.restMinutes"
                  :min="0"
                  :max="480"
                  :step="15"
                  button-size="32px"
                  input-width="56px"
                />
              </template>
            </van-cell>

            <van-cell title="启用状态" center>
              <template #right-icon>
                <van-switch v-model="form.status" :active-value="1" :inactive-value="0" size="24px" aria-label="启用状态" />
              </template>
            </van-cell>
            <p class="tip">停用后该班次不再被排班引用，也不再作为打卡时段。</p>

            <!-- C 组：重叠仅提示不阻断（warning 色），仍可保存 -->
            <p v-if="overlapHint" class="overlap-hint" role="alert">{{ overlapHint }}</p>
          </div>

          <p v-if="serverError" class="form-error" role="alert">{{ serverError }}</p>
        </div>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="note" :submitting="submitting" @select="onSave" />

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="stationId"
      :allow-all="false"
      :loading="stationsLoading"
      :error="stationsError"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="retryStations"
      @select="pickStation"
    />

    <van-popup v-model:show="showStartPicker" round position="bottom" safe-area-inset-bottom>
      <van-time-picker
        v-model="startValue"
        title="选择开始时间"
        :columns-type="['hour', 'minute']"
        :filter="startFilter"
        @confirm="onStartConfirm"
        @cancel="showStartPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showEndPicker" round position="bottom" safe-area-inset-bottom>
      <van-time-picker
        v-model="endValue"
        title="选择结束时间"
        :columns-type="['hour', 'minute']"
        :filter="endFilter"
        @confirm="onEndConfirm"
        @cancel="showEndPicker = false"
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

/* 编辑态站点只读：降为正文色，不做成可点样式（避免误以为能切站） */
.station-pick__value--readonly {
  color: var(--text-2);
}

.field-block {
  padding: var(--sp-3) var(--sp-4) var(--sp-4);
  border-top: 1px solid var(--border-line);
}

.field-block__label {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-body);
  color: var(--text-1);
}

.color-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

/* 配色 chip：文字标签必在（状态不只靠颜色），触控区 ≥44 */
.color-chip {
  display: inline-flex;
  gap: var(--sp-2);
  align-items: center;
  min-height: var(--touch-min);
  padding: 0 var(--sp-3);
  font-size: var(--fs-body);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.color-chip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.color-chip__dot {
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: var(--r-xs);
}

.overlap-hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-warning);
}

.form-error {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}
</style>
