/**
 * 移动端登录态持久化
 *
 * 为什么独立 storage key（demo-design.md 6.2）：PC 端用 @admin/stores/auth 的 key，
 * 移动端另起一套，同一浏览器可同时保持两套登录态 —— 演示 S13「三端数据一致性」时需要双标签并存。
 * 为什么单独成模块：http.js 要在请求拦截器里读 token，若直接依赖 stores/auth.js 会形成循环引用。
 * 键名真源在 shared/constants/storageKey.js（demo 层需在同一份契约上清理登录态，不得反向依赖本模块）。
 */
import { MOBILE_TOKEN_KEY as TOKEN_KEY, MOBILE_USER_KEY as USER_KEY } from '@/shared/constants/storageKey.js'

export { TOKEN_KEY, USER_KEY }

const hasStorage = () => typeof localStorage !== 'undefined'

export function readToken() {
  return hasStorage() ? localStorage.getItem(TOKEN_KEY) || '' : ''
}

export function readUser() {
  if (!hasStorage()) return null
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    // 存储被外部改写时按未登录处理，避免脏数据导致路由守卫崩溃
    return null
  }
}

export function writeAuth(token, user) {
  if (!hasStorage()) return
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  if (!hasStorage()) return
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
