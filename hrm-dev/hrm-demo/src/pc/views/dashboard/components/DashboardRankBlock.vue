<script setup>
import { computed } from 'vue'
import DashboardPanel from './DashboardPanel.vue'
import StateBlock from '../../../components/StateBlock.vue'
import { RANK_SORTS } from '../model/dashboardMeta.js'

/**
 * 驿站排行 TOP5 区块
 * 排序口径切换同趋势图：不持有状态，选中即抛给容器重取排行数据。
 */
const props = defineProps({
  rows: { type: Array, default: () => [] },
  sort: { type: String, default: 'parcelTotal' },
  label: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['select-sort', 'retry'])

const model = computed({
  get: () => props.sort,
  set: (value) => emit('select-sort', value)
})
</script>

<template>
  <DashboardPanel title="驿站排行 TOP5">
    <template #actions>
      <el-radio-group v-model="model" size="small">
        <el-radio-button v-for="option in RANK_SORTS" :key="option.value" :value="option.value">
          {{ option.label }}
        </el-radio-button>
      </el-radio-group>
    </template>

    <el-skeleton v-if="loading" :rows="5" animated />
    <StateBlock v-else-if="error" variant="error" title="驿站排行加载失败" @action="emit('retry')" />
    <StateBlock v-else-if="!rows.length" variant="empty" title="暂无排行数据" />
    <!-- 排行用行式布局而非 el-table：行内要放进度条与副信息，表格列宽会被挤成一行省略号 -->
    <ol v-else class="rank">
      <li v-for="row in rows" :key="row.id" class="rank__row">
        <span class="rank__badge" :class="row.rank <= 3 ? `rank-${row.rank}` : 'rank-rest'">{{ row.rank }}</span>
        <span class="rank__name">{{ row.stationName }}</span>
        <span class="rank__value">{{ row.mainValue }}<i class="rank__unit">{{ row.mainUnit }}</i></span>
        <span class="rank__bar" :aria-label="`占最高值的 ${row.percent}%`">
          <i class="rank__bar-fill" :class="`is-${sort}`" :style="{ width: `${row.percent}%` }" />
        </span>
        <span class="rank__sub">{{ row.sub }}</span>
      </li>
    </ol>
    <p class="block-caption">当前按{{ label }}排序；进度条为相对当前列表最大值的百分比，非绝对值。</p>
  </DashboardPanel>
</template>

<style scoped lang="scss">
.block-caption {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.rank {
  margin: 0;
  padding: 0;
  list-style: none;

  &__row {
    display: grid;
    grid-template-columns: 24px auto 1fr auto;
    grid-template-areas:
      'badge name value value'
      'bar bar bar sub';
    align-items: center;
    gap: var(--sp-1) var(--sp-2);
    padding: var(--sp-2) 0;
    border-bottom: 1px solid var(--border-line);

    &:last-child {
      border-bottom: none;
    }
  }

  &__badge {
    grid-area: badge;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: var(--r-xs);
    font-size: var(--fs-caption);
    font-weight: var(--fw-medium);
    font-variant-numeric: tabular-nums;

    // 1/2/3 名用实底白字：改前 PC 用红色表示冠军，与"危险"语义冲突（修 P18）
    &.rank-1 {
      background-color: var(--rank-1-bg);
      color: var(--text-inverse);
    }

    &.rank-2 {
      background-color: var(--rank-2-bg);
      color: var(--text-inverse);
    }

    &.rank-3 {
      background-color: var(--rank-3-bg);
      color: var(--text-inverse);
    }

    &.rank-rest {
      background-color: var(--rank-rest-bg);
      color: var(--rank-rest-fg);
    }
  }

  &__name {
    grid-area: name;
    font-size: var(--fs-body-strong);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }

  &__value {
    grid-area: value;
    justify-self: end;
    font-size: var(--fs-num-sm);
    font-weight: var(--fw-semibold);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__unit {
    margin-left: 2px;
    font-size: var(--fs-caption);
    font-weight: var(--fw-regular);
    color: var(--text-3);
    font-style: normal;
  }

  &__bar {
    grid-area: bar;
    height: 8px;
    border-radius: var(--r-full);
    background-color: var(--surface-sunken);
    overflow: hidden;
  }

  &__bar-fill {
    display: block;
    height: 100%;
    border-radius: var(--r-full);

    // 进度条色随口径（三端一致，见 6.3）
    &.is-parcelTotal {
      background-color: var(--chart-inbound);
    }

    &.is-pickupRate {
      background-color: var(--chart-pickup);
    }

    &.is-abnormalRate {
      background-color: var(--color-danger-icon);
    }
  }

  &__sub {
    grid-area: sub;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
