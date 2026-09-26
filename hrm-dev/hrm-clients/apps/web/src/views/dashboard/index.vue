<template>
  <div class="dashboard">
    <PageHeader title="数据看板" :sub="page.headerSub" :loading="page.loading">
      <template #actions>
        <el-button :icon="Refresh" @click="page.loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 工单指标（保留）：待处理 / 处理中 / 今日新增 / 超时未处理 + 平均处理时长 -->
    <DashboardWorkOrderBlock
      :metrics="page.woMetrics"
      :over-sla-count="page.workOrder.overSlaCount"
      :loading="page.woLoading"
      :error="page.woError"
      @retry="page.loadWorkOrder"
      @go="goWorkOrder"
    />

    <!-- 一期口径指标：组织规模（员工/驿站/部门/今日登录） -->
    <DashboardOrgBlock
      :cards="page.baseCards"
      :summary="page.summary"
      :summary-text="page.orgSummaryText"
      :error="page.baseError"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import DashboardWorkOrderBlock from './components/DashboardWorkOrderBlock.vue'
import DashboardOrgBlock from './components/DashboardOrgBlock.vue'
import { useDashboardData } from './composables/useDashboardData.js'

/**
 * 数据看板（T14）页面壳
 *
 * MVP 裁剪：包裹（入库/取件/在库/异常）、同步健康度、驿站排行、包裹趋势四块整体下架——
 * 它们全部源自「包裹族」模块，本页只保留工单指标与一期组织规模两块。
 * 被裁的区块组件与取数逻辑（Hero/Inline/Trend/SyncHealth/Rank）保留在磁盘待二期恢复，本文件不再引用。
 */
const router = useRouter()
const page = useDashboardData()

/** 工单区块右上角「查看全部工单」入口 */
const goWorkOrder = () => router.push('/work-order')

onMounted(() => page.loadAll())
</script>

<style scoped lang="scss">
.dashboard {
  .dash-row {
    margin-bottom: var(--sp-4);
  }
}
</style>
