import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 待办 store 的硬规则断言（B4-2）：
 * 1. 逐组独立降级 —— 一组接口挂掉不影响其他组取到数；
 * 2. `null` = 未知、`0` = 确实没有，两者绝不可互相顶替（绝不用 0 冒充未知）。
 * 分组配置本身在 constants/todoGroups.js 里是「接口 → 文案」的映射，这里替换为可控桩，
 * 把断言聚焦在 store 的聚合与降级逻辑上（真实链路由 verify:mock 的 754 项契约断言兜底）。
 */
const mocks = vi.hoisted(() => ({
  readToken: vi.fn(),
  loadOk: vi.fn(),
  loadFail: vi.fn(),
  loadZero: vi.fn()
}))

vi.mock('../utils/authStorage.js', () => ({ readToken: mocks.readToken }))
vi.mock('./auth.js', () => ({ useAuthStore: () => ({ isAdmin: true }) }))
vi.mock('../constants/todoGroups.js', () => ({
  BOSS_TODO_GROUPS: [
    { key: 'ok', title: '正常组', to: '/ok', load: mocks.loadOk },
    { key: 'fail', title: '失败组', to: '/fail', load: mocks.loadFail },
    { key: 'zero', title: '零值组', to: '/zero', load: mocks.loadZero }
  ],
  STAFF_TODO_GROUPS: []
}))

const { useTodoStore } = await import('./todo.js')

const byKey = (store) => Object.fromEntries(store.groups.map((item) => [item.key, item]))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mocks.readToken.mockReturnValue('demo-token')
  mocks.loadOk.mockResolvedValue({ total: 2, rows: [{ key: 1, title: '待处理' }] })
  mocks.loadFail.mockRejectedValue(new Error('接口挂了'))
  mocks.loadZero.mockResolvedValue({ total: 0, rows: [] })
})

describe('useTodoStore · 逐组独立降级', () => {
  it('单组 reject 只影响该组，其他组照常取值', async () => {
    const store = useTodoStore()
    await store.refresh()
    const groups = byKey(store)
    expect(groups.ok.total).toBe(2)
    expect(groups.ok.error).toBe('')
    expect(groups.fail.total).toBeNull()
    expect(groups.fail.error).toBe('接口挂了')
    expect(groups.fail.rows).toEqual([])
    expect(groups.zero.total).toBe(0)
    expect(groups.zero.error).toBe('')
  })

  it('reject 无 message 时兜底为「加载失败」，不把 undefined 透到界面', async () => {
    mocks.loadFail.mockRejectedValue({})
    const store = useTodoStore()
    await store.refresh()
    expect(byKey(store).fail.error).toBe('加载失败')
  })
})

describe('useTodoStore · null 与 0 语义不可互换', () => {
  it('失败组是 null，零值组是 0，二者必须可区分', async () => {
    const store = useTodoStore()
    await store.refresh()
    expect(store.counts.fail).toBeNull()
    expect(store.counts.zero).toBe(0)
    expect(store.counts.fail).not.toBe(store.counts.zero)
  })

  it('只要有一组成功，known 即为 true（消费方可放行渲染）', async () => {
    const store = useTodoStore()
    await store.refresh()
    expect(store.known).toBe(true)
  })

  it('全部失败时 known=false，消费方显示 ··· 而不是 0', async () => {
    mocks.loadOk.mockRejectedValue(new Error('x'))
    mocks.loadZero.mockRejectedValue(new Error('y'))
    const store = useTodoStore()
    await store.refresh()
    expect(store.groups.every((item) => item.total === null)).toBe(true)
    expect(store.known).toBe(false)
  })

  it('总数只累加已确认的组，失败组按 0 计而不是把整页判成 0', async () => {
    const store = useTodoStore()
    await store.refresh()
    expect(store.total).toBe(2)
  })
})

describe('useTodoStore · 会话与加载态', () => {
  it('counts 按分组 key 收敛，供首页宫格按 key 取角标', async () => {
    const store = useTodoStore()
    await store.refresh()
    expect(store.counts).toEqual({ ok: 2, fail: null, zero: 0 })
  })

  it('未登录时不发请求并清空快照', async () => {
    mocks.readToken.mockReturnValue('')
    const store = useTodoStore()
    await store.refresh()
    expect(store.groups).toEqual([])
    expect(mocks.loadOk).not.toHaveBeenCalled()
  })

  it('refresh 期间 loading 为真，结束后复位', async () => {
    const store = useTodoStore()
    const pending = store.refresh()
    expect(store.loading).toBe(true)
    await pending
    expect(store.loading).toBe(false)
  })

  it('clear 清空快照（切身份/登出时用）', async () => {
    const store = useTodoStore()
    await store.refresh()
    store.clear()
    expect(store.groups).toEqual([])
  })
})
