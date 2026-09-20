import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 角标单点收敛断言：未读数只有这一个写入口，
 * 页脚 Tabbar 与通知页都读它，避免「首页说 3 条、消息页说 2 条」。
 */
const mocks = vi.hoisted(() => ({
  readToken: vi.fn(),
  getUnreadCount: vi.fn(),
  getNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn()
}))

vi.mock('../utils/authStorage.js', () => ({ readToken: mocks.readToken }))
vi.mock('../api/index.js', () => ({
  getUnreadCount: mocks.getUnreadCount,
  getNotifications: mocks.getNotifications,
  markNotificationRead: mocks.markNotificationRead,
  markAllNotificationsRead: mocks.markAllNotificationsRead
}))

const { useNotifyStore } = await import('./notify.js')
const { badgeText } = await import('../utils/format.js')

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mocks.readToken.mockReturnValue('demo-token')
  mocks.getUnreadCount.mockResolvedValue({ count: 7 })
})

describe('useNotifyStore · 数值钳制', () => {
  it('负值归零（角标不能出现 -1）', () => {
    const store = useNotifyStore()
    store.set(-3)
    expect(store.unread).toBe(0)
  })

  it('NaN / 非数字 / null / undefined 一律归零', () => {
    const store = useNotifyStore()
    for (const bad of [NaN, 'abc', null, undefined, {}]) {
      store.set(12)
      store.set(bad)
      expect(store.unread).toBe(0)
    }
  })

  it('小数按原值保留（钳制只处理非法与非负，不改口径）', () => {
    const store = useNotifyStore()
    store.set(2.5)
    expect(store.unread).toBe(2.5)
  })

  it('超 99 不在 store 里截断，收敛交给展示层的 badgeText', () => {
    const store = useNotifyStore()
    store.set(120)
    expect(store.unread).toBe(120)
    expect(badgeText(store.unread)).toBe('99+')
    expect(badgeText(99)).toBe('99')
  })
})

describe('useNotifyStore · refresh', () => {
  it('成功时覆盖为接口值', async () => {
    const store = useNotifyStore()
    await store.refresh()
    expect(store.unread).toBe(7)
  })

  it('失败时静默保留旧值，绝不把角标归零冒充「没有未读」', async () => {
    const store = useNotifyStore()
    store.set(5)
    mocks.getUnreadCount.mockRejectedValue(new Error('网络异常'))
    await store.refresh()
    expect(store.unread).toBe(5)
  })

  it('未登录时不发请求并归零', async () => {
    mocks.readToken.mockReturnValue('')
    const store = useNotifyStore()
    store.set(9)
    await store.refresh()
    expect(store.unread).toBe(0)
    expect(mocks.getUnreadCount).not.toHaveBeenCalled()
  })
})

describe('useNotifyStore · 读写联动', () => {
  it('标记单条已读后角标减一', async () => {
    mocks.markNotificationRead.mockResolvedValue({ id: 1, isRead: true })
    const store = useNotifyStore()
    store.set(3)
    const updated = await store.markRead(1)
    expect(updated).toEqual({ id: 1, isRead: true })
    expect(store.unread).toBe(2)
  })

  it('角标为 0 时再标记已读不会变成负数', async () => {
    mocks.markNotificationRead.mockResolvedValue({ id: 1, isRead: true })
    const store = useNotifyStore()
    await store.markRead(1)
    expect(store.unread).toBe(0)
  })

  it('全部已读后直接归零', async () => {
    mocks.markAllNotificationsRead.mockResolvedValue(undefined)
    const store = useNotifyStore()
    store.set(8)
    await store.markAllRead()
    expect(store.unread).toBe(0)
  })

  it('clear 清空角标', () => {
    const store = useNotifyStore()
    store.set(6)
    store.clear()
    expect(store.unread).toBe(0)
  })
})
