<template>
  <div class="schedule-page">
    <PageHeader title="排班管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <!-- 受控写法：切换前要先确认「未保存改动是否丢弃」，确认不通过时不改 model，界面不会先行跳站 -->
        <el-select
          v-if="isAdmin"
          :model-value="stationId"
          class="header-station"
          placeholder="选择驿站"
          @update:model-value="handleStationChange"
        >
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
        <!-- 批量工具（需求2）：把逐人逐天的 112 次点击降到 1~4 次；非写权限角色整个下拉禁用并说明原因 -->
        <el-dropdown v-if="canWrite" trigger="click" :disabled="!canWrite" @command="handleBatchCommand">
          <el-button
            :icon="MagicStick"
            :disabled="!canWrite"
            :title="canWrite ? '批量铺排 / 复制 / 清空' : '仅超级管理员可执行批量排班'"
          >
            批量工具
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="spread">一键铺排…</el-dropdown-item>
              <el-dropdown-item command="copy">复制上一周</el-dropdown-item>
              <el-dropdown-item command="batch">整行 / 整列批量设置…</el-dropdown-item>
              <el-dropdown-item divided command="clear">清空本周…</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <template v-if="canWrite">
          <el-button v-if="dirtyCount" @click="discardChanges">撤销修改</el-button>
          <el-button type="primary" :loading="saving" :disabled="!dirtyCount" @click="handleSave">
            {{ dirtyCount ? `保存排班（${dirtyCount}）` : '保存排班' }}
          </el-button>
        </template>
        <el-button :icon="Refresh" @click="refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="grid-card">
      <template #header>
        <div class="week-bar">
          <div class="week-bar__nav">
            <el-button :icon="ArrowLeft" @click="changeWeek(-1)">上一周</el-button>
            <el-button @click="goCurrentWeek">本周</el-button>
            <el-button :icon="ArrowRight" @click="changeWeek(1)">下一周</el-button>
          </div>
          <div class="week-bar__meta">
            <span class="week-bar__range">{{ weekLabel }}</span>
            <!-- 本周排班进度（A10-4）：老板一眼看出这周排完没有，不用逐列数 -->
            <span v-if="totalCells" class="week-bar__progress" :class="{ 'is-incomplete': filledCells < totalCells }">
              已排 {{ filledCells }} / {{ totalCells }} 格
            </span>
          </div>
        </div>
      </template>

      <div class="grid-toolbar">
        <div class="shift-legend">
          <span class="shift-legend__label">班次</span>
          <span v-for="shift in shifts" :key="shift.id" class="shift-legend__item">
            <!-- 色块取接口返回的配色：服务端已校验为 #RRGGBB，与班次管理表格共用同一份值 -->
            <i class="shift-legend__dot" :style="{ backgroundColor: shift.color }" aria-hidden="true" />
            {{ shift.shiftName }} {{ shift.startTime }}-{{ shift.endTime }}
            <span v-if="shift.status !== 1" class="shift-legend__off">已停用</span>
          </span>
        </div>
        <div class="grid-toolbar__right">
          <span class="toolbar-tip">共 {{ rows.length }} 名在岗员工 · 单元格清空表示当天不排班</span>
          <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新排班表" @click="refreshPage" />
        </div>
      </div>

      <StateBlock v-if="matrixError" variant="error" title="排班表加载失败" @action="fetchMatrix" />

      <StateBlock
        v-else-if="!loading && !rows.length"
        variant="empty"
        title="该驿站暂无在岗员工"
        description="请先在员工管理中为本驿站分配在岗员工后再排班"
      />

      <el-table v-else v-loading="loading" :data="rows" border :row-key="(row) => row.employeeId">
        <el-table-column label="员工" min-width="150" fixed="left">
          <template #default="{ row }">
            <div class="employee-cell">
              <span class="employee-name">{{ row.employeeName }}</span>
              <!-- 行内批量锚点（B2.3）：hover 才出现，避免整表被按钮压满；44×44 热区，键盘可 Tab 到 -->
              <button
                v-if="canWrite"
                type="button"
                class="batch-anchor"
                :aria-label="`批量设置 ${row.employeeName} 整周班次`"
                @click="openBatch('row', row.employeeId)"
              >
                全周
              </button>
            </div>
          </template>
        </el-table-column>
        <!-- days 与 dates 由服务端按同一周日期数组生成，下标一一对应，这里按下标取可省掉 140 次日期查找 -->
        <el-table-column v-for="(date, index) in dates" :key="date" :min-width="150">
          <template #header>
            <div
              class="day-head"
              :class="{ 'is-today': date === today, 'is-incomplete': dayFilledCount(index) < rows.length }"
            >
              <span class="day-head__week">{{ WEEK_LABEL[index] }}</span>
              <span class="day-head__date">{{ date.slice(5) }}</span>
              <span v-if="date === today" class="day-head__today">今天</span>
              <!-- 列头批量锚点（B2.3）：该天未排满时给提示色，hover 出现「全员」 -->
              <button
                v-if="canWrite"
                type="button"
                class="batch-anchor batch-anchor--head"
                :aria-label="`批量设置 ${date} 全员班次`"
                @click="openBatch('column', null, date)"
              >
                全员
              </button>
            </div>
          </template>
          <template #default="{ row }">
            <div
              class="cell-shift"
              :class="{ 'is-stale': isUnavailableShift(row.days[index].shiftId) }"
              :title="
                isUnavailableShift(row.days[index].shiftId)
                  ? `${row.employeeName} ${date} 的班次已停用或删除，保存时会被服务端拒绝（9106），请重新选择`
                  : `${row.employeeName} ${date} 班次`
              "
              :style="{ '--cell-shift-color': shiftColorOf(row.days[index].shiftId) }"
            >
              <!-- TODO(扩展): 支持「选中单元格 → 数字键直接赋班次 / Ctrl+C·Ctrl+V 复制列」的键盘录入（A10-2），
                   排班是同构重复操作，键盘录入比下拉快 5-10 倍；当前仅支持下拉 -->
              <el-select
                v-model="row.days[index].shiftId"
                size="small"
                clearable
                placeholder="未排班"
                :disabled="!canWrite"
                @change="markDirty(row, row.days[index])"
              >
                <el-option
                  v-for="shift in shifts"
                  :key="shift.id"
                  :label="shift.shiftName"
                  :value="shift.id"
                  :disabled="shift.status !== 1"
                >
                  <span class="option-shift">
                    <i class="option-shift__dot" :style="{ backgroundColor: shift.color }" aria-hidden="true" />
                    {{ shift.shiftName }}
                    <span v-if="shift.status !== 1" class="option-shift__off">已停用</span>
                  </span>
                </el-option>
              </el-select>
              <!-- 已停用/已删除班次的历史遗留格（A10-5）：提交前就标出，避免保存时才被 9106 打回 -->
              <span v-if="isUnavailableShift(row.days[index].shiftId)" class="cell-shift__stale">已停用</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <p v-if="!canWrite" class="grid-card__readonly">
        只读视角：排班保存仅超级管理员可执行，数据范围由服务端收敛为本人驿站
      </p>
    </el-card>

    <ShiftManager
      :station-id="effectiveStationId"
      :station-name="currentStationName"
      :can-write="canWrite"
      @changed="handleShiftChanged"
    />

    <BatchSpreadDialog
      v-model="batchVisible"
      :mode="batchMode"
      :station-id="effectiveStationId"
      :station-name="currentStationName"
      :week-start="weekStart"
      :dates="dates"
      :rows="rows"
      :shifts="shifts"
      :seed="batchSeed"
      @spread-done="fetchMatrix"
      @apply="applyBatchChanges"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowRight, MagicStick, Refresh } from '@element-plus/icons-vue'
