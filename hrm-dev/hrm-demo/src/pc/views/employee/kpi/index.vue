<template>
  <div class="kpi-page">
    <PageHeader title="KPI 考核" :sub="headerSub" :loading="loading">
      <template #actions>
        <!-- 生成考核只对 ADMIN 开放（契约 roles:['ADMIN']）；模板编辑同理 -->
        <el-button v-if="activeTab === 'result'" type="primary" :icon="MagicStick" @click="handleGenerate"
          >生成本期考核</el-button
        >
        <el-button v-else type="primary" :icon="EditPen" @click="templateVisible = true">编辑指标模板</el-button>
        <el-button :icon="Refresh" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="kpi-tabs">
      <el-tab-pane name="result" label="考核结果" />
      <el-tab-pane name="metric" label="指标模板" />
    </el-tabs>

    <template v-if="activeTab === 'result'">
      <el-card shadow="never" class="filter-card">
        <el-form inline>
          <el-form-item label="考核周期">
            <el-date-picker
              v-model="query.month"
              type="month"
              value-format="YYYY-MM"
              :clearable="false"
              placeholder="选择月份"
              @change="handleFilterChange"
            />
          </el-form-item>
          <el-form-item label="驿站">
            <el-select
              v-model="query.stationId"
              clearable
              placeholder="全部驿站"
              style="width: 160px"
              @change="handleFilterChange"
            >
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="员工">
            <!-- 契约的排名接口不支持按人过滤，故选中员工后改走得分列表接口按 employeeId 收口 -->
            <el-select
              v-model="query.employeeId"
              filterable
              clearable
              placeholder="全部员工"
              style="width: 180px"
              @change="handleFilterChange"
            >
              <el-option
                v-for="item in employees"
                :key="item.id"
                :label="`${item.realName}（${item.stationName || '—'}）`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </el-card>

      <MiniStats
        :items="summaryCards"
        :data="summary"
        :cols="4"
        :loading="summaryLoading"
        :error="summaryError"
        :hint="summaryHint"
        @retry="loadSummary"
      />

      <el-card shadow="never" class="content-card">
        <KpiResultTable
          :list="list"
          :total="total"
          :loading="listLoading"
          :error="listError"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          :can-generate="true"
          @retry="fetchList"
          @generate="handleGenerate"
          @open-detail="handleOpenDetail"
          @open-employee="handleOpenEmployee"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </el-card>
    </template>

    <el-card v-else shadow="never" class="content-card">
      <StateBlock v-if="metricError" variant="error" title="指标模板加载失败" @action="loadMetrics" />

      <template v-else>
        <!-- 权重合计条与编辑器同一套三态口径：超出/不足都必须一眼看见（B7.4） -->
        <div class="kpi-page__sum" :class="`is-${weightState.tone}`">
          <span class="kpi-page__sum-label">启用指标权重合计</span>
          <span class="kpi-page__sum-value">{{ weightSum }}%</span>
          <span class="kpi-page__sum-track"
            ><span class="kpi-page__sum-fill" :style="{ width: `${Math.min(100, weightSum)}%` }"
          /></span>
          <span class="kpi-page__sum-text">{{ weightState.text }}</span>
        </div>

        <el-table v-loading="metricLoading" :data="metrics" row-key="id">
          <el-table-column prop="metricName" label="指标名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="类型" min-width="104">
            <template #default="{ row }">{{ row.metricTypeLabel }}</template>
          </el-table-column>
          <el-table-column label="目标值" min-width="104" align="right">
            <template #default="{ row }">{{ row.targetValue }}{{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="方向" min-width="96">
            <template #default="{ row }">{{ row.directionLabel }}</template>
          </el-table-column>
          <el-table-column label="评分规则" min-width="110">
            <template #default="{ row }">{{ row.scoreModeLabel }}</template>
          </el-table-column>
          <el-table-column label="适用角色" min-width="120">
            <template #default="{ row }">{{ roleScopeText(row) }}</template>
          </el-table-column>
          <el-table-column label="权重" width="80" align="right">
            <template #default="{ row }">{{ row.weight }}%</template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <StatusTag v-if="row.enabled === 1" :dict="ENABLED_DICT" value="ON" variant="soft" />
              <StatusTag v-else :dict="ENABLED_DICT" value="OFF" variant="outline" />
            </template>
          </el-table-column>
        </el-table>

        <p class="kpi-page__tip">
          指标模板决定「考什么、怎么算、占多少权重」。权重只对启用项计入合计，必须等于 100% 才能保存。
        </p>
      </template>
    </el-card>

    <KpiScoreDrawer v-model="scoreVisible" :employee-id="activeEmployeeId" :month="query.month" />
    <KpiTemplateDrawer v-model="templateVisible" :can-write="isAdmin" @saved="reloadAll" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, MagicStick, Refresh } from '@element-plus/icons-vue'
