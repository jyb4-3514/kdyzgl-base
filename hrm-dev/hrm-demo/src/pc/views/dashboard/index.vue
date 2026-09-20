<template>
  <div class="dashboard">
    <PageHeader title="数据看板" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 首屏主指标：今日入库 / 今日取件 / 在库待取 / 异常件，可点下钻到包裹列表 -->
    <el-row :gutter="16" class="dash-row">
      <el-col v-for="card in heroCards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <MetricCard
          v-if="!parcelLoading"
          class="dash-card"
          variant="hero"
          :label="card.label"
          :value="card.value"
          :icon="card.icon"
          :tone="card.tone"
          :trend="card.trend"
          :error="parcelError"
          :error-text="card.errorText"
          clickable
          @click="router.push(card.to)"
        />
        <el-card v-else class="dash-card" shadow="never"><el-skeleton :rows="2" animated /></el-card>
      </el-col>
    </el-row>

    <p class="dash-caption">环比口径：今日值 vs 近 7 天日均值（数据契约暂无同比字段，前端本地推导）</p>

    <!-- 次级指标条：包裹 / 同步 / 工单各出一项，单项失败只影响自己那一格（5.2 第 7 条） -->
    <el-row :gutter="16" class="dash-row">
      <el-col v-for="card in inlineCards" :key="card.key" :xs="12" :sm="12" :md="8" :lg="6">
        <MetricCard
          class="dash-card"
          variant="inline"
          :label="card.label"
          :value="card.value"
          :unit="card.unit"
          :format="card.format"
          :loading="card.loading"
          :error="card.error"
          :error-text="card.errorText"
          :accent="card.key === 'overSlaCount'"
          :tone="card.tone"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-head">
              <span>包裹趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                <el-radio-button :value="7">近 7 天</el-radio-button>
                <el-radio-button :value="30">近 30 天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <TrendChart :data="trend" :loading="trendLoading" :error="trendError" @retry="loadTrend" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-head"><span>同步健康度</span></div>
          </template>

          <el-skeleton v-if="healthLoading" :rows="3" animated />
          <StateBlock v-else-if="healthError" variant="error" title="同步健康度加载失败" @action="loadSyncHealth" />
          <template v-else>
            <div class="health-rate">
              <span class="health-rate__value">{{ (syncHealth.latestBatchSuccessRate * 100).toFixed(1) }}%</span>
              <span class="health-rate__label">最近批次成功率</span>
            </div>
            <!-- 绿色取 600 档（#389E0D），与 --color-success（700 档）不同档，暂保留 L1 直引（P2-4 已登记） -->
            <el-progress
              :percentage="Math.round(syncHealth.latestBatchSuccessRate * 100)"
              :stroke-width="8"
              :show-text="false"
              :color="syncHealth.failedStationCount ? 'var(--color-warning-icon)' : 'var(--c-green-600)'"
            />
            <ul class="health-list">
              <li>
                <span>同步失败驿站</span>
                <strong :class="{ 'is-danger': syncHealth.failedStationCount > 0 }">
                  {{ syncHealth.failedStationCount }} 个
                </strong>
              </li>
              <li>
                <span>最近批次时间</span>
                <strong>{{ syncHealth.lastBatchTime || '—' }}</strong>
              </li>
            </ul>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-head">
              <span>驿站排行 TOP5</span>
              <el-radio-group v-model="rankSort" size="small" @change="loadRanking">
                <el-radio-button value="parcelTotal">按包裹量</el-radio-button>
                <el-radio-button value="pickupRate">按取件率</el-radio-button>
                <el-radio-button value="abnormalRate">按异常率</el-radio-button>
              </el-radio-group>
            </div>
          </template>

          <el-skeleton v-if="rankLoading" :rows="5" animated />
          <StateBlock v-else-if="rankError" variant="error" title="驿站排行加载失败" @action="loadRanking" />
          <StateBlock v-else-if="!rankRows.length" variant="empty" title="暂无排行数据" />
          <!-- 排行用行式布局而非 el-table：行内要放进度条与副信息，表格列宽会被挤成一行省略号 -->
          <ol v-else class="rank">
            <li v-for="row in rankRows" :key="row.id" class="rank__row">
              <span class="rank__badge" :class="row.rank <= 3 ? `rank-${row.rank}` : 'rank-rest'">{{ row.rank }}</span>
              <span class="rank__name">{{ row.stationName }}</span>
              <span class="rank__value"
                >{{ row.mainValue }}<i class="rank__unit">{{ row.mainUnit }}</i></span
              >
              <span class="rank__bar" :aria-label="`占最高值的 ${row.percent}%`">
                <i class="rank__bar-fill" :class="`is-${rankSort}`" :style="{ width: `${row.percent}%` }" />
              </span>
              <span class="rank__sub">{{ row.sub }}</span>
            </li>
          </ol>
          <p class="block-caption">当前按{{ rankLabel }}排序；进度条为相对当前列表最大值的百分比，非绝对值。</p>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="block-card">
          <template #header>
            <div class="block-head">
              <span>工单 / SLA</span>
              <el-button link type="primary" @click="goWorkOrder">查看工单</el-button>
            </div>
          </template>

          <el-skeleton v-if="woLoading" :rows="3" animated />
          <StateBlock v-else-if="woError" variant="error" title="工单指标加载失败" @action="loadWorkOrder" />
          <template v-else>
            <!-- 4 项 2×2 + 时长项独占整行：改前 5 项塞 2 列会留孤儿格，且"件数"与"时长"混排（修 P19） -->
            <div class="wo-grid">
              <div v-for="item in woMetrics.primary" :key="item.label" class="wo-item">
                <span class="wo-item__value" :class="{ 'is-danger': item.danger }">{{ item.value }}</span>
                <span class="wo-item__label">{{ item.label }}</span>
              </div>
              <div class="wo-item wo-item--wide">
                <span class="wo-item__value">{{ woMetrics.avgValue }}</span>
                <span class="wo-item__label">平均处理时长（件数与时长不同量纲，独占一行）</span>
              </div>
            </div>
            <p v-if="workOrder.overSlaCount > 0" class="wo-alert">
              有 {{ workOrder.overSlaCount }} 条工单超时未处理
              <el-button link type="danger" @click="goWorkOrder">去处理</el-button>
            </p>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 一期口径指标降为末块：看板重心是包裹/同步/工单，组织规模默认折叠 -->
    <el-card shadow="never" class="block-card org-card">
      <button type="button" class="org-toggle" :aria-expanded="orgOpen" @click="orgOpen = !orgOpen">
        <span>组织规模（一期口径）</span>
        <span class="org-toggle__sum">{{ orgSummaryText }}</span>
        <el-icon :size="14" class="org-toggle__arrow" :class="{ 'is-open': orgOpen }"><ArrowDown /></el-icon>
      </button>
      <div v-show="orgOpen" class="org-body">
        <p v-if="baseError" class="org-error">组织规模加载失败，请刷新重试</p>
        <dl v-else class="org-list">
          <div v-for="item in baseCards" :key="item.key" class="org-list__item">
            <dt>{{ item.label }}</dt>
            <dd>{{ summary ? summary[item.key] : '—' }}</dd>
          </div>
        </dl>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { getSummary } from '@admin/api/dashboard'
