// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MiniChip from '@/components/MiniChip.vue'
import VerifyCard from './VerifyCard.vue'

/**
 * 自查卡骨架的契约（三张卡共用）：
 * 1. 标记走 MiniChip（非交互属性标记），可带 role="note" 标明「模拟值」这类来源说明；
 * 2. 明细行与标记组由数据驱动，空数组不渲染容器（不留空 margin）；
 * 3. 错误行必须是 role="alert"；插槽留给定位卡的「重新定位 + 演示辅助」这类卡专属内容。
 */
const mountCard = (props = {}, slots = {}) => mount(VerifyCard, { props, slots })

describe('VerifyCard · 卡头与主值', () => {
  it('标题与主值渲染；空主值不渲染主值行', () => {
    expect(mountCard({ title: '当前 WiFi', value: 'ZYD-WIFI' }).find('.verify__value').text()).toBe('ZYD-WIFI')
    expect(mountCard({ title: '规则要求' }).find('.verify__value').exists()).toBe(false)
  })

  it('numeric 决定主值是否等宽数字（坐标 / 距离需要对齐）', () => {
    const wrapper = mountCard({ title: '当前定位', value: '120.100000, 30.200000', numeric: true })
    expect(wrapper.find('.verify__value').classes()).toContain('tabular-nums')
    expect(mountCard({ title: '当前定位', value: 'x' }).find('.verify__value').classes()).not.toContain('tabular-nums')
  })

  it('有标记时用 MiniChip，note=true 时带 role=note 说明来源', () => {
    const wrapper = mountCard({ title: '当前 WiFi', badge: { text: '模拟', tone: 'warning', note: true } })
    const chip = wrapper.findComponent(MiniChip)
    expect(chip.exists()).toBe(true)
    expect(chip.props('text')).toBe('模拟')
    expect(chip.props('tone')).toBe('warning')
    expect(chip.attributes('role')).toBe('note')
  })

  it('无标记时用 headExtra 渲染卡头右侧说明（如规则名）', () => {
    const wrapper = mountCard({ title: '规则要求', headExtra: '城东规则' })
    expect(wrapper.find('.verify__hint').text()).toBe('城东规则')
    expect(wrapper.findComponent(MiniChip).exists()).toBe(false)
  })
})

describe('VerifyCard · 明细行与标记组', () => {
  it('明细行支持字符串与对象两种写法，对象带 numeric 时加等宽类', () => {
    const wrapper = mountCard({
      title: '当前定位',
      hints: ['由安卓壳读取的真实 WiFi', { text: '距围栏中心 12 米', numeric: true }]
    })
    const hints = wrapper.findAll('.verify__hint')
    expect(hints[0].text()).toBe('由安卓壳读取的真实 WiFi')
    expect(hints[1].text()).toBe('距围栏中心 12 米')
    expect(hints[1].classes()).toContain('tabular-nums')
  })

  it('明细行为空时不渲染任何 hint（不留空行）', () => {
    expect(mountCard({ title: '当前 WiFi', hints: [] }).findAll('.verify__hint')).toHaveLength(0)
  })

  it('标记组为空时不渲染容器；有值时按 on / tone 渲染', () => {
    expect(mountCard({ title: '规则要求', chips: [] }).find('.verify__chips').exists()).toBe(false)

    const wrapper = mountCard({
      title: '规则要求',
      chips: [
        { text: 'WiFi', on: true, tone: 'primary' },
        { text: '定位', on: false }
      ]
    })
    const chips = wrapper.findAllComponents(MiniChip)
    expect(chips).toHaveLength(2)
    expect(chips[0].props('on')).toBe(true)
    expect(chips[1].props('on')).toBe(false)
  })
})

describe('VerifyCard · 错误行与插槽', () => {
  it('错误行是 role=alert 的独立提示（如定位失败原因）', () => {
    const wrapper = mountCard({ title: '当前定位', errorText: '定位未授权' })
    const error = wrapper.find('.verify__error')
    expect(error.attributes('role')).toBe('alert')
    expect(error.text()).toBe('定位未授权')
    expect(mountCard({ title: '当前定位' }).find('.verify__error').exists()).toBe(false)
  })

  it('插槽渲染卡专属内容（定位卡的重新定位按钮与演示辅助行）', () => {
    const wrapper = mountCard({ title: '当前定位' }, { default: '<button class="verify__btn">重新定位</button>' })
    expect(wrapper.find('.verify__btn').exists()).toBe(true)
  })
})
