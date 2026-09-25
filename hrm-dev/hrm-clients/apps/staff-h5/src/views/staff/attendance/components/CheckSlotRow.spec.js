// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CheckSlotRow from './CheckSlotRow.vue'

/**
 * 槽位行的操作形态契约：
 * 1. 已打卡不给按钮（重复打卡必被打回），审批中按钮保留但禁用（入口不凭空消失）；
 * 2. 窗口已关改为「申请补卡」；可打 / 过点未打的主操作都是「补打」，补卡只作次入口 —— 优先关系不得改变；
 * 3. 每个按钮的 aria-label 必须含「时段名 + 卡类型」。
 */
const VanButton = {
  name: 'VanButton',
  inheritAttrs: false,
  props: ['disabled', 'loading', 'plain', 'block', 'type', 'size'],
  emits: ['click'],
  template:
    '<button v-bind="$attrs" :disabled="disabled" :class="{ \'is-loading\': !!loading }" @click="$emit(\'click\')"><slot /></button>'
}

const stubs = { 'van-button': VanButton }
const mountRow = (props) => mount(CheckSlotRow, { props, global: { stubs } })

describe('CheckSlotRow · 已打卡与审批中', () => {
  it('已打卡：只留状态文案，不再给点了必错的按钮', () => {
    const wrapper = mountRow({ periodName: '上午班', checkType: 'ON', state: { key: 'done', text: '08:05 已打卡' } })
    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.find('.check-item__state--done').text()).toBe('08:05 已打卡')
  })

  it('审批中：按钮保留但禁用，员工一眼看出这一格已提过单', () => {
    const wrapper = mountRow({ periodName: '上午班', checkType: 'ON', state: { key: 'pending', text: '待审批' } })
    const button = wrapper.find('button')
    expect(button.text()).toBe('审批中')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('check')).toBeUndefined()
  })
})

describe('CheckSlotRow · 主操作与次入口', () => {
  it('窗口已关：主入口换成申请补卡，aria-label 含时段名与卡类型', async () => {
    const wrapper = mountRow({ periodName: '上午班', checkType: 'ON', state: { key: 'missed', text: '已过期' } })
    const button = wrapper.find('button')
    expect(button.text()).toBe('申请补卡')
    expect(button.attributes('aria-label')).toBe('上午班上班卡申请补卡')

    await button.trigger('click')
    expect(wrapper.emitted('makeup')).toHaveLength(1)
    expect(wrapper.emitted('check')).toBeUndefined()
  })

  it('可打：主按钮文案随卡类型变化，下班卡走描边', async () => {
    const on = mountRow({ periodName: '上午班', checkType: 'ON', state: { key: 'todo', text: '未打卡' } })
    expect(on.find('.check-item__btn').text()).toBe('上班打卡')

    const off = mountRow({ periodName: '下午班', checkType: 'OFF', state: { key: 'todo', text: '未打卡' } })
    expect(off.find('.check-item__btn').text()).toBe('下班打卡')
    expect(off.findComponent(VanButton).props('plain')).toBe(true)

    await off.find('.check-item__btn').trigger('click')
    expect(off.emitted('check')).toHaveLength(1)
    expect(off.emitted('makeup')).toBeUndefined()
  })

  it('过点未打：主操作仍是补打，补卡是次入口（优先关系不变）', async () => {
    const wrapper = mountRow({
      periodName: '上午班',
      checkType: 'ON',
      state: { key: 'overdue', text: '已过 08:00 未打卡' }
    })
    expect(wrapper.find('.check-item__btn').text()).toBe('上班打卡')

    const link = wrapper.find('.check-item__link')
    expect(link.text()).toBe('申请补卡')
    await link.trigger('click')

    expect(wrapper.emitted('makeup')).toHaveLength(1)
    expect(wrapper.emitted('check')).toBeUndefined()
  })
})

describe('CheckSlotRow · 提交互斥', () => {
  it('本槽位提交中给 loading 与 aria-busy；任一槽位提交中时按钮禁用', () => {
    const wrapper = mountRow({
      periodName: '上午班',
      checkType: 'ON',
      state: { key: 'todo', text: '未打卡' },
      submitting: true,
      disabled: true
    })
    const button = wrapper.find('.check-item__btn')
    expect(button.classes()).toContain('is-loading')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-busy')).toBe('true')
  })
})