import { getParcelRanking, getParcelSummary, getParcelTrend } from '../../api/parcel.js'
import { getSyncTasks } from '../../api/syncTask.js'
import { getWorkOrders } from '../../api/workOrder.js'
import { formatMinutes, parseDateTime } from '../../utils/format.js'
import PageHeader from '../../components/PageHeader.vue'
import MetricCard from '../../components/MetricCard.vue'
import StateBlock from '../../components/StateBlock.vue'
import TrendChart from '../../components/TrendChart.vue'

/**
 * 扩展版看板（T14，demo-ui-redesign.md 5.2）
 * 重排理由：改前一等公民是一期的员工/部门/驿站 4 个数字，包裹/同步/工单反而靠后，
 * 与"快递驿站"的业务重心错位；本次把包裹指标提到首屏，一期指标降为可折叠末块。
 * 数据来源与口径完全沿用改造前的接口调用，只改呈现。
 */

const router = useRouter()

const loading = ref(false)
const trendDays = ref(7)
const rankSort = ref('parcelTotal')
const orgOpen = ref(false)
const updatedAt = ref('')

const summary = ref(null)
const parcelSummary = ref(null)
const trend = ref([])
const ranking = ref([])
const workOrder = ref({
  pendingCount: 0,
  processingCount: 0,
  overSlaCount: 0,
  todayNewCount: 0,
  avgHandleMinutes: null
})
// 同步健康度用「各驿站最近一个已结束批次」聚合，避免待领取/执行中批次把分母撑大导致健康度恒为 0
const syncHealth = ref({ latestBatchSuccessRate: 0, failedStationCount: 0, lastBatchTime: '' })

