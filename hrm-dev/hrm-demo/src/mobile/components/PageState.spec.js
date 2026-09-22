// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import PageState from './PageState.vue'

/**
 * 三态包装的关键行为：
 * 1. 加载中一律不渲染默认插槽（否则数据未到位时插槽里的字段访问会空指针）；
 * 2. 快请求（<200ms）不显示骨架，避免骨架闪一下反而更「跳」；
 * 3. 错误态必给 retry，且文案与空态必须不同（否则用户会把系统异常当成业务为空）。
 */
const stubs = { 'van-icon': true, 'van-skeleton': true }
const mountState = (props, slots) => mount(PageState, { props, slots, global: { stubs } })

afterEach(() => {
  vi.useRealTimers()
})

describe('PageState · 状态优先级', () => {
  it('加载中不渲染默认插槽', () => {
    const wrapper = mountState({ loading: true }, { default: '<p class="content">数据</p>' })
    expect(wrapper.find('.content').exists()).toBe(false)
    expect(wrapper.find('.page-state__loading').exists()).toBe(true)
  })

  it('错误态优先于空态，且给出重试按钮', async () => {
    const wrapper = mountState({ error: '网络异常', empty: true })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('网络异常')
    expect(wrapper.text()).toContain('请检查网络后重试，若持续失败请联系管理员')
    await wrapper.find('.page-state__action').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('空态文案与错误态不同，并可挂载自定义动作', () => {
    const wrapper = mountState({ empty: true, emptyText: '暂无工单' }, { 'empty-action': '<a class="go">去建单</a>' })
    expect(wrapper.text()).toContain('暂无工单')
    expect(wrapper.find('.go').exists()).toBe(true)
    expect(wrapper.find('.page-state__action').exists()).toBe(false)
  })

  it('正常态渲染默认插槽', () => {
    const wrapper = mountState({}, { default: '<p class="content">数据</p>' })
    expect(wrapper.find('.content').exists()).toBe(true)
  })
})

describe('PageState · 无权限降级（variant=denied）', () => {
  it('denied 只给说明，不渲染插槽、也不渲染重试按钮', () => {
    const wrapper = mountState({ variant: 'denied' }, { default: '<p class="content">数据</p>' })
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无查看权限')
    expect(wrapper.text()).toContain('该内容由其他角色办理')
    expect(wrapper.find('.page-state__action').exists()).toBe(false)
    expect(wrapper.find('.content').exists()).toBe(false)
  })

  it('denied 文案可覆盖，用于写清「谁能办、去哪办」', () => {
    const wrapper = mountState({ variant: 'denied', deniedText: '流程进度仅人事端可见', deniedHint: '请联系驿站站长' })
    expect(wrapper.text()).toContain('流程进度仅人事端可见')
    expect(wrapper.text()).toContain('请联系驿站站长')
  })

  it('denied 优先于 error：无权限不是系统异常，给重试只会让用户反复点', () => {
    const wrapper = mountState({ variant: 'denied', error: '网络异常' })
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('variant 传未知值时按普通三态处理，不静默吞掉数据', () => {
    const wrapper = mountState({ variant: 'unknown' }, { default: '<p class="content">数据</p>' })
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.find('.content').exists()).toBe(true)
  })

  it('向后兼容：不传 variant 时错误态仍是 alert + 重试，行为与改造前一致', async () => {
    const wrapper = mountState({ error: '网络异常', empty: true })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.page-state__action').exists()).toBe(true)
    await wrapper.find('.page-state__action').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})

describe('PageState · 骨架延迟', () => {
  it('加载 200ms 后才出现骨架，快请求不闪骨架', async () => {
    vi.useFakeTimers()
    const wrapper = mountState({ loading: true })

    expect(wrapper.find('.page-state__skeleton').exists()).toBe(false)
    vi.advanceTimersByTime(199)
    await nextTick()
    expect(wrapper.find('.page-state__skeleton').exists()).toBe(false)

    vi.advanceTimersByTime(1)
    await nextTick()
    expect(wrapper.find('.page-state__skeleton').exists()).toBe(true)
    wrapper.unmount()
  })

  it('加载结束立即收起骨架并撤销待触发的定时器', async () => {
    vi.useFakeTimers()
    const wrapper = mountState({ loading: true })
    vi.advanceTimersByTime(200)
    await nextTick()
    expect(wrapper.find('.page-state__skeleton').exists()).toBe(true)

    await wrapper.setProps({ loading: false })
    expect(wrapper.find('.page-state__skeleton').exists()).toBe(false)

    // 已撤销的定时器不能再把骨架打开
    vi.advanceTimersByTime(1000)
    await nextTick()
    expect(wrapper.find('.page-state__skeleton').exists()).toBe(false)
    wrapper.unmount()
  })
})
