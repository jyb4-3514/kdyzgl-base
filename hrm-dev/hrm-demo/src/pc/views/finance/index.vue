<template>
  <div class="finance-page">
    <PageHeader title="财务管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-button v-if="activeTab === 'payroll'" type="primary" :icon="Plus" @click="generateVisible = true"
          >生成工资单</el-button
        >
        <el-button v-else-if="activeTab === 'rule'" type="primary" :icon="Plus" @click="openRule(null)"
          >新建规则</el-button
        >
        <el-button :icon="Refresh" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="finance-tabs" @tab-change="handleTabChange">
      <el-tab-pane name="payroll" label="工资单" />
      <el-tab-pane name="rule" label="计算规则" />
      <el-tab-pane name="objection" label="异议处理" />
    </el-tabs>

    <!-- Tab 1 工资单 -->
    <template v-if="activeTab === 'payroll'">
      <el-card shadow="never" class="filter-card">
        <el-form inline>
          <el-form-item label="月份">
            <el-date-picker
              v-model="query.month"
              type="month"
              value-format="YYYY-MM"
              clearable
              placeholder="全部月份"
              style="width: 140px"
              @change="resetPage"
            />
          </el-form-item>
          <el-form-item label="驿站">
            <el-select
              v-model="query.stationId"
              clearable
              placeholder="全部驿站"
              style="width: 150px"
              @change="resetPage"
            >
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px" @change="resetPage">
              <el-option v-for="(dict, key) in PAYROLL_STATUS" :key="key" :value="key" :label="dict.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="员工姓名 / 单号"
              clearable
              style="width: 180px"
              @keyup.enter="resetPage"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="resetPage">查询</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="content-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <!-- 状态计数条：点一下即按该状态筛选，省掉一次下拉操作 -->
            <span class="finance-page__chips">
              <button
                v-for="(dict, key) in PAYROLL_STATUS"
                :key="key"
                type="button"
                class="finance-page__chip"
                :class="{ 'is-active': query.status === key }"
                @click="filterByStatus(key)"
              >
                {{ dict.label }} {{ (counts && counts[key]) || 0 }}
              </button>
            </span>
          </div>
          <div class="toolbar-right">
            <el-button
              :disabled="!pendingSubmitCount"
              :title="pendingSubmitCount ? '把当前范围内草稿/已驳回的单据提交审核' : '当前范围内没有可提交的单据'"
              @click="handleBatchSubmit"
            >
              批量提交审核（{{ pendingSubmitCount }}）
            </el-button>
            <el-button
              :disabled="!approvedCount"
              :title="approvedCount ? '发布当前范围内已通过的单据' : '当前范围内没有已通过待发布的单据'"
              @click="handleBatchPublish"
            >
              批量发布（{{ approvedCount }}）
            </el-button>
            <!-- TODO(扩展): 工资单导出待契约提供导出接口（当前无 /finance/payrolls/export） -->
          </div>
        </div>

        <PayrollDetailTable
          :list="list"
          :total="total"
          :loading="listLoading"
          :error="listError"
          :page-num="query.pageNum"
          :page-size="query.pageSize"
          :can-write="true"
          @retry="fetchList"
          @generate="generateVisible = true"
          @open="openDetail"
          @action="handleRowAction"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </el-card>
    </template>

    <!-- Tab 2 计算规则 -->
    <el-card v-else-if="activeTab === 'rule'" shadow="never" class="content-card">
      <p class="finance-page__note">
        算薪不写死规则：金额由「工资项目」计算得出，项目只声明方向 + 数据来源 + 计算参数。
        改一项津贴、调一次扣款标准都只是改配置，不影响已生成的工资单（历史单据保留当时的规则快照）。
      </p>

      <StateBlock v-if="ruleError" variant="error" title="计薪规则加载失败" @action="loadRules" />
      <StateBlock
        v-else-if="!ruleLoading && !rules.length"
        variant="empty"
        title="暂无计薪规则"
        description="先建一条规则并启用，才能生成工资单草稿"
        action-text="新建规则"
        @action="openRule(null)"
      />
      <el-table v-else v-loading="ruleLoading" :data="rules" row-key="id">
        <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <StatusTag
              :dict="RULE_STATUS"
              :value="String(row.status)"
              :variant="row.status === 1 ? 'soft' : 'outline'"
            />
          </template>
        </el-table-column>
        <el-table-column label="工资项目" width="130">
          <template #default="{ row }">启用 {{ row.enabledItemCount }} / 共 {{ row.itemCount }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="164" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRule(row)">编辑</el-button>
            <el-button link type="primary" @click="handleToggleRule(row)">{{
              row.status === 1 ? '停用' : '启用'
            }}</el-button>
            <el-button link type="danger" @click="handleDeleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Tab 3 异议处理 -->
    <el-card v-else shadow="never" class="content-card">
      <p class="finance-page__note">
        员工提异议后单据会退回「待审核」，由老板重新核定后再发布。契约没有独立的异议列表接口，
        本表取「待审核」状态的单据后筛选有异议原因的行。
      </p>
      <StateBlock v-if="objectionError" variant="error" title="异议列表加载失败" @action="loadObjections" />
      <StateBlock
        v-else-if="!objectionLoading && !objections.length"
        variant="empty"
        title="当前没有待处理的异议"
        description="员工在移动端提出异议后会出现在这里"
      />
      <el-table v-else v-loading="objectionLoading" :data="objections" row-key="id">
        <el-table-column prop="employeeName" label="员工" min-width="100" show-overflow-tooltip />
        <el-table-column prop="month" label="月份" width="92" />
        <el-table-column prop="objectionReason" label="异议原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="objectionTime" label="提出时间" width="164" />
        <el-table-column label="状态" width="104">
          <template #default="{ row }">
            <StatusTag :dict="PAYROLL_STATUS" :value="row.status" :variant="PAYROLL_STATUS[row.status].variant" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <GeneratePayrollDialog
      v-model="generateVisible"
      :stations="stations"
      :departments="departmentOptions"
      @generated="afterGenerate"
    />
    <PayrollRuleEditor v-model="ruleVisible" :rule-id="editingRuleId" @saved="reloadAll" />
    <PayrollDetailDrawer v-model="detailVisible" :payroll-id="detailId" :can-write="true" @action="handleRowAction" />

    <!-- 审核弹窗：通过 / 驳回共用一份表单，驳回必须填意见 -->
    <el-dialog v-model="approveVisible" title="审核工资单" width="480px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item label="审核结论">
          <el-radio-group v-model="approveForm.approved">
            <el-radio :value="true">审核通过</el-radio>
            <el-radio :value="false">驳回退回草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="approveForm.approved ? '审核意见（选填）' : '驳回意见（必填）'">
          <el-input v-model="approveForm.approveRemark" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approving" @click="submitApprove">
          {{ approveForm.approved ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { getStations } from '@admin/api/station'
import { getDepartmentTree } from '@admin/api/department'
import {
  approvePayroll,
  deletePayrollRule,
  getPayrolls,
  getPayrollRules,
  publishPayrolls,
  submitPayrolls,
  updatePayrollRule
} from '../../api/finance.js'
import { PAYROLL_STATUS } from '@/shared/constants/dict.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import PayrollDetailTable from './components/PayrollDetailTable.vue'
import PayrollDetailDrawer from './components/PayrollDetailDrawer.vue'
import PayrollRuleEditor from './components/PayrollRuleEditor.vue'
import GeneratePayrollDialog from './components/GeneratePayrollDialog.vue'

/**
 * 财务管理 · 工资单（需求9）
 *
 * 老板端的完整闭环：配计算规则 → 生成草稿 → 批量调整人工项 → 提交审核 → 审核通过并发布 → 处理员工异议。
 * 所有不可撤回的操作（发布、删除规则、驳回）都走 B0.3 的确认文案：动词标题 + 影响范围 + 具体动词按钮。
 *
 * 契约边界：工资单列表是按「员工 × 账期」逐行的，没有「按驿站汇总的单批次」概念，
 * 因此列表列与状态计数都按员工单据口径呈现，不额外造一层批次聚合。
 */
const RULE_STATUS = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

const currentMonth = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const activeTab = ref('payroll')
const stations = ref([])
const departmentOptions = ref([])

/* ==================== Tab 1 工资单 ==================== */

const list = ref([])
const total = ref(0)
const counts = ref(null)
const listLoading = ref(false)
const listError = ref(false)
const query = reactive({
  month: currentMonth(),
  stationId: undefined,
  status: undefined,
  keyword: '',
  pageNum: 1,
  pageSize: 20
})

const detailVisible = ref(false)
const detailId = ref(null)
const generateVisible = ref(false)

async function fetchList() {
  listLoading.value = true
  listError.value = false
  try {
    const page = await getPayrolls({
      month: query.month || undefined,
      stationId: query.stationId,
      status: query.status,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list || []
    total.value = page.total || 0
    counts.value = page.counts || null
  } catch (e) {
    listError.value = true
  } finally {
    listLoading.value = false
  }
}

function resetPage() {
  query.pageNum = 1
  fetchList()
}

function filterByStatus(status) {
  query.status = query.status === status ? undefined : status
  resetPage()
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

/** 批量提交的候选数：以状态计数为准（同月同驿站口径，不受当前分页影响） */
const pendingSubmitCount = computed(
  () => ((counts.value && counts.value.DRAFT) || 0) + ((counts.value && counts.value.REJECTED) || 0)
)
const approvedCount = computed(() => (counts.value && counts.value.APPROVED) || 0)

function openDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

/** 行内/抽屉动作统一入口：按动作分派，确认文案全部走 B0.3 规范 */
function handleRowAction({ action, row }) {
  if (!row) return
  if (action === 'submit') {
    ElMessageBox.confirm(
      `将把 ${row.employeeName} ${row.month} 的工资单提交审核，提交后本人仍需老板审核通过才能发布。`,
      '提交审核',
      { confirmButtonText: '确认提交', cancelButtonText: '再想想', type: 'warning' }
    )
      .then(() => submitPayrolls([row.id]))
      .then((data) => {
        ElMessage.success(`已提交 ${data.submitted} 份工资单待审核`)
        detailVisible.value = false
        fetchList()
        loadObjections()
      })
      .catch(() => {})
    return
  }
  if (action === 'approve') {
    openApprove(row)
    return
  }
  if (action === 'publish') {
    confirmPublish([row.id], row.employeeName)
  }
}

async function handleBatchSubmit() {
  try {
    await ElMessageBox.confirm(
      `将把当前范围内全部草稿与已驳回的工资单（共 ${pendingSubmitCount.value} 份）提交审核；提交后需审核通过才能发布。`,
      '批量提交审核',
      { confirmButtonText: '确认提交', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (e) {
    return
  }
  // 契约的提交接口按 ids 收口，故先取全量范围（上限 100 条）再提交
  const page = await getPayrolls({
    month: query.month || undefined,
    stationId: query.stationId,
    pageNum: 1,
    pageSize: 100
  })
  const ids = (page.list || []).filter((row) => ['DRAFT', 'REJECTED'].includes(row.status)).map((row) => row.id)
  if (!ids.length) {
    ElMessage.warning('当前范围内没有可提交的单据')
    return
  }
  const data = await submitPayrolls(ids)
  ElMessage.success(`已提交 ${data.submitted} 份工资单待审核`)
  fetchList()
  loadObjections()
}

async function handleBatchPublish() {
  try {
    await ElMessageBox.confirm(
      `将发布当前范围内全部已通过的工资单（共 ${approvedCount.value} 份）。发布后员工可见并需逐人确认，发布动作不可撤回。`,
      '批量发布工资单',
      { confirmButtonText: '确认发布', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (e) {
    return
  }
  const data = await publishPayrolls({ month: query.month || undefined, stationId: query.stationId })
  ElMessage.success(`已发布 ${data.published} 份工资单${data.skipped ? `，跳过 ${data.skipped} 份状态不符的单据` : ''}`)
  fetchList()
}

/** 单份发布：与批量发布共用一份文案口径 */
function confirmPublish(ids, employeeName) {
  ElMessageBox.confirm(`将发布 ${employeeName} 的工资单。发布后员工可见并需确认，发布动作不可撤回。`, '发布工资单', {
    confirmButtonText: '确认发布',
    cancelButtonText: '再想想',
    type: 'warning'
  })
    .then(() => publishPayrolls({ ids }))
    .then((data) => {
      ElMessage.success(`已发布 ${data.published} 份工资单`)
      detailVisible.value = false
      fetchList()
    })
    .catch(() => {})
}

/* ==================== 审核弹窗（通过 / 驳回 + 意见） ==================== */

const approveVisible = ref(false)
const approveRow = ref(null)
const approveForm = ref({ approved: true, approveRemark: '' })
const approving = ref(false)

function openApprove(row) {
  approveRow.value = row
  approveForm.value = { approved: true, approveRemark: '' }
  approveVisible.value = true
}

async function submitApprove() {
  if (!approveRow.value) return
  const remark = String(approveForm.value.approveRemark || '').trim()
  if (!approveForm.value.approved && remark.length < 2) {
    ElMessage.warning('驳回须填写审核意见（2-200 字）')
    return
  }
  approving.value = true
  try {
    await approvePayroll(approveRow.value.id, {
      approved: approveForm.value.approved,
      approveRemark: remark || undefined
    })
    ElMessage.success(approveForm.value.approved ? '审核通过，可在列表里发布' : '已驳回，单据退回草稿状态')
    approveVisible.value = false
    detailVisible.value = false
    fetchList()
    loadObjections()
  } finally {
    approving.value = false
  }
}

/* ==================== Tab 2 计算规则 ==================== */

const rules = ref([])
const ruleLoading = ref(false)
const ruleError = ref(false)
const ruleVisible = ref(false)
const editingRuleId = ref(null)

async function loadRules() {
  ruleLoading.value = true
  ruleError.value = false
  try {
    const data = await getPayrollRules()
    rules.value = data.list || []
  } catch (e) {
    ruleError.value = true
  } finally {
    ruleLoading.value = false
  }
}

function openRule(row) {
  editingRuleId.value = row ? row.id : null
  ruleVisible.value = true
}

function handleToggleRule(row) {
  const next = row.status === 1 ? 0 : 1
  const confirmText =
    next === 1
      ? `启用「${row.ruleName}」后，新生成的工资单将按该规则计算；已生成的单据不受影响。`
      : `停用「${row.ruleName}」后，生成工资单时会找不到启用的规则；已生成的单据不受影响。`
  ElMessageBox.confirm(confirmText, next === 1 ? '启用计薪规则' : '停用计薪规则', {
    confirmButtonText: next === 1 ? '确认启用' : '确认停用',
    cancelButtonText: '再想想',
    type: 'warning'
  })
    .then(() => updatePayrollRule(row.id, { status: next }))
    .then(() => {
      ElMessage.success(next === 1 ? '规则已启用' : '规则已停用')
      loadRules()
    })
    .catch(() => {})
}

function handleDeleteRule(row) {
  ElMessageBox.confirm(
    `删除规则「${row.ruleName}」不可撤回；已被工资单引用的规则不允许删除，只能停用。`,
    '删除计薪规则',
    { confirmButtonText: '确认删除', cancelButtonText: '再想想', type: 'warning' }
  )
    .then(() => deletePayrollRule(row.id))
    .then(() => {
      ElMessage.success('规则已删除')
      loadRules()
    })
    .catch(() => {})
}

/* ==================== Tab 3 异议处理 ==================== */

const objections = ref([])
const objectionLoading = ref(false)
const objectionError = ref(false)

async function loadObjections() {
  objectionLoading.value = true
  objectionError.value = false
  try {
    const page = await getPayrolls({ status: 'PENDING_APPROVAL', pageNum: 1, pageSize: 100 })
    objections.value = (page.list || []).filter((row) => !!row.objectionReason)
  } catch (e) {
    objectionError.value = true
  } finally {
    objectionLoading.value = false
  }
}

/* ==================== 初始化 ==================== */

const loading = computed(() => listLoading.value || ruleLoading.value || objectionLoading.value)
const headerSub = computed(() => `当前筛选共 ${total.value} 份工资单 · 全站按月生成，审核后发布给员工确认`)

function afterGenerate() {
  fetchList()
  loadRules()
}

function handleTabChange(name) {
  if (name === 'rule') loadRules()
  if (name === 'objection') loadObjections()
}

function reloadAll() {
  if (activeTab.value === 'payroll') fetchList()
  else if (activeTab.value === 'rule') loadRules()
  else loadObjections()
}

async function loadBaseData() {
  const [stationList, deptTree] = await Promise.all([getStations(), getDepartmentTree().catch(() => [])])
  stations.value = stationList || []
  const flat = []
  const walk = (nodes) => {
    ;(nodes || []).forEach((node) => {
      flat.push({ id: node.id, deptName: node.deptName })
      walk(node.children)
    })
  }
  walk(deptTree)
  departmentOptions.value = flat
}

onMounted(async () => {
  await loadBaseData()
  fetchList()
})
</script>

<style scoped lang="scss">
.finance-page {
  .finance-tabs {
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

  .toolbar-right {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: var(--sp-1);
  }

  // 状态计数用真按钮：既能点筛选，也天然可聚焦（不用绑 click 的 span）
  &__chip {
    padding: var(--sp-1) var(--sp-2);
    border: 1px solid var(--border-line);
    border-radius: var(--r-full);
    background-color: var(--surface-card);
    color: var(--text-3);
    font-family: inherit;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    font-variant-numeric: tabular-nums;
    cursor: pointer;
    transition:
      border-color var(--dur-fast) var(--ease-std),
      color var(--dur-fast) var(--ease-std);

    &:hover {
      border-color: var(--color-primary-border);
      color: var(--color-primary-strong);
    }

    &.is-active {
      border-color: var(--color-primary-border);
      background-color: var(--color-primary-surface);
      color: var(--color-primary-strong);
      font-weight: var(--fw-medium);
    }
  }

  &__note {
    margin: 0 0 var(--sp-4);
    padding: var(--sp-3);
    border-left: 3px solid var(--color-primary-border);
    border-radius: var(--r-xs);
    background-color: var(--color-primary-surface);
    color: var(--text-2);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
  }
}
</style>
