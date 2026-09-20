// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import * as dicts from '@/shared/constants/dict.js'
import StatusTag from './StatusTag.vue'

const { NOTIFICATION_TYPE, PARCEL_STATUS, WORK_ORDER_PRIORITY } = dicts

/**
 * variant 三值硬保证（字典合并 R4 风险）：
 * 两套字典合一时最容易出的问题是同一状态在两端形态不同，
 * 形态只能由字典的 variant 决定且只能取 soft / outline / solid 三值 ——
 * 一旦有人写进第四个值，这里的全量扫描会立刻红，不需要等视觉走查。
 *
 * 挂载方式说明：必须经父组件传参，与页面里的真实调用一致。
 * 直接 `mount(StatusTag, { props })` 会被包装成 reactive 代理，
 * 而组件内部用 Map 按对象本体（identity）查配色，代理会让查表静默失配、退化到 type 兜底。
 */
const VARIANTS = ['soft', 'outline', 'solid']

const mountTag = (dict, value, variant) =>
  mount({
    setup: () => () => h(StatusTag, { dict, value, variant })
  }).findComponent(StatusTag)

describe('StatusTag · variant 三值硬保证', () => {
  it('全量字典里出现的 variant 只能是 soft / outline / solid', () => {
    const seen = new Set()
    for (const [dictName, table] of Object.entries(dicts)) {
      if (!table || typeof table !== 'object' || Array.isArray(table)) continue
      for (const [value, item] of Object.entries(table)) {
        if (!item || typeof item !== 'object' || item.variant === undefined) continue
        expect(VARIANTS, `${dictName}.${value} 的 variant 越界`).toContain(item.variant)
        seen.add(item.variant)
      }
    }
    // 三值都应被真实使用，避免有人把 variant 全删了也算通过
    expect([...seen].sort()).toEqual([...VARIANTS].sort())
  })

  it('按字典渲染出的形态 class 必在三值内', () => {
    for (const table of [PARCEL_STATUS, WORK_ORDER_PRIORITY]) {
      for (const [value, item] of Object.entries(table)) {
        expect(VARIANTS).toContain(item.variant)
        expect(mountTag(table, value).classes()).toContain(`status-tag--${item.variant}`)
      }
    }
  })

  it('显式 variant 覆盖字典 variant（通知类型统一描边的场景）', () => {
    expect(mountTag(PARCEL_STATUS, 4, 'solid').classes()).toContain('status-tag--solid')
  })

  it('未登记 variant 的字典回落 type 语义，info 走描边', () => {
    expect(mountTag(NOTIFICATION_TYPE, 1).classes()).toContain('status-tag--outline')
  })
})

describe('StatusTag · 值与配色', () => {
  it('命中时渲染字典文案，不渲染占位符', () => {
    const wrapper = mountTag(PARCEL_STATUS, 1)
    expect(wrapper.text()).toBe('在库待取')
    expect(wrapper.classes()).not.toContain('status-tag--missing')
  })

  it('未命中渲染「—」而不是空白（避免误以为漏数据）', () => {
    const wrapper = mountTag(PARCEL_STATUS, 99)
    expect(wrapper.text()).toBe('—')
    expect(wrapper.classes()).toContain('status-tag--missing')
  })

  it('value 为空串同样按未命中处理', () => {
    expect(mountTag(PARCEL_STATUS, '').text()).toBe('—')
  })

  it('配色经内联 CSS 变量注入，实心态取深色底 + 反色字', () => {
    const style = mountTag(WORK_ORDER_PRIORITY, 2).attributes('style') || ''
    expect(style).toContain('--tag-surface: var(--color-danger)')
    expect(style).toContain('--tag-text: var(--text-on-dark)')
  })

  it('字典不是本体（传了副本）时静默退化到 type 兜底 —— 锁住这条已登记的约束', () => {
    const style = mountTag({ ...WORK_ORDER_PRIORITY }, 2).attributes('style') || ''
    expect(style).toContain('--tag-surface: var(--color-danger-surface)')
  })
})
