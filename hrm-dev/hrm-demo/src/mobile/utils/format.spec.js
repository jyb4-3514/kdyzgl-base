import { describe, expect, it } from 'vitest'
import { WORK_ORDER_SLA_HOURS } from '@/shared/constants/dict.js'
import { SLA_THRESHOLD_RATIO } from '@/shared/domain/sla.js'
import { badgeText, durationText, slaState } from './format.js'

const HOUR = 3600 * 1000
const NOW = new Date(2026, 8, 20, 12, 0, 0).getTime()
const THRESHOLD = 6 * HOUR
const pad = (n) => String(n).padStart(2, '0')
/** 生成本地时区的 'yyyy-MM-dd HH:mm:ss'（不能用 toISOString，那会转成 UTC 偏移 8 小时） */
const deadlineAt = (now, diff) => {
  const d = new Date(now + diff)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

describe('slaState · 四态判定', () => {
  it('恰好等于阈值算「临近」（闭区间向下取告警）', () => {
    const r = slaState(deadlineAt(NOW, THRESHOLD), true, NOW, THRESHOLD)
    expect(r.state).toBe('warning')
    expect(r.text).toBe('剩余 6小时0分')
  })

  it('超出阈值 1 秒即回到正常态', () => {
    expect(slaState(deadlineAt(NOW, THRESHOLD + 1000), true, NOW, THRESHOLD).state).toBe('normal')
  })

  it('diff 归零算临近而不是超时', () => {
    expect(slaState(deadlineAt(NOW, 0), true, NOW, THRESHOLD).state).toBe('warning')
  })

  it('已过截止时间算超时，文案是「已超时」', () => {
    const r = slaState(deadlineAt(NOW, -HOUR), true, NOW, THRESHOLD)
    expect(r.state).toBe('over')
    expect(r.text).toBe('已超时 1小时0分')
  })

  it('active=false（已解决/已关闭）不做超时判定，与后端 overdueUnhandled 口径一致', () => {
    expect(slaState(deadlineAt(NOW, -10 * HOUR), false, NOW, THRESHOLD)).toEqual({ state: 'hidden', text: '' })
  })

  it('deadline 非法串按「无值」处理而不是抛出', () => {
    expect(slaState('不是时间', true, NOW, THRESHOLD).state).toBe('hidden')
    expect(slaState('', true, NOW, THRESHOLD).state).toBe('hidden')
    expect(slaState(null, true, NOW, THRESHOLD).state).toBe('hidden')
  })

  it('未传阈值时默认 6 小时（对应优先级「中」）', () => {
    expect(slaState(deadlineAt(NOW, 5 * HOUR), true, NOW).state).toBe('warning')
    expect(slaState(deadlineAt(NOW, 7 * HOUR), true, NOW).state).toBe('normal')
  })
})

describe('durationText', () => {
  it('天 / 小时 / 分钟三档与负数取绝对值', () => {
    expect(durationText(2 * 24 * HOUR + 3 * HOUR)).toBe('2天3小时')
    expect(durationText(90 * 60 * 1000)).toBe('1小时30分')
    expect(durationText(-5 * 60 * 1000)).toBe('5分钟')
  })

  it('不足 1 分钟也显示 1 分钟，不出现 0 分钟', () => {
    expect(durationText(0)).toBe('1分钟')
    expect(durationText(20 * 1000)).toBe('1分钟')
  })
})

describe('SLA 阈值口径（三端统一）', () => {
  it('阈值 = 各优先级 SLA 总时长的 25%，低/中/高 → 12h / 6h / 2h', () => {
    expect(SLA_THRESHOLD_RATIO).toBe(0.25)
    expect(WORK_ORDER_SLA_HOURS[0] * SLA_THRESHOLD_RATIO).toBe(12)
    expect(WORK_ORDER_SLA_HOURS[1] * SLA_THRESHOLD_RATIO).toBe(6)
    expect(WORK_ORDER_SLA_HOURS[2] * SLA_THRESHOLD_RATIO).toBe(2)
  })
})

describe('badgeText · 角标不冒充未知', () => {
  it('0 不渲染角标（确实没有待办）', () => {
    expect(badgeText(0)).toBe('')
    expect(badgeText('0')).toBe('')
  })

  it('null / undefined / NaN 同样不渲染（取数失败，未知）', () => {
    expect(badgeText(null)).toBe('')
    expect(badgeText(undefined)).toBe('')
    expect(badgeText(NaN)).toBe('')
    expect(badgeText('abc')).toBe('')
  })

  it('负数不渲染', () => {
    expect(badgeText(-3)).toBe('')
  })

  it('超过 99 收敛为 99+，边界 99 仍显示原值', () => {
    expect(badgeText(1)).toBe('1')
    expect(badgeText(99)).toBe('99')
    expect(badgeText(100)).toBe('99+')
  })
})
