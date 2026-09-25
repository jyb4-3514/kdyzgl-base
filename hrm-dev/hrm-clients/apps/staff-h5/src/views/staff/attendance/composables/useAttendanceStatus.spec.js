import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { LOCATE_UNSUPPORTED } from '../model/attendanceUi.js'

/**
 * 打卡页状态中枢的硬规则：
 * 1. 四态：首屏失败进整页错误态；非首屏刷新失败只给提示、保留原状态（否则判定结果会被清空）；
 * 2. 取数唯一来源是 stores/attendance.js（getAttendanceStatus 由 store 调用，本页只读 store.error 判成败）；
 * 3. 接口失败与「无排班 / 无规则」不可互相顶替；
 * 4. 6 态判定的边界：字段缺失按「未打卡」处理，不误报过点或过期；
 * 5. 演示辅助开启后提交坐标取围栏中心，且自查卡标记转为确定态。
 */
const mocks = vi.hoisted(() => ({
  getStatus: vi.fn(),
  getMakeups: vi.fn(),
  getWifi: vi.fn(),
  failToast: vi.fn(),
  readToken: vi.fn()
}))

vi.mock('@/api/attendance.js', () => ({
  getAttendanceStatus: mocks.getStatus,
  getMyMakeups: mocks.getMakeups
}))
vi.mock('@/utils/authStorage.js', () => ({ readToken: mocks.readToken }))
vi.mock('@/utils/bridge.js', () => ({ getWifiInfo: mocks.getWifi }))
vi.mock('vant', () => ({ showFailToast: mocks.failToast }))

const { useAttendanceStatus } = await import('./useAttendanceStatus.js')
const { useAttendanceStore } = await import('@/stores/attendance.js')

const RULE = {
  ruleName: '城东规则',
  matchMode: 'ANY',
  enableWifi: true,
  enableLocation: true,
  enableTimeWindow: true,
  wifiList: [{ ssid: 'ZYD-WIFI' }],
  longitude: 120.1,
  latitude: 30.2,
  radius: 200,
  lateThresholdMin: 5,
  earlyLeaveThresholdMin: 5,
  allowEarlyMin: 30,
  allowLateMin: 60
}

const periodOf = (periodIndex, extra = {}) => ({
  periodIndex,
  name: `时段${periodIndex}`,
  startTime: '08:00',
  endTime: '18:00',
  windowStart: '07:30',
  windowEnd: '19:00',
  onChecked: false,
  offChecked: false,
  onTime: null,
  offTime: null,
  ...extra
})

const statusOf = (periods, extra = {}) => ({
  workDate: '2026-09-24',
  hasSchedule: true,
  shift: { shiftName: '早班', startTime: '08:00', endTime: '18:00', restMinutes: 60, color: '#0958D9' },
  requireSummary: '每日 2 次打卡',
  rule: RULE,
  periods,
  ...extra
})

beforeEach(() => {
  // 取数已收敛到 store，store 是 setup store，每个用例都要有独立 pinia 实例，避免快照跨用例串台
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mocks.readToken.mockReturnValue('demo-token')
  mocks.getWifi.mockReturnValue({ ssid: 'ZYD-WIFI', mock: true })
  mocks.getMakeups.mockResolvedValue({ list: [] })
})

