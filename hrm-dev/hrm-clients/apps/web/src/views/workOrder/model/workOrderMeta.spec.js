import { describe, expect, it } from 'vitest'
import {
  LOG_ACTION,
  LOG_DOT,
  PRIORITY_VARIANT,
  TABS,
  TRANSITIONS,
  actionLabelOf,
  rowClassNameOf,
  slaFinished,
  sourceVariantOf,
  statusVariantOf
} from './workOrderMeta.js'

/**
 * 工单展示元数据的回归网
 * 重点钉住：状态机与 Mock 的 TRANSITIONS 一致、Tab 口径（含超时未处理派生键）不被改坏、
 * 行类名与形态映射是列表/详情两处唯一真源。
 */
describe('workOrderMeta · 状态机与动作文案', () => {
  it('TRANSITIONS 与 Mock 口径一致：待处理→接单/关闭、处理中→解决、已解决→重开/关闭、已关闭为终态', () => {
    expect(TRANSITIONS).toEqual({ 0: [1, 3], 1: [2], 2: [1, 3], 3: [] })
  })

  it('actionLabelOf：关闭、重开、接单、解决四种文案', () => {
    expect(actionLabelOf(0, 3)).toBe('关闭工单')
    expect(actionLabelOf(2, 1)).toBe('驳回重开')
    expect(actionLabelOf(0, 1)).toBe('接单处理')
    expect(actionLabelOf(1, 2)).toBe('标记解决')
  })

  it('LOG_ACTION / LOG_DOT 覆盖全部流转动作，且转单有独立轴点色', () => {
    const actions = ['create', 'assign', 'accept', 'resolve', 'close', 'reopen', 'transfer', 'auto_dispatch']
    actions.forEach((action) => {
      expect(LOG_ACTION[action]).toBeTruthy()
      expect(LOG_DOT[action]).toBeTruthy()
    })
    expect(LOG_DOT.transfer).toBe('var(--color-accent)')
  })

  it('TABS 顺序与覆盖全部状态 + 超时未处理派生键', () => {
    expect(TABS.map((tab) => tab.name)).toEqual(['all', '0', '1', '2', '3', 'overdueUnhandled'])
    expect(TABS[TABS.length - 1].label).toBe('超时未处理')
  })
})

describe('workOrderMeta · 单元格形态与行类名', () => {
  it('优先级形态：低描边 / 中浅底 / 高实心', () => {
    expect(PRIORITY_VARIANT).toEqual({ 0: 'outline', 1: 'soft', 2: 'solid' })
  })

  it('来源形态：缺失 source 按手工建单兜底为描边', () => {
    expect(sourceVariantOf(undefined)).toBe('outline')
    expect(sourceVariantOf('AUTO_WECHAT')).toBe('soft')
    expect(sourceVariantOf('NOT_EXIST')).toBe('outline')
  })

  it('状态形态：仅已关闭走描边', () => {
    expect(statusVariantOf(0)).toBe('soft')
    expect(statusVariantOf(3)).toBe('outline')
  })

  it('slaFinished：已解决与已关闭视为终止', () => {
    expect([0, 1, 2, 3].map(slaFinished)).toEqual([false, false, true, true])
  })

  it('行类名：超时与高亮可叠加，均不命中时返回空串', () => {
    expect(rowClassNameOf({ id: 1 }, null)).toBe('')
    expect(rowClassNameOf({ id: 1, overdueUnhandled: true }, null)).toBe('is-oversla')
    expect(rowClassNameOf({ id: 1, overdueUnhandled: true }, 1)).toBe('is-oversla is-highlight')
    expect(rowClassNameOf({ id: 2, overdueUnhandled: true }, 1)).toBe('is-oversla')
    expect(rowClassNameOf({ id: 1 }, 1)).toBe('is-highlight')
    expect(rowClassNameOf({ id: 2 }, 1)).toBe('')
  })
})
