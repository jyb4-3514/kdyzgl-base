import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getNotifications, getUnreadCount, markAllNotificationsRead, markNotificationRead } from '../api/notification.js'
import { readToken } from '../utils/authStorage.js'

/**
 * 未读通知角标 + 通知读写（组件不再直连 api，P1-7）
 * 单独成 store 的原因：Tabbar 角标与通知页分处两个组件，角标是它们唯一的共享状态；
 * 各页面无需自行请求未读数，避免同一数字四处实现。
 */
export const useNotifyStore = defineStore('mobileNotify', () => {
  const unread = ref(0)

  async function refresh() {
    if (!readToken()) {
      unread.value = 0
      return
    }
    try {
      const data = await getUnreadCount()
      unread.value = data.count
    } catch (e) {
      // 角标属辅助信息，失败时静默（页面主体已由各自的请求提示兜底）
    }
  }

  /** 通知页本地增减后直接回写，省掉一次请求 */
  function set(count) {
    unread.value = Math.max(0, Number(count) || 0)
  }

  /** 通知列表分页查询：读接口经 store 转发，组件层不再 import api */
  async function fetchList(params) {
    return getNotifications(params)
  }

  /** 标记单条已读并同步角标；返回更新后的整条通知，供列表就地替换而不是整页重拉 */
  async function markRead(id) {
    const updated = await markNotificationRead(id)
    set(unread.value - 1)
    return updated
  }

  /** 全部已读：成功后角标直接归零，省掉一次未读数请求 */
  async function markAllRead() {
    await markAllNotificationsRead()
    set(0)
  }

  function clear() {
    unread.value = 0
  }

  return { unread, refresh, set, clear, fetchList, markRead, markAllRead }
})