// 每个区块独立记录失败，让"哪一块挂了"在页面上直接可见（修 P14 的错误态伪装成空态）
const baseError = ref(false)
const parcelError = ref(false)
const healthError = ref(false)
const woError = ref(false)
const rankError = ref(false)
const trendError = ref(false)

const parcelLoading = ref(false)
const healthLoading = ref(false)
const woLoading = ref(false)
const rankLoading = ref(false)
const trendLoading = ref(false)

const baseCards = [
  { key: 'employeeTotal', label: '员工总数' },
  { key: 'stationTotal', label: '驿站数' },
  { key: 'departmentTotal', label: '部门数' },
  { key: 'todayLoginCount', label: '今日登录数' }
]

const HERO_META = [
  { key: 'todayInbound', label: '今日入库', icon: 'Box', tone: 'blue', to: '/parcel' },
  { key: 'todayPickup', label: '今日取件', icon: 'Checked', tone: 'green', to: '/parcel?status=2' },
  { key: 'pendingPickup', label: '在库待取', icon: 'Clock', tone: 'orange', to: '/parcel?status=1' },
  { key: 'abnormalCount', label: '异常件', icon: 'Warning', tone: 'red', to: '/parcel?status=3' }
]

const num = (value) => (value == null ? null : Number(value))

/** 环比：今日值 vs 近 N 天日均（排除最后一天），数据契约无同比字段，故本地推导（9.3 D-6） */
function dayOverDay(key) {
  const list = trend.value
  if (list.length < 2) return null
  const today = num(list[list.length - 1][key]) || 0
  const history = list.slice(0, -1)
  const avg = history.reduce((sum, item) => sum + (num(item[key]) || 0), 0) / history.length
  if (!avg) return null
  const diff = (today - avg) / avg
  return {
    dir: diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat',
    text: `${Math.abs(diff * 100).toFixed(1)}%`
  }
}

const heroCards = computed(() =>
  HERO_META.map((meta) => ({
    ...meta,
    value: parcelSummary.value ? parcelSummary.value[meta.key] : null,
    trend: meta.key === 'todayInbound' || meta.key === 'todayPickup' ? dayOverDay(meta.key) : null,
    errorText: `${meta.label}加载失败`
  }))
)

const inlineCards = computed(() => [
  {
    key: 'parcelTotal',
    label: '包裹总量',
    value: parcelSummary.value ? parcelSummary.value.parcelTotal : null,
    unit: '件',
    tone: 'blue',
    loading: parcelLoading.value,
    error: parcelError.value,
    errorText: '包裹总量加载失败'
  },
  {
    key: 'pickupRate',
    label: '今日取件率',
    value: parcelSummary.value ? parcelSummary.value.pickupRate : null,
    format: (value) => `${(Number(value) * 100).toFixed(1)}%`,
    tone: 'green',
    loading: parcelLoading.value,
    error: parcelError.value,
    errorText: '取件率加载失败'
  },
  {
    key: 'successRate',
    label: '同步成功率',
    value: healthLoading.value ? null : syncHealth.value.latestBatchSuccessRate,
    format: (value) => `${(Number(value) * 100).toFixed(1)}%`,
    tone: 'neutral',
    loading: healthLoading.value,
    error: healthError.value,
    errorText: '同步成功率加载失败'
  },
  {
    key: 'overSlaCount',
    label: '超时未处理工单',
    value: woLoading.value ? null : workOrder.value.overSlaCount,
    unit: '条',
    tone: 'red',
    loading: woLoading.value,
    error: woError.value,
    errorText: '超时未处理工单加载失败'
  }
])

