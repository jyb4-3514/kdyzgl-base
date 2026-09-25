// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'

/**
 * PageNav 的返回行为（新增可选 prop backFallback 的回归网）：
 * 1. 不传 backFallback（默认 ''）时必须与改造前逐字一致 —— router.back()，既有 40+ 调用方零回归；
 * 2. 传了 backFallback 但历史栈有上一页，仍走 router.back()（正常从列表进入的路径）；
 * 3. 传了 backFallback 且历史栈为空（深链直达/刷新后），replace 到兜底路径，避免返回键变成空操作。
 */
const router = vi.hoisted(() => ({ back: vi.fn(), replace: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

const NavBarStub = {
  name: 'VanNavBar',
  emits: ['click-left'],
  template: '<div class="nav-stub" @click="$emit(\'click-left\')"><slot name="left" /><slot name="right" /></div>'
}

const mountNav = (props = {}) =>
  mount(PageNav, { props, global: { stubs: { 'van-nav-bar': NavBarStub, 'van-icon': true } } })

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PageNav · 返回行为', () => {
  it('默认（不传 backFallback）走 router.back()，与改造前一致', async () => {
    window.history.replaceState({ back: '/staff/message' }, '')
    const wrapper = mountNav()
    await wrapper.find('.nav-stub').trigger('click')
    expect(router.back).toHaveBeenCalledTimes(1)
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('传了 backFallback 但历史栈有上一页 → 仍走 router.back()', async () => {
    window.history.replaceState({ back: '/staff/message' }, '')
    const wrapper = mountNav({ backFallback: '/staff/message?tab=notice' })
    await wrapper.find('.nav-stub').trigger('click')
    expect(router.back).toHaveBeenCalledTimes(1)
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('传了 backFallback 且历史栈为空（深链直达/刷新）→ replace 到兜底路径', async () => {
    window.history.replaceState({ back: null }, '')
    const wrapper = mountNav({ backFallback: '/staff/message?tab=notice' })
    await wrapper.find('.nav-stub').trigger('click')
    expect(router.replace).toHaveBeenCalledWith('/staff/message?tab=notice')
    expect(router.back).not.toHaveBeenCalled()
  })
})
