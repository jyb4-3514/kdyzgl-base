<template>
  <div class="work-order-page">
    <PageHeader title="工单管理" :sub="page.headerSub" :loading="page.loading">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="page.createVisible = true">新建工单</el-button>
        <!-- 自动派单只对老板渲染：规则维护接口仅 ADMIN，且契约 GET 未加 roles 属权限不一致（U6） -->
        <el-button v-if="page.isAdmin" :icon="Promotion" @click="page.dispatchVisible = true">自动派单</el-button>
        <el-button :icon="Refresh" @click="page.refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <WorkOrderTabs v-model="page.activeTab" :tabs="TABS" :counts="page.tabCounts" @change="page.handleTabChange" />

    <WorkOrderFilterBar
      :model-value="page.filters"
      :is-admin="page.isAdmin"
      :stations="page.stations"
      @update:model-value="page.applyFilters"
      @search="page.handleSearch"
      @reset="page.handleReset"
    />

    <WorkOrderListTable
      :list="page.list"
      :total="page.total"
      :loading="page.loading"
      :list-error="page.listError"
      :has-filter="page.hasFilter"
      :page-num="page.query.pageNum"
      :page-size="page.query.pageSize"
      :highlight-id="page.highlightId"
      @refresh="page.fetchList"
      @reset="page.handleReset"
      @page-change="page.handlePageChange"
      @size-change="page.handlePageSizeChange"
      @detail="page.openDetail"
    />

    <!-- 详情抽屉：基础信息 + 工单描述 + 流转时间线 + 底部操作 -->
    <WorkOrderDetailDrawer
      v-model="page.detailVisible"
      :detail="page.detail"
      :loading="page.loadingDetail"
      :acting="page.acting"
      :can-assign="page.canAssign"
      :can-transfer="page.canTransfer"
      :available-actions="page.availableActions"
      :timeline-events="page.timelineEvents"
      @assign="page.openAssign(page.detail)"
      @transfer="page.openTransfer(page.detail)"
      @transition="page.handleTransition"
    />

    <!-- 指派弹窗：处理人下拉取本站启用员工 -->
    <WorkOrderAssignDialog
      v-model="page.assignDialog.visible"
      :order-no="page.assignDialog.orderNo"
      :assignees="page.assignees"
      :assignee-id="page.assignDialog.assigneeId"
      :loading="page.assignDialog.loading"
      @update:assignee-id="page.assignDialog.assigneeId = $event"
      @submit="page.submitAssign"
    />

    <!-- 转单弹窗：只改处理人、不改状态；理由必填（2-100 字，与后端校验同口径） -->
    <WorkOrderTransferDialog
      v-model="page.transferDialog.visible"
      :order-no="page.transferDialog.orderNo"
      :from-name="page.transferDialog.fromName"
      :candidates="page.transferCandidates"
      :options-loading="page.transferOptionsLoading"
      :scope-hint="page.transferScopeHint"
      :loading="page.transferDialog.loading"
      @submit="page.submitTransfer"
    />

    <!-- 新建工单（需求3）：ADMIN 与站长都可用，归属驿站站长固定为本站 -->
    <CreateWorkOrderDialog
      v-model="page.createVisible"
      :is-admin="page.isAdmin"
      :stations="page.stations"
      :default-station-id="page.createStationId"
      :fixed-station-name="page.currentUser.stationName || ''"
      @created="page.handleCreated"
    />

    <!-- 企微自动派单模拟入口（需求3）：仅 ADMIN 渲染（规则维护接口仅 ADMIN） -->
    <AutoDispatchDrawer
      v-if="page.isAdmin"
      v-model="page.dispatchVisible"
      :stations="page.stations"
      :default-station-id="page.createStationId"
      :default-group-name="page.defaultGroupName"
      @dispatched="page.handleDispatched"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Promotion, Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import WorkOrderTabs from './components/WorkOrderTabs.vue'
import WorkOrderFilterBar from './components/WorkOrderFilterBar.vue'
import WorkOrderListTable from './components/WorkOrderListTable.vue'
import WorkOrderDetailDrawer from './components/WorkOrderDetailDrawer.vue'
import WorkOrderAssignDialog from './components/WorkOrderAssignDialog.vue'
import WorkOrderTransferDialog from './components/WorkOrderTransferDialog.vue'
import CreateWorkOrderDialog from './components/CreateWorkOrderDialog.vue'
import AutoDispatchDrawer from './components/AutoDispatchDrawer.vue'
import { TABS } from './model/workOrderMeta.js'
import { useWorkOrderPage } from './composables/useWorkOrderPage.js'

/**
 * 工单管理（T17，demo-ui-redesign.md 5.4）页面壳
 *
 * 只做装配：标题 + Tab + 筛选 + 列表 + 详情/指派/转单/建单/派单五个弹层。
 * 状态与流转合法性以 Mock（= 未来后端）为准，本页只按当前状态渲染合法动作（demo-design.md 7.4.5）。
 */
const route = useRoute()
const page = useWorkOrderPage()

onMounted(() => {
  page.init()
  // 通知中心跳转（?orderId=）时直接展开对应工单
  if (route.query.orderId) page.openDetail(route.query.orderId)
})
</script>
