import { describe, expect, it } from 'vitest'
import { enabledShiftsSorted, matchPeriodsToShifts, minutesOfDay } from './shiftPeriods.js'

/**
 * 时段 ↔ 班次 配对回归网（打卡规则页「就地改时间」的前置逻辑）
 * 重点锁三条：**只认启用班次**（停用不参与派生，也不该被写）、**一行一个班次**（不重复占用）、
 * **配不到就留空**（宁可不给编辑入口，也不拿相邻行/别站班次顶替——写错班次会直接改掉打卡时间）。
 */

describe('minutesOfDay · HH:mm 解析', () => {
  it('常规时刻换算为当日分钟数', () => {
    expect(minutesOfDay('00:00')).toBe(0)
    expect(minutesOfDay('08:30')).toBe(510)
    expect(minutesOfDay('23:30')).toBe(1410)
  })

  it('收班 24:00 落在当日末尾（1440），保证「结束晚于开始」可直接比较', () => {
    expect(minutesOfDay('24:00')).toBe(1440)
    expect(minutesOfDay('08:00') < minutesOfDay('24:00')).toBe(true)
  })

  it('空值 / 非法值回 NaN（调用方据此判空，不把空串当 00:00）', () => {
    expect(Number.isNaN(minutesOfDay(''))).toBe(true)
    expect(Number.isNaN(minutesOfDay(undefined))).toBe(true)
    expect(Number.isNaN(minutesOfDay('abc'))).toBe(true)
  })
})

describe('enabledShiftsSorted · 启用班次升序', () => {
  it('过滤停用班次并按开始时间升序（与服务端派生时段同口径）', () => {
    const rows = enabledShiftsSorted([
      { id: 1, shiftName: '晚班', startTime: '16:00', endTime: '24:00', status: 1 },
      { id: 2, shiftName: '中班', startTime: '12:00', endTime: '20:00', status: 0 },
      { id: 3, shiftName: '早班', startTime: '08:00', endTime: '16:00', status: 1 }
    ])
    expect(rows.map((r) => r.id)).toEqual([3, 1])
  })

  it('入参非数组时回空数组（班次拉取失败不抛错，页面降级为只读）', () => {
    expect(enabledShiftsSorted(null)).toEqual([])
    expect(enabledShiftsSorted(undefined)).toEqual([])
  })
})

describe('matchPeriodsToShifts · 时段配回班次', () => {
  const shifts = [
    { id: 11, shiftName: '早班', startTime: '08:00', endTime: '16:00', status: 1 },
    { id: 12, shiftName: '中班', startTime: '12:00', endTime: '20:00', status: 0 },
    { id: 13, shiftName: '晚班', startTime: '16:00', endTime: '24:00', status: 1 }
  ]

  it('按 (班次名, 起止) 精确命中，且每行配对到不同班次（两班站点各自可改）', () => {
    const rows = matchPeriodsToShifts(
      [
        { name: '早班', startTime: '08:00', endTime: '16:00' },
        { name: '晚班', startTime: '16:00', endTime: '24:00' }
      ],
      shifts
    )
    expect(rows.map((r) => r.shift && r.shift.id)).toEqual([11, 13])
    expect(rows.map((r) => r.name)).toEqual(['早班', '晚班'])
  })

  it('同名同起止的多条班次不会被同一行复用（命中后移出池）', () => {
    const rows = matchPeriodsToShifts(
      [
        { name: '早班', startTime: '08:00', endTime: '16:00' },
        { name: '早班', startTime: '08:00', endTime: '16:00' }
      ],
      [
        { id: 21, shiftName: '早班', startTime: '08:00', endTime: '16:00', status: 1 },
        { id: 22, shiftName: '早班', startTime: '08:00', endTime: '16:00', status: 1 }
      ]
    )
    expect(rows.map((r) => r.shift && r.shift.id)).toEqual([21, 22])
  })

  it('名称/时间对不上但两侧数量一致时按升序位置兜底', () => {
    const rows = matchPeriodsToShifts([{ name: '班次A', startTime: '08:00', endTime: '16:00' }], [
      { id: 31, shiftName: '早班', startTime: '08:00', endTime: '16:00', status: 1 }
    ])
    expect(rows[0].shift && rows[0].shift.id).toBe(31)
  })

  it('配不到班次时 shift 为 null（页面降级为只读，不臆造班次 id）', () => {
    const rows = matchPeriodsToShifts([{ name: '不存在', startTime: '08:00', endTime: '16:00' }], [])
    expect(rows[0].shift).toBeNull()
  })

  it('班次名缺失时兜底为「未命名班次」，不影响配对结果', () => {
    const rows = matchPeriodsToShifts([{ startTime: '08:00', endTime: '16:00' }], [
      { id: 41, shiftName: '早班', startTime: '08:00', endTime: '16:00', status: 1 }
    ])
    expect(rows[0].name).toBe('未命名班次')
    expect(rows[0].shift && rows[0].shift.id).toBe(41)
  })

  it('空时段（无启用班次）回空数组：空态与「去班次管理」引导据此渲染', () => {
    expect(matchPeriodsToShifts([], shifts)).toEqual([])
  })
})
