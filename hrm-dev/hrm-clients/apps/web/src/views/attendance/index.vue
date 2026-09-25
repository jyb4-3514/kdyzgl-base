<template>
  <div class="attendance-page">
    <PageHeader title="考勤管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-select
          v-if="isAdmin"
          v-model="stationId"
          class="header-station"
          clearable
          placeholder="全部驿站"
          @change="handleStationChange"
        >
          <!-- 「全部驿站」(null) 为 ADMIN 默认值：把"全域"这个数据范围显式呈现，而不是默认钻进第一个驿站（A9-3） -->
          <el-option label="全部驿站" :value="null" />
          <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
        </el-select>
        <!-- 导出（需求5）：位于「驿站选择」右侧、「刷新」左侧（B5.1） -->
        <el-button
          :icon="Download"
          :disabled="!total"
          :title="total ? '按当前筛选条件导出考勤记录' : '当前筛选无数据可导出'"
          @click="openExport"
        >
          导出
        </el-button>
        <el-button :icon="Refresh" @click="refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 今日概况：口径与 GET /attendance/summary 一致，非 ADMIN 由服务端收敛本站 -->
    <MiniStats
      title="今日打卡概况"
      :cols="6"
      :items="SUMMARY_CARDS"
      :data="summary"
      :loading="summaryLoading"
      :error="summaryError"
      error-text="打卡概况加载失败"
      :hint="summaryHint"
      @retry="fetchSummary"
    />

    <!-- 打卡规则按驿站一栈一档，「全部驿站」下无处安放：给明确说明而不是让它掉进错误态（A9-3） -->
    <el-card v-if="stationAll" shadow="never" class="scope-note-card">
      <p class="scope-note">打卡规则按驿站单独配置，当前为「全部驿站」视图。选择具体驿站后可查看并编辑该站规则。</p>
    </el-card>
    <RuleCard
      v-else
      :rule="rule"
      :station-id="effectiveStationId"
      :station-name="currentStationName"
      :can-write="isAdmin"
      :loading="ruleLoading"
      :saving="savingRule"
      :missing="ruleMissing"
      :error="ruleError"
      @save="handleSaveRule"
      @retry="fetchRule"
    />

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="打卡日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="filter-date"
          />
        </el-form-item>
        <el-form-item label="员工">
          <el-select
            v-model="query.employeeId"
            clearable
            filterable
            :disabled="stationAll"
            :placeholder="stationAll ? '请先选择驿站' : '全部员工'"
            :title="stationAll ? '员工名册按驿站提供，选择具体驿站后可筛选员工' : ''"
          >
            <el-option v-for="item in employeeOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <!-- A9-2：契约确未解构 checkType（已确认不支持），按"禁止保留可用但无效的第三态"处置为禁用 + title 说明 -->
        <el-form-item label="打卡类型">
          <el-select
            v-model="query.checkType"
            clearable
            disabled
            placeholder="全部类型"
            title="演示契约暂未支持按打卡类型筛选（待后端补 checkType 参数）"
          >
            <el-option v-for="(item, key) in CHECK_TYPE" :key="key" :label="item.label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option v-for="(item, key) in ATTENDANCE_STATUS" :key="key" :label="item.label" :value="key" />
          </el-select>
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
          <span class="toolbar-tip">
            共 {{ total }} 条 · 日期 {{ dateRangeText
            }}<template v-if="filterCount"> · 已筛选 {{ filterCount }} 个条件</template>
          </span>
        </div>
        <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="fetchList" />
      </div>

      <StateBlock v-if="listError" variant="error" title="打卡记录加载失败" @action="fetchList" />

      <StateBlock
        v-else-if="!loading && !list.length"
        variant="empty"
        :title="filterCount ? '当前筛选条件下没有打卡记录' : '所选日期区间内暂无打卡记录'"
        action-text="重置筛选条件"
        @action="handleReset"
      />

      <template v-else>
        <el-table v-loading="loading" class="sticky-table" :data="list" border>
          <el-table-column prop="employeeName" label="员工" min-width="100" fixed="left" />
          <!-- 驿站列只在 ADMIN 视角出现：接口不返回 stationName，非 ADMIN 的数据范围本就是本站，列冗余 -->
          <el-table-column v-if="isAdmin" label="驿站" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ stationNameOf(row.stationId) }}</template>
          </el-table-column>
          <el-table-column prop="workDate" label="打卡日期" width="120" />
          <!-- 时段列：频次为 4 时同一天有上/下午两对卡，只靠打卡类型看不出是哪一段 -->
          <el-table-column label="打卡时段" min-width="110">
            <template #default="{ row }">{{ row.periodName || '—' }}</template>
          </el-table-column>
          <el-table-column label="打卡类型" width="120" align="center">
            <template #default="{ row }">
              {{ dictLabel(CHECK_TYPE, row.checkType) }}
              <!-- 补卡来源标记：补录卡与设备打卡在同一张表里，只靠时间看不出差别 -->
              <span v-if="row.source === 'MAKEUP'" class="src-chip">补卡</span>
            </template>
          </el-table-column>
          <el-table-column prop="checkTime" label="打卡时间" min-width="160" />
          <el-table-column label="校验方式" width="120" align="center">
            <template #default="{ row }">{{ dictLabel(CHECK_MODE, row.checkMode) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <StatusTag :dict="ATTENDANCE_STATUS" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="fetchList"
          />
        </div>
      </template>
    </el-card>

    <RecordDrawer v-model="drawerVisible" :record="activeRecord" :radius="rule ? rule.radius : null" />

    <!-- 导出确认（需求5）：范围回显与列表筛选同源，导出参数由页面统一给出（B5.2） -->
    <ExportConfirmDialog
      v-model="exportVisible"
      :filters="exportFilters"
      :total="total"
      :exporting="exporting"
      :error="exportError"
      @confirm="handleExport"
    />

    <!-- 补卡审批仅管理员（ADMIN）可见：站长无审批权，接口也会回 403，前端不渲染入口避免误导 -->
    <!-- TODO(扩展): ①「缺卡」卡加下钻（筛选 status=ABNORMAL）；② 补卡审批改为页内 Tab 视图，
         避免高频动作被压在页面末尾（A9-5） -->
    <MakeupApproval v-if="isAdmin" :stations="stations" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useOrgStore } from '../../stores/org.js'
