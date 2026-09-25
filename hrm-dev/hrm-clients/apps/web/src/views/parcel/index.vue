<template>
  <div class="parcel-page">
    <PageHeader title="包裹管理" :sub="headerSub" :loading="loading || loadingSummary">
      <template #actions>
        <el-button :disabled="!total" :loading="exporting" @click="handleExport">导出 CSV</el-button>
        <span class="header-tip">最多导出前 {{ EXPORT_LIMIT }} 条</span>
        <el-button :icon="Refresh" @click="refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 统计条：口径与看板一致（GET /parcels/summary），非 ADMIN 由 Mock 强制收敛为本驿站 -->
    <MiniStats
      title="今日指标"
      :cols="6"
      :items="summaryCards"
      :data="summary"
      :loading="loadingSummary"
      :error="summaryError"
      error-text="包裹指标加载失败"
      :hint="scopeHint"
      @retry="fetchSummary"
    />

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item v-if="isAdmin" label="驿站">
          <el-select v-model="query.stationId" clearable placeholder="全部驿站">
            <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option v-for="(item, key) in PARCEL_STATUS" :key="key" :label="item.label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="filter-date"
          />
        </el-form-item>
        <el-form-item label="运单号">
          <el-input
            v-model.trim="query.waybillNo"
            placeholder="运单号（精确匹配）"
            clearable
            class="filter-input"
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
        <div class="toolbar-left">
          <span class="toolbar-tip">共 {{ total }} 条 · 已筛选 {{ filterCount }} 个条件</span>
        </div>
        <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="fetchList" />
      </div>

      <!-- 错误态与空态文案必须不同：改前接口失败会渲染成"没有符合条件的包裹"，用户会误判为业务为空（修 P14） -->
      <StateBlock v-if="listError" variant="error" title="包裹列表加载失败" @action="fetchList" />

      <StateBlock
        v-else-if="!loading && !list.length"
        variant="empty"
        :title="filterCount ? '当前筛选条件下没有包裹' : '暂无包裹数据'"
        :action-text="filterCount ? '清空筛选' : ''"
        @action="handleReset"
      />

      <!-- TODO(扩展): 可补 8 行行骨架，进一步弱化首次加载的遮罩感（当前保留表头的 v-loading 已满足"不整页遮罩"） -->
      <template v-else>
        <el-table v-loading="loading" class="sticky-table" :data="list" border>
          <el-table-column prop="waybillNo" label="运单号" min-width="140" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <StatusTag :dict="PARCEL_STATUS" :value="row.status" :variant="row.status === 4 ? 'outline' : 'soft'" />
            </template>
          </el-table-column>
          <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
          <el-table-column prop="receiverName" label="收件人" min-width="90" />
          <el-table-column prop="receiverPhone" label="收件人手机号" min-width="130" />
          <el-table-column prop="shelfCode" label="货架码" width="100" align="center" />
          <el-table-column prop="inboundTime" label="入库时间" min-width="160" />
          <el-table-column label="取件时间" min-width="160">
            <template #default="{ row }">{{ row.pickupTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
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

    <el-drawer v-model="detailVisible" title="包裹详情" :size="drawerSize" destroy-on-close>
      <div v-loading="loadingDetail" class="drawer-body">
        <el-descriptions v-if="detail" :column="2" size="small" border>
          <el-descriptions-item label="运单号">{{ detail.waybillNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag
              :dict="PARCEL_STATUS"
              :value="detail.status"
              :variant="detail.status === 4 ? 'outline' : 'soft'"
            />
          </el-descriptions-item>
          <el-descriptions-item label="归属驿站">{{ detail.stationName }}</el-descriptions-item>
          <el-descriptions-item label="收件人">{{ detail.receiverName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="收件人手机号">{{ detail.receiverPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="货架码">{{ detail.shelfCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="入库时间">{{ detail.inboundTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="取件时间">{{ detail.pickupTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="同步批次号">{{ detail.syncBatchNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <StateBlock v-else-if="!loadingDetail" variant="empty" title="未获取到包裹信息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useOrgStore } from '../../stores/org.js'
import { PARCEL_STATUS } from '@kdyzgl/shared/constants/dict'
import { getParcelDetail, getParcels, getParcelSummary } from '../../api/parcel.js'
import { downloadCsv } from '../../utils/csv.js'
import PageHeader from '../../components/PageHeader.vue'
import MiniStats from '../../components/MiniStats.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'

/**
 * 包裹管理（T15，demo-ui-redesign.md 5.3 列表页模板）
 * 交互取舍不变：组合筛选 + 详情抽屉 + 统计条（先看量再看单条），导出走前端生成 CSV；
 * 本次只改呈现与状态处理，并补上「筛选条件与 URL 同步」（刷新不再丢筛选）。
 */

/** 单次导出上限：Mock 未提供 /parcels/export，只能分页拉取后本地生成，控量避免 20 万行卡死浏览器 */
const EXPORT_LIMIT = 500
const PAGE_SIZE = 100

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
// 驿站名册跨页共享，取数收口到 org store；本页筛选用的 stationId 仍属页面状态
const orgStore = useOrgStore()
const { stations } = storeToRefs(orgStore)

const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')

const loading = ref(false)
const loadingSummary = ref(false)
const loadingDetail = ref(false)
const exporting = ref(false)
const list = ref([])
const total = ref(0)
const summary = ref(null)
const dateRange = ref([])
const detailVisible = ref(false)
const detail = ref(null)
const listError = ref(false)
const summaryError = ref(false)
const updatedAt = ref('')

/**
 * 抽屉宽度统一到 --drawer-w，并在窄视口退化为 92vw（C-P9，修 P16 的 460/520/560 三套宽度）
 * TODO(扩展): 三期可改为按内容量自适应宽度
 */
const drawerSize = 'min(var(--drawer-w), 92vw)'

const query = reactive({ stationId: undefined, status: undefined, waybillNo: '', pageNum: 1, pageSize: 20 })

const summaryCards = [
  { key: 'parcelTotal', label: '包裹总量' },
  { key: 'todayInbound', label: '今日入库' },
  { key: 'todayPickup', label: '今日取件' },
  { key: 'pendingPickup', label: '在库待取' },
  { key: 'abnormalCount', label: '异常件' },
  { key: 'pickupRate', label: '今日取件率', format: (value) => `${((Number(value) || 0) * 100).toFixed(1)}%` }
]

const scopeHint = computed(() =>
  isAdmin.value
    ? '指标口径：超级管理员为全量数据'
    : `指标口径：已自动收敛为${authStore.user && authStore.user.stationName ? `「${authStore.user.stationName}」` : '本站'}`
)

const filterCount = computed(() => {
  let count = 0
  if (query.stationId !== undefined && query.stationId !== null && query.stationId !== '') count += 1
  if (query.status !== undefined && query.status !== null) count += 1
  if (dateRange.value && dateRange.value.length === 2) count += 1
  if (query.waybillNo) count += 1
  return count
})

const headerSub = computed(() => {
  const scope = isAdmin.value ? '数据范围：全域' : '数据范围：本站'
  return `${scope} · 共 ${total.value} 条 · 更新于 ${updatedAt.value || '—'}`
})

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** 查询条件：日期区间只在选中时下发，起止补足 00:00:00 / 23:59:59 兜住整天（Mock 按时间戳比较） */
function buildParams() {
  const [start, end] = dateRange.value || []
  return {
    stationId: query.stationId,
    status: query.status,
    waybillNo: query.waybillNo || undefined,
    startTime: start ? `${start} 00:00:00` : undefined,
    endTime: end ? `${end} 23:59:59` : undefined
  }
}

/** 筛选条件写回 URL：刷新/分享链接后能还原筛选（5.3 新增规则），用 replace 避免污染浏览历史 */
function syncQuery() {
  const [start, end] = dateRange.value || []
  const next = {}
  if (query.stationId) next.stationId = String(query.stationId)
  if (query.status !== undefined && query.status !== null) next.status = String(query.status)
  if (start) next.startTime = start
  if (end) next.endTime = end
  if (query.waybillNo) next.waybillNo = query.waybillNo
  if (query.pageNum > 1) next.pageNum = String(query.pageNum)
  router.replace({ path: route.path, query: next })
}

/** 从 URL 还原筛选条件；通知中心跳转带的 ?parcelId= 不参与筛选，故只读白名单字段 */
function restoreQuery() {
  const { stationId, status, startTime, endTime, waybillNo, pageNum } = route.query
  if (stationId) query.stationId = Number(stationId)
  if (status !== undefined && status !== '') query.status = Number(status)
  if (waybillNo) query.waybillNo = String(waybillNo)
  if (startTime && endTime) dateRange.value = [String(startTime), String(endTime)]
  if (pageNum) query.pageNum = Number(pageNum) || 1
}

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const page = await getParcels({ ...buildParams(), pageNum: query.pageNum, pageSize: query.pageSize })
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

async function fetchSummary() {
  loadingSummary.value = true
  summaryError.value = false
  try {
    summary.value = await getParcelSummary()
  } catch (e) {
    summaryError.value = true
  } finally {
    loadingSummary.value = false
  }
}

async function loadStations() {
  try {
    await orgStore.loadStations()
  } catch (e) {
    /* 拦截器已统一提示：站点下拉失败不阻塞列表，用户仍可按其他条件筛选 */
  }
}

async function openDetail(id) {
  detailVisible.value = true
  detail.value = null
  loadingDetail.value = true
  try {
    detail.value = await getParcelDetail(id)
  } catch (e) {
    detail.value = null
  } finally {
    loadingDetail.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  syncQuery()
  fetchList()
}

function handleReset() {
  query.stationId = undefined
  query.status = undefined
  query.waybillNo = ''
  dateRange.value = []
  query.pageNum = 1
  syncQuery()
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

function refreshPage() {
  fetchSummary()
  fetchList()
}

/** 本地日期戳：不用 toISOString，避免 UTC 把清晨的导出算到前一天 */
function dateStamp() {
  const now = new Date()
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
}

async function handleExport() {
  if (!total.value) {
    ElMessage.warning('当前筛选无数据可导出')
    return
  }
  exporting.value = true
  try {
    const rowCount = Math.min(total.value, EXPORT_LIMIT)
    const pages = await Promise.all(
      Array.from({ length: Math.ceil(rowCount / PAGE_SIZE) }, (_item, index) =>
        getParcels({ ...buildParams(), pageNum: index + 1, pageSize: PAGE_SIZE })
      )
    )
    const rows = pages.flatMap((page) => page.list).slice(0, rowCount)
    downloadCsv(
      `包裹数据_${dateStamp()}.csv`,
      ['运单号', '驿站', '状态', '收件人', '收件人手机号', '货架码', '入库时间', '取件时间'],
      rows.map((row) => [
        row.waybillNo,
        row.stationName,
        PARCEL_STATUS[row.status] ? PARCEL_STATUS[row.status].label : row.status,
        row.receiverName,
        row.receiverPhone,
        row.shelfCode,
        row.inboundTime,
        row.pickupTime || ''
      ])
    )
    ElMessage.success(
      `已导出 ${rows.length} 条${total.value > EXPORT_LIMIT ? `（超出单次上限，仅前 ${EXPORT_LIMIT} 条）` : ''}`
    )
  } catch (e) {
    /* 拦截器已统一提示 */
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  restoreQuery()
  refreshPage()
  if (isAdmin.value) loadStations()
  // 通知中心跳转（?parcelId=）时直接展开对应包裹
  if (route.query.parcelId) openDetail(route.query.parcelId)
})
</script>

<style scoped lang="scss">
.parcel-page {
  .header-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  .filter-card {
    margin-bottom: var(--sp-4);

    // 控件定宽规则（C-P8）：替换原先把宽度写在 style 属性里的魔法值
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

    :deep(.filter-input) {
      width: 200px;
    }
  }

  .content-card {
    margin-bottom: 0;
  }

  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  .drawer-body {
    min-height: 200px;
  }
}
</style>