import { getStations } from '@admin/api/station'
import { getEmployees } from '@admin/api/employee'
import { useAuthStore } from '@admin/stores/auth'
import { calculateKpiScores, getKpiMetrics, getKpiRanking, getKpiScores } from '../../../api/kpi.js'
import { KPI_ROLE_SCOPE } from '@/shared/constants/dict.js'
import PageHeader from '../../../components/PageHeader.vue'
import MiniStats from '../../../components/MiniStats.vue'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'
import KpiResultTable from './components/KpiResultTable.vue'
import KpiScoreDrawer from './components/KpiScoreDrawer.vue'
import KpiTemplateDrawer from './components/KpiTemplateDrawer.vue'

/**
 * 员工 KPI 考核（需求7）
 *
 * 挂载点选择：一期员工页在冻结清单内不可改，故 KPI 走独立路由 /employee/kpi 与「员工管理」并列，
 * 「并入员工管理模块」通过信息架构归属实现——KPI 表格点员工姓名进入 /employee/detail/:id 档案聚合页（A11-1）。
 *
 * 数据口径：得分与排名全部由服务端计算（得分列表/排名接口），前端不做任何加权或排名重算；
 * 「各指标得分」只在明细抽屉里展示，原因是列表接口不含逐指标数据，拆成动态列会对每行各打一次明细请求。
 */

/** 当前月份（YYYY-MM）：默认考核周期取本月，避免用户一进来就看到空列表 */
const currentMonth = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const ENABLED_DICT = {
  ON: { label: '已启用', type: 'success' },
  OFF: { label: '已停用', type: 'info' }
}

const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')

const activeTab = ref('result')
const stations = ref([])
const employees = ref([])

const query = reactive({ month: currentMonth(), stationId: undefined, employeeId: undefined, pageNum: 1, pageSize: 20 })

/* ==================== 考核结果 ==================== */

