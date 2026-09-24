import { describe, expect, it } from 'vitest'
import {
  CHECK_TYPES,
  MAKEUP_REASON_MAX,
  MAKEUP_REASON_MIN,
  MORE_LINKS,
  fenceHintsOf,
  locateBadgeOf,
  periodDoneCount,
  positionTextOf,
  ruleChipsOf,
  ruleHintsOf,
  shiftProps,
  slotKey,
  slotText,
  wifiBadgeOf,
  wifiHintsOf,
  wifiTextOf
} from './attendanceUi.js'

/**
 * model 是纯常量与纯函数，直测三件事：
 * 1. 槽位定位键与完成卡数（补卡唯一性与进度口径的页面表达）；
 * 2. 状态文案含插值时不能因字段缺失抛错（接口字段可空）；
 * 3. 展示映射（班次 props / 就近入口）不得随重构改名或改路径。
 */
describe('attendanceUi · 状态键与槽位定位键', () => {
  it('槽位键 = 时段序号 + 卡类型', () => {
    expect(slotKey(0, 'ON')).toBe('0-ON')
    expect(slotKey(3, 'OFF')).toBe('3-OFF')
  })

  it('卡类型只有 ON / OFF 两种', () => {
    expect(CHECK_TYPES).toEqual(['ON', 'OFF'])
  })
})

describe('attendanceUi · 时段完成卡数', () => {
  it('0 / 1 / 2 三档正确', () => {
    expect(periodDoneCount({ onChecked: false, offChecked: false })).toBe(0)
    expect(periodDoneCount({ onChecked: true, offChecked: false })).toBe(1)
    expect(periodDoneCount({ onChecked: true, offChecked: true })).toBe(2)
  })

  it('时段缺失时不抛错（空集边界：接口未下发时段）', () => {
    expect(periodDoneCount(null)).toBe(0)
    expect(periodDoneCount(undefined)).toBe(0)
    expect(periodDoneCount({})).toBe(0)
  })
})

describe('attendanceUi · 状态文案', () => {
  it('已打卡带时刻；时刻缺失时只留「已打卡」，不出现 undefined', () => {
    expect(slotText.done('2026-09-24 08:05:00')).toBe('08:05 已打卡')
    expect(slotText.done(null)).toBe(' 已打卡')
  })

  it('过点未打同时给出规定时刻与补打截止时刻', () => {
    expect(slotText.overdue('08:00', '19:00')).toBe('已过 08:00 未打卡（19:00 前可补打）')
  })

  it('未到时间给出开放时刻', () => {
    expect(slotText.wait('07:30')).toContain('07:30')
  })

  it('审批中与过期两种文案不含插值，直接给出确定结论', () => {
    expect(slotText.pending()).toContain('待老板审批')
    expect(slotText.missed()).toContain('待补卡')
  })
})

describe('attendanceUi · 展示常量', () => {
  it('补卡理由门槛为 2-200 字', () => {
    expect(MAKEUP_REASON_MIN).toBe(2)
    expect(MAKEUP_REASON_MAX).toBe(200)
  })

  it('就近入口三条且路径固定（底部按钮不随重构改名）', () => {
    expect(MORE_LINKS.map((link) => link.label)).toEqual(['我的排班', '打卡记录', '补卡申请'])
    expect(MORE_LINKS.map((link) => link.to)).toEqual([
      '/staff/schedule',
      '/staff/attendance/records',
      '/staff/attendance/makeup'
    ])
  })
})

describe('attendanceUi · 班次 props 映射', () => {
  it('契约字段 color 落到组件 colorKey（不把接口字段名泄进组件）', () => {
    const mapped = shiftProps({
      shiftName: '早班',
      startTime: '08:00',
      endTime: '18:00',
      restMinutes: 60,
      color: '#0958D9'
    })
    expect(mapped).toEqual({
      shiftName: '早班',
      startTime: '08:00',
      endTime: '18:00',
      restMinutes: 60,
      colorKey: '#0958D9'
    })
  })

  it('班次缺失时返回空字段而不抛错（边界：无排班）', () => {
    expect(shiftProps(null).shiftName).toBeUndefined()
    expect(shiftProps(undefined).colorKey).toBeUndefined()
  })
})

describe('attendanceUi · 自查卡纯映射', () => {
  const RULE = {
    enableWifi: true,
    enableLocation: false,
    enableTimeWindow: true,
    longitude: 120.1,
    latitude: 30.2,
    radius: 200,
    lateThresholdMin: 5,
    earlyLeaveThresholdMin: 5,
    allowEarlyMin: 30,
    allowLateMin: 60
  }

  it('WiFi：模拟值必须带「模拟」标记，非模拟不给标记', () => {
    expect(wifiBadgeOf({ ssid: 'X', mock: true })).toEqual({ text: '模拟', tone: 'warning', note: true })
    expect(wifiBadgeOf({ ssid: 'X', mock: false })).toBeNull()
    expect(wifiTextOf({ ssid: '', mock: true })).toBe('未获取到')
    expect(wifiHintsOf({ ssid: 'X', mock: false }, '')).toEqual([
      { text: '由安卓壳读取的真实 WiFi' },
      { text: '规则白名单：未配置' }
    ])
  })

  it('定位主值：演示辅助优先取围栏中心，无定位时给「尚未获取到」', () => {
    expect(positionTextOf(RULE, null, true)).toBe('120.100000, 30.200000')
    expect(positionTextOf(RULE, { longitude: 1.5, latitude: 2.5 }, false)).toBe('1.500000, 2.500000')
    expect(positionTextOf(RULE, null, false)).toBe('尚未获取到定位')
  })

  it('定位标记：演示辅助与围栏内均为确定态，超出围栏为 warn', () => {
    expect(locateBadgeOf(true, false)).toEqual({ text: '在围栏内', tone: 'success' })
    expect(locateBadgeOf(false, true).text).toBe('演示辅助 · 围栏中心')
    expect(locateBadgeOf(false, false)).toEqual({ text: '超出围栏', tone: 'danger' })
  })

  it('围栏明细：未配规则不给明细（不伪造围栏）', () => {
    expect(fenceHintsOf(null, null, null, false)).toEqual([])
    const hints = fenceHintsOf(RULE, 0, { accuracy: 12 }, false)
    expect(hints[0].text).toContain('允许半径 200 米')
    expect(hints[1].text).toContain('定位精度约 12 米')
  })

  it('规则标记：三档启停 + 组合模式，未启用项按 off 渲染', () => {
    const chips = ruleChipsOf(RULE, '全部满足')
    expect(chips.map((chip) => chip.text)).toEqual(['WiFi', '定位', '时间窗', '组合：全部满足'])
    expect(chips.map((chip) => chip.on)).toEqual([true, false, true, true])
    expect(ruleChipsOf(null, '-')).toEqual([])
  })

  it('规则说明：纳入接口下发的摘要，未启用校验项单独补一句', () => {
    const hints = ruleHintsOf(RULE, '每日 2 次打卡').map((hint) => hint.text)
    expect(hints[0]).toBe('每日 2 次打卡')
    expect(hints.some((text) => text.includes('不参与判定'))).toBe(true)
    expect(ruleHintsOf(null, 'x')).toEqual([])
  })
})
