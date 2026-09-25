<script setup>
import { computed } from 'vue'
import { badgeText } from '../utils/format.js'

/**
 * 统一计数角标（UI 规范 C2）
 * 为什么抽成组件：宫格单元、消息 Tab、通知列表三处各写一份同尺寸同配色的角标，改一处必漏两处。
 *
 * 数值口径复用 badgeText：0 / 空 / 非数一律不渲染。0 是「没有待办」的确定结论，
 * 与「取数失败（null）」不是一回事，但两者都不渲染角标 —— 绝不用 0 冒充未知（B4-2 硬规则 2）。
 * 读屏语义由宿主元素的 aria-label 承担（如 QuickGridItem 的做法），故本体 aria-hidden。
 */
const props = defineProps({
  value: { type: [Number, String], default: null },
  /** 圆点变体：只提示「有更新」，不表达数量 */
  dot: { type: Boolean, default: false }
})

const text = computed(() => badgeText(props.value))
const visible = computed(() => props.dot || !!text.value)
</script>

<template>
  <span v-if="visible" class="badge" :class="{ 'badge--dot': dot }" aria-hidden="true">{{ dot ? '' : text }}</span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--badge-h);
  height: var(--badge-h);
  padding: 0 var(--badge-pad-x);
  font-size: var(--fs-badge);
  font-weight: var(--fw-medium);
  line-height: 1;
  color: var(--text-on-dark);
  background: var(--color-danger);
  border-radius: var(--r-full);
}

/* 圆点不带数量，尺寸压到 8（--sp-2）保持与数字角标的视觉重量差 */
.badge--dot {
  width: var(--sp-2);
  min-width: var(--sp-2);
  height: var(--sp-2);
  padding: 0;
}
</style>
