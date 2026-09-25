// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CheckResultPanel from './CheckResultPanel.vue'
import CheckSlotRow from './CheckSlotRow.vue'
import PeriodCard from './PeriodCard.vue'

/**
 * 时段卡的两条契约：
 * 1. 判定结果只渲染在被操作的本卡内（不得跑到页顶或串到相邻时段）；
 * 2. loading 只给被点的那一格（按槽位键匹配），其余格子仅禁用，避免整列一起转圈。
 */
const VanButton = {
  name: 'VanButton',
  inheritAttrs: false,
  props: ['disabled', 'loading', 'plain', 'block', 'type', 'size'],
  emits: ['click'],
  template:
    '<button v-bind="$attrs" :disabled="disabled" :class="{ \'is-loading\': !!loading }" @click="$emit(\'click\')"><slot /></button>'
}

const PERIOD = {
  periodIndex: 0,
  name: '上午班',
  startTime: '08:00',
  endTime: '12:00',
  onChecked: true,
  offChecked: false,
  cells: [
    { checkType: 'ON', state: { key: 'done', text: '08:05 已打卡' } },
    { checkType: 'OFF', state: { key: 'todo', text: '未打卡' } }
  ]
}
const RESULT = {
  ok: true,
  periodName: '上午班',
  checkType: 'ON',
  status: 'NORMAL',
  checkTime: '08:05',
  checkMode: 'WIFI',
  distance: 12
}

const mountCard = (props = {}) =>
  mount(PeriodCard, {
    props: { period: PERIOD, windowText: '07:30 - 13:00', ...props },
    global: { stubs: { 'van-button': VanButton, 'van-icon': true } }
  })

describe('PeriodCard · 表头与时段说明', () => {
  it('渲染时段名、按已打卡数算出的进度，以及接口下发的时间窗', () => {
    const wrapper = mountCard()
    expect(wrapper.text()).toContain('上午班')
    expect(wrapper.text()).toContain('1/2 已完成')
    expect(wrapper.text()).toContain('08:00 - 12:00 · 可打卡 07:30 - 13:00')
  })

  it('时间窗文案缺失时给占位，不留空白（边界：接口未下发 windowStart/End）', () => {
    const wrapper = mountCard({ windowText: '-' })
    expect(wrapper.text()).toContain('可打卡 -')
  })
})

describe('PeriodCard · 槽位与事件', () => {
  it('每个卡类型渲染一个槽位行', () => {
    expect(mountCard().findAllComponents(CheckSlotRow)).toHaveLength(2)
  })

  it('槽位事件带卡类型冒泡给壳', async () => {
    const wrapper = mountCard()
    const rows = wrapper.findAllComponents(CheckSlotRow)
    await rows[0].vm.$emit('check')
    await rows[1].vm.$emit('makeup')
    expect(wrapper.emitted('check')[0]).toEqual(['ON'])
    expect(wrapper.emitted('makeup')[0]).toEqual(['OFF'])
  })

  it('只给被点的槽位 loading，其余仅禁用', () => {
    const rows = mountCard({ submittingKey: '0-OFF', submitting: true }).findAllComponents(CheckSlotRow)
    expect(rows[0].props('submitting')).toBe(false)
    expect(rows[1].props('submitting')).toBe(true)
    expect(rows[1].props('disabled')).toBe(true)
  })

  it('无提交时全部槽位不禁用', () => {
    const rows = mountCard().findAllComponents(CheckSlotRow)
    expect(rows.every((row) => row.props('disabled') === false)).toBe(true)
  })
})

describe('PeriodCard · 就近判定结果', () => {
  it('无结果不渲染结果区（不占位、不留白）', () => {
    expect(mountCard().findComponent(CheckResultPanel).exists()).toBe(false)
  })

  it('结果渲染在本卡内，文案为成功结论', () => {
    const wrapper = mountCard({ result: RESULT })
    expect(wrapper.findComponent(CheckResultPanel).exists()).toBe(true)
    expect(wrapper.text()).toContain('打卡成功')
  })
})
