<script setup>
import { computed } from 'vue'

/**
 * 非交互属性标记胶囊（UI 规范 C3）
 * 为什么不复用 StatusTag：StatusTag 表达「业务状态流转中的态」（有字典、有三档形态），
 * 本组件表达「某项校验是否启用」这类静态属性，语义不同不可共用（同 --state-simulate-* 拆出的理由）。
 *
 * 只有静态两态：on=false 中性描边，on=true 语义浅底。on 为 null（未知）时按 false 渲染，
 * 不伪造「已启用」——把未知说成结论比不说更糟。
 */
const props = defineProps({
  text: { type: String, required: true },
  on: { type: Boolean, default: false },
  tone: { type: String, default: 'neutral' }
})

/** 语义键直取既有 --state-* 变量，避免为四种色调各写一条样式分支 */
const style = computed(() =>
  props.on
    ? { color: `var(--state-${props.tone}-fg)`, background: `var(--state-${props.tone}-bg)` }
    : { color: 'var(--text-2)', background: 'var(--surface-card)' }
)
</script>

<template>
  <span class="mini-chip" :class="{ 'mini-chip--on': on }" :style="style">{{ text }}</span>
</template>

<style scoped>
/* 尺寸复用既有 --tag-*，不新增 Token（UI 规范 C3） */
.mini-chip {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  line-height: 1;
  white-space: nowrap;
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.mini-chip--on {
  border-color: transparent;
}
</style>
