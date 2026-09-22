<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import HomeQuickGrid from '@/mobile/components/HomeQuickGrid.vue'
import LineChart from '@/mobile/components/LineChart.vue'
import PageState from '@/mobile/components/PageState.vue'
import StatCard from '@/mobile/components/StatCard.vue'
import BossRankBar from '../components/BossRankBar.vue'
import { getAttendanceSummary } from '@/mobile/api/attendance.js'
import { getDashboardSummary } from '@/mobile/api/dashboard.js'
import { getParcelRanking, getParcelSummary, getParcelTrend, getParcels } from '@/mobile/api/parcel.js'
import { getSyncOverview, getSyncTasks } from '@/mobile/api/syncTask.js'
import { getWorkOrders } from '@/mobile/api/workOrder.js'
import { BOSS_QUICK_ENTRIES } from '@/mobile/constants/quickEntries.js'
import { useTodoStore } from '@/mobile/stores/todo.js'
import { clockText, hoursAgoParam, numberText, percent, relativeTime, shortDateText } from '@/mobile/utils/format.js'

/**
 * B2 经营总览（ADMIN · 全局数据）
 * 指标口径见 demo-design.md 7.4.7：本站/全局的收敛由 Mock 层按角色强制完成，前端只负责展示与下钻。
 * 首屏顺序（3.2 / B2）：Hero（经营概览 + 待办总数）→ 4 指标 → 异常条 → 快捷宫格 → 趋势 → 同步健康度 → 驿站 TOP3 → 组织规模。
 * 待办类数字统一由 stores/todo.js 提供：角标、消息页分组、Hero 总数共用一份，避免同一数字三处各算（A1-1 原则 4）。
 */
const router = useRouter()
const todo = useTodoStore()

const loading = ref(true)
const error = ref('')
const summary = ref(null)
const team = ref(null)
const trend = ref([])
const sync = ref({ total: 0, failed: 0, lastBatchTime: '' })
const overdueUnhandled = ref(0)
/** 采集状态总览（需求1）：null 表示接口不可用，卡片显示「暂无数据」而不是误导为 0 */
const overview = ref(null)
/** 今日考勤异常项数（宫格「考勤概览」）：null 表示取数失败，角标不渲染 */
const attendanceAbnormal = ref(null)
/** 超 48h 未取件数（宫格「异常预警」的一项）：null 表示取数失败 */
const overdueParcels = ref(null)
const topStations = ref([])
/** 一期指标对老板是次要信息，默认折叠，避免占据首屏视线 */
const showOrg = ref(false)

const dateText = shortDateText()
const updatedText = `数据截止 ${clockText()}`

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [
      parcelSummary,
      dashboard,
      trendPoints,
      syncPage,
      overduePage,
      ranking,
      syncOverview,
      attendance,
      overdueParcelPage
    ] = await Promise.all([
      getParcelSummary(),
      getDashboardSummary(),
      getParcelTrend({ days: 7 }),
      getSyncTasks({ pageNum: 1, pageSize: 100 }),
      // 只要总数，取 1 条即可（避免为看一个数字拉回 120 条工单）
      getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 1 }),
      getParcelRanking(),
      // 采集状态只占一行数字，取数失败不该让整页变错误态（与打卡状态的降级口径一致）
      getSyncOverview().catch(() => null),
      // 宫格数据属「锦上添花」，一律独立降级为 null，由宫格按项显示「—」或无角标（B4-2 硬规则 1）
      getAttendanceSummary().catch(() => null),
      getParcels({ status: 1, endTime: hoursAgoParam(48), pageNum: 1, pageSize: 1 }).catch(() => null)
    ])
    summary.value = parcelSummary
    team.value = dashboard
    trend.value = trendPoints
    sync.value = {
      total: syncPage.total,
      failed: syncPage.list.filter((task) => task.status === 3).length,
      lastBatchTime: syncPage.list.length ? syncPage.list[0].createTime : ''
    }
    overdueUnhandled.value = overduePage.total
    topStations.value = ranking.slice(0, 3)
    overview.value = syncOverview
    attendanceAbnormal.value = attendance
      ? attendance.lateCount + attendance.earlyLeaveCount + attendance.absentCount
      : null
    overdueParcels.value = overdueParcelPage ? overdueParcelPage.total : null
    // 待办计数在首页预取（C5-5），供宫格角标与消息 Tab 角标共用
    await todo.refresh()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const alertText = computed(() => {
  const messages = []
  if (overdueUnhandled.value) messages.push(`${overdueUnhandled.value} 条工单超时未处理`)
  if (sync.value.failed) messages.push(`${sync.value.failed} 个批次同步失败`)
  return messages.join('，')
})

/**
 * 「异常预警」总数（B2 #8）= 超 48h 未取件 + 同步失败批次 + 超时未处理工单 + 采集异常/未配置。
 * 四项里任一项取不到就整体不显示角标：用一个「部分已知」的数字冒充完整口径，比不显示更容易误导。
 */