const woMetrics = computed(() => ({
  primary: [
    { label: '待处理', value: workOrder.value.pendingCount },
    { label: '处理中', value: workOrder.value.processingCount },
    { label: '今日新增', value: workOrder.value.todayNewCount },
    { label: '超时未处理', value: workOrder.value.overSlaCount, danger: workOrder.value.overSlaCount > 0 }
  ],
  avgValue: workOrder.value.avgHandleMinutes == null ? '—' : formatMinutes(workOrder.value.avgHandleMinutes)
}))

const rankLabel = computed(
  () => ({ parcelTotal: '包裹量', pickupRate: '取件率', abnormalRate: '异常率' })[rankSort.value]
)

/**
 * TODO(扩展): 看板本轮未扩项，留待与「人事/财务/KPI」一起收口（A4-1 / A4-3 / A4-5）：
 *   ① 次级指标扩到 6 项（新增「采集异常驿站」「待审核工资单」并可下钻）；
 *   ② 同步成功率改用 GET /sync/overview 的权威 counts，删掉下面按 100 条样本的前端聚合；
 *   ③ 删掉 dash-caption 的负 margin 对抗写法。
 */

/** 排行行：主值为当前排序口径，副信息三端固定顺序（包裹 · 取件率 · 异常率） */
const rankRows = computed(() => {
  const list = ranking.value
  const key = rankSort.value
  const nums = list.map((row) => num(row[key]) || 0)
  const max = nums.length ? Math.max(...nums) : 0
  const isRate = key !== 'parcelTotal'
  return list.map((row, index) => {
    const value = num(row[key]) || 0
    return {
      id: row.stationId ?? row.id ?? index,
      rank: index + 1,
      stationName: row.stationName,
      mainValue: isRate ? (value * 100).toFixed(1) : value.toLocaleString('zh-CN'),
      mainUnit: isRate ? '%' : '件',
      percent: max ? Math.round((value / max) * 100) : 0,
      sub: `包裹 ${(num(row.parcelTotal) || 0).toLocaleString('zh-CN')} · 取件率 ${(
        (num(row.pickupRate) || 0) * 100
      ).toFixed(1)}% · 异常率 ${((num(row.abnormalRate) || 0) * 100).toFixed(1)}%`
    }
  })
})

const orgSummaryText = computed(() =>
  summary.value ? `员工 ${summary.value.employeeTotal} · 驿站 ${summary.value.stationTotal}` : '—'
)

const headerSub = computed(() => {
  const parts = ['数据范围：全域']
  if (parcelSummary.value) parts.push(`包裹 ${Number(parcelSummary.value.parcelTotal).toLocaleString('zh-CN')} 件`)
  if (updatedAt.value) parts.push(`更新于 ${updatedAt.value}`)
  return parts.join(' · ')
})

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

async function loadBase() {
  baseError.value = false
  try {
    summary.value = await getSummary()
  } catch (e) {
    baseError.value = true
  }
}

async function loadParcel() {
  parcelLoading.value = true
  parcelError.value = false
  try {
    parcelSummary.value = await getParcelSummary()
  } catch (e) {
    parcelError.value = true
  } finally {
    parcelLoading.value = false
  }
}

async function loadTrend() {
  trendLoading.value = true
  trendError.value = false
  try {
    trend.value = await getParcelTrend(trendDays.value)
  } catch (e) {
    trend.value = []
    trendError.value = true
  } finally {
    trendLoading.value = false
  }
}

async function loadRanking() {
  rankLoading.value = true
  rankError.value = false
  try {
    ranking.value = (await getParcelRanking(rankSort.value)).slice(0, 5)
  } catch (e) {
    ranking.value = []
    rankError.value = true
  } finally {
    rankLoading.value = false
  }
}