const list = ref([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref(false)

const summary = ref(null)
const summaryLoading = ref(false)
const summaryError = ref(false)

const summaryCards = [
  { key: 'count', label: '参与考核人数' },
  { key: 'avgScore', label: '平均得分' },
  { key: 'topScore', label: '最高得分' },
  { key: 'improveCount', label: '待改进（<60）' }
]

/**
 * 汇总条与列表分两次请求：
 * - 排名接口给的是权威计数（count / avgScore / topScore），不受分页影响；
 * - 列表接口支持 employeeId 收口（排名接口不支持），故选中员工时必须走它。
 */
async function loadSummary() {
  summaryLoading.value = true
  summaryError.value = false
  try {
    // 取前 100 名用于统计「待改进」人数：该口径受样本上限约束，超过 100 人时需后端补聚合字段
    const page = await getKpiRanking({ month: query.month, stationId: query.stationId, pageNum: 1, pageSize: 100 })
    summary.value = {
      count: page.count,
      avgScore: page.avgScore,
      topScore: page.topScore,
      improveCount: (page.list || []).filter((row) => Number(row.totalScore) < 60).length
    }
  } catch (e) {
    summaryError.value = true
  } finally {
    summaryLoading.value = false
  }
}

async function fetchList() {
  if (!query.month) return
  listLoading.value = true
  listError.value = false
  try {
    const params = {
      month: query.month,
      stationId: query.stationId,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    }
    const page = query.employeeId
      ? await getKpiScores({ ...params, employeeId: query.employeeId })
      : await getKpiRanking(params)
    list.value = page.list || []
    total.value = page.total || 0
  } catch (e) {
    listError.value = true
  } finally {
    listLoading.value = false
  }
}

const summaryHint = computed(() => {
  if (!summary.value || !summary.value.count) return ''
  return `口径：${query.month} · ${query.stationId ? stationName(query.stationId) : '全域'} · 综合得分 = Σ(单项得分 × 权重) ÷ Σ(适用指标权重)`
})

const loading = computed(() => listLoading.value || summaryLoading.value)

function handleFilterChange() {
  query.pageNum = 1
  loadSummary()
  fetchList()
}

function handlePageChange(page) {
  query.pageNum = page
  fetchList()
}

function handleSizeChange(size) {
  query.pageSize = size
  query.pageNum = 1
  fetchList()
}

const stationName = (id) => {
  const hit = stations.value.find((item) => item.id === id)
  return hit ? hit.stationName : '—'
}

const activeEmployeeId = ref(null)
const scoreVisible = ref(false)

function handleOpenDetail(row) {
  activeEmployeeId.value = row.employeeId
  scoreVisible.value = true
}

function handleOpenEmployee(row) {
  router.push(`/employee/detail/${row.employeeId}`)
}

/** 生成考核属不可撤回的批量写入，按 B0.3 给「动词标题 + 影响范围 + 具体动词按钮」 */
function handleGenerate() {
  if (!query.month) {
    ElMessage.warning('请先选择考核周期')
    return
  }
  ElMessageBox.confirm(
    `将按当前指标模板，对 ${query.month} 的全部在职员工重新计算考核结果；同一周期重复生成会覆盖已有结果。`,
    '生成本期考核',
    { confirmButtonText: '确认生成', cancelButtonText: '再想想', type: 'warning' }
  )
    .then(() => calculateKpiScores({ month: query.month, stationId: query.stationId || undefined }))
    .then((data) => {
      ElMessage.success(`已生成 ${data.employeeCount} 名员工的考核结果，覆盖 ${data.metricCount} 项指标`)
      loadSummary()
      fetchList()
    })
    .catch(() => {})
}

/* ==================== 指标模板 ==================== */

const metrics = ref([])
const weightSum = ref(0)
const metricLoading = ref(false)
const metricError = ref(false)
const templateVisible = ref(false)

async function loadMetrics() {
  metricLoading.value = true
  metricError.value = false
  try {
    const data = await getKpiMetrics()
    metrics.value = data.list || []
    weightSum.value = data.weightSum
  } catch (e) {
    metricError.value = true
  } finally {
    metricLoading.value = false
  }
}

const weightState = computed(() => {
  if (weightSum.value === 100) return { tone: 'success', text: '已平衡' }
  return weightSum.value < 100
    ? { tone: 'warning', text: `还差 ${100 - weightSum.value}%` }
    : { tone: 'danger', text: `超出 ${weightSum.value - 100}%` }
})

const roleScopeText = (row) => {
  const roles = row.roleScope || []
  if (!roles.length) return '全员'
  return roles.map((role) => (KPI_ROLE_SCOPE[role] ? KPI_ROLE_SCOPE[role].label : role)).join(' / ')
}

/* ==================== 初始化 ==================== */

function reloadAll() {
  loadMetrics()
  if (activeTab.value === 'result') {
    loadSummary()
    fetchList()
  }
}

async function loadBaseData() {
  const [stationList, employeePage] = await Promise.all([getStations(), getEmployees({ pageNum: 1, pageSize: 100 })])
  stations.value = stationList || []
  employees.value = (employeePage.list || []).map((item) => ({
    id: item.id,
    realName: item.realName,
    stationName: item.stationName
  }))
}

onMounted(() => {
  loadBaseData()
  loadSummary()
  fetchList()
  loadMetrics()
})
</script>

<style scoped lang="scss">
.kpi-page {
  .kpi-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--sp-4);
    }

    :deep(.el-tabs__item) {
      height: 40px;
      font-size: var(--fs-body);
      color: var(--text-2);
    }

    :deep(.el-tabs__item.is-active) {
      color: var(--color-primary-strong);
      font-weight: var(--fw-medium);
    }

    :deep(.el-tabs__active-bar) {
      height: 2px;
      background-color: var(--color-primary-strong);
    }
  }

  .content-card {
    margin-bottom: 0;
  }

  &__sum {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-bottom: var(--sp-4);
    padding: var(--sp-3);
    border: 1px solid var(--sum-border);
    border-radius: var(--r-md);
    background-color: var(--sum-bg);
    color: var(--sum-fg);

    &.is-success {
      --sum-bg: var(--state-success-bg);
      --sum-fg: var(--state-success-fg);
      --sum-border: var(--state-success-border);
    }

    &.is-warning {
      --sum-bg: var(--state-warning-bg);
      --sum-fg: var(--state-warning-fg);
      --sum-border: var(--state-warning-border);
    }

    &.is-danger {
      --sum-bg: var(--state-danger-bg);
      --sum-fg: var(--state-danger-fg);
      --sum-border: var(--state-danger-border);
    }
  }

  &__sum-label,
  &__sum-text {
    font-size: var(--fs-caption);
  }

  &__sum-value {
    font-size: var(--fs-num-sm);
    font-weight: var(--fw-semibold);
    font-variant-numeric: tabular-nums;
  }

  &__sum-track {
    flex: 1;
    height: 6px;
    border-radius: var(--r-full);

    /* 进度条轨道白色底：语义是「轨道」而非卡面/反色，无对应 L2，保留 L1 直引（P2-4 已登记） */
    background-color: var(--c-neutral-0);
    overflow: hidden;
  }

  &__sum-fill {
    display: block;
    height: 100%;
    border-radius: var(--r-full);
    background-color: var(--sum-fg);
  }

  &__tip {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
