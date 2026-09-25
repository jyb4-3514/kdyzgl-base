<script setup>
import { computed } from 'vue'
import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode'
import { ATTENDANCE_STATUS, CHECK_MODE, CHECK_TYPE, dictLabel } from '@kdyzgl/shared/constants/dict'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 打卡记录详情抽屉
 *
 * 为什么不需要详情接口：GET /attendance/records 的每一行已经带全判定字段
 * （wifiMatched / distance / locationMatched / checkMode），再发一次请求只是把同一份数据取回来。
 * 抽屉的职责因此收敛为「把判定过程讲清楚」：命中情况 → 坐标与距离 → 结论，
 * 让管理员不用去猜「为什么这条是异常卡」。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null },
  // 围栏半径来自当前驿站的规则（服务端不随记录返回），用于把距离讲成「在不在范围内」
  radius: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

/** 补卡来源：审批通过后由系统补录，没有 WiFi / 定位校验数据，直接讲明比逐项显示「未命中」更准确 */
const isMakeup = computed(() => !!props.record && props.record.source === 'MAKEUP')

/** 校验结果文案：补卡记录无校验数据时返回 '—'，避免把「没有这项数据」误读成「校验未通过」 */
function matchedText(value, yes, no) {
  if (value == null) return '—'
  return value ? yes : no
}

/** 判定结论：文案与错误码对齐 91xx 段，便于按提示排查（异常卡不计入出勤口径） */
const verdict = computed(() => {
  const row = props.record
  if (!row) return ''
  if (isMakeup.value) return '补卡审批通过后由系统补录，非设备打卡，无 WiFi / 定位校验数据'
  if (row.status === 'ABNORMAL') {
    const reason = row.wifiMatched
      ? `定位超出围栏（${ATTENDANCE_CODE.LOCATION_MISMATCH}）`
      : `WiFi 校验未通过（${ATTENDANCE_CODE.WIFI_MISMATCH}）`
    return `校验未通过：${reason}，该卡不计入实到与正常/迟到/早退`
  }
  if (row.status === 'LATE') return row.remark || '晚于上班时间打卡'
  if (row.status === 'EARLY_LEAVE') return row.remark || '早于下班时间打卡'
  return '校验通过，按所属时段判定为正常出勤'
})

const wifiText = computed(() => matchedText(props.record && props.record.wifiMatched, '命中', '未命中'))
const locationText = computed(() => matchedText(props.record && props.record.locationMatched, '在围栏内', '超出围栏'))

/** 「上午班 · 上班卡」：频次为 4 时同一天有两对卡，单看类型分不清是哪一段的 */
const periodLabel = computed(() => {
  const row = props.record
  if (!row) return '—'
  return `${row.periodName || '—'} · ${dictLabel(CHECK_TYPE, row.checkType)}`
})

const distanceText = computed(() => {
  const row = props.record
  if (!row) return '—'
  if (row.distance == null) return '—'
  const inFence = row.locationMatched ? '在围栏内' : '超出围栏'
  return props.radius == null ? `${row.distance} m` : `${row.distance} m（围栏半径 ${props.radius} m，${inFence}）`
})

const coordinateText = computed(() => {
  const row = props.record
  if (!row || row.longitude == null || row.latitude == null) return '—'
  return `${row.longitude}, ${row.latitude}`
})
</script>

<template>
  <el-drawer v-model="visible" title="打卡记录详情" :size="'min(var(--drawer-w), 92vw)'">
    <div v-if="record" class="record-detail">
      <!-- 结论先行：先给「这张卡算不算出勤」，再看判定过程 -->
      <div class="record-detail__verdict">
        <StatusTag :dict="ATTENDANCE_STATUS" :value="record.status" />
        <span v-if="isMakeup" class="record-detail__chip">补卡</span>
        <span class="record-detail__verdict-text">{{ verdict }}</span>
      </div>

      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="员工">{{ record.employeeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="时段 · 类型">{{ periodLabel }}</el-descriptions-item>
        <el-descriptions-item label="打卡日期">{{ record.workDate }}</el-descriptions-item>
        <el-descriptions-item label="打卡时间">{{ record.checkTime }}</el-descriptions-item>
        <el-descriptions-item label="记录来源">
          <span :class="{ 'is-makeup': isMakeup }">{{ isMakeup ? '补卡（审批通过后系统补录）' : '正常打卡' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="校验方式">{{ dictLabel(CHECK_MODE, record.checkMode) }}</el-descriptions-item>
        <el-descriptions-item label="WiFi 命中">
          <span :class="{ 'is-miss': !isMakeup && !record.wifiMatched }">{{ wifiText }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="命中 SSID">{{ record.wifiSsid || '—' }}</el-descriptions-item>
        <el-descriptions-item label="定位命中">
          <span :class="{ 'is-miss': !isMakeup && !record.locationMatched }">{{ locationText }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="与围栏中心距离">{{ distanceText }}</el-descriptions-item>
        <el-descriptions-item label="打卡坐标">{{ coordinateText }}</el-descriptions-item>
        <el-descriptions-item label="判定说明" :span="2">{{ record.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <StateBlock v-else variant="empty" title="未获取到打卡记录" />
  </el-drawer>
</template>

<style scoped lang="scss">
.record-detail {
  &__verdict {
    display: flex;
    align-items: flex-start;
    gap: var(--sp-2);
    margin-bottom: var(--sp-4);
  }

  &__verdict-text {
    font-size: var(--fs-body);
    line-height: var(--lh-body);
    color: var(--text-2);
  }

  // 补卡标记：文字通道，不依赖颜色单一表达
  &__chip {
    flex: none;
    padding: 2px var(--sp-2);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-xs);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
  }

  .is-miss {
    color: var(--color-danger);
  }

  .is-makeup {
    color: var(--state-warning-fg);
  }
}
</style>
