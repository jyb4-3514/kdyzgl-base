import { describe, expect, it } from 'vitest'
import {
  BSSID_INVALID_TEXT,
  NEW_WIFI_KEY,
  SSID_DUPLICATE_TEXT,
  SSID_EMPTY_TEXT,
  SSID_MAX,
  SSID_TOO_LONG_TEXT,
  canAutoFillWifi,
  countEmptySsid,
  isWifiEditable,
  rowErrors,
  validateBssid,
  validateSsid,
  wifiExtraLabel
} from './wifiWhitelist.js'

/**
 * WiFi 白名单校验口径回归网
 * 重点是「判定只比对 SSID、区分大小写」与「读取当前 WiFi 的降级判据用 mock===false」两条硬约束 ——
 * 它们在页面上看不见摸不着，一旦被改成「不区分大小写」或「按是否在壳内」判断就不会有人发现。
 */
const ROWS = [
  { key: 'w1', ssid: 'ST001-Express', bssid: 'AC:84:C6:00:00:03' },
  { key: 'w2', ssid: 'ST001-Guest', bssid: '' }
]

describe('validateSsid · SSID 必填 / 超长 / 重复（区分大小写）', () => {
  it('空串与纯空白都按「未填写」拦截', () => {
    expect(validateSsid('')).toBe(SSID_EMPTY_TEXT)
    expect(validateSsid('   ')).toBe(SSID_EMPTY_TEXT)
  })

  it('恰好 32 字符放行，33 字符拦截', () => {
    expect(validateSsid('A'.repeat(SSID_MAX))).toBe('')
    expect(validateSsid('A'.repeat(SSID_MAX + 1))).toBe(SSID_TOO_LONG_TEXT)
  })

  it('与同站已配置项精确重复时拦截', () => {
    expect(validateSsid('ST001-Express', { list: ROWS })).toBe(SSID_DUPLICATE_TEXT)
  })

  it('区分大小写：大小写不同不算重复（与后端 w.ssid === wifiSsid 同口径）', () => {
    expect(validateSsid('st001-express', { list: ROWS })).toBe('')
  })

  it('编辑既有行时排除自身，不会把自己判成重复', () => {
    expect(validateSsid('ST001-Express', { list: ROWS, selfKey: 'w1' })).toBe('')
  })

  it('前后空格先 trim 再比对，避免用空格绕过重复校验', () => {
    expect(validateSsid('  ST001-Express  ', { list: ROWS })).toBe(SSID_DUPLICATE_TEXT)
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

describe('rowErrors / countEmptySsid · 行内错误与汇总', () => {
  it('返回 { ssid, bssid } 两字段错误', () => {
    const r = rowErrors({ ssid: '', bssid: 'bad' }, { list: ROWS })
    expect(r.ssid).toBe(SSID_EMPTY_TEXT)
    expect(r.bssid).toBe(BSSID_INVALID_TEXT)
  })

  it('统计未填 SSID 的条数（含新增行的空值哨兵）', () => {
    expect(countEmptySsid(ROWS)).toBe(0)
    expect(countEmptySsid([...ROWS, { key: NEW_WIFI_KEY, ssid: '', bssid: '' }])).toBe(1)
  })
})

describe('权限分支与 extra 文案', () => {
  it('仅 ADMIN 可编辑；非 ADMIN / 未定角色一律只读（防御位）', () => {
    expect(isWifiEditable(true)).toBe(true)
    expect(isWifiEditable(false)).toBe(false)
    expect(isWifiEditable(undefined)).toBe(false)
  })

  it('卡标题 extra 按权限分支给「可编辑 / 只读」', () => {
    expect(wifiExtraLabel(2, true)).toBe('2 条 · 可编辑')
    expect(wifiExtraLabel(0, false)).toBe('0 条 · 只读')
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
