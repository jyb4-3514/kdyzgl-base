<script setup>
import { valueTextOf } from '../utils/configCenter.js'
import StateBlock from '../../../components/StateBlock.vue'
import InheritToggleCell from './InheritToggleCell.vue'

/**
 * 驿站覆盖矩阵（设计 B.4 / E.1 Organism）
 * 8 站 × N 配置项，规模可控不分页。列头悬浮给出配置项说明 + 全局默认值，
 * 单元格为「值 + 来源标记」，恢复继承只在抽屉里操作（避免矩阵表误触，B.5）。
 * 数据完整 / 部分配置项缺失两种形态：缺失列整体置灰「暂不支持」，不伪造「继承」标记。
 */
const props = defineProps({
  configs: { type: Array, default: () => [] },
  items: { type: Array, default: () => [] },
  globalValues: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  // 从「全局默认」跳转过来时高亮该配置项列
  focusItemKey: { type: String, default: '' },
  optionSetOf: { type: Function, default: null }
})

const emit = defineEmits(['retry', 'configure'])

/** 该配置项是否已纳入接口返回：未返回时整列「暂不支持」（A.1.2 后两项的降级口径） */
const supported = (row, item) => row.values && Object.prototype.hasOwnProperty.call(row.values, item.itemKey)

function cellValue(row, item) {
  const set = props.optionSetOf ? props.optionSetOf(item.optionSetKey) : null
  return valueTextOf(item, row.values ? row.values[item.itemKey] : null, set)
}

function headerTitle(item) {
  const set = props.optionSetOf ? props.optionSetOf(item.optionSetKey) : null
  const globalText = valueTextOf(item, props.globalValues[item.itemKey], set)
  return `${item.description || item.name}｜全局默认：${globalText}`
}
</script>

<template>
  <section class="override-table">
    <StateBlock v-if="error" variant="error" title="驿站覆盖数据加载失败" @action="emit('retry')" />

    <StateBlock
      v-else-if="!loading && !configs.length"
      variant="empty"
      title="暂无驿站配置"
      description="驿站建立后会自动生成采集配置行"
    />

    <el-table v-else v-loading="loading" class="sticky-table" :data="configs" border row-key="stationId">
      <el-table-column prop="stationName" label="驿站" min-width="120" fixed="left" show-overflow-tooltip />

      <el-table-column
        v-for="item in items"
        :key="item.itemKey"
        :min-width="170"
        :class-name="item.itemKey === focusItemKey ? 'is-focused-col' : ''"
      >
        <template #header>
          <span :title="headerTitle(item)">{{ item.name }}</span>
        </template>
        <template #default="{ row }">
          <InheritToggleCell
            :station-name="row.stationName"
            :item-name="item.name"
            :source="row.sources ? row.sources[item.itemKey] : 'INHERIT'"
            :value="cellValue(row, item)"
            :supported="supported(row, item)"
          />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :aria-label="`配置 ${row.stationName} 的覆盖项`"
            @click="emit('configure', row)"
            >配置</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped lang="scss">
.override-table {
  // 从全局默认跳转过来时，目标列浅底高亮（辅助定位，不改变任何数据语义）
  :deep(.is-focused-col) {
    background-color: var(--surface-hover);
  }
}
</style>
