<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StationPicker from '@kdyzgl/shared/ui/StationPicker.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { bossConfirm } from '../components/bossConfirm.js'
import { deleteShift, getShifts, updateShift } from '@/api/attendance.js'
import { getStationList } from '@/api/org.js'
import { STATION_STATUS } from '@kdyzgl/shared/constants/dict.js'

/**
 * B12 班次管理 · 列表（ADMIN，设计 ⑭.2）
 *
 * 为什么独立成页（不塞进「打卡规则」）：班次是打卡时段与上下班时间的唯一时间真源，
 * 有自己完整的一套增删改与站点级约束，与「规则」只是派生关系，拆开后规则页保持只读派生、职责不重叠。
 *
 * 站点级校验说明：启用数 >2 / 一早一晚为**前置提示**（本页只提示不阻断列表），
 * 真实拦截以服务端 9114 为准（设计 ⑫-31：演示态以本页提示兜底）。
 */
const router = useRouter()

const loading = ref(true)
const error = ref('')
const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const stationId = ref(null)
const list = ref([])
const showStation = ref(false)
/** 行内动作的忙碌标记（停用 / 启用 / 删除）：只锁当行，不冻结整表 */
const busyId = ref(null)

/** 启用班次上限：打卡时段最多 2 段（一早一晚），超限服务端回 9114 */
const MAX_ENABLED_SHIFTS = 2
/** 午前 / 午后的界值（分钟）：与后端 middayBoundaryMinute 默认值同口径；界面不出现常量字面量（⑭.3-(6)） */
const MIDDAY_BOUNDARY_MIN = 12 * 60

const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '-'
})

const enabledCount = computed(() => list.value.filter((item) => item.status === 1).length)

const minutesOfDay = (text) => {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  return Number.isFinite(h) && Number.isFinite(m) ? h * 60 + m : NaN
}

/** 班次开始时间落点：午前(0) / 午后(1)，与后端 ordinal 口径一致（一早一晚才能区分打卡时段） */
const ordinalOf = (startTime) => (minutesOfDay(startTime) < MIDDAY_BOUNDARY_MIN ? 0 : 1)

/**
 * 站点级提示条（互斥取一条，超限优先；与 PC ShiftManager 的 stationHint 同口径）：
 * 只提示不阻断列表，说明「为什么这两个班次会打架 / 会挤掉打卡时段」。
 */
const stationHint = computed(() => {
  const enabled = list.value.filter((item) => item.status === 1)
  if (enabled.length > MAX_ENABLED_SHIFTS) {
    return `本站启用班次 ${enabled.length} 个，超过 ${MAX_ENABLED_SHIFTS} 个上限：打卡时段只取其中互不重叠的 ${MAX_ENABLED_SHIFTS} 个，建议停用多余班次。`
  }
  const ordinals = enabled.map((item) => ordinalOf(item.startTime))
  if (new Set(ordinals).size !== ordinals.length) {
    return '本站启用班次存在同落上午 / 下午的班次：打卡时段需一早一晚才可区分，请调整班次起止或停用其一。'
  }
  return ''
})

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

/** 弹层内重试：失败只落在弹层区块，不把整页打成错误态（列表可能仍在正常展示） */
async function retryStations() {
  try {
    await loadStations()
  } catch (e) {
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  }
}

