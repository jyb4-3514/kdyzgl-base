<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { daysText, rangeText, typeText } from '../utils/leave.js'

/**
 * 撤回已批准请假弹窗（仅 ADMIN）
 *
 * 为什么原因必填：撤回等于撤销一次已经生效的公司决定（会回滚考勤标记与算薪口径），
 * 必须留因，事后才能解释「这条已批的假为什么又没了」（设计规范 §1.4）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const reason = ref('')

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) reason.value = ''
  }
)

const trimmed = computed(() => reason.value.trim())
const canSubmit = computed(() => trimmed.value.length >= 2 && trimmed.value.length <= 100)

function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning('撤回原因须为 2-100 字')
    return
  }
  emit('submit', trimmed.value)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="撤回已批准的请假"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="row">
      <el-alert
        class="revoke-dialog__alert"
        type="warning"
        show-icon
        :closable="false"
        title="撤回会回滚考勤与算薪口径"
        description="该单的已批请假天数将不再计入，缺勤天数随之恢复；若所属账期已生成非草稿工资单，撤回会被拒绝（9606）。"
      />

      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="申请人">{{ row.employeeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="驿站">{{ row.stationName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="假别">{{ typeText(row) }}</el-descriptions-item>
        <el-descriptions-item label="天数">{{ daysText(row) }}</el-descriptions-item>
        <el-descriptions-item label="请假时间" :span="2">{{ rangeText(row) }}</el-descriptions-item>
        <el-descriptions-item label="终审人">{{ row.approverName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="终审时间">{{ row.approveTime || '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" class="revoke-dialog__form">
        <el-form-item label="撤回原因" required>
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="必填，请说明撤回原因（2-100 字），将写入操作留痕并通知申请人"
            aria-label="撤回原因"
          />
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="danger" :loading="loading" :disabled="!canSubmit" @click="handleSubmit">确认撤回</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.revoke-dialog {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__form {
    margin-top: var(--sp-4);
  }
}
</style>