import { formatDateCompact, saveResponseFile } from '@admin/utils/download'
import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode'
import { ATTENDANCE_STATUS, CHECK_MODE, CHECK_TYPE, dictLabel } from '@kdyzgl/shared/constants/dict'
import { addDays, formatDate } from '@kdyzgl/shared/domain/time.js'
import {
  exportAttendance,
  getAttendanceRecords,
  getAttendanceRule,
  getAttendanceSummary,
  getSchedules,
  saveAttendanceRule
} from '../../api/attendance.js'
import PageHeader from '../../components/PageHeader.vue'
import MiniStats from '../../components/MiniStats.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import RuleCard from './components/RuleCard.vue'
import RecordDrawer from './components/RecordDrawer.vue'
import MakeupApproval from './components/MakeupApproval.vue'
import ExportConfirmDialog from './components/ExportConfirmDialog.vue'

/**
 * 考勤管理（demo-ui-redesign.md 5.3 列表页模板）
 *
 * 页面围绕「一个驿站」组织：打卡规则是一栈一档，规则里的围栏与 WiFi 白名单决定了本站所有打卡判定，
 * 所以驿站切换放在页头统一控制概况、规则、记录三块，而不是各块各放一个下拉（避免出现三块数据不同源）。
 *
 * 写操作（规则保存）在服务端仅放行 ADMIN，非 ADMIN 渲染为只读表单并给出说明，
 * 不做本地权限推断之外的事（数据范围收敛仍由服务端强制覆盖）。
 */

const RECENT_DAYS = 30

const authStore = useAuthStore()
// 驿站名册跨页共享，取数收口到 org store；本页自己的「选中驿站」仍由 stationId 持有（默认全域）
const orgStore = useOrgStore()
const { stations } = storeToRefs(orgStore)

const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')
const currentUser = computed(() => authStore.user || {})

const SUMMARY_CARDS = [
  { key: 'shouldCount', label: '应到' },
  { key: 'actualCount', label: '实到' },
  { key: 'normalCount', label: '正常' },
  { key: 'lateCount', label: '迟到' },
  { key: 'earlyLeaveCount', label: '早退' },
  { key: 'absentCount', label: '缺卡' }
]

const loading = ref(false)
const summaryLoading = ref(false)
const summaryError = ref(false)
const ruleLoading = ref(false)
const ruleError = ref(false)
const ruleMissing = ref(false)
const savingRule = ref(false)
const listError = ref(false)

const stationId = ref(null)
const summary = ref(null)
const rule = ref(null)
const list = ref([])
const total = ref(0)
const employeeOptions = ref([])
const updatedAt = ref('')

const drawerVisible = ref(false)
const activeRecord = ref(null)

// 导出（需求5）：exporting 控制按钮 loading，exportError 用于弹窗内提示且不关闭弹窗
const exportVisible = ref(false)
const exporting = ref(false)
const exportError = ref('')

// 默认查近 30 天：排班与打卡记录的种子窗口就是 30 天，默认区间与可演示数据量对齐
const dateRange = ref([formatDate(addDays(new Date(), -(RECENT_DAYS - 1))), formatDate(new Date())])