import { getStations } from '@admin/api/station'
import { useAuthStore } from '@admin/stores/auth'
import { ATTENDANCE_CODE } from '@/shared/constants/errorCode'
import { addDays, formatDate, mondayOf } from '@/shared/domain/time.js'
import { getSchedules, getShifts, saveSchedulesBatch } from '../../api/attendance.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import ShiftManager from './components/ShiftManager.vue'
import BatchSpreadDialog from './components/BatchSpreadDialog.vue'

/**
 * 排班管理（行 = 员工，列 = 周一至周日）
 *
 * 交互取舍：单元格内直接放班次下拉，而不是「点开弹层再选」——排班是高频批量操作，
 * 一周 7 天 × 全站员工要一次性铺完，每次多一次弹层点击会翻倍操作成本。
 * 所有改动先在本地累积（dirty），点「保存排班」才批量提交 POST /schedules/batch，
 * 避免每改一格发一次请求（服务端单次上限 200 条，正好覆盖一整屏矩阵）。
 */

const WEEK_LABEL = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const authStore = useAuthStore()

const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')
const currentUser = computed(() => authStore.user || {})
// 排班保存与班次维护在服务端仅放行 ADMIN，非 ADMIN 进入只读视角（数据范围仍由服务端强制收敛本站）
const canWrite = computed(() => isAdmin.value)

