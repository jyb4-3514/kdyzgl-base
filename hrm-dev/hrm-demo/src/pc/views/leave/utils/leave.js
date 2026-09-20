import { HALF_DAY, LEAVE_TYPE, dictLabel } from '@/shared/constants/dict'

/**
 * 请假的展示口径（列表 / 审批弹窗 / 详情抽屉三处共用）
 * 为什么抽出来：同一句话在三个地方各写一遍，改一次口径要改三处，必然漏。
 */

/** 驳回副信息文案：设计规范 §1.2 要求列表副信息讲「谁没通过」，
 *  与字典 LEAVE_REJECT_STAGE 的「初审驳回 / 终审驳回」（筛选统计口径）刻意分开表述 */
const REJECT_STAGE_TEXT = { STATION: '站长未通过', BOSS: '老板未通过' }

/** 区间展示：同日压缩为「2026-10-01 上午 ~ 下午」，与移动端、通知文案同一口径（设计规范 §4.2） */
export function rangeText(leave) {
  if (!leave) return '—'
  const head = `${leave.startDate} ${dictLabel(HALF_DAY, leave.startPeriod, '')}`
  const tail =
    leave.endDate === leave.startDate
      ? dictLabel(HALF_DAY, leave.endPeriod, '')
      : `${leave.endDate} ${dictLabel(HALF_DAY, leave.endPeriod, '')}`
  return `${head} ~ ${tail}`
}

/** 驳回副信息：两级驳回共用一个状态，不补这行就分不清是站长否的还是老板否的 */
export const rejectStageText = (leave) =>
  leave && leave.status === 'REJECTED' ? REJECT_STAGE_TEXT[leave.rejectStage] || '审批未通过' : ''

export const typeText = (leave) => dictLabel(LEAVE_TYPE, leave && leave.leaveType)

/** 天数展示：两个口径必须同时出（设计规范 §4.3.3），少一个员工就会质疑扣款结果 */
export const daysText = (leave) => (leave ? `${leave.naturalDays} 天（自然日） · ${leave.countedDays} 天（计薪）` : '—')
