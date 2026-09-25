<script setup>
import { onMounted } from 'vue'
import TodoList from './TodoList.vue'

/**
 * 消息 Tab（D2-8）中立页 —— 管理端 / 员工端共用（ADR §3.5 第 14 项，B-3 裁定取 ①）
 *
 * 中立约束（ADR §3.5 硬边界）：本页数据一律 props 注入，**禁 import stores/ api/ mock**，
 * 也不 import Element Plus / Vant 运行时（模板里的 <van-*> 由宿主 App 全局注册）。
 * 通知列表因含取数与写操作，由宿主经默认插槽注入（两端各持自己的列表组件，见 §3.2「业务通用先各端自带」）。
 *
 * 为什么「发布」按钮不在这里：消息是 Tab 根页，页头由宿主的 TabbarLayout 渲染，两个 NavBar 会打架。
 */
const props = defineProps({
  /** 当前子视图：'notice' | 'todo'（宿主从 ?tab= 派生） */
  activeTab: { type: String, default: 'notice' },
  /** 通知未读角标文案（0/未知一律空串，口径由宿主 badgeText 承担） */
  noticeBadge: { type: String, default: '' },
  /** 待办角标文案 */
  todoBadge: { type: String, default: '' },
  /** 待办整页空态文案（两端语气不同，由宿主给） */
  emptyText: { type: String, default: '暂无待办事项' },
  /** 待办分组快照（宿主从 store 取） */
  todoGroups: { type: Array, default: () => [] },
  /** 待办加载态 */
  todoLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:activeTab', 'refresh', 'retry'])

/** 进入本页一律刷新一次角标与快照：避免用户对着过期条数做判断（C5-6） */
onMounted(() => emit('refresh'))
</script>

<template>
  <div class="page">
    <van-tabs :active="activeTab" class="bleed" @update:active="emit('update:activeTab', $event)">
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

    <slot v-if="activeTab === 'notice'" />
    <TodoList v-else :groups="todoGroups" :loading="todoLoading" :empty-text="emptyText" @retry="emit('retry')" />
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
