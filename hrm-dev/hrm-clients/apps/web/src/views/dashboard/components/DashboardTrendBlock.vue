<script setup>
import { computed } from 'vue'
import DashboardPanel from './DashboardPanel.vue'
import TrendChart from '../../../components/TrendChart.vue'

/**
 * 包裹趋势区块（近 7 / 30 天）
 * 切档用代理 v-model 上报：本组件不持有天数，选中即把新值抛给容器重取数据（与改前 v-model + change 等效）。
 */
const props = defineProps({
  days: { type: Number, default: 7 },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['select-days', 'retry'])

const model = computed({
  get: () => props.days,
  set: (value) => emit('select-days', value)
})
</script>

<template>
  <DashboardPanel title="包裹趋势">
    <template #actions>
      <el-radio-group v-model="model" size="small">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </template>
    <TrendChart :data="data" :loading="loading" :error="error" @retry="emit('retry')" />
  </DashboardPanel>
</template>
