<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import KpiDetail from '@kdyzgl/shared/ui/KpiDetail.vue'
import { KPI_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { getKpiScoreDetail } from '../../api/kpi.js'
import { useAuthStore } from '../../stores/auth.js'
import { recentMonths } from '../../utils/format.js'

/**
 * B7 得分明细容器（A12-7 的复用约定：同一业务对象两端共用一个页面，只按角色改标题与入口）
 * - 管理端：/boss/kpi/:employeeId（从排名点人进来，看「这分怎么来的」）
 * - 员工端：/staff/kpi（员工号取登录身份，契约侧强制只返回本人）
 *
 * B-3（ADR §3.5 第 15 项）：页面本体已提升为 @kdyzgl/shared/ui/KpiDetail.vue 中立页，
 * 本容器只做「员工号来源 + 取数 → props 注入 / 事件回流」，中立页不 import stores/api。
 * 无考核记录（9204）走空态而不是错误态：那是业务上「这月还没算分」，不是系统故障。
 */
const route = useRoute()
const auth = useAuthStore()

const employeeId = computed(() => Number(route.params.employeeId) || auth.user.id)
const isBossView = computed(() => auth.isAdmin)
const title = computed(() => (isBossView.value ? '考核明细' : '我的 KPI'))

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
    // 9204：该员工该月尚未算分，落空态并由「去生成本期考核」引导（管理端）或等待人事（员工端）
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
  <KpiDetail
    :detail="detail"
    :month="month"
    :loading="loading"
    :error="error"
    :title="title"
    :is-boss-view="isBossView"
    @update:month="onMonthChange"
    @retry="load"
  />
</template>
