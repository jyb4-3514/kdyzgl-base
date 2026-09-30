import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMakeupList, getMyMakeups } from '../api/attendance.js'
import { getPayrolls, getMyPayrolls } from '../api/finance.js'
import { getOffboardingFlows, getOnboardingFlows } from '../api/hr.js'
import { getLeaveList, getMyLeaves } from '../api/leave.js'
import { getWorkOrders } from '../api/workOrder.js'
import { CHECK_TYPE, LEAVE_TYPE, PAYROLL_STATUS, WORK_ORDER_PRIORITY, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import { canAccess } from '@kdyzgl/shared/domain/permission.js'
import { BOSS_TODO_GROUPS } from '../constants/todoGroups.js'
import { readToken } from '../utils/authStorage.js'
import { moneyText } from '../utils/format.js'
import { useAuthStore } from './auth.js'

/**
 * 待办快照（A4-3 的收敛点）
 *
 * 为什么单独成 store：待办计数同时被三处消费 —— 首页宫格角标、消息 Tab 角标、消息页「待办」子视图。
 * 三处各拉一遍接口必然出现「首页说 3 条、消息页说 2 条」的口径漂移，且首屏请求翻三倍。
 *
 * 取值约定：`total` 为 `null` 表示「取数失败 / 未知」，`0` 表示「确实没有待办」。
 * 两者在角标与文案上表现不同，绝不可互相顶替（B4-2 硬规则 2）。
 *
 * 取数职责已从 constants/todoGroups.js 迁入本文件的 LOADERS：配置层只留静态描述（消 G3）。
 */

/** 入离职流程类型 → 中文（契约无 flowName，语义由 flowType 唯一确定，不臆造字段） */
const flowLabel = (flowType) => (flowType === 'OFFBOARDING' ? '离职' : '入职')

/** 假别 → 中文：字典缺项回 '-'，不臆造 */
const leaveTypeLabel = (leaveType) => dictLabel(LEAVE_TYPE, leaveType)

/**
 * 分组取数：每个 key = 「一次请求 + 一套行文案」。
 * 行文案与接口字段强绑定，故与 constants 的静态描述分开放；新增一类待办 = 加一条配置 + 一个 loader。
 */
const LOADERS = {
  orders: async ({ params }) => {
    const page = await getWorkOrders(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `#${item.orderNo} ${item.title}`,
        meta: item.stationName,
        tag: { dict: WORK_ORDER_PRIORITY, value: item.priority }
      }))
    }
  },
  makeups: async ({ params }) => {
    const page = await getMakeupList(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.employeeName} ${item.workDate}`,
        meta: `${item.periodName} · ${dictLabel(CHECK_TYPE, item.checkType)}`,
        // 事由为可选 note：审批中心据此「先判要不要批」，字段出参已含、仅映射层补取（设计 ⑧.3 / O6）
        note: item.reason
      }))
    }
  },
  payrolls: async ({ params }) => {
    const page = await getPayrolls(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.month} ${item.stationName} 工资单`,
        // 实发金额让审批人在列表即可判断金额，减少「点进详情才发现不是这单」的往返（O6）
        meta: `${item.employeeName} · 实发 ${moneyText(item.netAmount)}`,
        tag: { dict: PAYROLL_STATUS, value: item.status }
      }))
    }
  },
  // 异议待办（C-1）：异议单落 OBJECTED，与「待审核（PENDING_APPROVAL）」分属两组，避免互相淹没
  payrollObjections: async ({ params }) => {
    const page = await getPayrolls(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.month} ${item.employeeName} 工资单`,
        meta: item.objectionReason || '员工提出异议，待重新核定',
        tag: { dict: PAYROLL_STATUS, value: item.status }
      }))
    }
  },
  flows: async ({ params }) => {
    // 两类流程是两个端点的两个 total，合并口径固定写在这里，避免首页与消息页各算一遍
    const [onboarding, offboarding] = await Promise.all([getOnboardingFlows(params), getOffboardingFlows(params)])
    const rows = [...onboarding.list, ...offboarding.list].slice(0, 3).map((item) => ({
      key: `${item.flowType}-${item.id}`,
      title: `${item.employeeName} ${flowLabel(item.flowType)}`,
      meta: `${item.stationName || '总部'} · ${item.currentStepName || '待办理'}`,
      // 业务来源（后端 HrFlowVO.source：ADMIN / SELF_REGISTER）：审批中心据此识别「员工注册」（设计 ⑧.4）；
      // 缺省 null → 不渲染标识，不臆造来源
      source: item.source || null
    }))
    return { total: onboarding.total + offboarding.total, rows }
  },
  leaves: async ({ params }) => {
    const page = await getLeaveList(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.employeeName} ${leaveTypeLabel(item.leaveType)}`,
        meta: `${item.startDate} ~ ${item.endDate} · ${item.countedDays} 天`
      }))
    }
  },
  myPayrolls: async ({ params }) => {
    const page = await getMyPayrolls(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.month} 工资单待确认`,
        meta: `实发 ${moneyText(item.netAmount)}`,
        tag: { dict: PAYROLL_STATUS, value: item.status }
      }))
    }
  },
  myMakeups: async ({ params }) => {
    const page = await getMyMakeups(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.workDate} ${item.periodName} · ${dictLabel(CHECK_TYPE, item.checkType)}`,
        meta: '审批中，通过后系统自动补录记录'
      }))
    }
  },
  myLeaves: async ({ params }) => {
    // 'PENDING' 是服务端展开的聚合虚拟值（待初审 + 待终审），前端只传一个值，不做两次请求求和
    const page = await getMyLeaves(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.startDate} ~ ${item.endDate} ${leaveTypeLabel(item.leaveType)}`,
        meta: '审批中，通过后生效'
      }))
    }
  },
  leaveReview: async ({ params }) => {
    const page = await getLeaveList(params)
    return {
      total: page.total,
      rows: page.list.map((item) => ({
        key: item.id,
        title: `${item.employeeName} ${item.startDate} ~ ${item.endDate}`,
        meta: `${leaveTypeLabel(item.leaveType)} · 待初审`
      }))
    }
  }
}

/**
 * 审批中心白名单（boss-management-ui-design.md ⑧.6）：视图过滤口径，**不含工单 orders**。
 * 为什么不放 constants/todoGroups.js：那是「数据定义」单一真源，本常量表达的是
 * 「审批中心这个视图要哪几组」，属视图过滤，换视图可改而不动取数配置。
 */
const APPROVAL_KEYS = ['makeups', 'leaves', 'flows', 'payrolls', 'payrollObjections']

/**
 * 分组跳转目标：配置带字符串 status 时附加 query，落到业务页「该组状态」筛选视图。
 * 为什么只认字符串：工单组 params.status 是数字 0，工单页不消费 query，附加只会产生无意义参数；
 * 其余 5 组（补卡/请假/入离职/工资单/异议）status 均为字符串枚举，正是设计 ⑧.3 要求的筛选口径。
 */
function toTarget(config) {
  const status = config.params && config.params.status
  return typeof status === 'string' && status ? { path: config.to, query: { status } } : config.to
}

export const useTodoStore = defineStore('mobileTodo', () => {
  const auth = useAuthStore()

  /** 分组快照（含前 3 条明细），结构由 constants/todoGroups.js 的静态描述 + 本文件 LOADERS 共同定义 */
  const groups = ref([])
  const loading = ref(false)

  /** 按角色取分组：管理端恒为 5 类（A4-3 + 待终审请假，MVP 裁剪下架「采集异常」组）。端固定化（B4）后本端只有管理端配置。
   *  组级 roles 未声明 = 所有能看到本表的角色都可见。 */
  const configs = computed(() => BOSS_TODO_GROUPS.filter((item) => canAccess(item.roles, auth.user)))

  /** key → total，供首页宫格按 key 取角标值（null 即「未知」，不渲染角标） */
  const counts = computed(() => Object.fromEntries(groups.value.map((item) => [item.key, item.total])))

  /** 「待我处理」总数：取数失败的分组不计入（该部分按 0 计），全失败即 0 */
  const total = computed(() => groups.value.reduce((sum, item) => sum + (item.total || 0), 0))

  /** 是否至少有一组取数成功：全失败时消费方显示 `···` 而不是 0（B4-2 硬规则 2） */
  const known = computed(() => groups.value.some((item) => item.total !== null))

  /**
   * 审批中心角标（纯前端派生，不改契约）：5 组求和，**不含工单 orders**。
   * 未知传播：任一组的 total 为 null（取数失败/未知）→ 整值 null → 不渲染角标，
   * 绝不用「部分和」冒充合计（否则会与消息页总数形成假一致）。
   */
  const approvalTotal = computed(() => {
    const values = APPROVAL_KEYS.map((key) => counts.value[key] ?? null)
    return values.every((value) => value !== null) ? values.reduce((sum, value) => sum + value, 0) : null
  })

  /** 审批中心角标是否有确定值（未知时页头显示「待处理项获取中」而不是 0） */
  const approvalKnown = computed(() => APPROVAL_KEYS.every((key) => counts.value[key] != null))

  async function refresh() {
    if (!readToken()) {
      groups.value = []
      return
    }
    loading.value = true
    // 逐组独立降级：某一组接口挂了只让该组显示重试，不把整个待办页判成错误态（B4-2 硬规则 1）
    const results = await Promise.all(
      configs.value.map(async (config) => {
        try {
          const { total: count, rows } = await LOADERS[config.key](config)
          return { key: config.key, title: config.title, to: toTarget(config), total: count, rows, error: '' }
        } catch (e) {
          return {
            key: config.key,
            title: config.title,
            to: toTarget(config),
            total: null,
            rows: [],
            error: e.message || '加载失败'
          }
        }
      })
    )
    groups.value = results
    loading.value = false
  }

  function clear() {
    groups.value = []
  }

  return {
    groups,
    counts,
    total,
    known,
    approvalKeys: APPROVAL_KEYS,
    approvalTotal,
    approvalKnown,
    loading,
    refresh,
    clear
  }
})
