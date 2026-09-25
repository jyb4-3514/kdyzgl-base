import { defineStore } from 'pinia'
import { clearAuth as clearStoredAuth, readToken, readUser, writeAuth } from '../utils/authStorage.js'

/**
 * 网页端登录态（端固定 WEB）
 *
 * 与一期 `@admin/stores/auth` 的关系（ADR §3.5 第 12 项 + D2）：
 * - 本 store 是**本端自有**的登录态真源（PC 页面/路由/布局一律经它），端语义固定为 WEB（仅 ADMIN 可登录）；
 * - 存储键与一期一致（见 utils/authStorage.js 说明），使只读引用的一期页面（内部用 @admin/stores/auth）
 *   仍能读到同一份登录态；两 store 不直接互相调用，只共享 localStorage 契约。
 */
export const useAuthStore = defineStore('webAuth', {
  state: () => ({
    token: readToken() || '',
    user: readUser() || null
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => !!state.user && state.user.role === 'ADMIN',
    /** 首登强制改密：pwdChanged=false 的账号被路由守卫锁定在 /profile（沿用一期规则） */
    needChangePwd: (state) => !!state.user && state.user.pwdChanged === false
  },
  actions: {
    setAuth(token, user) {
      this.token = token || ''
      this.user = user || null
      writeAuth(this.token, this.user)
    },
    updateUser(user) {
      this.user = user || null
      writeAuth(this.token, this.user)
    },
    clearAuth() {
      this.token = ''
      this.user = null
      clearStoredAuth()
    }
  }
})
