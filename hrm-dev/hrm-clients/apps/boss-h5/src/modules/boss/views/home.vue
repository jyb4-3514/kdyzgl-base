<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import HomeQuickGrid from '@/components/HomeQuickGrid.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import { getAttendanceSummary } from '@/api/attendance.js'
import { getDashboardSummary } from '@/api/dashboard.js'
import { getWorkOrders } from '@/api/workOrder.js'
import { BOSS_QUICK_ENTRIES } from '@/constants/quickEntries.js'
import { useTodoStore } from '@/stores/todo.js'
import { clockText, numberText, shortDateText } from '@/utils/format.js'

/**
 * B2 经营总览（ADMIN · 全局数据）
 * 指标口径见 demo-design.md 7.4.7：本站/全局的收敛由 Mock 层按角色强制完成，前端只负责展示与下钻。
 * 待办类数字统一由 stores/todo.js 提供：角标、消息页分组、Hero 总数共用一份，避免同一数字三处各算（A1-1 原则 4）。
 *
 * MVP 裁剪：包裹指标卡（今日入库/取件/在库/异常）、近 7 天包裹趋势、同步健康度、驿站 TOP3
 * 四块随「包裹族」整体下架；首页保留 Hero（今日经营 + 待办总数）、工单超时提示、快捷宫格与组织规模。
 */
const router = useRouter()
const todo = useTodoStore()

const loading = ref(true)
const error = ref('')
const team = ref(null)
const overdueUnhandled = ref(0)
/** 今日考勤异常项数（宫格「考勤概览」）：null 表示取数失败，角标不渲染 */
const attendanceAbnormal = ref(null)
/** 一期指标对管理员是次要信息，默认折叠，避免占据首屏视线 */
const showOrg = ref(false)

const dateText = shortDateText()
const updatedText = `数据截止 ${clockText()}`

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [dashboard, overduePage, attendance] = await Promise.all([
      getDashboardSummary(),
      // 只要总数，取 1 条即可（避免为看一个数字拉回 120 条工单）
      getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 1 }),
      // 宫格数据属「锦上添花」，一律独立降级为 null，由宫格按项显示「—」或无角标（B4-2 硬规则 1）
      getAttendanceSummary().catch(() => null)
    ])
    team.value = dashboard
    overdueUnhandled.value = overduePage.total
    attendanceAbnormal.value = attendance
      ? attendance.lateCount + attendance.earlyLeaveCount + attendance.absentCount
      : null
    // 待办计数在首页预取（C5-5），供宫格角标与消息 Tab 角标共用
    await todo.refresh()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const alertText = computed(() =>
  overdueUnhandled.value ? `${overdueUnhandled.value} 条工单超时未处理` : ''
)

/** 「异常预警」总数 = 超时未处理工单（MVP 裁剪后仅剩这一项口径） */
const alertTotal = computed(() => overdueUnhandled.value)

/** 宫格实时值：计数型给数值（null=未知），状态型给文案（null=取数失败显示 `—`）。
 *  v1.1：删除 4 类审批 key（makeups/payrolls/flows/leaves）—— 入口统一由审批中心承接（设计 ②.2.1）。 */
const quickData = computed(() => ({
  orders: todo.counts.orders ?? null,
  attendance: attendanceAbnormal.value,
  alerts: alertTotal.value,
  // 审批中心角标（派生，不含工单）：任一子组未知即 null → 不渲染角标
  approvals: todo.approvalTotal,
  // 驿站管理副行：复用已加载的 dashboard.stationTotal，不新增请求（设计 ②.3）
  stations: team.value ? `共 ${team.value.stationTotal} 个` : null
}))
</script>

<template>
  <div class="page">
    <!-- 加载态：Hero 占位，数据到达时版面不跳（B2 三态） -->
    <template v-if="loading">
      <div class="skeleton-block sk-hero" />
    </template>

    <PageState v-else :error="error" @retry="load">
      <!-- Hero：深蓝灰底色建立「经营报告」心智，与员工端的品牌蓝 Hero 明确区分（3.2） -->
      <section class="hero hero--deep">
        <!-- 品牌行落在页面主张之上（品牌 → 页面两级）；深底用 --text-inverse，白对深底 14.679:1 -->
        <p class="hero__brand">驿站精灵</p>
        <div class="flex-between">
          <span class="hero__title">今日经营</span>
          <!-- 口径不可切换：不做成像按钮的 chip，避免用户反复点击（A12-6）；真的开放切换时再改回控件 -->
          <span class="hero__scope" title="当前账号固定为全域口径，暂不支持切换">口径：全域</span>
        </div>
        <p class="hero__sub">{{ dateText }} · {{ updatedText }}</p>
        <!-- 待办总数：待办取数全失败时显示 ···，不用 0 冒充「没有待办」（B4-2 硬规则 2） -->
        <p class="hero__sub">今日待处理 {{ todo.known ? numberText(todo.total) : '···' }} 条</p>
      </section>

      <van-notice-bar
        v-if="alertText"
        class="notice"
        left-icon="warning-o"
        :text="alertText"
        wrapable
        color="var(--color-danger)"
        background="var(--color-danger-surface)"
        @click="router.push('/boss/alerts')"
      />
      <van-notice-bar
        v-else
        class="notice"
        left-icon="passed"
        text="暂无超时未处理工单"
        color="var(--color-success)"
        background="var(--color-success-surface)"
      />
    </PageState>

    <!-- 快捷功能宫格：全部带实时数据（B5-1）；排在提示条之后 —— 提示条刚说完「N 条工单超时未处理」，紧接着就是处理入口。
         宫格独立于 PageState 骨架、不被整块骨架替换：项名先渲染，仅数据位随 loading 处于加载态（B4-2 · P1-3）。 -->
    <HomeQuickGrid
      v-if="!error"
      :loading="loading"
      :entries="BOSS_QUICK_ENTRIES"
      :data="quickData"
      hint="待办与概览在前"
    />

    <!-- 组织规模：一期指标移到末位并默认折叠（B2） -->
    <template v-if="!loading && !error">
      <button type="button" class="org-head" :aria-expanded="showOrg" @click="showOrg = !showOrg">
        <span>组织规模（一期）</span>
        <van-icon :name="showOrg ? 'arrow-up' : 'arrow-down'" aria-hidden="true" />
      </button>
      <van-cell-group v-show="showOrg" inset>
        <van-cell title="员工总数" :value="`${team.employeeTotal} 人`" />
        <van-cell title="驿站总数" :value="`${team.stationTotal} 个`" />
        <van-cell title="部门总数" :value="`${team.departmentTotal} 个`" />
        <van-cell title="今日登录" :value="`${team.todayLoginCount} 人`" />
      </van-cell-group>
    </template>
  </div>
</template>

<style scoped>
.sk-hero {
  height: 88px;
  margin-top: var(--sp-4);
}

/* 品牌行：与 .hero__title 同一左边缘，间距 --sp-1（§13.7） */
.hero__brand {
  margin: 0 0 var(--sp-1);
  font-size: var(--fs-caption);
  font-weight: var(--fw-medium);
  line-height: var(--lh-caption);
  color: var(--text-inverse);
}

/* 不可交互的口径说明：纯文字表达，去掉 chip 的底与边（A12-6） */
.hero__scope {
  flex: none;
  font-size: var(--fs-caption);
  color: rgba(255, 255, 255, 0.82);
}

.org-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  margin-top: var(--sp-6);
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
  background: none;
  border: none;
}
</style>
