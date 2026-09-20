<script setup>
import { recentMonths } from '../utils/format.js'

/**
 * 账期选择（近 6 个月）
 * 为什么不用 van-picker 弹层：账期切换在 KPI 与工资单两页都是高频动作，一屏内的 chip 行一步点到，
 * 不必「开弹层 → 滚动 → 确认」三步。只给 6 个月是因为演示数据只覆盖本期与上期，更早的月份选了也是空态。
 */
defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '账期' }
})

const emit = defineEmits(['update:modelValue'])
const months = recentMonths()
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
      @click="emit('update:modelValue', month)"
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
</style>
