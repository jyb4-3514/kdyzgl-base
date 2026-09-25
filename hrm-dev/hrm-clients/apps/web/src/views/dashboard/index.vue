<template>
  <div class="dashboard">
    <PageHeader title="数据看板" :sub="page.headerSub" :loading="page.loading">
      <template #actions>
        <el-button :icon="Refresh" @click="page.loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 首屏主指标：今日入库 / 今日取件 / 在库待取 / 异常件，可点下钻到包裹列表 -->
    <DashboardHeroCards
      :cards="page.heroCards"
      :loading="page.parcelLoading"
      :error="page.parcelError"
      @select="go"
    />

    <p class="dash-caption">环比口径：今日值 vs 近 7 天日均值（数据契约暂无同比字段，前端本地推导）</p>

    <!-- 次级指标条：包裹 / 同步 / 工单各出一项，单项失败只影响自己那一格（5.2 第 7 条） -->
    <DashboardInlineCards :cards="page.inlineCards" />

    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="16">
        <DashboardTrendBlock
          :days="page.trendDays"
          :data="page.trend"
          :loading="page.trendLoading"
          :error="page.trendError"
          @select-days="page.setTrendDays"
          @retry="page.loadTrend"
        />
      </el-col>
      <el-col :xs="24" :lg="8">
        <DashboardSyncHealthBlock
          :health="page.syncHealth"
          :loading="page.healthLoading"
          :error="page.healthError"
          @retry="page.loadSyncHealth"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dash-row">
      <el-col :xs="24" :lg="14">
        <DashboardRankBlock
          :rows="page.rankRows"
          :sort="page.rankSort"
          :label="page.rankLabel"
          :loading="page.rankLoading"
          :error="page.rankError"
          @select-sort="page.setRankSort"
          @retry="page.loadRanking"
        />
      </el-col>
      <el-col :xs="24" :lg="10">
        <DashboardWorkOrderBlock
          :metrics="page.woMetrics"
          :over-sla-count="page.workOrder.overSlaCount"
          :loading="page.woLoading"
          :error="page.woError"
          @retry="page.loadWorkOrder"
          @go="goWorkOrder"
        />
      </el-col>
    </el-row>

    <!-- 一期口径指标降为末块：看板重心是包裹/同步/工单，组织规模默认折叠 -->
    <DashboardOrgBlock
      :cards="page.baseCards"
      :summary="page.summary"
      :summary-text="page.orgSummaryText"
      :error="page.baseError"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import DashboardHeroCards from './components/DashboardHeroCards.vue'
import DashboardInlineCards from './components/DashboardInlineCards.vue'
import DashboardTrendBlock from './components/DashboardTrendBlock.vue'
import DashboardSyncHealthBlock from './components/DashboardSyncHealthBlock.vue'
import DashboardRankBlock from './components/DashboardRankBlock.vue'
import DashboardWorkOrderBlock from './components/DashboardWorkOrderBlock.vue'
import DashboardOrgBlock from './components/DashboardOrgBlock.vue'
import { useDashboardData } from './composables/useDashboardData.js'

/**
 * 扩展版看板（T14，demo-ui-redesign.md 5.2）页面壳
 *
 * 重排理由：改前一等公民是一期的员工/部门/驿站 4 个数字，包裹/同步/工单反而靠后，
 * 与"快递驿站"的业务重心错位；本次把包裹指标提到首屏，一期指标降为可折叠末块。
 * 数据来源与口径完全沿用改造前的接口调用，只改呈现 —— 六路取数、四态与竞态守卫收在 useDashboardData。
 */
const router = useRouter()
const page = useDashboardData()

/** 下钻目标由 model 的 HERO_META 给出，壳只负责真实跳转 */
const go = (to) => router.push(to)
const goWorkOrder = () => router.push('/work-order')

onMounted(() => page.loadAll())
</script>

<style scoped lang="scss">
.dashboard {
  .dash-row {
    margin-bottom: var(--sp-4);
  }

  .dash-caption {
    margin: calc(-1 * var(--sp-2)) 0 var(--sp-4);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
