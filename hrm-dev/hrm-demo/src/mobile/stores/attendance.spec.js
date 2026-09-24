import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 打卡状态 store 的硬规则断言：
 * 1. 接口失败与「无排班」不可互相顶替；
 * 2. 失败不抹除已有快照：首次失败=空快照，刷新失败=保留 last-good（否则整页被打成错误态）；
 * 3. 打卡成功后就地回写，不整页重拉。
 */
const mocks = vi.hoisted(() => ({ readToken: vi.fn(), getAttendanceStatus: vi.fn() }))

vi.mock('../utils/authStorage.js', () => ({ readToken: mocks.readToken }))
vi.mock('../api/attendance.js', () => ({ getAttendanceStatus: mocks.getAttendanceStatus }))

const { useAttendanceStore } = await import('./attendance.js')

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mocks.readToken.mockReturnValue('demo-token')
})

describe('useAttendanceStore · 取数与失败语义', () => {
  it('成功时覆盖为接口快照', async () => {
    mocks.getAttendanceStatus.mockResolvedValue({ shift: { shiftName: '早班' }, hasSchedule: true, periods: [] })
    const store = useAttendanceStore()
    await store.refresh()

    expect(store.status.shift.shiftName).toBe('早班')
    expect(store.error).toBe('')
    expect(store.loading).toBe(false)
  })

  it('无排班是业务空态：status 有值、error 为空，绝不当成失败', async () => {
    mocks.getAttendanceStatus.mockResolvedValue({ shift: null, rule: null, periods: [], hasSchedule: false })
    const store = useAttendanceStore()
    await store.refresh()

    expect(store.status.hasSchedule).toBe(false)
    expect(store.error).toBe('')
  })

  it('首次取数失败：快照为空并记原因，不用「无排班」冒充失败', async () => {
    mocks.getAttendanceStatus.mockRejectedValue(new Error('出勤状态获取失败'))
    const store = useAttendanceStore()
    await store.refresh()

    expect(store.status).toBeNull()
    expect(store.error).toBe('出勤状态获取失败')
  })

  it('已有快照后刷新失败：保留 last-good 且只更新 error（不把整页打成错误态）', async () => {
    mocks.getAttendanceStatus.mockResolvedValueOnce({ shift: { shiftName: '早班' }, hasSchedule: true, periods: [] })
    mocks.getAttendanceStatus.mockRejectedValueOnce(new Error('网络抖动'))
    const store = useAttendanceStore()

    await store.refresh()
    await store.refresh()

    expect(store.status.shift.shiftName).toBe('早班')
    expect(store.error).toBe('网络抖动')
  })

  it('未登录不发请求并清空快照', async () => {
    mocks.readToken.mockReturnValue('')
    const store = useAttendanceStore()
    store.update({ hasSchedule: true })
    await store.refresh()

    expect(store.status).toBeNull()
    expect(mocks.getAttendanceStatus).not.toHaveBeenCalled()
  })
})

describe('useAttendanceStore · 就地回写', () => {
  it('update 只覆盖传入槽位，其余字段保持', async () => {
    mocks.getAttendanceStatus.mockResolvedValue({ shift: { shiftName: '早班' }, periods: [{ periodIndex: 0 }] })
    const store = useAttendanceStore()
    await store.refresh()

    store.update({ periods: [{ periodIndex: 0, status: 'NORMAL' }] })
    expect(store.status.shift.shiftName).toBe('早班')
    expect(store.status.periods[0].status).toBe('NORMAL')
  })

  it('clear 清空快照与失败原因', async () => {
    mocks.getAttendanceStatus.mockResolvedValue({ hasSchedule: true })
    const store = useAttendanceStore()
    await store.refresh()
    store.clear()

    expect(store.status).toBeNull()
    expect(store.error).toBe('')
  })
})
