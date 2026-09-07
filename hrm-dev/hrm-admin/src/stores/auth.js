import { defineStore } from 'pinia'

// localStorage 持久化键名（与 utils/request.js 共享，避免魔法字符串）
export const TOKEN_KEY = 'hrm_admin_token'
export const USER_KEY = 'hrm_admin_user'

function readUserFromStorage() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

/**
 * 登录态 Store
 * - token / user 持久化到 localStorage，刷新页面不丢失
 * - needChangePwd：pwdChanged=false 的账号被路由守卫锁定在 /profile 改密流程
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUserFromStorage()
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => !!state.user && state.user.role === 'ADMIN',
    needChangePwd: (state) => !!state.user && state.user.pwdChanged === false
  },
  actions: {
    setAuth(token, user) {
      this.token = token || ''
      this.user = user || null
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    updateUser(user) {
      this.user = user || null
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    clearAuth() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
