<script setup>
import { computed } from 'vue'

/**
 * 班次卡（UI 规范 C9）
 * 落点由架构规范 §13.2 裁决：员工端考勤域与排班页共用，管理端不消费，故放员工端域共享层，
 * 不下沉到跨域组件层（避免管理端反向依赖员工端域）。
 *
 * 色条不走接口下发的契约 hex 直出（P1-1）：契约值一旦改色会静默脱离色板，
 * 这里经「契约色 → Token」映射落色，未命中一律回落分隔线色，保证永远落在色板内。
 */
const props = defineProps({
  shiftName: { type: String, required: true },
  startTime: { type: String, default: '' },
  endTime: { type: String, default: '' },
  restMinutes: { type: [Number, String], default: 0 },
  /** 契约色 hex 或语义键（primary/accent/neutral），两种都能命中映射 */
  colorKey: { type: String, default: '' }
})

const COLOR_TOKENS = new Map([
  ['#0958D9', 'var(--color-primary)'],
  ['#FA8C16', 'var(--color-accent)'],
  ['#1F2937', 'var(--c-neutral-800)'],
  ['PRIMARY', 'var(--color-primary)'],
  ['ACCENT', 'var(--color-accent)'],
  ['NEUTRAL', 'var(--c-neutral-800)']
])

/** 归一化成大写后查表，兼容接口大小写不一致 */
const barColor = computed(
  () =>
    COLOR_TOKENS.get(
      String(props.colorKey || '')
        .trim()
        .toUpperCase()
    ) || 'var(--border-line)'
)

const timeText = computed(() => [props.startTime, props.endTime].filter(Boolean).join(' - '))

/** 休息时长为 0 或缺失时不渲染该段，避免「休息 0 分钟」这种无意义信息 */
const restText = computed(() => {
  const minutes = Number(props.restMinutes)
  return Number.isFinite(minutes) && minutes > 0 ? `休息 ${minutes} 分钟` : ''
})
</script>

<template>
  <div class="shift-card">
    <!-- 色条纯装饰：班次名与时间窗已承载语义，读屏重复播报只会干扰 -->
    <span class="shift-card__bar" :style="{ background: barColor }" aria-hidden="true" />
    <div class="shift-card__body">
      <span class="shift-card__name">{{ shiftName }}</span>
      <span v-if="timeText" class="shift-card__time">
        {{ timeText }}<template v-if="restText"> · {{ restText }}</template>
      </span>
    </div>
  </div>
</template>

<style scoped>
.shift-card {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
}

.shift-card__bar {
  flex: none;
  width: var(--shift-bar-w);
  height: 32px;
  border-radius: var(--r-xs);
}

.shift-card__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.shift-card__name {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
}

.shift-card__time {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