async function load() {
  if (stationId.value == null) return
  loading.value = true
  error.value = ''
  try {
    list.value = await getShifts({ stationId: stationId.value })
  } catch (e) {
    list.value = []
    error.value = e.message || '加载失败'
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
  // 无可用驿站时不必再拉班次，直接给空态让用户看到「没有可选驿站」的根因
  if (stationId.value == null) {
    loading.value = false
    return
  }
  await load()
}

function pickStation(id) {
  if (id === stationId.value) return
  stationId.value = id
  load()
}

function goEdit(row) {
  router.push(`/boss/shifts/${row.id}/edit`)
}

/** 停用 / 启用：携带该行全字段 + 翻转后的 status（PUT 不含 stationId，归属不可改） */
async function toggleStatus(row) {
  if (busyId.value != null) return
  busyId.value = row.id
  const nextStatus = row.status === 1 ? 0 : 1
  try {
    await updateShift(row.id, {
      shiftName: row.shiftName,
      startTime: row.startTime,
      endTime: row.endTime,
      color: row.color,
      restMinutes: row.restMinutes,
      status: nextStatus
    })
    showSuccessToast(nextStatus === 1 ? '班次已启用' : '班次已停用')
    await load()
  } catch (e) {
    // 启用可能触发站点级 9114（超限 / 一早一晚）：就地提示并刷新，让用户看到真实状态
    showFailToast(e.message || '操作失败')
    await load()
  } finally {
    busyId.value = null
  }
}

/** 删除：二次确认（不可恢复）；被排班引用时展示服务端 400 文案，列表不变 */
async function askDelete(row) {
  if (busyId.value != null) return
  const ok = await bossConfirm({
    action: '删除班次',
    target: `${currentStationName.value} · ${row.shiftName} ${row.startTime}-${row.endTime}`,
    impact: '已被排班引用的班次不能删除；删除后不可恢复',
    irreversible: true,
    confirmText: '确认删除'
  })
  if (!ok) return
  busyId.value = row.id
  try {
    await deleteShift(row.id)
    showSuccessToast('班次已删除')
    await load()
  } catch (e) {
    // 服务端 400「该班次已被排班引用，不能删除」原样展示，列表保持不变
    showFailToast(e.message || '删除失败')
  } finally {
    busyId.value = null
  }
}

const actions = computed(() => [{ key: 'create', label: '新增班次', plain: false }])

function onAction(key) {
  if (key === 'create') router.push('/boss/shifts/create')
}

onMounted(init)
</script>

<template>
  <div class="shift-page">
    <PageNav title="班次管理" />
    <div class="page page--bar">
      <button type="button" class="station-pick card" @click="showStation = true">
        <span class="muted">当前驿站</span>
        <span class="station-pick__value">{{ currentStationName }}<van-icon name="arrow" aria-hidden="true" /></span>
      </button>

      <!-- 因果关系说明：把「班次要在这里维护、改完打卡时间即时跟随」讲在列表之前（设计 ⑭.4） -->
      <p class="tip">
        班次决定打卡时段与上下班时间：本页新增 / 调整班次后，本站打卡时间即时跟随，无需在「打卡规则」另行配置。
      </p>

      <PageState :loading="loading" :error="error" :rows="4" @retry="load">
        <!-- 站点级提示条：只提示不阻断（超限优先取一条） -->
        <p v-if="stationHint" class="shift-hint" role="alert">{{ stationHint }}</p>

        <div class="section-title">
          本驿站班次
          <span class="section-title__extra tabular-nums">共 {{ list.length }} 个班次 · 启用 {{ enabledCount }}</span>
        </div>

        <PageState
          v-if="!list.length"
          :empty="true"
          empty-text="该驿站暂无班次，点下方「新增班次」开始配置"
        />

        <template v-else>
          <!-- 整行可点进编辑的是左侧主体按钮；停用/启用/删除为并列的真实按钮，避免交互元素嵌套 -->
          <div v-for="row in list" :key="row.id" class="list-item list-item--rich shift-row">
            <button type="button" class="shift-row__main" @click="goEdit(row)">
              <span class="shift-row__head">
                <!-- 色条取接口返回的配色（仅装饰，名称文字必在，状态不只靠颜色） -->
                <i class="shift-row__bar" :style="{ background: row.color }" aria-hidden="true" />
                <span class="shift-row__name">{{ row.shiftName }}</span>
                <StatusTag
                  :dict="STATION_STATUS"
                  :value="row.status"
                  :variant="row.status === 1 ? 'soft' : 'outline'"
                />
              </span>
              <span class="shift-row__meta tabular-nums">
                {{ row.startTime }} - {{ row.endTime }} · 休息 {{ row.restMinutes }} 分钟
              </span>
            </button>
            <div class="shift-row__actions">
              <button
                type="button"
                class="shift-act"
                :aria-busy="busyId === row.id || undefined"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </button>
              <button
                type="button"
                class="shift-act shift-act--danger"
                :aria-busy="busyId === row.id || undefined"
                @click="askDelete(row)"
              >
                删除
              </button>
            </div>
          </div>
        </template>
      </PageState>
    </div>

    <ActionBar :actions="actions" @select="onAction" />

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

/* 站点级提示条：warning 浅底 + 描边，与普通说明区分开（只提示不阻断） */
.shift-hint {
  margin: var(--sp-3) 0 0;
  padding: var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-warning);
  background: var(--color-warning-surface);
  border: 1px solid var(--state-warning-border);
  border-radius: var(--r-sm);
}

.shift-row {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  width: 100%;
  text-align: left;
}

/* 主体按钮：铺满整行、无默认按钮样式，保证整行可点进编辑 */
.shift-row__main {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  width: 100%;
  padding: 0;
  text-align: left;
  background: none;
  border: none;
}

.shift-row__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  width: 100%;
}

/* 色条仅装饰，宽度取设计 Token；名称单行省略不撑破标题行 */
.shift-row__bar {
  flex: none;
  width: var(--shift-bar-w);
  height: 18px;
  border-radius: var(--r-xs);
}

.shift-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shift-row__meta {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

/* 行内动作：编辑走整行点击，停用/启用/删除用行内自绘按钮，触控区 ≥44×44 */
.shift-row__actions {
  display: flex;
  gap: var(--sp-2);
  justify-content: flex-end;
  width: 100%;
}

.shift-act {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--touch-min);
  min-height: var(--touch-min);
  padding: 0 var(--sp-2);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

.shift-act--danger {
  color: var(--color-danger);
}
</style>
