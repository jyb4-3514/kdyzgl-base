<script setup>
/**
 * 状态筛选 chip 组（设计规范 §5.2.2-C1）
 *
 * 为什么抽出来：这套 chip（换行 + 44px 触控高 + 选中态描边）在补卡两页已各写一遍，
 * 请假三页若再各写一遍就是第五份实现（项目规则「同一逻辑不得重复实现三次以上」）。
 *
 * TODO(扩展): 补卡两页（staff/makeupList.vue、boss/makeupApproval.vue）迁移到本组件后，
 *   删掉它们各自的 .filter-row / .fchip 样式块；本轮按批次要求不做该重构。
 */
defineProps({
  /** [{ value, label }]，value 为空串表示「全部」 */
  items: { type: Array, required: true },
  /** 当前选中值，与 items[].value 严格比较（含空串） */
  active: { type: [String, Number], default: '' },
  /** 读屏用分组说明 */
  label: { type: String, default: '按状态筛选' }
})

const emit = defineEmits(['change'])
</script>

<template>
  <div class="filter-chips" role="group" :aria-label="label">
    <button
      v-for="item in items"
      :key="item.value || 'all'"
      type="button"
      class="filter-chips__chip"
      :class="{ 'filter-chips__chip--active': active === item.value }"
      :aria-pressed="active === item.value"
      @click="emit('change', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<style scoped>
.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

/* 筛选 chip 是主触控目标，高度 44 达标（7.4） */
.filter-chips__chip {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.filter-chips__chip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}
</style>