describe('useAttendanceStatus · 四态', () => {
  it('首屏失败进整页错误态，状态清空（不用「无排班」冒充失败）', async () => {
    mocks.getStatus.mockRejectedValue(new Error('出勤状态获取失败'))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.error.value).toBe('出勤状态获取失败')
    expect(att.status.value).toBeNull()
    expect(att.loading.value).toBe(false)
  })

  it('非首屏刷新失败：快照保留、只提示，失败原因记在 store（不置整页错误态）', async () => {
    mocks.getStatus.mockResolvedValueOnce(statusOf([periodOf(0)]))
    mocks.getStatus.mockRejectedValueOnce(new Error('网络抖动'))
    const att = useAttendanceStatus()

    await att.load()
    await att.load(false)

    // 页面侧：数据不动、错误态不亮，只弹一次提示
    expect(att.status.value.workDate).toBe('2026-09-24')
    expect(att.error.value).toBe('')
    expect(mocks.failToast).toHaveBeenCalledTimes(1)
    // store 侧：失败原因仍被如实记录（供首页等其它消费方感知），且 last-good 未被抹除
    expect(useAttendanceStore().error).toBe('网络抖动')
    expect(useAttendanceStore().status.workDate).toBe('2026-09-24')
  })

  it('未登录（无 token）：空快照、无 error、不请求接口', async () => {
    mocks.readToken.mockReturnValue('')
    const att = useAttendanceStatus()

    await att.load()

    expect(att.status.value).toBeNull()
    expect(att.error.value).toBe('')
    expect(att.loading.value).toBe(false)
    expect(mocks.getStatus).not.toHaveBeenCalled()
  })

  it('无排班是业务空态：status 有值、error 为空', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([], { hasSchedule: false }))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.hasSchedule.value).toBe(false)
    expect(att.error.value).toBe('')
  })

  it('成功后 periods / rule / hasSchedule 由 store 快照正确派生', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0), periodOf(1)]))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.periods.value).toHaveLength(2)
    expect(att.rule.value.ruleName).toBe('城东规则')
    expect(att.hasSchedule.value).toBe(true)
    expect(att.error.value).toBe('')
  })
})

describe('useAttendanceStatus · 时段与槽位派生', () => {
  it('空集：进度 0/0、无槽位', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([]))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.doneCount.value).toBe(0)
    expect(att.requireTotal.value).toBe(0)
    expect(att.periodsWithState.value).toEqual([])
  })

  it('每个时段展开 ON / OFF 两个槽位，完成卡数计入进度', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0, { onChecked: true, onTime: '2026-09-24 08:05:00' })]))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.requireTotal.value).toBe(2)
    expect(att.doneCount.value).toBe(1)
    expect(att.periodsWithState.value[0].cells.map((cell) => cell.checkType)).toEqual(['ON', 'OFF'])
    expect(att.periodsWithState.value[0].cells[0].state.key).toBe('done')
  })

  it('字段缺失按「未打卡」处理，不误报过点或过期（极端值边界）', async () => {
    mocks.getStatus.mockResolvedValue(
      statusOf([
        periodOf(0, { startTime: undefined, endTime: undefined, windowStart: undefined, windowEnd: undefined })
      ])
    )
    const att = useAttendanceStatus()

    await att.load()
    att.now.value = new Date('2026-09-24T23:59:00')

    const cells = att.periodsWithState.value[0].cells
    expect(cells[0].state.key).toBe('todo')
    expect(cells[1].state.key).toBe('todo')
  })

  it('窗口已关 → missed；窗口未关但已过规定时刻 → overdue（补打与补卡入口仍并存）', async () => {
    mocks.getStatus.mockResolvedValue(
      statusOf([periodOf(0, { windowEnd: '19:00' }), periodOf(1, { windowEnd: '21:00' })])
    )
    const att = useAttendanceStatus()

    await att.load()
    att.now.value = new Date('2026-09-24T20:30:00')

    const [closed, open] = att.periodsWithState.value
    expect(closed.cells[0].state.key).toBe('missed')
    expect(open.cells[0].state.key).toBe('overdue')
    expect(open.cells[0].state.text).toContain('21:00')
  })

  it('未到开放时间 → wait', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0, { windowStart: '09:00', windowEnd: '10:00' })]))
    const att = useAttendanceStatus()

    await att.load()
    att.now.value = new Date('2026-09-24T08:00:00')

    expect(att.periodsWithState.value[0].cells[0].state.key).toBe('wait')
  })
})

