<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import ActionBar from './ActionBar.vue'
import PageNav from './PageNav.vue'
import PageState from './PageState.vue'
import StatusTag from './StatusTag.vue'
import { NOTICE_ANNOUNCEMENT, NOTIFICATION_TYPE, PUBLISH_SCOPE, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import { formatDateTime } from './format.js'

/**
 * 通知阅读页（D 章）中立页 —— 员工端 /staff/message/notice 与管理端 /boss/message/notice 共用
 * （ADR §3.5 第 14 项，B-3 裁定取 ①：提升为 packages/shared/ui 中立页）
 *
 * 中立约束：数据与动作一律 props / emits 注入，**禁 import stores/ api/ mock**，
 * 也不 import Element Plus / Vant 运行时（模板里的 <van-*> 由宿主 App 全局注册）。
 * 取数（notify.fetchDetail / markRead）与「去处理」目标解析（依赖角色与路由）由宿主容器承担。
 *
 * 状态（7 态）：加载 → PageState 骨架；不存在/非本人（9001）→ 空态且不给重试；
 * 网络/5xx → 错误态可重试；禁用 → 宿主把 action.disabled + note 传入，本页只渲染；
 * 无权限 → 由宿主路由守卫拦截；边界 → 超长标题/长正文/连续空行/空白字段（见 paragraphs）。
 */
const props = defineProps({
  /** 通知详情（宿主取数后注入；null = 尚无数据） */
  detail: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** 9001（不存在 / 非本人）：映射到 PageState 空态，文案不区分两种成因 */
  isMissing: { type: Boolean, default: false },
  /** 标记已读失败：顶部常驻提示条，不阻断阅读 */
  markFailed: { type: Boolean, default: false },
  /** 深链返回兜底落点（各端消息页） */
  listPath: { type: String, default: '' },
  /** 底部动作（[{ key, label, disabled }]），由宿主按角色解析后注入 */
  actions: { type: Array, default: () => [] },
  /** 动作不可用原因说明 */
  actionNote: { type: String, default: '' }
})

const emit = defineEmits(['retry', 'select'])

const titleEl = ref(null)

/** 发布范围：契约只下发档位，未下发具体目标（与列表同口径，本页不新增目标明细） */
const scopeLabel = (scope) => (scope ? dictLabel(PUBLISH_SCOPE, scope) : '')

/** 正文按 \n 切段；只丢掉纯空白的段（连续空行归一为单一段间距，避免拉出半屏空白），段内空白由 pre-wrap 保留 */
const paragraphs = computed(() => {
  const content = (props.detail && props.detail.content) || ''
  const parts = content.split('\n').filter((line) => line.trim() !== '')
  return parts.length ? parts : ['（无正文）']
})

/** 详情就绪后把焦点落到标题（tabindex="-1" 不进 Tab 序列），单页应用换页否则读屏不播报新内容 */
watch(
  () => props.detail,
  async (value) => {
    if (!value) return
    await nextTick()
    if (titleEl.value) titleEl.value.focus()
  }
)
</script>

<template>
  <div class="detail-page">
    <PageNav title="通知详情" :back-fallback="listPath" />
    <div class="page" :class="[actions.length ? 'page--bar' : 'page--loose', actionNote ? 'reader--note' : '']">
      <van-notice-bar
        v-if="markFailed"
        class="notice"
        left-icon="warning-o"
        wrapable
        text="未能标记为已读，返回列表后该条仍显示为未读"
        color="var(--color-warning)"
        background="var(--color-warning-surface)"
      />

      <PageState
        :loading="loading"
        :error="error"
        :empty="isMissing"
        empty-text="该通知不存在或已被删除"
        :rows="6"
        @retry="emit('retry')"
      >
        <div v-if="detail" class="card reader">
          <div class="reader__tags">
            <StatusTag v-if="detail.isPublished" :dict="NOTICE_ANNOUNCEMENT" value="PUBLISHED" variant="outline" />
            <StatusTag :dict="NOTIFICATION_TYPE" :value="detail.type" variant="outline" />
          </div>
          <!-- 标题全量展示、不截断；空标题回落到占位文案，不让焦点落到空元素（读屏无输出） -->
          <h1 ref="titleEl" class="reader__title" tabindex="-1">{{ detail.title || '（无标题）' }}</h1>
          <p class="reader__meta">
            {{ formatDateTime(detail.createTime) }}
            <template v-if="detail.isPublished">
              · 由 {{ detail.publisherName || '管理员' }} 发布 · 范围：{{ scopeLabel(detail.publishScope) }}
            </template>
          </p>
          <hr class="reader__divider" />
          <div class="reader__body">
            <p v-for="(paragraph, index) in paragraphs" :key="index" class="reader__para">{{ paragraph }}</p>
          </div>
        </div>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="actionNote" @select="emit('select', $event)" />
  </div>
</template>

<style scoped>
/* 正文与元信息一律落在 --surface-card 上：--text-3 落页面底色只有 4.50:1（无余量） */
.reader {
  margin-top: var(--sp-3);
}

.reader__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  align-items: center;
  margin-bottom: var(--sp-2);
}

/* 超长标题（上限 100 字符）完整换行展示，禁止 line-clamp/text-overflow。
 * overflow-wrap: anywhere 即可断长串且不出现横向滚动；word-break: break-word 已被 stylelint 判为废弃关键字，不用 */
.reader__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-h1);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h1);
  color: var(--text-1);
  overflow-wrap: anywhere;
}

.reader__meta {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.reader__divider {
  height: 0;
  margin: var(--sp-3) 0;
  border: 0;
  border-top: 1px solid var(--border-line);
}

/* 正文不做 max-height、不做展开收起：全量展示就是本次需求本身。
 * 段内 pre-wrap 保留多余空格与缩进；overflow-wrap: anywhere 断长串（见 .reader__title 的同一说明） */
.reader__para {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-1);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.reader__para + .reader__para {
  margin-top: var(--sp-4);
}

/* 动作区带原因说明时栏体高出 --actionbar-h，补一段底部留白避免遮住正文末尾 */
.page--bar.reader--note {
  padding-bottom: calc(var(--page-pad-bottom) + var(--sp-6));
}
</style>
