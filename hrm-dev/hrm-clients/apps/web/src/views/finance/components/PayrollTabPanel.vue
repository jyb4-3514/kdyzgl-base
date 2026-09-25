<script setup>
import { PAYROLL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import PayrollDetailTable from './PayrollDetailTable.vue'

/**
 * 工资单面板（筛选 + 状态计数条 + 批量动作 + 列表）
 *
 * 筛选字段值全部受控：只回抛变更后的整份筛选对象（月份/驿站/状态改完即查，关键字回车或点查询），
 * 不在子组件里改父级筛选对象，避免「子组件一份值、父级一份值」两处各说各话。
 */
const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  stations: { type: Array, default: () => [] },
  list: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  counts: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  pendingSubmitCount: { type: Number, default: 0 },
  approvedCount: { type: Number, default: 0 }
})

const emit = defineEmits([
  'update:modelValue',
  'search',
  'filter-status',
  'batch-submit',
  'batch-publish',
  'refresh',
  'generate',
  'open',
  'action',
  'page-change',
  'size-change'
])

/** 回抛整份筛选对象：父级一次合并，避免逐字段事件把壳撑长 */
function patch(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

/** 月份/驿站/状态：改完即回第 1 页重查（与改前 v-model + @change=resetPage 等效） */
function patchAndSearch(key, value) {
  patch(key, value)
  emit('search')
}
</script>

<template>
  <el-card shadow="never" class="filter-card">
    <el-form inline>
      <el-form-item label="月份">
        <el-date-picker
          :model-value="modelValue.month"
          type="month"
          value-format="YYYY-MM"
          clearable
          placeholder="全部月份"
          style="width: 140px"
          @update:model-value="patchAndSearch('month', $event)"
        />
      </el-form-item>
      <el-form-item label="驿站">
        <el-select
          :model-value="modelValue.stationId"
          clearable
          placeholder="全部驿站"
          style="width: 150px"
          @update:model-value="patchAndSearch('stationId', $event)"
        >
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          :model-value="modelValue.status"
          clearable
          placeholder="全部状态"
          style="width: 140px"
          @update:model-value="patchAndSearch('status', $event)"
        >
          <el-option v-for="(dict, key) in PAYROLL_STATUS" :key="key" :value="key" :label="dict.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input
          :model-value="modelValue.keyword"
          placeholder="员工姓名 / 单号"
          clearable
          style="width: 180px"
          @update:model-value="patch('keyword', $event)"
          @keyup.enter="emit('search')"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="emit('search')">查询</el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card shadow="never" class="content-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <!-- 状态计数条：点一下即按该状态筛选，省掉一次下拉操作 -->
        <span class="finance-page__chips">
          <button
            v-for="(dict, key) in PAYROLL_STATUS"
            :key="key"
            type="button"
            class="finance-page__chip"
            :class="{ 'is-active': modelValue.status === key }"
            @click="emit('filter-status', key)"
          >
            {{ dict.label }} {{ (counts && counts[key]) || 0 }}
          </button>
        </span>
      </div>
      <div class="toolbar-right">
        <el-button
          :disabled="!pendingSubmitCount"
          :title="pendingSubmitCount ? '把当前范围内草稿/已驳回的单据提交审核' : '当前范围内没有可提交的单据'"
          @click="emit('batch-submit')"
        >
          批量提交审核（{{ pendingSubmitCount }}）
        </el-button>
        <el-button
          :disabled="!approvedCount"
          :title="approvedCount ? '发布当前范围内已通过的单据' : '当前范围内没有已通过待发布的单据'"
          @click="emit('batch-publish')"
        >
          批量发布（{{ approvedCount }}）
        </el-button>
        <!-- TODO(扩展): 工资单导出待契约提供导出接口（当前无 /finance/payrolls/export） -->
      </div>
    </div>

    <PayrollDetailTable
      :list="list"
      :total="total"
      :loading="loading"
      :error="error"
      :page-num="pageNum"
      :page-size="pageSize"
      :can-write="true"
      @retry="emit('refresh')"
      @generate="emit('generate')"
      @open="emit('open', $event)"
      @action="emit('action', $event)"
      @page-change="emit('page-change', $event)"
      @size-change="emit('size-change', $event)"
    />
  </el-card>
</template>

<style scoped lang="scss">
.content-card {
  margin-bottom: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.finance-page__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
}

// 状态计数用真按钮：既能点筛选，也天然可聚焦（不用绑 click 的 span）
.finance-page__chip {
  padding: var(--sp-1) var(--sp-2);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
  background-color: var(--surface-card);
  color: var(--text-3);
  font-family: inherit;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-std),
    color var(--dur-fast) var(--ease-std);

  &:hover {
    border-color: var(--color-primary-border);
    color: var(--color-primary-strong);
  }

  &.is-active {
    border-color: var(--color-primary-border);
    background-color: var(--color-primary-surface);
    color: var(--color-primary-strong);
    font-weight: var(--fw-medium);
  }
}
</style>
