<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 驳回流程弹窗（C4 Molecule，B10.5 / B10.6）
 * 两条硬约定写进组件而不是留给调用方：
 * 1. 驳回原因必填（2–100 字），空原因不给提交；
 * 2. 必须提示「驳回不回滚已产生的数据」——契约的驳回只改流程状态，不会自动作废已生成的工资单/结算单。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 当前待办步骤名：驳回默认回退到这一步
  currentStepName: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const reason = ref('')

// 每次打开重置，避免上一次的驳回原因残留到下一次（尤其是不同流程之间串味）
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
    ElMessage.warning('驳回原因须为 2-100 字')
    return
  }
  emit('submit', trimmed.value)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="驳回流程"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert
      class="reject-dialog__alert"
      type="warning"
      show-icon
      :closable="false"
      title="驳回不会撤销已产生的数据"
      description="若流程中已生成工资单、结算单或员工账号，需手动前往对应模块核对处理。"
    />

    <el-form label-width="88px" label-position="top">
      <!-- 契约的驳回接口只接收原因，不支持指定退回到更早步骤：控件置灰并写清原因，不留「可点但无效」的第三态 -->
      <el-form-item label="退回至">
        <el-select
          :model-value="currentStepName"
          disabled
          style="width: 100%"
          title="当前契约的驳回接口不支持指定退回步骤，默认退回到当前待办步骤"
        >
          <el-option :value="currentStepName" :label="currentStepName || '当前待办步骤'" />
        </el-select>
        <p class="reject-dialog__hint">暂不支持指定退回到更早步骤，驳回后默认回退到当前待办步骤。</p>
      </el-form-item>

      <el-form-item label="驳回原因" required>
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="请填写驳回原因（2-100 字），将记入流程步骤记录"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">再想想</el-button>
      <el-button type="danger" :loading="loading" :disabled="!canSubmit" @click="handleSubmit">确认驳回</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.reject-dialog {
  &__alert {
    margin-bottom: var(--sp-4);
  }

  &__hint {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
