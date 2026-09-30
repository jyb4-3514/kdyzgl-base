import { PAYROLL_RUN_STATUS, PAYROLL_RUN_TRIGGER, PAYROLL_SKIP_CODE } from '@kdyzgl/shared/constants/dict.js'

/**
 * 薪资结算自动化展示层纯函数（web）
 *
 * 为什么抽出来：运行记录面板与对账面板都要做「机器码 → 中文」「正负金额 → 符号 + 色族」的映射，
 * 就地各写一份会漂移；抽成纯函数后既能跨面板复用，也能脱离组件单测。
 */

/** 运行结果 / 触发方式 / 跳过原因：统一「查字典，查不到回落原值」，不出现空白 */
export function runStatusLabel(status) {
  return (PAYROLL_RUN_STATUS[status] || {}).label || status || '—'
}

export function runTriggerLabel(type) {
  return (PAYROLL_RUN_TRIGGER[type] || {}).label || type || '—'
}

/** 跳过原因：机器码转中文，人类可读原因与中文同义时不重复拼接（避免「X（X）」） */
export function runSkipText(row) {
  if (!row || !row.skipCode) return '—'
  const code = PAYROLL_SKIP_CODE[row.skipCode]
  const label = code ? code.label : row.skipCode
  return row.skipReason && row.skipReason !== label ? `${label}（${row.skipReason}）` : label
}

/** 起止时间：finishTime 为空代表仍在进行中，不得渲染成空白 */
export function runRangeText(row) {
  const start = (row && row.startTime) || '—'
  const finish = (row && row.finishTime) || '进行中'
  return `${start} ~ ${finish}`
}

/** 金额文本：可选带正负号（净影响用），始终保留两位小数 */
export function signedMoney(value, withSign = false) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0.00'
  const sign = withSign ? (num > 0 ? '+' : num < 0 ? '-' : '') : ''
  return `${sign}${Math.abs(num).toFixed(2)}`
}

/** 净影响正负色族：正 success / 负 danger / 零中性；必须与符号双通道，不得仅靠颜色（SC 1.4.1） */
export function netImpactClass(value) {
  const num = Number(value)
  if (num > 0) return 'is-plus'
  if (num < 0) return 'is-minus'
  return ''
}
