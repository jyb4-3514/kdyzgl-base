<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import NoticeList from '../../components/NoticeList.vue'
import TodoList from '../../components/TodoList.vue'
import { useAuthStore } from '../../stores/auth.js'
import { useNotifyStore } from '../../stores/notify.js'
import { useTodoStore } from '../../stores/todo.js'
import { useReselect } from '../../composables/useReselect.js'
import { badgeText } from '../../utils/format.js'

/**
 * 消息 Tab（D2-8）· 老板端与员工端共用，按角色渲染
 *
 * 两类内容的关系（A4-2）：通知是「事件流」（有已读概念），待办是「状态快照」（只有已处理，没有已读）。
 * 二者不合并、不去重 —— 同一次工单指派既产生一条通知，也产生一条待办，但它们是两个事实。
 *
 * 「发布」按钮不在本页：消息是 Tab 根页，页头由 TabbarLayout 统一渲染，发布动作放在那儿（避免两个 NavBar）。
 * 无权限差异：待办分组由 stores/todo.js 按角色给定；待办子视图不提供「标记已办」（会产生伪状态，A4-2）。
 */
const route = useRoute()
const auth = useAuthStore()
const notify = useNotifyStore()
const todo = useTodoStore()

/** 默认落在「通知」；旧路由 /staff/notification 重定向时带 ?tab=notice */
const activeTab = ref(route.query.tab === 'todo' ? 'todo' : 'notice')

const noticeBadge = computed(() => badgeText(notify.unread))
const todoBadge = computed(() => badgeText(todo.total))
/** 空态文案两端不同：员工端给一句人话的「今天只剩你自己了」，老板端保持中性 */
const emptyText = computed(() => (auth.isAdmin ? '暂无待办事项' : '暂无待办，今天只剩你自己了'))

/** 待办是快照、角标是聚合值，进入消息页一律刷新一次，避免用户对着过期条数做判断（C5-6） */
async function refreshAll() {
  await Promise.all([notify.refresh(), todo.refresh()])
}

// 重复点击「消息」Tab：回到「通知」子视图；已在通知子视图时刷新未读数（C5-2）
useReselect(() => {
  if (activeTab.value !== 'notice') activeTab.value = 'notice'
  else notify.refresh()
})

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <van-tabs v-model:active="activeTab" class="bleed">
      <van-tab name="notice">
        <template #title>
          通知<span v-if="noticeBadge" class="tab-count">{{ noticeBadge }}</span>
        </template>
      </van-tab>
      <van-tab name="todo">
        <template #title>
          待办<span v-if="todoBadge" class="tab-count">{{ todoBadge }}</span>
        </template>
      </van-tab>
    </van-tabs>

    <NoticeList v-if="activeTab === 'notice'" />
    <TodoList v-else :groups="todo.groups" :loading="todo.loading" :empty-text="emptyText" @retry="refreshAll" />
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
</style>
