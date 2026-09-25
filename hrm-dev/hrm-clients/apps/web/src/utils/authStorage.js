import { createAuthStorage } from '@kdyzgl/api-client'

/**
 * 网页端登录态持久化 · apps/web（B5 独立工程）
 *
 * 键名经共享工厂 createAuthStorage 收口（ADR §3.5 第 2 项），但**沿用一期 PC 端的键值**
 * `hrm_admin_token` / `hrm_admin_user`，而非 `hrm:web:*`：
 *
 * 为什么：D2 裁定「短期保留 @admin 只读引用」—— 本端路由仍只读引用一期页面（员工/部门/驿站/个人中心等），
 * 这些页面内部经 `@admin/stores/auth` 与 `@admin/utils/request` 读写**上述键**；若本端改用新键，
 * 一期页面将读不到 token（首个请求 401）而确定性白屏/跳转，而一期源码在本批禁改（§12.1）。
 *
 * 端固定语义（clientType=WEB）落在 http.js（X-Client-Type 头）与登录页入参，不靠存储键区分。
 * TODO(扩展): 随 D2 长期方案（自建页面 / 摘除 @admin）另立 ADR 时，键名一并切到 hrm:web:token / hrm:web:user。
 */
const storage = createAuthStorage({ tokenKey: 'hrm_admin_token', userKey: 'hrm_admin_user' })

export const TOKEN_KEY = storage.tokenKey
export const USER_KEY = storage.userKey

export const readToken = storage.readToken
export const readUser = storage.readUser
export const writeAuth = storage.writeAuth
export const clearAuth = storage.clearAuth
