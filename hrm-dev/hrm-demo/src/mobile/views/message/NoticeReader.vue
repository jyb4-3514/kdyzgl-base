<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ActionBar from '../../components/ActionBar.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { NOTICE_ANNOUNCEMENT, NOTIFICATION_TYPE, PUBLISH_SCOPE, dictLabel } from '@/shared/constants/dict.js'
import { DEMO_CODE } from '@/shared/constants/errorCode.js'
import { ALL_ROLES, ROLE, ROLE_LABEL } from '@/shared/constants/role.js'
import { canAccess } from '@/shared/domain/permission.js'
import { useAuthStore } from '../../stores/auth.js'
import { useNotifyStore } from '../../stores/notify.js'
import { formatDateTime } from '../../utils/format.js'

/**
 * 通知阅读页（D 章）：两端共用页，员工端 /staff/message/notice 与管理端 /boss/message/notice 指向本组件。
 *
 * 为什么列表点击一律先进本页（而不是直接跳业务页）：通知正文只在列表里一行、还被截断，
 * 直接跳业务页会永久隐藏正文，「所有消息可打开、可读全」这条需求就只修了三分之一；
 * 业务动作降为底部「去处理」（二次点击），由 ActionBar 固定底栏承载，不需要先滑到正文末尾。
 *
 * 状态（7 态）：加载 → PageState 骨架；通知不存在/非本人（9001）→ 空态且不给重试（数据不会自己回来）；
 * 网络/5xx → 错误态可重试；禁用 → STAFF 的 sync_task 动作保留可见但置灰 + note 说明原因；
 * 无权限 → 由路由守卫拦截（meta.roles），数据级非本人统一回 9001，不泄露他人通知的存在性；
 * 边界 → 超长标题/长正文/连续空行/纯英文长串/正文内 URL（按纯文本）/空字段（见模板与下方 normalize）。
 *
 * TODO(扩展): 通知正文 URL 的识别与可点击（前置：安全评估结论 + 域名白名单）
 * TODO(扩展): 深链返回时按 id 定位到具体条（需列表支持游标定位）
 */

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notify = useNotifyStore()

const STAFF_ROLES = [ROLE.STATION_ADMIN, ROLE.STAFF]
const ADMIN_ONLY = [ROLE.ADMIN]
const STATION_ADMIN_ONLY = [ROLE.STATION_ADMIN]

const loading = ref(true)
const error = ref('')
const detail = ref(null)
/** 9001（不存在 / 非本人）：映射到 PageState 空态，文案不区分两种成因 */
const isMissing = ref(false)
/** 标记已读失败：给顶部常驻提示条，不阻断阅读 */
const markFailed = ref(false)

const titleEl = ref(null)

const id = computed(() => String(route.query.id || ''))
/** 本端消息页：既是深链返回的兜底落点，也是「去处理」目标页域的判断依据 */
const isBoss = computed(() => route.path.startsWith('/boss'))
const listPath = computed(() => (isBoss.value ? '/boss/message?tab=notice' : '/staff/message?tab=notice'))

/** 发布范围：契约只下发档位，未下发具体目标（与列表同口径，本页不新增目标明细） */
const scopeLabel = (scope) => (scope ? dictLabel(PUBLISH_SCOPE, scope) : '')

/** 正文按 \n 切段；只丢掉纯空白的段（连续空行归一为单一段间距，避免拉出半屏空白），段内空白由 pre-wrap 保留 */
const paragraphs = computed(() => {
  const content = (detail.value && detail.value.content) || ''
  const parts = content.split('\n').filter((line) => line.trim() !== '')
  return parts.length ? parts : ['（无正文）']
})

/* ==================== 动作表（D6.2 `bizType × 角色`） ==================== */

const act = (key, label, target, roles) => ({ key, label, target, roles })

/**
 * 单个动作或 null（null = 不命中任何 bizType，动作区整块隐藏，不留空位、不给伪动作）。
 * roles 为该目标页的角色白名单，仅用于 push 前的兜底降级（D6.3）。
 */
