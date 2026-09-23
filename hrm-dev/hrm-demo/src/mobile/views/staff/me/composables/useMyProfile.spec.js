import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * useMyProfile 的两条契约：
 * 1. state 由 store 的三标志派生 —— 未取到资料即 loading、失败即 error、拿到资料即 ready；
 * 2. retry 只走 store 的 refreshMe，失败不抛出、不弹第二条提示（原因由 store 记录、页面回显）。
 */
const mocks = vi.hoisted(() => ({ readToken: vi.fn(), readUser: vi.fn(), getMe: vi.fn() }))

vi.mock('@/mobile/utils/authStorage.js', () => ({
  readToken: mocks.readToken,
  readUser: mocks.readUser,
  writeAuth: vi.fn(),
  clearAuth: vi.fn()
}))
vi.mock('@/mobile/api/auth.js', () => ({ getMe: mocks.getMe, login: vi.fn(), logout: vi.fn(), updatePassword: vi.fn() }))

const { useAuthStore } = await import('@/mobile/stores/auth.js')
const { useMyProfile } = await import('./useMyProfile.js')

const USER = { realName: '张三', role: 'STAFF', username: 'st001_staff' }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mocks.readToken.mockReturnValue('demo-token')
  mocks.readUser.mockReturnValue(null)
})

describe('useMyProfile · 三态派生', () => {
  it('未取到资料时 state=loading', () => {
    expect(useMyProfile().state.value).toBe('loading')
  })

  it('refreshMe 成功后 state=ready 且无失败原因', async () => {
    mocks.getMe.mockResolvedValue(USER)
    const auth = useAuthStore()
    const { state } = useMyProfile()
    await auth.refreshMe()

    expect(state.value).toBe('ready')
    expect(auth.userError).toBe('')
  })

  it('refreshMe 失败后 state=error 并记录原因', async () => {
    mocks.getMe.mockRejectedValue(new Error('账号信息获取失败'))
    const auth = useAuthStore()
    const { state } = useMyProfile()
    await expect(auth.refreshMe()).rejects.toThrow('账号信息获取失败')

    expect(state.value).toBe('error')
    expect(auth.userError).toBe('账号信息获取失败')
  })
})

describe('useMyProfile · retry', () => {
  it('失败后重试成功，态复位为 ready', async () => {
    mocks.getMe.mockRejectedValueOnce(new Error('账号信息获取失败')).mockResolvedValueOnce(USER)
    const auth = useAuthStore()
    const { state, retry } = useMyProfile()
    await auth.refreshMe().catch(() => {})
    expect(state.value).toBe('error')

    await retry()
    expect(state.value).toBe('ready')
  })

  it('重试再失败不抛出（错误已由 store 记录并回显，不重复提示）', async () => {
    mocks.getMe.mockRejectedValue(new Error('账号信息获取失败'))
    const { state, retry } = useMyProfile()

    await expect(retry()).resolves.toBeUndefined()
    expect(state.value).toBe('error')
  })
})