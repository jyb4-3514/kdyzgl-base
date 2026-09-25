<script setup>
import { Refresh } from '@element-plus/icons-vue'
import CollectStateBoard from './CollectStateBoard.vue'
import CollectConfigTable from './CollectConfigTable.vue'
import CollectConfigDrawer from './CollectConfigDrawer.vue'

/**
 * 采集配置面板（状态计数 + 待处理提示 + 配置表 + 配置抽屉）
 *
 * 本面板只做编排与事件上报：总览计数、排序表、抽屉三段各自维护四态，
 * 写操作（开关、保存配置）由容器统一处理，避免同一份 API 调用散在两处。
 */
defineProps({
  overview: { type: Object, default: () => ({ total: 0, counts: null }) },
  collectLoading: { type: Boolean, default: false },
  collectError: { type: Boolean, default: false },
  attentionCount: { type: Number, default: 0 },
  attentionText: { type: String, default: '' },
  collectFilter: { type: String, default: null },
  visibleConfigs: { type: Array, default: () => [] },
  canWrite: { type: Boolean, default: false },
  actingStationId: { type: [Number, String], default: null },
  configVisible: { type: Boolean, default: false },
  configStation: { type: Object, default: () => ({ id: null, name: '' }) }
})

const emit = defineEmits(['refresh', 'edit', 'toggle', 'focus-attention', 'clear-filter', 'close-config'])

const handleEdit = (row) => emit('edit', row)
const handleToggle = (row, value) => emit('toggle', row, value)
</script>

<template>
  <CollectStateBoard
    :counts="overview.counts"
    :loading="collectLoading"
    :error="collectError"
    @retry="emit('refresh')"
  />

  <!-- 待处理提示条：仅异常 + 未配置 > 0 时出现，点击把表格收敛到待处理项（B1.2 ②） -->
  <el-alert v-if="!collectError && attentionCount > 0" class="collect-alert" type="warning" :closable="false" show-icon>
    <template #title>
      <span>{{ attentionText }}，建议优先处理</span>
      <el-button
        v-if="collectFilter !== 'attention'"
        link
        type="primary"
        class="collect-alert__action"
        @click="emit('focus-attention')"
      >
        查看列表
      </el-button>
      <el-button v-else link type="primary" class="collect-alert__action" @click="emit('clear-filter')">显示全部</el-button>
    </template>
  </el-alert>

  <el-card shadow="never" class="content-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-tip">
          共 {{ overview.total }} 个驿站 · 按状态排序：异常 → 未配置 → 正常 → 已停用
          <template v-if="collectFilter === 'attention'"> · 当前仅看待处理项</template>
        </span>
      </div>
      <el-button
        :icon="Refresh"
        circle
        text
        :loading="collectLoading"
        aria-label="刷新采集配置"
        @click="emit('refresh')"
      />
    </div>

    <CollectConfigTable
      :configs="visibleConfigs"
      :loading="collectLoading"
      :error="collectError"
      :can-write="canWrite"
      :acting-station-id="actingStationId"
      @retry="emit('refresh')"
      @edit="handleEdit"
      @toggle="handleToggle"
    />
  </el-card>

  <CollectConfigDrawer
    :model-value="configVisible"
    :station-id="configStation.id"
    :station-name="configStation.name"
    :can-write="canWrite"
    @update:model-value="emit('close-config')"
    @saved="emit('refresh')"
  />
</template>

<style scoped lang="scss">
.toolbar-tip {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.collect-alert {
  margin-bottom: var(--sp-4);

  &__action {
    margin-left: var(--sp-2);
  }
}
</style>
