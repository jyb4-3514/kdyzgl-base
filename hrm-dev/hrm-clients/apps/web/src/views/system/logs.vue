<template>
  <div class="logs-page">
    <PageHeader title="运行日志" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          aria-label="自动刷新运行日志"
          @change="toggleAutoRefresh"
        />
        <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="danger" plain :loading="clearing" @click="handleClear">清空</el-button>
      </template>
    </PageHeader>

    <!-- 统计口径写在界面上：counts 是「当前筛选结果」的量，不是全局量，用户改筛选后必须能自解释 -->
    <MiniStats
      :items="STAT_ITEMS"
      :data="counts"
      :cols="4"
      :loading="loading"
      :error="listError"
      error-text="运行日志统计加载失败"
      hint="统计口径：当前筛选结果（不含分页）。日志来自客户端运行时上报，出现异常后自动产生。"
      @retry="fetchList"
    />

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="级别">
          <el-select v-model="query.level" clearable placeholder="全部级别" class="filter-level" @change="handleSearch">
            <el-option v-for="item in LEVEL_OPTIONS" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="上报端">
          <el-select v-model="query.source" clearable placeholder="全部端" @change="handleSearch">
            <el-option v-for="(item, key) in CLIENT_LOG_SOURCE" :key="key" :label="item.label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="员工">
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
        <el-form-item label="时间区间">
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
        <el-form-item label="关键字">
          <el-input
            v-model.trim="query.keyword"
            placeholder="错误信息模糊匹配"
            clearable
            class="filter-keyword"
            @keyup.enter="handleSearch"
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
        <span class="toolbar-tip">共 {{ total }} 条 · 导出为当前页内容（单次最多 {{ query.pageSize }} 条）</span>
        <div class="toolbar-actions">
          <el-button :icon="Download" :disabled="!list.length" @click="handleExport">导出 CSV</el-button>
          <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新运行日志" @click="fetchList" />
        </div>
      </div>

      <StateBlock v-if="listError" variant="error" title="运行日志加载失败" @action="fetchList" />

      <StateBlock
        v-else-if="!loading && !list.length"
        variant="empty"
        title="暂未采集到运行日志"
        description="日志为客户端运行时上报，出现异常后自动产生"
        :action-text="hasFilter ? '重置筛选条件' : ''"
        @action="handleReset"
      />

      <template v-else>
        <el-table v-loading="loading" class="sticky-table" :data="list" border>
          <el-table-column prop="time" label="时间" width="170" />
          <el-table-column label="级别" width="90" align="center">
            <template #default="{ row }">
              <StatusTag :dict="LOG_LEVEL" :value="row.level" />
            </template>
          </el-table-column>
          <el-table-column label="上报端" width="80" align="center">
            <template #default="{ row }">{{ dictLabel(CLIENT_LOG_SOURCE, row.source) }}</template>
          </el-table-column>
          <el-table-column label="员工" width="100">
            <template #default="{ row }">{{ employeeName(row.employeeId) }}</template>
          </el-table-column>
          <el-table-column label="路由" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.route || '—' }}</template>
          </el-table-column>
          <el-table-column label="错误摘要" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message }}</template>
          </el-table-column>
          <el-table-column label="次数" width="70" align="center">
            <template #default="{ row }">{{ row.count }}</template>
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
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="fetchList"
          />
        </div>
      </template>
    </el-card>

    <ClientLogDrawer v-model="detailVisible" :row="detailRow" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { getEmployees } from '@/api/employee.js'
import { CLIENT_LOG_SOURCE, SYNC_LOG_LEVEL, dictLabel } from '@kdyzgl/shared/constants/dict'
import { clearClientLogsApi, getClientLogs } from '../../api/leave.js'
import { downloadCsv } from '../../utils/csv.js'
import MiniStats from '../../components/MiniStats.vue'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import ClientLogDrawer from './components/ClientLogDrawer.vue'

/**
 * PC 运行日志查看（P7，设计规范 §7.4，仅 ADMIN）
 *
 * 为什么只在 PC：手机屏幕读不了堆栈，运行日志是运维 / 排障场景，属 PC 工作台职责；
 * 三端都上报（PC / 移动 H5 / 安卓壳），查看与清空仅 ADMIN。
 * 不做导出全量：服务端单页上限 100，导出改为「当前页内容」，界面上明确写出来，不假装导了全量。
 */

/** 级别字典按 label 取值：SYNC_LOG_LEVEL 的键是 0/1/2，接口与 VO 用的是 'INFO'/'WARN'/'ERROR' */
const LOG_LEVEL = Object.fromEntries(Object.values(SYNC_LOG_LEVEL).map((item) => [item.label, item]))
const LEVEL_OPTIONS = Object.keys(LOG_LEVEL)
const STAT_ITEMS = [
  { key: 'total', label: '日志总数' },
  { key: 'error', label: 'ERROR' },
  { key: 'warn', label: 'WARN' },
  { key: 'sourceCount', label: '涉及端数' }
]
const POLL_INTERVAL_MS = 30 * 1000

