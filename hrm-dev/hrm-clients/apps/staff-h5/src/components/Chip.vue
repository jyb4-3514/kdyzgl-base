<script setup>
/**
 * 可点筛选/切换胶囊（UI 规范 C1）
 * 为什么抽成组件：同一视觉此前有全局 `.chip`、`FilterChips` 内置样式、补卡页私有 `.fchip` 三份实现，
 * 收敛成一个原子后，改选中态只需改一处。
 *
 * 状态说明：选中是「视觉变体」不是状态；加载/空/错误由父级容器表达（本组件不取数）。
 */
defineProps({
  label: { type: String, required: true },
  /** 选中态，由父级持有（子组件不改 props） */
  active: { type: Boolean, default: false },
  /** danger 用于超时类筛选（需要立刻行动），其余用 neutral */
  tone: { type: String, default: 'neutral' },
  /** Vant 图标名，留空则不渲染图标 */
  icon: { type: String, default: '' }
})

const emit = defineEmits(['click'])
</script>

<template>
  <button
    type="button"
    class="chip"
    :class="[`chip--${tone}`, { 'chip--active': active }]"
    :aria-pressed="active"
    @click="emit('click')"
  >
    <van-icon v-if="icon" :name="icon" aria-hidden="true" />
    {{ label }}
  </button>
</template>

<style scoped>
/* 高度取 --touch-min：筛选 chip 是主触控目标，44 是下限而不是建议值 */
.chip {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  min-height: var(--touch-min);
  padding: 0 var(--sp-4);
  font-size: var(--fs-body);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.chip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.chip--danger.chip--active {
  color: var(--color-danger);
  background: var(--color-danger-surface);
  border-color: var(--color-danger);
}
</style>
