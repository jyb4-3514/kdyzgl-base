<script setup>
import DashboardPanel from './DashboardPanel.vue'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 工单 / SLA 区块
 * 指标与超时数由容器推算（Mock 无汇总接口），本组件只呈现；跳转以 go 事件上报。
 */
defineProps({
  metrics: { type: Object, default: () => ({ primary: [], avgValue: '—' }) },
  overSlaCount: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['retry', 'go'])
</script>

<template>
  <DashboardPanel title="工单 / SLA">
    <template #actions>
      <el-button link type="primary" @click="emit('go')">查看工单</el-button>
    </template>

    <el-skeleton v-if="loading" :rows="3" animated />
    <StateBlock v-else-if="error" variant="error" title="工单指标加载失败" @action="emit('retry')" />
    <template v-else>
      <!-- 4 项 2×2 + 时长项独占整行：改前 5 项塞 2 列会留孤儿格，且"件数"与"时长"混排（修 P19） -->
      <div class="wo-grid">
        <div v-for="item in metrics.primary" :key="item.label" class="wo-item">
          <span class="wo-item__value" :class="{ 'is-danger': item.danger }">{{ item.value }}</span>
          <span class="wo-item__label">{{ item.label }}</span>
        </div>
        <div class="wo-item wo-item--wide">
          <span class="wo-item__value">{{ metrics.avgValue }}</span>
          <span class="wo-item__label">平均处理时长（件数与时长不同量纲，独占一行）</span>
        </div>
      </div>
      <p v-if="overSlaCount > 0" class="wo-alert">
        有 {{ overSlaCount }} 条工单超时未处理
        <el-button link type="danger" @click="emit('go')">去处理</el-button>
      </p>
    </template>
  </DashboardPanel>
</template>

<style scoped lang="scss">
.wo-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-3);
}

.wo-item {
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background-color: var(--surface-subtle);

  &--wide {
    grid-column: 1 / -1;
  }

  &__value {
    display: block;
    font-size: var(--fs-num-md);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-num-md);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;

    &.is-danger {
      color: var(--color-danger);
    }
  }

  &__label {
    display: block;
    margin-top: var(--sp-1);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}

.wo-alert {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  color: var(--color-danger);
}
</style>
