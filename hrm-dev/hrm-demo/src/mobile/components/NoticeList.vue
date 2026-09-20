<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import PageState from './PageState.vue'
import StatusTag from './StatusTag.vue'
import { NOTIFICATION_TYPE, PUBLISH_SCOPE, dictLabel } from '@/shared/constants/dict.js'
import { useAuthStore } from '../stores/auth.js'
import { useNotifyStore } from '../stores/notify.js'
import { relativeTime } from '../utils/format.js'

/**
 * 通知列表（D2-4，Organism）—— 从原 staff/notification.vue 抽出，列表交互整体保留不重写
 *
 * 点击通知标记已读并按 bizType/bizId 跳转业务详情，跳转后若目标页不可见（如 STAFF 点同步失败通知）
 * 给出明确提示而不是静默失败；分页由一次拉 50 条改为 van-list 20/页（低端机首屏卡顿）。
 * 手工发布的公告与系统联动通知同列表：前者没有业务跳转，但必须能一眼认出「这是谁发的、发给谁」。
 *
 * 状态（7 态）：默认按时间倒序；加载走 van-list/PageState；空态按当前 Tab 给不同文案；
 * 错误走 PageState error + 重试；禁用 = 未读为 0 时「全部已读」置灰并给出原因；无权限不适用（通知全员可见）；
 * 边界 = 标题省略、内容截断、bizType 目标页无权限时给明确 Toast。
 * TODO(扩展): 契约暂未下发「指定驿站 / 指定员工」的具体目标，范围只显示到档位；
 *   待 notification 的 VO 补 publishScopeTarget 后再显示「城东驿站 / 指定 3 人」
 */
const ANNOUNCEMENT_TAG = { PUBLISHED: { label: '公告' } }
const PAGE_SIZE = 20

const router = useRouter()
const auth = useAuthStore()
const notify = useNotifyStore()

const activeTab = ref('')
const list = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const error = ref('')
const initialized = ref(false)

let busy = false

const unreadCount = computed(() => list.value.filter((item) => !item.isRead).length)
const emptyText = computed(() => (activeTab.value === 0 ? '没有未读通知' : '暂无通知'))

/** 发布范围：契约只下发档位，未下发具体目标（见文件头 TODO） */
const scopeLabel = (scope) => (scope ? dictLabel(PUBLISH_SCOPE, scope) : '')

async function fetchPage() {
  if (busy) return
  busy = true
  try {
    const page = await notify.fetchList({ isRead: activeTab.value, pageNum: pageNum.value, pageSize: PAGE_SIZE })
    error.value = ''
    list.value = pageNum.value === 1 ? page.list : list.value.concat(page.list)
    finished.value = list.value.length >= page.total
    pageNum.value += 1
    await notify.refresh()
  } catch (e) {
    error.value = e.message || '加载失败'
    finished.value = true
  } finally {
    busy = false
    loading.value = false
    refreshing.value = false
    initialized.value = true
  }
}

function reset() {
  pageNum.value = 1
  list.value = []
  finished.value = false
  error.value = ''
}

async function onLoad() {
  loading.value = true
  await fetchPage()
}

async function onRefresh() {
  reset()
  await fetchPage()
}

async function onReadAll() {
  if (!unreadCount.value) {
    showToast('没有未读通知')
    return
  }
  try {
    await showConfirmDialog({ title: '全部已读', message: `将 ${unreadCount.value} 条未读通知标记为已读？` })
  } catch (e) {
    return
  }
  await notify.markAllRead()
  showSuccessToast('已全部标记为已读')
  await onRefresh()
}

/** 点击通知：先标记已读，再按业务类型跳转 */
async function onOpen(item) {
  if (!item.isRead) {
    try {
      // 角标同步收敛在 store 内，组件只负责替换本行数据
      const updated = await notify.markRead(item.id)
      Object.assign(item, updated)
    } catch (e) {
      // 9001（通知不存在）等错误已由 http 层提示
    }
  }
  if (item.bizType === 'work_order' && item.bizId) {
    router.push(`/staff/workorder/${item.bizId}`)
    return
  }
  if (item.bizType === 'sync_task') {
    if (auth.canSeeSync) router.push('/staff/sync')
    else showToast('同步状态页仅站长可见，请切换到站长身份查看')
    return
  }
  if (item.bizType === 'parcel' && item.bizId) {
    router.push(`/staff/parcel/${item.bizId}`)
    return
  }
  // 需求 9/10 的联动通知（工资单发布/异议处理、入离职流程进度）：一跳到位，跳不到的通知等于死信（A13-5）
  if (item.bizType === 'payroll' && item.bizId) {
    router.push(`/staff/payroll/${item.bizId}`)
    return
  }
  if (item.bizType === 'flow') {
    router.push('/staff/flow')
  }
  // 请假（M11 §6.3）：同一份 NoticeList 同时服务老板端与员工端，落点必须按角色分流；
  // 不跳详情页（移动端不新增详情路由，§3.1），三个落点页的默认筛选已能定位到相关单。
  // TODO(扩展): bizId 已随通知下发，待移动端补详情路由后可深链到具体那一单。
  if (item.bizType === 'leave') {
    if (auth.isAdmin) router.push('/boss/leave')
    else if (auth.role === 'STATION_ADMIN') router.push('/staff/leave/review')
    else router.push('/staff/leave')
    return
  }
  // TODO(扩展): 无 bizType 但需确认（如「工资单已发布，请确认」）的通知，待契约补动作字段后加「去处理」按钮
}

