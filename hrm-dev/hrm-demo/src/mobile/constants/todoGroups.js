import { CHECK_TYPE, LEAVE_TYPE, PAYROLL_STATUS, WORK_ORDER_PRIORITY, dictLabel } from '@/shared/constants/dict.js'
import { moneyText } from '../utils/format.js'
import {
  getLeaveList,
  getMakeupList,
  getMyLeaves,
  getMyMakeups,
  getMyPayrolls,
  getOffboardingFlows,
  getOnboardingFlows,
  getPayrolls,
  getSyncOverview,
  getWorkOrders
} from '../api/index.js'

/**
 * 消息 Tab「待办」子视图的分组配置（demo-mobile-nav-redesign.md A4-3 配置表，逐条落地）
 *
 * 为什么把配置与取数写在一起：每条待办 = 「一组静态描述 + 一次取数 + 一套行文案」，拆到三个文件后
 * 改一个接口要跳三处；收在这里后新增一类待办只加一个对象。
 * `load()` 只负责把接口返回翻译成 `{ total, rows }`，行的视觉形态由 TodoGroup 决定。
 *
 * 契约对齐说明：A4-3 老板端入离职行文案写 `{employeeName} {flowName}`，但 `/hr/onboarding|offboarding`
 * 的 VO 无 `flowName` 字段（仅有 `flowType`），故按 `flowType` 本地派生成「入职 / 离职」，不臆造字段。
 *
 * 组级角色白名单（M11 新增，向后兼容）：`roles` 缺省 = 所有能看到本表的角色都可见，
 * 既有 5 + 3 组无须改动；消费侧（stores/todo.js）用 shared/domain/permission.canAccess 按登录角色过滤。
 */

/** 入离职流程类型 → 中文（契约无 flowName，语义由 flowType 唯一确定） */
const flowLabel = (flowType) => (flowType === 'OFFBOARDING' ? '离职' : '入职')

/** 假别 → 中文：字典缺项回 '-'，不臆造 */
const typeLabel = (leaveType) => dictLabel(LEAVE_TYPE, leaveType)

const orderRow = (item) => ({
  key: item.id,
  title: `#${item.orderNo} ${item.title}`,
  meta: item.stationName,
  tag: { dict: WORK_ORDER_PRIORITY, value: item.priority }
})

const makeupRow = (item) => ({
  key: item.id,
  title: `${item.employeeName} ${item.workDate}`,
  meta: `${item.periodName} · ${dictLabel(CHECK_TYPE, item.checkType)}`
})

export const BOSS_TODO_GROUPS = [
  {
    key: 'orders',
    title: '待处理工单',
    to: '/boss/workorder',
    async load() {
      const page = await getWorkOrders({ status: 0, pageNum: 1, pageSize: 3 })
      return { total: page.total, rows: page.list.map(orderRow) }
    }
  },
  {
    key: 'makeups',
    title: '待审批补卡',
    to: '/boss/attendance/makeup',
    async load() {
      const page = await getMakeupList({ status: 'PENDING', pageNum: 1, pageSize: 3 })
      return { total: page.total, rows: page.list.map(makeupRow) }
    }
  },
  {
    key: 'payrolls',
    title: '待审核工资单',
    to: '/boss/payroll',
    async load() {
      const page = await getPayrolls({ status: 'PENDING_APPROVAL', pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `${item.month} ${item.stationName} 工资单`,
          meta: item.employeeName,
          tag: { dict: PAYROLL_STATUS, value: item.status }
        }))
      }
    }
  },
  {
    key: 'flows',
    title: '进行中入离职',
    to: '/boss/flow',
    async load() {
      // 两类流程是两个端点的两个 total，合并口径固定写在这里，避免首页与消息页各算一遍
      const [onboarding, offboarding] = await Promise.all([
        getOnboardingFlows({ status: 'IN_PROGRESS', pageNum: 1, pageSize: 3 }),
        getOffboardingFlows({ status: 'IN_PROGRESS', pageNum: 1, pageSize: 3 })
      ])
      const rows = [...onboarding.list, ...offboarding.list].slice(0, 3).map((item) => ({
        key: `${item.flowType}-${item.id}`,
        title: `${item.employeeName} ${flowLabel(item.flowType)}`,
        meta: `${item.stationName || '总部'} · ${item.currentStepName || '待办理'}`
      }))
      return { total: onboarding.total + offboarding.total, rows }
    }
  },
  {
    key: 'leaves',
    title: '待终审请假',
    to: '/boss/leave',
    async load() {
      const page = await getLeaveList({ status: 'PENDING_BOSS', pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `${item.employeeName} ${typeLabel(item.leaveType)}`,
          meta: `${item.startDate} ~ ${item.endDate} · ${item.countedDays} 天`
        }))
      }
    }
  },
  {
    key: 'collect',
    title: '采集异常 · 未配置',
    to: '/boss/alerts',
    async load() {
      const data = await getSyncOverview()
      const { abnormal, unconfigured } = data.counts
      return {
        total: abnormal + unconfigured,
        // 只读提醒：不需要「办理」，一行讲清「哪一站没在采、哪一站采挂了」
        rows: [
          { key: 'collect', title: `异常 ${abnormal} 站 · 未配置采集 ${unconfigured} 站`, meta: '采集配置在 PC 端维护' }
        ]
      }
    }
  }
]

export const STAFF_TODO_GROUPS = [
  {
    key: 'orders',
    title: '待处理工单',
    to: '/staff/workorder',
    async load() {
      const page = await getWorkOrders({ status: 0, pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `#${item.orderNo} ${item.title}`,
          meta: item.stationName,
          tag: { dict: WORK_ORDER_PRIORITY, value: item.priority }
        }))
      }
    }
  },
  {
    key: 'myPayrolls',
    title: '待确认工资单',
    to: '/staff/payroll',
    async load() {
      const page = await getMyPayrolls({ status: 'PUBLISHED', pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `${item.month} 工资单待确认`,
          meta: `实发 ${moneyText(item.netAmount)}`,
          tag: { dict: PAYROLL_STATUS, value: item.status }
        }))
      }
    }
  },
  {
    key: 'myMakeups',
    title: '我的补卡申请',
    to: '/staff/attendance/makeup',
    async load() {
      const page = await getMyMakeups({ status: 'PENDING', pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `${item.workDate} ${item.periodName} · ${dictLabel(CHECK_TYPE, item.checkType)}`,
          meta: '审批中，通过后系统自动补录记录'
        }))
      }
    }
  },
  {
    key: 'myLeaves',
    title: '我的请假申请',
    to: '/staff/leave',
    async load() {
      // 'PENDING' 是服务端展开的聚合虚拟值（待初审 + 待终审），前端只传一个值，不做两次请求求和
      const page = await getMyLeaves({ status: 'PENDING', pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `${item.startDate} ~ ${item.endDate} ${typeLabel(item.leaveType)}`,
          meta: '审批中，通过后生效'
        }))
      }
    }
  },
  {
    key: 'leaveReview',
    title: '待初审请假',
    to: '/staff/leave/review',
    roles: ['STATION_ADMIN'],
    async load() {
      const page = await getLeaveList({ status: 'PENDING_STATION', pageNum: 1, pageSize: 3 })
      return {
        total: page.total,
        rows: page.list.map((item) => ({
          key: item.id,
          title: `${item.employeeName} ${item.startDate} ~ ${item.endDate}`,
          meta: `${typeLabel(item.leaveType)} · 待初审`
        }))
      }
    }
  }
]
