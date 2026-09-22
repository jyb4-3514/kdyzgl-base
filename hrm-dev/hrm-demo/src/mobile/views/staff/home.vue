<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AttendanceStatusBar from '../../components/AttendanceStatusBar.vue'
import HomeQuickGrid from '../../components/HomeQuickGrid.vue'
import PageState from '../../components/PageState.vue'
import StatCard from '../../components/StatCard.vue'
import { getAttendanceStatus } from '../../api/attendance.js'
import { getKpiScoreQuiet } from '../../api/kpi.js'
import { getParcelSummary } from '../../api/parcel.js'
import { getWorkOrders } from '../../api/workOrder.js'
import { KPI_CODE } from '@/shared/constants/errorCode.js'
import { roleLabel } from '../../constants/accounts.js'
import { STAFF_QUICK_ENTRIES } from '../../constants/quickEntries.js'
import { useAuthStore } from '../../stores/auth.js'
import { useTodoStore } from '../../stores/todo.js'
import { attendanceProgress } from '../../utils/attendance.js'
import { numberText, percent, recentMonths } from '../../utils/format.js'

/**
 * S1 工作台（STATION_ADMIN / STAFF）
 * 数据范围收敛为本站：Mock 层对非 ADMIN 强制覆盖 station_id，前端不再传、也不再过滤（避免两处口径）
 * 首屏顺序（3.2 / A3）：Hero（站名 + 角色 + 待处理总数与构成）→ 出勤状态条 + 一键打卡 →
 * 工单超时提示条 → 4 指标 → 快捷宫格 → 包裹口径行。
 *
 * 三处刻意收敛：
 * 1. 「今日待处理」的构成（工单 / 工资单 / 补卡）统一由 stores/todo.js 给出，与消息 Tab 角标、待办子视图同源；
 * 2. 原来的「打卡提示条」被出勤状态条取代 —— 同一出勤状态不用两处表达（A1-1 原则 4）；
 * 3. 待办类取数一律独立降级为 null（显示 `···`），绝不用 0 冒充「没有待办」（B4-2 硬规则 2）。
 */
const auth = useAuthStore()
const todo = useTodoStore()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const refreshing = ref(false)
const summary = ref(null)
const overdueOrders = ref(0)
/** 今日打卡状态：不参与工作台整体三态，接口失败只让状态条降级，不打断包裹/工单数据 */
const attStatus = ref(null)
const attLoading = ref(true)
const attError = ref('')
/** 我的 KPI 本月得分：null 表示取数失败（宫格显示 `—`） */
const kpiText = ref(null)

const progress = computed(() => attendanceProgress(attStatus.value))

/** 待处理总数的构成：只说总数会让员工以为「还有 5 条工单」，点进去发现是工资单待确认 */
const breakdown = computed(() => {
  const counts = todo.counts
  const cell = (value, unit) => (value == null ? '···' : `${numberText(value)} ${unit}`)
  const parts = [
    `待处理工单 ${cell(counts.orders, '条')}`,
    `待确认工资单 ${cell(counts.myPayrolls, '张')}`,
    `补卡申请 ${cell(counts.myMakeups, '单')}`,
    `请假申请 ${cell(counts.myLeaves, '单')}`
  ]
  // 站长多一条初审队列：员工端与站长共用同一张待办表，只有站长会多出「待初审请假」分组
  if (auth.role === 'STATION_ADMIN') parts.push(`待初审请假 ${cell(counts.leaveReview, '单')}`)
  return `含${parts.join(' · ')}`
})

const quickData = computed(() => ({
  attendance: attStatus.value ? `已完成 ${progress.value.done}/${progress.value.total}` : null,
  orders: todo.counts.orders ?? null,
  myPayrolls: todo.counts.myPayrolls ?? null,
  myMakeups: todo.counts.myMakeups ?? null,
  // 站长初审待办：与消息 Tab 的「待初审请假」同一份快照，不为一个角标再拉一次列表（B6-3）
  leaveReview: todo.counts.leaveReview ?? null,
  // 无排班时 todayStatus 会下发规则合成的兜底班次，故必须用 hasSchedule 区分「未排班」与「真班次」
  schedule: attStatus.value
    ? attStatus.value.hasSchedule && attStatus.value.shift
      ? attStatus.value.shift.shiftName
      : '未排班'
    : null,
  kpi: kpiText.value
}))

