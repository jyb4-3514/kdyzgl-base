<script setup>
import { computed, ref, watch } from 'vue'
import { HALF_DAY, LEAVE_LOG_ACTION, LEAVE_STATUS, LEAVE_TYPE, dictLabel } from '@/shared/constants/dict'
import { getLeave } from '../../../api/leave.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import { daysText, rangeText, rejectStageText, typeText } from '../utils/leave.js'

/**
 * 请假详情抽屉：单据全字段 + 审批链 + handleLog 操作留痕时间线
 *
 * 为什么详情单独拉一次接口而不是复用列表行：列表行没有 handleLog，也没有 canEdit / canRevoke 派生标志；
 * 且时间线是审计证据，必须拿服务端的最新留痕，不能拿列表的旧快照糊过去。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  leaveId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const error = ref(false)
const detail = ref(null)

/** 留痕按时间升序读：审计要看的是「先提交、后初审、再终审」这条链 */
const logs = computed(() => {
  if (!detail.value || !Array.isArray(detail.value.handleLog)) return []
  return detail.value.handleLog.slice().sort((a, b) => (a.time === b.time ? a.id - b.id : a.time < b.time ? -1 : 1))
})

const snapshot = computed(() => detail.value && detail.value.countedDaysSnapshot)

async function fetchDetail() {
  if (!props.leaveId) return
  loading.value = true
  error.value = false
  try {
    detail.value = await getLeave(props.leaveId)
  } catch (e) {
    detail.value = null
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.leaveId],
  ([visible]) => {
    if (visible) fetchDetail()
  }
)

/** 留痕里的前后值（仅 UPDATE 有）：两串放一行对比，避免时间线被撑成两屏 */
const snapText = (snap) => {
  if (!snap) return '—'
  return `${dictLabel(LEAVE_TYPE, snap.leaveType)} ${snap.startDate} ${dictLabel(HALF_DAY, snap.startPeriod)} ~ ${
    snap.endDate
  } ${dictLabel(HALF_DAY, snap.endPeriod)}；事由：${snap.reason}`
}

const transitionText = (log) => {
  if (!log.fromStatus && !log.toStatus) return ''
  if (!log.fromStatus) return dictLabel(LEAVE_STATUS, log.toStatus)
  if (log.fromStatus === log.toStatus) return dictLabel(LEAVE_STATUS, log.fromStatus)
  return `${dictLabel(LEAVE_STATUS, log.fromStatus)} → ${dictLabel(LEAVE_STATUS, log.toStatus)}`
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="请假详情"
    :size="520"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="leave-detail">
      <StateBlock v-if="error" variant="error" title="请假详情加载失败" @action="fetchDetail" />

      <template v-else-if="detail">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="申请人">{{ detail.employeeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="驿站">{{ detail.stationName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="假别">{{ typeText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag
              :dict="LEAVE_STATUS"
              :value="detail.status"
              :variant="(LEAVE_STATUS[detail.status] || {}).variant || 'soft'"
            />
            <span v-if="rejectStageText(detail)" class="leave-detail__sub">{{ rejectStageText(detail) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="请假时间" :span="2">{{ rangeText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="天数" :span="2">{{ daysText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="请假事由" :span="2">{{ detail.reason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ detail.applyTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ detail.updateTime || '—' }}</el-descriptions-item>
          <!-- 重提单才有原单，留个引用方便顺着审计链往回查 -->
          <el-descriptions-item v-if="detail.originId" label="原单编号" :span="2">
            #{{ detail.originId }}（驳回后重新提交生成的新单）
          </el-descriptions-item>
          <!-- 计薪天数快照：终审通过时落库，排班事后变更不回改，是工资单口径的核对依据 -->
          <el-descriptions-item v-if="snapshot" label="终审快照" :span="2">
            自然 {{ snapshot.naturalDays }} 天 · 计薪 {{ snapshot.countedDays }} 天 · 排班
            {{ snapshot.scheduleDigest || '（按自然日计，与排班无关）' }}
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="leave-detail__title">审批信息</h3>
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="初审人">{{ detail.stationApproverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="初审时间">{{ detail.stationApproveTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="初审意见" :span="2">
            {{ detail.stationApproveRemark || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="终审人">{{ detail.approverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="终审时间">{{ detail.approveTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="终审意见" :span="2">{{ detail.approveRemark || '—' }}</el-descriptions-item>
          <template v-if="detail.revokeTime">
            <el-descriptions-item label="撤回人">{{ detail.revokerName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="撤回时间">{{ detail.revokeTime }}</el-descriptions-item>
            <el-descriptions-item label="撤回原因" :span="2">{{ detail.revokeReason || '—' }}</el-descriptions-item>
          </template>
          <template v-if="detail.cancelTime">
            <el-descriptions-item label="撤销时间" :span="2"
              >{{ detail.cancelTime }}（申请人自行撤销）</el-descriptions-item
            >
          </template>
        </el-descriptions>

        <h3 class="leave-detail__title">操作留痕</h3>
        <el-timeline class="leave-detail__timeline">
          <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="log.time" placement="top">
            <div class="leave-log">
              <p class="leave-log__head">
                <span class="leave-log__action">{{ dictLabel(LEAVE_LOG_ACTION, log.action) }}</span>
                <span v-if="log.operatorName" class="leave-log__who">
                  {{ log.operatorName }}（{{ log.operatorRole }}）
                </span>
              </p>
              <p v-if="transitionText(log)" class="leave-log__meta">状态：{{ transitionText(log) }}</p>
              <p v-if="log.action === 'UPDATE'" class="leave-log__meta">
                修改前：{{ snapText(log.before) }}<br />
                修改后：{{ snapText(log.after) }}
              </p>
              <p v-if="log.remark" class="leave-log__remark">说明：{{ log.remark }}</p>
            </div>
          </el-timeline-item>
        </el-timeline>
        <StateBlock v-if="!logs.length" variant="empty" title="暂无操作留痕" />
      </template>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.leave-detail {
  min-height: 120px;

  &__sub {
    margin-left: var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-2);
  }

  &__title {
    margin: var(--sp-5) 0 var(--sp-3);
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__timeline {
    padding-left: var(--sp-1);
  }
}

.leave-log {
  &__head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--sp-2);
    margin: 0;
  }

  &__action {
    font-size: var(--fs-body);
    font-weight: var(--fw-medium);
    color: var(--text-1);
  }

  &__who {
    font-size: var(--fs-caption);
    color: var(--text-2);
  }

  /* 说明文字用 --text-2：--text-3 在浅底上只有 4.23:1，不达 AA（设计规范 §5.1 的对比度禁区） */
  &__meta,
  &__remark {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-2);
  }
}
</style>
