import { computed, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useOrgStore } from '../../../stores/org.js'
import { usePayrollList } from './usePayrollList.js'
import { usePayrollRules } from './usePayrollRules.js'
import { usePayrollObjections } from './usePayrollObjections.js'
import { usePayrollActions } from './usePayrollActions.js'

/**
 * 财务管理页编排：把工资单 / 计薪规则 / 异议处理 + 审核动作串成一份页面级状态
 *
 * 为什么用 reactive 聚合而不是让页面壳逐个解构：四块展开近 40 个绑定，逐个解构会把壳撑到 200 行，
 * 壳就失去了「只看装配」的意义（reactive 会自动脱 ref，模板里直接 page.xxx，v-model 也能写入）。
 * 四个子 composable 仍保持 plain object 返回，便于单测单独调用。
 *
 * 管理端的完整闭环：配计算规则 → 生成草稿 → 批量调整人工项 → 提交审核 → 审核通过并发布 → 处理员工异议。
 */
export function useFinancePage() {
  // 驿站与部门都是跨页基础数据，取数收口到 org store；部门下拉由 store 统一拍平
  const orgStore = useOrgStore()
  const { stations, departmentOptions } = storeToRefs(orgStore)

  const activeTab = ref('payroll')

  // 详情抽屉与生成弹窗仅是页面级开合状态，故留在编排层
  const detailVisible = ref(false)
  const detailId = ref(null)
  const generateVisible = ref(false)

  const list = usePayrollList()
  const rules = usePayrollRules()
  const objections = usePayrollObjections()
  const actions = usePayrollActions({
    query: list.query,
    pendingSubmitCount: list.pendingSubmitCount,
    approvedCount: list.approvedCount,
    fetchList: list.fetchList,
    loadObjections: objections.loadObjections,
    closeDetail: () => {
      detailVisible.value = false
    }
  })

  const filters = computed(() => ({
    month: list.query.month,
    stationId: list.query.stationId,
    status: list.query.status,
    keyword: list.query.keyword
  }))

  /** 筛选栏只回抛变更后的整份筛选对象，此处合并进 query，保持筛选值单一来源 */
  function applyFilters(next) {
    Object.assign(list.query, next)
  }

  function openDetail(row) {
    detailId.value = row.id
    detailVisible.value = true
  }

  const loading = computed(() => list.listLoading.value || rules.ruleLoading.value || objections.objectionLoading.value)
  const headerSub = computed(() => `当前筛选共 ${list.total.value} 份工资单 · 全站按月生成，审核后发布给员工确认`)

  function handleTabChange(name) {
    if (name === 'rule') rules.loadRules()
    if (name === 'objection') objections.loadObjections()
  }

  function reloadAll() {
    if (activeTab.value === 'payroll') list.fetchList()
    else if (activeTab.value === 'rule') rules.loadRules()
    else objections.loadObjections()
  }

  /** 生成草稿会同时影响工资单列表与规则（可能顺手建了规则），故两边都刷 */
  function afterGenerate() {
    list.fetchList()
    rules.loadRules()
  }

  async function loadBaseData() {
    // 部门树失败降级为空下拉；驿站失败仍向上抛（生成工资单的归属驿站依赖它）
    await Promise.all([orgStore.loadStations(), orgStore.loadDepartments().catch(() => [])])
  }

  async function init() {
    await loadBaseData()
    list.fetchList()
  }

  return reactive({
    activeTab,
    stations,
    departmentOptions,
    detailVisible,
    detailId,
    generateVisible,
    filters,
    loading,
    headerSub,
    applyFilters,
    openDetail,
    handleTabChange,
    reloadAll,
    afterGenerate,
    init,
    ...list,
    ...rules,
    ...objections,
    ...actions
  })
}
