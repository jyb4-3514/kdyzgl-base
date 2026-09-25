// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MakeupPopup from './MakeupPopup.vue'

/**
 * 补卡弹层的契约：
 * 1. 日期 / 时段 / 卡类型只读展示（由接口数据带齐），员工只填理由；
 * 2. 理由 2-200 字：下限由提交按钮 disabled 表达，上限由 maxlength 截断；
 * 3. 提交失败在弹层内 role="alert" 渲染，弹层不自动关闭。
 */
const VanPopup = {
  name: 'VanPopup',
  props: ['show'],
  emits: ['update:show'],
  template: '<div v-if="show" class="popup-stub"><slot /></div>'
}
const VanField = {
  name: 'VanField',
  inheritAttrs: false,
  props: ['modelValue', 'maxlength'],
  emits: ['update:modelValue'],
  template:
    '<textarea v-bind="$attrs" :maxlength="maxlength" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
}
const VanButton = {
  name: 'VanButton',
  inheritAttrs: false,
  props: ['disabled', 'loading', 'block', 'type'],
  emits: ['click'],
  template:
    '<button v-bind="$attrs" :disabled="disabled" :class="{ \'is-loading\': !!loading }" @click="$emit(\'click\')"><slot /></button>'
}

const VanCell = {
  name: 'VanCell',
  props: ['title', 'value'],
  template: '<div class="cell">{{ title }}：{{ value }}</div>'
}

const VanCellGroup = {
  name: 'VanCellGroup',
  template: '<div class="cell-group"><slot /></div>'
}

const stubs = {
  'van-popup': VanPopup,
  'van-field': VanField,
  'van-button': VanButton,
  'van-cell': VanCell,
  'van-cell-group': VanCellGroup
}

const TARGET = { workDate: '2026-09-24', periodName: '下午班', checkType: 'OFF' }
const mountPop = (props = {}) => mount(MakeupPopup, { props: { target: TARGET, ...props }, global: { stubs } })

describe('MakeupPopup · 开关与只读上下文', () => {
  it('未打开时不渲染内容', () => {
    expect(mountPop({ show: false }).find('.makeup-pop').exists()).toBe(false)
  })

  it('打开后渲染只读的日期 / 时段 / 卡类型（卡类型按字典转中文）', () => {
    const wrapper = mountPop({ show: true })
    expect(wrapper.find('.makeup-pop__title').text()).toBe('申请补卡')
    const cells = wrapper.findAll('.cell').map((cell) => cell.text())
    expect(cells).toEqual(['补卡日期：2026-09-24', '打卡时段：下午班', '卡类型：下班卡'])
  })

  it('van-popup 的关闭事件原样上抛，由调用方决定 v-model', async () => {
    const wrapper = mountPop({ show: true })
    wrapper.findComponent(VanPopup).vm.$emit('update:show', false)
    expect(wrapper.emitted('update:show')[0]).toEqual([false])
  })
})

describe('MakeupPopup · 理由校验', () => {
  it('理由框 maxlength 为 200，输入上抛 update:reason', async () => {
    const wrapper = mountPop({ show: true })
    const field = wrapper.find('textarea')
    expect(field.attributes('maxlength')).toBe('200')

    field.element.value = '外出取件'
    await field.trigger('input')
    expect(wrapper.emitted('update:reason')[0]).toEqual(['外出取件'])
  })

  it('不足 2 字时提交按钮禁用；满足后点击上抛 submit', async () => {
    const disabled = mountPop({ show: true, reason: 'x', canSubmit: false })
    expect(disabled.find('button').attributes('disabled')).toBeDefined()

    const ready = mountPop({ show: true, reason: '外出取件', canSubmit: true })
    const button = ready.find('button')
    expect(button.attributes('disabled')).toBeUndefined()
    await button.trigger('click')
    expect(ready.emitted('submit')).toHaveLength(1)
  })

  it('提交中按钮进入 loading（防连点；重复提交由 composable 拦截）', () => {
    const wrapper = mountPop({ show: true, reason: '外出取件', canSubmit: true, submitting: true })
    const button = wrapper.find('button')
    expect(button.classes()).toContain('is-loading')
  })
})

describe('MakeupPopup · 失败就地反馈', () => {
  it('错误在弹层内以 role=alert 渲染，弹层保持打开', () => {
    const wrapper = mountPop({ show: true, error: '该时段当日已有补卡申请；可到「我的补卡申请」查看审批进度' })
    const error = wrapper.find('.makeup-pop__error')
    expect(error.attributes('role')).toBe('alert')
    expect(error.text()).toContain('我的补卡申请')
    expect(wrapper.find('.makeup-pop').exists()).toBe(true)
  })

  it('无错误时不渲染错误行（不留空行）', () => {
    expect(mountPop({ show: true }).find('.makeup-pop__error').exists()).toBe(false)
  })
})