const alertTotal = computed(() => {
  const collect = overview.value ? overview.value.counts.abnormal + overview.value.counts.unconfigured : null
  const parts = [overdueParcels.value, sync.value.failed, overdueUnhandled.value, collect]
  if (parts.some((value) => value == null)) return null
  return parts.reduce((sum, value) => sum + value, 0)
})

/** 宫格实时值：计数型给数值（null=未知），状态型给文案（null=取数失败显示 `—`） */
const quickData = computed(() => ({
  orders: todo.counts.orders ?? null,
  makeups: todo.counts.makeups ?? null,
  payrolls: todo.counts.payrolls ?? null,
  flows: todo.counts.flows ?? null,
  // 待终审请假：与消息 Tab 的「待终审请假」同一份快照（M11 §3.4-A）
  leaves: todo.counts.leaves ?? null,
  attendance: attendanceAbnormal.value,
  alerts: alertTotal.value,
  trend: summary.value ? `${numberText(summary.value.todayPickup)} 件` : null,
  rank: topStations.value.length ? topStations.value[0].stationName : '暂无数据'
}))

/** 近 7 天日均：数据契约无同比字段，环比只能本地推导（已知偏差 D-6） */
const trendStats = computed(() => {
  const len = trend.value.length || 1
  const sum = trend.value.reduce(
    (acc, item) => {
      acc.inbound += item.inbound
      acc.pickup += item.pickup
      return acc
    },
    { inbound: 0, pickup: 0 }
  )
  return { avgInbound: sum.inbound / len, avgPickup: sum.pickup / len, avgTotal: sum.pickup / len }
})

/** TODO(扩展): 待后端出环比字段后改为直接取值，不再用「今日 vs 近 7 天日均」推导 */
function trendOf(today, avg) {
  return avg > 0 ? ((today - avg) / avg) * 100 : null
}

/** 传 BossRankBar 的行数据：条形按批内最大值归一化，名次配色由组件统一取真源 --rank-* */
const rankItems = computed(() =>
  topStations.value.map((item) => ({ key: item.stationId, name: item.stationName, value: item.parcelTotal }))
)
</script>

