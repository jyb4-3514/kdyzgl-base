<script setup>
import { PAYROLL_STATUS } from '@/shared/constants/dict.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 异议处理面板（Tab 3）
 *
 * 契约没有独立的异议列表接口，本表取「待审核」状态的单据后筛选有异议原因的行（口径见 composable）；
 * 点「处理」复用工资单详情抽屉，不另造一套异议详情。
 */
defineProps({
  objections: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['refresh', 'handle'])
</script>

<template>
  <el-card shadow="never" class="content-card">
    <p class="finance-page__note">
      员工提异议后单据会退回「待审核」，由管理员重新核定后再发布。契约没有独立的异议列表接口， 本表取「待审核」状态的单据后筛选有异议原因的行。
    </p>
    <StateBlock v-if="error" variant="error" title="异议列表加载失败" @action="emit('refresh')" />
    <StateBlock
      v-else-if="!loading && !objections.length"
      variant="empty"
      title="当前没有待处理的异议"
      description="员工在移动端提出异议后会出现在这里"
    />
    <el-table v-else v-loading="loading" :data="objections" row-key="id">
      <el-table-column prop="employeeName" label="员工" min-width="100" show-overflow-tooltip />
      <el-table-column prop="month" label="月份" width="92" />
      <el-table-column prop="objectionReason" label="异议原因" min-width="220" show-overflow-tooltip />
      <el-table-column prop="objectionTime" label="提出时间" width="164" />
      <el-table-column label="状态" width="104">
        <template #default="{ row }">
          <StatusTag :dict="PAYROLL_STATUS" :value="row.status" :variant="PAYROLL_STATUS[row.status].variant" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('handle', row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped lang="scss">
.content-card {
  margin-bottom: 0;
}

.finance-page__note {
  margin: 0 0 var(--sp-4);
  padding: var(--sp-3);
  border-left: 3px solid var(--color-primary-border);
  border-radius: var(--r-xs);
  background-color: var(--color-primary-surface);
  color: var(--text-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
}
</style>
