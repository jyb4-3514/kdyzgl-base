<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StationPicker from '../../components/StationPicker.vue'
import { batchSchedulesByStation, getSchedules, getStationList, saveSchedules } from '../../api/index.js'
import { addDays, formatDate, mondayOf, WEEKDAYS } from '../../utils/attendance.js'

/**
 * B9 排班管理（ADMIN · 周视图查看 + 快捷调整 + 批量工具）
 *
 * 为什么不做「员工 × 7 天」的矩阵表：7.5 明确移动端不使用表格。改为
 * 「先在日期条选一天 → 只列出当天员工 → 点行改班次」，一屏内完成调整，且行高足够点得准。
 * 改动先在本地累积，最后一次性提交 /schedules/batch（单次上限 200 条，与接口口径一致）。
 *
 * 批量工具是移动端的有意能力降级（B2.6）：不做整行/整列批选，只保留三个高频动作——
 * 一键铺排（直接落库）、复制上一周与清空本周（写入本地待保存区，用户仍可逐人微调后再保存）。
 * 复制为什么不直接落库：复制结果几乎一定要微调（有人请假、有人换班），直接落库会让用户改完再存第二次。
 */
const loading = ref(true)
const error = ref('')
const saving = ref(false)
const stations = ref([])
const stationId = ref(null)
const weekStart = ref(formatDate(mondayOf(new Date())))
const matrix = ref(null)
const selectedDate = ref(formatDate(new Date()))
/** 本地待提交的排班：`员工ID|日期` → shiftId（null 表示清空该天） */
const draft = ref({})
const sheet = ref({ show: false, employeeId: null, employeeName: '' })
const showStation = ref(false)

/* ---------- 批量工具（需求2） ---------- */

/** 批量工具菜单 / 一键铺排 / 复制上一周三个弹层互斥，各用独立开关避免嵌套弹层的关闭竞态 */
const showTools = ref(false)
const showSpread = ref(false)
const showCopy = ref(false)
const spreading = ref(false)
const copyLoading = ref(false)
/** 上一周排班矩阵：复制预览与写入本地都读它 */
const prevWeek = ref(null)

const spread = reactive({ shiftId: null, startDate: '', endDate: '', weekdays: [], skipExisting: true })

/** 星期多选按「周一 → 周日」排布（排班场景的心智顺序），值仍是 0=周日…6=周六 的 JS getDay 口径 */
const WEEKDAY_CHIPS = [1, 2, 3, 4, 5, 6, 0].map((value) => ({ value, label: WEEKDAYS[value].slice(1) }))

const thisWeekStart = computed(() => formatDate(mondayOf(new Date())))
const today = formatDate(new Date())
const rangeText = computed(() => (matrix.value ? `${matrix.value.weekStart} ~ ${matrix.value.weekEnd}` : ''))
const shifts = computed(() => (matrix.value ? matrix.value.shifts : []))
const dates = computed(() => (matrix.value ? matrix.value.dates : []))

const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '-'
})

/** 服务端当前值：脏检查与「清空」判断的基准 */
const serverMap = computed(() => {
  const map = {}
  if (!matrix.value) return map
  matrix.value.employees.forEach((employee) => {
    employee.days.forEach((day) => {
      map[`${employee.employeeId}|${day.workDate}`] = day.shiftId
    })
  })
  return map
})

const pendingItems = computed(() =>
  Object.keys(draft.value)
    .filter((key) => (serverMap.value[key] || null) !== (draft.value[key] || null))
    .map((key) => {
      const [employeeId, workDate] = key.split('|')
      // shiftId 显式传 null 表示清空：undefined 会被 JSON.stringify 丢掉，服务端就收不到这条调整
      return { employeeId: Number(employeeId), workDate, shiftId: draft.value[key] || null }
    })
)

const selectedText = computed(() => {
  const d = new Date(`${selectedDate.value}T00:00:00`)
  return `${selectedDate.value.slice(5)} ${WEEKDAYS[d.getDay()]}`
})

