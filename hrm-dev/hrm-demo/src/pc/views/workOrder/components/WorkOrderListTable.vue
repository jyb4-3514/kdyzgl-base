<script setup>
import { Refresh } from '@element-plus/icons-vue'
import {
  WORK_ORDER_PRIORITY,
  WORK_ORDER_SOURCE,
  WORK_ORDER_STATUS,
  WORK_ORDER_TYPE,
  dictLabel
} from '@/shared/constants/dict'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import SlaCountdown from '../../../components/SlaCountdown.vue'
import WorkOrderCopyButton from '../../../components/WorkOrderCopyButton.vue'
import { PRIORITY_VARIANT, rowClassNameOf, slaFinished, sourceVariantOf, statusVariantOf } from '../model/workOrderMeta.js'

/**
 * 工单列表（工具栏 + 表格 + 分页 + 三态）
 *
 * 数据全部由页面壳以 props 注入，行类名与单元格形态取 model 的映射，本组件不另写一套状态分支。
 */
const props = defineProps({
  list: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  listError: { type: Boolean, default: false },
  hasFilter: { type: Boolean, default: false },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  highlightId: { type: [Number, String], default: null }
})

const emit = defineEmits(['refresh', 'reset', 'pageChange', 'sizeChange', 'detail'])

const rowClassName = ({ row }) => rowClassNameOf(row, props.highlightId)
</script>

<template>
  <el-card shadow="never" class="content-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <span class="toolbar-tip">
          共 {{ total }} 条 · SLA 口径：低 48h / 中 24h / 高 8h · 超时未处理 = 已过 SLA
          且仍为待处理/处理中；仅高亮提醒，不自动改状态
        </span>
      </div>
      <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="emit('refresh')" />
    </div>

    <StateBlock v-if="listError" variant="error" title="工单列表加载失败" @action="emit('refresh')" />

    <StateBlock
      v-else-if="!loading && !list.length"
      variant="empty"
      :title="hasFilter ? '当前筛选条件下没有工单' : '暂无工单'"
      :action-text="hasFilter ? '清空筛选' : ''"
      @action="emit('reset')"
    />

    <template v-else>
      <el-table v-loading="loading" class="sticky-table" :data="list" border :row-class-name="rowClassName">
        <el-table-column prop="orderNo" label="工单号" min-width="150" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ dictLabel(WORK_ORDER_TYPE, row.type) }}</template>
        </el-table-column>
        <!-- 来源列（A7-6）：企微自动派发的工单与手工工单混在一起，老板需要一眼区分 -->
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :dict="WORK_ORDER_SOURCE" :value="row.source || 'MANUAL'" :variant="sourceVariantOf(row.source)" />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <StatusTag
              :dict="WORK_ORDER_PRIORITY"
              :value="row.priority"
              :variant="PRIORITY_VARIANT[row.priority] || 'soft'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
        <el-table-column label="上报人" min-width="90">
          <template #default="{ row }">{{ row.reporterName || '—' }}</template>
        </el-table-column>
        <el-table-column label="处理人" min-width="90">
          <template #default="{ row }">{{ row.assigneeName || '未指派' }}</template>
        </el-table-column>
        <!-- SLA 列同时给胶囊与截止时间：只靠颜色一种通道会挡住色觉障碍用户 -->
        <el-table-column label="SLA" width="140">
          <template #default="{ row }">
            <div class="sla-cell">
              <SlaCountdown :deadline="row.slaDeadline" :priority="row.priority" :finished="slaFinished(row.status)" />
              <span class="sla-cell__deadline">{{ row.slaDeadline }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :dict="WORK_ORDER_STATUS" :value="row.status" :variant="statusVariantOf(row.status)" />
          </template>
        </el-table-column>
        <!-- 操作列由 80 放宽到 120：原宽度只放得下「详情」文字链，复制改用图标按钮 + tooltip 控宽 -->
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="emit('detail', row.id)">详情</el-button>
            <WorkOrderCopyButton :order="row" label="复制工单详情" />
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="emit('sizeChange', $event)"
          @current-change="emit('pageChange', $event)"
        />
      </div>
    </template>
  </el-card>
</template>

<style scoped lang="scss">
/* 行底色与复制按钮热区都挂在卡片根上再 :deep 下去：拆组件后少了外层页面类，
   若直接顶层 :deep，选择器权重会低于 el-table 自带的 hover 行底色规则（0,4,2）而失效 */
.content-card {
  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  /* 超时未处理行：左侧 3px 红色竖条 + 极浅底。改前用 !important 覆盖斑马纹（P17），
     现在去掉斑马纹 + 改由行类名接管，不再与 Element 的层级机制打架
     （类名 is-oversla 沿用不动，纯内部标识，改名只会扩大改动面） */
  :deep(.el-table__body .is-oversla > td.el-table__cell) {
    background-color: var(--state-danger-row-bg);
  }

  :deep(.el-table__body .is-oversla > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 var(--color-danger-icon);
  }

  /* 新派发工单的短时高亮：让演示现场一眼定位刚生成的工单 */
  :deep(.el-table__body .is-highlight > td.el-table__cell) {
    background-color: var(--state-primary-bg);
    transition: background-color var(--dur-slow) var(--ease-std);
  }

  /* link 形态默认 padding 只有 2px：图标按钮补到 8px，鼠标可点区域与「详情」拉开间距 */
  :deep(.wo-copy) {
    padding: var(--sp-2);
    margin-left: var(--sp-1);
  }
}

.sla-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  &__deadline {
    font-size: var(--fs-caption);
    color: var(--text-3);
    font-variant-numeric: tabular-nums;
  }
}
</style>
