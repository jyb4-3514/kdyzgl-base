<script setup>
import { computed } from 'vue'
import PageState from './PageState.vue'
import TodoGroup from './TodoGroup.vue'

/**
 * 待办列表（D2-5，Organism）
 *
 * 承接 stores/todo.js 的分组快照：空组不渲染（不显示「0 条」分组），
 * 全部为空才走整页空态，且空态与错误态文案严格区分 —— 把「没有待办」显示成「加载失败」会让员工白跑一趟（D2-5）。
 */
const props = defineProps({
  /** [{ key, title, to, total, rows, error }]，total 为 null 表示该组取数失败 */
  groups: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无待办事项' }
})

const emit = defineEmits(['retry'])

/** 有内容或有错误的分组才渲染：total 为 0 的空组直接不出现 */
const visibleGroups = computed(() => props.groups.filter((item) => item.total || item.error))
</script>

<template>
  <div>
    <div v-if="loading && !groups.length" class="todo-list__loading">
      <div v-for="i in 3" :key="i" class="skeleton-block todo-list__skeleton" />
    </div>

    <template v-else>
      <TodoGroup
        v-for="group in visibleGroups"
        :key="group.key"
        :title="group.title"
        :to="group.to"
        :total="group.total"
        :rows="group.rows"
        :error="group.error"
        @retry="emit('retry')"
      />
      <PageState v-if="!visibleGroups.length" :empty="true" :empty-text="emptyText" />
    </template>
  </div>
</template>

<style scoped>
.todo-list__loading {
  margin-top: var(--sp-3);
}

.todo-list__skeleton {
  height: 48px;
  margin-bottom: var(--sp-2);
  border-radius: var(--r-sm);
}
</style>
