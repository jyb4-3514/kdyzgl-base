import { computed, onUnmounted, ref } from 'vue'

/**
 * 新建工单 / 企业微信自动派单入口编排
 *
 * 与列表、详情分开：这一块只管「入口默认值 + 新工单落地后的定位反馈」，
 * 不掺列表筛选与详情流转，避免页面壳里再堆一段状态。
 */
export function useWorkOrderCreate({ isAdmin, currentUser, query, stations, activeTab, refreshPage, openDetail }) {
  // 新建工单 / 自动派单（需求3）
  const createVisible = ref(false)
  const dispatchVisible = ref(false)
  // 新派发的工单高亮 3s，让演示现场一眼找到刚生成的那条
  const highlightId = ref(null)
  // 高亮定时器句柄：卸载时必须清掉，否则离开页面后回调仍会写已销毁组件的 ref（P1-5）
  let highlightTimer = null

  /** 建单 / 派单的默认归属驿站：ADMIN 取当前筛选值（未选则第一个驿站），站长固定本站 */
  const createStationId = computed(() => {
    if (!isAdmin.value) return currentUser.value.stationId || null
    if (query.stationId !== undefined && query.stationId !== null) return query.stationId
    return stations.value.length ? stations.value[0].id : null
  })

  const createStationName = computed(() => {
    const hit = stations.value.find((item) => item.id === createStationId.value)
    if (hit) return hit.stationName
    return isAdmin.value ? '' : currentUser.value.stationName || ''
  })

  /** 模拟群名称按当前驿站取名，贴近真实演示场景 */
  const defaultGroupName = computed(() => (createStationName.value ? `${createStationName.value}-异常件处理群` : ''))

  /** 新建成功后自动展开该工单详情（B3.2 交互流程） */
  async function handleCreated(result) {
    await refreshPage()
    if (result && result.id) await openDetail(result.id)
  }

  /**
   * 自动派单成功后：回到「全部」Tab 并刷新，再把新工单高亮 3s。
   * 切 Tab 的理由：新工单 status=0，若当前停在「已关闭」这类筛选下，高亮目标根本不在列表里，
   * 演示现场会以为「派单没成功」。
   */
  async function handleDispatched(order) {
    activeTab.value = 'all'
    query.pageNum = 1
    highlightId.value = order.id
    await refreshPage()
    await openDetail(order.id)
    // 连续派单时先清掉上一轮，避免旧回调提前把新高亮取消
    clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => {
      if (highlightId.value === order.id) highlightId.value = null
    }, 3000)
  }

  onUnmounted(() => clearTimeout(highlightTimer))

  return { createVisible, dispatchVisible, highlightId, createStationId, createStationName, defaultGroupName, handleCreated, handleDispatched }
}
