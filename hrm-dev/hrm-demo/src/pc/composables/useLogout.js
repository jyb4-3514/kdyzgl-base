import { useAuthStore } from '@admin/stores/auth'
import { logout as logoutApi } from '@admin/api/auth'

/**
 * PC 端退出登录（薄封装）
 *
 * 为什么放在 hrm-demo 侧新建：一期 auth store 只有 setAuth / updateUser / clearAuth，没有 logout action，
 * 而「调退出接口 + 清登录态」是业务动作，布局层不该直连 api；一期源码冻结不能改，故在此收口。
 * 返回函数而不是在 useLogout 里直接执行：确认弹窗被取消时不应产生任何副作用。
 */
export function useLogout() {
  const authStore = useAuthStore()

  return async function logout() {
    try {
      // 会话可能已失效（401 已由拦截器处理），退出接口失败不阻断本地登出
      await logoutApi()
    } catch (e) {
      /* 忽略：继续清本地登录态 */
    }
    authStore.clearAuth()
  }
}
