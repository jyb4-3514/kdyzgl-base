// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ATTENDANCE_CODE } from '@/shared/constants/errorCode.js'
import CheckResultPanel from './CheckResultPanel.vue'

/**
 * 判定结果的两条硬规则：
 * 1. role 按结果切换 status / alert —— 失败必须立刻被读屏读到；
 * 2. 「已落异常卡」只对 WiFi / 定位类失败提示，超窗、重复卡不能套用同一句话。
 */
const mountPanel = (result) => mount(CheckResultPanel, { props: { result }, global: { stubs: { 'van-icon': true } } })

describe('CheckResultPanel · 成功', () => {
  it('role=status，标题含时段名与卡类型，并挂业务状态胶囊', () => {
    const wrapper = mountPanel({
      ok: true,
      periodName: '上午班',
      checkType: 'ON',
      status: 'NORMAL',
      checkTime: '2026-09-24 08:05:00',
      checkMode: 'WIFI',
      distance: 12
    })
    expect(wrapper.attributes('role')).toBe('status')
    expect(wrapper.text()).toContain('打卡成功 · 上午班 · 上班卡')
    expect(wrapper.text()).toContain('2026-09-24 08:05:00')
    expect(wrapper.text()).toContain('命中 WiFi')
    expect(wrapper.find('.status-tag').exists()).toBe(true)
  })

  it('remark 有值才追加到明细行（无值不出现分隔符尾巴）', () => {
    const base = {
      ok: true,
      periodName: '上午班',
      checkType: 'ON',
      status: 'LATE',
      checkTime: '08:20',
      checkMode: 'LOCATION'
    }
    expect(mountPanel({ ...base, remark: '设备正常' }).text()).toContain('· 设备正常')
    expect(mountPanel(base).text()).not.toContain('设备正常')
  })

  it('距离未知时给「未知」而非空白（边界：定位取不到）', () => {
    const wrapper = mountPanel({
      ok: true,
      periodName: '上午班',
      checkType: 'ON',
      status: 'NORMAL',
      checkTime: '08:05',
      checkMode: 'WIFI',
      distance: null
    })
    expect(wrapper.text()).toContain('距围栏 未知')
  })

  it('时段名为空时退回卡类型文案（边界：多时段数据缺失）', () => {
    const wrapper = mountPanel({
      ok: true,
      periodName: '',
      checkType: 'OFF',
      status: 'NORMAL',
      checkTime: '18:00',
      checkMode: 'WIFI'
    })
    expect(wrapper.text()).toContain('打卡成功 · 下班卡')
  })
})

describe('CheckResultPanel · 失败', () => {
  it('role=alert，就近给出针对性提示，且不渲染成功胶囊与留痕说明', () => {
    const wrapper = mountPanel({
      ok: false,
      periodName: '上午班',
      checkType: 'ON',
      code: ATTENDANCE_CODE.OUT_OF_TIME_WINDOW,
      hint: '当前不在打卡时间窗内'
    })
    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.text()).toContain('打卡未通过')
    expect(wrapper.text()).toContain('当前不在打卡时间窗内')
    expect(wrapper.find('.status-tag').exists()).toBe(false)
    expect(wrapper.find('.result__foot').exists()).toBe(false)
  })

  it('仅 WiFi / 定位校验未通过才提示「已落异常卡」', () => {
    for (const code of [ATTENDANCE_CODE.WIFI_MISMATCH, ATTENDANCE_CODE.LOCATION_MISMATCH]) {
      expect(mountPanel({ ok: false, code, hint: 'x' }).find('.result__foot').exists()).toBe(true)
    }
    for (const code of [ATTENDANCE_CODE.OUT_OF_TIME_WINDOW, ATTENDANCE_CODE.DUPLICATE_CHECK]) {
      expect(mountPanel({ ok: false, code, hint: 'x' }).find('.result__foot').exists()).toBe(false)
    }
  })
})
