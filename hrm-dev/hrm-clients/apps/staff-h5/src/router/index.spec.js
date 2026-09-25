// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 员工端路由守卫回归网（迁移自 hrm-demo router/index.spec.js 的本端子集，B3）
 *
 * 移动端守卫三条分支（公开页 / 未登录 / 角色白名单）与 404 兜底此前只有演示工程覆盖；
 * 端固定化后本端仍需独立守护：越权与 404 不得回归成「静默回首页」。
 */

const auth = vi.hoisted(() => ({
  state: { token: '', user: null },
  HOME: { STATION_ADMIN: '/staff/home', STAFF: '/staff/home' }
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
vi.mock('../views/message/MessagePage.vue', () => ({ default: pages.stub }))
vi.mock('../views/error/NotFound.vue', () => ({ default: pages.stub }))

const { default: router } = await import('./index.js')

// jsdom 未实现 scrollTo，router 的 scrollBehavior 每次成功导航都会调用，会产生 jsdomError 噪声
window.scrollTo = () => {}

const STATION_ADMIN = { id: 3, role: 'STATION_ADMIN', stationId: 1, realName: '站长' }
const STAFF = { id: 4, role: 'STAFF', stationId: 1, realName: '员工' }

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

describe('员工端守卫 · 登录态', () => {
  it('未登录访问业务页 → 跳登录页并带上回跳地址', async () => {
    await router.push('/staff/parcel')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/staff/parcel')
  })

  it('已登录访问 /login → 落员工端工作台', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = STATION_ADMIN
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/staff/home')

    auth.state.user = STAFF
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/staff/home')
  })
})

describe('员工端守卫 · 越权与未匹配路径', () => {
  it('角色不在页面白名单 → 提示无权访问并回员工端工作台', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = STAFF
    // /staff/sync 为站长专属（roles: [STATION_ADMIN]），普通员工不得进入
    await router.push('/staff/sync')
    expect(vant.showFailToast).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/staff/home')
  })

  it('未知路径 → 渲染 404 且地址不变，不再静默回首页', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = STATION_ADMIN
    await router.push('/no-such-page')
    expect(router.currentRoute.value.name).toBe('mobileNotFound')
    expect(router.currentRoute.value.path).toBe('/no-such-page')
  })

  it('旧通知路由 → 落员工端消息页并带通知 Tab', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = STAFF
    await router.push('/staff/notification')
    expect(router.currentRoute.value.path).toBe('/staff/message')
    expect(router.currentRoute.value.query.tab).toBe('notice')
  })
})
