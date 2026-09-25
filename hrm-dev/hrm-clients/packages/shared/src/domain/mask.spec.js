import { describe, expect, it } from 'vitest'
import { maskBankAccount, maskName, maskPhone } from './mask.js'

describe('maskPhone', () => {
  it('11 位手机号取前 3 后 4', () => {
    expect(maskPhone('13812345678')).toBe('138****5678')
  })

  it('非 11 位数字只保留前 3 位', () => {
    expect(maskPhone('1381234')).toBe('138****')
  })

  it('数字型入参也按字符串处理', () => {
    expect(maskPhone(13812345678)).toBe('138****5678')
  })

  it('空值原样返回（不给空串硬造掩码）', () => {
    expect(maskPhone('')).toBe('')
    expect(maskPhone(null)).toBeNull()
    expect(maskPhone(undefined)).toBeUndefined()
  })
})

describe('maskName', () => {
  it('多字姓名只留姓', () => {
    expect(maskName('张三')).toBe('张*')
    expect(maskName('欧阳娜娜')).toBe('欧***')
  })

  it('单字姓名不再打码', () => {
    expect(maskName('张')).toBe('张')
  })

  it('空值原样返回', () => {
    expect(maskName('')).toBe('')
    expect(maskName(null)).toBeNull()
  })
})

describe('maskBankAccount', () => {
  it('保留末 4 位，其余按 4 位一组占位', () => {
    expect(maskBankAccount('6222021234567890')).toBe('**** **** **** 7890')
  })

  it('忽略入参里的空格', () => {
    expect(maskBankAccount('6222 0212 3456 7890')).toBe('**** **** **** 7890')
  })

  it('不足 4 位全部打码', () => {
    expect(maskBankAccount('1234')).toBe('****')
    expect(maskBankAccount('12')).toBe('**')
  })

  it('位数非 4 的倍数时余数单独占位，不丢字符', () => {
    expect(maskBankAccount('62220212345678901')).toBe('**** **** **** * 8901')
  })

  it('空值原样返回', () => {
    expect(maskBankAccount('')).toBe('')
    expect(maskBankAccount(null)).toBeNull()
  })
})
