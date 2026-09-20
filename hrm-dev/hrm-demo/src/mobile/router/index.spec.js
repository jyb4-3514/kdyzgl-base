// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 移动端路由守卫回归网（对应 demo-design.md 6.2 / 7.2 与 P1-4 修复）
 *
 * 与 PC 同样的思路：用真实 router 实例驱动，才能验到「重定向后的最终落点」。
 * 移动端守卫只有三条分支（公开页 / 未登录 / 角色白名单），但角色越权与 404 兜底
 * 此前完全没有自动化保护，改动一多就容易回归成「静默回首页」。
 */

const auth = vi.hoisted(() => ({
  state: { token: '', user: null },
  HOME: { ADMIN: '/boss/home', STATION_ADMIN: '/staff/home', STAFF: '/staff/home' }
}))

const vant = vi.hoisted(() => ({ showFailToast: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  // hash 历史在 jsdom 下会改写 window.location.hash，用例间互相污染；memory history 完全隔离
  return { ...actual, createWebHashHistory: () => actual.createMemoryHistory() }
})

vi.mock('vant', () => ({ showFailToast: vant.showFailToast }))

// storage 兜底读 token：恒为空，登录态完全由 auth.state 驱动，避免用例间残留
vi.mock('../utils/authStorage.js', () => ({ readToken: () => '', readUser: () => null }))

vi.mock('../stores/auth.js', () => ({
  useAuthStore: () => ({
    get token() {
      return auth.state.token
    },
    get user() {
      return auth.state.user
    },
    get homePath() {
      return (auth.state.user && auth.HOME[auth.state.user.role]) || '/login'
    }
  })
}))

// 页面组件一律 stub（路径与 router/index.js 内的写法一致，否则 mock 不命中、真实视图会让导航挂起）
const pages = vi.hoisted(() => ({ stub: { template: '<div />' } }))
vi.mock('../views/login/index.vue', () => ({ default: pages.stub }))
vi.mock('../views/staff/home.vue', () => ({ default: pages.stub }))
vi.mock('../views/boss/home.vue', () => ({ default: pages.stub }))
vi.mock('../views/error/NotFound.vue', () => ({ default: pages.stub }))

const { default: router } = await import('./index.js')

// jsdom 未实现 scrollTo，router 的 scrollBehavior 每次成功导航都会调用，会产生 jsdomError 噪声
window.scrollTo = () => {}

const ADMIN = { id: 1, role: 'ADMIN', realName: '老板' }
const STAFF = { id: 2, role: 'STAFF', stationId: 1, realName: '员工' }

/** 当前地址在用例间共享：不复位会让「目标与当前同址」的导航被判重复、守卫不跑（假通过） */
let seq = 0
async function reset() {
  auth.state.token = ''
  auth.state.user = null
  vant.showFailToast.mockClear()
  await router.replace({ path: '/login', query: { t: String((seq += 1)) } }).catch(() => {})
}

beforeEach(async () => {
  await reset()
})

describe('移动端守卫 · 登录态', () => {
  it('未登录访问业务页 → 跳登录页并带上回跳地址', async () => {
    await router.push('/staff/parcel')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/staff/parcel')
  })

  it('已登录访问 /login → 按角色各自的 homePath 分流', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = ADMIN
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/boss/home')

    auth.state.user = { ...STAFF, role: 'STATION_ADMIN' }
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/staff/home')
  })
})

describe('移动端守卫 · 越权与未匹配路径', () => {
  it('角色不在页面白名单 → 提示无权访问并回本角色 homePath', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = STAFF
    await router.push('/boss/home')
    expect(vant.showFailToast).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/staff/home')
  })

  it('未知路径 → 渲染 404 且地址不变，不再静默回首页', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = ADMIN
    await router.push('/no-such-page')
    expect(router.currentRoute.value.name).toBe('mobileNotFound')
    expect(router.currentRoute.value.path).toBe('/no-such-page')
  })
})
