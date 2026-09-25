<script setup>
import DashboardPanel from './DashboardPanel.vue'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 同步健康度区块
 * 数值口径由容器算好（各驿站最近一个已结束批次的成功率），本组件只呈现。
 */
defineProps({
  health: { type: Object, default: () => ({ latestBatchSuccessRate: 0, failedStationCount: 0, lastBatchTime: '' }) },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['retry'])
</script>

<template>
  <DashboardPanel title="同步健康度">
    <el-skeleton v-if="loading" :rows="3" animated />
    <StateBlock v-else-if="error" variant="error" title="同步健康度加载失败" @action="emit('retry')" />
    <template v-else>
      <div class="health-rate">
        <span class="health-rate__value">{{ (health.latestBatchSuccessRate * 100).toFixed(1) }}%</span>
        <span class="health-rate__label">最近批次成功率</span>
      </div>
      <!-- 绿色取 600 档（#389E0D），与 --color-success（700 档）不同档，暂保留 L1 直引（P2-4 已登记） -->
      <el-progress
        :percentage="Math.round(health.latestBatchSuccessRate * 100)"
        :stroke-width="8"
        :show-text="false"
        :color="health.failedStationCount ? 'var(--color-warning-icon)' : 'var(--c-green-600)'"
      />
      <ul class="health-list">
        <li>
          <span>同步失败驿站</span>
          <strong :class="{ 'is-danger': health.failedStationCount > 0 }">{{ health.failedStationCount }} 个</strong>
        </li>
        <li>
          <span>最近批次时间</span>
          <strong>{{ health.lastBatchTime || '—' }}</strong>
        </li>
      </ul>
    </template>
  </DashboardPanel>
</template>

<style scoped lang="scss">
.health-rate {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  margin-bottom: var(--sp-2);

  &__value {
    font-size: var(--fs-num-lg);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-num-lg);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__label {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}

.health-list {
  margin: var(--sp-3) 0 0;
  padding: 0;
  list-style: none;
  font-size: var(--fs-body);

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--sp-2) 0;
    border-top: 1px solid var(--border-line);
    color: var(--text-3);
  }

  strong {
    font-weight: var(--fw-medium);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;

    &.is-danger {
      color: var(--color-danger);
    }
  }
}
</style>
