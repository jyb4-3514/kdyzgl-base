<template>
  <div class="work-order-page">
    <PageHeader title="工单管理" :sub="headerSub" :loading="loading">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="createVisible = true">新建工单</el-button>
        <!-- 自动派单只对老板渲染：规则维护接口仅 ADMIN，且契约 GET 未加 roles 属权限不一致（U6） -->
        <el-button v-if="isAdmin" :icon="Promotion" @click="dispatchVisible = true">自动派单</el-button>
        <el-button :icon="Refresh" @click="refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <!-- Tab 条独立在卡片之外：改前 Tab + 筛选 + 表格挤在同一张卡里，层次分不清（5.4） -->
    <el-tabs v-model="activeTab" class="wo-tabs" @tab-change="handleTabChange">
      <el-tab-pane v-for="tab in TABS" :key="tab.name" :name="tab.name">
        <template #label>
          <span>{{ tab.label }}</span>
          <span
            v-if="tabCounts[tab.name] !== undefined"
            class="wo-tabs__count"
            :class="{ 'is-danger': tab.name === 'overdueUnhandled' && tabCounts[tab.name] > 0 }"
          >
            {{ tabCounts[tab.name] }}
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item v-if="isAdmin" label="驿站">
          <el-select v-model="query.stationId" clearable placeholder="全部驿站">
            <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.type" clearable placeholder="全部类型">
            <el-option v-for="(item, key) in WORK_ORDER_TYPE" :key="key" :label="item.label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" clearable placeholder="全部优先级">
            <el-option v-for="(item, key) in WORK_ORDER_PRIORITY" :key="key" :label="item.label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model.trim="query.keyword"
            placeholder="工单号 / 标题"
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
          <span class="toolbar-tip">
            共 {{ total }} 条 · SLA 口径：低 48h / 中 24h / 高 8h · 超时未处理 = 已过 SLA
            且仍为待处理/处理中；仅高亮提醒，不自动改状态
          </span>
        </div>
        <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="fetchList" />
      </div>

      <StateBlock v-if="listError" variant="error" title="工单列表加载失败" @action="fetchList" />

      <StateBlock
        v-else-if="!loading && !list.length"
        variant="empty"
        :title="hasFilter ? '当前筛选条件下没有工单' : '暂无工单'"
        :action-text="hasFilter ? '清空筛选' : ''"
        @action="handleReset"
      />

      <template v-else>
        <el-table v-loading="loading" class="sticky-table" :data="list" border :row-class-name="rowClassName">
          <el-table-column prop="orderNo" label="工单号" min-width="150" />
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">{{ dictLabel(WORK_ORDER_TYPE, row.type) }}</template>
          </el-table-column>
          <!-- 来源列（A7-6）：企微自动派发的工单与手工工单混在一起，老板需要一眼区分 -->
          <el-table-column label="来源" width="100" align="center">
            <template #default="{ row }">
              <StatusTag
                :dict="WORK_ORDER_SOURCE"
                :value="row.source || 'MANUAL'"
                :variant="(WORK_ORDER_SOURCE[row.source || 'MANUAL'] || {}).variant || 'outline'"
              />
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="80" align="center">
            <template #default="{ row }">
              <StatusTag
                :dict="WORK_ORDER_PRIORITY"
                :value="row.priority"
                :variant="PRIORITY_VARIANT[row.priority] || 'soft'"
              />
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
          <el-table-column prop="stationName" label="驿站" min-width="110" show-overflow-tooltip />
          <el-table-column label="上报人" min-width="90">
            <template #default="{ row }">{{ row.reporterName || '—' }}</template>
          </el-table-column>
          <el-table-column label="处理人" min-width="90">
            <template #default="{ row }">{{ row.assigneeName || '未指派' }}</template>
          </el-table-column>
          <!-- SLA 列同时给胶囊与截止时间：只靠颜色一种通道会挡住色觉障碍用户 -->
          <el-table-column label="SLA" width="140">
            <template #default="{ row }">
              <div class="sla-cell">
                <SlaCountdown
                  :deadline="row.slaDeadline"
                  :priority="row.priority"
                  :finished="row.status === 2 || row.status === 3"
                />
                <span class="sla-cell__deadline">{{ row.slaDeadline }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <StatusTag
                :dict="WORK_ORDER_STATUS"
                :value="row.status"
                :variant="row.status === 3 ? 'outline' : 'soft'"
              />
            </template>
          </el-table-column>
          <!-- 操作列由 80 放宽到 120：原宽度只放得下「详情」文字链，复制改用图标按钮 + tooltip 控宽 -->
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-tooltip content="复制工单详情" placement="top">
                <el-button
                  link
                  class="wo-copy"
                  :icon="DocumentCopy"
                  aria-label="复制工单详情"
                  @click="handleCopy(row)"
                />
              </el-tooltip>
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

    <!-- 详情抽屉：基础信息 + 工单描述 + 流转时间线 + 底部操作（动作可见性镜像 Mock 的流转校验） -->
    <el-drawer v-model="detailVisible" title="工单详情" :size="drawerSize" destroy-on-close>
      <div v-loading="loadingDetail" class="drawer-body">
        <template v-if="detail">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="工单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusTag
                :dict="WORK_ORDER_STATUS"
                :value="detail.status"
                :variant="detail.status === 3 ? 'outline' : 'soft'"
              />
            </el-descriptions-item>
            <el-descriptions-item label="类型">{{ dictLabel(WORK_ORDER_TYPE, detail.type) }}</el-descriptions-item>
            <el-descriptions-item label="来源">
              <StatusTag
                :dict="WORK_ORDER_SOURCE"
                :value="detail.source || 'MANUAL'"
                :variant="(WORK_ORDER_SOURCE[detail.source || 'MANUAL'] || {}).variant || 'outline'"
              />
            </el-descriptions-item>
            <el-descriptions-item label="优先级">
              <StatusTag
                :dict="WORK_ORDER_PRIORITY"
                :value="detail.priority"
                :variant="PRIORITY_VARIANT[detail.priority] || 'soft'"
              />
            </el-descriptions-item>
            <el-descriptions-item label="归属驿站">{{ detail.stationName }}</el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detail.assigneeName || '未指派' }}</el-descriptions-item>
            <el-descriptions-item label="上报人">{{ detail.reporterName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="关联运单">{{ detail.waybillNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="SLA 截止">{{ detail.slaDeadline }}</el-descriptions-item>
            <el-descriptions-item label="SLA 剩余">
              <SlaCountdown
                :deadline="detail.slaDeadline"
                :priority="detail.priority"
                :finished="detail.status === 2 || detail.status === 3"
              />
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
            <el-descriptions-item label="解决时间">{{ detail.resolvedTime || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-block">
            <div class="block-title">工单描述</div>
            <p class="block-text">{{ detail.content || '无' }}</p>
          </div>

          <div class="detail-block">
            <div class="block-title">
              流转记录
              <span class="block-hint">转单留痕单列，便于追溯处理人变更</span>
            </div>
            <el-timeline v-if="timelineEvents.length">
              <el-timeline-item
                v-for="event in timelineEvents"
                :key="event.key"
                :timestamp="event.time"
                placement="top"
                :color="LOG_DOT[event.action] || 'var(--text-disabled)'"
              >
                <div class="log-line">
                  <strong>{{ LOG_ACTION[event.action] || event.action }}</strong>
                  <span class="log-operator">{{ event.operatorName || '系统' }}</span>
                </div>
                <!-- 转单：结构化展示「转出人 → 转入人 + 理由」，与普通流转事件在视觉上区分开 -->
                <template v-if="event.action === 'transfer'">
                  <div class="transfer-line">
                    <span class="transfer-line__who">{{ event.fromName || '未指派' }}</span>
                    <el-icon class="transfer-line__arrow" aria-hidden="true"><Right /></el-icon>
                    <span class="transfer-line__who transfer-line__who--to">{{ event.toName }}</span>
                  </div>
                  <div class="log-content">理由：{{ event.reason }}</div>
                </template>
                <div v-else-if="event.content" class="log-content">{{ event.content }}</div>
              </el-timeline-item>
            </el-timeline>
            <StateBlock v-else variant="empty" title="暂无流转记录" />
          </div>
        </template>
        <StateBlock v-else-if="!loadingDetail" variant="empty" title="未获取到工单信息" />
      </div>

      <template #footer>
        <div class="drawer-footer">
          <!-- 次操作固定左侧、主操作（流转）固定右侧：按钮位置不随状态变化跳动，避免误点（A7-5）；
               转单属低频次操作，收进「更多」下拉 -->
          <div class="drawer-footer__secondary">
            <el-button v-if="detail" :icon="DocumentCopy" plain @click="handleCopy(detail)">复制详情</el-button>
            <el-button v-if="detail && canAssign" plain @click="openAssign(detail)">指派</el-button>
            <!-- 转单权限与流转同口径（canManage）：ADMIN / 本站站长 / 当前处理人 -->
            <el-dropdown v-if="detail && canTransfer" trigger="click" @command="openTransfer(detail)">
              <el-button>
                更多
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="transfer">转单</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="drawer-footer__primary">
            <el-button
              v-for="action in availableActions"
              :key="action.target"
              :type="action.type"
              :plain="action.plain"
              :loading="acting"
              @click="handleTransition(action.target)"
            >
              {{ action.label }}
            </el-button>
            <span v-if="detail && !availableActions.length && !canAssign && !canTransfer" class="footer-tip"
              >当前账号对该工单无可执行动作</span
            >
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 指派弹窗：处理人下拉取本站启用员工 -->
    <el-dialog v-model="assignDialog.visible" title="指派处理人" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="工单号">{{ assignDialog.orderNo }}</el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="assignDialog.assigneeId" filterable placeholder="请选择本站员工" style="width: 100%">
            <el-option
              v-for="item in assignees"
              :key="item.id"
              :label="`${item.realName}（${ROLE_LABEL[item.role] || item.role}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="assignDialog.loading" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 转单弹窗：只改处理人、不改状态；理由必填（2-100 字，与后端 2-100 校验同口径） -->
    <el-dialog v-model="transferDialog.visible" title="工单转单" width="480px" :close-on-click-modal="false">
      <el-form ref="transferFormRef" :model="transferForm" :rules="TRANSFER_RULES" label-width="90px">
        <el-form-item label="工单号">{{ transferDialog.orderNo }}</el-form-item>
        <el-form-item label="当前处理人">{{ transferDialog.fromName || '未指派' }}</el-form-item>
        <el-form-item label="转单对象" prop="toEmployeeId">
          <el-select
            v-model="transferForm.toEmployeeId"
            filterable
            :loading="transferOptionsLoading"
            :placeholder="transferScopeHint"
            class="transfer-select"
          >
            <el-option v-for="item in transferCandidates" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="转单理由" prop="reason">
          <el-input
            v-model="transferForm.reason"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="请说明转单原因（2-100 字），将随留痕写入时间线"
          />
        </el-form-item>
      </el-form>
      <p class="transfer-tip">转单只变更处理人，工单状态与 SLA 截止时间不变；提交前会再次确认。</p>
      <template #footer>
        <el-button @click="transferDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="transferDialog.loading" @click="submitTransfer">提交转单</el-button>
      </template>
    </el-dialog>

    <!-- 新建工单（需求3）：ADMIN 与站长都可用，归属驿站站长固定为本站 -->
    <CreateWorkOrderDialog
      v-model="createVisible"
      :is-admin="isAdmin"
      :stations="stations"
      :default-station-id="createStationId"
      :fixed-station-name="currentUser.stationName || ''"
      @created="handleCreated"
    />

    <!-- 企微自动派单模拟入口（需求3）：仅 ADMIN 渲染（规则维护接口仅 ADMIN） -->
    <AutoDispatchDrawer
      v-if="isAdmin"
      v-model="dispatchVisible"
      :stations="stations"
      :default-station-id="createStationId"
      :default-group-name="defaultGroupName"
      @dispatched="handleDispatched"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, DocumentCopy, Plus, Promotion, Refresh, Right } from '@element-plus/icons-vue'
import { getEmployees } from '@admin/api/employee'
import { getStations } from '@admin/api/station'
import { useAuthStore } from '@admin/stores/auth'
import { DEMO_CODE } from '@/shared/constants/errorCode'
import { ROLE_LABEL } from '@/shared/constants/role'
import {
  WORK_ORDER_PRIORITY,
  WORK_ORDER_SOURCE,
  WORK_ORDER_STATUS,
  WORK_ORDER_TYPE,
  dictLabel
} from '@/shared/constants/dict'
import { copyText } from '@/shared/domain/text.js'
import { buildWorkOrderText } from '@/shared/domain/workOrderText.js'
import { getSchedules } from '../../api/attendance.js'
import {
  assignWorkOrder,
  changeWorkOrderStatus,
  getWorkOrderDetail,
  getWorkOrders,
  transferWorkOrder
} from '../../api/workOrder.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import SlaCountdown from '../../components/SlaCountdown.vue'
import CreateWorkOrderDialog from './components/CreateWorkOrderDialog.vue'
import AutoDispatchDrawer from './components/AutoDispatchDrawer.vue'

/**
 * 工单管理（T17，demo-ui-redesign.md 5.4）
 * 状态机与流转合法性以 Mock（= 未来后端）为准，本页只负责「按当前状态渲染合法动作」：
 * 待处理→接单/关闭、处理中→解决、已解决→重开/关闭（demo-design.md 7.4.5）
 */

/** 与 Mock 层 TRANSITIONS 一致；UI 仅做按钮可见性，非法跳转仍由服务端返回 8001 兜底 */
const TRANSITIONS = { 0: [1, 3], 1: [2], 2: [1, 3], 3: [] }

/** 流转记录 action → 展示文案 */
const LOG_ACTION = {
  create: '创建工单',
  assign: '指派',
  accept: '接单',
  resolve: '标记已解决',
  close: '关闭工单',
  reopen: '驳回重开',
  transfer: '转单',
  // 企微群消息自动派发（需求3）：时间线上必须能区分「人派」与「机器派」
  auto_dispatch: '企业微信自动派发'
}
/** 流转动作 → 时间线轴点色（工作流语义，与同步日志级别各自一套，不复用 el-timeline 的语义 type） */
const LOG_DOT = {
  create: 'var(--text-disabled)',
  assign: 'var(--color-primary-icon)',
  accept: 'var(--color-primary-icon)',
  resolve: 'var(--color-success-icon)',
  close: 'var(--text-3)',
  reopen: 'var(--color-warning-icon)',
  // 转单用物流强调色：与「流转」动作族区分开，一眼可辨
  transfer: 'var(--color-accent)',
  auto_dispatch: 'var(--color-primary-icon)'
}

/**
 * 优先级形态（2.7）：高 = 实心（需要立刻行动）、中 = 浅底、低 = 描边
 * 字典只给了 el-tag 语义色，区分不出"高优先级要实心"，故在此显式映射
 */
const PRIORITY_VARIANT = { 0: 'outline', 1: 'soft', 2: 'solid' }

const TABS = [
  { name: 'all', label: '全部' },
  { name: '0', label: '待处理' },
  { name: '1', label: '处理中' },
  { name: '2', label: '已解决' },
  { name: '3', label: '已关闭' },
  // 需求6：内部键与文案统一为「超时未处理」（口径 = 已过 SLA 且仍为待处理/处理中）
  { name: 'overdueUnhandled', label: '超时未处理' }
]

const route = useRoute()
const authStore = useAuthStore()

const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')
const currentUser = computed(() => authStore.user || {})

const activeTab = ref('all')
const loading = ref(false)
const loadingDetail = ref(false)
const acting = ref(false)
const list = ref([])
const total = ref(0)
const stations = ref([])
const detailVisible = ref(false)
const detail = ref(null)
const listError = ref(false)
const updatedAt = ref('')

/**
 * 列表请求序号：只认最后一次请求的结果，避免「筛选请求先发、首屏请求后到」时用旧数据覆盖新结果。
 * TODO(扩展): 同类竞态在其它带筛选的列表页（考勤/包裹/员工等）同样存在，且实现相同；
 *   等抽公共 composable（如 useLatestRequest）后统一替换，不要逐页复制这段序号逻辑。
 */
let listSeq = 0

// Tab 计数与列表分开维护：列表只查当前 Tab，计数需要各状态各查一次（pageSize=1 取 total）
const tabCounts = ref({ all: 0 })

// 新建工单 / 自动派单（需求3）
const createVisible = ref(false)
const dispatchVisible = ref(false)
// 新派发的工单高亮 3s，让演示现场一眼找到刚生成的那条
const highlightId = ref(null)
// 高亮定时器句柄：卸载时必须清掉，否则离开页面后回调仍会写已销毁组件的 ref（P1-5）
let highlightTimer = null

/** 建单 / 派单的默认归属驿站：ADMIN 取当前筛选值（未选则第一个驿站），站长固定本站 */
const createStationId = computed(() => {
  if (!isAdmin.value) return currentUser.value.stationId || null
  if (query.stationId !== undefined && query.stationId !== null) return query.stationId
  return stations.value.length ? stations.value[0].id : null
})

const createStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === createStationId.value)
  if (hit) return hit.stationName
  return isAdmin.value ? '' : currentUser.value.stationName || ''
})

/** 模拟群名称按当前驿站取名，贴近真实演示场景 */
const defaultGroupName = computed(() => (createStationName.value ? `${createStationName.value}-异常件处理群` : ''))

// 抽屉宽度统一到 --drawer-w，窄视口退化为 92vw（C-P9，修 P16）
const drawerSize = 'min(var(--drawer-w), 92vw)'

// TODO(扩展): 筛选条件写回 URL（A5-2 的 useQuerySync），并让 el-descriptions 列数随抽屉宽度自适应（A5-3）
const query = reactive({
  stationId: undefined,
  type: undefined,
  priority: undefined,
  keyword: '',
  pageNum: 1,
  pageSize: 20
})

const assignDialog = reactive({ visible: false, orderId: null, orderNo: '', assigneeId: undefined, loading: false })
const assignees = ref([])

const transferDialog = reactive({ visible: false, orderId: null, orderNo: '', fromName: '', loading: false })
const transferForm = reactive({ toEmployeeId: undefined, reason: '' })
const transferFormRef = ref(null)
const transferOptions = ref([])
const transferOptionsLoading = ref(false)

/** 转单理由与后端校验同口径（2-100 字），前端先拦一道，避免明知会被拒仍发请求 */
const TRANSFER_RULES = {
  toEmployeeId: [{ required: true, message: '请选择转单对象', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写转单理由', trigger: 'blur' },
    { min: 2, max: 100, message: '转单理由长度须为 2-100 字', trigger: 'blur' }
  ]
}

const hasFilter = computed(
  () =>
    query.stationId !== undefined ||
    query.type !== undefined ||
    query.priority !== undefined ||
    !!query.keyword ||
    activeTab.value !== 'all'
)

const headerSub = computed(
  () =>
    `${isAdmin.value ? '数据范围：全域' : '数据范围：本站'} · 共 ${total.value} 条 · 更新于 ${updatedAt.value || '—'}`
)

/** 流转操作权限：镜像 Mock 的 canManage（ADMIN 全量 / 本站站长 / 本人为处理人） */
function canManage(order) {
  const user = currentUser.value
  if (!user.role) return false
  if (user.role === 'ADMIN') return true
  if (user.role === 'STATION_ADMIN') return order.stationId === user.stationId
  return order.assigneeId === user.id
}

/**
 * 指派入口仅对 ADMIN 开放：/employees 是 ADMIN 专属接口，非 ADMIN 拿不到本站员工列表供选择。
 * TODO(扩展): 待后端提供「本站员工简表」接口后，按 Mock 的 canAssign 规则对站长开放指派
 */
const canAssign = computed(() => isAdmin.value && detail.value && detail.value.status !== 3)

/** 转单入口：权限与流转同口径（ADMIN / 本站站长 / 当前处理人） */
const canTransfer = computed(() => !!detail.value && canManage(detail.value))

/** 候选范围提示：把「为什么搜不到某个同事」讲在前面，减少无效尝试 */
const transferScopeHint = computed(() => (isAdmin.value ? '请选择员工（老板可跨驿站）' : '请选择本站员工'))
const transferCandidates = computed(() => transferOptions.value)

/**
 * 时间线事件：转单在留痕表里有结构化数据（转出人 → 转入人 + 理由），
 * 故从 handleLog 中剔除同名事件、改用 transfers 渲染，避免同一次转单在时间线上出现两条。
 */
const timelineEvents = computed(() => {
  const order = detail.value
  if (!order) return []
  const logs = (order.handleLog || []).reduce((acc, log, index) => {
    if (log.action === 'transfer') return acc
    acc.push({
      key: `log-${index}`,
      time: log.time,
      action: log.action,
      operatorName: log.operatorName,
      content: log.content
    })
    return acc
  }, [])
  const transfers = (order.transfers || []).map((item) => ({
    key: `transfer-${item.id}`,
    time: item.transferTime,
    action: 'transfer',
    operatorName: item.operatorName,
    fromName: item.fromEmployeeName,
    toName: item.toEmployeeName,
    reason: item.reason
  }))
  // 两段数据都按时间串（YYYY-MM-DD HH:mm:ss）递增拼回单一时间线，字符串比较等价于时间比较
  return [...logs, ...transfers].sort((a, b) => (a.time < b.time ? -1 : 1))
})

const availableActions = computed(() => {
  if (!detail.value || !canManage(detail.value)) return []
  return (TRANSITIONS[detail.value.status] || []).map((target) => ({
    target,
    label: actionLabel(detail.value.status, target),
    // 关闭是终态但非危险操作（info）；驳回重开单独用 warning 描边，避免与"接单"同款
    type: target === 3 ? 'info' : target === 1 && detail.value.status === 2 ? 'warning' : 'primary',
    plain: target === 1 && detail.value.status === 2
  }))
})

function actionLabel(from, to) {
  if (to === 3) return '关闭工单'
  if (to === 1) return from === 2 ? '驳回重开' : '接单处理'
  return '标记解决'
}

/** 超时未处理行底色 + 新派发工单高亮（后者供演示时快速定位刚生成的工单） */
function rowClassName({ row }) {
  const classes = []
  if (row.overdueUnhandled) classes.push('is-oversla')
  if (row.id === highlightId.value) classes.push('is-highlight')
  return classes.join(' ')
}

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** 只含筛选条件的公共部分，供列表与 Tab 计数复用 */
function baseParams() {
  return {
    stationId: query.stationId,
    type: query.type,
    priority: query.priority,
    keyword: query.keyword || undefined
  }
}

function buildParams() {
  const params = baseParams()
  if (activeTab.value === 'overdueUnhandled') params.overdueUnhandled = '1'
  else if (activeTab.value !== 'all') params.status = Number(activeTab.value)
  return params
}

async function fetchList() {
  const seq = (listSeq += 1)
  loading.value = true
  listError.value = false
  try {
    const page = await getWorkOrders({ ...buildParams(), pageNum: query.pageNum, pageSize: query.pageSize })
    // 请求期间用户又改了筛选／翻了页：本次结果已过期，直接丢弃，连 loading 也交给更新的那次收尾
    if (seq !== listSeq) return
    list.value = page.list
    total.value = page.total
    updatedAt.value = stamp()
  } catch (e) {
    if (seq !== listSeq) return
    list.value = []
    total.value = 0
    listError.value = true
  } finally {
    if (seq === listSeq) loading.value = false
  }
}

/**
 * Tab 计数：计数失败不阻塞列表，只让计数位不显示
 * TODO(扩展): 待后端提供 GET /work-orders/stats 聚合接口后改为单次请求（A7-3 依赖 R-1），
 * 现在 6 次 pageSize=1 的请求会随筛选维度增加而继续膨胀
 */
async function loadCounts() {
  const base = baseParams()
  try {
    const [all, s0, s1, s2, s3, over] = await Promise.all([
      getWorkOrders({ ...base, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ ...base, status: 0, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ ...base, status: 1, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ ...base, status: 2, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ ...base, status: 3, pageNum: 1, pageSize: 1 }),
      getWorkOrders({ ...base, overdueUnhandled: '1', pageNum: 1, pageSize: 1 })
    ])
    tabCounts.value = {
      all: all.total,
      0: s0.total,
      1: s1.total,
      2: s2.total,
      3: s3.total,
      overdueUnhandled: over.total
    }
  } catch (e) {
    tabCounts.value = {}
  }
}

async function loadDetail(id) {
  loadingDetail.value = true
  try {
    detail.value = await getWorkOrderDetail(id)
  } catch (e) {
    detail.value = null
  } finally {
    loadingDetail.value = false
  }
}

async function openDetail(id) {
  detailVisible.value = true
  detail.value = null
  await loadDetail(id)
}

/**
 * 复制整条工单为多行纯文本（列表行内与详情抽屉共用同一份拼装与降级链路）
 * 剪贴板通道全部失败时必须给可见提示，不做静默失败
 */
async function handleCopy(order) {
  const ok = await copyText(buildWorkOrderText(order))
  if (ok) ElMessage.success('已复制工单详情')
  else ElMessage.error('复制失败，请手动选中文本后复制')
}

async function loadStations() {
  try {
    stations.value = await getStations()
  } catch (e) {
    /* 站点下拉失败不阻塞列表筛选 */
  }
}

/**
 * TODO(扩展): 「待处理直关」的原因改用与转单同款的 el-dialog + textarea(show-word-limit)（A7-7），
 * 当前用 ElMessageBox.prompt 单行输入，与转单理由不是一套形态
 */
async function handleTransition(target) {
  const order = detail.value
  if (!order) return
  const label = actionLabel(order.status, target)
  const closeReasonRequired = target === 3 && order.status === 0
  let remark = ''
  try {
    const result = await ElMessageBox.prompt(
      closeReasonRequired ? '未处理的工单需填写关闭原因' : '可填写处理说明（选填）',
      label,
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: closeReasonRequired ? '关闭原因（必填）' : '处理说明',
        inputValidator: (text) => (closeReasonRequired && !String(text || '').trim() ? '请填写关闭原因' : true)
      }
    )
    remark = result.value || ''
  } catch (e) {
    return // 用户取消
  }

  acting.value = true
  try {
    await changeWorkOrderStatus(order.id, target, remark)
    ElMessage.success(`${label}成功`)
    await refreshPage()
    await loadDetail(order.id)
  } catch (e) {
    /* 拦截器已统一提示（8001/8002） */
  } finally {
    acting.value = false
  }
}

async function openAssign(order) {
  assignDialog.visible = true
  assignDialog.orderId = order.id
  assignDialog.orderNo = order.orderNo
  assignDialog.assigneeId = undefined
  assignees.value = []
  try {
    const page = await getEmployees({ stationId: order.stationId, status: 1, pageNum: 1, pageSize: 100 })
    assignees.value = page.list
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

async function submitAssign() {
  if (!assignDialog.assigneeId) {
    ElMessage.warning('请选择处理人')
    return
  }
  assignDialog.loading = true
  try {
    await assignWorkOrder(assignDialog.orderId, assignDialog.assigneeId)
    ElMessage.success('指派成功，已通知处理人')
    assignDialog.visible = false
    await refreshPage()
    await loadDetail(assignDialog.orderId)
  } catch (e) {
    /* 拦截器已统一提示（8002） */
  } finally {
    assignDialog.loading = false
  }
}

/** 列表与计数一起刷新：Tab 计数与列表数据必须同源同刻，否则会出现"计数 3、列表 2 条" */
function refreshPage() {
  fetchList()
  loadCounts()
}

/** 新建成功后自动展开该工单详情（B3.2 交互流程） */
async function handleCreated(result) {
  await refreshPage()
  if (result && result.id) await openDetail(result.id)
}

/**
 * 自动派单成功后：回到「全部」Tab 并刷新，再把新工单高亮 3s。
 * 切 Tab 的理由：新工单 status=0，若当前停在「已关闭」这类筛选下，高亮目标根本不在列表里，
 * 演示现场会以为「派单没成功」。
 */
async function handleDispatched(order) {
  activeTab.value = 'all'
  query.pageNum = 1
  highlightId.value = order.id
  await refreshPage()
  await openDetail(order.id)
  // 连续派单时先清掉上一轮，避免旧回调提前把新高亮取消
  clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => {
    if (highlightId.value === order.id) highlightId.value = null
  }, 3000)
}

/**
 * 转单候选：按角色收敛范围，与后端 8004 同口径，减少必然失败的请求
 * - ADMIN 走 /employees（可选全域在职员工，支持跨站转单）
 * - 站长/处理人走 /schedules 的本站名册（/employees 是 ADMIN 专属，站长打开会 403）
 * 两种来源都排除本人：后端对「转给自己」直接回 8004
 * TODO(扩展): 排班名册不含在职状态，本站禁用员工仍会出现在下拉里（由 8004 兜底）；
 * 待后端提供「本站员工简表」接口后改为按 status 过滤，并复用给指派弹窗放开站长指派
 */
async function openTransfer(order) {
  transferDialog.visible = true
  transferDialog.orderId = order.id
  transferDialog.orderNo = order.orderNo
  transferDialog.fromName = order.assigneeName
  transferForm.toEmployeeId = undefined
  transferForm.reason = ''
  transferOptions.value = []
  transferOptionsLoading.value = true
  const me = currentUser.value
  try {
    if (isAdmin.value) {
      const page = await getEmployees({ status: 1, pageNum: 1, pageSize: 100 })
      transferOptions.value = page.list
        .filter((item) => item.id !== me.id)
        .map((item) => ({
          id: item.id,
          label: `${item.realName}（${item.stationName || '总部'} · ${ROLE_LABEL[item.role] || item.role}）`
        }))
    } else {
      const matrix = await getSchedules({ stationId: me.stationId })
      transferOptions.value = matrix.employees
        .filter((item) => item.employeeId !== me.id)
        .map((item) => ({ id: item.employeeId, label: `${item.employeeName}（本站）` }))
    }
  } catch (e) {
    /* 拦截器已统一提示；下拉为空时提交会被表单必填拦下 */
  } finally {
    transferOptionsLoading.value = false
  }
}

async function submitTransfer() {
  const form = transferFormRef.value
  if (form) {
    const valid = await form.validate().catch(() => false)
    if (!valid) return
  }
  const target = transferOptions.value.find((item) => item.id === transferForm.toEmployeeId)
  try {
    await ElMessageBox.confirm(
      `确认将工单 ${transferDialog.orderNo} 由「${transferDialog.fromName || '未指派'}」转给「${target ? target.label : ''}」？转单只变更处理人，工单状态不变。`,
      '转单确认',
      { confirmButtonText: '确认转单', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }

  transferDialog.loading = true
  try {
    // silent：8003/8004 需要给可操作文案（如"只能转给本站在职同事"），通用 toast 覆盖不到
    await transferWorkOrder(
      transferDialog.orderId,
      { toEmployeeId: transferForm.toEmployeeId, reason: transferForm.reason.trim() },
      { silent: true }
    )
    ElMessage.success('转单成功，已通知新处理人')
    transferDialog.visible = false
    await refreshPage()
    await loadDetail(transferDialog.orderId)
  } catch (e) {
    const code = e && e.code
    if (code === DEMO_CODE.WORK_ORDER_TRANSFER_TARGET_INVALID) {
      ElMessage.error('转单对象不合法：只能转给在职同事，不能转给自己；站长与处理人只能转本站同事')
    } else if (code === DEMO_CODE.WORK_ORDER_TRANSFER_NO_PERMISSION) {
      ElMessage.error('无权转单该工单：仅老板、本站站长或当前处理人可转单')
    } else {
      ElMessage.error((e && e.message) || '转单失败，请稍后重试')
    }
  } finally {
    transferDialog.loading = false
  }
}

function handleTabChange() {
  query.pageNum = 1
  fetchList()
}

function handleSearch() {
  query.pageNum = 1
  refreshPage()
}

function handleReset() {
  query.stationId = undefined
  query.type = undefined
  query.priority = undefined
  query.keyword = ''
  query.pageNum = 1
  refreshPage()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

onMounted(() => {
  refreshPage()
  if (isAdmin.value) loadStations()
  // 通知中心跳转（?orderId=）时直接展开对应工单
  if (route.query.orderId) openDetail(route.query.orderId)
})

onUnmounted(() => clearTimeout(highlightTimer))
</script>

<style scoped lang="scss">
.work-order-page {
  // Tab 条独立于卡片：高 40px，选中项主色下划线由 --el-color-primary 承接
  .wo-tabs {
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

    &__count {
      margin-left: var(--sp-1);
      font-variant-numeric: tabular-nums;

      &.is-danger {
        color: var(--color-danger);
      }
    }
  }

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

    :deep(.filter-input) {
      width: 200px;
    }
  }

  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  /* 超时未处理行：左侧 3px 红色竖条 + 极浅底。改前用 !important 覆盖斑马纹（P17），
     现在去掉斑马纹 + 改由行类名接管，不再与 Element 的层级机制打架
     （类名 is-oversla 沿用不动，纯内部标识，改名只会扩大改动面） */
  :deep(.el-table__body .is-oversla > td.el-table__cell) {
    background-color: var(--state-danger-row-bg);
  }

  :deep(.el-table__body .is-oversla > td.el-table__cell:first-child) {
    box-shadow: inset 3px 0 0 var(--color-danger-icon);
  }

  /* 新派发工单的短时高亮：让演示现场一眼定位刚生成的工单 */
  :deep(.el-table__body .is-highlight > td.el-table__cell) {
    background-color: var(--state-primary-bg);
    transition: background-color var(--dur-slow) var(--ease-std);
  }

  .sla-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;

    &__deadline {
      font-size: var(--fs-caption);
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
    }
  }

  /* link 形态默认 padding 只有 2px：图标按钮补到 8px，鼠标可点区域与「详情」拉开间距 */
  .wo-copy {
    padding: var(--sp-2);
    margin-left: var(--sp-1);
  }

  .drawer-body {
    min-height: 200px;
  }

  .detail-block {
    margin-top: var(--sp-5);

    .block-title {
      margin-bottom: var(--sp-2);
      font-size: var(--fs-h3);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
    }

    .block-hint {
      margin-left: var(--sp-2);
      font-size: var(--fs-caption);
      font-weight: var(--fw-regular);
      color: var(--text-3);
    }

    .block-text {
      margin: 0;
      font-size: var(--fs-body);
      line-height: 1.7;
      color: var(--text-2);
    }

    .log-line {
      display: flex;
      align-items: center;
      gap: var(--sp-2);

      .log-operator {
        font-size: var(--fs-caption);
        color: var(--text-3);
      }
    }

    // 转单留痕：换人链路用「A → B」表达，与普通流转事件的纯文本说明区分
    .transfer-line {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
      margin-top: var(--sp-1);

      &__who {
        padding: 0 var(--sp-2);
        border: 1px solid var(--state-warning-border);
        border-radius: var(--r-xs);
        background-color: var(--state-warning-bg);
        color: var(--state-warning-fg);
        font-size: var(--fs-caption);
        line-height: var(--lh-caption);

        &--to {
          border-color: var(--state-primary-border);
          background-color: var(--state-primary-bg);
          color: var(--state-primary-fg);
        }
      }

      &__arrow {
        color: var(--color-accent);
        font-size: var(--fs-caption);
      }
    }

    .log-content {
      margin-top: 2px;
      font-size: var(--fs-body);
      color: var(--text-2);
    }
  }

  .transfer-select {
    width: 100%;
  }

  .transfer-tip {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  .drawer-footer {
    display: flex;
    align-items: center;
    gap: var(--sp-2);

    // 主操作固定右侧：按钮位置不随状态变化跳动，降低误点概率（A7-5）
    &__secondary {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
    }

    &__primary {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
      margin-left: auto;
    }

    .footer-tip {
      font-size: var(--fs-caption);
      color: var(--text-3);
    }
  }
}
</style>