const loading = ref(false)
const clearing = ref(false)
const listError = ref(false)
const list = ref([])
const total = ref(0)
const counts = reactive({ total: 0, error: 0, warn: 0, info: 0, sourceCount: 0 })
const employees = ref([])
const autoRefresh = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)
let pollTimer = null

const query = reactive({
  level: '',
  source: '',
  keyword: '',
  dateRange: null,
  employeeId: undefined,
  pageNum: 1,
  pageSize: 20
})

const hasFilter = computed(
  () => !!(query.level || query.source || query.keyword || query.dateRange || query.employeeId != null)
)
const headerSub = computed(
  () => `仅 ADMIN 可见 · 共 ${total.value} 条 · ${autoRefresh.value ? '每 30 秒自动刷新' : '手动刷新'}`
)

const employeeMap = computed(() => {
  const map = {}
  employees.value.forEach((item) => {
    map[item.id] = item.realName
  })
  return map
})
const employeeName = (id) => (id == null ? '—' : employeeMap.value[id] || `#${id}`)

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const [start, end] = query.dateRange || []
    const page = await getClientLogs({
      level: query.level || undefined,
      source: query.source || undefined,
      keyword: query.keyword || undefined,
      employeeId: query.employeeId,
      // 服务端按字符串比较时间，末端补到 23:59:59 才能把结束当天的日志包含进来
      startTime: start ? `${start} 00:00:00` : undefined,
      endTime: end ? `${end} 23:59:59` : undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
    Object.assign(counts, page.counts || {})
  } catch (e) {
    list.value = []
    total.value = 0
    Object.assign(counts, { total: 0, error: 0, warn: 0, info: 0, sourceCount: 0 })
    listError.value = true
  } finally {
    loading.value = false
  }
}

async function loadEmployees() {
  try {
    const page = await getEmployees({ pageNum: 1, pageSize: 100 })
    employees.value = (page && page.list) || []
  } catch (e) {
    employees.value = []
  }
}

function refresh() {
  fetchList()
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.level = ''
  query.source = ''
  query.keyword = ''
  query.dateRange = null
  query.employeeId = undefined
  query.pageNum = 1
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

/** 自动刷新默认关：排障时页面自己跳动会打断阅读，必须由用户显式开启 */
function toggleAutoRefresh(next) {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (next) pollTimer = setInterval(fetchList, POLL_INTERVAL_MS)
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleClear() {
  try {
    await ElMessageBox.confirm(
      '清空后历史运行日志不可恢复（环形缓冲仅保留最近 200 条，清空即全删）。',
      '确认清空运行日志？',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  clearing.value = true
  try {
    const res = await clearClientLogsApi()
    ElMessage.success(`已清空 ${(res && res.cleared) || 0} 条运行日志`)
    query.pageNum = 1
    fetchList()
  } catch (e) {
    ElMessage.error((e && e.message) || '清空失败，请稍后重试')
  } finally {
    clearing.value = false
  }
}

function handleExport() {
  if (!list.value.length) {
    ElMessage.warning('当前页没有可导出的日志')
    return
  }
  const header = [
    '时间',
    '级别',
    '上报端',
    '员工',
    '路由',
    '错误摘要',
    '次数',
    '请求',
    'HTTP状态',
    '业务码',
    '耗时(ms)'
  ]
  const rows = list.value.map((row) => [
    row.time,
    row.level,
    dictLabel(CLIENT_LOG_SOURCE, row.source),
    employeeName(row.employeeId),
    row.route || '',
    row.message,
    row.count,
    row.method ? `${row.method} ${row.path || ''}` : '',
    row.status == null ? '' : row.status,
    row.code == null ? '' : row.code,
    row.duration == null ? '' : row.duration
  ])
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  downloadCsv(`运行日志_${stamp}.csv`, header, rows)
}

onMounted(() => {
  loadEmployees()
  fetchList()
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="scss">
.logs-page {
  .filter-card {
    margin-bottom: var(--sp-4);

    :deep(.el-form-item) {
      margin: 0 var(--sp-4) var(--sp-3) 0;
    }

    :deep(.el-form-item__label) {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }

    :deep(.el-select),
    :deep(.filter-keyword) {
      width: 170px;
    }

    :deep(.filter-date) {
      width: 260px;
    }
  }

  .content-card {
    .table-toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--sp-3);
      margin-bottom: var(--sp-3);
    }

    .toolbar-tip {
      font-size: var(--fs-caption);
      color: var(--text-2);
    }

    .toolbar-actions {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
    }

    .pagination-wrap {
      display: flex;
      justify-content: flex-end;
      margin-top: var(--sp-4);
    }
  }
}
</style>
