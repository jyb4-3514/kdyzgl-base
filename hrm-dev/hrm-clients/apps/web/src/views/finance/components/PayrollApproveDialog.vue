<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 审核工资单弹窗（通过 / 驳回共用一份表单）
 *
 * 表单状态留在本组件：页面只收提交载荷（approved + approveRemark），校验规则也贴在输入旁，
 * 避免「页面一份表单、弹窗一份表单」。驳回必须填意见（与后端校验同口径 2-200 字）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const form = ref({ approved: true, approveRemark: '' })

// 每次打开重置：改前在 openApprove 里重置，语义一致（不残留上一次的结论与意见）
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) form.value = { approved: true, approveRemark: '' }
  }
)

function handleSubmit() {
  const remark = String(form.value.approveRemark || '').trim()
  if (!form.value.approved && remark.length < 2) {
    ElMessage.warning('驳回须填写审核意见（2-200 字）')
    return
  }
  emit('submit', { approved: form.value.approved, approveRemark: form.value.approveRemark })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="审核工资单"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="审核结论">
        <el-radio-group v-model="form.approved">
          <el-radio :value="true">审核通过</el-radio>
          <el-radio :value="false">驳回退回草稿</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="form.approved ? '审核意见（选填）' : '驳回意见（必填）'">
        <el-input v-model="form.approveRemark" type="textarea" :rows="3" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ form.approved ? '确认通过' : '确认驳回' }}
      </el-button>
    </template>
  </el-dialog>
</template>
