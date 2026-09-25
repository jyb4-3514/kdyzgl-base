import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 待办 store 的硬规则断言（B4-2）· 员工端「驿站助手」
 * 1. 逐组独立降级 —— 一组接口挂掉不影响其他组取到数；
 * 2. `null` = 未知、`0` = 确实没有，两者绝不可互相顶替（绝不用 0 冒充未知）。
 *
 * 分组取数已从 constants/todoGroups.js 迁入 store，配置层只剩静态描述、没有可插桩的 `load`，
 * 故这里改为桩住 api 层；真实链路由 verify:mobile 的契约断言兜底。
 * 端固定化：分组集合为员工端 4 类（STAFF）/ 5 类（STATION_ADMIN 多「待初审请假」）。
 */
const mocks = vi.hoisted(() => ({
  readToken: vi.fn(),
  getWorkOrders: vi.fn(),
  getMyMakeups: vi.fn(),
  getMyPayrolls: vi.fn(),
  getMyLeaves: vi.fn(),
  getLeaveList: vi.fn(),
  auth: { role: 'STAFF' }
}))

vi.mock('../utils/authStorage.js', () => ({ readToken: mocks.readToken }))
vi.mock('./auth.js', () => ({ useAuthStore: () => ({ user: { role: mocks.auth.role } }) }))
vi.mock('../api/workOrder.js', () => ({ getWorkOrders: mocks.getWorkOrders }))
vi.mock('../api/attendance.js', () => ({ getMyMakeups: mocks.getMyMakeups }))
vi.mock('../api/finance.js', () => ({ getMyPayrolls: mocks.getMyPayrolls }))
vi.mock('../api/leave.js', () => ({ getMyLeaves: mocks.getMyLeaves, getLeaveList: mocks.getLeaveList }))

const { useTodoStore } = await import('./todo.js')

const byKey = (store) => Object.fromEntries(store.groups.map((item) => [item.key, item]))
const emptyPage = { list: [], total: 0 }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mocks.auth.role = 'STAFF'
  mocks.readToken.mockReturnValue('demo-token')
  mocks.getWorkOrders.mockResolvedValue({
    list: [{ id: 1, orderNo: 'W1', title: '包裹破损', stationName: 'A站', priority: 1 }],
    total: 2
  })
  mocks.getMyMakeups.mockRejectedValue(new Error('接口挂了'))
  mocks.getMyPayrolls.mockResolvedValue(emptyPage)
  mocks.getMyLeaves.mockResolvedValue(emptyPage)
  mocks.getLeaveList.mockResolvedValue(emptyPage)
})

describe('useTodoStore · 逐组独立降级', () => {
  it('单组 reject 只影响该组，其他组照常取值', async () => {
    const store = useTodoStore()
    await store.refresh()
    const groups = byKey(store)

    expect(groups.orders.total).toBe(2)
    expect(groups.orders.error).toBe('')
    expect(groups.myMakeups.total).toBeNull()
    expect(groups.myMakeups.error).toBe('接口挂了')
    expect(groups.myMakeups.rows).toEqual([])
    expect(groups.myPayrolls.total).toBe(0)
    expect(groups.myPayrolls.error).toBe('')
  })

  it('reject 无 message 时兜底为「加载失败」，不把 undefined 透到界面', async () => {
    mocks.getMyMakeups.mockRejectedValue({})
    const store = useTodoStore()
    await store.refresh()

    expect(byKey(store).myMakeups.error).toBe('加载失败')
  })

  it('行文案与接口字段绑定：工单行标题、驿站与优先级 tag 均按契约拼出', async () => {
    const store = useTodoStore()
    await store.refresh()

    const row = byKey(store).orders.rows[0]
    expect(row.title).toBe('#W1 包裹破损')
    expect(row.meta).toBe('A站')
    expect(row.tag.value).toBe(1)
  })
})

describe('useTodoStore · null 与 0 语义不可互换', () => {
  it('失败组是 null，零值组是 0，二者必须可区分', async () => {
    const store = useTodoStore()
    await store.refresh()

    expect(store.counts.myMakeups).toBeNull()
    expect(store.counts.myPayrolls).toBe(0)
    expect(store.counts.myMakeups).not.toBe(store.counts.myPayrolls)
  })

  it('只要有一组成功，known 即为 true（消费方可放行渲染）', async () => {
    const store = useTodoStore()
    await store.refresh()
    expect(store.known).toBe(true)
  })

  it('全部失败时 known=false，消费方显示 ··· 而不是 0', async () => {
    mocks.getWorkOrders.mockRejectedValue(new Error('x'))
    mocks.getMyMakeups.mockRejectedValue(new Error('x'))
    mocks.getMyPayrolls.mockRejectedValue(new Error('x'))
    mocks.getMyLeaves.mockRejectedValue(new Error('x'))

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
  it('counts 按分组 key 收敛，供首页宫格按 key 取角标（STAFF：4 组）', async () => {
    const store = useTodoStore()
    await store.refresh()
    expect(store.counts).toEqual({ orders: 2, myPayrolls: 0, myMakeups: null, myLeaves: 0 })
  })

  it('组级角色白名单：站长多「待初审请假」一组，员工不可见', async () => {
    const staffStore = useTodoStore()
    await staffStore.refresh()
    expect(byKey(staffStore).leaveReview).toBeUndefined()

    // 换角色须换 pinia：store 在创建时捕获 auth，同会期内不重算分组集合（模拟重新登录的新会期）
    setActivePinia(createPinia())
    mocks.auth.role = 'STATION_ADMIN'
    const adminStore = useTodoStore()
    await adminStore.refresh()
    expect(byKey(adminStore).leaveReview).toBeDefined()
    expect(mocks.getLeaveList).toHaveBeenCalled()
  })

  it('未登录时不发请求并清空快照', async () => {
    mocks.readToken.mockReturnValue('')
    const store = useTodoStore()
    await store.refresh()

    expect(store.groups).toEqual([])
    expect(mocks.getWorkOrders).not.toHaveBeenCalled()
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