// TODO(扩展): 筛选条件写回 URL（A5-2 的 useQuerySync），避免刷新即丢筛选
const query = reactive({ employeeId: undefined, checkType: undefined, status: undefined, pageNum: 1, pageSize: 20 })

/** 数据范围：ADMIN 取页头所选驿站，其他角色取本人归属驿站（服务端会再次强制覆盖） */
const effectiveStationId = computed(() => (isAdmin.value ? stationId.value : currentUser.value.stationId || null))

/** 全部驿站（ADMIN 未选具体驿站）：ADMIN 传空即全域，但「打卡规则」必须具体驿站，故需单独交互（A9-3） */
const stationAll = computed(() => effectiveStationId.value == null)

const currentStationName = computed(() => {
  if (!isAdmin.value) return currentUser.value.stationName || ''
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : ''
})

const headerSub = computed(
  () =>
    `${isAdmin.value ? '数据范围：全域（可切换驿站）' : `数据范围：本站 ${currentStationName.value || ''}`}` +
    ` · 记录共 ${total.value} 条 · 更新于 ${updatedAt.value || '—'}`
)

const summaryHint = computed(
  () =>
    `口径：应到 = 当日排班人数，实到 = 有有效上班卡的人数；校验未通过的异常卡不计入实到与正常/迟到/早退 · 统计日期 ${formatDate(new Date())}`
)

/** 已筛选条件数：日期区间是默认条件（近 30 天），不计入，否则「已筛选」会恒为 1 */
const filterCount = computed(() => {
  let count = 0
  if (query.employeeId) count += 1
  if (query.checkType) count += 1
  if (query.status) count += 1
  return count
})

const dateRangeText = computed(() => {
  const [start, end] = dateRange.value || []
  return start && end ? `${start} ~ ${end}` : '未限定'
})

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

function stationNameOf(id) {
  const hit = stations.value.find((item) => item.id === id)
  return hit ? hit.stationName : '—'
}

async function loadStations() {
  try {
    // ADMIN 不把驿站默认成第一个：数据范围是全域，页面必须让这一点可见（A9-3）
    await orgStore.loadStations()
  } catch (e) {
    /* 拦截器已统一提示；此处不阻塞，规则/记录区会各自进入错误态 */
  }
}

async function fetchSummary() {
  summaryLoading.value = true
  summaryError.value = false
  try {
    summary.value = await getAttendanceSummary({ stationId: effectiveStationId.value })
  } catch (e) {
    summary.value = null
    summaryError.value = true
  } finally {
    summaryLoading.value = false
  }
}

async function fetchRule() {
  // 「全部驿站」下没有单站规则可取：直接清空而不是发一个必然 400 的请求
  if (stationAll.value) {
    rule.value = null
    ruleError.value = false
    ruleMissing.value = false
    return
  }
  ruleLoading.value = true
  ruleError.value = false
  ruleMissing.value = false
  try {
    rule.value = await getAttendanceRule(effectiveStationId.value)
  } catch (e) {
    rule.value = null
    // 9101 是「该驿站还没配规则」这一业务事实，与加载失败必须区分：
    // 前者引导管理员填写并保存，后者才是刷新重试
    if (e && e.code === ATTENDANCE_CODE.RULE_NOT_CONFIGURED) ruleMissing.value = true
    else ruleError.value = true
  } finally {
    ruleLoading.value = false
  }
}

/**
 * 员工下拉的数据源取「周排班矩阵的员工名册」而不是 /employees：
 * /employees 是 ADMIN 专属接口，站长打开本页会 403；排班矩阵对 ADMIN 与站长都开放，且天然是本站名册
 */
async function loadEmployeeOptions() {
  if (effectiveStationId.value == null) {
    // 清空而不是保留上一个驿站的名册，否则「全部驿站」下会残留可点的错误候选
    employeeOptions.value = []
    return
  }
  try {
    const matrix = await getSchedules({ stationId: effectiveStationId.value })
    employeeOptions.value = matrix.employees.map((item) => ({ id: item.employeeId, name: item.employeeName }))
  } catch (e) {
    employeeOptions.value = []
  }
}

/**
 * 筛选参数（不含分页）：列表与导出共用这一份，保证「导出结果 = 屏幕上看到的筛选结果」（B5.2 硬要求）。
 * 不在导出时重新拼参数，是本次实现最重要的一条约束。
 * TODO(扩展): 契约补 checkType 后在此加回该参数（A9-2 依赖 R-3），当前控件已禁用，不发无效参数。
 */
function buildFilterParams() {
  const [start, end] = dateRange.value || []
  return {
    stationId: effectiveStationId.value,
    employeeId: query.employeeId,
    status: query.status,
    startDate: start,
    endDate: end
  }
}

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const page = await getAttendanceRecords({
      ...buildFilterParams(),
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
    updatedAt.value = stamp()
  } catch (e) {
    list.value = []
    total.value = 0
    listError.value = true
  } finally {
    loading.value = false
  }
}

