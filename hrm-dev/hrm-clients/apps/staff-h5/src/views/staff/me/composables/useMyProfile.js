import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth.js'

/**
 * 员工端「我的」页 Hero 取数三态
 *
 * 为什么状态取 store 而不是组件内自发请求：/auth/me 目前没有页面级调用点，
 * 组件内自行请求等于给接口加了一个新调用时机；收进 store 后壳 onResume 与用户重试能复用同一份状态。
 * 员工端删「账号信息」块后 Hero 成为唯一取数区块，三态必须由它承担，否则 userError 无处回显。
 *
 * @returns {{ state: import('vue').ComputedRef<'loading' | 'error' | 'ready'>, retry: () => Promise<void> }}
 */
export function useMyProfile() {
  const auth = useAuthStore()

  const state = computed(() => {
    if (auth.userError) return 'error'
    if (auth.userLoading || !auth.userLoaded) return 'loading'
    return 'ready'
  })

  /** 重试只走 store 的 refreshMe：不新增 /auth/me 的调用时机，仅用户主动触发 */
  async function retry() {
    try {
      await auth.refreshMe()
    } catch (e) {
      // 失败原因已记入 userError 并由错误态回显，这里不再弹第二条提示
    }
  }

  return { state, retry }
}