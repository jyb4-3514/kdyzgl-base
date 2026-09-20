<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { ATTENDANCE_CODE } from '@/shared/constants/errorCode'
import { CHECK_TYPE, dictLabel } from '@/shared/constants/dict'
import { approveMakeup, getMakeupList } from '../../../api/attendance.js'
import StateBlock from '../../../components/StateBlock.vue'
import StatusTag from '../../../components/StatusTag.vue'

/**
 * 补卡审批区块（T19，仅 ADMIN 有权审批，由父页按角色决定是否挂载）
 *
 * 为什么独立成区块而不是并入上面的打卡记录表：审批是「全域待办」语义，
 * 与页头单站视角不同源；独立后自带筛选，不会出现「页头选了城东、待办却跨站」的口径歧义。
 *
 * 默认只查 PENDING：审批是待办驱动的，历史已审批记录通过状态筛选或列表内「详情」查看。
 *
 * TODO(扩展): 移动端补卡两页（staff/makeupList.vue、boss/makeupApproval.vue）的状态筛选 chip 已重复三处，
 * 本期裁决（Q10）不重构，留待下一轮抽 FilterChips 时连同请假三页一起替换。
 */
defineProps({
  stations: { type: Array, default: () => [] }
})

/**
 * 补卡状态字典：移动端真源在 mobile/constants/makeup.js，PC 侧只读展示，就地声明同一口径
 * TODO(扩展): 补卡字典迁入 shared/constants/dict.js 后改为共用导入，两端不再各写一份
 */