describe('useAttendanceStatus · 待审批补卡回读', () => {
  it('今日待审批的槽位显示「审批中」，其余槽位不受影响', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0)]))
    mocks.getMakeups.mockResolvedValue({ list: [{ periodIndex: 0, checkType: 'ON' }] })
    const att = useAttendanceStatus()

    await att.load()
    // 用例断言「其余槽位不受影响 = 可打（todo）」，故显式固定页面时钟到窗口内、规定时刻前（18:00 前），
    // 否则真实时钟走到 18:00 之后时该槽位会合法变成 overdue，用例随运行时刻翻转（确定性修复，不改断言主张）
    att.now.value = new Date('2026-09-24T10:00:00')

    expect(mocks.getMakeups).toHaveBeenCalledWith({
      status: 'PENDING',
      startDate: '2026-09-24',
      endDate: '2026-09-24',
      pageNum: 1,
      pageSize: 20
    })
    const cells = att.periodsWithState.value[0].cells
    expect(cells[0].state.key).toBe('pending')
    expect(cells[1].state.key).toBe('todo')
  })

  it('回读失败不打断主流程：集合清空而非整页报错', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0)]))
    mocks.getMakeups.mockRejectedValue(new Error('boom'))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.error.value).toBe('')
    expect(att.pendingKeys.value.size).toBe(0)
  })

  it('无工作日期时不回读（边界：状态未就绪）', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([], { workDate: '' }))
    const att = useAttendanceStatus()

    await att.load()

    expect(mocks.getMakeups).not.toHaveBeenCalled()
  })
})

describe('useAttendanceStatus · 规则与定位自查', () => {
  it('未配规则：围栏距离 / 在栏 / 提交坐标均为「未知」，不伪造结论', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([], { rule: null }))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.fenceDistance.value).toBeNull()
    expect(att.inFence.value).toBeFalsy()
    expect(att.submitCoord.value).toEqual({ longitude: null, latitude: null })
  })

  it('规则摘要给三档启停标记与组合模式，未启用项单独说明', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0)], { rule: { ...RULE, enableLocation: false } }))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.ruleChips.value.map((chip) => chip.text)).toEqual(['WiFi', '定位', '时间窗', '组合：任一满足'])
    expect(att.ruleChips.value[1].on).toBe(false)
    expect(att.ruleHints.value.some((hint) => hint.text.includes('不参与判定'))).toBe(true)
  })

  it('演示辅助开启：提交坐标系围栏中心、距离 0、标记转确定态', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0)]))
    const att = useAttendanceStatus()

    await att.load()
    att.toggleAssist()

    expect(att.submitCoord.value).toEqual({ longitude: 120.1, latitude: 30.2 })
    expect(att.fenceDistance.value).toBe(0)
    expect(att.inFence.value).toBe(true)
    expect(att.locateBadge.value.tone).toBe('success')
    expect(att.submitOptions(periodOf(0), 'ON')).toEqual({
      key: '0-ON',
      coordinate: { longitude: 120.1, latitude: 30.2 },
      demoHint: false
    })
  })

  it('演示辅助关闭时按真实定位取坐标，未取到则交服务端判定', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0)]))
    const att = useAttendanceStatus()

    await att.load()

    expect(att.submitCoord.value).toEqual({ longitude: null, latitude: null })
    expect(att.submitOptions(periodOf(0), 'OFF').demoHint).toBe(true)
  })

  it('环境不提供定位能力时给出可操作提示，不静默失败', () => {
    vi.stubGlobal('navigator', {})
    const att = useAttendanceStatus()

    att.locate()

    expect(att.locationError.value).toBe(LOCATE_UNSUPPORTED)
    expect(att.locating.value).toBe(false)
    vi.unstubAllGlobals()
  })

  it('WiFi 模拟值标注来源：非壳环境标记为「模拟」，SSID 空值给占位', async () => {
    mocks.getStatus.mockResolvedValue(statusOf([periodOf(0)]))
    mocks.getWifi.mockReturnValue({ ssid: '', mock: true })
    const att = useAttendanceStatus()

    await att.load()

    expect(att.wifiBadge.value).toEqual({ text: '模拟', tone: 'warning', note: true })
    expect(att.wifiText.value).toBe('未获取到')
    expect(att.wifiHints.value[0].text).toContain('模拟值')
  })
})
