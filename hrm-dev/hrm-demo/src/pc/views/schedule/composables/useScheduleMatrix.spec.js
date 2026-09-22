import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 排班矩阵 composable 的回归网
 * 重点钉住：竞态守卫（切周乱序响应不覆盖新周）、错误态与空态互不顶替、
 * 本地增量（改回原值不留待提交项、撤销回写服务端值）。
 */
const mocks = vi.hoisted(() => ({ getSchedules: vi.fn(), getShifts: vi.fn(), saveSchedulesBatch: vi.fn(), confirm: vi.fn() }))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm }
}))
vi.mock('@admin/api/station', () => ({
  getStations: vi.fn().mockResolvedValue([{ id: 1, stationName: '城东驿站' }])
}))
// 驿站经 org store 取数，部门接口同模块引入，必须一并桩掉（否则会拉起一期 request）
vi.mock('@admin/api/department', () => ({ getDepartmentTree: vi.fn() }))
vi.mock('@admin/stores/auth', () => ({ useAuthStore: () => ({ user: { id: 9, role: 'ADMIN', stationId: 1 } }) }))
vi.mock('../../../api/attendance.js', () => ({
  getSchedules: mocks.getSchedules,
  getShifts: mocks.getShifts,
  saveSchedulesBatch: mocks.saveSchedulesBatch
}))

const { useScheduleMatrix } = await import('./useScheduleMatrix.js')

const matrixOf = (dates, employees) => ({ dates, shifts: [], employees, weekStart: dates[0] })

beforeEach(() => {
  vi.clearAllMocks()
  // composable 内消费 pinia store，测试需先激活一个干净实例
  setActivePinia(createPinia())
})

describe('useScheduleMatrix · 竞态守卫', () => {
  it('切周乱序响应：旧周结果不覆盖新周结果', async () => {
    const resolvers = []
    mocks.getSchedules.mockImplementation(() => new Promise((resolve) => resolvers.push(resolve)))
    const api = useScheduleMatrix()
    api.stationId.value = 1

    const first = api.fetchMatrix()
    api.weekStart.value = '2026-09-28'
    const second = api.fetchMatrix()

    // 后发（新周）先回
    resolvers[1](matrixOf(['2026-09-28'], [{ employeeId: 1, employeeName: '张三', days: [{ workDate: '2026-09-28', shiftId: 5 }] }]))
    await second
    // 先发（旧周）后回：必须整段丢弃，含 loading 收尾
    resolvers[0](matrixOf(['2026-09-21'], []))
    await first

    expect(api.dates.value).toEqual(['2026-09-28'])
    expect(api.rows.value).toHaveLength(1)
    expect(api.rows.value[0].days[0].shiftId).toBe(5)
    expect(api.loading.value).toBe(false)
  })

  it('过期请求失败也不得把界面推进错误态', async () => {
    const resolvers = []
    mocks.getSchedules.mockImplementation(() => new Promise((resolve, reject) => resolvers.push({ resolve, reject })))
    const api = useScheduleMatrix()
    api.stationId.value = 1

    const first = api.fetchMatrix()
    const second = api.fetchMatrix()
    resolvers[1].resolve(matrixOf(['2026-09-28'], []))
    await second
    resolvers[0].reject(new Error('旧请求失败'))
    await first

    expect(api.matrixError.value).toBe(false)
    expect(api.dates.value).toEqual(['2026-09-28'])
  })
})

describe('useScheduleMatrix · 四态与空集', () => {
  it('驿站未选定时不发请求', async () => {
    const api = useScheduleMatrix()
    api.stationId.value = null
    await api.fetchMatrix()
    expect(mocks.getSchedules).not.toHaveBeenCalled()
  })

  it('空矩阵：行与日期置空，不进入错误态', async () => {
    mocks.getSchedules.mockResolvedValue(matrixOf(['2026-09-21'], []))
    const api = useScheduleMatrix()
    api.stationId.value = 1
    await api.fetchMatrix()
    expect(api.rows.value).toEqual([])
    expect(api.matrixError.value).toBe(false)
  })

  it('取数失败：清空矩阵并置错误态，不与空态混淆', async () => {
    mocks.getSchedules.mockRejectedValue(new Error('接口挂了'))
    const api = useScheduleMatrix()
    api.stationId.value = 1
    await api.fetchMatrix()
    expect(api.rows.value).toEqual([])
    expect(api.matrixError.value).toBe(true)
  })
})

describe('useScheduleMatrix · 本地增量与撤销', () => {
  async function loaded() {
    mocks.getSchedules.mockResolvedValue(
      matrixOf(['2026-09-21', '2026-09-22'], [
        {
          employeeId: 1,
          employeeName: '张三',
          days: [
            { workDate: '2026-09-21', shiftId: 3 },
            { workDate: '2026-09-22', shiftId: null }
          ]
        }
      ])
    )
    const api = useScheduleMatrix()
    api.stationId.value = 1
    await api.fetchMatrix()
    return api
  }

  it('改回原值不产生待提交项，清空算一次改动', async () => {
    const api = await loaded()
    const row = api.rows.value[0]

    api.handleShiftChange({ row, index: 0, shiftId: 7 })
    expect(api.dirtyCount.value).toBe(1)

    api.handleShiftChange({ row, index: 0, shiftId: 3 })
    expect(api.dirtyCount.value).toBe(0)

    // 原值为 null，清空后仍为 null，不算改动
    api.handleShiftChange({ row, index: 1, shiftId: null })
    expect(api.dirtyCount.value).toBe(0)

    // 有值的格置空才算改动
    api.handleShiftChange({ row, index: 0, shiftId: null })
    expect(api.dirtyCount.value).toBe(1)
    expect(api.rows.value[0].days[0].shiftId).toBeNull()
  })

  it('同一格反复改只留最后一次提交体', async () => {
    const api = await loaded()
    const row = api.rows.value[0]
    api.handleShiftChange({ row, index: 0, shiftId: 4 })
    api.handleShiftChange({ row, index: 0, shiftId: 9 })
    expect(api.dirtyCount.value).toBe(1)
    expect([...api.rows.value[0].days].length).toBe(2)
  })

  it('discardChanges 回写服务端值并清空待提交队列', async () => {
    const api = await loaded()
    const row = api.rows.value[0]
    api.handleShiftChange({ row, index: 0, shiftId: 7 })
    expect(api.dirtyCount.value).toBe(1)

    api.discardChanges()
    expect(api.dirtyCount.value).toBe(0)
    expect(api.rows.value[0].days[0].shiftId).toBe(3)
  })

  it('无未保存改动时不弹确认直接放行', async () => {
    const api = await loaded()
    await expect(api.confirmDiscard()).resolves.toBe(true)
    expect(mocks.confirm).not.toHaveBeenCalled()
  })

  it('本周进度：分母 = 员工数 × 天数，分子 = 已排格数', async () => {
    const api = await loaded()
    expect(api.totalCells.value).toBe(2)
    expect(api.filledCells.value).toBe(1)
  })
})
