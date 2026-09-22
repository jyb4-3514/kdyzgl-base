<script setup>
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import { RULE_STATUS } from '../model/financeMeta.js'

/**
 * 计薪规则面板（Tab 2）
 *
 * 规则的可编辑细节在 PayrollRuleEditor 抽屉里，本面板只做列表展示与动作上报：
 * 启停/删除的语义（已生成单据不受影响、被引用只能停用）由容器侧的确认文案承担。
 */
defineProps({
  rules: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['refresh', 'create', 'edit', 'toggle', 'remove'])
</script>

<template>
  <el-card shadow="never" class="content-card">
    <p class="finance-page__note">
      算薪不写死规则：金额由「工资项目」计算得出，项目只声明方向 + 数据来源 + 计算参数。 改一项津贴、调一次扣款标准都只是改配置，不影响已生成的工资单（历史单据保留当时的规则快照）。
    </p>

    <StateBlock v-if="error" variant="error" title="计薪规则加载失败" @action="emit('refresh')" />
    <StateBlock
      v-else-if="!loading && !rules.length"
      variant="empty"
      title="暂无计薪规则"
      description="先建一条规则并启用，才能生成工资单草稿"
      action-text="新建规则"
      @action="emit('create')"
    />
    <el-table v-else v-loading="loading" :data="rules" row-key="id">
      <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <StatusTag :dict="RULE_STATUS" :value="String(row.status)" :variant="row.status === 1 ? 'soft' : 'outline'" />
        </template>
      </el-table-column>
      <el-table-column label="工资项目" width="130">
        <template #default="{ row }">启用 {{ row.enabledItemCount }} / 共 {{ row.itemCount }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="164" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="emit('edit', row)">编辑</el-button>
          <el-button link type="primary" @click="emit('toggle', row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          <el-button link type="danger" @click="emit('remove', row)">删除</el-button>
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