function resolveAction(item, role) {
  if (!item || !item.bizType) return null
  const { bizId } = item
  switch (item.bizType) {
    case 'work_order':
      return bizId ? act('work_order', '去处理工单', `/staff/workorder/${bizId}`, ALL_ROLES) : null
    case 'parcel':
      return bizId ? act('parcel', '查看包裹', `/staff/parcel/${bizId}`, ALL_ROLES) : null
    case 'payroll':
      if (!bizId) return null
      // 管理端必须落 /boss/payroll/:id：/staff/payroll/:id 的 roles 是 STAFF_ROLES，ADMIN 会被守卫拦下
      return role === ROLE.ADMIN
        ? act('payroll', '查看工资单', `/boss/payroll/${bizId}`, ADMIN_ONLY)
        : act('payroll', '查看工资单', `/staff/payroll/${bizId}`, STAFF_ROLES)
    case 'sync_task':
      if (role === ROLE.ADMIN) return act('sync_task', '查看异常预警', '/boss/alerts', ADMIN_ONLY)
      if (role === ROLE.STATION_ADMIN) return act('sync_task', '查看同步状态', '/staff/sync', STATION_ADMIN_ONLY)
      // STAFF：保留可见但禁用 + 说明原因，告知能力不退化（原为点击后弹 Toast）
      return {
        key: 'sync_task',
        label: '查看同步状态',
        disabled: true,
        note: '同步状态页仅站长可见，请用站长身份查看'
      }
    case 'flow':
      return role === ROLE.ADMIN
        ? act('flow', '查看入离职流程', '/boss/flow', ADMIN_ONLY)
        : act('flow', '查看入离职流程', '/staff/flow', STAFF_ROLES)
    case 'leave':
      if (role === ROLE.ADMIN) return act('leave', '查看请假单', '/boss/leave', ADMIN_ONLY)
      if (role === ROLE.STATION_ADMIN) return act('leave', '查看请假单', '/staff/leave/review', STATION_ADMIN_ONLY)
      return act('leave', '查看请假单', '/staff/leave', STAFF_ROLES)
    default:
      return null
  }
}

const action = computed(() => {
  const base = resolveAction(detail.value, auth.role)
  if (!base || base.disabled) return base
  if (canAccess(base.roles, auth.user)) return base
  // 兜底降级：目标页白名单与本身份不符时原地置灰 + 说明原因，不再走「点了被守卫弹走」
  const name = router.resolve(base.target).meta.title || '目标页面'
  const allow = base.roles.map((r) => ROLE_LABEL[r] || r).join('/')
  return { key: base.key, label: base.label, disabled: true, note: `${name}仅${allow}可见` }
})

const actions = computed(() => {
  const current = action.value
  if (!current) return []
  return [{ key: current.key, label: current.label, disabled: !!current.disabled }]
})
const actionNote = computed(() => (action.value && action.value.note) || '')

function onAction() {
  const current = action.value
  if (!current || current.disabled || !current.target) return
  router.push(current.target)
}

/* ==================== 取数与已读标记（D3.4 时序） ==================== */

async function load() {
  loading.value = true
  error.value = ''
  isMissing.value = false
  markFailed.value = false
  let fetched = null
  try {
    // 详情取回成功才可能标记已读：取不到内容就标已读 = 把「没读到」记成「已读」
    fetched = await notify.fetchDetail(id.value)
    detail.value = fetched
  } catch (e) {
    detail.value = null
    if (e.code === DEMO_CODE.NOTIFICATION_NOT_EXISTS) isMissing.value = true
    else error.value = e.message || '通知加载失败'
  } finally {
    loading.value = false
  }
  await nextTick()
  if (!fetched) return
  // 单页应用换页后焦点默认留在 body，读屏不会播报新内容：就绪后把焦点落到标题（tabindex="-1" 不进 Tab 序列）
  if (titleEl.value) titleEl.value.focus()
  if (!fetched.isRead) markReadOnce()
}

/** 标记已读失败不阻断阅读：非 401 给顶部提示条，401 交 http 层的全局下线广播（不再弹第二条） */
async function markReadOnce() {
  try {
    await notify.markRead(detail.value.id)
  } catch (e) {
    if (e.code !== 401) markFailed.value = true
  }
}

onMounted(load)
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
        @retry="load"
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

    <ActionBar :actions="actions" :note="actionNote" @select="onAction" />
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