const loading = ref(false)
const saving = ref(false)
const matrixError = ref(false)
const updatedAt = ref('')

const stations = ref([])
const stationId = ref(null)
const weekStart = ref(formatDate(mondayOf(new Date())))
const dates = ref([])
const shifts = ref([])

const rows = ref([])
/** 服务端当前值（key = employeeId_workDate），用于判断某格是否真的改过、以及撤销 */
const original = ref(new Map())
/** 待提交改动（key 同上，value = 提交体），Map 去重保证同一格反复改只提交最后一次 */
const dirty = ref(new Map())

// 批量工具（需求2）：mode 由页头下拉 / 行内列头锚点决定，seed 用于把锚点选中的行或列预置进弹窗
const batchVisible = ref(false)
const batchMode = ref('spread')
const batchSeed = ref({ direction: 'row', employeeId: null, workDate: null })

const today = formatDate(new Date())

const effectiveStationId = computed(() => (isAdmin.value ? stationId.value : currentUser.value.stationId || null))

const currentStationName = computed(() => {
  if (!isAdmin.value) return currentUser.value.stationName || ''
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : ''
})

const dirtyCount = computed(() => dirty.value.size)

// 本周排班进度（A10-4）：Y = 员工数 × 7，X = 已排格数；列头据此判断该天是否排满
const totalCells = computed(() => rows.value.length * dates.value.length)
const filledCells = computed(() =>
  rows.value.reduce((sum, row) => sum + row.days.filter((day) => day.shiftId != null).length, 0)
)
const dayFilledCount = (index) => rows.value.filter((row) => row.days[index] && row.days[index].shiftId != null).length

const weekLabel = computed(() => (dates.value.length ? `${dates.value[0]} ~ ${dates.value[6]}` : weekStart.value))

const headerSub = computed(() => {
  const scope = isAdmin.value ? '数据范围：全域（可切换驿站）' : `数据范围：本站 ${currentStationName.value || ''}`
  const readonly = canWrite.value ? '' : ' · 只读视角'
  return `${scope} · 周 ${weekLabel.value} · 更新于 ${updatedAt.value || '—'}${readonly}`
})

const cellKey = (employeeId, workDate) => `${employeeId}_${workDate}`

/** 'YYYY-MM-DD' → 本地 0 点：new Date('YYYY-MM-DD') 按 UTC 解析，跨时区会算错一天 */
function localDate(text) {
  const [y, m, d] = String(text).split('-').map(Number)
  return new Date(y, m - 1, d)
}

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** 单元格左侧色条取所选班次的配色，未排班则不显色 */
function shiftColorOf(shiftId) {
  if (shiftId == null) return 'transparent'
  const hit = shifts.value.find((item) => item.id === shiftId)
  // 班次已被删除时（不在列表里）用警告色左条，提示该格需要重选（A10-5）
  return hit ? hit.color : 'var(--state-warning-fg)'
}

/** 已停用或已删除的班次：提交时服务端会回 9106，故在提交前就把这些格标出来（A10-5） */
function isUnavailableShift(shiftId) {
  if (shiftId == null) return false
  const hit = shifts.value.find((item) => item.id === shiftId)
  return !hit || hit.status !== 1
}

async function loadStations() {
  try {
    stations.value = await getStations()
    // 默认选中第一个驿站：演示的排班种子数据只投给城东驿站
    if (!stationId.value && stations.value.length) stationId.value = stations.value[0].id
  } catch (e) {
    /* 拦截器已统一提示；驿站未选定时排班表会进入错误态并给出重试 */
  }
}

async function fetchMatrix() {
  if (effectiveStationId.value == null) return
  loading.value = true
  matrixError.value = false
  try {
    const matrix = await getSchedules({ stationId: effectiveStationId.value, weekStart: weekStart.value })
    dates.value = matrix.dates
    shifts.value = matrix.shifts
    // 深拷贝一层：单元格直接改本地副本，避免把服务端返回对象改脏后无法判断增量
    rows.value = matrix.employees.map((item) => ({
      employeeId: item.employeeId,
      employeeName: item.employeeName,
      days: item.days.map((day) => ({ ...day }))
    }))
    // 服务端会把 weekStart 归一到周一，回写以保证界面显示与请求口径一致
    weekStart.value = matrix.weekStart
    original.value = new Map(
      matrix.employees.flatMap((item) =>
        item.days.map((day) => [cellKey(item.employeeId, day.workDate), day.shiftId ?? null])
      )
    )
    dirty.value = new Map()
    updatedAt.value = stamp()
  } catch (e) {
    rows.value = []
    dates.value = []
    matrixError.value = true
  } finally {
    loading.value = false
  }
}

