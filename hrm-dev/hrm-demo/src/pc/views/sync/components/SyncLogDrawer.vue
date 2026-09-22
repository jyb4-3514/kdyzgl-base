<script setup>
import { SYNC_LOG_LEVEL, SYNC_STATUS } from '@/shared/constants/dict'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import { DRAWER_SIZE, SYNC_LOG_DOT } from '../model/syncMeta.js'

/**
 * 批次日志抽屉（批次摘要 + 流转时间线）
 *
 * 每次状态流转追加一条日志，重试/触发后由容器重新拉取即可看到追加；
 * 轴点配色与抽屉宽度取 model 常量，与列表页其它抽屉同口径。
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object, default: () => ({}) },
  logs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'retry'])
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="`批次日志 · ${task.batchNo || ''}`"
    :size="DRAWER_SIZE"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="drawer-body">
      <el-descriptions v-if="task.id" :column="2" size="small" border class="log-summary">
        <el-descriptions-item label="驿站">{{ task.stationName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :dict="SYNC_STATUS" :value="task.status" :variant="task.status === 0 ? 'outline' : 'soft'" />
        </el-descriptions-item>
        <el-descriptions-item label="包裹数">{{ task.parcelTotal }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ task.retryCount }}</el-descriptions-item>
      </el-descriptions>

      <el-skeleton v-if="loading" :rows="4" animated />
      <StateBlock v-else-if="error" variant="error" title="日志加载失败" @action="emit('retry')" />
      <StateBlock v-else-if="!logs.length" variant="empty" title="该批次暂无日志" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="log in logs"
          :key="log.id"
          :timestamp="log.logTime"
          placement="top"
          :color="SYNC_LOG_DOT[log.level]"
        >
          <div class="log-item">
            <StatusTag :dict="SYNC_LOG_LEVEL" :value="log.level" />
            <span class="log-message">{{ log.message }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-body {
  min-height: 200px;
}

.log-summary {
  margin-bottom: var(--sp-5);
}

.log-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.log-message {
  font-size: var(--fs-body);
  color: var(--text-1);
  word-break: break-all;
}
</style>
