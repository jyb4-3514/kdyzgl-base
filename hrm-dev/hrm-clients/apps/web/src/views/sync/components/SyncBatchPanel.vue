<script setup>
import { Refresh } from '@element-plus/icons-vue'
import { SYNC_STATUS } from '@kdyzgl/shared/constants/dict'
import { formatDuration } from '../../../utils/format.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import { PAGE_SIZES, failedRowClassOf } from '../model/syncMeta.js'

/**
 * 批次流水面板（筛选 + 表格 + 分页）
 *
 * 筛选字段值全部受控：只回抛变更后的整份筛选对象，不在子组件里改父级筛选对象，
 * 避免「子组件一份值、父级一份值」两处各说各话（与工单筛选栏同口径）。
 * 行内可执行动作的合法性以服务端下发的 row.status 为准，前端只决定按钮禁用与文案。
 */
const props = defineProps({
  filters: { type: Object, default: () => ({}) },
  isAdmin: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] },
  list: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  listError: { type: Boolean, default: false },
  hasFilter: { type: Boolean, default: false },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  // 触发 / 重试各自独立的 loading 标识，避免点触发时重试也跟着转圈（A6-2）
  triggeringId: { type: [Number, String], default: null },
  retryingId: { type: [Number, String], default: null }
})

const emit = defineEmits([
  'update:filters',
  'search',
  'reset',
  'refresh',
  'trigger',
  'retry',
  'open-logs',
  'page-change',
  'size-change'
])

/** 回抛整份筛选对象：父级一次合并，避免逐字段事件把壳撑长 */
function patch(key, value) {
  emit('update:filters', { ...props.filters, [key]: value })
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
          :model-value="filters.stationId"
          clearable
          placeholder="全部驿站"
          @update:model-value="patch('stationId', $event)"
        >
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          :model-value="filters.status"
          clearable
          placeholder="全部状态"
          @update:model-value="patch('status', $event)"
        >
          <el-option v-for="(item, key) in SYNC_STATUS" :key="key" :label="item.label" :value="Number(key)" />
        </el-select>
      </el-form-item>
      <el-form-item label="批次号">
        <el-input
          :model-value="filters.keyword"
          placeholder="批次号模糊查询"
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

  <el-card shadow="never" class="content-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-tip">共 {{ total }} 个批次 · 状态机：待领取 → 执行中 → 成功 / 失败</span>
      </div>
      <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="emit('refresh')" />
    </div>

    <!-- 错误态独立于空态：接口失败时不能让用户看到"没有符合条件的同步任务"（修 P14） -->
    <StateBlock v-if="listError" variant="error" title="同步任务加载失败" @action="emit('refresh')" />

    <StateBlock
      v-else-if="!loading && !list.length"
      variant="empty"
      :title="hasFilter ? '当前筛选条件下没有同步任务' : '暂无同步任务'"
      :action-text="hasFilter ? '清空筛选' : ''"
      @action="emit('reset')"
    />

    <template v-else>
      <el-table v-loading="loading" class="sticky-table" :data="list" border row-key="id" :row-class-name="failedRowClassOf">
        <!-- 失败行可展开：失败原因是排查第一信息，tooltip 不能复制也不便对多行比对（A6-3）；
             仅失败行出现展开图标（非失败行由样式隐藏），避免整表都是无意义的展开箭头 -->
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <div class="expand-panel">
              <p class="expand-panel__label">失败原因</p>
              <p class="expand-panel__text">{{ row.errorMsg || '未记录失败原因' }}</p>
              <p class="expand-panel__meta">
                成功 {{ row.successCount }} 件 · 失败 {{ row.failCount }} 件 · 重试 {{ row.retryCount }} 次
              </p>
              <el-button link type="primary" @click="emit('open-logs', row)">查看批次日志</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" min-width="150" />
        <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :dict="SYNC_STATUS" :value="row.status" :variant="row.status === 0 ? 'outline' : 'soft'" />
          </template>
        </el-table-column>
        <el-table-column prop="parcelTotal" label="包裹数" width="90" align="right" />
        <el-table-column label="成功 / 失败" width="110" align="right">
          <template #default="{ row }">{{ row.successCount }} / {{ row.failCount }}</template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="70" align="center" />
        <el-table-column label="耗时" width="110" align="center">
          <template #default="{ row }">{{ formatDuration(row.startTime, row.finishTime) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="失败原因" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMsg || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status !== 0"
              :title="row.status === 0 ? '手动触发该批次同步' : '仅「待领取」批次可触发'"
              :loading="triggeringId === row.id"
              @click="emit('trigger', row)"
            >
              触发
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="row.status !== 3"
              :title="row.status === 3 ? '重试后将回到待领取队列' : '仅「失败」批次可重试'"
              :loading="retryingId === row.id"
              @click="emit('retry', row)"
            >
              重试
            </el-button>
            <el-button link type="primary" @click="emit('open-logs', row)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="PAGE_SIZES"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="emit('size-change', $event)"
          @current-change="emit('page-change', $event)"
        />
      </div>
    </template>
  </el-card>
</template>

<style scoped lang="scss">
/* 卡片即组件根，已无外层页面类可挂，故补 .el-form--inline 一层做权重兜底：
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

.content-card {
  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  // 只给失败行显示展开图标；非失败行保留占位宽度，表头列不错位
  :deep(.el-table__row:not(.is-failed-row) .el-table__expand-icon) {
    visibility: hidden;
    pointer-events: none;
  }
}

.expand-panel {
  padding: var(--sp-3) var(--sp-6);
  background-color: var(--surface-sub);

  &__label {
    margin: 0;
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__text {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-body);
    color: var(--text-1);
    word-break: break-all;
  }

  &__meta {
    margin: var(--sp-1) 0 var(--sp-2);
    font-size: var(--fs-caption);
    color: var(--text-3);
    font-variant-numeric: tabular-nums;
  }
}
</style>
