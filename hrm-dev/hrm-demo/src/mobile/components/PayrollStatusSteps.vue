<script setup>
/**
 * 纵向步骤条（C4：移动端的 FlowSteps 与 PayrollStatusSteps 只保留这一份实现，财务与入离职两处复用）
 *
 * 为什么纵向：375px 下横向 6 步每步仅约 55px，步骤名 4–8 字必然折行错位（B10.3 移动端规范）。
 *
 * 状态覆盖（B0.2 七态）：
 * - 默认：按 step.state 渲染 完成 / 进行中 / 等待中 / 已驳回
 * - 加载：由页面骨架承担（步骤数据与主体同时到达）
 * - 空：steps 为空时不渲染（调用方在数据未就绪前不会调它）
 * - 错误：由页面级 PageState 承担
 * - 禁用：step.state 传 muted，整条置灰（用于流程已撤销、单据已作废）
 * - 无权限：本组件不渲染任何按钮，只读降级由页面负责
 * - 边界：步骤名超长省略；步骤条数与文案长度均不设上限（纵向天然可滚动）
 *
 * 颜色全部复用语义 Token，不新增色板：完成/进行中 --color-primary-icon、驳回 --color-danger、
 * 未开始 --c-neutral-300、连接线 --border-line（C3-3）。
 */
defineProps({
  /** [{ key, label, state, time, desc }]，state: done / current / pending / danger / muted */
  steps: { type: Array, default: () => [] },
  /** 步骤条下方的口径说明（如「驳回不会撤销已产生的数据」） */
  note: { type: String, default: '' }
})

const STATE_TEXT = { done: '已完成', current: '进行中', pending: '等待中', danger: '已驳回', muted: '已作废' }
const stateText = (step) => step.stateLabel || STATE_TEXT[step.state] || STATE_TEXT.pending
</script>

<template>
  <div v-if="steps.length" class="steps" role="list">
    <div
      v-for="(step, index) in steps"
      :key="step.key"
      class="step"
      :class="[`is-${step.state || 'pending'}`, { 'step--last': index === steps.length - 1 }]"
      role="listitem"
      :aria-current="step.state === 'current' ? 'step' : undefined"
    >
      <span class="step__dot" aria-hidden="true" />
      <div class="step__body">
        <div class="step__head">
          <span class="step__name">{{ step.label }}</span>
          <span class="step__state">{{ stateText(step) }}</span>
        </div>
        <p v-if="step.desc" class="step__desc">{{ step.desc }}</p>
        <p v-if="step.time" class="step__time tabular-nums">{{ step.time }}</p>
      </div>
    </div>
  </div>
  <p v-if="note" class="tip">{{ note }}</p>
</template>

<style scoped>
.step {
  position: relative;
  padding: 0 0 var(--sp-4) calc(var(--step-dot) + var(--sp-3));
}

.step--last {
  padding-bottom: 0;
}

.step__dot {
  position: absolute;
  top: 3px;
  left: 0;
  width: var(--step-dot);
  height: var(--step-dot);
  background: var(--text-disabled);
  border-radius: var(--r-full);
}

/* 连接线画在自身内边距里：长度 = 本项高度 - 圆点，最后一项不画 */
.step:not(.step--last)::before {
  position: absolute;
  top: calc(3px + var(--step-dot));
  left: calc(var(--step-dot) / 2 - var(--step-line) / 2);
  width: var(--step-line);
  height: calc(100% - var(--step-dot) - 3px);
  content: '';
  background: var(--border-line);
}

.is-done:not(.step--last)::before,
.is-current:not(.step--last)::before {
  background: var(--color-primary-icon);
}

.is-done .step__dot,
.is-current .step__dot {
  background: var(--color-primary-icon);
}

.is-danger .step__dot {
  background: var(--color-danger);
}

/* 进行中脉冲：animation 会被全局 prefers-reduced-motion 媒体查询降级（C6 SC 2.3.3） */
.is-current .step__dot {
  animation: step-pulse 1.6s var(--ease-std) infinite;
}

@keyframes step-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 var(--color-primary-surface-strong);
  }

  50% {
    box-shadow: 0 0 0 6px var(--color-primary-surface-strong);
  }
}

.step__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.step__name {
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
  color: var(--text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.step__state {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.is-current .step__name {
  font-weight: var(--fw-semibold);
  color: var(--color-primary);
}

.is-danger .step__name,
.is-danger .step__state {
  color: var(--color-danger);
}

/* 已作废/已撤销：统一降到辅助文字色，仍保留 4.83:1 对比度（不用 --text-disabled） */
.is-muted .step__name,
.is-muted .step__state,
.is-muted .step__desc {
  color: var(--text-3);
}

.step__desc {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.step__time {
  margin: 2px 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