function markDirty(row, day) {
  const key = cellKey(row.employeeId, day.workDate)
  const originId = original.value.get(key) ?? null
  if ((day.shiftId ?? null) === originId) dirty.value.delete(key)
  else dirty.value.set(key, { employeeId: row.employeeId, workDate: day.workDate, shiftId: day.shiftId ?? null })
}

/** 丢弃本地改动：回写服务端值并清空待提交队列 */
function discardChanges() {
  rows.value.forEach((row) => {
    row.days.forEach((day) => {
      day.shiftId = original.value.get(cellKey(row.employeeId, day.workDate)) ?? null
    })
  })
  dirty.value = new Map()
}

/** 切换周/驿站会丢弃未保存改动，先确认再执行 */
async function confirmDiscard() {
  if (!dirtyCount.value) return true
  try {
    await ElMessageBox.confirm(
      `当前有 ${dirtyCount.value} 处排班改动未保存，继续操作将丢弃这些改动。`,
      '未保存的修改',
      { confirmButtonText: '丢弃并继续', cancelButtonText: '返回修改', type: 'warning' }
    )
    return true
  } catch (e) {
    return false
  }
}

/**
 * 批量工具入口：有未保存改动时先确认丢弃。
 * 理由：一键铺排成功后矩阵会整体重载，若不清空本地 dirty，重载会把未保存改动静默冲掉（B2.5 异常流）。
 */
async function openBatchTools(mode, seed) {
  if (!canWrite.value) return
  if (dirtyCount.value) {
    if (!(await confirmDiscard())) return
    discardChanges()
  }
  batchMode.value = mode
  batchSeed.value = seed || { direction: 'row', employeeId: null, workDate: null }
  batchVisible.value = true
}

function handleBatchCommand(command) {
  openBatchTools(command)
}

/** 行首 / 列头锚点：预置方向与目标格，直接进「整行 / 整列批量设置」 */
function openBatch(direction, employeeId = null, workDate = null) {
  openBatchTools('batch', { direction, employeeId, workDate })
}

/** 复制 / 整行整列 / 清空的结果落到本地 dirty，与逐格编辑共用同一套保存链路（B2.5 正常流 B） */
function applyBatchChanges(changes) {
  let applied = 0
  changes.forEach((change) => {
    const row = rows.value.find((item) => item.employeeId === change.employeeId)
    const day = row && row.days.find((item) => item.workDate === change.workDate)
    if (!day) return
    day.shiftId = change.shiftId
    markDirty(row, day)
    applied += 1
  })
  ElMessage.success(`已应用 ${applied} 处改动，点「保存排班」后提交`)
}

/** 关闭标签页 / 刷新时兜底提示：56 格编辑成本高，误关一次就是几分钟白干（A10-3） */
function handleBeforeUnload(event) {
  if (!dirtyCount.value) return
  event.preventDefault()
  event.returnValue = ''
}

/** 离开页面的路由守卫：与切周/切驿站的确认同口径（A10-3） */
onBeforeRouteLeave(async () => {
  if (!dirtyCount.value) return true
  return confirmDiscard()
})

async function handleSave() {
  if (!dirtyCount.value) return
  saving.value = true
  try {
    // silent：错误提示按 91xx 业务码在这里给针对性文案，不让拦截器先弹一条通用提示
    const result = await saveSchedulesBatch(
      { stationId: effectiveStationId.value, items: [...dirty.value.values()] },
      { silent: true }
    )
    ElMessage.success(`已保存 ${result.saved} 处排班${result.removed ? `，清空 ${result.removed} 处` : ''}`)
    await fetchMatrix()
  } catch (e) {
    const code = e && e.code
    if (code === ATTENDANCE_CODE.SHIFT_UNAVAILABLE) {
      ElMessage.warning('提交的班次已被删除或停用（9106），已刷新排班表，请重新选择班次')
      await fetchMatrix()
    } else {
      ElMessage.error((e && e.message) || '排班保存失败，请重试')
    }
  } finally {
    saving.value = false
  }
}

async function changeWeek(delta) {
  if (!(await confirmDiscard())) return
  weekStart.value = formatDate(addDays(localDate(weekStart.value), delta * 7))
  fetchMatrix()
}

async function goCurrentWeek() {
  if (!(await confirmDiscard())) return
  weekStart.value = formatDate(mondayOf(new Date()))
  fetchMatrix()
}

