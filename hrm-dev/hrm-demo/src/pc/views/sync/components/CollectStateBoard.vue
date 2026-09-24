<script setup>
import { computed } from 'vue'
import MetricCard from '../../../components/MetricCard.vue'
import StateBlock from '../../../components/StateBlock.vue'

/**
 * 采集状态计数卡（需求1，B1.2 ①）
 * 数据源固定为 GET /sync/overview 的 counts（服务端权威计数），不做前端聚合——
 * 否则看板与采集配置表会出现两套口径（A4-3 同类问题）。
 * 空态不适用：每站必有配置行（db.js:393 注释），四态计数之和恒等于驿站总数，故只保留加载/错误/边界（0 值）。
 */
const props = defineProps({
  counts: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['retry'])

/** 顺序把「异常」放最前：管理员第一眼要看到的是待处理项，而不是正常项 */
const CARDS = [
  { key: 'abnormal', label: '采集异常', tone: 'red' },
  { key: 'unconfigured', label: '未配置采集', tone: 'orange' },
  { key: 'normal', label: '采集正常', tone: 'green' },
  { key: 'disabled', label: '已停用', tone: 'neutral' }
]

// counts 未到位时按 0 渲染（边界：某态为 0 显示 0，不显示「—」）
const cards = computed(() => CARDS.map((item) => ({ ...item, value: props.counts ? props.counts[item.key] : 0 })))
</script>

<template>
  <section class="collect-board">
    <StateBlock v-if="error" variant="error" title="采集状态加载失败" @action="emit('retry')" />

    <el-row v-else :gutter="16" class="collect-board__row" role="list">
      <el-col v-for="card in cards" :key="card.key" :xs="12" :sm="12" :md="12" :lg="6" role="listitem">
        <MetricCard
          variant="inline"
          class="collect-board__card"
          :label="card.label"
          :value="card.value"
          unit="个驿站"
          :tone="card.tone"
          :accent="card.key === 'abnormal' && card.value > 0"
          :loading="loading"
        />
      </el-col>
    </el-row>
  </section>
</template>

<style scoped lang="scss">
.collect-board {
  margin-bottom: var(--sp-4);

  &__row {
    row-gap: var(--sp-4);
  }

  &__card {
    height: 100%;
  }
}
</style>
