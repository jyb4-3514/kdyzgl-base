<template>
  <div class="notification-page">
    <PageHeader title="通知中心" :sub="headerSub" :loading="loading">
      <template #actions>
        <!-- 发布通知仅 ADMIN：契约 roles:['ADMIN']，站长/员工不可发布（B4.1） -->
        <el-button v-if="isAdmin" type="primary" :icon="Promotion" @click="publishVisible = true">发布通知</el-button>
        <el-button :icon="Refresh" @click="refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <!-- Tab 独立于卡片，与工单页保持同一层次结构（5.5） -->
    <el-tabs v-model="activeTab" class="notice-tabs" @tab-change="handleTabChange">
      <el-tab-pane name="all" label="全部" />
      <el-tab-pane name="unread">
        <template #label>
          <span>未读</span>
          <span v-if="unreadCount" class="notice-tabs__count">{{ unreadCount }}</span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-card shadow="never" class="content-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <!-- 禁用态给原因，避免用户对着灰按钮猜（A8-4） -->
          <el-button
            :disabled="!unreadCount"
            :title="unreadCount ? '将全部未读标记为已读' : '当前没有未读通知'"
            @click="handleReadAll"
          >
            全部标记已读
          </el-button>
          <!-- TODO(扩展): 通知类型筛选待契约补 type 参数后开放（A8-3 依赖 R-2），
               契约当前只认 isRead，先不加无效控件，避免出现「筛选了但不生效」 -->
          <span class="toolbar-tip">共 {{ total }} 条 · 未读 {{ unreadCount }} 条</span>
        </div>
        <el-button :icon="Refresh" circle text :loading="loading" aria-label="刷新列表" @click="refreshPage" />
      </div>

      <StateBlock v-if="listError" variant="error" title="通知加载失败" @action="refreshPage" />

      <StateBlock
        v-else-if="!loading && !list.length"
        variant="empty"
        :title="activeTab === 'unread' ? '没有未读通知' : '暂无通知'"
      />

      <div v-else class="notice-list">
        <div v-for="row in skeletonRows" :key="`sk-${row}`" class="notice-skeleton">
          <el-skeleton :rows="1" animated />
        </div>

        <div
          v-for="item in list"
          :key="item.id"
          class="notice-item"
          :class="{ 'is-unread': !item.isRead }"
          role="button"
          tabindex="0"
          :aria-label="`${item.title}，${item.isRead ? '已读' : '未读'}，${formatRelativeTime(item.createTime)}`"
          @click="handleOpen(item)"
          @keydown.enter.prevent="handleOpen(item)"
          @keydown.space.prevent="handleOpen(item)"
        >
          <div class="notice-main">
            <div class="notice-head">
              <!-- 类型标签统一到状态标签体系（改前是 el-tag effect="plain"） -->
              <StatusTag :dict="NOTIFICATION_TYPE" :value="item.type" variant="outline" />
              <!-- 手工公告与系统联动通知的处置优先级不同，标题前必须可辨（A8-2 / B4.5） -->
              <StatusTag v-if="item.isPublished" :dict="ANNOUNCEMENT" value="ANNOUNCEMENT" variant="outline" />
              <span class="notice-title">{{ item.title }}</span>
              <!-- 未读同时给竖条 + 文字标签：不只靠颜色/字重区分（修颜色单一依赖） -->
              <span v-if="!item.isRead" class="notice-flag">未读</span>
              <span class="notice-time">{{ formatRelativeTime(item.createTime) }}</span>
            </div>
            <div class="notice-content">{{ item.content }}</div>
            <div v-if="item.isPublished" class="notice-meta">
              由 {{ item.publisherName || '系统' }} 发布 · 范围：{{
                dictLabel(PUBLISH_SCOPE, item.publishScope, '全员')
              }}
            </div>
          </div>
          <div class="notice-actions" @click.stop>
            <el-button link type="primary" :disabled="item.isRead" @click="handleRead(item)">标记已读</el-button>
            <el-button link type="primary" :disabled="!item.bizType" @click="handleOpen(item)">查看</el-button>
          </div>
        </div>
      </div>

      <div v-if="list.length" class="pagination-wrap">
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
    </el-card>

    <!-- 发布通知（需求4）：仅 ADMIN 渲染 -->
    <PublishDrawer v-if="isAdmin" v-model="publishVisible" :stations="stations" @published="handlePublished" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion, Refresh } from '@element-plus/icons-vue'