<template>
  <div class="page">
    <!-- 加载态：Hero + 4 卡分别占位，数据到达时版面不跳（B2 三态） -->
    <template v-if="loading">
      <div class="skeleton-block sk-hero" />
      <div class="stat-grid stat-grid--roomy">
        <div v-for="i in 4" :key="i" class="skeleton-block sk-card" />
      </div>
    </template>

    <PageState v-else :error="error" @retry="load">
      <!-- Hero：深蓝灰底色建立「经营报告」心智，与员工端的品牌蓝 Hero 明确区分（3.2） -->
      <section class="hero hero--deep">
        <div class="flex-between">
          <span class="hero__title">今日经营</span>
          <!-- 口径不可切换：不做成像按钮的 chip，避免用户反复点击（A12-6）；真的开放切换时再改回控件 -->
          <span class="hero__scope" title="当前演示账号固定为全域口径，暂不支持切换">口径：全域</span>
        </div>
        <p class="hero__sub">{{ dateText }} · {{ updatedText }}</p>
        <!-- 待办总数：待办取数全失败时显示 ···，不用 0 冒充「没有待办」（B4-2 硬规则 2） -->
        <p class="hero__sub">今日待处理 {{ todo.known ? numberText(todo.total) : '···' }} 条</p>
      </section>

      <div class="stat-grid stat-grid--roomy">
        <StatCard
          label="今日入库"
          :value="numberText(summary.todayInbound)"
          unit="件"
          tone="primary"
          :trend="trendOf(summary.todayInbound, trendStats.avgInbound)"
          @click="router.push('/boss/trend')"
        />
        <StatCard
          label="今日取件"
          :value="numberText(summary.todayPickup)"
          unit="件"
          tone="success"
          :trend="trendOf(summary.todayPickup, trendStats.avgPickup)"
          @click="router.push('/boss/trend')"
        />
        <StatCard
          label="待取件"
          :value="numberText(summary.pendingPickup)"
          unit="件"
          tone="warning"
          @click="router.push('/boss/alerts')"
        />
        <StatCard
          label="异常件"
          :value="numberText(summary.abnormalCount)"
          unit="件"
          tone="danger"
          @click="router.push('/boss/alerts')"
        />
      </div>

      <van-notice-bar
        v-if="alertText"
        class="notice"
        left-icon="warning-o"
        :text="alertText"
        wrapable
        color="var(--color-danger)"
        background="var(--color-danger-surface)"
        @click="router.push('/boss/alerts')"
      />
      <van-notice-bar
        v-else
        class="notice"
        left-icon="passed"
        text="暂无超时未处理工单与同步失败批次"
        color="var(--color-success)"
        background="var(--color-success-surface)"
      />
    </PageState>

    <!-- 快捷功能宫格：4 列 × 2 行共 8 项，全部带实时数据（B5-1）。
         排在异常提示条之后 —— 提示条刚说完「N 条工单超时未处理」，紧接着就是处理入口。
         宫格独立于 PageState 骨架、不被整块骨架替换：项名先渲染，仅数据位随 loading 处于加载态（B4-2 · P1-3）。 -->
    <HomeQuickGrid
      v-if="!error"
      :loading="loading"
      :entries="BOSS_QUICK_ENTRIES"
      :data="quickData"
      hint="按待办优先排序"
    />

    <!-- 趋势及以下依赖首屏数据：加载中保留图表骨架占位，数据到达后展示 -->
    <div v-if="loading" class="skeleton-block sk-chart" />
    <template v-else-if="!error">
      <div class="section-title">
        <span>近 7 天包裹趋势</span>
        <button type="button" class="section-title__extra link" @click="router.push('/boss/trend')">查看详情 ›</button>
      </div>
      <div
        class="card trend-card"
        role="button"
        tabindex="0"
        @click="router.push('/boss/trend')"
        @keydown.enter="router.push('/boss/trend')"
      >
        <LineChart :points="trend" compact empty-text="该区间暂无包裹数据" />
        <div class="flex-between trend-foot">
          <span class="muted">区间取件率</span>
          <span class="tabular-nums">{{ percent(summary.pickupRate) }}</span>
          <span class="muted">日均取件</span>
          <span class="tabular-nums">{{ numberText(Math.round(trendStats.avgPickup)) }} 件</span>
        </div>
      </div>

      <div class="section-title">
        同步健康度<span class="section-title__extra">近 {{ sync.total }} 个批次</span>
      </div>
      <div class="card">
        <van-cell title="失败批次" :value="`${sync.failed} 个`" :value-class="sync.failed ? 'cell-danger' : ''" />
        <!-- 未配置采集单独一行：老板看不到「有驿站压根没配采集」才是需求1要解决的核心盲区 -->
        <van-cell
          title="未配置采集"
          :value="overview ? `${overview.counts.unconfigured} 站` : '暂无数据'"
          :value-class="overview && overview.counts.unconfigured ? 'cell-warning' : ''"
          is-link
          to="/boss/alerts"
        />
        <van-cell title="最近批次时间" :value="sync.lastBatchTime ? relativeTime(sync.lastBatchTime) : '-'" />
        <van-cell title="包裹总量" :value="`${numberText(summary.parcelTotal)} 件`" />
      </div>

      <div class="section-title">
        <span>驿站 TOP3</span>
        <button type="button" class="section-title__extra link" @click="router.push('/boss/rank')">全部 ›</button>
      </div>
      <div class="card rank-list">
        <!-- 排行行收口到 BossRankBar（N-01）：名次配色与条形只在此处定义，避免与排行页分叉 -->
        <BossRankBar
          :items="rankItems"
          metric="count"
          :max-visible="3"
          empty-text="暂无驿站数据"
          @select="router.push('/boss/rank')"
        />
      </div>

      <!-- 组织规模：一期指标移到末位并默认折叠（B2） -->
      <button type="button" class="org-head" :aria-expanded="showOrg" @click="showOrg = !showOrg">
        <span>组织规模（一期）</span>
        <van-icon :name="showOrg ? 'arrow-up' : 'arrow-down'" aria-hidden="true" />
      </button>
      <van-cell-group v-show="showOrg" inset>
        <van-cell title="员工总数" :value="`${team.employeeTotal} 人`" />
        <van-cell title="驿站总数" :value="`${team.stationTotal} 个`" />
        <van-cell title="部门总数" :value="`${team.departmentTotal} 个`" />
        <van-cell title="今日登录" :value="`${team.todayLoginCount} 人`" />
      </van-cell-group>
    </template>
  </div>
</template>

<style scoped>
.sk-hero {
  height: 88px;
  margin-top: var(--sp-4);
}

.sk-card {
  height: 88px;
}

.sk-chart {
  height: 96px;
  margin-top: var(--sp-6);
}

.trend-card {
  cursor: pointer;
}

.trend-foot {
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
}

/* 可点的区块标题右侧动作：高 44 满足触控，视觉仍是 Caption 字号 */
.link {
  min-height: 44px;
  padding: 0;
  color: var(--color-primary);
  background: none;
  border: none;
}

/* 不可交互的口径说明：纯文字表达，去掉 chip 的底与边（A12-6） */
.hero__scope {
  flex: none;
  font-size: var(--fs-caption);
  color: rgba(255, 255, 255, 0.82);
}

/* van-cell 的 value 元素在子组件内部，拿不到 scoped 属性，只能 :deep 命中 */
:deep(.cell-danger) {
  color: var(--color-danger);
}

:deep(.cell-warning) {
  color: var(--color-warning);
}

/* 卡片内不叠第二层带阴影的卡：按 alerts.vue 的既有正例改浅底无阴影（AP-03） */
.rank-list :deep(.list-item) {
  background: var(--surface-subtle);
  box-shadow: none;
}

.org-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  margin-top: var(--sp-6);
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
  background: none;
  border: none;
}
</style>