async function handleStationChange(nextStationId) {
  if (nextStationId === stationId.value) return
  // 未保存改动未确认时直接返回：stationId 不变，界面与数据保持一致
  if (!(await confirmDiscard())) return
  stationId.value = nextStationId
  discardChanges()
  fetchMatrix()
}

/**
 * 班次变化：只同步图例与下拉选项，不整体重拉矩阵——
 * 重拉会把排班表里未保存的改动一起冲掉，而班次增删改并不影响这些格子指向的 shiftId
 */
async function handleShiftChanged() {
  if (effectiveStationId.value == null) return
  try {
    shifts.value = await getShifts(effectiveStationId.value)
  } catch (e) {
    /* 拦截器已统一提示，图例保持旧值不影响排班编辑 */
  }
}

async function refreshPage() {
  if (!(await confirmDiscard())) return
  fetchMatrix()
}

onMounted(async () => {
  if (isAdmin.value) await loadStations()
  fetchMatrix()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped lang="scss">
.schedule-page {
  .header-station {
    width: 160px;
  }

  .grid-card {
    margin-bottom: var(--sp-4);
  }

  .week-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);

    &__nav {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
    }

    &__range {
      font-size: var(--fs-body);
      font-weight: var(--fw-medium);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;
    }
  }

  .grid-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);
    margin-bottom: var(--sp-3);

    &__right {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
      flex-shrink: 0;
    }
  }

  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  .shift-legend {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--sp-1) var(--sp-4);

    &__label {
      font-size: var(--fs-caption);
      color: var(--text-3);
    }

    &__item {
      display: inline-flex;
      align-items: center;
      gap: var(--sp-1);
      font-size: var(--fs-caption);
      color: var(--text-2);
      font-variant-numeric: tabular-nums;
    }

    &__dot {
      width: 10px;
      height: 10px;
      border-radius: var(--r-xs);
    }

    &__off {
      color: var(--text-3);
    }
  }

  .day-head {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--sp-1);

    &__week {
      color: var(--text-2);
    }

    &__date {
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
    }

    // 今天所在列：文字改用承白字实底色，扫读时能立刻定位
    &.is-today {
      .day-head__week {
        color: var(--color-primary);
        font-weight: var(--fw-semibold);
      }
    }

    &__today {
      padding: 0 var(--sp-1);
      border-radius: var(--r-xs);
      background-color: var(--state-primary-bg);
      color: var(--state-primary-fg);
    }
  }

  .cell-shift {
    padding-left: var(--sp-2);
    border-left: 3px solid var(--cell-shift-color, transparent);
  }

  .option-shift {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-1);

    &__dot {
      width: 10px;
      height: 10px;
      border-radius: var(--r-xs);
    }

    &__off {
      color: var(--text-3);
    }
  }

  .employee-name {
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-medium);
    color: var(--text-1);
  }

  .grid-card__readonly {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  // 周标签 + 本周排班进度（A10-4）
  .week-bar__meta {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }

  .week-bar__progress {
    padding: 0 var(--sp-2);
    border-radius: var(--r-full);
    background-color: var(--state-success-bg);
    color: var(--state-success-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    font-variant-numeric: tabular-nums;

    &.is-incomplete {
      background-color: var(--state-warning-bg);
      color: var(--state-warning-fg);
    }
  }

  .employee-cell {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-2);
  }

  // 行内 / 列头批量锚点：默认隐形，hover 或键盘聚焦时才出现，不干扰矩阵扫读
  .batch-anchor {
    box-sizing: border-box;
    min-width: 28px;
    height: 44px;
    padding: 0 var(--sp-2);
    border: 0;
    border-radius: var(--r-xs);
    background: transparent;
    color: var(--color-primary-strong);
    font-size: var(--fs-caption);
    font-family: inherit;
    cursor: pointer;
    opacity: 0;
    transition: opacity var(--dur-fast) var(--ease-std);

    &:hover {
      background-color: var(--state-primary-bg);
    }

    &--head {
      height: 24px;
    }
  }

  :deep(.el-table__row:hover .batch-anchor),
  :deep(.el-table__header-wrapper th:hover .batch-anchor) {
    opacity: 1;
  }

  .batch-anchor:focus-visible {
    opacity: 1;
  }

  // 已停用 / 已删除班次的历史遗留格：警告色左条 + 文字标签，不只靠颜色区分（A10-5）
  .cell-shift.is-stale {
    border-left-style: dashed;
  }

  .cell-shift__stale {
    margin-left: var(--sp-1);
    padding: 0 var(--sp-1);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-xs);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    white-space: nowrap;
  }

  .day-head.is-incomplete .day-head__week {
    color: var(--state-warning-fg);
  }
}
</style>
