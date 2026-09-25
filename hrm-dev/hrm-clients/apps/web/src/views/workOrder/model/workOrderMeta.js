import { WORK_ORDER_SOURCE } from '@kdyzgl/shared/constants/dict'

/**
 * 工单域展示元数据（状态机 / 动作文案 / 时间线配色 / 单元格形态）
 *
 * 为什么收口：这些映射原本散在 index.vue 的列表模板、详情模板与抽屉底部三处分支里，
 * 拆成表格与抽屉两个组件后必须有唯一真源，否则改一处漏一处，列表与详情会各说各话。
 */

/** 与 Mock 层 TRANSITIONS 逐字一致；UI 只决定按钮可见性，非法跳转仍由服务端 8001 兜底 */
export const TRANSITIONS = { 0: [1, 3], 1: [2], 2: [1, 3], 3: [] }

/** 流转记录 action → 展示文案 */
export const LOG_ACTION = {
  create: '创建工单',
  assign: '指派',
  accept: '接单',
  resolve: '标记已解决',
  close: '关闭工单',
  reopen: '驳回重开',
  transfer: '转单',
  // 企微群消息自动派发（需求3）：时间线上必须能区分「人派」与「机器派」
  auto_dispatch: '企业微信自动派发'
}

/** 流转动作 → 时间线轴点色（工作流语义，与同步日志级别各自一套，不复用 el-timeline 的语义 type） */
export const LOG_DOT = {
  create: 'var(--text-disabled)',
  assign: 'var(--color-primary-icon)',
  accept: 'var(--color-primary-icon)',
  resolve: 'var(--color-success-icon)',
  close: 'var(--text-3)',
  reopen: 'var(--color-warning-icon)',
  // 转单用物流强调色：与「流转」动作族区分开，一眼可辨
  transfer: 'var(--color-accent)',
  auto_dispatch: 'var(--color-primary-icon)'
}

/**
 * 优先级形态（2.7）：高 = 实心（需要立刻行动）、中 = 浅底、低 = 描边
 * 字典只给了 el-tag 语义色，区分不出「高优先级要实心」，故在此显式映射
 */
export const PRIORITY_VARIANT = { 0: 'outline', 1: 'soft', 2: 'solid' }

/**
 * Tab 定义：name 既是筛选键也是状态值（数字字符串），overdueUnhandled 为派生口径
 * 需求6：内部键与文案统一为「超时未处理」（口径 = 已过 SLA 且仍为待处理/处理中）
 */
export const TABS = [
  { name: 'all', label: '全部' },
  { name: '0', label: '待处理' },
  { name: '1', label: '处理中' },
  { name: '2', label: '已解决' },
  { name: '3', label: '已关闭' },
  { name: 'overdueUnhandled', label: '超时未处理' }
]

/** 抽屉宽度统一到 --drawer-w，窄视口退化为 92vw（C-P9，修 P16） */
export const DRAWER_SIZE = 'min(var(--drawer-w), 92vw)'

/** 来源形态：旧数据可能没有 source 字段，按手工建单兜底 */
export function sourceVariantOf(source) {
  return (WORK_ORDER_SOURCE[source || 'MANUAL'] || {}).variant || 'outline'
}

/** 已关闭是终态，用描边降低视觉权重 */
export function statusVariantOf(status) {
  return status === 3 ? 'outline' : 'soft'
}

/** 已解决 / 已关闭视为 SLA 已终止，倒计时组件据此不再播报超时 */
export function slaFinished(status) {
  return status === 2 || status === 3
}

/** 超时未处理行底色 + 新派发工单短时高亮（后者供演示现场快速定位刚生成的工单） */
export function rowClassNameOf(row, highlightId) {
  const classes = []
  if (row.overdueUnhandled) classes.push('is-oversla')
  if (row.id === highlightId) classes.push('is-highlight')
  return classes.join(' ')
}

/** 流转动作文案：关闭是终态；从已解决回退到处理中叫「驳回重开」 */
export function actionLabelOf(from, to) {
  if (to === 3) return '关闭工单'
  if (to === 1) return from === 2 ? '驳回重开' : '接单处理'
  return '标记解决'
}
