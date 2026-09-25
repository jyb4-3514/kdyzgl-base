<script setup>
import KpiGauge from './KpiGauge.vue'
import KpiIndicatorCard from './KpiIndicatorCard.vue'
import MonthPicker from './MonthPicker.vue'
import PageNav from './PageNav.vue'
import PageState from './PageState.vue'
import StatusTag from './StatusTag.vue'
import { KPI_LEVEL } from '@kdyzgl/shared/constants/dict.js'

/**
 * B7 考核明细中立页（A12-7 复用约定）—— 员工端 /staff/kpi 与管理端 /boss/kpi/:employeeId 共用
 * （ADR §3.5 第 15 项，B-3 裁定取 ①：提升为 packages/shared/ui 中立页，消除跨域直引）
 *
 * 中立约束：数据一律 props 注入、交互一律 emits 上抛，**禁 import stores/ api/ mock**，
 * 也不 import Element Plus / Vant 运行时（模板里的 <van-*> 由宿主 App 全局注册）。
 * 取数（getKpiScoreDetail）、员工号来源（登录身份 vs 路由参数）与角色判定由宿主容器承担。
 *
 * 无考核记录（9204）走空态而不是错误态：那是业务上「这月还没算分」，不是系统故障。
 */
defineProps({
  /** 考核明细（宿主取数后注入；null = 无数据 → 空态） */
  detail: { type: Object, default: null },
  /** 当前账期 'YYYY-MM' */
  month: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** 导航标题：管理端「考核明细」/ 员工端「我的 KPI」 */
  title: { type: String, default: '我的 KPI' },
  /** 管理端视角：仅影响空态引导文案 */
  isBossView: { type: Boolean, default: false }
})

const emit = defineEmits(['update:month', 'retry'])
</script>

<template>
  <div class="kpi-detail">
    <PageNav :title="title" />
    <div class="page page--loose">
      <MonthPicker :model-value="month" label="考核周期" @update:model-value="emit('update:month', $event)" />

      <PageState
        :loading="loading"
        :error="error"
        :empty="!detail"
        :empty-text="`${month} 暂无考核结果`"
        @retry="emit('retry')"
      >
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
