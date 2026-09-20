<script setup>
import { computed, useAttrs } from 'vue'

/**
 * 看板指标卡（C-M1，老板端总览 / 员工端工作台共用）
 * 为什么 tone 取 600/700 档：主色 500 档对白底只有 3.24:1，不足以承载数值文字（2.3 硬规则）。
 * 员工端数值 22px / 老板端 24px 由 valueSize 区分（3.2 差异表），结构完全一致。
 */
const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: null },
  unit: { type: String, default: '' },
  /** 环比百分比，如 12.4 / -3.1；无数据传 null 时不渲染整行 */
  trend: { type: [Number, String], default: null },
  tone: { type: String, default: 'neutral' },
  /** 工作台用 dense：不要环比行，压缩高度 */
  dense: { type: Boolean, default: false },
  /** lg=老板端 24 / staff=员工端 22 / md=18（次级） */
  valueSize: { type: String, default: 'lg' },
  loading: { type: Boolean, default: false },
  /** 加载失败：数值位显示「—」并在下方给出可读原因 */
  error: { type: String, default: '' }
})

const attrs = useAttrs()

// 不声明 emits，让 onClick 原样透传到根元素；页面没绑 click 时退化为静态卡片，
// 避免渲染出「看着能点、点了没反应」的假按钮（C-M1 A11y 要求）
const clickable = computed(() => typeof attrs.onClick === 'function')

const toneClass = computed(() => `stat-card--${props.tone}`)
const valueClass = computed(() => `stat-card__value--${props.valueSize}`)
const hasValue = computed(() => props.value !== null && props.value !== '' && props.value !== undefined)

const trendText = computed(() => {
  const num = Number(props.trend)
  if (props.trend === null || !Number.isFinite(num)) return ''
  if (num > 0) return `↑ ${num.toFixed(1)}%`
  if (num < 0) return `↓ ${Math.abs(num).toFixed(1)}%`
  return '— 0.0%'
})
const trendClass = computed(() => {
  const num = Number(props.trend)
  if (!Number.isFinite(num) || num === 0) return 'stat-card__trend--flat'
  return num > 0 ? 'stat-card__trend--up' : 'stat-card__trend--down'
})

const ariaLabel = computed(() => `${props.label} ${hasValue.value ? props.value : '暂无数据'}${props.unit}`)
</script>

<template>
  <component
    :is="clickable ? 'button' : 'div'"
    :type="clickable ? 'button' : undefined"
    class="stat-card tabular-nums"
    :class="[toneClass, { 'stat-card--clickable': clickable }]"
    :aria-label="ariaLabel"
  >
    <div class="stat-card__label">{{ label }}</div>

    <div v-if="loading" class="stat-card__skeleton" aria-hidden="true" />
    <div v-else class="stat-card__value" :class="valueClass">
      <template v-if="hasValue"
        >{{ value }}<span v-if="unit" class="stat-card__unit">{{ unit }}</span></template
      >
      <template v-else
        >—<span v-if="unit" class="stat-card__unit">{{ unit }}</span></template
      >
    </div>

    <p v-if="error" class="stat-card__error">{{ error }}</p>
    <p v-else-if="!dense && trendText" class="stat-card__trend" :class="trendClass">{{ trendText }}</p>
  </component>
</template>

<style scoped>
.stat-card {
  display: block;
  width: 100%;
  min-height: 88px;
  padding: var(--sp-4);
  font-family: inherit;
  text-align: left;
  background: var(--surface-card);
  border: none;
  border-radius: var(--r-lg);
  box-shadow: var(--e1);
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
  transition: transform var(--dur-fast) var(--ease-std);
}

.stat-card--clickable:active {
  transform: scale(0.985);
}

.stat-card__label {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.stat-card__value {
  margin-top: var(--sp-2);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-lg);
}

.stat-card__value--lg {
  font-size: var(--fs-num-lg-boss);
}

.stat-card__value--staff {
  font-size: var(--fs-num-lg-staff);
}

.stat-card__value--md {
  font-size: var(--fs-num-md);
  line-height: var(--lh-num-md);
}

.stat-card__unit {
  margin-left: 2px;
  font-size: var(--fs-caption);
  font-weight: var(--fw-regular);
  color: var(--text-3);
}

.stat-card__trend {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
}

.stat-card__trend--up {
  color: var(--color-success);
}

.stat-card__trend--down {
  color: var(--color-danger);
}

.stat-card__trend--flat {
  color: var(--text-3);
}

.stat-card__error {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

/* 数值位骨架：尺寸固定，避免数据到达时布局跳动 */
.stat-card__skeleton {
  width: 60px;
  height: 24px;
  margin-top: var(--sp-2);
  background: var(--surface-sunken);
  border-radius: var(--r-xs);
}

.stat-card--primary .stat-card__value {
  color: var(--color-primary);
}

.stat-card--success .stat-card__value {
  color: var(--color-success);
}

.stat-card--warning .stat-card__value {
  color: var(--color-warning);
}

.stat-card--danger .stat-card__value {
  color: var(--color-danger);
}

.stat-card--neutral .stat-card__value {
  color: var(--text-1);
}
</style>
