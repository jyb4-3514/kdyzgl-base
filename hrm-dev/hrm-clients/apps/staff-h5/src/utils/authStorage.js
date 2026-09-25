import { createAuthStorage } from '@kdyzgl/api-client'

/**
 * 员工端登录态持久化 · 驿站助手
 *
 * 键名走共享工厂 createAuthStorage（ADR §3.5 第 2 项），本端固定 `hrm:staff:*` 命名空间。
 * 为什么必须独立命名空间：hrm-demo 的移动端入口用 `hrm_demo_mobile_token/user`，
 * 若本端复用同键，同一浏览器下「staff 端 vs hrm-demo 入口」会互相覆盖登录态，直接违背后端
 * 多端会话「并存不互踢」的验收（ADR §3.7 B3 ⑤ / D9）。独立键 = 两端可同浏览器并存。
 *
 * 为什么单独成模块：http.js 要在请求拦截器里读 token，若直接依赖 stores/auth.js 会形成循环引用。
 * TODO(扩展): 键名口径最终统一为 `hrm:{client}:{token|user}`（当前即此规范）；如需兼容旧演示键读取，
 *   须由运维据现网已登录用户量评估后再引入（避免「清旧键即登出他端」的副作用）。
 */
const storage = createAuthStorage({ tokenKey: 'hrm:staff:token', userKey: 'hrm:staff:user' })

export const TOKEN_KEY = storage.tokenKey
export const USER_KEY = storage.userKey

export const readToken = storage.readToken
export const readUser = storage.readUser
export const writeAuth = storage.writeAuth
export const clearAuth = storage.clearAuth
