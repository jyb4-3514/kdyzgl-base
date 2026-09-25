<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import { STAFF_TABS } from '../constants/tabs.js'
import { useNotifyStore } from '../stores/notify.js'
import { useTodoStore } from '../stores/todo.js'
import { notifyReselect } from '../composables/useReselect.js'
import { badgeText } from '../utils/format.js'

/**
 * Tab 页外壳：NavBar + 内容区 + 底部 Tabbar（3 项）
 *
 * 安全区两条要点（7.1）：
 * 1. 顶部走 --safe-top（壳经 bridge.js 注入，浏览器为 0），#app 与固定 NavBar 同步偏移，无壳不塌陷
 * 2. 底部 Tabbar 自带 safe-area-inset-bottom；内容区底部留白用 --page-pad-bottom-tab 一次给足，
 *    所以不再用 Tabbar 的 placeholder —— 两者叠加会多出一屏空白
 *
 * 角标口径（C4）：仅「消息」项有角标，值 = 未读通知数 + 待我处理待办总数；
 * 两类都由各自 store 静默取数，任一失败按 0 计，绝不弹 Toast（角标是辅助信息）。
 */
const route = useRoute()
const router = useRouter()
const notify = useNotifyStore()
const todo = useTodoStore()

const tabs = STAFF_TABS
const title = computed(() => route.meta.title || '')
const messageBadge = computed(() => badgeText(notify.unread + todo.total))

const isActive = (item) => route.path === item.path

function badgeOf(item) {
  return item.key === 'message' ? messageBadge.value : ''
}

function ariaLabelOf(item) {
  const badge = badgeOf(item)
  return badge ? `${item.text}，${badge} 条未处理` : item.text
}

/**
 * 重复点击当前 Tab（C5-2）：路由不变，无导航发生，需自行处理 —— 回到该 Tab 根视图并滚到顶部。
 * Vant 在点击已选中项时不会 emit `change`（TabbarItem.mjs 有 `if (!active.value)` 守卫），
 * 故监听 `click`：它无论是否选中都会触发，天然覆盖「重复点击」这一场景。
 * TODO(扩展): 产品若要求「各 Tab 各自记忆滚动位置」，在此引入 per-Tab scroll 缓存（C5-4 本期不做）
 */
function onRepeat() {
  window.scrollTo(0, 0)
  notifyReselect()
}

/** 非当前项的跳转由 Vant 的路由模式完成（`to` 存在时内部已 push），这里只补「点的是当前项」这一无导航分支 */
function onTabClick(item) {
  if (isActive(item)) onRepeat()
}

/** Enter 兜底：Vant 的 TabbarItem 渲染为 div[role=tab]，原生不会把回车转成 click（C5-8） */
function onTabKeydown(item, event) {
  event.preventDefault()
  if (isActive(item)) onRepeat()
  else router.push(item.path)
}
</script>

<template>
  <div class="tabbar-layout">
    <PageNav :title="title" :back="false" />
    <main class="tabbar-layout__body">
      <slot />
    </main>
    <!-- 选中/未选中色由 tokens 的 --van-tabbar-item-* 提供，不写内联色值（修 P38） -->
    <nav aria-label="主导航">
      <van-tabbar route fixed safe-area-inset-bottom>
        <van-tabbar-item
          v-for="item in tabs"
          :key="item.path"
          :to="item.path"
          :icon="item.icon"
          :badge="badgeOf(item)"
          :aria-label="ariaLabelOf(item)"
          :aria-current="isActive(item) ? 'page' : undefined"
          @click="onTabClick(item)"
          @keydown.enter="onTabKeydown(item, $event)"
        >
          {{ item.text }}
        </van-tabbar-item>
      </van-tabbar>
    </nav>
  </div>
</template>

<style scoped>
.tabbar-layout {
  min-height: 100%;
}

.tabbar-layout__body {
  /* 顶部留白由 NavBar placeholder 承担，此处只管底部固定栏的让位 */
  padding-bottom: var(--page-pad-bottom-tab);
}

.tabbar-layout :deep(.van-tabbar) {
  border-top: 1px solid var(--border-line);
}

.tabbar-layout :deep(.van-tabbar-item__text) {
  transition: font-weight var(--dur-fast) var(--ease-std);
}

/* 选中态双通道：主色（--color-primary）+ 字重，不以颜色为唯一区分（WCAG SC 1.4.1） */
.tabbar-layout :deep(.van-tabbar-item--active .van-tabbar-item__text) {
  font-weight: var(--fw-semibold);
}

/* 按下态：仅当前项短暂着色，给触屏一个即时反馈 */
.tabbar-layout :deep(.van-tabbar-item) {
  transition: background-color var(--dur-fast) var(--ease-std);
}

.tabbar-layout :deep(.van-tabbar-item:active) {
  background: var(--surface-subtle);
}

/* 未读角标：底取 --color-danger、白字 10px（C4），Vant 默认 12px 与 Tabbar 文字同级 */
.tabbar-layout :deep(.van-badge) {
  font-size: 10px;
}
</style>
