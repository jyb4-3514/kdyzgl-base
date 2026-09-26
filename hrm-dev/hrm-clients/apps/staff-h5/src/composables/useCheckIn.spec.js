import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'

/**
 * 打卡提交的 WiFi 口径（安全评估 A 档，见 docs/security-wifi-checkin-bypass-review.md）：
 * 1. 未取到真实 WiFi（mock:true）→ 不回填任何 SSID，提交体 wifiSsid 必须为 null（A-1）；
 * 2. 不再把「规则白名单首项」当提交值（消除白名单泄露 → 打卡绕过的自动链路）（A-3）；
 * 3. 壳侧真实读取（mock:false 且 ssid 非空）→ 正常提交该 SSID（回归：正路不得修坏）；
 * 4. 未取到而 9103 时，提示指向需安装客户端（关联 A-5）。
 * DEMO_ENABLED 在单测环境为 false（无 VITE_MOCK_ENABLED）= 生产口径，故上述断言即生产行为。
 */
const mocks = vi.hoisted(() => ({
  checkIn: vi.fn(),
  getWifi: vi.fn()
}))

vi.mock('../api/attendance.js', () => ({ checkIn: mocks.checkIn }))
vi.mock('../utils/bridge.js', () => ({ getWifiInfo: mocks.getWifi }))
vi.mock('vant', () => ({ showSuccessToast: vi.fn() }))

const { useCheckIn } = await import('./useCheckIn.js')

const PERIOD = {
  periodIndex: 0,
  name: '上午班',
  startTime: '08:00',
  endTime: '12:00',
  windowStart: '07:30',
  windowEnd: '13:00'
}
const RULE = {
  wifiList: [{ ssid: 'ZYD-WIFI' }],
  longitude: 120.1,
  latitude: 30.2,
  radius: 200,
  allowEarlyMin: 30,
  allowLateMin: 60
}
const COORD = { longitude: 120.1, latitude: 30.2 }
const VO = { periodName: '上午班', status: 'NORMAL', checkTime: '2026-09-26 08:05:00' }

/** 取本次提交体的 wifiSsid 字段（断言口径落点） */
const submittedWifiSsid = () => mocks.checkIn.mock.calls[0][0].wifiSsid

beforeEach(() => {
  vi.clearAllMocks()
  mocks.checkIn.mockResolvedValue(VO)
})

describe('useCheckIn · WiFi 提交口径（A 档）', () => {
  it('未取到真实 WiFi（mock:true）→ 不回填，提交体 wifiSsid 为 null（A-1）', async () => {
    mocks.getWifi.mockReturnValue({ ssid: '', bssid: '', mock: true })
    const { submit } = useCheckIn()

    await submit(PERIOD, 'ON', RULE, { coordinate: COORD })

    expect(submittedWifiSsid()).toBeNull()
  })

  it('边界：mock:true 却带 ssid（异常来源）→ 仍不回填，提交 null（A-1）', async () => {
    mocks.getWifi.mockReturnValue({ ssid: 'ZYD-WIFI', mock: true })
    const { submit } = useCheckIn()

    await submit(PERIOD, 'ON', RULE, { coordinate: COORD })

    expect(submittedWifiSsid()).toBeNull()
  })

  it('生产口径不把规则白名单首项当提交值（白名单只作展示，不作当前 WiFi）（A-3）', async () => {
    mocks.getWifi.mockReturnValue({ ssid: '', mock: true })
    const { submit } = useCheckIn()

    await submit(PERIOD, 'ON', RULE, { coordinate: COORD })

    expect(submittedWifiSsid()).not.toBe('ZYD-WIFI')
    // getWifiInfo 不得再收到「白名单首项」作为模拟来源（调用不带参数）
    expect(mocks.getWifi).toHaveBeenCalledWith()
  })

  it('壳侧真实读取（mock:false 且 ssid 非空）→ 正常提交该 SSID（回归）', async () => {
    mocks.getWifi.mockReturnValue({ ssid: 'ZYD-WIFI', bssid: '', mock: false })
    const { submit } = useCheckIn()

    await submit(PERIOD, 'ON', RULE, { coordinate: COORD })

    expect(submittedWifiSsid()).toBe('ZYD-WIFI')
  })

  it('未取到 WiFi 导致 9103 时，提示指向需安装客户端（关联 A-5）', async () => {
    mocks.getWifi.mockReturnValue({ ssid: '', mock: true })
    mocks.checkIn.mockRejectedValue(Object.assign(new Error('WiFi 校验未通过'), { code: ATTENDANCE_CODE.WIFI_MISMATCH }))
    const { submit, result } = useCheckIn()

    await submit(PERIOD, 'ON', RULE, { coordinate: COORD })

    expect(result.value.ok).toBe(false)
    expect(result.value.code).toBe(ATTENDANCE_CODE.WIFI_MISMATCH)
    expect(result.value.hint).toContain('需安装客户端（安卓壳）才能完成 WiFi 校验打卡')
  })
})
