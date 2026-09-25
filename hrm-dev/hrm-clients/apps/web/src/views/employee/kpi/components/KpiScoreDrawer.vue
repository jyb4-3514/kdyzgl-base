<script setup>
import { computed, ref, watch } from 'vue'
import { KPI_DIRECTION, KPI_LEVEL, KPI_METRIC_TYPE } from '@kdyzgl/shared/constants/dict.js'
import { getKpiScoreDetail } from '../../../../api/kpi.js'
import KpiGauge from '../../../../components/KpiGauge.vue'
import StateBlock from '../../../../components/StateBlock.vue'
import StatusTag from '../../../../components/StatusTag.vue'

/**
 * KPI 得分明细抽屉（B7.3 ④）
 * 只负责「解释这一分怎么来的」：逐指标列目标值 / 实际值 / 达成率 / 单项得分 / 加权得分，
 * 综合分的口径由服务端给出，前端不重算，避免排名与总分两处口径漂移。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  employeeId: { type: Number, default: null },
  month: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const error = ref(false)
const detail = ref(null)

async function load() {
  if (!props.employeeId || !props.month) return
  loading.value = true
  error.value = false
  detail.value = null
  try {
    detail.value = await getKpiScoreDetail(props.employeeId, { month: props.month })
  } catch (e) {
    // 9204（该员工该月无评分记录）不是故障，是正常的业务空态；其余错误进错误态并给重试
    if (e && e.code === 9204) {
      detail.value = null
    } else {
      error.value = true
    }
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.employeeId, props.month],
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

const items = computed(() => (detail.value ? detail.value.items : []))

/** 达成率条配色（B7.5）：达标用主色，未达标用警告，严重不足（<60%）用危险 */
const barTone = (rate) => {
  const value = Number(rate || 0)
  if (value >= 1) return 'is-full'
  if (value < 0.6) return 'is-low'
  return 'is-part'
}

const rateText = (rate) => `${Math.round(Number(rate || 0) * 100)}%`
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="KPI 得分明细"
    :size="`min(var(--drawer-w), 92vw)`"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <StateBlock v-if="error" variant="error" title="考核明细加载失败" @action="load" />

    <StateBlock
      v-else-if="!loading && !detail"
      variant="empty"
      title="该员工本期暂无考核结果"
      description="可能因当月无考勤数据未纳入考核，可先生成本期考核"
    />

    <div v-else v-loading="loading" class="kpi-detail">
      <template v-if="detail">
        <div class="kpi-detail__hero">
          <KpiGauge :rate="detail.achievementRate" label="指标平均达成率" />
          <div class="kpi-detail__meta">
            <p class="kpi-detail__name">{{ detail.employeeName }}</p>
            <p class="kpi-detail__sub">{{ detail.stationName }} · {{ detail.month }}</p>
            <p class="kpi-detail__score">
              <span class="kpi-detail__score-num">{{ detail.totalScore }}</span>
              <span class="kpi-detail__score-unit">分</span>
              <StatusTag
                :dict="KPI_LEVEL"
                :value="detail.level"
                :variant="(KPI_LEVEL[detail.level] || {}).variant || 'soft'"
              />
            </p>
            <p class="kpi-detail__sub">
              排名第 {{ detail.rank }} 名 · 指标 {{ detail.metricCount }} 项 · 权重合计 {{ detail.weightSum }}%
            </p>
            <p class="kpi-detail__sub">算分时间 {{ detail.calculateTime }}</p>
          </div>
        </div>

        <el-table :data="items" class="kpi-detail__table" size="small">
          <el-table-column prop="metricName" label="指标" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.metricName }}</span>
              <span class="kpi-detail__type">{{
                KPI_METRIC_TYPE[row.metricType] ? KPI_METRIC_TYPE[row.metricType].label : row.metricType
              }}</span>
            </template>
          </el-table-column>
          <el-table-column label="权重" width="64" align="right">
            <template #default="{ row }">{{ row.weight }}%</template>
          </el-table-column>
          <el-table-column label="目标值" width="88" align="right">
            <template #default="{ row }">
              {{ row.targetValue }}{{ row.unit }}
              <span class="kpi-detail__dir">{{
                KPI_DIRECTION[row.direction] ? KPI_DIRECTION[row.direction].label : ''
              }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实际值" width="88" align="right">
            <template #default="{ row }">{{ row.actualValue }}{{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="达成率" min-width="132">
            <template #default="{ row }">
              <!-- 达成率同时给「条 + 文字」：颜色不是唯一通道（SC 1.4.1） -->
              <span class="kpi-detail__bar" :class="barTone(row.achievementRate)">
                <span class="kpi-detail__bar-track">
                  <span
                    class="kpi-detail__bar-fill"
                    :style="{ width: `${Math.min(100, Number(row.achievementRate || 0) * 100)}%` }"
                  />
                </span>
                <span class="kpi-detail__bar-text">{{ rateText(row.achievementRate) }}</span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="单项得分" width="80" align="right">
            <template #default="{ row }">{{ row.score }}</template>
          </el-table-column>
          <el-table-column label="加权分" width="80" align="right">
            <template #default="{ row }">
              <span class="kpi-detail__weighted">{{ row.weightedScore }}</span>
            </template>
          </el-table-column>
        </el-table>

        <p class="kpi-detail__hint">
          综合得分 = Σ(单项得分 × 权重) ÷ Σ(适用指标权重)；达成率为各指标达成率的算术平均。 评分规则：{{
            items[0] ? items[0].scoreModeLabel : '—'
          }}（逐项规则见上方「评分规则」列口径）。
        </p>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.kpi-detail {
  min-height: 200px;

  &__hero {
    display: flex;
    align-items: center;
    gap: var(--sp-6);
    padding: var(--sp-4);
    margin-bottom: var(--sp-4);
    border: 1px solid var(--border-line);
    border-radius: var(--r-md);
    background-color: var(--surface-sub);
  }

  &__meta {
    min-width: 0;
  }

  &__name {
    margin: 0;
    font-size: var(--fs-h2);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-h2);
    color: var(--text-1);
  }

  &__sub {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__score {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin: var(--sp-2) 0 0;
  }

  &__score-num {
    font-size: var(--fs-num-lg);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-num-lg);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__score-unit {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  &__table {
    width: 100%;
  }

  &__type,
  &__dir {
    display: block;
    font-size: var(--fs-micro);
    line-height: var(--lh-micro);
    color: var(--text-3);
  }

  &__bar {
    display: flex;
    align-items: center;
    gap: var(--sp-2);

    --rate-tone: var(--color-primary-icon);

    &.is-part {
      --rate-tone: var(--color-warning-icon);
    }

    &.is-low {
      --rate-tone: var(--color-danger-icon);
    }
  }

  &__bar-track {
    flex: 1;
    height: 6px;
    border-radius: var(--r-full);
    background-color: var(--gauge-track);
    overflow: hidden;
  }

  &__bar-fill {
    display: block;
    height: 100%;
    border-radius: var(--r-full);
    background-color: var(--rate-tone);
  }

  &__bar-text {
    min-width: 40px;
    font-size: var(--fs-caption);
    color: var(--text-2);
    font-variant-numeric: tabular-nums;
  }

  &__weighted {
    font-weight: var(--fw-medium);
    color: var(--text-1);
  }

  &__hint {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
