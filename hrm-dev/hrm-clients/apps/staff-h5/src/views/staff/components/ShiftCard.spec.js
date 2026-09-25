// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ShiftCard from './ShiftCard.vue'

/**
 * ShiftCard 的两条契约：
 * 1. 色条颜色只能来自「契约色/语义键 → Token」映射，未命中回落分隔线色
 *    —— 接口下发的 hex 直出会把整条色条带出色板（P1-1）；
 * 2. 时间窗与休息时长是可选信息，缺失就不渲染该段，不显示「休息 0 分钟」这类噪音。
 */
const mountCard = (props) => mount(ShiftCard, { props })
const barStyle = (wrapper) => wrapper.find('.shift-card__bar').attributes('style') || ''

describe('ShiftCard · 色条映射', () => {
  it('契约色命中映射表，落到 Token 而非 hex', () => {
    const wrapper = mountCard({ shiftName: '早班', colorKey: '#0958D9' })
    expect(barStyle(wrapper)).toContain('var(--color-primary)')
    expect(barStyle(wrapper)).not.toContain('#0958D9')
  })

  it('小写契约色同样命中（接口大小写不固定）', () => {
    expect(barStyle(mountCard({ shiftName: '午班', colorKey: '#fa8c16' }))).toContain('var(--color-accent)')
  })

  it('语义键命中映射表', () => {
    expect(barStyle(mountCard({ shiftName: '夜班', colorKey: 'neutral' }))).toContain('var(--c-neutral-800)')
  })

  it('未命中与空值一律回落分隔线色，不留透明色条', () => {
    for (const colorKey of ['#123456', '', undefined]) {
      expect(barStyle(mountCard({ shiftName: '班次', colorKey }))).toContain('var(--border-line)')
    }
  })

  it('色条对读屏隐藏（班次名已承载语义，避免重复播报）', () => {
    expect(
      mountCard({ shiftName: '早班', colorKey: 'primary' }).find('.shift-card__bar').attributes('aria-hidden')
    ).toBe('true')
  })
})

describe('ShiftCard · 时间窗与休息时长', () => {
  it('起止时间均有值时渲染区间', () => {
    const wrapper = mountCard({ shiftName: '早班', startTime: '08:00', endTime: '12:00' })
    expect(wrapper.find('.shift-card__time').text()).toBe('08:00 - 12:00')
  })

  it('只给开始时间时只渲染该值（边界：班次末段未定）', () => {
    expect(mountCard({ shiftName: '早班', startTime: '08:00' }).find('.shift-card__time').text()).toBe('08:00')
  })

  it('时间全缺时不渲染时间行', () => {
    expect(mountCard({ shiftName: '早班' }).find('.shift-card__time').exists()).toBe(false)
  })

  it('休息时长 > 0 才渲染，0 / 负数 / 非法值都不渲染', () => {
    // 休息时长挂在时间窗行内（与 attendance.vue:364 / schedule.vue:97 原口径一致），
    // 故断言时必须给出时间窗，否则测到的是「时间行未渲染」而不是「休息时长被过滤」
    const withTimes = (restMinutes) =>
      mountCard({ shiftName: '早班', startTime: '08:00', endTime: '12:00', restMinutes })
    expect(withTimes(30).text()).toContain('休息 30 分钟')
    expect(withTimes('60').text()).toContain('休息 60 分钟')
    for (const restMinutes of [0, -5, 'abc', null]) {
      expect(withTimes(restMinutes).text()).not.toContain('休息')
    }
  })

  it('班次名为空不抛错（边界：契约缺字段时不至于整页崩）', () => {
    expect(mountCard({ shiftName: '' }).find('.shift-card').exists()).toBe(true)
  })
})