/** 当天员工：同时给出「当前生效班次」与「本地待保存班次」，让改动一眼可见 */
const dayRows = computed(() => {
  if (!matrix.value) return []
  return matrix.value.employees.map((employee) => {
    const key = `${employee.employeeId}|${selectedDate.value}`
    const shiftId = draft.value[key] || null
    return {
      employeeId: employee.employeeId,
      employeeName: employee.employeeName,
      shift: shifts.value.find((item) => item.id === shiftId) || null,
      changed: (serverMap.value[key] || null) !== shiftId
    }
  })
})

const scheduledCount = computed(() => dayRows.value.filter((row) => row.shift).length)

const actions = computed(() => [
  { key: 'save', label: '保存排班', plain: false, loading: saving.value, disabled: !pendingItems.value.length }
])
const barNote = computed(() => (pendingItems.value.length ? `待保存 ${pendingItems.value.length} 条调整` : ''))

/** 铺排命中的日期：星期全不选 = 全周（与服务端口径一致，界面上必须显式说明） */
const spreadDates = computed(() => {
  if (!spread.startDate || !spread.endDate || spread.startDate > spread.endDate) return []
  const filter = spread.weekdays.length ? new Set(spread.weekdays) : null
  const list = []
  for (let day = new Date(`${spread.startDate}T00:00:00`); formatDate(day) <= spread.endDate; day = addDays(day, 1)) {
    if (!filter || filter.has(day.getDay())) list.push(formatDate(day))
  }
  return list
})

/**
 * 铺排预览：把「要写多少格子、其中多少已有排班会被跳过」在提交前算清楚。
 * 跳过数只能对「已加载的这一周」精确统计，跨周部分服务端才有全貌，故用 partial 标记并显式提示，
 * 不把估算当承诺；提交后的 toast 一律用服务端返回的 created / skipped。
 */
const spreadPreview = computed(() => {
  const staff = matrix.value ? matrix.value.employees.length : 0
  const total = staff * spreadDates.value.length
  let skipped = 0
  if (spread.skipExisting && matrix.value) {
    spreadDates.value.forEach((date) => {
      if (!dates.value.includes(date)) return
      matrix.value.employees.forEach((employee) => {
        if (serverMap.value[`${employee.employeeId}|${date}`]) skipped += 1
      })
    })
  }
  const knownOutside = spreadDates.value.some((date) => !dates.value.includes(date))
  return { staff, total, skipped, created: total - skipped, partial: knownOutside }
})

/** 复制预览：三类计数分别列出，用户才能判断「这次复制会动多少东西」 */
const copyPreview = computed(() => {
  const result = { created: 0, overwritten: 0, cleared: 0 }
  if (!prevWeek.value || !matrix.value) return { ...result, total: 0 }
  matrix.value.employees.forEach((employee) => {
    const source = prevWeek.value.employees.find((item) => item.employeeId === employee.employeeId)
    if (!source) return
    dates.value.forEach((date, index) => {
      const current = serverMap.value[`${employee.employeeId}|${date}`] || null
      const next = (source.days[index] && source.days[index].shiftId) || null
      if (current === next) return
      if (!next) {
        if (current) result.cleared += 1
      } else if (current) result.overwritten += 1
      else result.created += 1
    })
  })
  return { ...result, total: result.created + result.overwritten + result.cleared }
})

function shiftOfDate(dateText) {
  const d = new Date(`${dateText}T00:00:00`)
  return { md: dateText.slice(5), wd: WEEKDAYS[d.getDay()] }
}

function buildDraft() {
  const next = {}
  Object.keys(serverMap.value).forEach((key) => {
    next[key] = serverMap.value[key]
  })
  draft.value = next
}

async function loadStations() {
  const list = await getStationList()
  stations.value = list
  if (stationId.value == null && list.length) stationId.value = list[0].id
}

async function load() {
  if (stationId.value == null) return
  loading.value = true
  error.value = ''
  try {
    const data = await getSchedules({ stationId: stationId.value, weekStart: weekStart.value })
    matrix.value = data
    buildDraft()
    // 选中日默认落在「本周的今天」；切到别的周时退回周一，避免选中一个不在当前周的日期
    if (!data.dates.includes(selectedDate.value))
      selectedDate.value = data.dates.includes(today) ? today : data.dates[0]
  } catch (e) {
    error.value = e.message || '加载失败'
    matrix.value = null
  } finally {
    loading.value = false
  }
}

