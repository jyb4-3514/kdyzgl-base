import { dictLabel, WORK_ORDER_PRIORITY, WORK_ORDER_STATUS, WORK_ORDER_TYPE } from '../constants/dict.js'

/**
 * 工单详情 → 多行纯文本（PC / 员工端 / 管理端共用这一份拼装逻辑）
 *
 * 为什么必须收口到 shared：三端四处入口（PC 列表与详情、移动端列表与详情）都要复制同一条工单，
 * 各写一份必然在字段顺序、标签文案上分叉（一期字典两份维护的教训），复制出来的内容对不上就没法用。
 *
 * 标签取「工单详情页界面上显示的文案」：如详情页写「SLA 截止」「关联运单」「工单描述」，
 * 复制文本就用同名标签，避免用户对着页面怀疑复制到了别的地方。
 *
 * 排除内部字段：id / stationId / parcelId / reporterId / assigneeId / handleLog / transfers /
 * overdueUnhandled / overSla / updateTime 等技术字段不进入复制文本。
 * 值为空的行省略（不出现「处理人：」这种空行）；工单号与状态始终保留，便于识别是哪一单。
 */

const HEADER = '【工单详情】'

/** 空值归一：null / undefined / 纯空白一律按空处理 */
function pick(value) {
  return value == null ? '' : String(value).trim()
}

/**
 * 字段清单即唯一真源：调整顺序、增删字段都只改这里
 * keep=true 的字段即使为空也保留整行（工单号 / 状态）
 */
const FIELDS = [
  { label: '工单号', keep: true, value: (order) => pick(order.orderNo) },
  { label: '类型', value: (order) => dictLabel(WORK_ORDER_TYPE, order.type, '') },
  { label: '优先级', value: (order) => dictLabel(WORK_ORDER_PRIORITY, order.priority, '') },
  { label: '状态', keep: true, value: (order) => dictLabel(WORK_ORDER_STATUS, order.status, '') },
  { label: '归属驿站', value: (order) => pick(order.stationName) },
  { label: '处理人', value: (order) => pick(order.assigneeName) },
  { label: '上报人', value: (order) => pick(order.reporterName) },
  { label: '创建时间', value: (order) => pick(order.createTime) },
  { label: 'SLA 截止', value: (order) => pick(order.slaDeadline) },
  { label: '关联运单', value: (order) => pick(order.waybillNo) },
  { label: '解决时间', value: (order) => pick(order.resolvedTime) },
  { label: '关闭时间', value: (order) => pick(order.closedTime) },
  { label: '工单描述', value: (order) => pick(order.content) }
]

/**
 * @param {object} order 工单列表 / 详情 VO（字段名以 Mock 契约返回为准）
 * @returns {string} 多行纯文本；order 为空时返回空串，由调用方按复制失败处理
 */
export function buildWorkOrderText(order) {
  if (!order) return ''
  const lines = [HEADER]
  FIELDS.forEach((field) => {
    const value = field.value(order)
    if (!value && !field.keep) return
    lines.push(`${field.label}：${value}`)
  })
  return lines.join('\n')
}
