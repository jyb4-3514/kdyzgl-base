import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 补卡表单的三条契约：
 * 1. 日期/时段/卡类型由 open() 从接口数据带齐，员工只填理由（少一次选错槽位的机会）；
 * 2. 理由去空白后 < 2 字不可提交，且不发出请求；
 * 3. 提交无论成败都回读（onSettled），失败错误留在弹层内而不是关掉弹层。
 */
const mocks = vi.hoisted(() => ({ applyMakeup: vi.fn(), success: vi.fn() }))

vi.mock('@/api/attendance.js', () => ({ applyMakeup: mocks.applyMakeup }))
vi.mock('vant', () => ({ showSuccessToast: mocks.success }))

const { useMakeupForm } = await import('./useMakeupForm.js')

const PERIOD = { periodIndex: 1, name: '下午班' }

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useMakeupForm · 打开与校验', () => {
  it('open 带齐日期/时段/卡类型，并清空上次输入与错误', () => {
    const form = useMakeupForm()
    form.reason.value = '旧理由'
    form.error.value = '旧错误'

    form.open(PERIOD, 'OFF', '2026-09-24')

    expect(form.show.value).toBe(true)
    expect(form.target.value).toEqual({
      workDate: '2026-09-24',
      periodIndex: 1,
      periodName: '下午班',
      checkType: 'OFF'
    })
    expect(form.reason.value).toBe('')
    expect(form.error.value).toBe('')
  })

  it('理由去空白后不足 2 字一律不可提交（空串 / 单字 / 纯空白 / 前后空白夹单字）', () => {
    const form = useMakeupForm()
    for (const text of ['', 'x', ' ', ' a ']) {
      form.reason.value = text
      expect(form.canSubmit.value).toBe(false)
    }
    form.reason.value = '外出'
    expect(form.canSubmit.value).toBe(true)
  })
})

describe('useMakeupForm · 提交', () => {
  it('理由不足 2 字直接返回，不发请求', async () => {
    const form = useMakeupForm()
    form.open(PERIOD, 'ON', '2026-09-24')
    form.reason.value = 'x'

    await form.submit()

    expect(mocks.applyMakeup).not.toHaveBeenCalled()
  })

  it('提交成功：入库理由已去空白、关闭弹层、Toast 并回读', async () => {
    const onSettled = vi.fn().mockResolvedValue(undefined)
    mocks.applyMakeup.mockResolvedValue({})
    const form = useMakeupForm({ onSettled })
    form.open(PERIOD, 'OFF', '2026-09-24')
    form.reason.value = '  外出取件错过下班打卡  '

    await form.submit()

    expect(mocks.applyMakeup).toHaveBeenCalledWith({
      workDate: '2026-09-24',
      periodIndex: 1,
      checkType: 'OFF',
      reason: '外出取件错过下班打卡'
    })
    expect(form.show.value).toBe(false)
    expect(mocks.success).toHaveBeenCalledTimes(1)
    expect(onSettled).toHaveBeenCalledTimes(1)
    expect(form.submitting.value).toBe(false)
  })

  it('提交失败：错误落在弹层内、弹层保持打开、仍回读（9108 已有申请或已打卡）', async () => {
    const onSettled = vi.fn().mockResolvedValue(undefined)
    mocks.applyMakeup.mockRejectedValue({ code: 9108, message: '' })
    const form = useMakeupForm({ onSettled })
    form.open(PERIOD, 'ON', '2026-09-24')
    form.reason.value = '外出取件'

    await form.submit()

    expect(form.show.value).toBe(true)
    expect(form.error.value).toContain('我的补卡申请')
    expect(mocks.success).not.toHaveBeenCalled()
    expect(onSettled).toHaveBeenCalledTimes(1)
    expect(form.submitting.value).toBe(false)
  })

  it('提交中重复调用不重复发请求（防连点）', async () => {
    let release
    mocks.applyMakeup.mockImplementation(() => new Promise((resolve) => (release = resolve)))
    const form = useMakeupForm()
    form.open(PERIOD, 'ON', '2026-09-24')
    form.reason.value = '外出取件'

    const first = form.submit()
    await form.submit()
    expect(mocks.applyMakeup).toHaveBeenCalledTimes(1)

    release({})
    await first
  })

  it('未提供 onSettled 时不抛错（边界：调用方不需要回读）', async () => {
    mocks.applyMakeup.mockResolvedValue({})
    const form = useMakeupForm()
    form.open(PERIOD, 'ON', '2026-09-24')
    form.reason.value = '外出取件'

    await expect(form.submit()).resolves.toBeUndefined()
  })
})
