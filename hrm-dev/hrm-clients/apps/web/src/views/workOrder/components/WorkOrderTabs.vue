<script setup>
/**
 * 工单状态 Tab 条
 *
 * 独立于卡片之外：改前 Tab + 筛选 + 表格挤在同一张卡里，层次分不清（5.4）。
 * 计数位只在拿到计数时渲染；「超时未处理」是派生口径，>0 时用危险色提醒。
 */
defineProps({
  modelValue: { type: String, default: 'all' },
  tabs: { type: Array, default: () => [] },
  counts: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue', 'change'])

function onChange(name) {
  emit('update:modelValue', name)
  emit('change', name)
}
</script>

<template>
  <el-tabs :model-value="modelValue" class="wo-tabs" @tab-change="onChange">
    <el-tab-pane v-for="tab in tabs" :key="tab.name" :name="tab.name">
      <template #label>
        <span>{{ tab.label }}</span>
        <span
          v-if="counts[tab.name] !== undefined"
          class="wo-tabs__count"
          :class="{ 'is-danger': tab.name === 'overdueUnhandled' && counts[tab.name] > 0 }"
        >
          {{ counts[tab.name] }}
        </span>
      </template>
    </el-tab-pane>
  </el-tabs>
</template>

<style scoped lang="scss">
/* Tab 条独立于卡片：高 40px，选中项主色下划线由 --el-color-primary 承接 */
.wo-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: var(--sp-4);
  }

  :deep(.el-tabs__item) {
    height: 40px;
    font-size: var(--fs-body);
    color: var(--text-2);
  }

  :deep(.el-tabs__item.is-active) {
    color: var(--color-primary);
    font-weight: var(--fw-medium);
  }

  :deep(.el-tabs__active-bar) {
    height: 2px;
    background-color: var(--color-primary);
  }

  &__count {
    margin-left: var(--sp-1);
    font-variant-numeric: tabular-nums;

    &.is-danger {
      color: var(--color-danger);
    }
  }
}
</style>
