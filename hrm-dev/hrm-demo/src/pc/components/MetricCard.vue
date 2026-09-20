<script setup>
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

/**
 * 指标卡（C-P2 / T08）
 * 为什么收敛成一个组件：改前同一屏「指标数值」有 28/26/22/20 四种字号（P7）、
 * hover 阴影与描边在同一页里语义不明（P8）、栅格列宽还靠 Math.round(24/n) 硬算（P9）。
 * 这里把三种用法固化为 variant：hero（首屏主指标）/ plain（次级指标条）/ inline（表格页统计条）。
 */
const props = defineProps({
  variant: { type: String, default: 'hero' }, // hero | plain | inline
  label: { type: String, required: true },
  value: { type: [Number, String], default: null },
  unit: { type: String, default: '' },
  // 页面自带的格式化（如百分比保留 1 位小数），不传则数字走千分位
  format: { type: Function, default: null },
  icon: { type: String, default: '' },
  tone: { type: String, default: 'blue' }, // blue | orange | green | red | neutral
  // { dir: 'up' | 'down' | 'flat', text: '12%' }，箭头与数字是双通道，不允许只给箭头
  trend: { type: Object, default: null },
  clickable: { type: Boolean, default: false },
  accent: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  errorText: { type: String, default: '指标加载失败' }
})

const emit = defineEmits(['click'])

const displayValue = computed(() => {
  if (props.error) return '—'
  const value = props.value
  if (value == null || value === '') return '—'
  if (props.format) return props.format(value)
  return typeof value === 'number' ? value.toLocaleString('zh-CN') : value
})

// 数值本身不带上下文，读屏只念"1,286"没意义，故拼成完整短语
const ariaLabel = computed(() => `${props.label} ${displayValue.value}${props.unit}`.trim())

const TREND_ARROW = { up: '↑', down: '↓', flat: '—' }
const trendText = computed(() => (props.trend ? `${TREND_ARROW[props.trend.dir] || ''}${props.trend.text || ''}` : ''))
</script>

<template>
  <component
    :is="clickable ? 'button' : 'div'"
    class="metric-card"
    :class="[
      `metric-card--${variant}`,
      `tone-${tone}`,
      { 'is-clickable': clickable, 'is-error': error, 'has-accent': accent }
    ]"
    :type="clickable ? 'button' : null"
    :aria-label="clickable ? ariaLabel : undefined"
    @click="clickable && emit('click')"
  >
    <span v-if="variant === 'hero' && icon" class="metric-card__icon" aria-hidden="true">
      <el-icon :size="20"><component :is="icon" /></el-icon>
    </span>

    <span class="metric-card__body">
      <span class="metric-card__label">{{ label }}</span>
      <span v-if="loading" class="metric-card__skeleton" aria-hidden="true" />
      <span v-else class="metric-card__value" :aria-label="clickable ? undefined : ariaLabel">
        {{ displayValue }}
        <span v-if="unit && displayValue !== '—'" class="metric-card__unit">{{ unit }}</span>
      </span>
      <span v-if="trend && !loading" class="metric-card__trend" :class="`is-${trend.dir}`">{{ trendText }}</span>
    </span>

    <el-icon v-if="error" class="metric-card__error" :size="16" :title="errorText" aria-hidden="true">
      <WarningFilled />
    </el-icon>
  </component>
</template>

<style scoped lang="scss">
.metric-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  box-sizing: border-box;
  width: 100%;
  padding: var(--sp-4);
  border: 1px solid var(--border-line);
  border-radius: var(--r-md);
  background-color: var(--surface-card);
  box-shadow: var(--e0);
  text-align: left;
  font-family: inherit;
  color: inherit;

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: var(--r-md);
    flex-shrink: 0;
    background-color: var(--tone-surface);
    color: var(--tone-strong);
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: var(--sp-1);
    min-width: 0;
  }

  &__label {
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__value {
    font-size: var(--fs-num-md);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-num-md);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__unit {
    margin-left: 2px;
    font-size: var(--fs-caption);
    font-weight: var(--fw-regular);
    color: var(--text-3);
  }

  &__trend {
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    font-variant-numeric: tabular-nums;

    &.is-up {
      color: var(--color-success);
    }

    &.is-down {
      color: var(--color-danger);
    }

    &.is-flat {
      color: var(--text-3);
    }
  }

  &__skeleton {
    width: 28px;
    height: 16px;
    border-radius: var(--r-xs);
    background-color: var(--surface-sunken);
  }

  &__error {
    position: absolute;
    top: var(--sp-2);
    right: var(--sp-2);
    color: var(--color-danger-icon);
  }

  // 主指标（首屏 4 大指标）：唯一允许用 Num-lg 26px 的位置
  &--hero .metric-card__value {
    font-size: var(--fs-num-lg);
    line-height: var(--lh-num-lg);
  }

  // 下沿 2px 语义色条：用在需要快速区分"类型"的指标条上
  &.has-accent::after {
    content: '';
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    height: 2px;
    border-radius: 0 0 var(--r-md) var(--r-md);
    background-color: var(--tone-strong);
  }

  // 可下钻的三态：默认 / hover / active。不可点时不出现任何悬停反馈（修 P8）
  &.is-clickable {
    cursor: pointer;
    transition:
      border-color var(--dur-fast) var(--ease-std),
      box-shadow var(--dur-fast) var(--ease-std),
      transform var(--dur-fast) var(--ease-std);

    &:hover {
      border-color: var(--color-primary-border);
      box-shadow: var(--e2);
      transform: translateY(-1px);
    }

    &:active {
      transform: translateY(0);
      box-shadow: var(--e1);
    }
  }

  // tone 映射：浅底取各色族 50 档，强调条取 500 档（仅图标/线/描边档）
  &.tone-blue {
    --tone-surface: var(--color-primary-surface);
    --tone-strong: var(--color-primary-icon);
  }

  &.tone-orange {
    --tone-surface: var(--color-accent-surface);
    --tone-strong: var(--color-accent);
  }

  &.tone-green {
    --tone-surface: var(--color-success-surface);
    --tone-strong: var(--color-success-icon);
  }

  &.tone-red {
    --tone-surface: var(--color-danger-surface);
    --tone-strong: var(--color-danger-icon);
  }

  &.tone-neutral {
    --tone-surface: var(--color-info-surface);
    --tone-strong: var(--text-3);
  }
}
</style>
