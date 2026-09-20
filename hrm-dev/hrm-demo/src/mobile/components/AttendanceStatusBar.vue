<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { attendanceProgress } from '../utils/attendance.js'
import { useCheckIn } from '../composables/useCheckIn.js'

/**
 * 今日出勤状态条 + 一键打卡（D2-3，Organism，仅员工端首页）
 *
 * 为什么自绘而不是复用 van-notice-bar：状态条要同时承载「状态文案 + 可点的打卡按钮」，
 * notice-bar 只能整条点击，无法表达「当前该打上班卡还是下班卡」这一主操作。
 *
 * 状态（7 态）：
 *   默认 = 有规则且有时段，按 periods 判定当前应打槽位；加载 = 文案 `···` + 按钮禁用；
 *   空   = rule 为空（未配置规则）；错误 = 状态取数失败，给「重试」且不阻塞首页其余内容；
 *   禁用 = 无可打卡槽位（今日打完 / 时间窗未开）→ 主操作降级为「查看打卡详情」，不留灰按钮；
 *   无权限 = 老板端不渲染本组件（由首页按角色决定）；边界 = 支持每日 2 段（4 张卡）。
 */
const props = defineProps({
  /** `/attendance/status` 的返回；null 且非 error 表示首次加载中 */
  status: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['refresh'])

const router = useRouter()
const { submitting, result, submit } = useCheckIn()

const rule = computed(() => (props.status ? props.status.rule : null))
const periods = computed(() => (props.status && props.status.periods) || [])
const progress = computed(() => attendanceProgress(props.status))

/** 当前应打槽位：按「先补上班卡、再补下班卡」逐段推进，第一段未打满就还轮不到下一段 */
const target = computed(() => {
  for (const period of periods.value) {
    if (!period.onChecked) return { period, checkType: 'ON' }
    if (!period.offChecked) return { period, checkType: 'OFF' }
  }
  return null
})

const shiftText = computed(() => {
  const shift = props.status && props.status.shift
  if (!shift) return '今日未排班'
  return `${shift.shiftName} ${shift.startTime}-${shift.endTime}`
})

/** 出勤状态文案：错误态独立成一句，避免与「未打卡」混淆（未打卡是业务状态，取数失败不是） */
const stateText = computed(() => {
  if (props.error) return '出勤状态获取失败'
  if (!props.status) return '···'
  if (!rule.value) return '该驿站尚未配置打卡规则'
  if (!periods.value.length) return '规则未配置打卡时段'
  if (!target.value) return '今日打卡已完成'
  return target.value.checkType === 'ON' ? '尚未打上班卡' : '尚未打下班卡'
})

const pending = computed(() => !!target.value)
const tone = computed(() => {
  if (props.error) return 'danger'
  if (!props.status || !rule.value || !periods.value.length) return 'muted'
  return pending.value ? 'warning' : 'success'
})

/** 主按钮的三类语义：打卡（primary）/ 详情或重试（secondary）；无可打槽位时不留一个点不动的灰按钮 */
const action = computed(() => {
  if (props.error) return { kind: 'refresh', text: '重试' }
  if (!props.status || !rule.value || !periods.value.length || !target.value)
    return { kind: 'detail', text: '查看打卡详情' }
  return { kind: 'punch', text: target.value.checkType === 'ON' ? '上班打卡' : '下班打卡' }
})

const buttonLabel = computed(() => {
  if (action.value.kind !== 'punch') return action.value.text
  return `${action.value.text}，${shiftText.value}`
})

async function onAction() {
  if (submitting.value) return
  if (action.value.kind === 'refresh') {
    emit('refresh')
    return
  }
  if (action.value.kind === 'detail') {
    router.push('/staff/attendance')
    return
  }
  const { period, checkType } = target.value
  const res = await submit(period, checkType, rule.value)
  // 成功与校验失败都会改变今日状态（失败还会落异常卡留痕），回读一次保证与服务端一致
  if (res) emit('refresh')
}
</script>

<template>
  <section class="att-bar" :class="`att-bar--${tone}`">
    <div class="att-bar__body">
      <p class="att-bar__state" role="status">
        <van-icon
          :name="tone === 'success' ? 'passed' : tone === 'danger' ? 'warning-o' : 'clock-o'"
          aria-hidden="true"
        />
        <span>{{ stateText }}</span>
      </p>
      <p v-if="status && rule && periods.length" class="att-bar__meta">
        已完成 <span class="tabular-nums">{{ progress.done }}/{{ progress.total }}</span> · {{ shiftText }}
      </p>
      <p v-else-if="!error && status && !rule" class="att-bar__meta">请联系站长或管理员在「打卡规则」中完成配置</p>
    </div>
    <van-button
      class="att-bar__btn"
      :type="action.kind === 'punch' ? 'primary' : 'default'"
      :plain="action.kind !== 'punch'"
      :loading="submitting"
      :disabled="submitting || (!status && !error)"
      :aria-label="buttonLabel"
      @click="onAction"
    >
      {{ action.text }}
    </van-button>

    <!-- 打卡判定结果就近反馈：失败要说清「卡在哪、下一步去哪」，不放任一个 Toast 飘过 -->
    <p v-if="result && !result.ok" class="att-bar__result" role="alert">
      {{ result.hint }}
      <button v-if="result.demoGuide" type="button" class="att-bar__link" @click="router.push('/staff/attendance')">
        去打卡页开启演示辅助
      </button>
    </p>
  </section>
</template>

<style scoped>
.att-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  align-items: center;
  padding: var(--sp-3) var(--sp-4);
  margin-top: var(--sp-3);
  background: var(--surface-card);
  border-radius: var(--r-lg);
  box-shadow: var(--e1);
  border-left: 3px solid var(--border-line);
}

.att-bar--warning {
  border-left-color: var(--color-warning);
}

.att-bar--success {
  border-left-color: var(--color-success);
}

.att-bar--danger {
  border-left-color: var(--color-danger);
}

.att-bar__body {
  flex: 1;
  min-width: 0;
}

.att-bar__state {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-body);
  color: var(--text-1);
}

.att-bar--warning .att-bar__state {
  color: var(--color-warning);
}

.att-bar--success .att-bar__state {
  color: var(--color-success);
}

.att-bar--danger .att-bar__state {
  color: var(--color-danger);
}

.att-bar__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.att-bar__btn {
  flex: none;
  min-width: 104px;
  min-height: 44px;
}

.att-bar__result {
  width: 100%;
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.att-bar__link {
  min-height: 44px;
  padding: 0 var(--sp-1);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}
</style>
