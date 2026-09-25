<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import MessagePage from '@kdyzgl/shared/ui/MessagePage.vue'
import NoticeList from '../../components/NoticeList.vue'
import { useNotifyStore } from '../../stores/notify.js'
import { useTodoStore } from '../../stores/todo.js'
import { useReselect } from '../../composables/useReselect.js'
import { badgeText } from '../../utils/format.js'

/**
 * 消息 Tab 容器（D2-8）· 员工端「驿站助手」
 *
 * B-3（ADR §3.5 第 14 项）：页面本体已提升为 @kdyzgl/shared/ui/MessagePage.vue 中立页，
 * 本文件只做「取数 → props 注入 / 事件回流」的薄容器；中立页不 import 任何 store。
 * 通知列表（含取数与写操作）由本容器经默认插槽注入（两端各持自己的 NoticeList，§3.2）。
 */
const route = useRoute()
const notify = useNotifyStore()
const todo = useTodoStore()

/** 默认落在「通知」；旧路由 /staff/notification 重定向时带 ?tab=notice */
const activeTab = ref(route.query.tab === 'todo' ? 'todo' : 'notice')

const noticeBadge = computed(() => badgeText(notify.unread))
const todoBadge = computed(() => badgeText(todo.total))
const emptyText = '暂无待办，今天只剩你自己了'

/** 待办是快照、角标是聚合值，进入消息页一律刷新一次，避免用户对着过期条数做判断（C5-6） */
async function refreshAll() {
  await Promise.all([notify.refresh(), todo.refresh()])
}

// 重复点击「消息」Tab：回到「通知」子视图；已在通知子视图时刷新未读数（C5-2）
useReselect(() => {
  if (activeTab.value !== 'notice') activeTab.value = 'notice'
  else notify.refresh()
})
</script>

<template>
  <MessagePage
    v-model:active-tab="activeTab"
    :notice-badge="noticeBadge"
    :todo-badge="todoBadge"
    :empty-text="emptyText"
    :todo-groups="todo.groups"
    :todo-loading="todo.loading"
    @refresh="refreshAll"
    @retry="refreshAll"
  >
    <NoticeList />
  </MessagePage>
</template>
