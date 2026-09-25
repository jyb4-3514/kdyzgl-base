<template>
  <div class="sync-page">
    <PageHeader title="同步任务" :sub="page.headerSub" :loading="page.headerLoading">
      <template #actions>
        <el-button :icon="Refresh" @click="refreshCurrent">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 页内三视图：批次流水 / 采集配置 / 配置管理；Tab 状态写 URL，便于演示时直接给链接。
         「配置管理」仅 ADMIN 渲染（写操作后端也仅 ADMIN），站长看不到「点了才知道没权限」的入口（B.1.1） -->
    <el-tabs v-model="page.activeTab" class="sync-tabs" @tab-change="page.handleTabChange">
      <el-tab-pane name="batch" label="批次流水" />
      <el-tab-pane name="collect" label="采集配置" />
      <el-tab-pane v-if="page.isAdmin" name="config" label="配置管理" />
    </el-tabs>

    <SyncBatchPanel
      v-if="page.activeTab === 'batch'"
      :filters="page.filters"
      :is-admin="page.isAdmin"
      :stations="page.stations"
      :list="page.list"
      :total="page.total"
      :loading="page.loading"
      :list-error="page.listError"
      :has-filter="page.hasFilter"
      :page-num="page.query.pageNum"
      :page-size="page.query.pageSize"
      :triggering-id="page.triggeringId"
      :retrying-id="page.retryingId"
      @update:filters="page.applyFilters"
      @search="page.handleSearch"
      @reset="page.handleReset"
      @refresh="page.fetchList"
      @trigger="page.handleTrigger"
      @retry="page.handleRetry"
      @open-logs="page.openLogs"
      @page-change="page.handlePageChange"
      @size-change="page.handleSizeChange"
    />

    <CollectTabPanel
      v-else-if="page.activeTab === 'collect'"
      :overview="page.overview"
      :collect-loading="page.collectLoading"
      :collect-error="page.collectError"
      :attention-count="page.attentionCount"
      :attention-text="page.attentionText"
      :collect-filter="page.collectFilter"
      :visible-configs="page.visibleConfigs"
      :can-write="page.isAdmin"
      :acting-station-id="page.actingStationId"
      :config-visible="page.configVisible"
      :config-station="page.configStation"
      @refresh="page.fetchCollect"
      @edit="page.openConfig"
      @toggle="page.handleToggle"
      @focus-attention="page.focusAttention"
      @clear-filter="page.clearFilter"
      @close-config="page.closeConfig"
    />

    <!-- 配置管理：容器自带分段与三段独立四态，页头刷新经 ref 触发 -->
    <SyncConfigCenter v-else-if="page.activeTab === 'config'" ref="configCenterRef" />

    <SyncLogDrawer
      v-model="page.logVisible"
      :task="page.logTask"
      :logs="page.logs"
      :loading="page.loadingLog"
      :error="page.logError"
      @retry="page.loadLogs(page.logTask.id)"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import SyncBatchPanel from './components/SyncBatchPanel.vue'
import CollectTabPanel from './components/CollectTabPanel.vue'
import SyncLogDrawer from './components/SyncLogDrawer.vue'
import SyncConfigCenter from './components/SyncConfigCenter.vue'
import { useSyncPage } from './composables/useSyncPage.js'

/**
 * 同步任务（T16 / 需求1，demo-ux-improvement.md B1）页面壳
 *
 * 只做装配：标题 + 三 Tab + 三个面板 + 日志抽屉。取数、竞态守卫与 Tab 的 URL 同步收在 useSyncPage；
 * 批次流水与采集配置两段的内容编排各自下沉为域内面板组件，与配置管理容器并列。
 */
const page = useSyncPage()

// 配置管理容器自身的刷新入口挂在 ref 上，故三个 Tab 的刷新分派留在壳里
const configCenterRef = ref(null)

function refreshCurrent() {
  if (page.activeTab === 'collect') page.fetchCollect()
  else if (page.activeTab === 'config') configCenterRef.value && configCenterRef.value.refresh()
  else page.fetchList()
}

onMounted(() => page.init())
</script>

<style scoped lang="scss">
.sync-page {
  // Tab 结构与工单/通知页保持一致（5.3 列表页模板）
  .sync-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--sp-4);
    }

    :deep(.el-tabs__item) {
      height: 40px;
      font-size: var(--fs-body);
      color: var(--text-2);
    }

    :deep(.el-tabs__item.is-active) {
      color: var(--color-primary);
      font-weight: var(--fw-medium);
    }

    :deep(.el-tabs__active-bar) {
      height: 2px;
      background-color: var(--color-primary);
    }
  }
}
</style>
