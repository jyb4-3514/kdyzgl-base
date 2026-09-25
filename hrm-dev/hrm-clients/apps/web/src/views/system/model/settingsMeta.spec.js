import { describe, expect, it } from 'vitest'
import {
  APP_VERSION,
  PERMISSION_NOTES,
  filterRows,
  formatSize,
  parseBrowser,
  resolveDataScope,
  resolvePresetScale,
  resolveRuntimeMode,
  resolveTimezone
} from './settingsMeta.js'

/**
 * 系统设置页字段口径的回归网
 * 重点钉住三件事：UA 特征串的优先级、取不到值时的降级（空串 / 不适用 / 未知），以及"空值整行不渲染"的过滤规则。
 */

describe('parseBrowser · 浏览器识别', () => {
  const UA = {
    edge: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.2210.61',
    chrome: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36',
    firefox: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0',
    safari:
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15'
  }

  it('Edge 命中 Edg/ 而非 Chrome（特征串顺序：更具体者在前）', () => {
    expect(parseBrowser(UA.edge)).toBe('Edge 120')
  })

  it('Chrome 命中 Chrome/ 而非 Safari', () => {
    expect(parseBrowser(UA.chrome)).toBe('Chrome 120')
  })

  it('Firefox 与 Safari 各取主版本', () => {
    expect(parseBrowser(UA.firefox)).toBe('Firefox 121')
    expect(parseBrowser(UA.safari)).toBe('Safari 17')
  })

  it('识别不出与非法入参一律降级为「未知浏览器」，不抛异常', () => {
    expect(parseBrowser('Mozilla/5.0 (compatible; SomeBot/1.0)')).toBe('未知浏览器')
    expect(parseBrowser('')).toBe('未知浏览器')
    expect(parseBrowser(undefined)).toBe('未知浏览器')
    expect(parseBrowser(12345)).toBe('未知浏览器')
  })
})

describe('resolveRuntimeMode · 运行模式', () => {
  it('只有显式 true / "true" 才算演示态（与 main.js 装配 Mock 的判定一致）', () => {
    expect(resolveRuntimeMode(true)).toMatchObject({ isDemo: true, label: '演示态（Mock 数据）', type: 'warning' })
    expect(resolveRuntimeMode('true')).toMatchObject({ isDemo: true, type: 'warning' })
  })

  it('未配置 / 拼错 / false 一律按生产态（fail-safe 向生产倾斜）', () => {
    for (const raw of [false, 'false', undefined, '', 'TRUE', 1]) {
      expect(resolveRuntimeMode(raw)).toMatchObject({ isDemo: false, label: '生产态（真实接口）', type: 'info' })
    }
  })
})

describe('resolveDataScope · 数据范围', () => {
  it('三级角色各自的可见范围', () => {
    expect(resolveDataScope('ADMIN')).toBe('全域')
    expect(resolveDataScope('STATION_ADMIN')).toBe('本站')
    expect(resolveDataScope('STAFF')).toBe('个人')
  })

  it('未知角色不猜范围，返回「未知」', () => {
    expect(resolveDataScope('BOSS')).toBe('未知')
    expect(resolveDataScope(undefined)).toBe('未知')
  })
})

describe('resolveTimezone · 时区', () => {
  it('正常返回 IANA 时区名', () => {
    const intl = { DateTimeFormat: () => ({ resolvedOptions: () => ({ timeZone: 'Asia/Shanghai' }) }) }
    expect(resolveTimezone(intl)).toBe('Asia/Shanghai')
  })

  it('Intl 不支持 / timeZone 非字符串 → 空串（页面据此不渲染该行）', () => {
    expect(resolveTimezone(undefined)).toBe('')
    expect(resolveTimezone({})).toBe('')
    const bad = { DateTimeFormat: () => ({ resolvedOptions: () => ({ timeZone: 8 }) }) }
    expect(resolveTimezone(bad)).toBe('')
  })

  it('resolvedOptions 抛异常时静默降级，不向上冒泡', () => {
    const throwing = {
      DateTimeFormat: () => ({
        resolvedOptions: () => {
          throw new Error('受限环境')
        }
      })
    }
    expect(resolveTimezone(throwing)).toBe('')
  })
})

describe('formatSize · 分辨率/视口', () => {
  it('数字间用全角乘号', () => {
    expect(formatSize(1920, 1080)).toBe('1920 × 1080')
    expect(formatSize(0, 0)).toBe('0 × 0')
  })

  it('任一边缺失即返回空串（不渲染半截尺寸）', () => {
    expect(formatSize(null, 1080)).toBe('')
    expect(formatSize(1920, undefined)).toBe('')
  })
})

describe('resolvePresetScale · 预置规模（声明值）', () => {
  it('生产态显式给「不适用」，而不是留空让人以为漏读', () => {
    expect(resolvePresetScale(false, 200000)).toBe('不适用')
    expect(resolvePresetScale(undefined, undefined)).toBe('不适用')
  })

  it('演示态取千分位数量', () => {
    expect(resolvePresetScale('true', 200000)).toBe('200,000')
    expect(resolvePresetScale(true, '3000')).toBe('3,000')
  })

  it('演示态但数量缺失 / 非法 → 空串（该行不渲染）', () => {
    expect(resolvePresetScale(true, undefined)).toBe('')
    expect(resolvePresetScale(true, 'abc')).toBe('')
    expect(resolvePresetScale(true, 0)).toBe('')
    expect(resolvePresetScale(true, -1)).toBe('')
  })
})

describe('filterRows · 空值整行不渲染', () => {
  it('丢掉 null / undefined / 空串 / 纯空白，保留 0 与 false 这类合法值', () => {
    const rows = filterRows([
      { label: 'a', value: 'x' },
      { label: 'b', value: '' },
      { label: 'c', value: null },
      { label: 'd', value: undefined },
      { label: 'e', value: '   ' },
      { label: 'f', value: 0 }
    ])
    expect(rows.map((r) => r.label)).toEqual(['a', 'f'])
  })

  it('非数组与空集不炸', () => {
    expect(filterRows(undefined)).toEqual([])
    expect(filterRows(null)).toEqual([])
    expect(filterRows([])).toEqual([])
  })
})

describe('常量 · 权限说明与版本号', () => {
  it('权限说明覆盖三级角色', () => {
    expect(PERMISSION_NOTES).toHaveLength(3)
    expect(PERMISSION_NOTES.join('')).toContain('超级管理员')
    expect(PERMISSION_NOTES.join('')).toContain('站长')
    expect(PERMISSION_NOTES.join('')).toContain('员工')
  })

  it('APP_VERSION 在未注入构建（单测环境）下为空串，页面据此不渲染版本号行', () => {
    expect(typeof APP_VERSION).toBe('string')
    expect(APP_VERSION).toBe('')
  })
})
