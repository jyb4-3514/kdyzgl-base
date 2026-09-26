<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import LineChart from '@/components/LineChart.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatCard from '@/components/StatCard.vue'
import BossScopeNote from '../components/BossScopeNote.vue'
import { getParcelTrend } from '@/api/parcel.js'
import { numberText, percent } from '@/utils/format.js'

/**
 * B3 包裹趋势（ADMIN · 全局）
 * 关键交互：7/30 天切换、图例开关（状态提到页面级，切范围时用户的开关选择不丢）
 * 指标卡复用 StatCard：原来本页自绘 .metric，与 StatCard 同概念第二份实现（修 P35）。
 *
 * TODO(扩展): 设计稿提到「可切驿站」，但当前 Mock 的趋势接口只接收 days（见 routes/parcel.js 的 trend handler），
 * 未开放 stationId 维度；待后端按驿站维度出数后，在本页加驿站选择器并透传 stationId，图表组件无需改动。
 */
const days = ref(7)
const loading = ref(false)
const error = ref('')
const points = ref([])
const refreshing = ref(false)
/** 图例开关由页面持有，与 LineChart 双向绑定 */
const seriesVisible = ref({ inbound: true, pickup: true })

const range = computed(() =>
  points.value.length ? `${points.value[0].date} ~ ${points.value[points.value.length - 1].date}` : '-'
)
const totals = computed(() =>
  points.value.reduce(
    (acc, item) => {
      acc.inbound += item.inbound
      acc.pickup += item.pickup
      return acc
    },
    { inbound: 0, pickup: 0 }
  )
)
const pickupRate = computed(() => (totals.value.inbound ? totals.value.pickup / totals.value.inbound : 0))
const avgInbound = computed(() => (points.value.length ? Math.round(totals.value.inbound / points.value.length) : 0))

async function load() {
  loading.value = true
  error.value = ''
  try {
    points.value = await getParcelTrend({ days: days.value })
  } catch (e) {
    error.value = e.message || '加载失败'
    points.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function onRefresh() {
  await load()
}

watch(days, load)
onMounted(load)
</script>

<template>
  <div class="page page--loose">
    <PageNav title="包裹趋势" />
    <van-tabs v-model:active="days" class="bleed">
      <van-tab title="近 7 天" :name="7" />
      <van-tab title="近 30 天" :name="30" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <PageState :loading="loading" :error="error" :rows="5" @retry="load">
        <div class="card chart-card">
          <div class="flex-between chart-card__meta">
            <span>{{ range }}</span>
            <span>{{ days }} 天</span>
          </div>
          <LineChart v-model:visible="seriesVisible" :points="points" empty-text="该区间暂无包裹数据" @retry="load" />
        </div>

        <div class="stat-grid">
          <StatCard
            label="区间入库"
            :value="numberText(totals.inbound)"
            unit="件"
            tone="primary"
            value-size="md"
            dense
          />
          <StatCard
            label="区间取件"
            :value="numberText(totals.pickup)"
            unit="件"
            tone="success"
            value-size="md"
            dense
          />
          <StatCard label="区间取件率" :value="percent(pickupRate)" tone="neutral" value-size="md" dense />
          <StatCard label="日均入库" :value="numberText(avgInbound)" unit="件" tone="neutral" value-size="md" dense />
        </div>

        <BossScopeNote text="口径：入库按包裹入库时间分天聚合；取件含历史派生取件时间与实时核销" />
      </PageState>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.chart-card {
  margin-top: var(--sp-3);
}

.chart-card__meta {
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
