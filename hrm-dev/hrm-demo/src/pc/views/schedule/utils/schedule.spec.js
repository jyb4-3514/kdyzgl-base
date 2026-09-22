import { describe, expect, it } from 'vitest'
import {
  buildOriginal,
  buildRows,
  cellKey,
  dayFilledCountOf,
  filledCellsOf,
  isUnavailableShift,
  localDate,
  shiftColorOf,
  stamp
} from './schedule.js'

/**
 * 排班纯函数的回归网
 * 重点钉住：日期按本地 0 点解析（跨时区不能算错一天）、班次可用性判定与 9106 口径一致、
 * 矩阵快照做深拷贝（改本地副本不能污染服务端返回对象）。
 */
const SHIFTS = [
  { id: 1, shiftName: '早班', color: '#0958D9', status: 1 },
  { id: 2, shiftName: '晚班', color: '#1F2937', status: 0 }
]

const EMPLOYEES = [
  {
    employeeId: 1,
    employeeName: '张三',
    days: [
      { workDate: '2026-09-21', shiftId: 1 },
      { workDate: '2026-09-22', shiftId: null }
    ]
  },
  { employeeId: 2, employeeName: '李四', days: [{ workDate: '2026-09-21', shiftId: null }] }
]

describe('schedule · 日期与键', () => {
  it('localDate 按本地 0 点解析，不因 UTC 解析而少一天', () => {
    const date = localDate('2026-09-21')
    expect(date.getFullYear()).toBe(2026)
    expect(date.getMonth()).toBe(8)
    expect(date.getDate()).toBe(21)
    expect(date.getHours()).toBe(0)
  })

  it('cellKey 用员工 × 日期唯一确定一格', () => {
    expect(cellKey(1, '2026-09-21')).toBe('1_2026-09-21')
  })

  it('stamp 输出 HH:mm', () => {
    expect(stamp()).toMatch(/^\d{2}:\d{2}$/)
  })
})

describe('schedule · 班次可用性', () => {
  it('shiftColorOf：未排班透明、命中取班次色、已删除取警告色', () => {
    expect(shiftColorOf(SHIFTS, null)).toBe('transparent')
    expect(shiftColorOf(SHIFTS, 1)).toBe('#0958D9')
    expect(shiftColorOf(SHIFTS, 99)).toBe('var(--state-warning-fg)')
  })

  it('isUnavailableShift：未排班可用、启用可用、停用与已删除不可用', () => {
    expect(isUnavailableShift(SHIFTS, null)).toBe(false)
    expect(isUnavailableShift(SHIFTS, 1)).toBe(false)
    expect(isUnavailableShift(SHIFTS, 2)).toBe(true)
    expect(isUnavailableShift(SHIFTS, 99)).toBe(true)
  })

  it('班次列表为空时不抛错，一律判为不可用', () => {
    expect(isUnavailableShift([], 1)).toBe(true)
    expect(shiftColorOf([], 1)).toBe('var(--state-warning-fg)')
  })
})

describe('schedule · 矩阵快照', () => {
  it('buildRows 深拷贝一层：改副本不污染服务端返回对象', () => {
    const rows = buildRows(EMPLOYEES)
    rows[0].days[0].shiftId = 2
    expect(EMPLOYEES[0].days[0].shiftId).toBe(1)
    expect(rows[0].employeeId).toBe(1)
    expect(rows[0].employeeName).toBe('张三')
  })

  it('buildRows / buildOriginal 对空集返回空结构', () => {
    expect(buildRows()).toEqual([])
    expect(buildRows([])).toEqual([])
    expect(buildOriginal().size).toBe(0)
  })

  it('buildOriginal 把空班次归一到 null，便于与 dirty 比较', () => {
    const original = buildOriginal(EMPLOYEES)
    expect(original.get('1_2026-09-21')).toBe(1)
    expect(original.get('1_2026-09-22')).toBeNull()
    expect(original.get('2_2026-09-21')).toBeNull()
  })
})

describe('schedule · 排班进度', () => {
  it('filledCellsOf 只数有班次的格', () => {
    expect(filledCellsOf(buildRows(EMPLOYEES))).toBe(1)
    expect(filledCellsOf([])).toBe(0)
  })

  it('dayFilledCountOf 按下标统计，越界日期不抛错', () => {
    const rows = buildRows(EMPLOYEES)
    expect(dayFilledCountOf(rows, 0)).toBe(1)
    expect(dayFilledCountOf(rows, 1)).toBe(0)
    expect(dayFilledCountOf(rows, 9)).toBe(0)
    expect(dayFilledCountOf([], 0)).toBe(0)
  })
})
