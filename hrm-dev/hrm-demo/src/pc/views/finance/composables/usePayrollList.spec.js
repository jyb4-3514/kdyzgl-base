import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 工资单列表 composable 的回归网
 * 重点钉住：竞态守卫（乱序响应丢弃旧结果）、状态计数派生批量候选数、状态条点选可反选。
 */
const mocks = vi.hoisted(() => ({ getPayrolls: vi.fn() }))

vi.mock('../../../api/finance.js', () => ({ getPayrolls: mocks.getPayrolls }))

const { usePayrollList } = await import('./usePayrollList.js')

beforeEach(() => {
  vi.clearAllMocks()
})

describe('usePayrollList · 竞态守卫', () => {
  it('乱序响应：先发后到的旧结果被丢弃', async () => {
    const resolvers = []
    mocks.getPayrolls.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)))
    const api = usePayrollList()

    const first = api.fetchList()
    const second = api.fetchList()
    resolvers[1]({ list: [{ id: 2 }], total: 2, counts: { DRAFT: 1 } })
    await second
    resolvers[0]({ list: [{ id: 1 }], total: 1, counts: {} })
    await first

    expect(api.list.value).toEqual([{ id: 2 }])
    expect(api.total.value).toBe(2)
    expect(api.counts.value).toEqual({ DRAFT: 1 })
    expect(api.listLoading.value).toBe(false)
    expect(api.listError.value).toBe(false)
  })
})

describe('usePayrollList · 参数与派生状态', () => {
  it('批量候选数 = 草稿 + 已驳回；可发布数 = 已通过', async () => {
    mocks.getPayrolls.mockResolvedValue({
      list: [],
      total: 0,
      counts: { DRAFT: 3, REJECTED: 2, APPROVED: 4, PAID: 1 }
    })
    const api = usePayrollList()
    await api.fetchList()

    expect(api.pendingSubmitCount.value).toBe(5)
    expect(api.approvedCount.value).toBe(4)
  })

  it('无计数时派生值为 0，不出现 NaN', () => {
    const api = usePayrollList()
    expect(api.pendingSubmitCount.value).toBe(0)
    expect(api.approvedCount.value).toBe(0)
  })

  it('状态条点选可反选：再点同一状态即取消筛选', async () => {
    mocks.getPayrolls.mockResolvedValue({ list: [], total: 0, counts: null })
    const api = usePayrollList()

    await api.filterByStatus('DRAFT')
    expect(api.query.status).toBe('DRAFT')
    await api.filterByStatus('DRAFT')
    expect(api.query.status).toBeUndefined()
  })

  it('改每页条数会回到第 1 页', async () => {
    mocks.getPayrolls.mockResolvedValue({ list: [], total: 0, counts: null })
    const api = usePayrollList()
    api.query.pageNum = 3
    await api.handleSizeChange(50)

    expect(api.query.pageSize).toBe(50)
    expect(api.query.pageNum).toBe(1)
  })

  it('月份为空时不作为请求参数下发', async () => {
    mocks.getPayrolls.mockResolvedValue({ list: [], total: 0, counts: null })
    const api = usePayrollList()
    api.query.month = ''
    await api.fetchList()

    expect(mocks.getPayrolls).toHaveBeenCalledWith(
      expect.objectContaining({ month: undefined, pageNum: 1, pageSize: 20 })
    )
  })
})
