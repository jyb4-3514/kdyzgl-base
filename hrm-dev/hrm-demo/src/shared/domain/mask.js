// TODO(扩展): 后端脱敏生效后删除前端兜底
/**
 * 敏感信息脱敏（api.md 1.4 口径）
 * 为什么前端还留一份：演示态数据源在浏览器内，接口返回的是明文，
 * 展示前必须自行打码；后端接管脱敏后本文件整体可删。
 */

/** 手机号脱敏：11 位取前 3 后 4，其余取前 3 位 + **** */
export function maskPhone(phone) {
  if (!phone) return phone
  const value = String(phone)
  if (/^\d{11}$/.test(value)) return `${value.slice(0, 3)}****${value.slice(7)}`
  return `${value.slice(0, 3)}****`
}

/** 姓名脱敏（demo-design.md 7.4.2）：只显示姓，其余用 * 占位 */
export function maskName(name) {
  if (!name) return name
  const value = String(name)
  return value.length <= 1 ? value : `${value.slice(0, 1)}${'*'.repeat(value.length - 1)}`
}

/** 银行卡号脱敏：保留末 4 位，其余按 4 位一组用 * 占位 */
export function maskBankAccount(value) {
  if (!value) return value
  const digits = String(value).replace(/\s/g, '')
  if (digits.length <= 4) return '*'.repeat(digits.length)
  const head = '*'
    .repeat(digits.length - 4)
    .match(/.{1,4}/g)
    .join(' ')
  return `${head} ${digits.slice(-4)}`
}
