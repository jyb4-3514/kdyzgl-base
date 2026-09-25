<template>
  <div class="hr-page">
    <PageHeader title="人事管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-button :icon="Refresh" @click="reloadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="hr-tabs" @tab-change="handleTabChange">
      <el-tab-pane name="profile" label="员工档案" />
      <el-tab-pane name="salary" label="定薪档案" />
      <el-tab-pane name="adjust" label="调薪记录" />
      <el-tab-pane name="position" label="岗位职级" />
    </el-tabs>

    <!-- Tab 1 员工档案 -->
    <template v-if="activeTab === 'profile'">
      <el-card shadow="never" class="filter-card">
        <el-form inline>
          <el-form-item label="驿站">
            <el-select
              v-model="profileQuery.stationId"
              clearable
              placeholder="全部驿站"
              style="width: 150px"
              @change="resetProfilePage"
            >
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门">
            <el-select
              v-model="profileQuery.deptId"
              clearable
              placeholder="全部部门"
              style="width: 150px"
              @change="resetProfilePage"
            >
              <el-option v-for="item in deptOptions" :key="item.id" :label="item.deptName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input
              v-model="profileQuery.keyword"
              placeholder="姓名 / 登录账号"
              clearable
              style="width: 180px"
              @keyup.enter="resetProfilePage"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="resetProfilePage">查询</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 合同到期提示条：人事最需要提前知道的事（B8.3）；计数为全域口径，不受表格分页影响 -->
      <div class="hr-page__alert" :class="{ 'is-empty': !contractAlert.total }">
        <el-icon><WarningFilled /></el-icon>
        <span v-if="contractAlert.total">
          合同 30 天内到期 <b>{{ contractAlert.soon }}</b> 人 · 已过期 <b>{{ contractAlert.expired }}</b> 人
          <span v-if="contractAlert.sampled" class="hr-page__alert-note"
            >（按前 {{ CONTRACT_SAMPLE }} 名员工统计）</span
          >
        </span>
        <span v-else>当前没有临近到期或已过期的合同</span>
      </div>

      <el-card shadow="never" class="content-card">
        <StateBlock v-if="profileError" variant="error" title="人事档案加载失败" @action="loadProfiles" />
        <StateBlock
          v-else-if="!profileLoading && !profiles.length"
          variant="empty"
          title="暂无员工档案"
          description="新员工完成入职流程建档后会自动出现在这里"
        />
        <template v-else>
          <el-table v-loading="profileLoading" :data="profiles" row-key="employeeId">
            <el-table-column prop="employeeName" label="姓名" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">
                <el-button link type="primary" @click="openProfile(row)">{{ row.employeeName }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="工号" min-width="120" show-overflow-tooltip />
            <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
            <el-table-column prop="deptName" label="部门" min-width="110" show-overflow-tooltip />
            <el-table-column prop="educationLabel" label="学历" width="92" />
            <el-table-column prop="entryDate" label="入职日期" width="112" />
            <el-table-column label="合同到期" width="150">
              <template #default="{ row }">
                <span>{{ row.contractEnd || '无固定期限' }}</span>
                <StatusTag
                  v-if="warnOf(row.contractEnd)"
                  :dict="CONTRACT_WARN"
                  :value="warnOf(row.contractEnd)"
                  :variant="CONTRACT_WARN[warnOf(row.contractEnd)].variant"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="88" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openProfile(row)">档案</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="profileQuery.pageNum"
              v-model:page-size="profileQuery.pageSize"
              :total="profileTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @size-change="resetProfilePage"
              @current-change="loadProfiles"
            />
          </div>
        </template>
      </el-card>
    </template>

    <!-- Tab 2 定薪档案 -->
    <template v-else-if="activeTab === 'salary'">
      <el-card shadow="never" class="filter-card">
        <el-form inline>
          <el-form-item label="驿站">
            <el-select
              v-model="salaryQuery.stationId"
              clearable
              placeholder="全部驿站"
              style="width: 150px"
              @change="resetSalaryPage"
            >
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input
              v-model="salaryQuery.keyword"
              placeholder="姓名 / 登录账号"
              clearable
              style="width: 180px"
              @keyup.enter="resetSalaryPage"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="resetSalaryPage">查询</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="content-card">
        <p class="hr-page__note">
          薪资标准是派生模板：新员工按「岗位 +
          职级」派生标准值建档，之后可单独调整；修改标准<strong>不会</strong>自动改变已建档员工的薪资。
        </p>

        <StateBlock v-if="salaryError" variant="error" title="定薪档案加载失败" @action="loadSalaries" />
        <StateBlock
          v-else-if="!salaryLoading && !salaries.length"
          variant="empty"
          title="暂无定薪档案"
          description="员工完成入职流程的「定薪」步骤后会出现在这里"
        />
        <template v-else>
          <el-table v-loading="salaryLoading" :data="salaries" row-key="employeeId">
            <el-table-column prop="employeeName" label="姓名" min-width="100" show-overflow-tooltip />
            <el-table-column label="基本工资" width="104" align="right">
              <template #default="{ row }">{{ row.basicSalary }}</template>
            </el-table-column>
            <el-table-column label="岗位工资" width="104" align="right">
              <template #default="{ row }">{{ row.postSalary }}</template>
            </el-table-column>
            <el-table-column label="绩效基数" width="104" align="right">
              <template #default="{ row }">{{ row.performanceBase }}</template>
            </el-table-column>
            <el-table-column label="津贴合计" width="104" align="right">
              <template #default="{ row }">{{ row.allowancesTotal }}</template>
            </el-table-column>
            <el-table-column label="标准合计" width="116" align="right">
              <template #default="{ row }">
                <span class="hr-page__strong">{{ row.totalSalary }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="effectiveDate" label="生效日期" width="112" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSalary(row)">调整薪资</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="salaryQuery.pageNum"
              v-model:page-size="salaryQuery.pageSize"
              :total="salaryTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @size-change="resetSalaryPage"
              @current-change="loadSalaries"
            />
          </div>
        </template>
      </el-card>
    </template>

    <!-- Tab 3 调薪记录 -->
    <el-card v-else-if="activeTab === 'adjust'" shadow="never" class="content-card">
      <p class="hr-page__note">
        调薪留痕只增不改。契约暂无「全局调薪记录」接口，本表按当前筛选范围内<strong
          >前 {{ HISTORY_EMPLOYEE_LIMIT }} 名员工</strong
        >的留痕聚合。
      </p>
      <StateBlock v-if="historyError" variant="error" title="调薪记录加载失败" @action="loadHistories" />
      <StateBlock
        v-else-if="!historyLoading && !histories.length"
        variant="empty"
        title="暂无调薪记录"
        description="员工首次定薪或调薪后会出现留痕"
      />
      <el-table v-else v-loading="historyLoading" :data="histories" row-key="id">
        <el-table-column prop="employeeName" label="员工" min-width="100" show-overflow-tooltip />
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <StatusTag :dict="CHANGE_TYPE" :value="row.changeType" variant="outline" />
          </template>
        </el-table-column>
        <el-table-column label="调整后构成" min-width="200">
          <template #default="{ row }">
            基本 {{ row.basicSalary }} / 岗位 {{ row.postSalary }} / 绩效 {{ row.performanceBase }} / 津贴
            {{ row.allowancesTotal }}
          </template>
        </el-table-column>
        <el-table-column label="合计" width="104" align="right">
          <template #default="{ row }">
            <span class="hr-page__strong">{{ row.totalSalary }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="112" />
        <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createTime" label="记录时间" width="164" />
      </el-table>
    </el-card>

    <!-- Tab 4 岗位职级 -->
    <el-card v-else shadow="never" class="content-card">
      <StateBlock
        variant="empty"
        title="岗位与职级字典尚未开放"
        description="契约未提供岗位/职级的字典接口，本页暂不展示；当前岗位信息随入职流程的「分配驿站/岗位」步骤记录。"
      />
    </el-card>

    <ProfileEditDrawer v-model="profileVisible" :employee-id="activeEmployeeId" @saved="afterSaved" />
    <SalaryEditorDrawer v-model="salaryVisible" :employee-id="activeEmployeeId" @saved="afterSaved" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import { useOrgStore } from '../../stores/org.js'
import { getHrProfiles, getHrSalaries, getHrSalary } from '../../api/hr.js'
import { CONTRACT_WARN } from '@kdyzgl/shared/constants/dict.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import ProfileEditDrawer from './components/ProfileEditDrawer.vue'
import SalaryEditorDrawer from './components/SalaryEditorDrawer.vue'

/**
 * 人事管理（需求8）
 *
 * Tab 取舍说明（契约优先）：
 * - 员工档案 / 定薪档案 各有独立接口；
 * - 「薪资标准」在契约层没有模板接口，故以「定薪档案」承载，并在页内说明标准 → 员工薪资的派生关系；
 * - 「调薪记录」没有全局接口，按当前筛选范围内的员工逐个取留痕后聚合（demo 量级可接受）；
 * - 「岗位职级」没有字典接口，页面保留位置并显式说明缺口，不做一个点了没有数据的假表。
 * TODO(扩展): 后端补 /hr/salary-standards、/hr/adjust-logs、/hr/positions 三个接口后替换以上四个 Tab 的实现。
 */
const CHANGE_TYPE = {
  ENTRY: { label: '入职定薪', type: 'primary' },
  ADJUST: { label: '调薪', type: 'info' }
}
const CONTRACT_WARN_DAYS = 30
const HISTORY_EMPLOYEE_LIMIT = 20
const CONTRACT_SAMPLE = 100

// 驿站与部门都是跨页基础数据，取数收口到 org store（部门下拉沿用页面既有变量名 deptOptions）
const orgStore = useOrgStore()
const { stations, departmentOptions: deptOptions } = storeToRefs(orgStore)

const activeTab = ref('profile')

const profileVisible = ref(false)
const salaryVisible = ref(false)
const activeEmployeeId = ref(null)

/* ==================== Tab 1 员工档案 ==================== */

const profiles = ref([])
const profileTotal = ref(0)
const profileLoading = ref(false)
const profileError = ref(false)
const profileQuery = reactive({ stationId: undefined, deptId: undefined, keyword: '', pageNum: 1, pageSize: 20 })

/** 合同到期预警：仅用于行内标签与提示条，不参与接口筛选（契约无该参数） */
function daysTo(dateText) {
  if (!dateText) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((new Date(`${dateText}T00:00:00`) - today) / 86400000)
}

const warnOf = (dateText) => {
  const days = daysTo(dateText)
  if (days === null) return ''
  if (days < 0) return 'EXPIRED'
  if (days <= CONTRACT_WARN_DAYS) return 'SOON'
  return ''
}

/**
 * 合同预警计数（全域口径）：契约没有「按到期日聚合」的接口，故单独取一页样本统计，
 * 而不是拿表格当前页去数——那会让提示条随分页变化，属于会误导人的假指标。
 * TODO(扩展): 后端补 /hr/profiles/contract-alerts 后改为直接取聚合值，去掉样本上限。
 */
const contractAlert = ref({ soon: 0, expired: 0, total: 0, sampled: false })

async function loadContractAlert() {
  try {
    const page = await getHrProfiles({ pageNum: 1, pageSize: CONTRACT_SAMPLE })
    let soon = 0
    let expired = 0
    ;(page.list || []).forEach((row) => {
      const warn = warnOf(row.contractEnd)
      if (warn === 'SOON') soon += 1
      if (warn === 'EXPIRED') expired += 1
    })
    contractAlert.value = { soon, expired, total: soon + expired, sampled: (page.total || 0) > CONTRACT_SAMPLE }
  } catch (e) {
    // 提示条是辅助信息，取数失败不阻断主表
    contractAlert.value = { soon: 0, expired: 0, total: 0, sampled: false }
  }
}

async function loadProfiles() {
  profileLoading.value = true
  profileError.value = false
  try {
    const page = await getHrProfiles({
      stationId: profileQuery.stationId,
      deptId: profileQuery.deptId,
      keyword: profileQuery.keyword || undefined,
      pageNum: profileQuery.pageNum,
      pageSize: profileQuery.pageSize
    })
    profiles.value = page.list || []
    profileTotal.value = page.total || 0
  } catch (e) {
    profileError.value = true
  } finally {
    profileLoading.value = false
  }
}

function resetProfilePage() {
  profileQuery.pageNum = 1
  loadProfiles()
}

/* ==================== Tab 2 定薪档案 ==================== */

const salaries = ref([])
const salaryTotal = ref(0)
const salaryLoading = ref(false)
const salaryError = ref(false)
const salaryQuery = reactive({ stationId: undefined, keyword: '', pageNum: 1, pageSize: 20 })

async function loadSalaries() {
  salaryLoading.value = true
  salaryError.value = false
  try {
    const page = await getHrSalaries({
      stationId: salaryQuery.stationId,
      keyword: salaryQuery.keyword || undefined,
      pageNum: salaryQuery.pageNum,
      pageSize: salaryQuery.pageSize
    })
    salaries.value = page.list || []
    salaryTotal.value = page.total || 0
  } catch (e) {
    salaryError.value = true
  } finally {
    salaryLoading.value = false
  }
}

function resetSalaryPage() {
  salaryQuery.pageNum = 1
  loadSalaries()
  if (activeTab.value === 'adjust') loadHistories()
}

/* ==================== Tab 3 调薪记录 ==================== */

const histories = ref([])
const historyLoading = ref(false)
const historyError = ref(false)

/**
 * 聚合留痕：先取当前筛选范围内的定薪档案（受 HISTORY_EMPLOYEE_LIMIT 限制），
 * 再逐个取 histories。并发请求数量有上限，避免翻到 100 人时打出上百个请求。
 */
async function loadHistories() {
  historyLoading.value = true
  historyError.value = false
  try {
    const page = await getHrSalaries({
      stationId: salaryQuery.stationId,
      keyword: salaryQuery.keyword || undefined,
      pageNum: 1,
      pageSize: HISTORY_EMPLOYEE_LIMIT
    })
    const rows = page.list || []
    const results = await Promise.all(
      rows.map((row) =>
        getHrSalary(row.employeeId)
          .then((detail) => (detail.histories || []).map((item) => ({ ...item, employeeName: row.employeeName })))
          .catch(() => [])
      )
    )
    histories.value = results.flat().sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
  } catch (e) {
    historyError.value = true
  } finally {
    historyLoading.value = false
  }
}

/* ==================== 抽屉与初始化 ==================== */

function openProfile(row) {
  activeEmployeeId.value = row.employeeId
  profileVisible.value = true
}

function openSalary(row) {
  activeEmployeeId.value = row.employeeId
  salaryVisible.value = true
}

function afterSaved() {
  if (activeTab.value === 'salary') loadSalaries()
  else loadProfiles()
}

const loading = computed(() => profileLoading.value || salaryLoading.value)

const headerSub = computed(() => `员工档案 ${profileTotal.value} 人 · 当前范围：全部在职员工（含试用期）`)

function handleTabChange(name) {
  if (name === 'salary') loadSalaries()
  if (name === 'adjust') loadHistories()
}

function reloadAll() {
  if (activeTab.value === 'profile') loadProfiles()
  else if (activeTab.value === 'salary') loadSalaries()
  else if (activeTab.value === 'adjust') loadHistories()
  else ElMessage.info('岗位职级字典待契约补齐')
}

async function loadBaseData() {
  // 部门树失败降级为空下拉；驿站失败仍向上抛（档案筛选依赖它，异常交给拦截器提示）
  await Promise.all([orgStore.loadStations(), orgStore.loadDepartments().catch(() => [])])
}

onMounted(async () => {
  await loadBaseData()
  loadProfiles()
  loadContractAlert()
})
</script>

<style scoped lang="scss">
.hr-page {
  .hr-tabs {
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

  &__alert {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-4);
    padding: var(--sp-3) var(--sp-4);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-md);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);

    &.is-empty {
      border-color: var(--border-line);
      background-color: var(--surface-sunken);
      color: var(--text-3);
    }
  }

  &__alert-note {
    margin-left: var(--sp-1);
    opacity: 0.8;
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

  &__strong {
    font-weight: var(--fw-semibold);
    color: var(--text-1);
  }
}
</style>
