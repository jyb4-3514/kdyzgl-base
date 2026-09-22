<script setup>
import { computed } from 'vue'

/**
 * 列表行卡片（UI 规范 C4）
 * 为什么成组件：行点击的「role + tabindex + Enter + Space」四件套此前在 5 处各写一遍且已经不一致
 * （工资单页缺 Space），这里固化为一份实现。
 *
 * 硬约束（UI 规范 C4）：行内独立操作（复制、跳详情按钮等）必须是本组件的**兄弟节点**，
 * 不能塞进标题行 —— 塞进去会把独立操作的点击冒泡成「整行跳转」，用户点复制却跳走了。
 *
 * 行间距由列表容器（flex + gap）负责：scoped 样式无法跨组件书写 `.list-item + .list-item`。
 */
const props = defineProps({
  clickable: { type: Boolean, default: false },
  /** 传 to 时根元素用 router-link（首选）；可点但无路由时降级为带原生键兜底的 div */
  to: { type: [String, Object], default: '' },
  /** 需要一眼扫到的行（未读/待处理）：左侧主色竖条 */
  marked: { type: Boolean, default: false },
  /** 失败/异常行：左侧危险色竖条 */
  failed: { type: Boolean, default: false },
  /** 行密度：2 = 两行（64），3 = 三行（76）；高度一律由 --row-h-* 派生 */
  density: { type: Number, default: 2 }
})

const emit = defineEmits(['click'])

const linked = computed(() => props.clickable && !!props.to)
const rootTag = computed(() => (linked.value ? 'router-link' : 'div'))

/** 不可点时不挂任何交互属性：避免造出「看着能点、点了没反应」的假按钮 */
const rootAttrs = computed(() => {
  if (!props.clickable) return {}
  return linked.value ? { to: props.to } : { role: 'button', tabindex: 0 }
})

/** 非可点行不派发事件，避免父级误把被动渲染当成用户操作 */
function handleClick() {
  if (props.clickable) emit('click')
}
</script>

<template>
  <component
    :is="rootTag"
    v-bind="rootAttrs"
    class="list-item-card"
    :class="{
      'list-item-card--marked': marked,
      'list-item-card--failed': failed,
      'list-item-card--density-3': density === 3
    }"
    @click="handleClick"
    @keydown.enter.prevent="handleClick"
    @keydown.space.prevent="handleClick"
  >
    <div class="list-item-card__title">
      <slot name="title" />
      <slot name="extra" />
    </div>
    <slot />
    <div v-if="$slots.tags" class="list-item-card__tags"><slot name="tags" /></div>
  </component>
</template>

<style scoped>
.list-item-card {
  display: block;
  min-height: var(--row-h-2);
  padding: var(--sp-3) var(--sp-4);
  color: inherit;
  text-decoration: none;
  background: var(--surface-card);
  border-radius: var(--r-lg);
  box-shadow: var(--e1);
  /* 高频点击：去掉 300ms 延迟与点击高亮 */
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

.list-item-card--density-3 {
  min-height: var(--row-h-3);
}

/* 未读/待处理：主色竖条；失败/异常：危险色竖条。色 + 位置双通道，不只靠颜色 */
.list-item-card--marked {
  border-left: 3px solid var(--color-primary-icon);
}

.list-item-card--failed {
  border-left: 3px solid var(--color-danger-icon);
}

.list-item-card__title {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
}

.list-item-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  align-items: center;
  margin-top: var(--sp-2);
}
</style>
