// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SlaTag from './SlaTag.vue'

/**
 * SlaTag 的两条硬要求（P1-5）：
 * 1. 一页 20 行只共享 1 个心跳定时器，订阅数归零即停表；
 * 2. 正常态不占标签位（纯文本），只有临近/超时才出胶囊。
 * 阈值 = 优先级 SLA 总时长 × 25%；不传优先级时按 24h 兜底 → 6h。
 */
const HOUR = 3600 * 1000
const pad = (n) => String(n).padStart(2, '0')

/** 相对当前时刻生成 'yyyy-MM-dd HH:mm:ss'（本地时区） */
const deadlineAfter = (diff) => {
  const d = new Date(Date.now() + diff)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const mounted = []
const mountTag = (props) => {
  const wrapper = mount(SlaTag, { props, global: { stubs: { 'van-icon': true } } })
  mounted.push(wrapper)
  return wrapper
}

afterEach(() => {
  while (mounted.length) mounted.pop().unmount()
})

describe('SlaTag · 四态渲染', () => {
  it('正常态渲染为纯文本，不出现胶囊', () => {
    const wrapper = mountTag({ deadline: deadlineAfter(10 * HOUR) })
    expect(wrapper.find('.sla-text').exists()).toBe(true)
    expect(wrapper.find('.sla-tag').exists()).toBe(false)
    expect(wrapper.text()).toContain('剩余')
  })

  it('临近态出 warning 胶囊', () => {
    const wrapper = mountTag({ deadline: deadlineAfter(3 * HOUR) })
    expect(wrapper.find('.sla-tag').classes()).toContain('sla-tag--warning')
  })

  it('超时态出 over 胶囊并带无障碍播报', () => {
    const wrapper = mountTag({ deadline: deadlineAfter(-HOUR) })
    expect(wrapper.find('.sla-tag').classes()).toContain('sla-tag--over')
    expect(wrapper.find('.sla-tag').attributes('aria-label')).toBe('已超时未处理')
    expect(wrapper.find('[role="status"]').text()).toBe('工单已超时未处理')
    expect(wrapper.text()).toContain('已超时')
  })

  it('active=false（已解决/已关闭）不渲染任何形态', () => {
    const wrapper = mountTag({ deadline: deadlineAfter(-HOUR), active: false })
    expect(wrapper.find('.sla-tag').exists()).toBe(false)
    expect(wrapper.find('.sla-text').exists()).toBe(false)
  })

  it('deadline 为空或非法时不渲染任何形态', () => {
    for (const deadline of ['', '不是时间']) {
      const wrapper = mountTag({ deadline })
      expect(wrapper.find('.sla-tag').exists()).toBe(false)
      expect(wrapper.find('.sla-text').exists()).toBe(false)
    }
  })

  it('优先级决定临近阈值：高优先级 8h×25%=2h，同一个「剩 3h」此时已算正常', () => {
    const deadline = deadlineAfter(3 * HOUR)
    expect(mountTag({ deadline, priority: 2 }).find('.sla-tag').exists()).toBe(false)
    expect(mountTag({ deadline }).find('.sla-tag').exists()).toBe(true)
    expect(
      mountTag({ deadline: deadlineAfter(HOUR), priority: 2 })
        .find('.sla-tag')
        .classes()
    ).toContain('sla-tag--warning')
  })
})

describe('SlaTag · 心跳定时器（P1-5）', () => {
  it('同一列表多个实例只共享 1 个定时器，最后一个卸载才停表', () => {
    const setSpy = vi.spyOn(globalThis, 'setInterval')
    const clearSpy = vi.spyOn(globalThis, 'clearInterval')
    setSpy.mockClear()
    clearSpy.mockClear()

    const wrappers = [
      mountTag({ deadline: deadlineAfter(HOUR) }),
      mountTag({ deadline: deadlineAfter(HOUR) }),
      mountTag({ deadline: deadlineAfter(HOUR) })
    ]
    expect(setSpy).toHaveBeenCalledTimes(1)

    wrappers[0].unmount()
    wrappers[1].unmount()
    expect(clearSpy).not.toHaveBeenCalled()

    wrappers[2].unmount()
    expect(clearSpy).toHaveBeenCalledTimes(1)

    setSpy.mockRestore()
    clearSpy.mockRestore()
  })
})
