<script setup>
import MetricCard from '../../../components/MetricCard.vue'

/**
 * 首屏主指标区（今日入库 / 今日取件 / 在库待取 / 异常件）
 * 数据封装与错误态都不在此处：卡片只按 props 渲染，点击下钻以 select 事件上报目标路由。
 */
defineProps({
  cards: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['select'])
</script>

<template>
  <el-row :gutter="16" class="dash-row">
    <el-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :lg="6">
      <MetricCard
        v-if="!loading"
        class="dash-card"
        variant="hero"
        :label="card.label"
        :value="card.value"
        :icon="card.icon"
        :tone="card.tone"
        :trend="card.trend"
        :error="error"
        :error-text="card.errorText"
        clickable
        @click="emit('select', card.to)"
      />
      <el-card v-else class="dash-card" shadow="never"><el-skeleton :rows="2" animated /></el-card>
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
