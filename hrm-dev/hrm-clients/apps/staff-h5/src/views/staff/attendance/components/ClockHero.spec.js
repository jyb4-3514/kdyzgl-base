// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ClockHero from './ClockHero.vue'

/**
 * 时钟恒有值，只保证两件事：
 * 1. role="timer" + aria-label 供读屏取到当前时间（秒级刷新不做 aria-live 播报，沿用原页取舍）；
 * 2. 日期与驿站拼一行，驿站缺失时不出现 undefined。
 */
const mountHero = (props) => mount(ClockHero, { props })

describe('ClockHero · 时钟与归属', () => {
  it('时钟按 HH:mm:ss 渲染，并给出 role=timer 与读屏文案', () => {
    const wrapper = mountHero({ time: '08:05:09' })
    const clock = wrapper.find('.clock__time')
    expect(clock.text()).toBe('08:05:09')
    expect(clock.attributes('role')).toBe('timer')
    expect(clock.attributes('aria-label')).toBe('当前时间 08:05:09')
  })

  it('日期与驿站拼一行展示', () => {
    const wrapper = mountHero({ time: '08:05:09', dateText: '2026-09-24 周四', stationName: '城东驿站' })
    expect(wrapper.find('.clock__date').text()).toBe('2026-09-24 周四 · 城东驿站')
  })

  it('未挂驿站时兜底「未归属驿站」，不显示空白或 undefined（边界：演示账号未归属）', () => {
    const wrapper = mountHero({ time: '08:05:09', dateText: '2026-09-24 周四', stationName: '' })
    expect(wrapper.find('.clock__date').text()).toBe('2026-09-24 周四 · 未归属驿站')
  })

  it('字段全缺时不抛错，也不渲染 undefined（边界：接口未就绪）', () => {
    const wrapper = mountHero({})
    expect(wrapper.find('.clock').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('undefined')
  })
})
