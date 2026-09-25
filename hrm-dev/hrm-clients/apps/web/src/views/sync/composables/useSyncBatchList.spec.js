import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 批次流水列表 composable 的回归网
 * 重点钉住：竞态守卫（乱序响应丢弃旧结果）、筛选参数口径、失败态与空态互不顶替。
 */
const mocks = vi.hoisted(() => ({
  getSyncTasks: vi.fn(),
  getStations: vi.fn(),
  triggerSyncTask: vi.fn(),
  retrySyncTask: vi.fn()
}))

vi.mock('@/api/station.js', () => ({ getStations: mocks.getStations }))
// 驿站经 org store 取数，部门接口同模块引入，必须一并桩掉（否则会拉起一期 request 与 element-plus）
vi.mock('@/api/department.js', () => ({ getDepartmentTree: vi.fn() }))
// vitest 未配 resolve.dedupe，一期 auth store 走的是 hrm-admin 自带的 pinia 副本，会读到另一个 activePinia；与本文件无关故直接桩掉
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 9, role: 'ADMIN' } }) }))
vi.mock('../../../api/syncTask.js', () => ({
  getSyncTasks: mocks.getSyncTasks,
  triggerSyncTask: mocks.triggerSyncTask,
  retrySyncTask: mocks.retrySyncTask
}))

const { useSyncBatchList } = await import('./useSyncBatchList.js')

beforeEach(() => {
  vi.clearAllMocks()
  // composable 内消费 pinia store，测试需先激活一个干净实例
  setActivePinia(createPinia())
  mocks.getStations.mockResolvedValue([])
})

describe('useSyncBatchList · 竞态守卫', () => {
  it('乱序响应：先发后到的旧结果被丢弃，不覆盖新结果', async () => {
    const resolvers = []
    mocks.getSyncTasks.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)))
    const api = useSyncBatchList()

    const first = api.fetchList()
    const second = api.fetchList()
    resolvers[1]({ list: [{ id: 2 }], total: 2 })
    await second
    resolvers[0]({ list: [{ id: 1 }], total: 1 })
    await first

    expect(api.list.value).toEqual([{ id: 2 }])
    expect(api.total.value).toBe(2)
    expect(api.loading.value).toBe(false)
    expect(api.listError.value).toBe(false)
  })

  it('过期请求失败也不得污染最新状态', async () => {
    const resolvers = []
    mocks.getSyncTasks.mockImplementation(() => new Promise((resolve, reject) => resolvers.push({ resolve, reject })))
    const api = useSyncBatchList()

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

describe('useSyncBatchList · 参数口径与状态', () => {
  it('请求参数：关键字空串不下发，页码与条数原样带上', async () => {
    mocks.getSyncTasks.mockResolvedValue({ list: [], total: 0 })
    const api = useSyncBatchList()
    await api.fetchList()

    expect(mocks.getSyncTasks).toHaveBeenCalledWith({
      stationId: undefined,
      status: undefined,
      keyword: undefined,
      pageNum: 1,
      pageSize: 20
    })
  })

  it('hasFilter 命中任一筛选条件即为 true，0 号驿站不算未筛选', () => {
    const api = useSyncBatchList()
    expect(api.hasFilter.value).toBe(false)
    api.query.keyword = 'B2026'
    expect(api.hasFilter.value).toBe(true)
    api.query.keyword = ''
    api.query.stationId = 0
    expect(api.hasFilter.value).toBe(true)
  })

  it('重置筛选回到第 1 页并清空条件', async () => {
    mocks.getSyncTasks.mockResolvedValue({ list: [], total: 0 })
    const api = useSyncBatchList()
    api.query.stationId = 3
    api.query.status = 3
    api.query.keyword = 'x'
    api.query.pageNum = 4
    await api.handleReset()

    expect(api.query.stationId).toBeUndefined()
    expect(api.query.status).toBeUndefined()
    expect(api.query.keyword).toBe('')
    expect(api.query.pageNum).toBe(1)
  })

  it('取数成功回写时间戳回调，失败不写', async () => {
    const markUpdated = vi.fn()
    const api = useSyncBatchList({ markUpdated })

    mocks.getSyncTasks.mockResolvedValue({ list: [{ id: 1 }], total: 1 })
    await api.fetchList()
    expect(markUpdated).toHaveBeenCalledTimes(1)

    mocks.getSyncTasks.mockRejectedValue(new Error('bad'))
    await api.fetchList()
    expect(markUpdated).toHaveBeenCalledTimes(1)
    expect(api.listError.value).toBe(true)
  })

  it('改每页条数会回到第 1 页', async () => {
    mocks.getSyncTasks.mockResolvedValue({ list: [], total: 0 })
    const api = useSyncBatchList()
    api.query.pageNum = 5
    await api.handleSizeChange(50)

    expect(api.query.pageSize).toBe(50)
    expect(api.query.pageNum).toBe(1)
  })
})