async function loadAttendance() {
  attLoading.value = true
  attError.value = ''
  try {
    attStatus.value = await getAttendanceStatus()
  } catch (e) {
    attStatus.value = null
    attError.value = e.message || '出勤状态获取失败'
  } finally {
    attLoading.value = false
  }
}

/** 9204（该月尚未算分）是业务空态而非取数失败，走 silent 取数避免首页平白弹一次失败提示 */
async function loadKpi() {
  try {
    const detail = await getKpiScoreQuiet(auth.user.id, { month: recentMonths()[0] })
    kpiText.value = `${detail.totalScore} 分`
  } catch (e) {
    kpiText.value = e.code === KPI_CODE.SCORE_NOT_EXISTS ? '未考核' : null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [parcelSummary, overduePage] = await Promise.all([
      getParcelSummary(),
      getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 1 })
    ])
    summary.value = parcelSummary
    overdueOrders.value = overduePage.total
    await Promise.all([todo.refresh(), loadAttendance(), loadKpi()])
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function onRefresh() {
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <PageState :loading="loading" :error="error" :rows="6" @retry="load">
        <!-- Hero：站名 + 角色 chip + 今日待处理条数与构成（建立「待办优先」心智） -->
        <section class="hero hero--brand station">
          <div class="flex-between">
            <span class="hero__title">{{ auth.user.stationName || '未归属驿站' }}</span>
            <span class="hero__chip">{{ roleLabel(auth.user.role) }}</span>
          </div>
          <p class="hero__sub">
            {{ auth.user.realName }}，今日待处理 {{ todo.known ? numberText(todo.total) : '···' }} 条
          </p>
          <p class="hero__sub">{{ breakdown }}</p>
        </section>

        <!-- 出勤状态条 + 一键打卡：取代原「打卡提示条」，同一出勤状态不用两处表达（A1-1 原则 4） -->
        <AttendanceStatusBar :status="attStatus" :loading="attLoading" :error="attError" @refresh="loadAttendance" />

        <van-notice-bar
          v-if="overdueOrders"
          class="notice"
          left-icon="warning-o"
          :text="`本站有 ${overdueOrders} 条工单超时未处理，请尽快处理`"
          wrapable
          color="var(--color-danger)"
          background="var(--color-danger-surface)"
          @click="router.push('/staff/workorder')"
        />

        <div class="stat-grid">
          <StatCard
            label="今日入库"
            :value="numberText(summary.todayInbound)"
            unit="件"
            tone="primary"
            value-size="staff"
            @click="router.push('/staff/parcel')"
          />
          <StatCard
            label="待取件"
            :value="numberText(summary.pendingPickup)"
            unit="件"
            tone="warning"
            value-size="staff"
            @click="router.push('/staff/pickup')"
          />
          <StatCard
            label="今日取件"
            :value="numberText(summary.todayPickup)"
            unit="件"
            tone="success"
            value-size="staff"
          />
          <StatCard
            label="异常件"
            :value="numberText(summary.abnormalCount)"
            unit="件"
            tone="danger"
            value-size="staff"
            @click="router.push('/staff/parcel')"
          />
        </div>
      </PageState>

      <!-- 快捷功能宫格：4 列 × 2 行共 8 项，「打卡」固定第 1 位（决策已确认），其余按待办优先排序（B1/B3）。
           宫格独立于 PageState 骨架、不被整块骨架替换：项名先渲染，仅数据位随 loading 处于加载态（B4-2 · P1-3）。 -->
      <HomeQuickGrid v-if="!error" :entries="STAFF_QUICK_ENTRIES" :data="quickData" :loading="loading" />

      <p v-if="summary" class="tip">
        本站包裹总量 {{ numberText(summary.parcelTotal) }} 件 · 取件率 {{ percent(summary.pickupRate) }}
      </p>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.station {
  margin-top: var(--sp-3);
}
</style>
