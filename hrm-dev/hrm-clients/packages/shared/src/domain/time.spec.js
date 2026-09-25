import { describe, expect, it } from 'vitest'
import {
  addDays,
  addMonths,
  currentMonth,
  formatDate,
  formatDateTime,
  hoursAgoParam,
  mondayOf,
  monthRange,
  monthShift,
  parseDate,
  parseTime,
  shiftDays,
  todayStart
} from './time.js'

/** 用例全部按本地时区断言（Asia/Shanghai），与 domain 声明的口径一致 */
describe('parseTime', () => {
  it('把 MySQL 风格的空格替换为 T 再解析，不落回 UTC', () => {
    expect(parseTime('2026-09-20 08:30:00')).toBe(new Date(2026, 8, 20, 8, 30, 0).getTime())
  })

  it('已是 ISO 串时结果不变（替换只影响首个空格）', () => {
    expect(parseTime('2026-09-20T08:30:00')).toBe(new Date(2026, 8, 20, 8, 30, 0).getTime())
  })

  it('仅日期串按 JS 规范走 UTC 解析（要用本地 0 点须改用 parseDate）', () => {
    expect(parseTime('2026-09-20')).toBe(new Date('2026-09-20').getTime())
  })

  it('空值 / 非法串返回 NaN 而不是 0（调用方据此判「无值」）', () => {
    expect(Number.isNaN(parseTime(''))).toBe(true)
    expect(Number.isNaN(parseTime(null))).toBe(true)
    expect(Number.isNaN(parseTime(undefined))).toBe(true)
    expect(Number.isNaN(parseTime('随便一串'))).toBe(true)
  })
})

describe('parseDate', () => {
  it('按本地 0 点构造，不出现东八区退回前一天', () => {
    const d = parseDate('2026-09-20')
    expect(d.getFullYear()).toBe(2026)
    expect(d.getMonth()).toBe(8)
    expect(d.getDate()).toBe(20)
    expect(d.getHours()).toBe(0)
  })
})

describe('formatDate / formatDateTime', () => {
  it('补零到两位', () => {
    expect(formatDate(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(formatDateTime(new Date(2026, 0, 5, 3, 7, 9))).toBe('2026-01-05 03:07:09')
  })

  it('接受可被 Date 解析的值', () => {
    expect(formatDate('2026-09-20T08:30:00')).toBe('2026-09-20')
  })
})

describe('mondayOf（周一为周首）', () => {
  it('周日算作本周最后一天，回退到本周一而不是下周一', () => {
    // 2026-09-20 是周日，本周一应为 09-14
    expect(formatDate(mondayOf(new Date(2026, 8, 20)))).toBe('2026-09-14')
  })

  it('传入周一时返回当天', () => {
    expect(formatDate(mondayOf(new Date(2026, 8, 14)))).toBe('2026-09-14')
  })

  it('跨月边界：7 月 1 日（周三）属于 6 月 29 日开始的那一周', () => {
    expect(formatDate(mondayOf(new Date(2026, 6, 1)))).toBe('2026-06-29')
  })

  it('时间被归零到当天 0 点', () => {
    const d = mondayOf(new Date(2026, 8, 20, 23, 59, 59))
    expect([d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds()]).toEqual([0, 0, 0, 0])
  })

  it('默认取当天', () => {
    expect(formatDate(mondayOf())).toBe(formatDate(mondayOf(new Date())))
  })
})

describe('monthShift', () => {
  it('默认往前一个月', () => {
    expect(monthShift('2026-09')).toBe('2026-08')
  })

  it('跨年：1 月往前 = 上一年 12 月', () => {
    expect(monthShift('2026-01', -1)).toBe('2025-12')
  })

  it('跨年：12 月往后 = 下一年 1 月', () => {
    expect(monthShift('2025-12', 1)).toBe('2026-01')
  })

  it('跨年且跨多年', () => {
    expect(monthShift('2026-02', -14)).toBe('2024-12')
  })
})

describe('monthRange', () => {
  it('普通月：起止日期与天数', () => {
    expect(monthRange('2026-09')).toEqual({ startDate: '2026-09-01', endDate: '2026-09-30', days: 30 })
  })

  it('闰年 2 月为 29 天', () => {
    expect(monthRange('2024-02')).toEqual({ startDate: '2024-02-01', endDate: '2024-02-29', days: 29 })
  })

  it('平年 2 月为 28 天（2026 非闰年）', () => {
    expect(monthRange('2026-02').days).toBe(28)
    expect(monthRange('2023-02').days).toBe(28)
  })

  it('世纪闰年 2000 年为 29 天、1900 年不是（格里高利规则）', () => {
    expect(monthRange('2000-02').days).toBe(29)
    expect(monthRange('1900-02').days).toBe(28)
  })

  it('月首补零', () => {
    expect(monthRange('2026-01').startDate).toBe('2026-01-01')
  })
})

describe('addDays / addMonths / shiftDays', () => {
  it('addDays 返回新对象，不改原值', () => {
    const base = new Date(2026, 8, 20)
    const next = addDays(base, 5)
    expect(formatDate(next)).toBe('2026-09-25')
    expect(formatDate(base)).toBe('2026-09-20')
  })

  it('addMonths 跨年进位', () => {
    expect(formatDate(addMonths(new Date(2026, 10, 15), 3))).toBe('2027-02-15')
  })

  it('shiftDays 相对今天偏移并可指定时分秒', () => {
    const d = shiftDays(-2, 9, 30, 15)
    expect(d.getHours()).toBe(9)
    expect(d.getMinutes()).toBe(30)
    expect(d.getSeconds()).toBe(15)
    expect(d.getMilliseconds()).toBe(0)
    const expected = new Date()
    expected.setDate(expected.getDate() - 2)
    expect(formatDate(d)).toBe(formatDate(expected))
  })
})

describe('currentMonth / todayStart / hoursAgoParam', () => {
  it('currentMonth 形如 yyyy-MM 且与当前月一致', () => {
    expect(currentMonth()).toMatch(/^\d{4}-\d{2}$/)
    expect(currentMonth()).toBe(formatDate(new Date()).slice(0, 7))
  })

  it('todayStart 是今天 0 点且不晚于此刻', () => {
    const ts = todayStart()
    expect(ts).toBe(new Date(new Date().setHours(0, 0, 0, 0)).getTime())
    expect(ts).toBeLessThanOrEqual(Date.now())
  })

  it('hoursAgoParam 返回接口可用的时间串且早于此刻', () => {
    const text = hoursAgoParam(3)
    expect(text).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(parseTime(text)).toBeLessThan(Date.now())
  })
})
