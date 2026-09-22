<script setup>
import MetricCard from '../../../components/MetricCard.vue'

/**
 * 次级指标条（包裹 / 同步 / 工单各出一项）
 * 单项失败只影响自己那一格（5.2 第 7 条）：loading / error 由每个卡片自带，不在本组件统一收敛。
 */
defineProps({
  cards: { type: Array, default: () => [] }
})
</script>

<template>
  <el-row :gutter="16" class="dash-row">
    <el-col v-for="card in cards" :key="card.key" :xs="12" :sm="12" :md="8" :lg="6">
      <MetricCard
        class="dash-card"
        variant="inline"
        :label="card.label"
        :value="card.value"
        :unit="card.unit"
        :format="card.format"
        :loading="card.loading"
        :error="card.error"
        :error-text="card.errorText"
        :accent="card.key === 'overSlaCount'"
        :tone="card.tone"
      />
    </el-col>
  </el-row>
</template>

<style scoped lang="scss">
.dash-row {
  margin-bottom: var(--sp-4);
}

.dash-card {
  height: 100%;
}
</style>
