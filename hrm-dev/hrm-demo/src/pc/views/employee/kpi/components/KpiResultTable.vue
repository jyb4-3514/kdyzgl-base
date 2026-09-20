<script setup>
import { computed } from 'vue'
import { KPI_LEVEL } from '@/shared/constants/dict.js'
import StateBlock from '../../../../components/StateBlock.vue'
import StatusTag from '../../../../components/StatusTag.vue'

/**
 * KPI 考核结果表（C4 Organism，B7.3 ③）
 * 按综合得分降序展示（排序由服务端的排名接口给出，前端不再排一次，避免两处口径不一致）。
 *
 * 契约缺口：得分列表接口只返回「按员工聚合」的综合分与达成率，逐指标得分在明细接口里，
 * 若把指标拆成动态列会对每一行各打一次明细请求（N+1），故指标得分的展示收敛到明细抽屉。
 * TODO(扩展): 契约在列表返回 items 或提供批量明细接口后，再补「各指标得分（最多 5 列，超出横向滚动）」。
 */
const props = defineProps({
  list: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  // 是否允许生成考核（ADMIN）：无权限时不渲染引导按钮，而不是点了才发现 403
  canGenerate: { type: Boolean, default: false }
})

const emit = defineEmits(['retry', 'generate', 'open-detail', 'open-employee', 'page-change', 'size-change'])

/** 前三名用排行榜徽标色，4 名之后退回中性（避免出现第 4 种「冠军色」，见 tokens 的 --rank-* 注释） */
const rankTone = (rank) => (rank <= 3 ? `is-top-${rank}` : 'is-rest')

const avgScoreText = computed(() => {
  if (!props.list.length) return '—'
  return (props.list.reduce((sum, row) => sum + Number(row.totalScore || 0), 0) / props.list.length).toFixed(1)
})
</script>

<template>
  <div class="kpi-result">
    <StateBlock v-if="error" variant="error" title="考核结果加载失败" @action="emit('retry')" />

    <StateBlock
      v-else-if="!loading && !list.length"
      variant="empty"
      title="该周期暂无考核结果"
      description="请确认当月考勤与业务数据已闭环，再生成考核"
      :action-text="canGenerate ? '生成本期考核' : ''"
      @action="emit('generate')"
    />

    <template v-else>
      <!-- 加载态保留表头 + 行区 loading：与包裹/工单等既有列表同一语义（B0.2） -->
      <el-table v-loading="loading" :data="list" class="kpi-result__table" row-key="employeeId" stripe>
        <el-table-column label="排名" width="88" align="center">
          <template #default="{ row }">
            <span class="kpi-result__rank" :class="rankTone(row.rank)">{{ row.rank }}</span>
            <span class="kpi-result__rank-total">/ {{ total }}</span>
          </template>
        </el-table-column>

        <el-table-column label="员工" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="emit('open-employee', row)">{{ row.employeeName }}</el-button>
          </template>
        </el-table-column>

        <el-table-column prop="stationName" label="驿站" min-width="120" show-overflow-tooltip />

        <el-table-column prop="metricCount" label="参与指标" width="96" align="right" />

        <el-table-column label="综合得分" width="112" align="right">
          <template #default="{ row }">
            <span class="kpi-result__score">{{ row.totalScore }}</span>
          </template>
        </el-table-column>

        <el-table-column label="达成率" width="104" align="right">
          <template #default="{ row }">{{ Math.round(Number(row.achievementRate || 0) * 100) }}%</template>
        </el-table-column>

        <el-table-column label="等级" width="104">
          <template #default="{ row }">
            <StatusTag :dict="KPI_LEVEL" :value="row.level" :variant="(KPI_LEVEL[row.level] || {}).variant || 'soft'" />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="emit('open-detail', row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="kpi-result__footer">
        <span class="kpi-result__avg">当前页平均得分 {{ avgScoreText }}</span>
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="emit('size-change', $event)"
          @current-change="emit('page-change', $event)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.kpi-result {
  &__table {
    width: 100%;
  }

  &__rank {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 24px;
    height: 24px;
    padding: 0 var(--sp-1);
    border-radius: var(--r-full);
    font-size: var(--fs-caption);
    font-variant-numeric: tabular-nums;

    &.is-top-1 {
      background-color: var(--rank-1-bg);
      color: var(--text-inverse);
    }

    &.is-top-2 {
      background-color: var(--rank-2-bg);
      color: var(--text-inverse);
    }

    &.is-top-3 {
      background-color: var(--rank-3-bg);
      color: var(--text-inverse);
    }

    &.is-rest {
      background-color: var(--rank-rest-bg);
      color: var(--rank-rest-fg);
    }
  }

  &__rank-total {
    margin-left: var(--sp-1);
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__score {
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);
    margin-top: var(--sp-4);
  }

  &__avg {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
