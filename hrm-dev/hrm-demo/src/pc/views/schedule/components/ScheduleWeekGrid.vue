<script setup>
import { ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue'
import ScheduleMatrixTable from './ScheduleMatrixTable.vue'

/**
 * 排班周视图卡片（周切换 + 图例 + 矩阵 + 只读提示）
 *
 * 页头与矩阵各自成组件后，这里只剩「周导航 + 图例 + 把矩阵放进来」，
 * 不再持有任何班次判定逻辑（那些在 utils/schedule.js 与 composable 里）。
 */
defineProps({
  rows: { type: Array, default: () => [] },
  dates: { type: Array, default: () => [] },
  shifts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  matrixError: { type: Boolean, default: false },
  canWrite: { type: Boolean, default: false },
  weekLabel: { type: String, default: '' },
  filledCells: { type: Number, default: 0 },
  totalCells: { type: Number, default: 0 },
  today: { type: String, default: '' }
})

const emit = defineEmits(['refresh', 'prevWeek', 'currentWeek', 'nextWeek', 'batchRow', 'batchColumn', 'shiftChange'])
</script>

<template>
  <el-card shadow="never" class="grid-card">
    <template #header>
      <div class="week-bar">
        <div class="week-bar__nav">
          <el-button :icon="ArrowLeft" @click="emit('prevWeek')">上一周</el-button>
          <el-button @click="emit('currentWeek')">本周</el-button>
          <el-button :icon="ArrowRight" @click="emit('nextWeek')">下一周</el-button>
        </div>
        <div class="week-bar__meta">
          <span class="week-bar__range">{{ weekLabel }}</span>
          <!-- 本周排班进度（A10-4）：老板一眼看出这周排完没有，不用逐列数 -->
          <span v-if="totalCells" class="week-bar__progress" :class="{ 'is-incomplete': filledCells < totalCells }">
            已排 {{ filledCells }} / {{ totalCells }} 格
          </span>
        </div>
      </div>
    </template>

    <div class="grid-toolbar">
      <div class="shift-legend">
        <span class="shift-legend__label">班次</span>
        <span v-for="shift in shifts" :key="shift.id" class="shift-legend__item">
          <!-- 色块取接口返回的配色：服务端已校验为 #RRGGBB，与班次管理表格共用同一份值 -->
          <i class="shift-legend__dot" :style="{ backgroundColor: shift.color }" aria-hidden="true" />
          {{ shift.shiftName }} {{ shift.startTime }}-{{ shift.endTime }}
          <span v-if="shift.status !== 1" class="shift-legend__off">已停用</span>
        </span>
      </div>
      <div class="grid-toolbar__right">
        <span class="toolbar-tip">共 {{ rows.length }} 名在岗员工 · 单元格清空表示当天不排班</span>
        <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新排班表" @click="emit('refresh')" />
      </div>
    </div>

    <ScheduleMatrixTable
      :rows="rows"
      :dates="dates"
      :shifts="shifts"
      :loading="loading"
      :matrix-error="matrixError"
      :can-write="canWrite"
      :today="today"
      @refresh="emit('refresh')"
      @batch-row="emit('batchRow', $event)"
      @batch-column="emit('batchColumn', $event)"
      @shift-change="emit('shiftChange', $event)"
    />

    <p v-if="!canWrite" class="grid-card__readonly">
      只读视角：排班保存仅超级管理员可执行，数据范围由服务端收敛为本人驿站
    </p>
  </el-card>
</template>

<style scoped lang="scss">
.grid-card {
  margin-bottom: var(--sp-4);
}

.week-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);

  &__nav {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__range {
    font-size: var(--fs-body);
    font-weight: var(--fw-medium);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }

  &__progress {
    padding: 0 var(--sp-2);
    border-radius: var(--r-full);
    background-color: var(--state-success-bg);
    color: var(--state-success-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    font-variant-numeric: tabular-nums;

    &.is-incomplete {
      background-color: var(--state-warning-bg);
      color: var(--state-warning-fg);
    }
  }
}

.grid-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-bottom: var(--sp-3);

  &__right {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    flex-shrink: 0;
  }
}

.toolbar-tip {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.shift-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-1) var(--sp-4);

  &__label {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-1);
    font-size: var(--fs-caption);
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
  }

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: var(--r-xs);
  }

  &__off {
    color: var(--text-3);
  }
}

.grid-card__readonly {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
