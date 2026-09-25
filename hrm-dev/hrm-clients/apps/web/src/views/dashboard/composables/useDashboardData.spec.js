import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 看板取数编排的回归网
 * 重点钉住：多路并发下的竞态守卫（切档乱序丢弃旧响应）、区块失败互相隔离、加载态聚合收尾。
 */
const mocks = vi.hoisted(() => ({
  getSummary: vi.fn(),
  getParcelSummary: vi.fn(),
  getParcelTrend: vi.fn(),
  getParcelRanking: vi.fn(),
  getSyncTasks: vi.fn(),
  getWorkOrders: vi.fn()
}))

vi.mock('@/api/dashboard.js', () => ({ getSummary: mocks.getSummary }))
vi.mock('../../../api/parcel.js', () => ({
  getParcelSummary: mocks.getParcelSummary,
  getParcelTrend: mocks.getParcelTrend,
  getParcelRanking: mocks.getParcelRanking
}))
vi.mock('../../../api/syncTask.js', () => ({ getSyncTasks: mocks.getSyncTasks }))
vi.mock('../../../api/workOrder.js', () => ({ getWorkOrders: mocks.getWorkOrders }))

const { useDashboardData } = await import('./useDashboardData.js')

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getSummary.mockResolvedValue({ employeeTotal: 1, stationTotal: 1, departmentTotal: 1, todayLoginCount: 1 })
  mocks.getParcelSummary.mockResolvedValue({
    parcelTotal: 5,
    pickupRate: 0.5,
    todayInbound: 1,
    todayPickup: 1,
    pendingPickup: 0,
    abnormalCount: 0
  })
  mocks.getParcelTrend.mockResolvedValue([])
  mocks.getParcelRanking.mockResolvedValue([])
  mocks.getSyncTasks.mockResolvedValue({ list: [], total: 0 })
  mocks.getWorkOrders.mockResolvedValue({ list: [], total: 0 })
})

describe('useDashboardData · 竞态守卫', () => {
  it('切档乱序：先发后到的旧趋势被整段丢弃', async () => {
    const resolvers = []
    mocks.getParcelTrend.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)))
    const page = useDashboardData()

    const first = page.loadTrend()
    page.trendDays = 30
    const second = page.loadTrend()
    // 后发的先回：以 30 天数据为准
    resolvers[1]([{ date: '2026-08-31', inbound: 7, pickup: 3 }])
    await second
    // 先发的后回：必须丢弃，否则 7 天旧数据会盖住 30 天
    resolvers[0]([{ date: '2026-09-06', inbound: 1, pickup: 1 }])
    await first

    expect(page.trend).toEqual([{ date: '2026-08-31', inbound: 7, pickup: 3 }])
    expect(page.trendLoading).toBe(false)
    expect(page.trendError).toBe(false)
  })

  it('切换排行口径乱序：旧口径结果不覆盖新口径', async () => {
    const resolvers = []
    mocks.getParcelRanking.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)))
    const page = useDashboardData()

    const first = page.loadRanking()
    page.rankSort = 'pickupRate'
    const second = page.loadRanking()
    resolvers[1]([{ stationId: 2, stationName: '新口径' }])
    await second
    resolvers[0]([{ stationId: 1, stationName: '旧口径' }])
    await first

    expect(page.ranking).toEqual([{ stationId: 2, stationName: '新口径' }])
    expect(page.rankLoading).toBe(false)
  })
})

describe('useDashboardData · 区块隔离与加载态', () => {
  it('同步健康度失败只置自身错误态，不污染包裹指标', async () => {
    mocks.getSyncTasks.mockRejectedValue(new Error('接口挂了'))
    const page = useDashboardData()

    await page.loadAll()

    expect(page.healthError).toBe(true)
    expect(page.parcelError).toBe(false)
    expect(page.parcelSummary).toMatchObject({ parcelTotal: 5 })
  })

  it('loadAll：六路并发全部发起，结束收 loading 并写入更新时间与页头副信息', async () => {
    const page = useDashboardData()
    await page.loadAll()

    expect(mocks.getSummary).toHaveBeenCalledTimes(1)
    expect(mocks.getParcelSummary).toHaveBeenCalledTimes(1)
    expect(mocks.getParcelTrend).toHaveBeenCalledWith(7)
    expect(mocks.getParcelRanking).toHaveBeenCalledWith('parcelTotal')
    expect(mocks.getSyncTasks).toHaveBeenCalledTimes(1)
    expect(mocks.getWorkOrders).toHaveBeenCalledTimes(5)

    expect(page.loading).toBe(false)
    expect(page.headerSub).toContain('数据范围：全域')
    expect(page.headerSub).toContain('更新于')
  })

  it('错误态即便列表为空也不与空态混淆：包裹失败时 errorText 随卡片自带', async () => {
    mocks.getParcelSummary.mockRejectedValue(new Error('bad'))
    const page = useDashboardData()
    await page.loadParcel()

    expect(page.parcelError).toBe(true)
    const pickupCard = page.inlineCards.find((card) => card.key === 'pickupRate')
    expect(pickupCard.error).toBe(true)
    expect(pickupCard.errorText).toBe('取件率加载失败')
  })
})
