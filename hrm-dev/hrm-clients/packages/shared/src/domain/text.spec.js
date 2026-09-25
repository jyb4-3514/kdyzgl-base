// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { copyText } from './text.js'

/**
 * 降级链路断言（顺序不可颠倒）：
 * 安全上下文 + Clipboard API → execCommand 兜底 → 返回 false 由调用方提示。
 * jsdom 未实现 document.execCommand，未打桩时该调用会抛错，正好覆盖「两条通道都失败」。
 */
const setClipboard = (value) => Object.defineProperty(navigator, 'clipboard', { value, configurable: true })
const setSecureContext = (value) => Object.defineProperty(window, 'isSecureContext', { value, configurable: true })

let originalExecCommand

beforeEach(() => {
  originalExecCommand = document.execCommand
  setSecureContext(true)
  setClipboard(undefined)
})

afterEach(() => {
  document.execCommand = originalExecCommand
  setClipboard(undefined)
  // 兜底用的临时 textarea 必须被清理，不能留在页面上
  document.querySelectorAll('textarea').forEach((el) => el.remove())
})

describe('copyText', () => {
  it('空文本直接判失败，不去碰剪贴板', async () => {
    const writeText = vi.fn()
    setClipboard({ writeText })
    expect(await copyText('')).toBe(false)
    expect(await copyText(null)).toBe(false)
    expect(writeText).not.toHaveBeenCalled()
  })

  it('安全上下文下优先用 Clipboard API', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setClipboard({ writeText })
    expect(await copyText('工单详情')).toBe(true)
    expect(writeText).toHaveBeenCalledWith('工单详情')
  })

  it('Clipboard API 被拒时落到 execCommand 兜底', async () => {
    setClipboard({ writeText: vi.fn().mockRejectedValue(new Error('NotAllowedError')) })
    const execCommand = vi.fn().mockReturnValue(true)
    document.execCommand = execCommand
    expect(await copyText('兜底文本')).toBe(true)
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('非安全上下文（安卓壳 file:// / 局域网 http）跳过 Clipboard API 直接兜底', async () => {
    setSecureContext(false)
    const writeText = vi.fn()
    setClipboard({ writeText })
    document.execCommand = vi.fn().mockReturnValue(true)
    expect(await copyText('离线包文本')).toBe(true)
    expect(writeText).not.toHaveBeenCalled()
  })

  it('两条通道都失败返回 false，绝不静默成功', async () => {
    document.execCommand = vi.fn().mockReturnValue(false)
    expect(await copyText('复制不了')).toBe(false)
  })

  it('execCommand 抛异常也被吞掉并返回 false', async () => {
    document.execCommand = vi.fn(() => {
      throw new Error('内核不支持')
    })
    expect(await copyText('复制不了')).toBe(false)
  })

  it('兜底完成后临时 textarea 被移除', async () => {
    document.execCommand = vi.fn().mockReturnValue(true)
    await copyText('清理检查')
    expect(document.querySelectorAll('textarea')).toHaveLength(0)
  })
})
