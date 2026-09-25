// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Badge from './Badge.vue'

/**
 * Badge 的数值口径（与 utils/format.js#badgeText 同一份判定）：
 * 0 / 空 / 非数一律不渲染 —— 0 是「没有待办」的确定结论，取数失败也不渲染，
 * 两者都不许用 0 或感叹号冒充未知。
 */
const mountBadge = (props) => mount(Badge, { props })

describe('Badge · 数值渲染', () => {
  it('正常值原样渲染', () => {
    const wrapper = mountBadge({ value: 5 })
    expect(wrapper.text()).toBe('5')
    expect(wrapper.attributes('aria-hidden')).toBe('true')
  })

  it('边界：99 不截断，100 起显示 99+', () => {
    expect(mountBadge({ value: 99 }).text()).toBe('99')
    expect(mountBadge({ value: 100 }).text()).toBe('99+')
    expect(mountBadge({ value: 9999 }).text()).toBe('99+')
  })

  it('0 与负数不渲染：0 是结论不是「未知」，不许出现在角标上', () => {
    expect(mountBadge({ value: 0 }).find('.badge').exists()).toBe(false)
    expect(mountBadge({ value: -3 }).find('.badge').exists()).toBe(false)
  })

  it('非法输入不渲染：null / 空串 / 非数文本 / NaN', () => {
    for (const value of [null, '', 'abc', NaN]) {
      expect(mountBadge({ value }).find('.badge').exists()).toBe(false)
    }
  })

  it('数字字符串按数字处理（接口常回传字符串计数值）', () => {
    expect(mountBadge({ value: '12' }).text()).toBe('12')
    expect(mountBadge({ value: '0' }).find('.badge').exists()).toBe(false)
  })
})

describe('Badge · 圆点变体', () => {
  it('dot 变体不渲染数字，且与取值无关', () => {
    const wrapper = mountBadge({ dot: true, value: 0 })
    expect(wrapper.text()).toBe('')
    expect(wrapper.classes()).toContain('badge--dot')
    expect(wrapper.find('.badge').exists()).toBe(true)
  })

  it('非 dot 变体即使无值也不占位（父级留空即可，不显示 0）', () => {
    expect(mountBadge({ dot: false, value: null }).find('.badge').exists()).toBe(false)
  })
})