async function loadSyncHealth() {
  healthLoading.value = true
  healthError.value = false
  try {
    const page = await getSyncTasks({ pageNum: 1, pageSize: 100 })
    const latestByStation = new Map()
    page.list
      .filter((task) => task.status === 2 || task.status === 3)
      .forEach((task) => {
        if (!latestByStation.has(task.stationId)) latestByStation.set(task.stationId, task)
      })
    const batches = [...latestByStation.values()]
    const parcelTotal = batches.reduce((sum, task) => sum + task.parcelTotal, 0)
    const successTotal = batches.reduce((sum, task) => sum + task.successCount, 0)
    syncHealth.value = {
      latestBatchSuccessRate: parcelTotal ? successTotal / parcelTotal : 0,
      failedStationCount: batches.filter((task) => task.status === 3).length,
      lastBatchTime: batches.reduce((latest, task) => (task.createTime > latest ? task.createTime : latest), '')
    }
  } catch (e) {
    healthError.value = true
  } finally {
    healthLoading.value = false
  }
}

/**
 * 工单指标：Mock 没有汇总接口，用列表接口的 total 与首页样本推算
 * TODO(扩展): 待后端提供 /dashboard/work-order-summary 后改为单次请求（含精确的 avgHandleMinutes）
 */
async function loadWorkOrder() {
  woLoading.value = true
  woError.value = false
  try {
    const [pending, processing, overSla, recent, resolved] = await Promise.all([
      getWorkOrders({ status: 0, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ status: 1, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 1 }),
      getWorkOrders({ pageNum: 1, pageSize: 100 }),
      getWorkOrders({ status: 2, pageNum: 1, pageSize: 100 })
    ])
    const todayStart = new Date().setHours(0, 0, 0, 0)
    const durations = resolved.list
      .map((order) => parseDateTime(order.resolvedTime) - parseDateTime(order.createTime))
      .filter((ms) => ms > 0)
    workOrder.value = {
      pendingCount: pending.total,
      processingCount: processing.total,
      overSlaCount: overSla.total,
      todayNewCount: recent.list.filter((order) => parseDateTime(order.createTime) >= todayStart).length,
      avgHandleMinutes: durations.length ? durations.reduce((sum, ms) => sum + ms, 0) / durations.length / 60000 : null
    }
  } catch (e) {
    woError.value = true
  } finally {
    woLoading.value = false
  }
}

function goWorkOrder() {
  router.push('/work-order')
}

