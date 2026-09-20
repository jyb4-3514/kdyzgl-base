<script setup>
import { computed } from 'vue'
import { rateText } from '../utils/format.js'

/**
 * KPI 达成率环（C4 Atom，移动端 80px 由 --gauge-size 收口）
 *
 * 状态覆盖（B0.2 七态）：
 * - 默认：环 + 中心百分比
 * - 加载：块骨架占同尺寸，数据到达不跳版
 * - 空 / 错误：灰环 + 「—」（错误原因由页面级 PageState 承担，避免同一屏两处报错文案）
 * - 边界：达成率 >100% 时环封顶，中心数字仍显示真实值（112%）
 * - 禁用 / 无权限：不适用 —— 纯展示原子组件，自身无交互元素（C4 对该组件也只登记空与边界两态）
 */
const props = defineProps({
  /** 达成率，0–1 小数（与契约 achievementRate 同口径） */
  rate: { type: Number, default: null },
  loading: { type: Boolean, default: false },
  /** 无考核数据时传 false，渲染灰环 + — */
  hasData: { type: Boolean, default: true }
})

const RADIUS = 36
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const progress = computed(() => Math.min(Math.max(Number(props.rate) || 0, 0), 1))
const dashOffset = computed(() => CIRCUMFERENCE * (1 - progress.value))
const text = computed(() => (props.hasData && props.rate !== null ? rateText(props.rate) : '—'))
const ariaLabel = computed(() =>
  props.hasData && props.rate !== null ? `KPI 达成率 ${rateText(props.rate)}` : 'KPI 达成率暂无数据'
)
</script>

<template>
  <div v-if="loading" class="gauge gauge--loading skeleton-block" />
  <div v-else class="gauge" :class="{ 'gauge--empty': !hasData }" role="img" :aria-label="ariaLabel">
    <!-- viewBox 与 --gauge-size 同为 80，故描边宽度可直接用 --gauge-stroke 的 px 值，1:1 不缩放 -->
    <svg class="gauge__svg" viewBox="0 0 80 80" aria-hidden="true">
      <circle class="gauge__track" cx="40" cy="40" :r="RADIUS" />
      <circle
        v-if="hasData && rate !== null"
        class="gauge__fill"
        cx="40"
        cy="40"
        :r="RADIUS"
        :stroke-dasharray="CIRCUMFERENCE"
        :stroke-dashoffset="dashOffset"
      />
    </svg>
    <span class="gauge__value tabular-nums">{{ text }}</span>
  </div>
</template>

<style scoped>
.gauge {
  position: relative;
  flex: none;
  width: var(--gauge-size);
  height: var(--gauge-size);
}

.gauge--loading {
  border-radius: var(--r-full);
}

.gauge__svg {
  width: 100%;
  height: 100%;

  /* 从 12 点方向起画，进度按顺时针增长 */
  transform: rotate(-90deg);
}

.gauge__track,
.gauge__fill {
  fill: none;
  stroke-width: var(--gauge-stroke);
}

.gauge__track {
  stroke: var(--gauge-track);
}

.gauge__fill {
  stroke: var(--gauge-fill);
  stroke-linecap: round;
  transition: stroke-dashoffset var(--dur-slow) var(--ease-out);
}

.gauge__value {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-num-md);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-md);
  color: var(--text-1);
}

.gauge--empty .gauge__value {
  font-size: var(--fs-body);
  color: var(--text-3);
}
</style>
