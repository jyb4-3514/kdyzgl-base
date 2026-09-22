<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import KpiGauge from '../../components/KpiGauge.vue'
import KpiIndicatorCard from '../../components/KpiIndicatorCard.vue'
import MonthPicker from '../../components/MonthPicker.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { KPI_LEVEL } from '@/shared/constants/dict.js'
import { getKpiScoreDetail } from '../../api/kpi.js'
import { useAuthStore } from '../../stores/auth.js'
import { recentMonths } from '../../utils/format.js'
import { KPI_CODE } from '@/shared/constants/errorCode.js'

/**
 * B7 得分明细（A12-7 的复用约定：同一业务对象两端共用一个页面，只按角色改标题与入口）
 * - 老板端：/boss/kpi/:employeeId（从排名点人进来，看「这分怎么来的」）
 * - 员工端：/staff/kpi（员工号取登录身份，契约侧强制只返回本人）
 *
 * 无考核记录（9204）走空态而不是错误态：那是业务上「这月还没算分」，不是系统故障。
 */
const route = useRoute()
const auth = useAuthStore()

const employeeId = computed(() => Number(route.params.employeeId) || auth.user.id)
const isBossView = computed(() => auth.isAdmin)

const month = ref(recentMonths()[0])
const loading = ref(true)
const error = ref('')
const detail = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await getKpiScoreDetail(employeeId.value, { month: month.value })
  } catch (e) {
    // 9204：该员工该月尚未算分，落空态并由「去生成本期考核」引导（老板端）或等待人事（员工端）
    if (e.code !== KPI_CODE.SCORE_NOT_EXISTS) error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function onMonthChange(value) {
  month.value = value
  load()
}

onMounted(load)
watch(employeeId, load)
</script>

<template>
  <div class="kpi-detail">
    <PageNav :title="isBossView ? '考核明细' : '我的 KPI'" />
    <div class="page page--loose">
      <MonthPicker :model-value="month" label="考核周期" @update:model-value="onMonthChange" />

      <PageState :loading="loading" :error="error" :empty="!detail" :empty-text="`${month} 暂无考核结果`" @retry="load">
        <template #empty-action>
          <p class="tip">
            {{
              isBossView ? '该员工本月尚未算分，可在 KPI 考核页生成本期考核' : '本月考核尚未生成，请等待人事完成算分'
            }}
          </p>
        </template>

        <section class="hero hero--brand score-hero">
          <div class="score-hero__main">
            <p class="hero__sub">{{ detail.employeeName }} · {{ detail.stationName || '总部' }} · {{ detail.month }}</p>
            <p class="score-hero__value tabular-nums">
              {{ detail.totalScore }}<span class="score-hero__unit">分</span>
            </p>
            <div class="score-hero__tags">
              <StatusTag :dict="KPI_LEVEL" :value="detail.level" />
              <span class="hero__chip tabular-nums">第 {{ detail.rank }} 名</span>
            </div>
          </div>
          <KpiGauge :rate="detail.achievementRate" />
        </section>

        <p class="tip tabular-nums">
          综合达成率 {{ Math.round(detail.achievementRate * 100) }}% · 参与指标 {{ detail.metricCount }} 项 · 权重合计
          {{ detail.weightSum }}% · 算分于 {{ detail.calculateTime }}
        </p>

        <div class="section-title">指标明细</div>
        <KpiIndicatorCard v-for="item in detail.items" :key="item.metricKey" :item="item" />
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.score-hero {
  display: flex;
  gap: var(--sp-4);
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);
}

.score-hero__main {
  min-width: 0;
}

.score-hero__value {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-num-lg-boss);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-lg);
}

.score-hero__unit {
  margin-left: 2px;
  font-size: var(--fs-caption);
  font-weight: var(--fw-regular);
}

.score-hero__tags {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  margin-top: var(--sp-2);
}
</style>
