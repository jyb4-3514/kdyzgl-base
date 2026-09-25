<script setup>
import { computed } from 'vue'
import { moneyText, numberText } from '@/utils/format.js'

/**
 * N-03 环比/差值徽标（管理端专属）
 * 为什么不用颜色一条通道：灰度打印与色觉障碍下会完全丢失方向，
 * 故符号（↑/↓/—）+ 文字 + 颜色三通道同时表达，读屏另有 aria-label。
 */
const props = defineProps({
  /** null = 无数据，整标不渲染（绝不用 0.0% 冒充「取不到」） */
  value: { type: Number, default: null },
  mode: { type: String, default: 'percent' },
  /** 逆向指标（异常率上升 = 坏），翻转配色语义 */
  intent: { type: String, default: '' },
  size: { type: String, default: 'sm' }
})

const visible = computed(() => props.value !== null && Number.isFinite(Number(props.value)))
const direction = computed(() => {
  const num = Number(props.value)
  return num > 0 ? 'up' : num < 0 ? 'down' : 'flat'
})

/** 配色语义：默认「升为好」，intent=inverse 时反转 */
const tone = computed(() => {
  if (direction.value === 'flat') return 'flat'
  const good = props.intent === 'inverse' ? direction.value === 'down' : direction.value === 'up'
  return good ? 'up' : 'down'
})

const symbol = computed(() => (direction.value === 'up' ? '↑' : direction.value === 'down' ? '↓' : '—'))

const text = computed(() => {
  const abs = Math.abs(Number(props.value))
  if (props.mode === 'money') return moneyText(abs)
  if (props.mode === 'number') return numberText(abs)
  return `${abs.toFixed(1)}%`
})

const directionText = computed(() => (direction.value === 'up' ? '上升' : direction.value === 'down' ? '下降' : '持平'))
const ariaLabel = computed(() => `较基准${directionText.value} ${text.value}`)
</script>

<template>
  <!-- role=img 让 aria-label 成为权威可访问名，读屏直接念出方向，不依赖箭头字形 -->
  <span
    v-if="visible"
    class="boss-metric-delta"
    :class="[`boss-metric-delta--${tone}`, `boss-metric-delta--${size}`]"
    role="img"
    :aria-label="ariaLabel"
  >
    <span class="boss-metric-delta__symbol" aria-hidden="true">{{ symbol }}</span
    ><span>{{ text }}</span>
  </span>
</template>

<style scoped>
.boss-metric-delta {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  font-weight: var(--fw-semibold);
  white-space: nowrap;
  border-radius: var(--r-sm);
}

.boss-metric-delta--sm {
  padding: 1px var(--sp-1);
  font-size: var(--fs-micro);
}

.boss-metric-delta--md {
  padding: 2px var(--sp-2);
  font-size: var(--fs-caption);
}

.boss-metric-delta--up {
  color: var(--color-success);
  background: var(--color-success-surface);
}

.boss-metric-delta--down {
  color: var(--color-danger);
  background: var(--color-danger-surface);
}

/* 持平态不用 --text-3：对浅底仅 4.23:1，与 3.1 硬规则 2 冲突 */
.boss-metric-delta--flat {
  color: var(--text-2);
  background: var(--surface-subtle);
}
</style>
