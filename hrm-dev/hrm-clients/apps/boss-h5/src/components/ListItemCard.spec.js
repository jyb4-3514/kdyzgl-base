// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ListItemCard from './ListItemCard.vue'

/**
 * ListItemCard 的键盘与点击语义（此前 5 处各写一遍且工资单页漏了 Space）：
 * 1. 不可点时不挂 role/tabindex、不派发 click —— 不造「看着能点、点了没反应」的假按钮；
 * 2. 可点且有 to 用 router-link（原生聚焦 + Enter），可点无 to 用 role="button" + Enter/Space 兜底；
 * 3. 行内独立操作必须由调用方放在组件**兄弟节点**，本组件不提供插槽吞并它的点击。
 */
const RouterLinkStub = { props: ['to'], template: '<a class="link-stub"><slot /></a>' }
const mountCard = (props = {}, slots = {}) =>
  mount(ListItemCard, { props, slots, global: { stubs: { RouterLink: RouterLinkStub } } })

describe('ListItemCard · 点击与键盘', () => {
  it('不可点：无 role/tabindex，点击不派发事件', async () => {
    const wrapper = mountCard({ clickable: false })
    expect(wrapper.element.tagName).toBe('DIV')
    expect(wrapper.attributes('role')).toBeUndefined()
    expect(wrapper.attributes('tabindex')).toBeUndefined()

    await wrapper.trigger('click')
    await wrapper.trigger('keydown.enter')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('可点且带 to：根元素用 router-link，点击派发 click', async () => {
    const wrapper = mountCard({ clickable: true, to: '/boss/parcel/1' })
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/boss/parcel/1')
    expect(wrapper.attributes('role')).toBeUndefined()

    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('可点但无 to：原生 button 语义兜底，Enter 与 Space 都能触发', async () => {
    const wrapper = mountCard({ clickable: true })
    expect(wrapper.attributes('role')).toBe('button')
    expect(wrapper.attributes('tabindex')).toBe('0')

    await wrapper.trigger('click')
    await wrapper.trigger('keydown.enter')
    await wrapper.trigger('keydown.space')
    expect(wrapper.emitted('click')).toHaveLength(3)
  })
})

describe('ListItemCard · 行样式与密度', () => {
  it('marked / failed 各自加竖条类，两者同给时以 failed 呈现', () => {
    const marked = mountCard({ marked: true })
    expect(marked.classes()).toContain('list-item-card--marked')
    expect(marked.classes()).not.toContain('list-item-card--failed')

    const both = mountCard({ marked: true, failed: true })
    expect(both.classes()).toEqual(expect.arrayContaining(['list-item-card--marked', 'list-item-card--failed']))
  })

  it('密度只认 2/3：3 走三行高度类，其余（含默认与非法值）走两行高度', () => {
    expect(mountCard({ density: 3 }).classes()).toContain('list-item-card--density-3')
    expect(mountCard({ density: 2 }).classes()).not.toContain('list-item-card--density-3')
    expect(mountCard({}).classes()).not.toContain('list-item-card--density-3')
    expect(mountCard({ density: 99 }).classes()).not.toContain('list-item-card--density-3')
  })
})

describe('ListItemCard · 插槽', () => {
  it('标题、右侧标签槽、元信息行、标签组各自渲染', () => {
    const wrapper = mountCard(
      {},
      {
        title: '<span class="vo-no">SF123456</span>',
        extra: '<span class="status">待取件</span>',
        default: '<p class="meta">3 天前入库</p>',
        tags: '<span class="warn">超时</span>'
      }
    )
    expect(wrapper.find('.vo-no').exists()).toBe(true)
    expect(wrapper.find('.status').exists()).toBe(true)
    expect(wrapper.find('.meta').exists()).toBe(true)
    expect(wrapper.find('.list-item-card__tags .warn').exists()).toBe(true)
  })

  it('未给 tags 插槽时不渲染标签组容器（不留空 margin）', () => {
    const wrapper = mountCard({}, { title: '仅标题' })
    expect(wrapper.find('.list-item-card__tags').exists()).toBe(false)
  })

  it('单元素与空插槽都不抛错', () => {
    expect(mountCard({}, { title: '单元素' }).find('.list-item-card__title').exists()).toBe(true)
    expect(mountCard().find('.list-item-card').exists()).toBe(true)
  })
})