/** 导出范围回显：逐项列出当前筛选，用户在确认弹窗里能核对（B5.2 ①） */
const exportFilters = computed(() => {
  const filters = [
    {
      label: '驿站',
      value: isAdmin.value ? currentStationName.value || '全部驿站' : currentStationName.value || '本站'
    },
    { label: '日期', value: dateRangeText.value },
    {
      label: '员工',
      value: query.employeeId
        ? employeeOptions.value.find((item) => item.id === query.employeeId)?.name || '指定员工'
        : '全部'
    },
    { label: '状态', value: query.status ? dictLabel(ATTENDANCE_STATUS, query.status) : '全部' }
  ]
  return filters
})

function openExport() {
  exportError.value = ''
  exportVisible.value = true
}

/** 导出：blob 落盘走一期工具 saveResponseFile，文件名优先取响应头（服务端已给中文名） */
async function handleExport() {
  exporting.value = true
  exportError.value = ''
  try {
    const response = await exportAttendance(buildFilterParams(), { silent: true })
    saveResponseFile(response, `考勤记录_${formatDateCompact()}.csv`)
    ElMessage.success('导出完成')
    exportVisible.value = false
  } catch (e) {
    // 弹窗内提示且不关闭，允许用户改条件后重试
    exportError.value = (e && e.message) || '导出失败，请稍后重试'
  } finally {
    exporting.value = false
  }
}

async function handleSaveRule(payload) {
  savingRule.value = true
  try {
    rule.value = await saveAttendanceRule(payload)
    ruleMissing.value = false
    ElMessage.success('打卡规则已保存，即时生效于本站后续打卡判定')
  } catch (e) {
    /* 400 字段校验 / 4001 驿站不存在由拦截器按服务端文案提示，不覆盖为通用文案 */
  } finally {
    savingRule.value = false
  }
}

function openDetail(row) {
  activeRecord.value = row
  drawerVisible.value = true
}

/** 切换驿站：员工名册随驿站变，选中项必须先清掉，否则会带着上一个驿站的 employeeId 查询 */
function handleStationChange() {
  query.employeeId = undefined
  query.pageNum = 1
  refreshPage()
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.employeeId = undefined
  query.checkType = undefined
  query.status = undefined
  query.pageNum = 1
  dateRange.value = [formatDate(addDays(new Date(), -(RECENT_DAYS - 1))), formatDate(new Date())]
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

/** 概况 / 规则 / 名册 / 记录一起刷新：三者都以当前驿站为准，分开刷新会出现混站显示 */
function refreshPage() {
  fetchSummary()
  fetchRule()
  loadEmployeeOptions()
  fetchList()
}

onMounted(() => {
  // 概况 / 规则 / 记录都只以「当前驿站 id」为输入（ADMIN 默认全域 = null），与驿站名册互不依赖：
  // 并行发出而不是串行 await 名册，否则首个 paint 会先把指标条渲染成「—」（失败语义）、约 300ms 后才转成骨架再出数
  if (isAdmin.value) loadStations()
  refreshPage()
})
</script>

<style scoped lang="scss">
.attendance-page {
  .header-station {
    width: 160px;
  }

  // 「全部驿站」下的规则说明卡（A9-3）：用说明替代错误态，避免用户以为是系统故障
  .scope-note-card {
    margin-bottom: var(--sp-4);
  }

  .scope-note {
    margin: 0;
    font-size: var(--fs-body);
    line-height: var(--lh-body);
    color: var(--text-2);
  }

  // 筛选卡样式与包裹/工单页同规范（demo-ui-redesign.md C-P8）：
  // 该规范有意不抽组件——筛选字段随页面不同，抽象出来反而多一层配置
  .filter-card {
    margin-bottom: var(--sp-4);

    :deep(.el-form-item) {
      margin: 0 var(--sp-4) var(--sp-3) 0;
    }

    :deep(.el-form-item:last-child) {
      margin-right: 0;
      margin-bottom: 0;
    }

    :deep(.el-form-item__label) {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }

    :deep(.el-select) {
      width: 160px;
    }

    :deep(.filter-date) {
      width: 260px;
    }
  }

  .content-card {
    margin-bottom: 0;
  }

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
    gap: var(--sp-1);
  }

  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  // 补卡来源标记：与「正常打卡」区分靠文字而非仅颜色，色觉障碍用户同样可辨
  .src-chip {
    margin-left: var(--sp-1);
    padding: 0 var(--sp-1);
    border: 1px solid var(--state-warning-border);
    border-radius: var(--r-xs);
    background-color: var(--state-warning-bg);
    color: var(--state-warning-fg);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
  }
}
</style>
