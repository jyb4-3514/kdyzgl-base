// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MiniChip from './MiniChip.vue'

/**
 * MiniChip 是「非交互属性标记」，只表达静态两态：
 * on=false 中性描边（未启用），on=true 走语义浅底（已启用）。
 * 它不是 StatusTag 的替代品（后者表达业务状态流转），也不是可点 chip（后者是 Chip）。
 */
const mountChip = (props) => mount(MiniChip, { props })

describe('MiniChip · 静态两态', () => {
  it('未启用：中性描边，取中性文本色', () => {
    const wrapper = mountChip({ text: '规则已配' })
    expect(wrapper.text()).toBe('规则已配')
    expect(wrapper.classes()).not.toContain('mini-chip--on')
    expect(wrapper.attributes('style')).toContain('--text-2')
  })

  it('已启用：按 tone 取语义浅底与前景', () => {
    const wrapper = mountChip({ text: '规则已配', on: true, tone: 'success' })
    expect(wrapper.classes()).toContain('mini-chip--on')
    expect(wrapper.attributes('style')).toContain('var(--state-success-fg)')
    expect(wrapper.attributes('style')).toContain('var(--state-success-bg)')
  })

  it('四种 tone 都能映射到既有 --state-* 变量', () => {
    for (const tone of ['neutral', 'success', 'warning', 'danger']) {
      const wrapper = mountChip({ text: '标记', on: true, tone })
      expect(wrapper.attributes('style')).toContain(`var(--state-${tone}-fg)`)
    }
  })

  it('on 缺省（未传入）按未启用渲染，不伪造「已启用」', () => {
    expect(mountChip({ text: '标记' }).classes()).not.toContain('mini-chip--on')
  })

  it('非法 tone 不抛错（样式变量解析失败由浏览器回落继承色）', () => {
    const wrapper = mountChip({ text: '标记', on: true, tone: 'not-a-tone' })
    expect(wrapper.classes()).toContain('mini-chip--on')
    expect(wrapper.attributes('style')).toContain('var(--state-not-a-tone-fg)')
  })

  it('本身不可聚焦、不承载点击语义（属性标记而非控件）', () => {
    const wrapper = mountChip({ text: '标记', on: true })
    expect(wrapper.element.tagName).toBe('SPAN')
    expect(wrapper.attributes('tabindex')).toBeUndefined()
    expect(wrapper.attributes('role')).toBeUndefined()
  })
})
