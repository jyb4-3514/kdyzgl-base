import { describe, expect, it } from 'vitest'
import { buildRankRows, dayOverDay } from './dashboardMeta.js'

/**
 * 看板指标口径的回归网
 * 重点钉住：环比基线为 0 时不显示、排行百分比是相对最大值而非绝对值、空集不炸。
 */
describe('dayOverDay · 环比口径', () => {
  it('空集与单元素无对比基线，返回 null', () => {
    expect(dayOverDay([], 'todayInbound')).toBeNull()
    expect(dayOverDay([{ todayInbound: 10 }], 'todayInbound')).toBeNull()
    expect(dayOverDay(null, 'todayInbound')).toBeNull()
  })

  it('近 N 天日均（排除最后一天）为 0 时不显示，避免除零放大', () => {
    expect(dayOverDay([{ todayInbound: 0 }, { todayInbound: 5 }], 'todayInbound')).toBeNull()
  })

  it('上升/下降/持平三态与文案', () => {
    expect(dayOverDay([{ todayInbound: 10 }, { todayInbound: 15 }], 'todayInbound')).toEqual({
      dir: 'up',
      text: '50.0%'
    })
    expect(dayOverDay([{ todayInbound: 10 }, { todayInbound: 5 }], 'todayInbound')).toEqual({
      dir: 'down',
      text: '50.0%'
    })
    expect(dayOverDay([{ todayInbound: 10 }, { todayInbound: 10 }], 'todayInbound')).toEqual({
      dir: 'flat',
      text: '0.0%'
    })
  })
})

describe('buildRankRows · 排行换算', () => {
  const rows = [
    { stationId: 1, stationName: '城东', parcelTotal: 100, pickupRate: 0.5, abnormalRate: 0.02 },
    { stationId: 2, stationName: '城西', parcelTotal: 50, pickupRate: 0.8, abnormalRate: 0.01 }
  ]

  it('按包裹量：主值为千分位件数，百分比相对当前列表最大值', () => {
    const out = buildRankRows(rows, 'parcelTotal')
    expect(out[0]).toMatchObject({ rank: 1, stationName: '城东', mainValue: '100', mainUnit: '件', percent: 100 })
    expect(out[1]).toMatchObject({ rank: 2, mainValue: '50', mainUnit: '件', percent: 50 })
  })

  it('按比率：主值转百分数保留 1 位，副信息仍是三口径固定顺序', () => {
    const out = buildRankRows(rows, 'pickupRate')
    expect(out[1]).toMatchObject({ mainValue: '80.0', mainUnit: '%', percent: 100 })
    expect(out[0].mainValue).toBe('50.0')
    expect(out[0].sub).toContain('包裹 100')
    expect(out[0].sub).toContain('取件率 50.0%')
    expect(out[0].sub).toContain('异常率 2.0%')
  })

  it('空集与全 0：不产生 NaN 百分比，id 退回下标保证 v-for key 稳定', () => {
    expect(buildRankRows([], 'parcelTotal')).toEqual([])
    const zero = buildRankRows([{ stationName: 'A', parcelTotal: 0 }], 'parcelTotal')
    expect(zero[0].percent).toBe(0)
    expect(zero[0].id).toBe(0)
  })
})
