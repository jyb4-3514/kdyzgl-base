<script setup>
import StateBlock from '../../../components/StateBlock.vue'
import { dayFilledCountOf, isUnavailableShift, shiftColorOf } from '../utils/schedule.js'

/**
 * 排班矩阵表（员工行 × 日期列）
 *
 * 单元格直接放班次下拉，而不是「点开弹层再选」：排班是高频批量操作，
 * 一周 7 天 × 全站员工要一次性铺完，每次多一次弹层点击会翻倍操作成本。
 * 改动只回抛 shift-change，落值与增量判定交给 composable，组件里不藏第二份 dirty 逻辑。
 */
const props = defineProps({
  rows: { type: Array, default: () => [] },
  dates: { type: Array, default: () => [] },
  shifts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  matrixError: { type: Boolean, default: false },
  canWrite: { type: Boolean, default: false },
  today: { type: String, default: '' }
})

const emit = defineEmits(['refresh', 'batchRow', 'batchColumn', 'shiftChange'])

const WEEK_LABEL = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const colorOf = (shiftId) => shiftColorOf(props.shifts, shiftId)
const unavailableOf = (shiftId) => isUnavailableShift(props.shifts, shiftId)
const filledOf = (index) => dayFilledCountOf(props.rows, index)

/** 单元格提示：停用/已删班次必须讲清「保存会被 9106 拒」，避免保存时才被打回 */
function cellTitle(row, index, date) {
  if (unavailableOf(row.days[index].shiftId)) {
    return `${row.employeeName} ${date} 的班次已停用或删除，保存时会被服务端拒绝（9106），请重新选择`
  }
  return `${row.employeeName} ${date} 班次`
}
</script>

<template>
  <StateBlock v-if="matrixError" variant="error" title="排班表加载失败" @action="emit('refresh')" />

  <StateBlock
    v-else-if="!loading && !rows.length"
    variant="empty"
    title="该驿站暂无在岗员工"
    description="请先在员工管理中为本驿站分配在岗员工后再排班"
  />

  <el-table v-else v-loading="loading" :data="rows" border :row-key="(row) => row.employeeId">
    <el-table-column label="员工" min-width="150" fixed="left">
      <template #default="{ row }">
        <div class="employee-cell">
          <span class="employee-name">{{ row.employeeName }}</span>
          <!-- 行内批量锚点（B2.3）：hover 才出现，避免整表被按钮压满；44×44 热区，键盘可 Tab 到 -->
          <button
            v-if="canWrite"
            type="button"
            class="batch-anchor"
            :aria-label="`批量设置 ${row.employeeName} 整周班次`"
            @click="emit('batchRow', row.employeeId)"
          >
            全周
          </button>
        </div>
      </template>
    </el-table-column>
    <!-- days 与 dates 由服务端按同一周日期数组生成，下标一一对应，这里按下标取可省掉 140 次日期查找 -->
    <el-table-column v-for="(date, index) in dates" :key="date" :min-width="150">
      <template #header>
        <div class="day-head" :class="{ 'is-today': date === today, 'is-incomplete': filledOf(index) < rows.length }">
          <span class="day-head__week">{{ WEEK_LABEL[index] }}</span>
          <span class="day-head__date">{{ date.slice(5) }}</span>
          <span v-if="date === today" class="day-head__today">今天</span>
          <!-- 列头批量锚点（B2.3）：该天未排满时给提示色，hover 出现「全员」 -->
          <button
            v-if="canWrite"
            type="button"
            class="batch-anchor batch-anchor--head"
            :aria-label="`批量设置 ${date} 全员班次`"
            @click="emit('batchColumn', date)"
          >
            全员
          </button>
        </div>
      </template>
      <template #default="{ row }">
        <div
          class="cell-shift"
          :class="{ 'is-stale': unavailableOf(row.days[index].shiftId) }"
          :title="cellTitle(row, index, date)"
          :style="{ '--cell-shift-color': colorOf(row.days[index].shiftId) }"
        >
          <!-- TODO(扩展): 支持「选中单元格 → 数字键直接赋班次 / Ctrl+C·Ctrl+V 复制列」的键盘录入（A10-2），
               排班是同构重复操作，键盘录入比下拉快 5-10 倍；当前仅支持下拉 -->
          <el-select
            :model-value="row.days[index].shiftId"
            size="small"
            clearable
            placeholder="未排班"
            :disabled="!canWrite"
            @change="emit('shiftChange', { row, index, shiftId: $event })"
          >
            <el-option
              v-for="shift in shifts"
              :key="shift.id"
              :label="shift.shiftName"
              :value="shift.id"
              :disabled="shift.status !== 1"
            >
              <span class="option-shift">
                <i class="option-shift__dot" :style="{ backgroundColor: shift.color }" aria-hidden="true" />
                {{ shift.shiftName }}
                <span v-if="shift.status !== 1" class="option-shift__off">已停用</span>
              </span>
            </el-option>
          </el-select>
          <!-- 已停用/已删除班次的历史遗留格（A10-5）：提交前就标出，避免保存时才被 9106 打回 -->
          <span v-if="unavailableOf(row.days[index].shiftId)" class="cell-shift__stale">已停用</span>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped lang="scss">
.day-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);

  &__week {
    color: var(--text-2);
  }

  &__date {
    color: var(--text-3);
    font-variant-numeric: tabular-nums;
  }

  /* 今天所在列：文字改用主色 + 加粗，扫读时能立刻定位 */
  &.is-today .day-head__week {
    color: var(--color-primary);
    font-weight: var(--fw-semibold);
  }

  &__today {
    padding: 0 var(--sp-1);
    border-radius: var(--r-xs);
    background-color: var(--state-primary-bg);
    color: var(--state-primary-fg);
  }

  &.is-incomplete .day-head__week {
    color: var(--state-warning-fg);
  }
}

.cell-shift {
  padding-left: var(--sp-2);
  border-left: 3px solid var(--cell-shift-color, transparent);

  /* 已停用 / 已删除班次的历史遗留格：警告色左条 + 虚线，不只靠颜色区分（A10-5） */
  &.is-stale {
    border-left-style: dashed;
  }

  &__stale {
    margin-left: var(--sp-1);
    padding: 0 var(--sp-1);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-xs);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    white-space: nowrap;
  }
}

.option-shift {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);

  &__dot {
    width: 10px;
    height: 10px;
    border-radius: var(--r-xs);
  }

  &__off {
    color: var(--text-3);
  }
}

.employee-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.employee-name {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

/* 行内 / 列头批量锚点：默认隐形，hover 或键盘聚焦时才出现，不干扰矩阵扫读 */
.batch-anchor {
  box-sizing: border-box;
  min-width: 28px;
  height: 44px;
  padding: 0 var(--sp-2);
  border: 0;
  border-radius: var(--r-xs);
  background: transparent;
  color: var(--color-primary-strong);
  font-size: var(--fs-caption);
  font-family: inherit;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--dur-fast) var(--ease-std);

  &:hover {
    background-color: var(--state-primary-bg);
  }

  &--head {
    height: 24px;
  }
}

:deep(.el-table__row:hover .batch-anchor),
:deep(.el-table__header-wrapper th:hover .batch-anchor) {
  opacity: 1;
}

.batch-anchor:focus-visible {
  opacity: 1;
}
</style>
