import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeWorkOrderStatus, getWorkOrderDetail } from '../../../api/workOrder.js'
import { TRANSITIONS, actionLabelOf } from '../model/workOrderMeta.js'

/**
 * 工单详情与流转处置
 *
 * 权限一律镜像 Mock（= 未来后端）的 canManage：ADMIN 全量 / 本站站长 / 本人为当前处理人。
 * 本 composable 只决定「按当前状态渲染哪些合法动作」，非法跳转仍由服务端 8001 兜底。
 */
export function useWorkOrderDetail({ isAdmin, currentUser, refreshPage }) {
  const detailVisible = ref(false)
  const detail = ref(null)
  const loadingDetail = ref(false)
  const acting = ref(false)

  /** 流转操作权限：镜像 Mock 的 canManage */
  function canManage(order) {
    const user = currentUser.value
    if (!user.role) return false
    if (user.role === 'ADMIN') return true
    if (user.role === 'STATION_ADMIN') return order.stationId === user.stationId
    return order.assigneeId === user.id
  }

  /**
   * 指派入口仅对 ADMIN 开放：/employees 是 ADMIN 专属接口，非 ADMIN 拿不到本站员工列表供选择。
   * TODO(扩展): 待后端提供「本站员工简表」接口后，按 Mock 的 canAssign 规则对站长开放指派
   */
  const canAssign = computed(() => isAdmin.value && detail.value && detail.value.status !== 3)

  /** 转单入口：权限与流转同口径 */
  const canTransfer = computed(() => !!detail.value && canManage(detail.value))

  /**
   * 时间线事件：转单在留痕表里有结构化数据（转出人 → 转入人 + 理由），
   * 故从 handleLog 中剔除同名事件、改用 transfers 渲染，避免同一次转单在时间线上出现两条。
   */
  const timelineEvents = computed(() => {
    const order = detail.value
    if (!order) return []
    const logs = (order.handleLog || []).reduce((acc, log, index) => {
      if (log.action === 'transfer') return acc
      acc.push({
        key: `log-${index}`,
        time: log.time,
        action: log.action,
        operatorName: log.operatorName,
        content: log.content
      })
      return acc
    }, [])
    const transfers = (order.transfers || []).map((item) => ({
      key: `transfer-${item.id}`,
      time: item.transferTime,
      action: 'transfer',
      operatorName: item.operatorName,
      fromName: item.fromEmployeeName,
      toName: item.toEmployeeName,
      reason: item.reason
    }))
    // 两段数据都按时间串（YYYY-MM-DD HH:mm:ss）递增拼回单一时间线，字符串比较等价于时间比较
    return [...logs, ...transfers].sort((a, b) => (a.time < b.time ? -1 : 1))
  })

  const availableActions = computed(() => {
    if (!detail.value || !canManage(detail.value)) return []
    return (TRANSITIONS[detail.value.status] || []).map((target) => ({
      target,
      label: actionLabelOf(detail.value.status, target),
      // 关闭是终态但非危险操作（info）；驳回重开单独用 warning 描边，避免与「接单」同款
      type: target === 3 ? 'info' : target === 1 && detail.value.status === 2 ? 'warning' : 'primary',
      plain: target === 1 && detail.value.status === 2
    }))
  })

  async function loadDetail(id) {
    loadingDetail.value = true
    try {
      detail.value = await getWorkOrderDetail(id)
    } catch (e) {
      detail.value = null
    } finally {
      loadingDetail.value = false
    }
  }

  async function openDetail(id) {
    detailVisible.value = true
    detail.value = null
    await loadDetail(id)
  }

  /**
   * 流转：先弹原因（未处理直关必填），确认后才提交。
   * TODO(扩展): 「待处理直关」的原因改用与转单同款的 el-dialog + textarea(show-word-limit)（A7-7），
   * 当前用 ElMessageBox.prompt 单行输入，与转单理由不是一套形态
   */
  async function handleTransition(target) {
    const order = detail.value
    if (!order) return
    const label = actionLabelOf(order.status, target)
    const closeReasonRequired = target === 3 && order.status === 0
    let remark = ''
    try {
      const result = await ElMessageBox.prompt(
        closeReasonRequired ? '未处理的工单需填写关闭原因' : '可填写处理说明（选填）',
        label,
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPlaceholder: closeReasonRequired ? '关闭原因（必填）' : '处理说明',
          inputValidator: (text) => (closeReasonRequired && !String(text || '').trim() ? '请填写关闭原因' : true)
        }
      )
      remark = result.value || ''
    } catch (e) {
      return // 用户取消
    }

    acting.value = true
    try {
      await changeWorkOrderStatus(order.id, target, remark)
      ElMessage.success(`${label}成功`)
      await refreshPage()
      await loadDetail(order.id)
    } catch (e) {
      /* 拦截器已统一提示（8001/8002） */
    } finally {
      acting.value = false
    }
  }

  return {
    detailVisible,
    detail,
    loadingDetail,
    acting,
    canManage,
    canAssign,
    canTransfer,
    timelineEvents,
    availableActions,
    loadDetail,
    openDetail,
    handleTransition
  }
}