async function init() {
  try {
    await loadStations()
  } catch (e) {
    loading.value = false
    error.value = e.message || '驿站列表加载失败'
    return
  }
  await load()
}

function pickStation(id) {
  if (id === stationId.value) return
  if (pendingItems.value.length) {
    showToast('请先保存或撤销当前排班调整，再切换驿站')
    return
  }
  stationId.value = id
  selectedDate.value = today
  load()
}

function shiftWeek(offset) {
  if (pendingItems.value.length) {
    showToast('请先保存或撤销当前排班调整，再切换周')
    return
  }
  weekStart.value = formatDate(addDays(new Date(`${weekStart.value}T00:00:00`), offset * 7))
  load()
}

function backToThisWeek() {
  weekStart.value = thisWeekStart.value
  selectedDate.value = today
  load()
}

function openSheet(row) {
  sheet.value = { show: true, employeeId: row.employeeId, employeeName: row.employeeName }
}

function applyShift(shiftId) {
  draft.value = { ...draft.value, [`${sheet.value.employeeId}|${selectedDate.value}`]: shiftId }
  sheet.value.show = false
}

async function onSave() {
  if (saving.value || !pendingItems.value.length) return
  saving.value = true
  try {
    const result = await saveSchedules({ stationId: stationId.value, items: pendingItems.value })
    showSuccessToast(`已保存 ${result.saved} 条${result.removed ? `，清空 ${result.removed} 条` : ''}`)
    await load()
  } catch (e) {
    // 错误提示由 http 层统一弹出；本地改动保留，用户修完可再点保存
  } finally {
    saving.value = false
  }
}

/* ---------- 批量工具交互 ---------- */

/** 本地有待保存改动时不让进批量工具：两者都会改同一批格子，交叉操作必然互相覆盖 */
function openTools() {
  if (pendingItems.value.length) {
    showToast('请先保存或撤销当前排班调整，再使用批量工具')
    return
  }
  showTools.value = true
}

function openSpread() {
  showTools.value = false
  // 默认铺排「当前显示的整周」，命中移动端最常见的诉求；日期区间仍可自由改
  spread.shiftId = shifts.value.length ? shifts.value[0].id : null
  spread.startDate = matrix.value ? matrix.value.weekStart : thisWeekStart.value
  spread.endDate = matrix.value ? matrix.value.weekEnd : thisWeekStart.value
  spread.weekdays = []
  spread.skipExisting = true
  showSpread.value = true
}

function toggleWeekday(value) {
  spread.weekdays = spread.weekdays.includes(value)
    ? spread.weekdays.filter((item) => item !== value)
    : spread.weekdays.concat(value)
}