const MAKEUP_STATUS = {
  PENDING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

/** 通用 toast 只说「该时段当日已有补卡申请…」，这里补上「所以不用再点什么」，降低无效重试 */
const APPROVE_ERROR = {
  [ATTENDANCE_CODE.MAKEUP_DUPLICATE]: '该时段当日已有补卡申请或已正常打卡，无需审批',
  [ATTENDANCE_CODE.MAKEUP_STATUS_INVALID]: '该申请已被处理过，已为你刷新列表，请查看最新结果'
}

const DIALOG_TITLE = { approve: '通过补卡申请', reject: '驳回补卡申请', view: '补卡申请详情' }
/** 审批意见模板：驿站现场高频场景，预填比让管理员空手写更快 */
const REMARK_PLACEHOLDER = { approve: '如：情况属实，予以补卡', reject: '如：缺少证明材料，不予补卡' }

const loading = ref(false)
const listError = ref(false)
const list = ref([])
const total = ref(0)
const pendingTotal = ref(0)

const query = reactive({ stationId: undefined, status: 'PENDING', dateRange: null, pageNum: 1, pageSize: 10 })
const dialog = reactive({ visible: false, mode: 'approve', row: null, remark: '', loading: false })

const dialogTitle = computed(() => DIALOG_TITLE[dialog.mode] || '补卡申请')
/** 通过即补录打卡记录，这一步会改动考勤口径，必须在弹窗里讲清楚 */
const approveTip = computed(() =>
  dialog.mode === 'approve' ? '通过后将按该申请时段的规定时间补录打卡记录，并标记为补卡来源' : ''
)

function baseParams() {
  const [start, end] = query.dateRange || []
  return { stationId: query.stationId, status: query.status, startDate: start, endDate: end }
}

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const page = await getMakeupList({ ...baseParams(), pageNum: query.pageNum, pageSize: query.pageSize })
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

/** 待审批计数：跨驿站口径（与筛选无关），审批完一条就应立刻变化，故与列表一起刷新 */
async function fetchPendingCount() {
  try {
    const page = await getMakeupList({ status: 'PENDING', pageNum: 1, pageSize: 1 })
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
  query.stationId = undefined
  query.status = 'PENDING'
  query.dateRange = null
  query.pageNum = 1
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

function openDialog(row, mode) {
  dialog.row = row
  dialog.mode = mode
  dialog.remark = ''
  dialog.visible = true
}

async function submit() {
  const row = dialog.row
  if (!row) return
  dialog.loading = true
  try {
    // silent：通用 toast 无法区分 9108/9109 的处置差异，这里自己给文案
    await approveMakeup(
      row.id,
      { approved: dialog.mode === 'approve', approveRemark: dialog.remark.trim() || null },
      { silent: true }
    )
    ElMessage.success(dialog.mode === 'approve' ? '已通过，打卡记录已补录' : '已驳回，申请人可见审批意见')
    dialog.visible = false
  } catch (e) {
    ElMessage.error(APPROVE_ERROR[e && e.code] || (e && e.message) || '审批失败，请稍后重试')
  } finally {
    dialog.loading = false
    // 无论成败都刷新：9109 说明状态已被他人改变，列表停留在旧态会持续误导
    refresh()
  }
}

onMounted(refresh)
</script>

<template>
  <el-card shadow="never" class="makeup-card">
    <div class="makeup-head">
      <div class="makeup-head__title">
        <span class="makeup-head__text">补卡审批</span>
        <span v-if="pendingTotal" class="makeup-head__badge" role="status">{{ pendingTotal }} 条待审批</span>
        <span class="makeup-head__hint">审批范围：全域（不受页头驿站选择影响）</span>
      </div>
      <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新补卡审批列表" @click="refresh" />
    </div>

    <el-form inline class="makeup-filter" @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态" @change="handleSearch">
          <el-option v-for="(item, key) in MAKEUP_STATUS" :key="key" :label="item.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="驿站">
        <el-select v-model="query.stationId" clearable placeholder="全部驿站" @change="handleSearch">
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="补卡日期">
        <el-date-picker
          v-model="query.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="makeup-filter__date"
          @change="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <StateBlock v-if="listError" variant="error" title="补卡审批列表加载失败" @action="refresh" />

    <StateBlock
      v-else-if="!loading && !list.length"
      variant="empty"
      :title="query.status === 'PENDING' ? '当前没有待审批的补卡申请' : '当前筛选条件下没有补卡申请'"
      :description="query.status === 'PENDING' ? '可切换状态查看历史审批结果与意见' : ''"
      action-text="重置筛选条件"
      @action="handleReset"
    />

    <template v-else>
      <el-table v-loading="loading" class="sticky-table" :data="list" border>
        <el-table-column prop="employeeName" label="申请人" min-width="90" fixed="left" />
        <el-table-column label="驿站" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.stationName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="workDate" label="补卡日期" width="120" />
        <el-table-column label="时段" min-width="100">
          <template #default="{ row }">{{ row.periodName || '—' }}</template>
        </el-table-column>
        <el-table-column label="卡类型" width="90" align="center">
          <template #default="{ row }">{{ dictLabel(CHECK_TYPE, row.checkType) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="补卡理由" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applyTime" label="申请时间" min-width="160" />
        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <StatusTag :dict="MAKEUP_STATUS" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="openDialog(row, 'approve')">通过</el-button>
              <el-button link type="danger" @click="openDialog(row, 'reject')">驳回</el-button>
            </template>
            <el-button v-else link type="primary" @click="openDialog(row, 'view')">详情</el-button>
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

    <el-dialog v-model="dialog.visible" :title="dialogTitle" width="480px" :close-on-click-modal="false">
      <template v-if="dialog.row">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="申请人">{{ dialog.row.employeeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="驿站">{{ dialog.row.stationName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="补卡日期">{{ dialog.row.workDate }}</el-descriptions-item>
          <el-descriptions-item label="时段 · 卡类型">
            {{ `${dialog.row.periodName || '—'} · ${dictLabel(CHECK_TYPE, dialog.row.checkType)}` }}
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ dialog.row.applyTime }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :dict="MAKEUP_STATUS" :value="dialog.row.status" />
          </el-descriptions-item>
          <el-descriptions-item label="补卡理由" :span="2">{{ dialog.row.reason || '—' }}</el-descriptions-item>
          <!-- 已审批的历史单才有结果与意见，待审批单不渲染空行 -->
          <template v-if="dialog.row.status !== 'PENDING'">
            <el-descriptions-item label="审批人">{{ dialog.row.approverName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="审批时间">{{ dialog.row.approveTime || '—' }}</el-descriptions-item>
            <el-descriptions-item label="审批意见" :span="2">{{
              dialog.row.approveRemark || '—'
            }}</el-descriptions-item>
          </template>
        </el-descriptions>

        <template v-if="dialog.mode !== 'view'">
          <p v-if="approveTip" class="approve-tip">{{ approveTip }}</p>
          <el-form label-position="top">
            <el-form-item label="审批意见（选填）">
              <el-input
                v-model="dialog.remark"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                :placeholder="REMARK_PLACEHOLDER[dialog.mode]"
                aria-label="审批意见"
              />
            </el-form-item>
          </el-form>
        </template>
      </template>

      <template #footer>
        <el-button @click="dialog.visible = false">{{ dialog.mode === 'view' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="dialog.mode === 'approve'" type="primary" :loading="dialog.loading" @click="submit">
          确认通过
        </el-button>
        <el-button v-else-if="dialog.mode === 'reject'" type="danger" :loading="dialog.loading" @click="submit">
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped lang="scss">
.makeup-card {
  margin-top: var(--sp-4);

  .makeup-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);
    margin-bottom: var(--sp-3);

    &__title {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sp-2);
    }

    &__text {
      font-size: var(--fs-h3);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
    }

    // 待办计数用警告语义：与「正常流转」区分，但不是危险（不阻塞作业）
    &__badge {
      padding: 2px var(--sp-2);
      border: 1px solid var(--state-warning-border);
      border-radius: var(--r-xs);
      background-color: var(--state-warning-bg);
      color: var(--state-warning-fg);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
    }

    &__hint {
      font-size: var(--fs-caption);
      color: var(--text-3);
    }
  }

  // 与页内其他筛选卡同规范（C-P8）
  .makeup-filter {
    margin-bottom: var(--sp-3);

    :deep(.el-form-item) {
      margin: 0 var(--sp-4) var(--sp-3) 0;
    }

    :deep(.el-form-item__label) {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }

    :deep(.el-select) {
      width: 160px;
    }

    &__date {
      width: 260px;
    }
  }

  .approve-tip {
    margin: var(--sp-4) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
