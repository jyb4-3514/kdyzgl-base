<script setup>
import { reactive, ref, watch } from 'vue'

/**
 * 工单转单弹窗（只改处理人、不改状态）
 *
 * 为什么校验放在弹窗内：转单对象与理由的规则只服务这一个入口，
 * 校验通过才回抛 submit，父层拿到已裁剪的 payload 直接做二次确认与落库，不必回读弹窗内部状态。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderNo: { type: String, default: '' },
  fromName: { type: String, default: '' },
  candidates: { type: Array, default: () => [] },
  optionsLoading: { type: Boolean, default: false },
  scopeHint: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref(null)
const form = reactive({ toEmployeeId: undefined, reason: '' })

/** 转单理由与后端校验同口径（2-100 字），前端先拦一道，避免明知会被拒仍发请求 */
const RULES = {
  toEmployeeId: [{ required: true, message: '请选择转单对象', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写转单理由', trigger: 'blur' },
    { min: 2, max: 100, message: '转单理由长度须为 2-100 字', trigger: 'blur' }
  ]
}

// 每次打开清空上一次的输入：避免误把上一单的对象与理由带到这一单
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.toEmployeeId = undefined
    form.reason = ''
  }
)

async function submit() {
  const valid = await (formRef.value ? formRef.value.validate().catch(() => false) : true)
  if (!valid) return
  emit('submit', { toEmployeeId: form.toEmployeeId, reason: form.reason.trim() })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="工单转单"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="RULES" label-width="90px">
      <el-form-item label="工单号">{{ orderNo }}</el-form-item>
      <el-form-item label="当前处理人">{{ fromName || '未指派' }}</el-form-item>
      <el-form-item label="转单对象" prop="toEmployeeId">
        <el-select
          v-model="form.toEmployeeId"
          filterable
          :loading="optionsLoading"
          :placeholder="scopeHint"
          class="transfer-select"
        >
          <el-option v-for="item in candidates" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="转单理由" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="请说明转单原因（2-100 字），将随留痕写入时间线"
        />
      </el-form-item>
    </el-form>
    <p class="transfer-tip">转单只变更处理人，工单状态与 SLA 截止时间不变；提交前会再次确认。</p>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">提交转单</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.transfer-select {
  width: 100%;
}

.transfer-tip {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
