/**
 * 入参校验：正则与 api.md 4.3/4.4/4.5 的字段规则逐条对齐
 * 抽成公共模块的原因：员工新增/编辑、导入行校验共用同一套规则，避免同一正则写三遍
 */

export const isPhone = (value) => /^1[3-9]\d{9}$/.test(String(value || ''))
export const isUsername = (value) => /^[a-zA-Z][a-zA-Z0-9_]{3,29}$/.test(String(value || ''))
/** 密码强度：8-20 位，必须同时包含字母和数字 */
export const isStrongPassword = (value) => /^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/.test(String(value || ''))
export const isStationCode = (value) => /^[A-Za-z0-9_-]{2,50}$/.test(String(value || ''))
export const isDate = (value) => !value || /^\d{4}-\d{2}-\d{2}$/.test(String(value))
/** 考核月 / 工资单账期：yyyy-MM（KPI 与财务共用一套格式判定） */
export const isMonth = (value) => !value || /^\d{4}-\d{2}$/.test(String(value))

/** 文本长度校验：min/max 按字符数（中文按 1 个字符，与 VARCHAR(n) 的常见实现口径一致） */
export const textLen = (value, min, max) => {
  const len = String(value ?? '').trim().length
  return len >= min && len <= max
}

/** 空值判定：空串、null、undefined 视为「未填」 */
export const isBlank = (value) => value == null || String(value).trim() === ''

/** 分页参数越界提示（api.md 1.1：pageSize 最大 100） */
export function pageSizeInvalid(pageSize) {
  const size = Number.parseInt(pageSize, 10)
  return pageSize != null && pageSize !== '' && (Number.isNaN(size) || size < 1 || size > 100)
}
