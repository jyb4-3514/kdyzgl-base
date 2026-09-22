<script setup>
import { ROLE_LABEL } from '@/shared/constants/role'

/**
 * 工单指派弹窗
 *
 * 处理人下拉取本站启用员工；名册由父层加载后注入，弹窗只做选择与回抛，
 * 不自己取数（名册加载失败时父层已按静默降级处理）。
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  orderNo: { type: String, default: '' },
  assignees: { type: Array, default: () => [] },
  assigneeId: { type: [Number, String], default: undefined },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'update:assigneeId', 'submit'])
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="指派处理人"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="工单号">{{ orderNo }}</el-form-item>
      <el-form-item label="处理人">
        <el-select
          :model-value="assigneeId"
          filterable
          placeholder="请选择本站员工"
          style="width: 100%"
          @update:model-value="emit('update:assigneeId', $event)"
        >
          <el-option
            v-for="item in assignees"
            :key="item.id"
            :label="`${item.realName}（${ROLE_LABEL[item.role] || item.role}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="emit('submit')">确定</el-button>
    </template>
  </el-dialog>
</template>
