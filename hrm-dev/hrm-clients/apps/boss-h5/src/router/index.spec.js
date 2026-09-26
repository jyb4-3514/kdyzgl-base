// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 管理端路由守卫回归网（迁移自 hrm-demo router/index.spec.js 的本端子集，B4）
 *
 * 移动端守卫三条分支（公开页 / 未登录 / 角色白名单）与 404 兜底此前只有演示工程覆盖；
 * 端固定化后本端仍需独立守护：越权与 404 不得回归成「静默回首页」。
 * 另加一条端隔离回归：员工端路径 /staff/* 不得出现在本端路由表（跨域路由分支 = 0）。
 */

const auth = vi.hoisted(() => ({
  state: { token: '', user: null },
  HOME: { ADMIN: '/boss/home' }
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
vi.mock('../views/message/MessagePage.vue', () => ({ default: pages.stub }))
vi.mock('../modules/boss/views/home.vue', () => ({ default: pages.stub }))
vi.mock('../views/error/NotFound.vue', () => ({ default: pages.stub }))

const { default: router } = await import('./index.js')

// jsdom 未实现 scrollTo，router 的 scrollBehavior 每次成功导航都会调用，会产生 jsdomError 噪声
window.scrollTo = () => {}

const ADMIN = { id: 1, role: 'ADMIN', stationId: null, realName: '管理员' }
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

describe('管理端守卫 · 登录态', () => {
  it('未登录访问业务页 → 跳登录页并带上回跳地址', async () => {
    await router.push('/boss/hr')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/boss/hr')
  })

  it('已登录访问 /login → 落管理端经营总览', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = ADMIN
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/boss/home')
  })
})

describe('管理端守卫 · 越权与未匹配路径', () => {
  it('非 ADMIN 角色进入管理端页面 → 提示无权访问且不落在业务页', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = STAFF
    // 非本端身份被守卫拦下；本端无 STAFF 首页，homePath 兜底为 /login，vue-router 对该重定向以 abort 结束导航。
    // 显式吞掉该导航错误，保证后续断言可执行（断言的是「拦下了」而非「跳去哪」）。
    await router.push('/boss/home').catch(() => {})
    expect(vant.showFailToast).toHaveBeenCalled()
    expect(router.currentRoute.value.path).not.toBe('/boss/home')
  })

  it('未知路径 → 渲染 404 且地址不变，不再静默回首页', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = ADMIN
    await router.push('/no-such-page')
    expect(router.currentRoute.value.name).toBe('mobileNotFound')
    expect(router.currentRoute.value.path).toBe('/no-such-page')
  })
})

describe('管理端守卫 · 端隔离（B4 ③ 跨域路由分支 = 0）', () => {
  it('员工端路径 /staff/* 不在本端路由表（解析为 404）', async () => {
    auth.state.token = 'demo-token'
    auth.state.user = ADMIN
    for (const path of ['/staff/home', '/staff/parcel', '/staff/workorder']) {
      const resolved = router.resolve(path)
      expect(resolved.name, `${path} 不应在本端定义`).toBe('mobileNotFound')
    }
  })

  it('被砍路由不再存在于本端路由表：KPI / 趋势 / 排行 / 包裹详情一律解析为 404', () => {
    for (const path of ['/boss/kpi', '/boss/kpi/3', '/boss/trend', '/boss/rank', '/boss/parcel/1']) {
      expect(router.resolve(path).name, `${path} 不应在本端定义`).toBe('mobileNotFound')
    }
  })

  it('保留路由仍在路由表内（反证裁剪未误伤）', () => {
    for (const path of ['/boss/home', '/boss/alerts', '/boss/workorder', '/boss/hr', '/boss/payroll']) {
      expect(router.resolve(path).name, `${path} 应仍可达`).not.toBe('mobileNotFound')
    }
  })
})
