<script setup>
/**
 * 步进导航按钮（员工端考勤域共享）
 *
 * 为什么成组件：attendanceRecords 的 .month-nav__btn 与 schedule 的 .week-nav__btn 是同一样式两处写，
 * 连 44×44 的字面量也各写一遍（尺寸字面量违反「--touch-min 唯一来源」）。收口后触控下限与描边只维护一处。
 *
 * 无障碍：
 * - 根元素保持原生 <button type="button">，Enter/Space 由浏览器原生承担，不重写键盘逻辑；
 * - aria-label 由调用方经属性透传（Vue 落到唯一根 button），文案必须说清步进方向（上一月/下一月/上一周/下一周）；
 * - 图标（箭头）本身 aria-hidden，不承载语义，避免读屏把「arrow」当成按钮名。
 */
const props = defineProps({
  /** 禁用态：置灰并吞掉点击（原生 disabled 已拦截，这里再兜一次，防属性透传失效） */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

function handleClick() {
  if (props.disabled) return
  emit('click')
}
</script>

<template>
  <button type="button" class="step-nav-btn" :disabled="disabled" @click="handleClick">
    <slot />
  </button>
</template>

<style scoped>
/* 尺寸取 --touch-min：步进按钮是主触控目标，44 是下限而非建议值（UI 规范 N1） */
.step-nav-btn {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--touch-min);
  height: var(--touch-min);
  font-size: var(--fs-h2);
  color: var(--text-1);
  background: var(--surface-subtle);
  border: 1px solid var(--border-line);
  border-radius: var(--r-sm);
}

.step-nav-btn:disabled {
  color: var(--text-disabled);
}
</style>
