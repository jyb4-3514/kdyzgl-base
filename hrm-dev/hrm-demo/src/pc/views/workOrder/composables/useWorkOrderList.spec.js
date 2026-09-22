import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

/**
 * 工单列表 composable 的回归网
 * 重点钉住：竞态守卫（乱序响应丢弃过期结果）、失败态与空态互不顶替、Tab 筛选参数口径。
 */
const mocks = vi.hoisted(() => ({ getWorkOrders: vi.fn(), getStations: vi.fn() }))

vi.mock('@admin/api/station', () => ({ getStations: mocks.getStations }))
vi.mock('../../../api/workOrder.js', () => ({ getWorkOrders: mocks.getWorkOrders }))

const { useWorkOrderList } = await import('./useWorkOrderList.js')

const isAdmin = ref(true)

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getStations.mockResolvedValue([])
})

describe('useWorkOrderList · 竞态守卫', () => {
  it('乱序响应：先发后到的旧结果被丢弃，不覆盖新结果', async () => {
    const resolvers = []
    mocks.getWorkOrders.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)))
    const api = useWorkOrderList(isAdmin)

    const first = api.fetchList()
    const second = api.fetchList()
    // 后发的先回：此刻列表应以第二次为准
    resolvers[1]({ list: [{ id: 2 }], total: 2 })
    await second
    // 先发的后回：必须整段丢弃（含 loading 收尾），否则会用旧数据盖住新数据
    resolvers[0]({ list: [{ id: 1 }], total: 1 })
    await first

    expect(api.list.value).toEqual([{ id: 2 }])
    expect(api.total.value).toBe(2)
    expect(api.loading.value).toBe(false)
    expect(api.listError.value).toBe(false)
  })

  it('过期请求失败也不得污染最新状态', async () => {
    const resolvers = []
    mocks.getWorkOrders.mockImplementation(() => new Promise((resolve, reject) => resolvers.push({ resolve, reject })))
    const api = useWorkOrderList(isAdmin)

    const first = api.fetchList()
    const second = api.fetchList()
    resolvers[1].resolve({ list: [{ id: 2 }], total: 2 })
    await second
    resolvers[0].reject(new Error('旧请求失败'))
    await first

    expect(api.list.value).toEqual([{ id: 2 }])
    expect(api.listError.value).toBe(false)
  })
})

describe('useWorkOrderList · 四态与搜索参数', () => {
  it('空集：列表为空、总数为 0，且不进入错误态', async () => {
    mocks.getWorkOrders.mockResolvedValue({ list: [], total: 0 })
    const api = useWorkOrderList(isAdmin)
    await api.fetchList()
    expect(api.list.value).toEqual([])
    expect(api.total.value).toBe(0)
    expect(api.listError.value).toBe(false)
  })

  it('请求失败：清空列表并置错误态，不与空态混淆', async () => {
    mocks.getWorkOrders.mockResolvedValue({ list: [{ id: 1 }], total: 1 })
    const api = useWorkOrderList(isAdmin)
    await api.fetchList()
    expect(api.list.value).toEqual([{ id: 1 }])

    mocks.getWorkOrders.mockRejectedValue(new Error('接口挂了'))
    await api.fetchList()
    expect(api.list.value).toEqual([])
    expect(api.total.value).toBe(0)
    expect(api.listError.value).toBe(true)
  })

  it('Tab 参数口径：全部不带状态、数字 Tab 带 status、超时未处理带 overdueUnhandled', () => {
    const api = useWorkOrderList(isAdmin)
    expect(api.buildParams()).toEqual({
      stationId: undefined,
      type: undefined,
      priority: undefined,
      keyword: undefined
    })
    api.activeTab.value = '1'
    expect(api.buildParams().status).toBe(1)
    api.activeTab.value = 'overdueUnhandled'
    expect(api.buildParams().overdueUnhandled).toBe('1')
    expect(api.buildParams().status).toBeUndefined()
  })

  it('hasFilter：全空且停在全部 Tab 为 false，任一条件命中即 true', () => {
    const api = useWorkOrderList(isAdmin)
    expect(api.hasFilter.value).toBe(false)
    api.activeTab.value = '0'
    expect(api.hasFilter.value).toBe(true)
    api.activeTab.value = 'all'
    api.query.keyword = 'x'
    expect(api.hasFilter.value).toBe(true)
    api.query.keyword = ''
    api.query.stationId = 0
    // 0 是合法驿站 id，不能被当成"未筛选"
    expect(api.hasFilter.value).toBe(true)
  })

  it('Tab 计数失败：计数位清空但不影响列表', async () => {
    mocks.getWorkOrders.mockRejectedValue(new Error('bad'))
    const api = useWorkOrderList(isAdmin)
    await api.loadCounts()
    expect(api.tabCounts.value).toEqual({})
  })

  it('headerSub 跟随角色切口径', async () => {
    const admin = useWorkOrderList(ref(true))
    const staff = useWorkOrderList(ref(false))
    expect(admin.headerSub.value.startsWith('数据范围：全域')).toBe(true)
    expect(staff.headerSub.value.startsWith('数据范围：本站')).toBe(true)
  })
})
