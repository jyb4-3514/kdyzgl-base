<script setup>
import { recentMonths } from './format.js'

/**
 * 账期选择（近 6 个月）
 * 为什么不用 van-picker 弹层：账期切换在 KPI 与工资单两页都是高频动作，一屏内的 chip 行一步点到，
 * 不必「开弹层 → 滚动 → 确认」三步。只给 6 个月是因为演示数据只覆盖本期与上期，更早的月份选了也是空态。
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '账期' },
  /** 整页重载期间禁用：切换账期会触发全量重查，不禁用会被连点打出多次请求 */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])
const months = recentMonths()

function pick(month) {
  if (props.disabled) return
  emit('update:modelValue', month)
}
</script>

<template>
  <div class="month-row" role="radiogroup" :aria-label="label">
    <button
      v-for="month in months"
      :key="month"
      type="button"
      class="chip month-row__chip"
      :class="{ 'chip--active': month === modelValue }"
      role="radio"
      :aria-checked="month === modelValue"
      :aria-disabled="disabled || undefined"
      :disabled="disabled"
      @click="pick(month)"
    >
      {{ month }}
    </button>
  </div>
</template>

<style scoped>
/* 横向滚动而不是折行：折行会把筛选区撑到两行，首屏可见内容被挤掉 */
.month-row {
  display: flex;
  gap: var(--sp-2);
  padding-bottom: var(--sp-1);
  overflow-x: auto;
  scrollbar-width: none;
}

.month-row::-webkit-scrollbar {
  display: none;
}

.month-row__chip {
  flex: none;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
}

/* 禁用态压成中性灰：WCAG 1.4.3 对失效控件豁免对比度，故可用 --text-disabled */
.month-row__chip:disabled {
  color: var(--text-disabled);
  background: var(--surface-sunken);
  border-color: var(--border-line);
}
</style>
