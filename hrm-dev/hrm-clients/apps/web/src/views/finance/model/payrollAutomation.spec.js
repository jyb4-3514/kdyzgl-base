import { describe, expect, it } from 'vitest'
import {
  netImpactClass,
  runRangeText,
  runSkipText,
  runStatusLabel,
  runTriggerLabel,
  signedMoney
} from './payrollAutomation.js'

/**
 * 薪资结算自动化展示层纯函数单测（I-4 / I-5 / I-10 的机器码 → 中文与正负通道）
 * 覆盖：字典回落、跳过原因拼接、进行中占位、金额正负号与净影响色族。
 */
describe('payrollAutomation 展示层纯函数', () => {
  it('runStatusLabel 命中字典返回中文，未命中回落原值', () => {
    expect(runStatusLabel('SKIPPED')).toBe('已跳过')
    expect(runStatusLabel('SUCCESS')).toBe('成功')
    expect(runStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(runStatusLabel('')).toBe('—')
  })

  it('runTriggerLabel 命中字典返回中文（AUTO 定时 / CATCH_UP 补跑 / MANUAL 手工）', () => {
    expect(runTriggerLabel('AUTO')).toBe('定时')
    expect(runTriggerLabel('CATCH_UP')).toBe('补跑')
    expect(runTriggerLabel('MANUAL')).toBe('手工')
  })

  it('runSkipText 拼接机器码中文与人类可读原因', () => {
    expect(runSkipText({ skipCode: 'BLOCKED_9405', skipReason: '该账期已存在非可覆盖工资单' })).toBe(
      '该账期已存在非可覆盖工资单'
    )
    expect(runSkipText({ skipCode: 'CONFIG_INVALID', skipReason: '配置项缺失' })).toBe('算薪配置非法（配置项缺失）')
    expect(runSkipText({})).toBe('—')
  })

  it('runRangeText 进行中的 finishTime 为空显示「进行中」而不是空白', () => {
    expect(runRangeText({ startTime: '2026-09-27 09:00:00', finishTime: null })).toBe('2026-09-27 09:00:00 ~ 进行中')
    expect(runRangeText({ startTime: 'a', finishTime: 'b' })).toBe('a ~ b')
  })

  it('signedMoney 正负号与两位小数口径', () => {
    expect(signedMoney(120)).toBe('120.00')
    expect(signedMoney(120, true)).toBe('+120.00')
    expect(signedMoney(-120, true)).toBe('-120.00')
    expect(signedMoney(0, true)).toBe('0.00')
    expect(signedMoney(null)).toBe('0.00')
  })

  it('netImpactClass 正负双通道：正 is-plus / 负 is-minus / 零中性', () => {
    expect(netImpactClass(10)).toBe('is-plus')
    expect(netImpactClass(-10)).toBe('is-minus')
    expect(netImpactClass(0)).toBe('')
  })
})
