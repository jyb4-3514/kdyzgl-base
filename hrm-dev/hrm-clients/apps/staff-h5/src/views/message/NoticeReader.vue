<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NoticeReader from '@kdyzgl/shared/ui/NoticeReader.vue'
import { DEMO_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { ROLE, ROLE_LABEL } from '@kdyzgl/shared/constants/role.js'
import { canAccess } from '@kdyzgl/shared/domain/permission.js'
import { useAuthStore } from '../../stores/auth.js'
import { useNotifyStore } from '../../stores/notify.js'

/**
 * 通知阅读页容器（D 章）· 员工端「驿站助手」
 *
 * B-3（ADR §3.5 第 14 项）：页面本体已提升为 @kdyzgl/shared/ui/NoticeReader.vue 中立页，
 * 本文件只做「取数 + 已在读标记 + 动作解析 → props 注入 / 事件回流」；中立页不 import 任何 store。
 * 动作解析依赖角色与路由（router.resolve），属端/角色语义，故留在容器侧；端固定化后无 ADMIN 分支。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notify = useNotifyStore()

const STAFF_ROLES = [ROLE.STATION_ADMIN, ROLE.STAFF]
const STATION_ADMIN_ONLY = [ROLE.STATION_ADMIN]

const loading = ref(true)
const error = ref('')
const detail = ref(null)
/** 9001（不存在 / 非本人）：映射到 PageState 空态，文案不区分两种成因 */
const isMissing = ref(false)
/** 标记已读失败：给顶部常驻提示条，不阻断阅读 */
const markFailed = ref(false)

const id = computed(() => String(route.query.id || ''))
/** 本端消息页：深链返回的兜底落点 */
const listPath = '/staff/message?tab=notice'

/* ==================== 动作表（D6.2 `bizType × 角色`，本端仅员工域目标） ==================== */

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
      return bizId ? act('work_order', '去处理工单', `/staff/workorder/${bizId}`, STAFF_ROLES) : null
    case 'parcel':
      return bizId ? act('parcel', '查看包裹', `/staff/parcel/${bizId}`, STAFF_ROLES) : null
    case 'payroll':
      return bizId ? act('payroll', '查看工资单', `/staff/payroll/${bizId}`, STAFF_ROLES) : null
    case 'sync_task':
      if (role === ROLE.STATION_ADMIN) return act('sync_task', '查看同步状态', '/staff/sync', STATION_ADMIN_ONLY)
      // STAFF：保留可见但禁用 + 说明原因，告知能力不退化（原为点击后弹 Toast）
      return {
        key: 'sync_task',
        label: '查看同步状态',
        disabled: true,
        note: '同步状态页仅站长可见，请用站长身份查看'
      }
    case 'flow':
      return act('flow', '查看入离职流程', '/staff/flow', STAFF_ROLES)
    case 'leave':
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
  if (!fetched) return
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
  <NoticeReader
    :detail="detail"
    :loading="loading"
    :error="error"
    :is-missing="isMissing"
    :mark-failed="markFailed"
    :list-path="listPath"
    :actions="actions"
    :action-note="actionNote"
    @retry="load"
    @select="onAction"
  />
</template>
