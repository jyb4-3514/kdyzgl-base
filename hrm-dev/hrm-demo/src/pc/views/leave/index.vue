<template>
  <div class="leave-page">
    <PageHeader title="请假管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 扣款开关（Q6：配置与受影响的数据同屏，不放独立菜单）。仅 ADMIN 可见可改 -->
    <el-card v-if="isAdmin" shadow="never" class="deduct-card">
      <div class="deduct-card__head">
        <span class="deduct-card__title">请假扣款设置</span>
        <span class="deduct-card__state">当前：{{ deductEnabled ? '按缺勤计（扣款）' : '不计缺勤（不扣款）' }}</span>
      </div>
      <!-- 这条语义必须写给用户看：开关名字面很含蓄，管理员猜错一次就是一次工资争议 -->
      <p class="deduct-card__rule">
        <strong>开启（扣款）</strong>：请假当天按缺勤计，缺勤天数 = 排班天数 − 出勤天数，请假天数照常计入缺勤。
        <br />
        <strong>关闭（不扣款，默认）</strong>：缺勤天数 = 排班天数 − 出勤天数 − 已批请假天数，请假不扣工资。
      </p>
      <div class="deduct-card__row">
        <el-switch
          v-model="deductEnabled"
          :loading="settingsSaving"
          :disabled="settingsLoading"
          active-text="扣款"
          inactive-text="不扣款"
          aria-label="请假扣款开关"
          @change="handleDeductChange"
        />
        <span class="deduct-card__hint">改动即影响全部驿站的算薪口径，切换前会二次确认。</span>
      </div>
      <StateBlock v-if="settingsError" variant="error" title="扣款设置加载失败" @action="loadSettings" />
    </el-card>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" class="filter-status" @change="handleSearch">
            <el-option v-for="item in LEAVE_FILTERS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <!-- 驿站与员工筛选仅 ADMIN：站长只能看本站，给他一个跨站选择器只会造成「选了不生效」的困惑 -->
        <el-form-item v-if="isAdmin" label="驿站">
          <el-select v-model="query.stationId" clearable placeholder="全部驿站" @change="handleSearch">
            <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isAdmin" label="员工">
          <el-select
            v-model="query.employeeId"
            clearable
            filterable
            placeholder="全部员工"
            class="filter-employee"
            @change="handleSearch"
          >
            <el-option v-for="item in employees" :key="item.id" :label="item.realName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="假别">
          <el-select v-model="query.leaveType" clearable placeholder="全部假别" @change="handleSearch">
            <el-option v-for="(item, key) in LEAVE_TYPE" :key="key" :label="item.label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="请假日期">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="filter-date"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="content-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-tip">共 {{ total }} 条</span>
          <!-- 待审计数固定按「当前在手的那一级」取，与筛选条件无关，审批完一条立刻变化 -->
          <span v-if="pendingTotal" class="toolbar-badge" role="status">
            {{ pendingTotal }} 条{{ isAdmin ? '待终审' : '待初审' }}
          </span>
        </div>
        <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新请假列表" @click="refresh" />
      </div>

      <StateBlock v-if="listError" variant="error" title="请假列表加载失败" @action="refresh" />

      <StateBlock
        v-else-if="!loading && !list.length"
        variant="empty"
        :title="isPendingView ? emptyPendingTitle : '当前筛选条件下没有请假申请'"
        :description="isPendingView ? '可切换状态查看历史审批结果与留痕' : ''"
        action-text="重置筛选条件"
        @action="handleReset"
      />

      <template v-else>
        <el-table v-loading="loading" class="sticky-table" :data="list" border>
          <el-table-column prop="employeeName" label="申请人" min-width="90" fixed="left" />
          <el-table-column label="驿站" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.stationName || '—' }}</template>
          </el-table-column>
          <el-table-column label="假别" width="80" align="center">
            <template #default="{ row }">{{ typeText(row) }}</template>
          </el-table-column>
          <el-table-column label="请假时间" min-width="230">
            <template #default="{ row }">{{ rangeText(row) }}</template>
          </el-table-column>
          <el-table-column label="自然天数" width="90" align="center">
            <template #default="{ row }">{{ row.naturalDays }}</template>
          </el-table-column>
          <!-- 两个口径必须同时出：只给计薪天数，员工会以为系统算错了假天数 -->
          <el-table-column label="计薪天数" width="90" align="center">
            <template #default="{ row }">
              <el-tooltip content="按排班逐日计，已排除轮休日；与自然天数不等是常态" placement="top">
                <span>{{ row.countedDays }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="事由" min-width="160" show-overflow-tooltip />
          <el-table-column prop="applyTime" label="申请时间" min-width="160" />
          <el-table-column label="状态" width="130" align="center">
            <template #default="{ row }">
              <StatusTag
                :dict="LEAVE_STATUS"
                :value="row.status"
                :variant="(LEAVE_STATUS[row.status] || {}).variant || 'soft'"
              />
              <span v-if="rejectStageText(row)" class="status-sub">{{ rejectStageText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <template v-if="canApprove(row)">
                <el-button link type="primary" @click="openApprove(row, 'approve')">通过</el-button>
                <el-button link type="danger" @click="openApprove(row, 'reject')">驳回</el-button>
              </template>
              <el-button v-else-if="canRevoke(row)" link type="warning" @click="openRevoke(row)">撤回</el-button>
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="fetchList"
          />
        </div>
      </template>
    </el-card>

    <LeaveApprovalDialog
      v-model="approval.visible"
      :row="approval.row"
      :mode="approval.mode"
      :loading="approval.loading"
      @submit="submitApprove"
    />
    <LeaveRevokeDialog
      v-model="revokeDialog.visible"
      :row="revokeDialog.row"
      :loading="revokeDialog.loading"
      @submit="submitRevoke"
    />
    <LeaveDetailDrawer v-model="detailVisible" :leave-id="detailId" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@admin/stores/auth'
import { getEmployees } from '@admin/api/employee'
import { useOrgStore } from '../../stores/org.js'
import { LEAVE_FILTERS, LEAVE_STATUS, LEAVE_TYPE } from '@/shared/constants/dict'
import { LEAVE_CODE } from '@/shared/constants/errorCode'
import {
  finalApproveLeave,
  getLeaveList,
  getLeaveSettings,
  revokeLeave,
  saveLeaveSettings,
  stationApproveLeave
} from '../../api/leave.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import LeaveApprovalDialog from './components/LeaveApprovalDialog.vue'
import LeaveDetailDrawer from './components/LeaveDetailDrawer.vue'
import LeaveRevokeDialog from './components/LeaveRevokeDialog.vue'
import { rangeText, rejectStageText, typeText } from './utils/leave.js'

/**
 * PC 请假管理（P6，设计规范 §3.1 / §3.2）
 *
 * 权限（§2.1）：ADMIN 全域终审 + 撤回；STATION_ADMIN 仅本站初审。差异体现在三处：
 * 1. 筛选栏的驿站/员工仅 ADMIN 渲染（站长看别的驿站只会「选了不生效」）；
 * 2. 行内动作按「角色 × 单据状态」判可见（初审只对 PENDING_STATION、终审只对 PENDING_BOSS）；
 * 3. 扣款开关卡片仅 ADMIN。
 * 这些只是渲染层的双保险，真正的越权拦截在 Mock/后端的 roles + stationId 强制收敛。
 *
 * 不做的事（§2.3 / §4）：PC 不提供请假申请入口 —— ADMIN 无上级可审，服务端 POST /leave 直接回 9605，
 * 站长的单在移动端提交（站长跳过初审直达终审的设计在移动端完成）。
 */

const authStore = useAuthStore()
// 驿站名册跨页共享，取数收口到 org store（仅 ADMIN 渲染驿站筛选时使用）
const orgStore = useOrgStore()
const { stations } = storeToRefs(orgStore)
const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')
const isStationAdmin = computed(() => !!authStore.user && authStore.user.role === 'STATION_ADMIN')

/** 待办口径：管理员看待终审、站长看待初审。固定取当前在手的那一级，与用户筛选无关 */
const pendingStatus = computed(() => (isAdmin.value ? 'PENDING_BOSS' : 'PENDING_STATION'))

/** 错误码人话化：9601/9602/9603/9606/9607 都要「就地说明 + 给下一步」，通用 toast 给不了处置动作 */
const LEAVE_ERROR = {
  [LEAVE_CODE.NOT_EXISTS]: '该申请已不存在（可能已被删除），已为你刷新列表',
  [LEAVE_CODE.STATUS_INVALID]: '该申请已被处理过，已为你刷新列表，请查看最新结果',
  [LEAVE_CODE.OVERLAP]: '该时间段与已有申请重叠，请调整时间或先撤销原申请',
  [LEAVE_CODE.DATE_INVALID]: '请假日期不合法，请核对起止日期后重试',
  [LEAVE_CODE.NO_PERMISSION]: '无权操作该请假申请：站长只能审批本站、且不能审批本人提交的单',
  [LEAVE_CODE.PAYROLL_LOCKED]: '该账期工资单已生成，撤回会导致工资数据不一致，请先在财务管理中作废该单据',
  [LEAVE_CODE.EDIT_FORBIDDEN]: '当前状态不允许修改，已通过初审的单需先撤销后重新申请'
}

const loading = ref(false)
const listError = ref(false)
const list = ref([])
const total = ref(0)
const pendingTotal = ref(0)
const employees = ref([])

const query = reactive({
  status: '',
  stationId: undefined,
  employeeId: undefined,
  leaveType: '',
  dateRange: null,
  pageNum: 1,
  pageSize: 10
})

const approval = reactive({ visible: false, row: null, mode: 'approve', loading: false })
const revokeDialog = reactive({ visible: false, row: null, loading: false })
const detailVisible = ref(false)
const detailId = ref(null)

const settingsLoading = ref(false)
const settingsSaving = ref(false)
const settingsError = ref(false)
const deductEnabled = ref(false)

const isPendingView = computed(() => query.status === pendingStatus.value)
const emptyPendingTitle = computed(() => (isAdmin.value ? '当前没有待终审的请假申请' : '当前没有待初审的请假申请'))
const headerSub = computed(
  () => `${isAdmin.value ? '全域' : '本站'}视角 · 共 ${total.value} 条 · 待审批 ${pendingTotal.value} 条`
)

const canApprove = (row) =>
  (isAdmin.value && row.status === 'PENDING_BOSS') || (isStationAdmin.value && row.status === 'PENDING_STATION')
const canRevoke = (row) => isAdmin.value && row.status === 'APPROVED'

function baseParams() {
  const [start, end] = query.dateRange || []
  return {
    status: query.status || undefined,
    stationId: isAdmin.value ? query.stationId : undefined,
    employeeId: isAdmin.value ? query.employeeId : undefined,
    leaveType: query.leaveType || undefined,
    startDate: start,
    endDate: end
  }
}

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const page = await getLeaveList({ ...baseParams(), pageNum: query.pageNum, pageSize: query.pageSize })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    list.value = []
    total.value = 0
    listError.value = true
  } finally {
    loading.value = false
  }
}

/** 待审计数：不做筛选收敛，审批完一条就要立刻变 */
async function fetchPendingCount() {
  try {
    const page = await getLeaveList({ status: pendingStatus.value, pageNum: 1, pageSize: 1 })
    pendingTotal.value = page.total
  } catch (e) {
    pendingTotal.value = 0
  }
}

function refresh() {
  fetchList()
  fetchPendingCount()
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.status = pendingStatus.value
  query.stationId = undefined
  query.employeeId = undefined
  query.leaveType = ''
  query.dateRange = null
  query.pageNum = 1
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

async function loadStations() {
  if (!isAdmin.value) return
  try {
    await orgStore.loadStations()
  } catch (e) {
    /* 驿站筛选失败不阻塞审批列表：本页仍可按状态与日期办理 */
  }
}

/** 员工筛选仅 ADMIN 用；站长拿不到跨站员工名单，也不应拿到 */
async function loadEmployees() {
  if (!isAdmin.value) return
  try {
    const page = await getEmployees({ pageNum: 1, pageSize: 100 })
    employees.value = (page && page.list) || []
  } catch (e) {
    employees.value = []
  }
}

async function loadSettings() {
  if (!isAdmin.value) return
  settingsLoading.value = true
  settingsError.value = false
  try {
    const data = await getLeaveSettings()
    deductEnabled.value = data.leaveDeductEnabled === true
  } catch (e) {
    settingsError.value = true
  } finally {
    settingsLoading.value = false
  }
}

async function handleDeductChange(next) {
  const target = next === true
  try {
    await ElMessageBox.confirm(
      target
        ? '开启后：请假当天按缺勤计（扣款）。已通过的请假单会重新计入缺勤天数，直接影响当月工资单。'
        : '关闭后：请假不计缺勤（不扣款，默认）。已通过的请假单将从缺勤天数中减掉。',
      target ? '确认开启请假扣款？' : '确认关闭请假扣款？',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch (e) {
    // 用户取消：把开关显示拨回去，不能让界面停留在「已改但没保存」的假象上
    deductEnabled.value = !target
    return
  }
  settingsSaving.value = true
  try {
    const data = await saveLeaveSettings({ leaveDeductEnabled: target })
    deductEnabled.value = data.leaveDeductEnabled === true
    ElMessage.success(target ? '已开启：请假按缺勤计（扣款）' : '已关闭：请假不计缺勤（不扣款）')
  } catch (e) {
    deductEnabled.value = !target
    ElMessage.error((e && e.message) || '保存失败，请稍后重试')
  } finally {
    settingsSaving.value = false
  }
}

function openApprove(row, mode) {
  approval.row = row
  approval.mode = mode
  approval.visible = true
}

function openRevoke(row) {
  revokeDialog.row = row
  revokeDialog.visible = true
}

function openDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

const errorText = (e) => {
  // 9606 的服务端文案带上了「具体是哪个月」的工资单，比本地的通用引导更有用，优先透出
  if (e && e.code === LEAVE_CODE.PAYROLL_LOCKED) return e.message || LEAVE_ERROR[LEAVE_CODE.PAYROLL_LOCKED]
  return LEAVE_ERROR[e && e.code] || (e && e.message) || '操作失败，请稍后重试'
}

async function submitApprove(remark) {
  const row = approval.row
  if (!row) return
  approval.loading = true
  const payload = { approved: approval.mode === 'approve', remark: remark || null }
  try {
    // 端点按单据当前状态选择：初审只可能出现在 PENDING_STATION，终审只可能出现在 PENDING_BOSS
    if (row.status === 'PENDING_STATION') await stationApproveLeave(row.id, payload)
    else await finalApproveLeave(row.id, payload)
    ElMessage.success(approval.mode === 'approve' ? '已通过，审批结果已通知申请人' : '已驳回，驳回原因已通知申请人')
    approval.visible = false
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    approval.loading = false
    // 无论成败都刷新：9602 说明状态已被他人改变，列表停在旧态会持续误导
    refresh()
  }
}

async function submitRevoke(reason) {
  const row = revokeDialog.row
  if (!row) return
  revokeDialog.loading = true
  try {
    await revokeLeave(row.id, { reason })
    ElMessage.success('已撤回，考勤与算薪口径已回滚')
    revokeDialog.visible = false
  } catch (e) {
    ElMessage.error(errorText(e))
  } finally {
    revokeDialog.loading = false
    refresh()
  }
}

onMounted(() => {
  // 默认落在「待我审批」这一档：审批是待办驱动的，先看历史结果没有意义
  query.status = pendingStatus.value
  loadStations()
  loadEmployees()
  loadSettings()
  refresh()
})
</script>

<style scoped lang="scss">
.leave-page {
  .deduct-card {
    margin-bottom: var(--sp-4);

    &__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sp-3);
    }

    &__title {
      font-size: var(--fs-h3);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
    }

    &__state {
      padding: 2px var(--sp-2);
      border: 1px solid var(--state-primary-border);
      border-radius: var(--r-xs);
      background-color: var(--state-primary-bg);
      color: var(--state-primary-fg);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
    }

    /* 语义说明：浅底 + --text-2（--text-3 在浅底上不达 AA，见设计规范 §5.1 对比度禁区） */
    &__rule {
      margin: var(--sp-3) 0 0;
      padding: var(--sp-3);
      border-radius: var(--r-sm);
      background-color: var(--surface-subtle);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
      color: var(--text-2);
    }

    &__row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sp-4);
      margin-top: var(--sp-3);
    }

    &__hint {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }
  }

  .filter-card {
    margin-bottom: var(--sp-4);

    :deep(.el-form-item) {
      margin: 0 var(--sp-4) var(--sp-3) 0;
    }

    :deep(.el-form-item__label) {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }

    /* 控件宽度按内容长度分档，避免所有下拉都撑成同一个宽度（中文标签长短差别大） */
    :deep(.filter-status),
    :deep(.filter-employee) {
      width: 170px;
    }

    :deep(.filter-date) {
      width: 260px;
    }
  }

  .content-card {
    .table-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--sp-4);
      margin-bottom: var(--sp-3);
    }

    .toolbar-left {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sp-3);
    }

    .toolbar-tip {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }

    /* 待办计数用警告语义：不是危险（不阻塞作业），但比普通信息更需要被看见 */
    .toolbar-badge {
      padding: 2px var(--sp-2);
      border: 1px solid var(--state-warning-border);
      border-radius: var(--r-xs);
      background-color: var(--state-warning-bg);
      color: var(--state-warning-fg);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
    }

    /* 驳回副信息：与胶囊同一格里，靠字号层级区分主次 */
    .status-sub {
      display: block;
      margin-top: var(--sp-1);
      font-size: var(--fs-micro);
      line-height: var(--lh-micro);
      color: var(--text-2);
    }

    .pagination-wrap {
      display: flex;
      justify-content: flex-end;
      margin-top: var(--sp-4);
    }
  }
}
</style>
