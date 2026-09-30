<template>
  <div class="finance-page">
    <PageHeader title="财务管理" :sub="page.headerSub" :loading="page.loading">
      <template #actions>
        <el-button v-if="page.activeTab === 'payroll'" type="primary" :icon="Plus" @click="page.generateVisible = true"
          >生成工资单</el-button
        >
        <el-button v-else-if="page.activeTab === 'rule'" type="primary" :icon="Plus" @click="page.openRule(null)"
          >新建规则</el-button
        >
        <el-button :icon="Refresh" @click="onRefresh">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="page.activeTab" class="finance-tabs" @tab-change="page.handleTabChange">
      <el-tab-pane name="payroll" label="工资单" />
      <el-tab-pane name="rule" label="计算规则" />
      <el-tab-pane name="objection" label="异议处理" />
      <el-tab-pane name="adjustment" label="手工调整对账" />
      <el-tab-pane name="setting" label="算薪日设置" />
    </el-tabs>

    <PayrollTabPanel
      v-if="page.activeTab === 'payroll'"
      :model-value="page.filters"
      :stations="page.stations"
      :list="page.list"
      :total="page.total"
      :counts="page.counts"
      :loading="page.listLoading"
      :error="page.listError"
      :page-num="page.query.pageNum"
      :page-size="page.query.pageSize"
      :pending-submit-count="page.pendingSubmitCount"
      :approved-count="page.approvedCount"
      @update:model-value="page.applyFilters"
      @search="page.resetPage"
      @filter-status="page.filterByStatus"
      @batch-submit="page.handleBatchSubmit"
      @batch-publish="page.handleBatchPublish"
      @refresh="page.fetchList"
      @generate="page.generateVisible = true"
      @open="page.openDetail"
      @action="page.handleRowAction"
      @page-change="page.handlePageChange"
      @size-change="page.handleSizeChange"
    />

    <PayrollRulesPanel
      v-else-if="page.activeTab === 'rule'"
      :rules="page.rules"
      :loading="page.ruleLoading"
      :error="page.ruleError"
      @refresh="page.loadRules"
      @create="page.openRule(null)"
      @edit="page.openRule"
      @toggle="page.handleToggleRule"
      @remove="page.handleDeleteRule"
    />

    <PayrollObjectionsPanel
      v-else-if="page.activeTab === 'objection'"
      :objections="page.objections"
      :loading="page.objectionLoading"
      :error="page.objectionError"
      @refresh="page.loadObjections"
      @handle="page.openDetail"
    />

    <ManualAdjustmentPanel v-else-if="page.activeTab === 'adjustment'" ref="adjustPanelRef" :stations="page.stations" />
    <PayrollSettingsPanel v-else ref="settingsPanelRef" />

    <GeneratePayrollDialog
      v-model="page.generateVisible"
      :stations="page.stations"
      :departments="page.departmentOptions"
      @generated="page.afterGenerate"
    />
    <PayrollRuleEditor v-model="page.ruleVisible" :rule-id="page.editingRuleId" @saved="page.reloadAll" />
    <PayrollDetailDrawer v-model="page.detailVisible" :payroll-id="page.detailId" :can-write="true" @action="page.handleRowAction" />
    <PayrollApproveDialog v-model="page.approveVisible" :loading="page.approving" @submit="page.submitApprove" />
  </div>
</template>

<script setup>
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import PayrollTabPanel from './components/PayrollTabPanel.vue'
import PayrollRulesPanel from './components/PayrollRulesPanel.vue'
import PayrollObjectionsPanel from './components/PayrollObjectionsPanel.vue'
import PayrollApproveDialog from './components/PayrollApproveDialog.vue'
import GeneratePayrollDialog from './components/GeneratePayrollDialog.vue'
import PayrollDetailDrawer from './components/PayrollDetailDrawer.vue'
import { useFinancePage } from './composables/useFinancePage.js'

/**
 * 计薪规则编辑器是财务页最重的抽屉、且只在「计算规则」Tab 点编辑才可见，
 * 异步拆 chunk 后不阻塞工资单列表首屏。保持常驻渲染（不加 v-if），DOM 结构不变。
 */
const PayrollRuleEditor = defineAsyncComponent(() => import('./components/PayrollRuleEditor.vue'))
/**
 * 对账面板与算薪日设置面板各自只在一个 Tab 内出现，
 * 按需异步拆 chunk，避免把两张表的首屏体积压到工资单主视图上。
 */
const ManualAdjustmentPanel = defineAsyncComponent(() => import('./components/ManualAdjustmentPanel.vue'))
const PayrollSettingsPanel = defineAsyncComponent(() => import('./components/PayrollSettingsPanel.vue'))

/**
 * 财务管理（需求9）页面壳
 *
 * 只做装配：标题 + 五个 Tab + 五个面板 + 四个弹层。取数、筛选、批量动作与审核提交收在 useFinancePage；
 * 对账 / 算薪日设置两个 Tab 的面板自带取数，刷新时按当前 Tab 转发到对应面板（面板 expose refresh）。
 * 「自动算薪运行」Tab 已下线（前端不再展示运行记录/手工触发，后端能力保留）。
 */
const page = useFinancePage()

const adjustPanelRef = ref(null)
const settingsPanelRef = ref(null)

/** 刷新按当前 Tab 分流：既有两 Tab 走 useFinancePage，自带取数的两 Tab 走各自面板的 refresh */
function onRefresh() {
  if (page.activeTab === 'adjustment') adjustPanelRef.value?.refresh?.()
  else if (page.activeTab === 'setting') settingsPanelRef.value?.refresh?.()
  else page.reloadAll()
}

onMounted(() => page.init())
</script>

<style scoped lang="scss">
.finance-page {
  .finance-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--sp-4);
    }

    :deep(.el-tabs__item) {
      height: 40px;
      font-size: var(--fs-body);
      color: var(--text-2);
    }

    :deep(.el-tabs__item.is-active) {
      color: var(--color-primary-strong);
      font-weight: var(--fw-medium);
    }

    :deep(.el-tabs__active-bar) {
      height: 2px;
      background-color: var(--color-primary-strong);
    }
  }
}
</style>
