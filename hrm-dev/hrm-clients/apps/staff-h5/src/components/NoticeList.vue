<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { NOTICE_ANNOUNCEMENT, NOTIFICATION_TYPE, PUBLISH_SCOPE, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import { useNotifyStore } from '../stores/notify.js'
import { relativeTime } from '../utils/format.js'

/**
 * 通知列表（D2-4，Organism）—— 从原 staff/notification.vue 抽出，列表加载/分页整体保留不重写
 *
 * 点击通知一律进本端阅读页（公告与带 bizType 的通知同入口），正文在那里全量呈现、
 * 业务动作降为阅读页底部的「去处理」（D3.1）。标记已读改由阅读页在详情取回成功后发起：
 * 列表侧零写请求、无 Toast、无 bizType 分支 —— 把一个网络写请求挡在导航前，只会把
 * 「点了没反馈」升级成「卡一下仍没反馈」，是同一个病的另一种形态。
 * 手工发布的公告与系统联动通知同列表：前者没有业务跳转，但必须能一眼认出「这是谁发的、发给谁」。
 *
 * 状态（7 态）：默认按时间倒序；加载走 van-list/PageState；空态按当前 Tab 给不同文案；
 * 错误走 PageState error + 重试；禁用 = 未读为 0 时「全部已读」置灰并给出原因；无权限不适用（通知全员可见）；
 * 边界 = 标题省略、正文两行截断、行尾 chevron 提示「可打开阅读全文」。
 * TODO(扩展): 契约暂未下发「指定驿站 / 指定员工」的具体目标，范围只显示到档位；
 *   待 notification 的 VO 补 publishScopeTarget 后再显示「城东驿站 / 指定 3 人」
 */
const PAGE_SIZE = 20
/** 高亮只做一次性提示，与工单列表同口径（1.6s 后撤销，避免残留 2px 描边） */
const HIGHLIGHT_DURATION = 1600
/** 返回态恢复键：一次性，读完即删（避免下次正常进入消息页被误恢复） */
const RETURN_KEY = 'demo:notice-return'

const router = useRouter()
const notify = useNotifyStore()

const activeTab = ref('')
const list = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const error = ref('')
const initialized = ref(false)
/** 返回列表后高亮刚读的那一条（复用既有 is-highlight，零新增样式） */
const highlightId = ref(0)

let busy = false
/** 导航期间忽略重复点击：连点会把同一个目标页两次压进路由栈 */
let navigating = false
/** 返回态只恢复一次：读键即删，恢复动作也只做一次 */
let restored = false
let highlightTimer = null
/** 行元素引用：返回态按 id 定位滚动并夺焦点（不往 DOM 塞自定义属性） */
const rowEls = new Map()

const unreadCount = computed(() => list.value.filter((item) => !item.isRead).length)
const emptyText = computed(() => (activeTab.value === 0 ? '没有未读通知' : '暂无通知'))

/** 发布范围：契约只下发档位，未下发具体目标（见文件头 TODO） */
const scopeLabel = (scope) => (scope ? dictLabel(PUBLISH_SCOPE, scope) : '')

function setRowRef(id, el) {
  if (el) rowEls.set(id, el)
  else rowEls.delete(id)
}

async function fetchPage() {
  if (busy) return
  busy = true
  const firstPage = pageNum.value === 1
  try {
    const page = await notify.fetchList({ isRead: activeTab.value, pageNum: pageNum.value, pageSize: PAGE_SIZE })
    error.value = ''
    list.value = firstPage ? page.list : list.value.concat(page.list)
    finished.value = list.value.length >= page.total
    pageNum.value += 1
    await notify.refresh()
    // 首屏第一页成功后才恢复返回态：错误态没有可定位的行
    if (firstPage && !restored) {
      restored = true
      restoreReturnState()
    }
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

/** 点击通知：一律进本端阅读页（D3.3） */
function onOpen(item) {
  if (navigating) return
  navigating = true
  try {
    // 记住返回上下文：Tab / 滚动位置 / 目标 id，供本组件重建后一次性恢复（D7.3）
    sessionStorage.setItem(RETURN_KEY, JSON.stringify({ tab: activeTab.value, scrollTop: window.scrollY, id: item.id }))
  } catch (e) {
    // 隐私模式下不可写：只是失去恢复能力，不阻断导航
  }
  // 员工端阅读页路由（端固定化：本端不再按角色分流到管理端消息页）
  router.push({ name: 'staffNoticeReader', query: { id: String(item.id) } })
}

/**
 * 返回态恢复（D7.3）：移动端没有 keep-alive 且 scrollBehavior 固定归顶，
 * 返回列表 = 组件重建 = Tab 复位、滚动归顶；不恢复的话用户被弹回顶部还得重新滑动找那条。
 */
function restoreReturnState() {
  let saved = null
  try {
    const raw = sessionStorage.getItem(RETURN_KEY)
    if (raw) {
      sessionStorage.removeItem(RETURN_KEY)
      saved = JSON.parse(raw)
    }
  } catch (e) {
    saved = null
  }
  if (!saved || !saved.id) return
  nextTick(() => {
    // 原 Tab 为「未读」时该条已因已读而移出未读列表：落回「全部」并高亮，避免用户以为通知丢了
    const target = saved.tab === 0 ? '' : saved.tab
    if (activeTab.value !== target) activeTab.value = target
    highlightId.value = saved.id
    const el = rowEls.get(saved.id)
    if (el) {
      el.scrollIntoView({ block: 'center' })
      // 焦点回到「用户离开时的位置」；preventScroll 避免与上面的滚动互相打架
      if (el.focus) el.focus({ preventScroll: true })
    } else {
      window.scrollTo(0, 0)
    }
  })
  highlightTimer = setTimeout(() => {
    highlightId.value = 0
  }, HIGHLIGHT_DURATION)
}

watch(activeTab, () => {
  reset()
  onLoad()
})

onMounted(onLoad)
onUnmounted(() => clearTimeout(highlightTimer))
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
            :ref="(el) => setRowRef(item.id, el)"
            class="list-item notice-item"
            :class="{ 'list-item--marked': !item.isRead, 'is-highlight': item.id === highlightId }"
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
                <StatusTag v-if="item.isPublished" :dict="NOTICE_ANNOUNCEMENT" value="PUBLISHED" variant="outline" />
                <StatusTag :dict="NOTIFICATION_TYPE" :value="item.type" variant="outline" />
              </span>
            </div>
            <div class="list-item__meta list-item__meta--clamp2">{{ item.content }}</div>
            <div class="flex-between">
              <span class="list-item__meta">
                {{ relativeTime(item.createTime) }}
                <template v-if="item.isPublished">
                  · 由 {{ item.publisherName || '管理员' }} 发布 · 范围：{{ scopeLabel(item.publishScope) }}
                </template>
              </span>
              <span v-if="!item.isRead" class="list-item__meta">未读</span>
            </div>
            <!-- 可打开的视觉提示：行尾 chevron（非文本 3.24:1 ≥ 3:1），语义提示走视觉隐藏文本 -->
            <van-icon class="notice-item__chevron" name="arrow" aria-hidden="true" />
            <span class="visually-hidden">，可打开阅读全文</span>
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

/* 行尾「可打开」chevron：绝对定位垂直居中，不动标题行两枚标签与右侧未读标记的布局（D10.1） */
.notice-item {
  position: relative;
  padding-right: var(--sp-6);
}

.notice-item__chevron {
  position: absolute;
  top: 50%;
  right: var(--sp-3);
  font-size: var(--fs-h2);
  color: var(--color-primary-icon);
  transform: translateY(-50%);
}

/* 正文两行截断：只作用于本行正文那一行，不得改共享类 .list-item__meta（全工程 77 处复用，会波及所有列表页） */
.list-item__meta--clamp2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
</style>
