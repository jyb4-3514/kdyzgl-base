import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEmployees } from '@admin/api/employee'
import { ROLE_LABEL } from '@/shared/constants/role'
import { DEMO_CODE } from '@/shared/constants/errorCode'
import { getSchedules } from '../../../api/attendance.js'
import { assignWorkOrder, transferWorkOrder } from '../../../api/workOrder.js'

/**
 * 工单处置弹窗（指派 / 转单）
 *
 * 两者共用同一件事：改「处理人」。指派是 ADMIN 定向选人，转单是「ADMIN / 本站站长 / 当前处理人」带理由换人，
 * 候选范围与错误码口径各不相同，故同放一处便于对照，表单校验则下沉到各自弹窗组件内完成。
 */
export function useWorkOrderAllocator({ isAdmin, currentUser, refreshPage, loadDetail }) {
  const assignDialog = reactive({ visible: false, orderId: null, orderNo: '', assigneeId: undefined, loading: false })
  const assignees = ref([])

  const transferDialog = reactive({ visible: false, orderId: null, orderNo: '', fromName: '', loading: false })
  const transferOptions = ref([])
  const transferOptionsLoading = ref(false)

  /** 候选范围提示：把「为什么搜不到某个同事」讲在前面，减少无效尝试 */
  const transferScopeHint = computed(() => (isAdmin.value ? '请选择员工（老板可跨驿站）' : '请选择本站员工'))
  const transferCandidates = computed(() => transferOptions.value)

  /** 指派弹窗：处理人下拉取本站启用员工 */
  async function openAssign(order) {
    assignDialog.visible = true
    assignDialog.orderId = order.id
    assignDialog.orderNo = order.orderNo
    assignDialog.assigneeId = undefined
    assignees.value = []
    try {
      const page = await getEmployees({ stationId: order.stationId, status: 1, pageNum: 1, pageSize: 100 })
      assignees.value = page.list
    } catch (e) {
      /* 拦截器已统一提示 */
    }
  }

  async function submitAssign() {
    if (!assignDialog.assigneeId) {
      ElMessage.warning('请选择处理人')
      return
    }
    assignDialog.loading = true
    try {
      await assignWorkOrder(assignDialog.orderId, assignDialog.assigneeId)
      ElMessage.success('指派成功，已通知处理人')
      assignDialog.visible = false
      await refreshPage()
      await loadDetail(assignDialog.orderId)
    } catch (e) {
      /* 拦截器已统一提示（8002） */
    } finally {
      assignDialog.loading = false
    }
  }

  /**
   * 转单候选：按角色收敛范围，与后端 8004 同口径，减少必然失败的请求
   * - ADMIN 走 /employees（可选全域在职员工，支持跨站转单）
   * - 站长/处理人走 /schedules 的本站名册（/employees 是 ADMIN 专属，站长打开会 403）
   * 两种来源都排除本人：后端对「转给自己」直接回 8004
   * TODO(扩展): 排班名册不含在职状态，本站禁用员工仍会出现在下拉里（由 8004 兜底）；
   *   待后端提供「本站员工简表」接口后改为按 status 过滤，并复用给指派弹窗放开站长指派
   */
  async function openTransfer(order) {
    transferDialog.visible = true
    transferDialog.orderId = order.id
    transferDialog.orderNo = order.orderNo
    transferDialog.fromName = order.assigneeName
    transferOptions.value = []
    transferOptionsLoading.value = true
    const me = currentUser.value
    try {
      if (isAdmin.value) {
        const page = await getEmployees({ status: 1, pageNum: 1, pageSize: 100 })
        transferOptions.value = page.list
          .filter((item) => item.id !== me.id)
          .map((item) => ({
            id: item.id,
            label: `${item.realName}（${item.stationName || '总部'} · ${ROLE_LABEL[item.role] || item.role}）`
          }))
      } else {
        const matrix = await getSchedules({ stationId: me.stationId })
        transferOptions.value = matrix.employees
          .filter((item) => item.employeeId !== me.id)
          .map((item) => ({ id: item.employeeId, label: `${item.employeeName}（本站）` }))
      }
    } catch (e) {
      /* 拦截器已统一提示；下拉为空时提交会被表单必填拦下 */
    } finally {
      transferOptionsLoading.value = false
    }
  }

  /** 转单提交：表单校验已在弹窗内完成，这里只做二次确认与落库 */
  async function submitTransfer(payload) {
    const target = transferOptions.value.find((item) => item.id === payload.toEmployeeId)
    try {
      await ElMessageBox.confirm(
        `确认将工单 ${transferDialog.orderNo} 由「${transferDialog.fromName || '未指派'}」转给「${target ? target.label : ''}」？转单只变更处理人，工单状态不变。`,
        '转单确认',
        { confirmButtonText: '确认转单', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch (e) {
      return // 用户取消
    }

    transferDialog.loading = true
    try {
      // silent：8003/8004 需要给可操作文案（如「只能转给本站在职同事」），通用 toast 覆盖不到
      await transferWorkOrder(
        transferDialog.orderId,
        { toEmployeeId: payload.toEmployeeId, reason: payload.reason },
        { silent: true }
      )
      ElMessage.success('转单成功，已通知新处理人')
      transferDialog.visible = false
      await refreshPage()
      await loadDetail(transferDialog.orderId)
    } catch (e) {
      const code = e && e.code
      if (code === DEMO_CODE.WORK_ORDER_TRANSFER_TARGET_INVALID) {
        ElMessage.error('转单对象不合法：只能转给在职同事，不能转给自己；站长与处理人只能转本站同事')
      } else if (code === DEMO_CODE.WORK_ORDER_TRANSFER_NO_PERMISSION) {
        ElMessage.error('无权转单该工单：仅老板、本站站长或当前处理人可转单')
      } else {
        ElMessage.error((e && e.message) || '转单失败，请稍后重试')
      }
    } finally {
      transferDialog.loading = false
    }
  }

  return {
    assignDialog,
    assignees,
    transferDialog,
    transferOptions,
    transferOptionsLoading,
    transferScopeHint,
    transferCandidates,
    openAssign,
    submitAssign,
    openTransfer,
    submitTransfer
  }
}
