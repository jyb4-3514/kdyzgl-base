<script setup>
import { computed } from 'vue'
import StatusTag from '@/mobile/components/StatusTag.vue'
import { ATTENDANCE_STATUS, CHECK_MODE, dictLabel } from '@/shared/constants/dict.js'
import { ATTENDANCE_CODE } from '@/shared/constants/errorCode.js'
import { distanceText, periodLabel } from '@/mobile/utils/attendance.js'

/**
 * 打卡判定结果（就近渲染在所属时段卡内）
 *
 * role 按结果切换 status / alert：成功是状态播报，失败要立刻被读屏读到（沿用原页取舍）。
 * 校验未通过的尝试会在服务端落一条异常卡留痕，页面必须说清「界面说没打、记录里有卡」的困惑；
 * 但只有 WiFi / 定位类失败会落卡，超窗、重复卡不能一律套用这句，否则又是一处误导。
 */
const props = defineProps({
  /** { ok, periodName, checkType, status, checkTime, checkMode, distance, remark, hint, code } */
  result: { type: Object, required: true }
})

const abnormalLogged = computed(
  () =>
    !props.result.ok &&
    (props.result.code === ATTENDANCE_CODE.WIFI_MISMATCH || props.result.code === ATTENDANCE_CODE.LOCATION_MISMATCH)
)

const title = computed(
  () =>
    `${props.result.ok ? '打卡成功' : '打卡未通过'} · ${periodLabel(props.result.periodName, props.result.checkType)}`
)
</script>

<template>
  <div class="result" :class="result.ok ? 'result--ok' : 'result--fail'" :role="result.ok ? 'status' : 'alert'">
    <div class="result__head">
      <van-icon :name="result.ok ? 'passed' : 'warning-o'" aria-hidden="true" />
      <span class="result__title">{{ title }}</span>
      <StatusTag v-if="result.ok" :dict="ATTENDANCE_STATUS" :value="result.status" />
    </div>
    <p v-if="result.ok" class="result__meta tabular-nums">
      {{ result.checkTime }} · 命中 {{ dictLabel(CHECK_MODE, result.checkMode) }} · 距围栏
      {{ distanceText(result.distance) }}
      <template v-if="result.remark"> · {{ result.remark }}</template>
    </p>
    <p v-else class="result__meta">{{ result.hint }}</p>
    <p v-if="abnormalLogged" class="result__foot">本次尝试已在服务端记录为异常卡（不计入出勤），修正后可重新打卡</p>
  </div>
</template>

<style scoped>
/* 左侧色条与班次条同栅格（--shift-bar-w = 4px），原先的 3px 与班次条不成栏 */
.result {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
  border-left: var(--shift-bar-w) solid var(--border-line);
}

.result--ok {
  border-left-color: var(--color-success);
}

.result--fail {
  border-left-color: var(--color-danger);
}

.result__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.result--ok .result__head {
  color: var(--color-success);
}

.result--fail .result__head {
  color: var(--color-danger);
}

.result__title {
  flex: 1;
  min-width: 0;
}

.result__meta {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.result__foot {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
