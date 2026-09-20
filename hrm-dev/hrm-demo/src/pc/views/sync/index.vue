<template>
  <div class="sync-page">
    <PageHeader title="同步任务" :sub="headerSub" :loading="headerLoading">
      <template #actions>
        <el-button :icon="Refresh" @click="refreshCurrent">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 页内三视图：批次流水 / 采集配置 / 配置管理；Tab 状态写 URL，便于演示时直接给链接。
         「配置管理」仅 ADMIN 渲染（写操作后端也仅 ADMIN），站长看不到「点了才知道没权限」的入口（B.1.1） -->
    <el-tabs v-model="activeTab" class="sync-tabs" @tab-change="handleTabChange">
      <el-tab-pane name="batch" label="批次流水" />
      <el-tab-pane name="collect" label="采集配置" />
      <el-tab-pane v-if="isAdmin" name="config" label="配置管理" />
    </el-tabs>

    <!-- ==================== 视图一：批次流水 ==================== -->
    <template v-if="activeTab === 'batch'">
      <el-card shadow="never" class="filter-card">
        <el-form inline @submit.prevent>
          <el-form-item v-if="isAdmin" label="驿站">
            <el-select v-model="query.stationId" clearable placeholder="全部驿站">
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态">
              <el-option v-for="(item, key) in SYNC_STATUS" :key="key" :label="item.label" :value="Number(key)" />
            </el-select>
          </el-form-item>
          <el-form-item label="批次号">
            <el-input
              v-model.trim="query.keyword"
              placeholder="批次号模糊查询"
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
            <span class="toolbar-tip">共 {{ total }} 个批次 · 状态机：待领取 → 执行中 → 成功 / 失败</span>
          </div>
          <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="fetchList" />
        </div>

        <!-- 错误态独立于空态：接口失败时不能让用户看到"没有符合条件的同步任务"（修 P14） -->
        <StateBlock v-if="listError" variant="error" title="同步任务加载失败" @action="fetchList" />

        <StateBlock
          v-else-if="!loading && !list.length"
          variant="empty"
          :title="hasFilter ? '当前筛选条件下没有同步任务' : '暂无同步任务'"
          :action-text="hasFilter ? '清空筛选' : ''"
          @action="handleReset"
        />

        <template v-else>
          <el-table
            v-loading="loading"
            class="sticky-table"
            :data="list"
            border
            row-key="id"
            :row-class-name="rowClassOf"
          >
            <!-- 失败行可展开：失败原因是排查第一信息，tooltip 不能复制也不便对多行比对（A6-3）；
                 仅失败行出现展开图标（非失败行由样式隐藏），避免整表都是无意义的展开箭头 -->
            <el-table-column type="expand" width="40">
              <template #default="{ row }">
                <div class="expand-panel">
                  <p class="expand-panel__label">失败原因</p>
                  <p class="expand-panel__text">{{ row.errorMsg || '未记录失败原因' }}</p>
                  <p class="expand-panel__meta">
                    成功 {{ row.successCount }} 件 · 失败 {{ row.failCount }} 件 · 重试 {{ row.retryCount }} 次
                  </p>
                  <el-button link type="primary" @click="openLogs(row)">查看批次日志</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="batchNo" label="批次号" min-width="150" />
            <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <StatusTag :dict="SYNC_STATUS" :value="row.status" :variant="row.status === 0 ? 'outline' : 'soft'" />
              </template>
            </el-table-column>
            <el-table-column prop="parcelTotal" label="包裹数" width="90" align="right" />
            <el-table-column label="成功 / 失败" width="110" align="right">
              <template #default="{ row }">{{ row.successCount }} / {{ row.failCount }}</template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试" width="70" align="center" />
            <el-table-column label="耗时" width="110" align="center">
              <template #default="{ row }">{{ formatDuration(row.startTime, row.finishTime) }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="160" />
            <el-table-column label="失败原因" min-width="170" show-overflow-tooltip>
              <template #default="{ row }">{{ row.errorMsg || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right" align="center">
              <template #default="{ row }">
                <!-- 触发 / 重试各自独立的 loading：改前共用一个 actingId，点触发时重试也跟着转圈（A6-2） -->
                <el-button
                  link
                  type="primary"
                  :disabled="row.status !== 0"
                  :title="row.status === 0 ? '手动触发该批次同步' : '仅「待领取」批次可触发'"
                  :loading="triggeringId === row.id"
                  @click="handleTrigger(row)"
                >
                  触发
                </el-button>
                <el-button
                  link
                  type="warning"
                  :disabled="row.status !== 3"
                  :title="row.status === 3 ? '重试后将回到待领取队列' : '仅「失败」批次可重试'"
                  :loading="retryingId === row.id"
                  @click="handleRetry(row)"
                >
                  重试
                </el-button>
                <el-button link type="primary" @click="openLogs(row)">日志</el-button>
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
    </template>

    <!-- ==================== 视图二：采集配置 ==================== -->
    <template v-else-if="activeTab === 'collect'">
      <CollectStateBoard
        :counts="overview.counts"
        :loading="collectLoading"
        :error="collectError"
        @retry="fetchCollect"
      />

      <!-- 待处理提示条：仅异常 + 未配置 > 0 时出现，点击把表格收敛到待处理项（B1.2 ②） -->
      <el-alert
        v-if="!collectError && attentionCount > 0"
        class="collect-alert"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title>
          <span>{{ attentionText }}，建议优先处理</span>
          <el-button
            v-if="collectFilter !== 'attention'"
            link
            type="primary"
            class="collect-alert__action"
            @click="focusAttention"
          >
            查看列表
          </el-button>
          <el-button v-else link type="primary" class="collect-alert__action" @click="clearFilter">显示全部</el-button>
        </template>
      </el-alert>

      <el-card shadow="never" class="content-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <span class="toolbar-tip">
              共 {{ overview.total }} 个驿站 · 按状态排序：异常 → 未配置 → 正常 → 已停用
              <template v-if="collectFilter === 'attention'"> · 当前仅看待处理项</template>
            </span>
          </div>
          <el-button
            :icon="Refresh"
            circle
            text
            :loading="collectLoading"
            aria-label="刷新采集配置"
            @click="fetchCollect"
          />
        </div>

        <CollectConfigTable
          :configs="visibleConfigs"
          :loading="collectLoading"
          :error="collectError"
          :can-write="isAdmin"
          :acting-station-id="actingStationId"
          @retry="fetchCollect"
          @edit="openConfig"
          @toggle="handleToggle"
        />
      </el-card>

      <CollectConfigDrawer
        v-model="configVisible"
        :station-id="configStation.id"
        :station-name="configStation.name"
        :can-write="isAdmin"
        @saved="fetchCollect"
      />
    </template>

    <!-- ==================== 视图三：配置管理（仅 ADMIN） ==================== -->
    <SyncConfigCenter v-else-if="activeTab === 'config'" ref="configCenterRef" />

    <!-- 批次日志抽屉：每次状态流转都会追加一条日志，重试/触发后重新拉取即可看到追加 -->
    <el-drawer v-model="logVisible" :title="`批次日志 · ${logTask.batchNo || ''}`" :size="drawerSize" destroy-on-close>
      <div class="drawer-body">
        <el-descriptions v-if="logTask.id" :column="2" size="small" border class="log-summary">
          <el-descriptions-item label="驿站">{{ logTask.stationName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag
              :dict="SYNC_STATUS"
              :value="logTask.status"
              :variant="logTask.status === 0 ? 'outline' : 'soft'"
            />
          </el-descriptions-item>
          <el-descriptions-item label="包裹数">{{ logTask.parcelTotal }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ logTask.retryCount }}</el-descriptions-item>
        </el-descriptions>

        <el-skeleton v-if="loadingLog" :rows="4" animated />
        <StateBlock v-else-if="logError" variant="error" title="日志加载失败" @action="loadLogs(logTask.id)" />
        <StateBlock v-else-if="!logs.length" variant="empty" title="该批次暂无日志" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :timestamp="log.logTime"
            placement="top"
            :color="LOG_DOT[log.level]"
          >
            <div class="log-item">
              <StatusTag :dict="SYNC_LOG_LEVEL" :value="log.level" />
              <span class="log-message">{{ log.message }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getStations } from '@admin/api/station'
import { useAuthStore } from '@admin/stores/auth'
import { SYNC_LOG_LEVEL, SYNC_STATUS } from '@/shared/constants/dict'
import { getSyncConfigs, getSyncOverview, saveSyncConfig } from '../../api/syncConfig.js'
import { getSyncTaskDetail, getSyncTaskLogs, getSyncTasks, retrySyncTask, triggerSyncTask } from '../../api/syncTask.js'
import { formatDuration } from '../../utils/format.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import CollectStateBoard from './components/CollectStateBoard.vue'
import CollectConfigTable from './components/CollectConfigTable.vue'
import CollectConfigDrawer from './components/CollectConfigDrawer.vue'
import SyncConfigCenter from './components/SyncConfigCenter.vue'
import { useSyncConfigMeta } from './composables/useSyncConfigMeta.js'

/**
 * 同步任务（T16 / 需求1，demo-ux-improvement.md B1）
 *
 * 页内双视图而不新开路由：`sync` 键已在菜单白名单，新路由要动 shared 冻结层；
 * 且「批次流水」与「采集配置」本就是同一业务的两种视图（一次同步 vs 一个驿站的采集策略）。
 */

/**
 * 日志级别 → 时间线轴点色（2.7）：改前借用 el-timeline 的语义 type，
 * 而 --el-color-info 已被收口成 #4B5563（深灰），当轴点用会过重，故显式给色值 Token。
 */
const LOG_DOT = { 0: 'var(--text-disabled)', 1: 'var(--color-warning-icon)', 2: 'var(--color-danger-icon)' }

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')

// 配置项定义 + 选项集：采集配置表/抽屉与配置管理都要用（单例，只拉一次），仅 ADMIN 有权限读取
const meta = useSyncConfigMeta()

const TABS = ['batch', 'collect', 'config']
// 站长无「配置管理」权限，直达 ?tab=config 时回落到批次流水，避免出现空 Tab（B.1.1）
const initialTab = TABS.includes(route.query.tab) ? route.query.tab : 'batch'
const activeTab = ref(initialTab === 'config' && !isAdmin.value ? 'batch' : initialTab)

// 配置管理分段控件/三个子区域的状态都在容器内，刷新按钮通过该 ref 触发
const configCenterRef = ref(null)

const loading = ref(false)
const loadingLog = ref(false)
const triggeringId = ref(null)
const retryingId = ref(null)
const list = ref([])
const total = ref(0)
const stations = ref([])
const logVisible = ref(false)
const logTask = ref({})
const logs = ref([])
const listError = ref(false)
const logError = ref(false)
const updatedAt = ref('')

// 采集配置视图状态
const collectLoading = ref(false)
const collectError = ref(false)
const overview = ref({ total: 0, counts: null })
const configs = ref([])
const collectFilter = ref(null)
const actingStationId = ref(null)
const configVisible = ref(false)
const configStation = ref({ id: null, name: '' })

// 抽屉宽度统一到 --drawer-w，窄视口退化为 92vw（C-P9，修 P16）
const drawerSize = 'min(var(--drawer-w), 92vw)'

// TODO(扩展): 批次筛选条件写回 URL（A5-2 的 useQuerySync；白名单需排除业务参数 taskId），
// 并让日志抽屉的 el-descriptions 列数随宽度自适应（A5-3）
const query = reactive({ stationId: undefined, status: undefined, keyword: '', pageNum: 1, pageSize: 20 })

const hasFilter = computed(() => query.stationId !== undefined || query.status !== undefined || !!query.keyword)

const attentionCount = computed(() => {
  const counts = overview.value.counts
  return counts ? counts.abnormal + counts.unconfigured : 0
})

const attentionText = computed(() => {
  const counts = overview.value.counts || { abnormal: 0, unconfigured: 0 }
  const parts = []
  if (counts.abnormal) parts.push(`${counts.abnormal} 个驿站采集异常`)
  if (counts.unconfigured) parts.push(`${counts.unconfigured} 个驿站未配置采集`)
  return parts.join('、')
})

/** 「查看列表」只收敛到待处理两项，其余状态仍完整可达（清除筛选即恢复） */
const visibleConfigs = computed(() => {
  if (collectFilter.value !== 'attention') return configs.value
  return configs.value.filter((item) => item.collectState === 'ABNORMAL' || item.collectState === 'UNCONFIGURED')
})

const headerLoading = computed(() => {
  if (activeTab.value === 'batch') return loading.value
  if (activeTab.value === 'config') return meta.loading.value
  return collectLoading.value
})

const headerSub = computed(() => {
  const scope = isAdmin.value ? '数据范围：全域' : '数据范围：本站'
  if (activeTab.value === 'config') {
    return `${scope} · 共 ${meta.items.value.length} 个配置项 · 更新于 ${updatedAt.value || '—'}`
  }
  if (activeTab.value === 'collect') {
    return `${scope} · 共 ${overview.value.total} 个驿站 · 更新于 ${updatedAt.value || '—'}`
  }
  return `${scope} · 共 ${total.value} 个批次 · 更新于 ${updatedAt.value || '—'}`
})

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const page = await getSyncTasks({
      stationId: query.stationId,
      status: query.status,
      keyword: query.keyword || undefined,
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

/** 采集视图：总览计数与配置明细必须同时刻刷新，否则计数与表格对不上 */
async function fetchCollect() {
  collectLoading.value = true
  collectError.value = false
  try {
    const [ov, listData] = await Promise.all([getSyncOverview(), getSyncConfigs()])
    overview.value = ov
    configs.value = listData
    updatedAt.value = stamp()
  } catch (e) {
    overview.value = { total: 0, counts: null }
    configs.value = []
    collectError.value = true
  } finally {
    collectLoading.value = false
  }
}

async function loadStations() {
  try {
    stations.value = await getStations()
  } catch (e) {
    /* 站点下拉失败不阻塞列表筛选 */
  }
}

async function loadLogs(id) {
  if (!id) return
  loadingLog.value = true
  logError.value = false
  try {
    logs.value = await getSyncTaskLogs(id)
  } catch (e) {
    logs.value = []
    logError.value = true
  } finally {
    loadingLog.value = false
  }
}

function openLogs(task) {
  logTask.value = task
  logVisible.value = true
  logs.value = []
  loadLogs(task.id)
}

/** 通知中心跳转（?taskId=）时任务可能不在当前页，用详情接口取回任务信息再开日志 */
async function openLogsById(id) {
  try {
    openLogs(await getSyncTaskDetail(id))
  } catch (e) {
    /* 拦截器已统一提示（跨驿站越权按 404 处理） */
  }
}

/** 触发 / 重试共用：成功后刷新列表，抽屉开着就顺带刷新日志（能看到追加的那几条） */
async function runAction(row, action, successText, loadingRef) {
  loadingRef.value = row.id
  try {
    await action()
    ElMessage.success(successText)
    await fetchList()
    if (logTask.value.id === row.id) await loadLogs(row.id)
  } catch (e) {
    /* 拦截器已统一提示（6001 等业务码） */
  } finally {
    loadingRef.value = null
  }
}

async function handleTrigger(row) {
  try {
    await ElMessageBox.confirm(`确定手动触发批次「${row.batchNo}」的同步吗？`, '手动触发', { type: 'warning' })
  } catch (e) {
    return
  }
  await runAction(row, () => triggerSyncTask(row.id), '已触发执行（Mock 直接跑完整个状态机）', triggeringId)
}

async function handleRetry(row) {
  try {
    await ElMessageBox.confirm(`确定重试批次「${row.batchNo}」吗？重试后回到待领取状态。`, '失败重试', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  await runAction(row, () => retrySyncTask(row.id), '已回到待领取队列，可继续触发', retryingId)
}

/** 仅失败行标记类名，配合样式只给失败行显示展开图标（A6-3） */
function rowClassOf({ row }) {
  return row.status === 3 ? 'is-failed-row' : ''
}

function openConfig(row) {
  configStation.value = { id: row.stationId, name: row.stationName }
  configVisible.value = true
}

/** 表格开关：关闭需二次确认（在途批次不受影响）；开启前若缺数据源由服务端 400 兜底并给可操作文案 */
async function handleToggle(row, value) {
  if (!value) {
    try {
      await ElMessageBox.confirm(
        `关闭「${row.stationName}」的采集后，该驿站将停止包裹采集，在途批次不受影响。`,
        '关闭采集',
        {
          confirmButtonText: '确认关闭',
          cancelButtonText: '再想想',
          type: 'warning'
        }
      )
    } catch (e) {
      return
    }
  }
  actingStationId.value = row.stationId
  try {
    await saveSyncConfig(row.stationId, { enabled: value ? 1 : 0 }, { silent: true })
    ElMessage.success(value ? '采集已开启，按配置频次生效' : '采集已关闭')
    await fetchCollect()
  } catch (e) {
    ElMessage.error((e && e.message) || '采集开关保存失败，请重试')
  } finally {
    actingStationId.value = null
  }
}

function focusAttention() {
  collectFilter.value = 'attention'
}

function clearFilter() {
  collectFilter.value = null
}

/** Tab 状态写 URL（?tab=collect / ?tab=config）：演示时可直达；其它业务参数（taskId）原样保留 */
function handleTabChange(name) {
  const nextQuery = { ...route.query }
  if (name === 'collect' || name === 'config') nextQuery.tab = name
  else delete nextQuery.tab
  router.replace({ query: nextQuery })
  if (name === 'collect') {
    if (!configs.value.length && !collectLoading.value) fetchCollect()
  } else if (name === 'batch') {
    if (!list.value.length && !loading.value) fetchList()
  } else if (!meta.loaded.value) {
    // 配置管理容器自身会在挂载时拉数据，这里只保证采集配置表的选项集标签也已就绪
    meta.loadMeta()
  }
}

function refreshCurrent() {
  if (activeTab.value === 'collect') fetchCollect()
  else if (activeTab.value === 'config') configCenterRef.value && configCenterRef.value.refresh()
  else fetchList()
}

function handleSearch() {
  query.pageNum = 1
  fetchList()
}

function handleReset() {
  query.stationId = undefined
  query.status = undefined
  query.keyword = ''
  query.pageNum = 1
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

onMounted(async () => {
  // 元数据是采集配置表/抽屉与配置管理的共同依赖，ADMIN 一进页就拉一次（站长无权限，不请求）
  if (isAdmin.value) meta.loadMeta()
  if (activeTab.value === 'collect') {
    await fetchCollect()
  } else if (activeTab.value === 'batch') {
    await fetchList()
  }
  if (isAdmin.value) loadStations()
  if (route.query.taskId) {
    activeTab.value = 'batch'
    openLogsById(route.query.taskId)
  }
})
</script>

<style scoped lang="scss">
.sync-page {
  // Tab 结构与工单/通知页保持一致（5.3 列表页模板）
  .sync-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--sp-4);
    }

    :deep(.el-tabs__item) {
      height: 40px;
      font-size: var(--fs-body);
      color: var(--text-2);
    }

    :deep(.el-tabs__item.is-active) {
      color: var(--color-primary);
      font-weight: var(--fw-medium);
    }

    :deep(.el-tabs__active-bar) {
      height: 2px;
      background-color: var(--color-primary);
    }
  }

  .filter-card {
    margin-bottom: var(--sp-4);

    // 控件定宽规则（C-P8）：驿站/状态 160px、关键字 200px
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

    :deep(.filter-input) {
      width: 200px;
    }
  }

  .collect-alert {
    margin-bottom: var(--sp-4);

    &__action {
      margin-left: var(--sp-2);
    }
  }

  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  // 只给失败行显示展开图标；非失败行保留占位宽度，表头列不错位
  :deep(.el-table__row:not(.is-failed-row) .el-table__expand-icon) {
    visibility: hidden;
    pointer-events: none;
  }

  .expand-panel {
    padding: var(--sp-3) var(--sp-6);
    background-color: var(--surface-sub);

    &__label {
      margin: 0;
      font-size: var(--fs-caption);
      color: var(--text-3);
    }

    &__text {
      margin: var(--sp-1) 0 0;
      font-size: var(--fs-body);
      color: var(--text-1);
      word-break: break-all;
    }

    &__meta {
      margin: var(--sp-1) 0 var(--sp-2);
      font-size: var(--fs-caption);
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
    }
  }

  .drawer-body {
    min-height: 200px;
  }

  .log-summary {
    margin-bottom: var(--sp-5);
  }

  .log-item {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  .log-message {
    font-size: var(--fs-body);
    color: var(--text-1);
    word-break: break-all;
  }
}
</style>
