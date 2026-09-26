import { describe, expect, it } from 'vitest'
import {
  BSSID_INVALID_TEXT,
  SSID_EMPTY_TEXT,
  SSID_MAX,
  SSID_TOO_LONG_TEXT,
  canAutoFillWifi,
  isWifiEditable,
  pickWifiEntry,
  rowErrors,
  validateBssid,
  validateSsid,
  wifiExtraLabel
} from './wifiWhitelist.js'

/**
 * WiFi 白名单校验口径回归网（单条化后，设计 §12）
 * 重点是两条硬约束：「判定只比对 SSID、区分大小写」（去重随单条化作废），
 * 与「读取当前 WiFi 的降级判据用 mock===false」——它们在页面上看不见摸不着，
 * 一旦被改成「按是否在壳内」判断就不会有人发现。
 * 单条化专项：pickWifiEntry 锁死「历史多条只取首条」的加载口径（§12.12①）。
 */

describe('validateSsid · SSID 必填 / 超长', () => {
  it('空串与纯空白都按「未填写」拦截', () => {
    expect(validateSsid('')).toBe(SSID_EMPTY_TEXT)
    expect(validateSsid('   ')).toBe(SSID_EMPTY_TEXT)
  })

  it('恰好 32 字符放行，33 字符拦截', () => {
    expect(validateSsid('A'.repeat(SSID_MAX))).toBe('')
    expect(validateSsid('A'.repeat(SSID_MAX + 1))).toBe(SSID_TOO_LONG_TEXT)
  })
})

describe('validateBssid · 选填，填了必须是合法 MAC', () => {
  it('留空放行（BSSID 仅留痕，不影响判定）', () => {
    expect(validateBssid('')).toBe('')
    expect(validateBssid(undefined)).toBe('')
  })

  it('大小写十六进制均接受', () => {
    expect(validateBssid('AC:84:C6:00:00:03')).toBe('')
    expect(validateBssid('ac:84:c6:00:00:03')).toBe('')
  })

  it('段数不足 / 分隔符错 / 含非十六进制字符均拦截', () => {
    const bad = ['AC:84:C6:00:00', 'AC-84-C6-00-00-03', 'GG:84:C6:00:00:03']
    bad.forEach((value) => expect(validateBssid(value)).toBe(BSSID_INVALID_TEXT))
  })
})

describe('rowErrors · 行内错误汇总', () => {
  it('返回 { ssid, bssid } 两字段错误', () => {
    const r = rowErrors({ ssid: '', bssid: 'bad' })
    expect(r.ssid).toBe(SSID_EMPTY_TEXT)
    expect(r.bssid).toBe(BSSID_INVALID_TEXT)
  })

  it('空值不抛错，按必填拦截', () => {
    expect(rowErrors(null).ssid).toBe(SSID_EMPTY_TEXT)
  })
})

describe('pickWifiEntry · 历史多条只取首条（设计 §12.12①）', () => {
  it('未配置（空数组 / 非数组）返回 null 且无多条标记', () => {
    expect(pickWifiEntry([])).toEqual({ entry: null, hasLegacyMultiple: false })
    expect(pickWifiEntry(undefined)).toEqual({ entry: null, hasLegacyMultiple: false })
  })

  it('单条原样取出，不置多条标记', () => {
    expect(pickWifiEntry([{ ssid: 'ST001-Express', bssid: 'AC:84:C6:00:00:03' }])).toEqual({
      entry: { ssid: 'ST001-Express', bssid: 'AC:84:C6:00:00:03' },
      hasLegacyMultiple: false
    })
  })

  it('多条历史数据只取首条并置多条标记（用于 T39 提示）', () => {
    const r = pickWifiEntry([
      { ssid: 'ST001-Express', bssid: '' },
      { ssid: 'ST001-Guest', bssid: '' }
    ])
    expect(r.entry).toEqual({ ssid: 'ST001-Express', bssid: '' })
    expect(r.hasLegacyMultiple).toBe(true)
  })
})

describe('权限分支与 extra 文案', () => {
  it('仅 ADMIN 可编辑；非 ADMIN / 未定角色一律只读（防御位）', () => {
    expect(isWifiEditable(true)).toBe(true)
    expect(isWifiEditable(false)).toBe(false)
    expect(isWifiEditable(undefined)).toBe(false)
  })

  it('卡标题 extra 按权限分支给「可编辑 / 只读」，不含条数', () => {
    expect(wifiExtraLabel(true)).toBe('可编辑')
    expect(wifiExtraLabel(false)).toBe('只读')
  })
})

describe('canAutoFillWifi · 「读取当前 WiFi」降级判据', () => {
  it('壳体未实现 getWifiInfo → mock:true，一律不允许回填', () => {
    expect(canAutoFillWifi({ ssid: 'ST001-Express', mock: true })).toBe(false)
  })

  it('mock:false 但 ssid 为空也不回填', () => {
    expect(canAutoFillWifi({ ssid: '', bssid: '', mock: false })).toBe(false)
  })

  it('仅 mock:false 且 ssid 非空才允许回填', () => {
    expect(canAutoFillWifi({ ssid: 'ST001-Express', bssid: '', mock: false })).toBe(true)
  })

  it('空值返回 false（不得抛错）', () => {
    expect(canAutoFillWifi(null)).toBe(false)
    expect(canAutoFillWifi(undefined)).toBe(false)
  })
})