watch(activeTab, () => {
  reset()
  onLoad()
})

onMounted(onLoad)
</script>

<template>
  <div>
    <van-tabs v-model:active="activeTab" class="bleed">
      <van-tab title="全部" name="" />
      <van-tab :name="0">
        <template #title>
          未读<span v-if="notify.unread" class="tab-count">{{ notify.unread }}</span>
        </template>
      </van-tab>
    </van-tabs>

    <div class="tool-row">
      <span>{{ activeTab === 0 ? `${unreadCount} 条未读` : `共 ${list.length} 条` }}</span>
      <!-- 禁用要给原因：未读为 0 时按钮置灰并说明「当前没有未读通知」，不让用户以为按钮坏了 -->
      <button
        type="button"
        class="read-all"
        :disabled="!unreadCount"
        :title="unreadCount ? '' : '当前没有未读通知'"
        @click="onReadAll"
      >
        全部已读
      </button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <PageState
        :loading="!initialized"
        :error="list.length ? '' : error"
        :empty="initialized && !list.length"
        :empty-text="emptyText"
        :rows="5"
        @retry="onRefresh"
      >
        <van-list
          v-model:loading="loading"
          :finished="finished"
          :immediate-check="false"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <div
            v-for="item in list"
            :key="item.id"
            class="list-item"
            :class="{ 'list-item--marked': !item.isRead }"
            role="button"
            tabindex="0"
            @click="onOpen(item)"
            @keydown.enter="onOpen(item)"
            @keydown.space.prevent="onOpen(item)"
          >
            <div class="list-item__title">
              <span>
                <i v-if="!item.isRead" class="unread-dot" aria-hidden="true" />
                <span :class="{ 'is-unread': !item.isRead }">{{ item.title }}</span>
              </span>
              <span class="title-tags">
                <!-- 公告是「人发的」而不是「系统联动的」，必须先于类型标签被认出来 -->
                <StatusTag v-if="item.isPublished" :dict="ANNOUNCEMENT_TAG" value="PUBLISHED" variant="outline" />
                <StatusTag :dict="NOTIFICATION_TYPE" :value="item.type" variant="outline" />
              </span>
            </div>
            <div class="list-item__meta">{{ item.content }}</div>
            <div class="flex-between">
              <span class="list-item__meta">
                {{ relativeTime(item.createTime) }}
                <template v-if="item.isPublished">
                  · 由 {{ item.publisherName || '管理员' }} 发布 · 范围：{{ scopeLabel(item.publishScope) }}
                </template>
              </span>
              <span v-if="!item.isRead" class="list-item__meta">未读</span>
            </div>
          </div>
        </van-list>
      </PageState>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.tab-count {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  margin-left: var(--sp-1);
  font-size: var(--fs-micro);
  line-height: 16px;
  color: var(--text-on-dark);
  text-align: center;
  background: var(--color-danger);
  border-radius: var(--r-full);
}

.read-all {
  min-height: 44px;
  padding: 0;
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

.read-all:disabled {
  color: var(--text-disabled);
}

.unread-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: var(--sp-1);
  vertical-align: middle;
  background: var(--color-danger);
  border-radius: var(--r-full);
}

/* 未读标题加粗（颜色 + 字重双通道，不只靠圆点）；已读降一级到二级文本 */
.is-unread {
  font-weight: var(--fw-semibold);
}

.list-item__title > span:first-child {
  display: flex;
  align-items: center;
}

/* 标题右侧可能并排「公告 + 类型」两枚标签，不能被标题挤变形 */
.title-tags {
  display: inline-flex;
  flex: none;
  gap: var(--sp-1);
  align-items: center;
}
</style>
