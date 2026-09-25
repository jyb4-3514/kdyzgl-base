<script setup>
import { WORK_ORDER_PRIORITY, WORK_ORDER_TYPE } from '@kdyzgl/shared/constants/dict'

/**
 * 工单列表筛选栏
 *
 * 字段值全部受控：只回抛变更后的筛选对象，不在子组件里改父级筛选对象，
 * 避免「子组件一份值、父级一份值」两处各说各话。父级只把变更合并回 query 即可。
 */
const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  isAdmin: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'search', 'reset'])

/** 回抛整份筛选对象：父级一次合并，避免逐字段事件把壳撑长 */
function patch(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

/** 复刻 v-model.trim 语义：只对字符串裁剪，清空时的空值原样透出 */
function patchKeyword(value) {
  patch('keyword', typeof value === 'string' ? value.trim() : value)
}
</script>

<template>
  <el-card shadow="never" class="filter-card">
    <el-form inline @submit.prevent>
      <el-form-item v-if="isAdmin" label="驿站">
        <el-select
          :model-value="modelValue.stationId"
          clearable
          placeholder="全部驿站"
          @update:model-value="patch('stationId', $event)"
        >
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select
          :model-value="modelValue.type"
          clearable
          placeholder="全部类型"
          @update:model-value="patch('type', $event)"
        >
          <el-option v-for="(item, key) in WORK_ORDER_TYPE" :key="key" :label="item.label" :value="Number(key)" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-select
          :model-value="modelValue.priority"
          clearable
          placeholder="全部优先级"
          @update:model-value="patch('priority', $event)"
        >
          <el-option v-for="(item, key) in WORK_ORDER_PRIORITY" :key="key" :label="item.label" :value="Number(key)" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          :model-value="modelValue.keyword"
          placeholder="工单号 / 标题"
          clearable
          class="filter-input"
          @update:model-value="patchKeyword"
          @keyup.enter="emit('search')"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="emit('search')">查询</el-button>
        <el-button @click="emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped lang="scss">
/* 卡片即组件根，没有外层页面类可挂，故补 .el-form--inline 一层做权重兜底：
   否则与 Element 自带的 .el-form--inline .el-form-item（0,2,0）同权重，只能靠样式注入顺序分胜负 */
.filter-card {
  margin-bottom: var(--sp-4);

  :deep(.el-form--inline .el-form-item) {
    margin: 0 var(--sp-4) var(--sp-3) 0;
  }

  :deep(.el-form--inline .el-form-item:last-child) {
    margin-right: 0;
    margin-bottom: 0;
  }

  :deep(.el-form--inline .el-form-item__label) {
    font-size: var(--fs-caption);
    color: var(--text-2);
  }

  :deep(.el-select) {
    width: 160px;
  }

  :deep(.filter-input) {
    width: 200px;
  }
}
</style>
