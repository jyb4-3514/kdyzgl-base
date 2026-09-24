// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * PC 路由守卫回归网（对应 demo-design.md 10.1 的四条规则 + 本轮 P1 修复）
 *
 * 为什么要驱动真实 router 实例：守卫的多数缺陷（半残态白屏、404 被静默兜到看板）
 * 都出在「二次导航」上，只测 route 对象或单独抽出守卫函数都验不到最终落点。
 *
 * 为什么把 createWebHistory 换成 createMemoryHistory：jsdom 下每次导航都会改写 window.history，
 * 用例之间互相污染；memory history 让每条用例的地址栈完全独立。
 */

/** 登录态桩：守卫只读 isLoggedIn / user / needChangePwd，并调用 clearAuth 自愈 */
const auth = vi.hoisted(() => ({
  state: { isLoggedIn: false, user: null, needChangePwd: false },
  clearAuth: vi.fn()
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, createWebHistory: () => actual.createMemoryHistory() }
})

vi.mock('@admin/stores/auth', () => ({
  useAuthStore: () => ({
    get isLoggedIn() {
      return auth.state.isLoggedIn
    },
    get user() {
      return auth.state.user
    },
    get needChangePwd() {
      return auth.state.needChangePwd
    },
    clearAuth: auth.clearAuth
  })
}))

// 页面组件一律 stub：本 spec 只验守卫分支，加载真实视图会拖入 Element Plus 等重依赖，
// 且懒加载中的组件会让导航挂起（路径必须与 router/index.js 内的写法一致，否则 mock 不命中）。
// 工厂函数必须内联：vi.mock 会被提升到文件顶部，引用外部常量会落在 TDZ
const pages = vi.hoisted(() => ({ stub: { template: '<div />' } }))
vi.mock('../layout/index.vue', () => ({ default: pages.stub }))
vi.mock('../views/dashboard/index.vue', () => ({ default: pages.stub }))
vi.mock('../views/error/NotFound.vue', () => ({ default: pages.stub }))
vi.mock('../views/login/index.vue', () => ({ default: pages.stub }))
vi.mock('@admin/views/profile/index.vue', () => ({ default: pages.stub }))

const { default: router } = await import('./index.js')

// jsdom 未实现 scrollTo，而 router 的 scrollBehavior 每次成功导航都会调用它，会刷一屏 jsdomError 噪声。
// 测试不关心滚动位置，直接置空
window.scrollTo = () => {}

const ADMIN = { id: 1, role: 'ADMIN', pwdChanged: true, realName: '管理员' }
const STAFF = { id: 2, role: 'STAFF', pwdChanged: true, realName: '员工' }

/** 当前地址在用例间是共享的：不复位会让「目标与当前同址」的导航被直接判定重复、守卫不跑（假通过） */
let seq = 0
async function reset() {
  auth.state.isLoggedIn = false
  auth.state.user = null
  auth.state.needChangePwd = false
  auth.clearAuth.mockClear()
  // 挂一个每次都不同的 query，确保下一条断言导航的地址必然与当前不同
  await router.replace({ path: '/login', query: { t: String((seq += 1)) } }).catch(() => {})
}

beforeEach(async () => {
  await reset()
})

describe('PC 守卫 · 登录态', () => {
  it('未登录访问受保护页 → 跳登录页并带上回跳地址', async () => {
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
  })

  it('已登录访问 /login → 直接进本角色落地页', async () => {
    auth.state.isLoggedIn = true
    auth.state.user = ADMIN
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('首登未改密 → 锁定在个人中心，其他页面一律弹回', async () => {
    auth.state.isLoggedIn = true
    auth.state.user = { ...ADMIN, pwdChanged: false }
    auth.state.needChangePwd = true
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/profile')
  })
})

describe('PC 守卫 · 半残态自愈（本轮白屏修复点）', () => {
  it('有 token 无 user → 清登录态恰好一次、落登录页带 redirect，且不抛 TypeError', async () => {
    auth.state.isLoggedIn = true
    auth.state.user = null
    // 清登录态必须让 isLoggedIn 同步失效，否则第二次导航仍按「已登录」分支走
    auth.clearAuth.mockImplementation(() => {
      auth.state.isLoggedIn = false
    })

    const err = await router.push('/dashboard').catch((e) => e)

    expect(err).toBeUndefined() // 守卫内裸解引用 user 会在这里炸成 TypeError
    expect(auth.clearAuth).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard')
  })
})

describe('PC 守卫 · 越权与未匹配路径', () => {
  it('角色不在 meta.roles 白名单 → 回本角色落地页而非报错页', async () => {
    auth.state.isLoggedIn = true
    auth.state.user = STAFF
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('未知路径 → 渲染 404 且地址不变，不再静默跳看板', async () => {
    auth.state.isLoggedIn = true
    auth.state.user = ADMIN
    await router.push('/no-such-page')
    expect(router.currentRoute.value.name).toBe('NotFound')
    expect(router.currentRoute.value.path).toBe('/no-such-page')
  })
})
