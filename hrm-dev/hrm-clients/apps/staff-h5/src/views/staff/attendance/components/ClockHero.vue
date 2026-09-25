<script setup>
import { computed } from 'vue'
import { STATION_FALLBACK } from '../model/attendanceUi.js'

/**
 * 打卡页顶部时钟（时钟 + 日期 + 驿站）
 *
 * role="timer" 而非 aria-live：秒级刷新若逐秒播报会淹没读屏，故只给 aria-label 静态描述（沿用原页取舍）。
 * 未挂驿站的演示账号显示「未归属驿站」而不是空白，由本组件兜底，调用方只传原始值。
 * 时钟恒有值，无远程取数，故不适用 loading / empty / error 三态。
 */
const props = defineProps({
  time: { type: String, default: '' },
  dateText: { type: String, default: '' },
  stationName: { type: String, default: '' }
})

const stationDisplay = computed(() => props.stationName || STATION_FALLBACK)
</script>

<template>
  <section class="clock">
    <p class="clock__time tabular-nums" role="timer" :aria-label="`当前时间 ${time}`">{{ time }}</p>
    <p class="clock__date">{{ dateText }} · {{ stationDisplay }}</p>
  </section>
</template>

<style scoped>
.clock {
  padding: var(--sp-6) 0 var(--sp-4);
  text-align: center;
}

.clock__time {
  margin: 0;
  font-size: var(--fs-clock);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-clock);
  color: var(--text-1);
}

.clock__date {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
