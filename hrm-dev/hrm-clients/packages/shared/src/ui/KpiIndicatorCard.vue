<script setup>
import { computed } from 'vue'
import { KPI_DIRECTION } from '@kdyzgl/shared/constants/dict.js'
import { rateText, valueText } from './format.js'

/**
 * KPI 指标卡（C4 Organism，移动端）
 *
 * 达成率不只靠颜色表达：进度条 + 文字百分比 + 目标/实际同排（C6 SC 1.4.1）。
 *
 * 状态覆盖（B0.2 七态）：
 * - 默认：名称 / 权重 / 目标 / 实际 / 达成率条 / 得分
 * - 加载：块骨架
 * - 空：不适用 —— 有考核结果必有指标项（C4 登记）
 * - 错误：item 缺失时给一行说明，整页错误由 PageState 承担
 * - 禁用 / 无权限：不适用 —— 纯展示，无交互元素
 * - 边界：指标名超长省略；达成率 >100% 时条封顶、文字显示真实值
 */
const props = defineProps({
  item: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const barPercent = computed(() =>
  Math.min(Math.round((Number(props.item && props.item.achievementRate) || 0) * 100), 100)
)
const directionLabel = computed(() => {
  const dict = KPI_DIRECTION[props.item && props.item.direction]
  return dict ? dict.label : ''
})

/** 三档配色（B7.5）：严重不足 <60% danger / 未达成 <100% warning / 达成 primary（500 档只用于线与条） */
const barColor = computed(() => {
  const rate = Number(props.item && props.item.achievementRate) || 0
  if (rate < 0.6) return 'var(--color-danger)'
  if (rate < 1) return 'var(--color-warning)'
  return 'var(--color-primary-icon)'
})
</script>

<template>
  <div v-if="loading" class="card kpi-card kpi-card--loading skeleton-block" />
  <div v-else-if="!item" class="card kpi-card">
    <p class="tip">{{ error || '暂无指标数据' }}</p>
  </div>
  <div v-else class="card kpi-card">
    <div class="kpi-card__head">
      <span class="kpi-card__name">{{ item.metricName }}</span>
      <span class="kpi-card__weight tabular-nums">权重 {{ item.weight }}%</span>
    </div>

    <p class="list-item__meta tabular-nums">
      目标 {{ valueText(item.targetValue, item.unit) }} · 实际 {{ valueText(item.actualValue, item.unit) }}
      <template v-if="directionLabel"> · {{ directionLabel }}</template>
    </p>

    <div class="kpi-card__bar">
      <van-progress
        class="kpi-card__progress"
        :percentage="barPercent"
        :show-pivot="false"
        :color="barColor"
        stroke-width="8"
      />
      <span class="kpi-card__rate tabular-nums">达成率 {{ rateText(item.achievementRate) }}</span>
    </div>

    <p class="list-item__meta tabular-nums">
      单项 {{ item.score }} 分 · 加权 {{ item.weightedScore }} 分<template v-if="item.scoreModeLabel">
        · {{ item.scoreModeLabel }}</template
      >
    </p>
  </div>
</template>

<style scoped>
.kpi-card--loading {
  height: 132px;
  padding: 0;
}

.kpi-card__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

/* 长指标名省略而不是折行：折行会让卡片高度参差，列表失去节奏 */
.kpi-card__name {
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-card__weight {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.kpi-card__bar {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  margin-top: var(--sp-2);
}

.kpi-card__progress {
  flex: 1;
  min-width: 0;
}

.kpi-card__rate {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-2);
}
</style>
