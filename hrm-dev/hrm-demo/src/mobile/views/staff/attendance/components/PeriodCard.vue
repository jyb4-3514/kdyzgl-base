<script setup>
import { computed } from 'vue'
import CheckResultPanel from './CheckResultPanel.vue'
import CheckSlotRow from './CheckSlotRow.vue'
import { periodDoneCount, slotKey } from '../model/attendanceUi.js'

/**
 * 时段卡（表头 + 进度 + N 个槽位 + 就近判定结果）
 *
 * 为什么判定结果必须渲染在本卡内：员工按完按钮不用滚动回顶部找结论，也不会与相邻时段的结论串台。
 * 为什么窗口文案由壳传入：时间窗以接口下发的 windowStart / windowEnd 为准（与服务端判定同一口径），
 * 组件不重推余量，避免「页面说能打、服务端说超窗」两套口径。
 */
const props = defineProps({
  /** `/attendance/status` 的单个时段（含 cells：[{ checkType, state }]） */
  period: { type: Object, required: true },
  /** 该时段可打卡窗口文案，如「07:30 - 13:00」 */
  windowText: { type: String, default: '-' },
  /** 正在提交的槽位键：只给被点的那一格 loading，其余格子只禁用 */
  submittingKey: { type: String, default: '' },
  /** 任一槽位提交中：全部按钮禁用，防连点重复落卡 */
  submitting: { type: Boolean, default: false },
  /** 命中本时段的判定结果；null 表示本时段没有结果，不渲染结果区 */
  result: { type: Object, default: null }
})

const emit = defineEmits(['check', 'makeup'])

const doneCount = computed(() => periodDoneCount(props.period))
</script>

<template>
  <div class="card period-card">
    <div class="period-card__head">
      <span class="period-card__name">{{ period.name }}</span>
      <span class="period-card__progress tabular-nums">{{ doneCount }}/2 已完成</span>
    </div>
    <p class="period-card__meta tabular-nums">
      {{ period.startTime }} - {{ period.endTime }} · 可打卡 {{ windowText }}
    </p>

    <CheckSlotRow
      v-for="cell in period.cells"
      :key="cell.checkType"
      :period-name="period.name"
      :check-type="cell.checkType"
      :state="cell.state"
      :submitting="submittingKey === slotKey(period.periodIndex, cell.checkType)"
      :disabled="submitting"
      @check="emit('check', cell.checkType)"
      @makeup="emit('makeup', cell.checkType)"
    />

    <CheckResultPanel v-if="result" :result="result" />
  </div>
</template>

<style scoped>
.period-card {
  margin-top: var(--sp-3);
}

.period-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.period-card__name {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.period-card__progress {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.period-card__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  word-break: break-all;
}

/* 首个槽位与时段说明拉开一档；后续槽位靠自身 border-top 分隔，不再加间距（避免双层间隔） */
:deep(.period-card__meta + .check-item) {
  margin-top: var(--sp-2);
}
</style>
