<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { HALF_DAY, LEAVE_STATUS, dictLabel } from '@kdyzgl/shared/constants/dict'
import { daysText, rangeText, typeText } from '../utils/leave.js'

/**
 * 请假审批弹窗（初审 / 终审同一套，按角色与单据状态切换动作）
 *
 * 为什么两级共用一套：两级审批的动作集完全相同（通过 / 驳回 + 意见），差别只有端点与标题；
 * 各写一套会让「驳回原因必填 2-100 字」这条硬规则出现两份实现。
 *
 * 与补卡的刻意差异：补卡驳回意见选填，请假驳回原因必填 —— 请假被驳回当天即为缺勤（可能直接扣款），
 * 必须给员工一个可追溯的理由（设计规范 §4.7）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: null },
  mode: { type: String, default: 'approve' }, // approve | reject
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const remark = ref('')

// 每次打开重置，避免上一单的驳回原因串到下一单
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) remark.value = ''
  }
)

const isReject = computed(() => props.mode === 'reject')
const stageLabel = computed(() => (props.row && props.row.status === 'PENDING_STATION' ? '站长初审' : '管理员终审'))
const title = computed(() => `${stageLabel.value} · ${isReject.value ? '驳回请假申请' : '通过请假申请'}`)
const statusLabel = computed(() => dictLabel(LEAVE_STATUS, props.row && props.row.status))
const periodText = computed(() => {
  if (!props.row) return '—'
  const start = dictLabel(HALF_DAY, props.row.startPeriod, '')
  const end = dictLabel(HALF_DAY, props.row.endPeriod, '')
  return `${start} ~ ${end}`
})

const trimmed = computed(() => remark.value.trim())
/** 驳回必填 2-100 字；通过选填但同样上限 100 字（与 Mock 的 approveRemarkError 同口径） */
const canSubmit = computed(() =>
  isReject.value ? trimmed.value.length >= 2 && trimmed.value.length <= 100 : trimmed.value.length <= 100
)

function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning('驳回原因须为 2-100 字')
    return
  }
  emit('submit', trimmed.value)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="row">
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="申请人">{{ row.employeeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="驿站">{{ row.stationName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="假别">{{ typeText(row) }}</el-descriptions-item>
        <el-descriptions-item label="午别">{{ periodText }}</el-descriptions-item>
        <el-descriptions-item label="请假时间" :span="2">{{ rangeText(row) }}</el-descriptions-item>
        <el-descriptions-item label="天数" :span="2">{{ daysText(row) }}</el-descriptions-item>
        <el-descriptions-item label="事由" :span="2">{{ row.reason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          {{ statusLabel }}
        </el-descriptions-item>
      </el-descriptions>

      <p v-if="isReject" class="approval-dialog__tip approval-dialog__tip--warn" role="alert">
        驳回原因必填（2-100 字），申请人可见；驳回后当天即为缺勤，可能直接影响当月工资。
      </p>
      <p v-else class="approval-dialog__tip">
        {{
          row.status === 'PENDING_STATION'
            ? '通过后进入管理员终审，站长初审意见会保留在操作留痕中。'
            : '终审通过后该单生效：写入请假考勤标记并参与当月算薪。'
        }}
      </p>

      <el-form label-position="top">
        <el-form-item :label="isReject ? '驳回原因' : '审批意见（选填）'" :required="isReject">
          <el-input
            v-model="remark"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            :placeholder="isReject ? '必填，请说明驳回原因（2-100 字）' : '选填，例如：情况属实，准假'"
            aria-label="审批意见"
          />
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="isReject" type="danger" :loading="loading" :disabled="!canSubmit" @click="handleSubmit">
        确认驳回
      </el-button>
      <el-button v-else type="primary" :loading="loading" @click="handleSubmit">确认通过</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.approval-dialog {
  &__tip {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-2);

    /* 驳回不可逆且影响工资：用警告语义而不是普通灰字 */
    &--warn {
      padding: var(--sp-2) var(--sp-3);
      border: 1px solid var(--state-warning-border);
      border-radius: var(--r-sm);
      background-color: var(--state-warning-bg);
      color: var(--state-warning-fg);
    }
  }
}
</style>
