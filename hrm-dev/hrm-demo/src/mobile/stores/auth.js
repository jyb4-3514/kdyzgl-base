import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMe, login as loginApi, logout as logoutApi, updatePassword as updatePasswordApi } from '../api/index.js'
import { clearAuth, readToken, readUser, writeAuth } from '../utils/authStorage.js'
import { HOME_BY_ROLE } from '../constants/accounts.js'

/**
 * 移动端登录态
 * 持久化用独立的 storage key（见 utils/authStorage.js），角色决定路由分流与 Tabbar 形态
 */
export const useAuthStore = defineStore('mobileAuth', () => {
  const token = ref(readToken())
  const user = ref(readUser())

  /**
   * 对外的 user 只读视图：登出、切换身份、401 强制下线都会把 user 置空，
   * 而「置空」到「跳转登录页」之间仍有一帧渲染窗口——此时仍在挂载的页面（工作台/我的）会继续读
   * user.realName 等字段，直接按 null 解引用即抛 TypeError（P0-1）。
   * 会话清空是瞬时动作，导航是异步的，无法用「先跳转再清理」规避（清理前跳转会因 token 仍在而被守卫弹回），
   * 故在读取侧统一兜底：返回空对象，页面渲染空值而不是崩溃，跳转行为不变。
   */
  const EMPTY_USER = Object.freeze({})
  const currentUser = computed(() => user.value || EMPTY_USER)

  const role = computed(() => (user.value && user.value.role) || '')
  const isAdmin = computed(() => role.value === 'ADMIN')
  /** STAFF 不开放同步状态页（T15 验收项），站长与管理员可见 */
  const canSeeSync = computed(() => role.value === 'ADMIN' || role.value === 'STATION_ADMIN')
  const stationId = computed(() => (user.value ? user.value.stationId : null))
  const homePath = computed(() => HOME_BY_ROLE[role.value] || '/login')

  function setSession(nextToken, nextUser) {
    token.value = nextToken
    user.value = nextUser
    writeAuth(nextToken, nextUser)
  }

  function clearSession() {
    token.value = ''
    user.value = null
    clearAuth()
  }

  async function login(payload) {
    const data = await loginApi(payload)
    setSession(data.token, data.employee)
    return data.employee
  }

  /** 演示身份切换：用固定演示账号重新登录（登录会覆盖同账号会话，旧 token 立即失效） */
  async function switchTo(accountKey) {
    // 演示账号只在 Mock 态可用：生产构建下本分支被静态剔除，连带 demo/accounts.js 不进包
    if (import.meta.env.VITE_MOCK_ENABLED !== 'true') throw new Error('当前环境未开启演示身份切换')
    const { DEMO_ACCOUNT_LIST, DEMO_PASSWORD } = await import('@/demo/accounts.js')
    const account = DEMO_ACCOUNT_LIST.find((item) => item.key === accountKey)
    if (!account) throw new Error('演示身份不存在')
    clearAuth() // 先清旧态，避免切换失败后残留上一个身份的角标与缓存
    token.value = ''
    user.value = null
    await login({ username: account.username, password: DEMO_PASSWORD })
    return homePath.value
  }

  /** 刷新当前用户（壳内 onResume 或长时间停留后调用，保证资料与 Mock 一致） */
  async function refreshMe() {
    if (!token.value) return null
    const data = await getMe()
    user.value = data
    writeAuth(token.value, data)
    return data
  }

  async function changePassword(payload) {
    await updatePasswordApi(payload)
    // api.md 4.1.4：改密后强制下线，前端回登录页
    clearSession()
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      clearSession()
    }
  }

  return {
    token,
    user: currentUser,
    role,
    isAdmin,
    canSeeSync,
    stationId,
    homePath,
    setSession,
    clearSession,
    login,
    switchTo,
    refreshMe,
    changePassword,
    logout
  }
})
