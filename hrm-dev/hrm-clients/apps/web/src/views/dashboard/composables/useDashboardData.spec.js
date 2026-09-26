import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 看板取数编排的回归网（MVP 裁剪后）
 *
 * 原「包裹趋势切档竞态」「排行口径竞态」「同步健康度隔离」「包裹失败错误态」四组用例
 * 随包裹族下架删除；现只守住「两路并发 + 失败隔离 + 收尾」。
 */
const mocks = vi.hoisted(() => ({
  getSummary: vi.fn(),
  getWorkOrders: vi.fn()
}))

vi.mock('@/api/dashboard.js', () => ({ getSummary: mocks.getSummary }))
vi.mock('../../../api/workOrder.js', () => ({ getWorkOrders: mocks.getWorkOrders }))

const { useDashboardData } = await import('./useDashboardData.js')

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getSummary.mockResolvedValue({ employeeTotal: 1, stationTotal: 1, departmentTotal: 1, todayLoginCount: 1 })
  mocks.getWorkOrders.mockResolvedValue({ list: [], total: 0 })
})

describe('useDashboardData · 两路并发与失败隔离', () => {
  it('工单失败只置自身错误态，不污染一期组织规模', async () => {
    mocks.getWorkOrders.mockRejectedValue(new Error('接口挂了'))
    const page = useDashboardData()

    await page.loadAll()

    expect(page.woError).toBe(true)
    expect(page.baseError).toBe(false)
    expect(page.summary).toMatchObject({ employeeTotal: 1 })
  })

  it('loadAll：两路并发发起，结束收 loading 并写入页头副信息', async () => {
    const page = useDashboardData()
    await page.loadAll()

    expect(mocks.getSummary).toHaveBeenCalledTimes(1)
    // 工单指标由 5 次列表请求推算（待处理 / 处理中 / 超时 / 最近 / 已解决）
    expect(mocks.getWorkOrders).toHaveBeenCalledTimes(5)
    expect(page.loading).toBe(false)
    expect(page.headerSub).toContain('数据范围：全域')
    expect(page.headerSub).toContain('更新于')
  })

  it('基础指标失败只置 baseError，不污染工单指标', async () => {
    mocks.getSummary.mockRejectedValue(new Error('bad'))
    const page = useDashboardData()
    await page.loadAll()

    expect(page.baseError).toBe(true)
    expect(page.woError).toBe(false)
  })
})