async function onSpreadConfirm() {
  if (spreading.value || spread.shiftId == null || !spreadDates.value.length) return
  if (!spread.skipExisting) {
    // 覆盖类操作必须先说清影响范围（B0.3），文案用具体动词而不是「确定/取消」
    try {
      await showConfirmDialog({
        title: '覆盖已有排班',
        message: `已关闭「跳过已有排班」，本次将写入 ${spreadPreview.value.total} 个格子，其中已排部分会被「${
          shifts.value.find((item) => item.id === spread.shiftId).shiftName
        }」覆盖，覆盖后不可撤回。`,
        confirmButtonText: '确认铺排',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return // 用户取消
    }
  }
  spreading.value = true
  try {
    const result = await batchSchedulesByStation({
      stationId: stationId.value,
      shiftId: spread.shiftId,
      startDate: spread.startDate,
      endDate: spread.endDate,
      weekdays: spread.weekdays,
      skipExisting: spread.skipExisting
    })
    showSpread.value = false
    showSuccessToast(`已铺排 ${result.created} 处${result.skipped ? `，跳过 ${result.skipped} 处` : ''}`)
    await load()
  } catch (e) {
    // 班次已停用（9106）等错误由 http 层提示；弹层保留，用户改完可再提交
  } finally {
    spreading.value = false
  }
}

async function openCopy() {
  showTools.value = false
  showCopy.value = true
  copyLoading.value = true
  prevWeek.value = null
  try {
    const prevStart = formatDate(addDays(new Date(`${weekStart.value}T00:00:00`), -7))
    prevWeek.value = await getSchedules({ stationId: stationId.value, weekStart: prevStart })
  } catch (e) {
    prevWeek.value = null
  } finally {
    copyLoading.value = false
  }
}

/** 复制结果写进本地待保存区：用户仍可逐人微调，最后统一保存，避免「复制后又改」产生第二次请求 */
async function onCopyConfirm() {
  if (!prevWeek.value || !copyPreview.value.total) return
  try {
    await showConfirmDialog({
      title: '复制上一周排班',
      message: `将把 ${prevWeek.value.weekStart} ~ ${prevWeek.value.weekEnd} 的排班套用到 ${rangeText.value}：新增 ${copyPreview.value.created} 处、覆盖 ${copyPreview.value.overwritten} 处、清空 ${copyPreview.value.cleared} 处，写入后仍需点「保存排班」才会生效。`,
      confirmButtonText: '确认复制',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return // 用户取消
  }
  const next = { ...draft.value }
  matrix.value.employees.forEach((employee) => {
    const source = prevWeek.value.employees.find((item) => item.employeeId === employee.employeeId)
    if (!source) return
    dates.value.forEach((date, index) => {
      next[`${employee.employeeId}|${date}`] = (source.days[index] && source.days[index].shiftId) || null
    })
  })
  draft.value = next
  showCopy.value = false
  showToast('已套用上一周排班，点「保存排班」生效')
}

async function onClearWeek() {
  showTools.value = false
  try {
    await showConfirmDialog({
      title: '清空本周排班',
      message: `将清空 ${rangeText.value} 内 ${matrix.value.employees.length} 名员工的全部排班，清空后仍需点「保存排班」才会生效。`,
      confirmButtonText: '确认清空',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return // 用户取消
  }
  const next = { ...draft.value }
  matrix.value.employees.forEach((employee) => {
    dates.value.forEach((date) => {
      next[`${employee.employeeId}|${date}`] = null
    })
  })
  draft.value = next
  showToast('已清空本周排班，点「保存排班」生效')
}

onMounted(init)
</script>

<template>
  <div class="sch-page">
    <PageNav title="排班管理">
      <!-- 批量工具放页头右上：它是「整周操作」的入口，与页内「单日 + 逐人」的调整动线区分开 -->
      <template #right>
        <button type="button" class="nav-action" @click="openTools">批量工具</button>
      </template>
    </PageNav>
    <div class="page page--bar">
      <button type="button" class="station-pick card" @click="showStation = true">
        <span class="muted">当前驿站</span>
        <span class="station-pick__value">{{ currentStationName }}<van-icon name="arrow" aria-hidden="true" /></span>
      </button>

      <PageState :loading="loading" :error="error" :rows="6" @retry="load">
        <div v-if="matrix">
          <div class="card week-nav">
            <button type="button" class="week-nav__btn" aria-label="上一周" @click="shiftWeek(-1)">
              <van-icon name="arrow-left" aria-hidden="true" />
            </button>
            <div class="week-nav__center">
              <p class="week-nav__range tabular-nums">{{ rangeText }}</p>
              <p class="week-nav__sub">本周共 {{ matrix.employees.length }} 名员工参与排班</p>
            </div>
            <button type="button" class="week-nav__btn" aria-label="下一周" @click="shiftWeek(1)">
              <van-icon name="arrow" aria-hidden="true" />
            </button>
          </div>
          <button v-if="weekStart !== thisWeekStart" type="button" class="plain-btn" @click="backToThisWeek">
            回到本周
          </button>

          <div class="day-bar" role="tablist" aria-label="选择日期">
            <button
              v-for="date in dates"
              :key="date"
              type="button"
              role="tab"
              class="day-chip"
              :class="{ 'day-chip--active': date === selectedDate }"
              :aria-selected="date === selectedDate"
              @click="selectedDate = date"
            >
              <span class="day-chip__md tabular-nums">{{ shiftOfDate(date).md }}</span>
              <span class="day-chip__wd">{{ shiftOfDate(date).wd }}</span>
            </button>
          </div>

          <div class="section-title">
            <span>当日排班</span>
            <span class="section-title__extra tabular-nums"
              >{{ selectedText }} · 已排 {{ scheduledCount }}/{{ dayRows.length }}</span
            >
          </div>
          <button
            v-for="row in dayRows"
            :key="row.employeeId"
            type="button"
            class="list-item emp-row"
            :class="{ 'emp-row--changed': row.changed }"
            @click="openSheet(row)"
          >
            <span class="emp-row__name">{{ row.employeeName }}</span>
            <span v-if="row.shift" class="emp-row__shift" :style="{ '--shift-color': row.shift.color }">
              {{ row.shift.shiftName }} {{ row.shift.startTime }}-{{ row.shift.endTime }}
            </span>
            <span v-else class="emp-row__shift emp-row__shift--rest">休息（未排班）</span>
            <span v-if="row.changed" class="emp-row__flag">待保存</span>
          </button>
          <p v-if="!dayRows.length" class="tip">该驿站当天无可排班员工</p>

          <div class="section-title">本驿站班次<span class="section-title__extra">只读</span></div>
          <div class="card">
            <div v-for="item in shifts" :key="item.id" class="shift-row">
              <span class="shift-row__bar" :style="{ background: item.color }" aria-hidden="true"></span>
              <span class="shift-row__name">{{ item.shiftName }}</span>
              <span class="shift-row__time tabular-nums">{{ item.startTime }} - {{ item.endTime }}</span>
            </div>
            <p v-if="!shifts.length" class="tip">该驿站暂无可用班次</p>
          </div>

          <p class="tip">
            调整与「复制上一周 / 清空本周」都先在本地累积，点「保存排班」一次性提交；「一键铺排」直接生效。
            移动端不做整行/整列批选（375px 下的矩阵选不准），整周操作走右上角「批量工具」。
          </p>
        </div>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="barNote" :submitting="saving" @select="onSave" />

    <van-popup v-model:show="sheet.show" round position="bottom" safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet__title">{{ sheet.employeeName }} · {{ selectedText }}</div>
        <button v-for="item in shifts" :key="item.id" type="button" class="sheet__item" @click="applyShift(item.id)">
          <span>{{ item.shiftName }} {{ item.startTime }}-{{ item.endTime }}</span>
          <span class="sheet__dot" :style="{ background: item.color }" aria-hidden="true"></span>
        </button>
        <button type="button" class="sheet__item sheet__item--danger" @click="applyShift(null)">
          清空该天排班（休息）
        </button>
      </div>
    </van-popup>

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="stationId"
      :allow-all="false"
      @select="pickStation"
    />

    <!-- 批量工具菜单（需求2）：只放 3 个整周动作，人员多选/整行列批选属 PC 能力 -->
    <van-popup v-model:show="showTools" round position="bottom" safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet__title">批量工具 · {{ currentStationName }}</div>
        <button type="button" class="sheet__item" @click="openSpread">
          <span>一键铺排</span>
          <span class="sheet__hint">按班次 + 日期区间批量写入</span>
        </button>
        <button type="button" class="sheet__item" @click="openCopy">
          <span>复制上一周</span>
          <span class="sheet__hint">套用到 {{ rangeText || '当前周' }}</span>
        </button>
        <button type="button" class="sheet__item sheet__item--danger" @click="onClearWeek">
          <span>清空本周</span>
          <span class="sheet__hint">先清本地，保存后生效</span>
        </button>
      </div>
    </van-popup>

    <!-- 一键铺排表单：班次 / 日期区间 / 星期多选，提交前给出格数与跳过数预览 -->
    <van-popup v-model:show="showSpread" round position="bottom" safe-area-inset-bottom>
      <div class="spread">
        <div class="sheet__title">一键铺排</div>
        <div class="spread__body">
          <p class="spread__label">班次</p>
          <div class="spread__chips">
            <button
              v-for="item in shifts"
              :key="item.id"
              type="button"
              class="chip chip--sm"
              :class="{ 'chip--active': spread.shiftId === item.id }"
              :aria-pressed="spread.shiftId === item.id"
              @click="spread.shiftId = item.id"
            >
              {{ item.shiftName }}
            </button>
          </div>
          <p v-if="!shifts.length" class="tip">该驿站暂无可用班次，请先在 PC 端维护班次</p>

          <p class="spread__label">日期区间</p>
          <div class="spread__range">
            <label class="spread__field">
              <span class="spread__field-label">开始</span>
              <!-- 用原生 date 输入：安卓壳与浏览器都会唤起系统日期选择器，比自绘滚轮在窄屏上更省操作 -->
              <input v-model="spread.startDate" type="date" class="spread__input" />
            </label>
            <label class="spread__field">
              <span class="spread__field-label">结束</span>
              <input v-model="spread.endDate" type="date" class="spread__input" />
            </label>
          </div>
          <p
            v-if="spread.startDate && spread.endDate && spread.startDate > spread.endDate"
            class="spread__error"
            role="alert"
          >
            结束日期不能早于开始日期
          </p>

          <p class="spread__label">星期<span class="spread__note">不选任何一天 = 全周每天</span></p>
          <div class="spread__chips">
            <button
              v-for="item in WEEKDAY_CHIPS"
              :key="item.value"
              type="button"
              class="chip chip--sm"
              :class="{ 'chip--active': spread.weekdays.includes(item.value) }"
              :aria-pressed="spread.weekdays.includes(item.value)"
              @click="toggleWeekday(item.value)"
            >
              周{{ item.label }}
            </button>
          </div>

          <div class="spread__switch">
            <div class="spread__switch-text">
              <p class="spread__switch-title">跳过已有排班</p>
              <p class="spread__switch-hint">关闭后已排的格子会被本次班次覆盖</p>
            </div>
            <van-switch v-model="spread.skipExisting" size="24px" aria-label="跳过已有排班" />
          </div>

          <!-- 预览区随选择实时变化，aria-live 让读屏也能听到格数变化（C4 无障碍要求） -->
          <div class="spread__preview" aria-live="polite">
            <p v-if="!spreadDates.length" class="muted">请选择有效的日期区间与星期</p>
            <template v-else>
              <p>
                将写入 <b class="tabular-nums">{{ spreadPreview.created }}</b> 个格子{{
                  spread.skipExisting ? `，跳过 ${spreadPreview.skipped} 个已有排班` : ''
                }}
              </p>
              <p class="spread__preview-meta">
                参与人员 {{ spreadPreview.staff }} 人 · 命中 {{ spreadDates.length }} 天 · 停用账号不计入
                <template v-if="spreadPreview.partial">；跨周部分的「跳过数」以服务端为准</template>
              </p>
            </template>
          </div>
        </div>
        <div class="spread__foot">
          <van-button
            block
            type="primary"
            :loading="spreading"
            :disabled="spread.shiftId == null || !spreadDates.length"
            @click="onSpreadConfirm"
          >
            确认铺排
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 复制上一周：只读预览 + 三类计数，确认后写入本地待保存区，不直接落库 -->
    <van-popup v-model:show="showCopy" round position="bottom" safe-area-inset-bottom>
      <div class="sheet">
        <div class="sheet__title">复制上一周</div>
        <div class="spread__body">
          <div v-if="copyLoading" class="skeleton-block copy__skeleton" />

          <p v-else-if="!prevWeek" class="copy__empty">
            上一周排班未取到，无法复制。请确认该驿站是否已排班，或稍后重试。
          </p>

          <p v-else-if="!copyPreview.total" class="copy__empty">上一周无排班记录，无可复制内容</p>

          <template v-else>
            <p class="copy__range tabular-nums">{{ prevWeek.weekStart }} ~ {{ prevWeek.weekEnd }} → {{ rangeText }}</p>
            <div class="copy__counts">
              <div class="copy__count">
                <span class="copy__num tabular-nums">{{ copyPreview.created }}</span>
                <span class="copy__label">新增</span>
              </div>
              <div class="copy__count">
                <span class="copy__num tabular-nums">{{ copyPreview.overwritten }}</span>
                <span class="copy__label">覆盖</span>
              </div>
              <div class="copy__count">
                <span class="copy__num tabular-nums">{{ copyPreview.cleared }}</span>
                <span class="copy__label">清空</span>
              </div>
            </div>
            <p class="tip">复制结果先写入本地，仍可逐人微调，点「保存排班」才会提交</p>
          </template>
        </div>
        <div class="spread__foot">
          <van-button block type="primary" :disabled="!copyPreview.total" @click="onCopyConfirm">确认复制</van-button>
        </div>
      </div>
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

.week-nav {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  margin-top: var(--sp-3);
}

.week-nav__btn {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: var(--fs-h2);
  color: var(--text-1);
  background: var(--surface-subtle);
  border: 1px solid var(--border-line);
  border-radius: var(--r-sm);
}

.week-nav__center {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.week-nav__range {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
}

.week-nav__sub {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.plain-btn {
  display: block;
  width: 100%;
  min-height: 44px;
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--r-sm);
}

/* 日期条：7 等分不滚动（375px 下每格约 50×44，触控达标且不会把页面撑出横向滚动） */
.day-bar {
  display: flex;
  gap: var(--sp-1);
  margin-top: var(--sp-3);
}

.day-chip {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 2px;
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-sm);
}

.day-chip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.day-chip__md {
  font-size: var(--fs-micro);
}

.day-chip__wd {
  font-size: var(--fs-micro);
  color: var(--text-3);
}

.day-chip--active .day-chip__wd {
  color: var(--color-primary);
}

.emp-row {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  width: 100%;
  text-align: left;
  margin-top: var(--sp-3);
}

.emp-row--changed {
  border: 1px solid var(--color-warning);
}

.emp-row__name {
  flex: none;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
}

.emp-row__shift {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-caption);
  color: var(--text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emp-row__shift--rest {
  color: var(--text-3);
}

.emp-row__flag {
  flex: none;
  font-size: var(--fs-micro);
  color: var(--color-warning);
}

.shift-row {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  min-height: 32px;
}

.shift-row__bar {
  flex: none;
  width: 4px;
  height: 18px;
  border-radius: var(--r-xs);
}

.shift-row__name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-body);
}

.shift-row__time {
  font-size: var(--fs-caption);
  color: var(--text-3);
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

.sheet__item--danger {
  color: var(--color-danger);
}

.sheet__dot {
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: var(--r-full);
}

/* 页头右上入口：高 44 满足触控，视觉压到 Caption 级不抢标题 */
.nav-action {
  min-height: 44px;
  padding: 0 var(--sp-1);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

/* chip 的紧凑形态：批量弹层里一行要放下 3-4 个选项，仍保持 ≥44 高的触控目标 */
.chip--sm {
  min-height: 44px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
}

.sheet__hint {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.spread__body {
  padding: 0 var(--sp-4);
}

.spread__label {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  margin: var(--sp-4) 0 var(--sp-2);
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.spread__note {
  font-size: var(--fs-caption);
  font-weight: var(--fw-regular);
  color: var(--text-3);
}

.spread__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.spread__range {
  display: flex;
  gap: var(--sp-3);
}

.spread__field {
  flex: 1;
  min-width: 0;
}

.spread__field-label {
  display: block;
  margin-bottom: var(--sp-1);
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.spread__input {
  width: 100%;
  min-height: 44px;
  padding: 0 var(--sp-2);
  font-family: inherit;
  font-size: var(--fs-body);
  color: var(--text-1);
  background: var(--surface-card);
  border: 1px solid var(--border-control);
  border-radius: var(--r-sm);
}

.spread__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  color: var(--color-danger);
}

.spread__switch {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  min-height: 56px;
  padding-top: var(--sp-3);
  margin-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
}

.spread__switch-text {
  flex: 1;
  min-width: 0;
}

.spread__switch-title {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--text-1);
}

.spread__switch-hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.spread__preview {
  padding: var(--sp-3);
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--text-1);
  background: var(--color-primary-surface);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--r-sm);
}

.spread__preview p {
  margin: 0;
}

.spread__preview .spread__preview-meta {
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.spread__foot {
  padding: var(--sp-4) var(--sp-4) var(--sp-5);
}

.copy__skeleton {
  height: 108px;
  margin-top: var(--sp-3);
}

.copy__empty {
  padding: var(--sp-5) 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-3);
  text-align: center;
}

.copy__range {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
  text-align: center;
}

.copy__counts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.copy__count {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-3) 0;
  background: var(--surface-subtle);
  border-radius: var(--r-sm);
}

.copy__num {
  font-size: var(--fs-num-md);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.copy__label {
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
