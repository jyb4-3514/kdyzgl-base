<script setup>
import { computed, ref, watch } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import { WORK_ORDER_SLA_HOURS } from '@/shared/constants/dict'
import { SLA_THRESHOLD_RATIO } from '@/shared/domain/sla.js'
import { useNow } from '@/shared/composables/useNow.js'
import { formatRemain } from '../utils/format.js'

/**
 * 工单 SLA 倒计时（C-P4 / T11）
 * 改前只有"剩余 ≤1 小时"一个固定阈值（P 相关色值也全是 Element 默认），
 * 与移动端"有/无超时两态"不一致；这里统一为文档 2.7 的四态，并改按优先级总时长的 25% 判定临近。
 */
const props = defineProps({
  deadline: { type: String, default: '' },
  finished: { type: Boolean, default: false },
  // 工单优先级：用于推算临近阈值（低 48h / 中 24h / 高 8h 的 25%）
  priority: { type: [Number, String], default: null },
  // 也可由页面直接给阈值，优先于 priority 推算
  thresholdMs: { type: Number, default: 0 }
})

const now = useNow()
const remain = computed(() => formatRemain(props.deadline, now.value))

const level = computed(() => {
  if (props.finished || !props.deadline) return 'muted'
  if (remain.value.over) return 'danger'
  const hours = WORK_ORDER_SLA_HOURS[props.priority]
  const threshold = props.thresholdMs || (hours ? hours * 3600000 * SLA_THRESHOLD_RATIO : 0)
  return threshold && remain.value.remainMs <= threshold ? 'warning' : 'normal'
})

const text = computed(() => (level.value === 'muted' ? '—' : remain.value.text))

/**
 * 30s 心跳不适合用 aria-live 一直播报（会反复打断读屏），
 * 只在"第一次跨入超时"时播报一次，之后的刷新静默。
 */
const announcement = ref('')
let announced = false
watch(
  () => remain.value.over && !props.finished && !!props.deadline,
  (over) => {
    if (over && !announced) {
      announced = true
      announcement.value = `工单已超时未处理，${remain.value.text}`
    }
  }
)
</script>

<template>
  <span class="sla" :class="`sla--${level}`">
    <el-icon v-if="level === 'warning' || level === 'danger'" :size="14" aria-hidden="true"><Clock /></el-icon>
    <span>{{ text }}</span>
  </span>
  <span class="visually-hidden" aria-live="polite">{{ announcement }}</span>
</template>

<style scoped lang="scss">
.sla {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  box-sizing: border-box;
  height: 22px;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  white-space: nowrap;

  // 正常态不占标签位：纯文本，避免整列都是彩色胶囊导致"全是告警"的错觉
  &--normal {
    color: var(--text-2);
  }

  &--warning {
    padding: 0 6px;
    border-radius: var(--r-full);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
  }

  &--danger {
    padding: 0 6px;
    border-radius: var(--r-full);
    background-color: var(--state-danger-fg);
    color: var(--text-inverse);
    font-weight: var(--fw-medium);
  }

  &--muted {
    color: var(--text-disabled);
  }
}
</style>
