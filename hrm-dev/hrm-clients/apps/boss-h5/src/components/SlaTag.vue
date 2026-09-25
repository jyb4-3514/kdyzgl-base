<script setup>
import { computed, ref, watch } from 'vue'
import { WORK_ORDER_SLA_HOURS } from '@kdyzgl/shared/constants/dict.js'
import { SLA_THRESHOLD_RATIO } from '@kdyzgl/shared/domain/sla.js'
import { useNow } from '@kdyzgl/shared/composables/useNow.js'
import { slaState } from '../utils/format.js'

/**
 * SLA 倒计时（C-M4，四态）
 * 30s 心跳改走 shared/composables/useNow：原先每行各起一个定时器（一页 20 行 = 20 个），
 * 现在与 PC 共用同一个订阅源，订阅数归零自动停表（P1-5）；30s 粒度足够（SLA 以小时计）。
 * 为什么正常态改成纯文本：2.7 规定正常不占标签位 —— 绝大部分工单不超时，
 * 全部渲染成胶囊会让列表被无信息量的标签淹没。
 */
const props = defineProps({
  deadline: { type: String, default: '' },
  /** 已解决/已关闭的工单不做超时判定（与后端 overdueUnhandled 口径一致） */
  active: { type: Boolean, default: true },
  /** 工单优先级：决定临近阈值（SLA 总时长的 25%） */
  priority: { type: [Number, String], default: null }
})

const HOUR_MS = 3600 * 1000

const now = useNow()

const thresholdMs = computed(() => {
  const hours = WORK_ORDER_SLA_HOURS[props.priority]
  return (hours || 24) * HOUR_MS * SLA_THRESHOLD_RATIO
})

const state = computed(() => slaState(props.deadline, props.active, now.value, thresholdMs.value))

// 30s 心跳不能每轮都打断读屏，只在首次超时播报一次（C-P4 A11y）
const announced = ref(false)
watch(
  () => state.value.state,
  (value) => {
    if (value === 'over') announced.value = true
  },
  { immediate: true }
)
</script>

<template>
  <span v-if="state.state === 'normal'" class="sla-text">
    <van-icon name="clock-o" aria-hidden="true" />
    <span class="sla-text__label">{{ state.text }}</span>
  </span>
  <span
    v-else-if="state.state !== 'hidden'"
    class="sla-tag"
    :class="`sla-tag--${state.state}`"
    :aria-label="state.state === 'over' ? '已超时未处理' : undefined"
  >
    <van-icon name="clock-o" aria-hidden="true" />
    <span class="sla-tag__label">{{ state.text }}</span>
  </span>
  <span v-if="announced" class="visually-hidden" role="status">工单已超时未处理</span>
</template>

<style scoped>
.sla-tag,
.sla-text {
  display: inline-flex;

  /* 3px 不在 4px 网格上：改成 4/8 会动图标与文字的间距，本轮保留原值（P2-7 已登记） */
  gap: 3px;
  align-items: center;
  font-size: var(--fs-micro);
  line-height: 1;
  white-space: nowrap;
}

/* 正常：无底无框，仅作时间提示 */
.sla-text {
  color: var(--text-2);
}

.sla-tag {
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  border-radius: var(--r-full);
}

/* 临近：浅底警告（#FFFBE6 / #B45309，4.71:1） */
.sla-tag--warning {
  color: var(--color-warning);
  background: var(--color-warning-surface);
}

/* 超时：实心危险（#CF1322 底 + 白字，5.57:1），与高优先级同为唯一实心态 */
.sla-tag--over {
  color: var(--text-on-dark);
  background: var(--color-danger);
}
</style>