import { getStations } from '@admin/api/station'
import { useAuthStore } from '@admin/stores/auth'
import { NOTIFICATION_TYPE, PUBLISH_SCOPE, dictLabel } from '@/shared/constants/dict'
import {
  getNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead
} from '../../api/notification.js'
import { formatRelativeTime } from '../../utils/format.js'
import PageHeader from '../../components/PageHeader.vue'
import StateBlock from '../../components/StateBlock.vue'
import StatusTag from '../../components/StatusTag.vue'
import PublishDrawer from './components/PublishDrawer.vue'

/**
 * 通知中心（T18，demo-ui-redesign.md 5.5，修 P21）
 * 只展示当前登录人的通知（Mock 按 token 收口），未读筛选走 isRead 参数；
 * 点击通知 → 标记已读 → 跳到 biz_type/biz_id 指向的业务页并自动展开详情（各页读 query 打开抽屉）。
 *
 * 提醒：通知是按人写入的数据，演示账号在种子里只有 1 条，页面能走通但列表偏空；
 * 想演示满屏未读与跳转，走「工单指派 → 被指派人收通知」这条链路（T16 的剧本预置也会补数据）。
 */

/** biz_type → 目标页与定位参数（参数名与对应页面的 route.query 读取保持一致） */
const BIZ_ROUTE = {
  work_order: (bizId) => ({ path: '/work-order', query: { orderId: bizId } }),
  parcel: (bizId) => ({ path: '/parcel', query: { parcelId: bizId } }),
  sync_task: (bizId) => ({ path: '/parcel/sync', query: { taskId: bizId } })
}

const router = useRouter()
const authStore = useAuthStore()

const isAdmin = computed(() => !!authStore.user && authStore.user.role === 'ADMIN')

/** 手工公告标识（B4.5）：只有 isPublished=true 的行才挂这枚标签 */
const ANNOUNCEMENT = { ANNOUNCEMENT: { label: '公告', type: 'info' } }

const publishVisible = ref(false)
const stations = ref([])

const activeTab = ref('all')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const unreadCount = ref(0)
const listError = ref(false)
const updatedAt = ref('')

// TODO(扩展): Tab 与筛选写回 URL（A5-2 的 useQuerySync）
const query = reactive({ pageNum: 1, pageSize: 20 })

const skeletonRows = computed(() => (loading.value && !list.value.length ? 5 : 0))

const headerSub = computed(
  () => `仅显示当前账号的通知 · 共 ${total.value} 条 · 未读 ${unreadCount.value} 条 · 更新于 ${updatedAt.value || '—'}`
)

function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

