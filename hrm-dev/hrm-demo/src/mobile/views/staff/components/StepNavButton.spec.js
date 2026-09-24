// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StepNavButton from './StepNavButton.vue'

/**
 * StepNavButton 的三条契约：
 * 1. 点击只派发一次 click 事件（父级据此做上一月/下一月、上一周/下一周平移）；
 * 2. disabled 时既是原生 disabled，也吞掉点击 —— 双保险，防属性透传失效造出假按钮；
 * 3. aria-label 由调用方透传到根 button，读屏名不依赖组件内部写死。
 */
describe('StepNavButton · 交互语义', () => {
  it('点击派发一次 click 事件', async () => {
    const wrapper = mount(StepNavButton)
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('根元素是 type=button，默认插槽承载箭头图标', () => {
    const wrapper = mount(StepNavButton, { slots: { default: '<span class="arrow" />' } })
    expect(wrapper.find('button').attributes('type')).toBe('button')
    expect(wrapper.find('.arrow').exists()).toBe(true)
  })
})

describe('StepNavButton · 禁用态', () => {
  it('disabled 落到原生属性', () => {
    const wrapper = mount(StepNavButton, { props: { disabled: true } })
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('disabled 时点击不派发事件（不造「看着能点、点了没反应」的假按钮）', async () => {
    const wrapper = mount(StepNavButton, { props: { disabled: true } })
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })
})

describe('StepNavButton · 无障碍', () => {
  it('aria-label 透传到根 button（文案由调用方给，如「上一月」）', () => {
    const wrapper = mount(StepNavButton, { attrs: { 'aria-label': '上一月' } })
    expect(wrapper.find('button').attributes('aria-label')).toBe('上一月')
  })
})