async function loadAll() {
  loading.value = true
  await Promise.all([loadBase(), loadParcel(), loadSyncHealth(), loadWorkOrder(), loadTrend(), loadRanking()])
  updatedAt.value = stamp()
  loading.value = false
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
.dashboard {
  .dash-row {
    margin-bottom: var(--sp-4);
  }

  .dash-card {
    height: 100%;
  }

  .dash-caption {
    margin: calc(-1 * var(--sp-2)) 0 var(--sp-4);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  .block-card {
    height: 100%;
    margin-bottom: var(--sp-4);
  }

  .block-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  .block-caption {
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  .health-rate {
    display: flex;
    align-items: baseline;
    gap: var(--sp-2);
    margin-bottom: var(--sp-2);

    &__value {
      font-size: var(--fs-num-lg);
      font-weight: var(--fw-semibold);
      line-height: var(--lh-num-lg);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;
    }

    &__label {
      font-size: var(--fs-caption);
      color: var(--text-3);
    }
  }

  .health-list {
    margin: var(--sp-3) 0 0;
    padding: 0;
    list-style: none;
    font-size: var(--fs-body);

    li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: var(--sp-2) 0;
      border-top: 1px solid var(--border-line);
      color: var(--text-3);
    }

    strong {
      font-weight: var(--fw-medium);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;

      &.is-danger {
        color: var(--color-danger);
      }
    }
  }

  .rank {
    margin: 0;
    padding: 0;
    list-style: none;

    &__row {
      display: grid;
      grid-template-columns: 24px auto 1fr auto;
      grid-template-areas:
        'badge name value value'
        'bar bar bar sub';
      align-items: center;
      gap: var(--sp-1) var(--sp-2);
      padding: var(--sp-2) 0;
      border-bottom: 1px solid var(--border-line);

      &:last-child {
        border-bottom: none;
      }
    }

    &__badge {
      grid-area: badge;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: var(--r-xs);
      font-size: var(--fs-caption);
      font-weight: var(--fw-medium);
      font-variant-numeric: tabular-nums;

      // 1/2/3 名用实底白字：改前 PC 用红色表示冠军，与"危险"语义冲突（修 P18）
      &.rank-1 {
        background-color: var(--rank-1-bg);
        color: var(--text-inverse);
      }

      &.rank-2 {
        background-color: var(--rank-2-bg);
        color: var(--text-inverse);
      }

      &.rank-3 {
        background-color: var(--rank-3-bg);
        color: var(--text-inverse);
      }

      &.rank-rest {
        background-color: var(--rank-rest-bg);
        color: var(--rank-rest-fg);
      }
    }

    &__name {
      grid-area: name;
      font-size: var(--fs-body-strong);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
    }

    &__value {
      grid-area: value;
      justify-self: end;
      font-size: var(--fs-num-sm);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;
    }

    &__unit {
      margin-left: 2px;
      font-size: var(--fs-caption);
      font-weight: var(--fw-regular);
      color: var(--text-3);
      font-style: normal;
    }

    &__bar {
      grid-area: bar;
      height: 8px;
      border-radius: var(--r-full);
      background-color: var(--surface-sunken);
      overflow: hidden;
    }

    &__bar-fill {
      display: block;
      height: 100%;
      border-radius: var(--r-full);

      // 进度条色随口径（三端一致，见 6.3）
      &.is-parcelTotal {
        background-color: var(--chart-inbound);
      }

      &.is-pickupRate {
        background-color: var(--chart-pickup);
      }

      &.is-abnormalRate {
        background-color: var(--color-danger-icon);
      }
    }

    &__sub {
      grid-area: sub;
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
      color: var(--text-3);
    }
  }

  .wo-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: var(--sp-3);
  }

  .wo-item {
    padding: var(--sp-3);
    border-radius: var(--r-md);
    background-color: var(--surface-subtle);

    &--wide {
      grid-column: 1 / -1;
    }

    &__value {
      display: block;
      font-size: var(--fs-num-md);
      font-weight: var(--fw-semibold);
      line-height: var(--lh-num-md);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;

      &.is-danger {
        color: var(--color-danger);
      }
    }

    &__label {
      display: block;
      margin-top: var(--sp-1);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
      color: var(--text-3);
    }
  }

  .wo-alert {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
    margin: var(--sp-3) 0 0;
    font-size: var(--fs-caption);
    color: var(--color-danger);
  }

  .org-card {
    margin-bottom: 0;
  }

  .org-toggle {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    width: 100%;
    padding: 0;
    border: none;
    background-color: transparent;
    font-family: inherit;
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
    cursor: pointer;

    &__sum {
      margin-left: auto;
      font-size: var(--fs-caption);
      font-weight: var(--fw-regular);
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
    }

    &__arrow {
      color: var(--text-3);
      transition: transform var(--dur-base) var(--ease-std);

      &.is-open {
        transform: rotate(180deg);
      }
    }
  }

  .org-body {
    margin-top: var(--sp-4);
  }

  .org-error {
    margin: 0;
    font-size: var(--fs-caption);
    color: var(--color-danger);
  }

  .org-list {
    display: flex;
    flex-wrap: wrap;
    gap: var(--sp-6);
    margin: 0;

    &__item {
      dt {
        font-size: var(--fs-caption);
        color: var(--text-3);
      }

      dd {
        margin: var(--sp-1) 0 0;
        font-size: var(--fs-num-md);
        font-weight: var(--fw-semibold);
        color: var(--text-1);
        font-variant-numeric: tabular-nums;
      }
    }
  }
}
</style>