async function fetchList() {
  loading.value = true
  listError.value = false
  try {
    const page = await getNotifications({
      isRead: activeTab.value === 'unread' ? 0 : undefined,
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

async function fetchUnreadCount() {
  try {
    const data = await getUnreadCount()
    unreadCount.value = data.count
  } catch (e) {
    /* 角标失败不阻塞列表 */
  }
}

/** 顶部工具条的刷新：列表与未读数一起拉，避免角标与列表不一致 */
function refreshPage() {
  fetchList()
  fetchUnreadCount()
}

async function handleRead(item) {
  if (item.isRead) return
  try {
    await markNotificationRead(item.id)
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    // 未读 Tab 下被标记的这条已不属于当前筛选，重新拉取保证分页与总数一致
    if (activeTab.value === 'unread') fetchList()
  } catch (e) {
    /* 拦截器已统一提示（9001） */
  }
}

async function handleOpen(item) {
  await handleRead(item)
  const to = item.bizType && BIZ_ROUTE[item.bizType]
  if (!to) {
    ElMessage.info('该通知没有关联的业务详情')
    return
  }
  router.push(to(item.bizId))
}

async function handleReadAll() {
  try {
    await markAllNotificationsRead()
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
    fetchList()
  } catch (e) {
    /* 拦截器已统一提示 */
  }
}

function handleTabChange() {
  query.pageNum = 1
  fetchList()
}

function handleSizeChange() {
  query.pageNum = 1
  fetchList()
}

async function loadStations() {
  try {
    stations.value = await getStations()
  } catch (e) {
    /* 驿站下拉失败不阻塞发布（发布抽屉会给出可操作提示） */
  }
}

/** 发布成功后刷新列表与未读数：发布范围含老板本人时，自己也会收到这条公告 */
function handlePublished() {
  refreshPage()
}

onMounted(() => {
  refreshPage()
  if (isAdmin.value) loadStations()
})
</script>

<style scoped lang="scss">
.notification-page {
  .notice-tabs {
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
    }
  }

  .content-card {
    margin-bottom: 0;
  }

  .toolbar-tip {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }

  .notice-list {
    min-height: 120px;
  }

  .notice-skeleton {
    min-height: 68px;
    padding: var(--sp-3) var(--sp-4);
    border-bottom: 1px solid var(--border-line);
  }

  .notice-item {
    position: relative;
    display: flex;
    align-items: flex-start;
    gap: var(--sp-3);
    padding: var(--sp-3) var(--sp-4);
    border-bottom: 1px solid var(--border-line);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-std);

    &:hover {
      background-color: var(--surface-subtle);
    }

    // 未读：左侧 3px 主色竖条，与"已读"形成稳定的位置差异
    &.is-unread::before {
      content: '';
      position: absolute;
      top: 0;
      bottom: 0;
      left: 0;
      width: 3px;

      /* 未读 3px 主色竖条：装饰性竖线不承载白字，按硬规则走 500 档 */
      background-color: var(--color-primary-icon);
    }

    .notice-main {
      flex: 1;
      min-width: 0;
    }

    .notice-head {
      display: flex;
      align-items: center;
      gap: var(--sp-2);
    }

    .notice-title {
      font-size: var(--fs-body);
      font-weight: var(--fw-regular);
      color: var(--text-2);
    }

    &.is-unread .notice-title {
      font-weight: var(--fw-semibold);
      color: var(--text-1);
    }

    .notice-flag {
      padding: 0 6px;
      border-radius: var(--r-xs);
      background-color: var(--state-primary-bg);
      color: var(--state-primary-fg);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
    }

    .notice-time {
      margin-left: auto;
      font-size: var(--fs-caption);
      color: var(--text-3);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .notice-content {
      margin-top: var(--sp-1);
      font-size: var(--fs-body);
      line-height: var(--lh-body);
      color: var(--text-3);
      word-break: break-all;
    }

    // 发布人与发布范围（B4.5）：公告的"来源"是判断其权威性的必要信息
    .notice-meta {
      margin-top: var(--sp-1);
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
      color: var(--text-3);
    }

    .notice-actions {
      flex-shrink: 0;
    }
  }

  /* 操作按钮 hover 时才显性化（改前常驻会挤压标题）；键盘聚焦或触屏设备下始终可见，
     否则触屏用户永远看不到这两个入口 */
  @media (hover: hover) {
    .notice-item .notice-actions {
      opacity: 0;
      transition: opacity var(--dur-fast) var(--ease-std);
    }

    .notice-item:hover .notice-actions,
    .notice-item:focus-within .notice-actions {
      opacity: 1;
    }
  }
}
</style>
