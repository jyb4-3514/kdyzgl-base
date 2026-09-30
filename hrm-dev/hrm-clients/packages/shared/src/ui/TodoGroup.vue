<script setup>
import { computed } from 'vue'
import StatusTag from './StatusTag.vue'

/**
 * 待办分组（D2-5，Molecule）
 *
 * 结构：分组标题（分组名 + N 条 + 查看全部 ›，计数与动作分色）→ 明细行（主文案 + 元信息 + 状态标签 + 可选 note）× ≤3。
 * 为什么每组只渲染 3 行 + 一个「查看全部」：消息页是「快照」不是第二个业务列表，
 * 全量明细属于各自业务页的职责（A4-2）。
 *
 * 状态（7 态）：默认 ≤3 行；加载为 3 条等高骨架；空由父级过滤（空组不渲染，不显示「0 条」）；
 * 错误只落在本组内并可重试，不影响其他组；禁用不存在；无权限由父级按角色过滤分组；
 * 边界 = total > 99 前端格式化、`查看全部` 的 aria-label 带分组名。
 */
const props = defineProps({
  title: { type: String, required: true },
  /** 点击分组标题与明细行的跳转目标（同一业务页） */
  to: { type: [String, Object], required: true },
  /** 待办条数：null 表示该组取数失败（显示 `···`，绝不用 0 冒充） */
  total: { type: Number, default: null },
  /** [{ key, title, meta, note?, tag?: { dict, value } }]；note 为可选第三行（1 行省略，未传不渲染） */
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['retry'])

const totalText = computed(() => (props.total == null ? '···' : `${props.total} 条`))
/** 「查看全部」的可访问名要带分组名与条数，否则读屏只听到一串「查看全部」分不清是哪一组 */
const moreLabel = computed(() => `查看全部${props.total == null ? '' : ` ${props.total} 条`}${props.title}`)
</script>

<template>
  <section class="todo-group">
    <header class="todo-group__head">
      <h3 class="todo-group__title">{{ title }}</h3>
      <router-link class="todo-group__more" :to="to" :aria-label="moreLabel">
        <span class="todo-group__count tabular-nums">{{ totalText }}</span>
        <span class="todo-group__more-action" aria-hidden="true">· 查看全部 ›</span>
      </router-link>
    </header>

    <div v-if="loading" class="todo-group__rows">
      <div v-for="i in 3" :key="i" class="skeleton-block todo-group__skeleton" />
    </div>

    <div v-else-if="error" class="todo-group__error" role="alert">
      <span>{{ error }}</span>
      <button type="button" class="todo-group__retry" @click="emit('retry')">点击重试</button>
    </div>

    <ul v-else class="todo-group__rows">
      <li v-for="row in rows" :key="row.key" class="todo-group__row">
        <router-link class="todo-group__link" :to="to">
          <span class="todo-group__row-head">
            <span class="todo-group__row-title">{{ row.title }}</span>
            <!-- 行徽标插槽：调用方自绘（如审批中心「注册」来源胶囊），不传则零渲染，既有调用方不受影响 -->
            <slot name="row-badge" :row="row" />
            <StatusTag v-if="row.tag" :dict="row.tag.dict" :value="row.tag.value" />
          </span>
          <span v-if="row.meta" class="list-item__meta">{{ row.meta }}</span>
          <!-- 可选第三行：不改既有调用方（未传 note 时为 undefined，不渲染） -->
          <span v-if="row.note" class="todo-group__note">{{ row.note }}</span>
        </router-link>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.todo-group {
  margin-top: var(--sp-3);
  overflow: hidden;
  background: var(--surface-card);
  border-radius: var(--r-lg);
  box-shadow: var(--e1);
}

.todo-group__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--border-line);
}

.todo-group__title {
  margin: 0;
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h3);
}

/* 组头计数与动作分色（设计 ⑧.3 / O7）：计数是数值事实用 --text-2，动作才是可点主色，
 * 避免两者同色时把「N 条」误读成链接；整条仍保留 44 高触控。 */
.todo-group__more {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  min-height: 44px;
  font-size: var(--fs-caption);
  color: var(--text-2);
  text-decoration: none;
}

.todo-group__more-action {
  color: var(--color-primary);
}

.todo-group__rows {
  padding: 0;
  margin: 0;
  list-style: none;
}

.todo-group__row + .todo-group__row {
  border-top: 1px solid var(--border-line);
}

.todo-group__link {
  display: block;
  min-height: 48px;
  padding: var(--sp-2) var(--sp-4);
  color: inherit;
  text-decoration: none;
}

.todo-group__row-head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.todo-group__row-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 可选 note 行：高度随内容（1 行），1 行省略；零新增 Token（字号 / 色 / 上边距均取自既有） */
.todo-group__note {
  display: block;
  overflow: hidden;
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-group__skeleton {
  height: 48px;
  margin: var(--sp-2) var(--sp-4);
  border-radius: var(--r-sm);
}

.todo-group__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.todo-group__retry {
  min-height: 44px;
  padding: 0;
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}
</style>
