<script setup>
import { computed } from 'vue'

/**
 * 字典驱动的状态标签（C-P3 / T10，修 P5、P30）
 * 改前直接用 el-tag：PC 浅底实心、移动描边空心，同一状态两端视觉权重不同（P5），
 * 且"实心/空心"随机出现、不承载任何语义（P30）。
 * 现在固化为三形态：soft（状态描述）/ outline（无动作诉求）/ solid（需要立刻行动），配色统一取 2.7 映射表的 Token。
 * 说明：dict 只提供 el-tag 语义色，不足以区分"待入库(soft)"与"已退回(outline)"这类同色不同形态，
 * 故 variant 由调用页按值显式指定，颜色仍由 dict 的 type 决定。
 */
const props = defineProps({
  dict: { type: Object, required: true },
  value: { type: [Number, String], default: null },
  variant: { type: String, default: 'soft' } // soft | outline | solid
})

const item = computed(() => props.dict[props.value] || null)

/** el-tag 语义色 → 本文档的状态色族（info 收口到中性，避免"信息蓝"与品牌蓝混淆） */
const TONE_BY_TYPE = { primary: 'primary', success: 'success', warning: 'warning', danger: 'danger' }
const tone = computed(() => TONE_BY_TYPE[(item.value && item.value.type) || ''] || 'neutral')
</script>

<template>
  <span v-if="item" class="status-tag" :class="[`status-tag--${variant}`, `tone-${tone}`]">{{ item.label }}</span>
  <span v-else class="status-tag status-tag--empty">—</span>
</template>

<style scoped lang="scss">
.status-tag {
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: var(--tag-h);

  /* 纵向 2px 不在 4px 网格上，改了会动胶囊高度，本轮只把横向换成 Token（P2-7 已登记） */
  padding: 2px var(--tag-pad-x);
  border: 1px solid transparent;
  border-radius: var(--r-xs);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  white-space: nowrap;
  cursor: default;

  &--soft {
    border-color: var(--state-border);
    background-color: var(--state-bg);
    color: var(--state-fg);
  }

  // 描边式：白底 + 中性描边，用于"已关闭/已退回/低优先级"这类无动作诉求的状态
  &--outline {
    border-color: var(--state-outline-border);
    background-color: var(--state-outline-bg);
    color: var(--state-outline-fg);
  }

  // 实心式：仅用于"高优先级/超时"。底色取各色族 600/700 档，白字对比度全部 ≥4.5:1
  &--solid {
    border-color: transparent;
    background-color: var(--state-fg);
    color: var(--text-inverse);
  }

  &--empty {
    height: auto;
    padding: 0;
    color: var(--text-disabled);
  }

  &.tone-primary {
    --state-bg: var(--state-primary-bg);
    --state-fg: var(--state-primary-fg);
    --state-border: var(--state-primary-border);
  }

  &.tone-success {
    --state-bg: var(--state-success-bg);
    --state-fg: var(--state-success-fg);
    --state-border: var(--state-success-border);
  }

  &.tone-warning {
    --state-bg: var(--state-warning-bg);
    --state-fg: var(--state-warning-fg);
    --state-border: var(--state-warning-border);
  }

  &.tone-danger {
    --state-bg: var(--state-danger-bg);
    --state-fg: var(--state-danger-fg);
    --state-border: var(--state-danger-border);
  }

  &.tone-neutral {
    --state-bg: var(--state-neutral-bg);
    --state-fg: var(--state-neutral-fg);
    --state-border: var(--state-neutral-border);
  }
}
</style>
