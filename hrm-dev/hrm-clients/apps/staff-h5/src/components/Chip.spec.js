// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Chip from './Chip.vue'

/**
 * Chip 的交互语义：
 * 1. 选中是视觉变体、由 props 驱动，组件自身不改状态（aria-pressed 必须跟着 active 走，不能各说各话）；
 * 2. 点击只派发事件，由父级决定切哪个筛选值；
 * 3. 图标是可选装饰，不传就不渲染空图标位。
 */
const mountChip = (props) => mount(Chip, { props, global: { stubs: { 'van-icon': true } } })

describe('Chip · 选中态', () => {
  it('默认未选中：aria-pressed 为 false 且无选中类', () => {
    const wrapper = mountChip({ label: '待取件' })
    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.attributes('aria-pressed')).toBe('false')
    expect(wrapper.classes()).not.toContain('chip--active')
  })

  it('active 为 true 时 aria-pressed 与选中类同步', () => {
    const wrapper = mountChip({ label: '待取件', active: true })
    expect(wrapper.attributes('aria-pressed')).toBe('true')
    expect(wrapper.classes()).toContain('chip--active')
  })

  it('tone 与选中态叠加：danger 只加色调类，不改变选中判定', () => {
    const wrapper = mountChip({ label: '超时未处理', tone: 'danger', active: true })
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['chip--danger', 'chip--active']))
  })

  it('非法 tone 不抛错，降级为普通胶囊', () => {
    const wrapper = mountChip({ label: '未知', tone: 'not-a-tone' })
    expect(wrapper.classes()).toContain('chip--not-a-tone')
    expect(wrapper.attributes('aria-pressed')).toBe('false')
  })
})

describe('Chip · 交互与装饰', () => {
  it('点击派发 click，且不携带隐式状态', async () => {
    const wrapper = mountChip({ label: '全部' })
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('连续点击按次数派发，不做单飞去重（去重属父级职责）', async () => {
    const wrapper = mountChip({ label: '全部' })
    await wrapper.trigger('click')
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(2)
  })

  it('不传 icon 不渲染图标位，传了才渲染且图标对读屏隐藏', () => {
    expect(mountChip({ label: '全部' }).find('van-icon-stub').exists()).toBe(false)

    const wrapper = mountChip({ label: '全部', icon: 'filter-o' })
    expect(wrapper.find('van-icon-stub').exists()).toBe(true)
    expect(wrapper.find('van-icon-stub').attributes('aria-hidden')).toBe('true')
  })

  it('空 label 不抛错（边界：接口未下发文案时不至于整页崩）', () => {
    const wrapper = mountChip({ label: '' })
    expect(wrapper.text()).toBe('')
  })
})
