import { describe, expect, it } from 'vitest'
import { resolveInitialTab, failedRowClassOf } from './syncMeta.js'

/**
 * 同步页 Tab 契约与行类名的回归网
 * 重点钉住：?tab=config 仅 ADMIN 可停留（B.1.1），失败行才带展开图标标识（A6-3）。
 */
describe('resolveInitialTab · ?tab= 回落规则', () => {
  it('合法 Tab 原样透出（ADMIN）', () => {
    expect(resolveInitialTab('collect', true)).toBe('collect')
    expect(resolveInitialTab('config', true)).toBe('config')
    expect(resolveInitialTab('batch', true)).toBe('batch')
  })

  it('站长直达 config 回落批次流水，避免空 Tab', () => {
    expect(resolveInitialTab('config', false)).toBe('batch')
    expect(resolveInitialTab('collect', false)).toBe('collect')
  })

  it('非法值与缺失值一律回落批次流水', () => {
    expect(resolveInitialTab(undefined, true)).toBe('batch')
    expect(resolveInitialTab('unknown', true)).toBe('batch')
    expect(resolveInitialTab(['config'], true)).toBe('batch')
  })
})

describe('failedRowClassOf · 失败行标识', () => {
  it('仅失败（status=3）带 is-failed-row，其余为空串', () => {
    expect(failedRowClassOf({ row: { status: 3 } })).toBe('is-failed-row')
    expect(failedRowClassOf({ row: { status: 0 } })).toBe('')
    expect(failedRowClassOf({ row: { status: 2 } })).toBe('')
  })
})
