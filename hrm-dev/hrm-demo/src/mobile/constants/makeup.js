/**
 * 补卡状态字典（移动端）
 *
 * 为什么落在移动端：shared/constants/dict.js 没有补卡字典，本轮不得改共享层；
 * 员工端「我的补卡申请」与管理端「补卡审批」看的是同一份状态，只在这里写一次，避免两处文案漂移。
 * 形态与 shared 字典一致（{ label, type }），StatusTag 未登记该字典时按 type 落到 soft 形态。
 */
export const MAKEUP_STATUS = {
  PENDING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

/** 筛选用的状态项（'' = 全部）：文案与 MAKEUP_STATUS 的标签保持一致，避免同一状态在筛选栏和行内两种说法 */
export const MAKEUP_FILTERS = [
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: '', label: '全部' }
]
