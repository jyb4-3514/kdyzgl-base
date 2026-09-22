import { ROLE } from '@/shared/constants/role.js'

/**
 * 老板端路由子表（由 ../router/index.js 单点聚合展开）
 *
 * 为什么导出数组而非自己 createRouter：整端只有一个 router 实例，子表只提供路由定义；
 * 实例创建与守卫（登录态、角色白名单）必须留在聚合点，否则会出现多实例、守卫漏挂。
 *
 * 共 23 条 = /boss 重定向 + 21 个老板端页面 + 1 个中立共享页（/boss/message 与 /staff/message 复用同页，
 * 页面刻意放在内核 views/message/ 而不进任何一端域目录）。
 * /boss/* 的 URL 与 meta（tabbar / roles / title）已冻结：共享内核 TabbarLayout 依赖
 * /boss/message 与 /boss/notification/publish 两个字面值，e2e 有 3 处 URL 断言，
 * 后续只允许改 component 的 import 路径，禁止改 path / name / meta。
 *
 * TODO(扩展): 跨域复用路由 /boss/kpi/:employeeId 有意留在聚合点，不在本文件内开 lint 例外。
 */
export const bossRoutes = [
  { path: '/boss', redirect: '/boss/home' },
  {
    path: '/boss/home',
    name: 'bossHome',
    component: () => import('./views/home.vue'),
    meta: { tabbar: 'boss', roles: [ROLE.ADMIN], title: '经营总览' }
  },
  // 消息 Tab：通知与待办两个子视图共用一页，按角色渲染（C2 / A4-1）
  {
    path: '/boss/message',
    name: 'bossMessage',
    component: () => import('@/mobile/views/message/MessagePage.vue'),
    meta: { tabbar: 'boss', roles: [ROLE.ADMIN], title: '消息' }
  },
  {
    path: '/boss/me',
    name: 'bossMe',
    component: () => import('./views/me.vue'),
    meta: { tabbar: 'boss', roles: [ROLE.ADMIN], title: '我的' }
  },
  // 以下四页原为 Tabbar 一级页，本轮（三 Tab 改造）降为首页宫格进入的二级页，自带返回 NavBar
  {
    path: '/boss/attendance',
    name: 'bossAttendance',
    component: () => import('./views/attendance.vue'),
    meta: { roles: [ROLE.ADMIN], title: '考勤概览' }
  },
  {
    path: '/boss/trend',
    name: 'bossTrend',
    component: () => import('./views/trend.vue'),
    meta: { roles: [ROLE.ADMIN], title: '包裹趋势' }
  },
  {
    path: '/boss/rank',
    name: 'bossRank',
    component: () => import('./views/rank.vue'),
    meta: { roles: [ROLE.ADMIN], title: '驿站排行' }
  },
  {
    path: '/boss/alerts',
    name: 'bossAlerts',
    component: () => import('./views/alerts.vue'),
    meta: { roles: [ROLE.ADMIN], title: '异常预警' }
  },
  {
    path: '/boss/workorder',
    name: 'bossWorkOrder',
    component: () => import('./views/workorder.vue'),
    meta: { roles: [ROLE.ADMIN], title: '工单管理' }
  },
  {
    path: '/boss/attendance/makeup',
    name: 'bossMakeupApproval',
    component: () => import('./views/makeupApproval.vue'),
    meta: { roles: [ROLE.ADMIN], title: '补卡审批' }
  },
  // 发布通知（需求4）：表单两段 + 实时人数预览，用整页而不是弹层（B4.6）
  {
    path: '/boss/notification/publish',
    name: 'bossNotificationPublish',
    component: () => import('./views/notificationPublish.vue'),
    meta: { roles: [ROLE.ADMIN], title: '发布通知' }
  },

  /* 需求 7–10 老板端：不进 Tabbar，入口在首页宫格与「我的 · 管理与配置」 */
  {
    path: '/boss/kpi',
    name: 'bossKpi',
    component: () => import('./views/kpi.vue'),
    meta: { roles: [ROLE.ADMIN], title: 'KPI 考核' }
  },
  {
    path: '/boss/hr',
    name: 'bossHr',
    component: () => import('./views/hr.vue'),
    meta: { roles: [ROLE.ADMIN], title: '人事管理' }
  },
  {
    path: '/boss/hr/:employeeId',
    name: 'bossHrDetail',
    component: () => import('./views/hrDetail.vue'),
    meta: { roles: [ROLE.ADMIN], title: '员工档案' }
  },
  {
    path: '/boss/payroll',
    name: 'bossPayroll',
    component: () => import('./views/payroll.vue'),
    meta: { roles: [ROLE.ADMIN], title: '工资单审核' }
  },
  {
    path: '/boss/payroll/:id',
    name: 'bossPayrollDetail',
    component: () => import('./views/payrollDetail.vue'),
    meta: { roles: [ROLE.ADMIN], title: '工资单详情' }
  },
  {
    path: '/boss/flow',
    name: 'bossFlow',
    component: () => import('./views/flow.vue'),
    meta: { roles: [ROLE.ADMIN], title: '入离职审批' }
  },
  {
    path: '/boss/flow/:type/:id',
    name: 'bossFlowDetail',
    component: () => import('./views/flowDetail.vue'),
    meta: { roles: [ROLE.ADMIN], title: '流程办理' }
  },
  // 请假（M11）：终审 + 扣款设置，均仅 ADMIN（§2.1 权限矩阵）
  {
    path: '/boss/leave',
    name: 'bossLeave',
    component: () => import('./views/leaveApproval.vue'),
    meta: { roles: [ROLE.ADMIN], title: '请假审批' }
  },
  {
    path: '/boss/leave/settings',
    name: 'bossLeaveSettings',
    component: () => import('./views/leaveSettings.vue'),
    meta: { roles: [ROLE.ADMIN], title: '请假扣款设置' }
  },
  {
    path: '/boss/attendance/rule',
    name: 'bossAttendanceRule',
    component: () => import('./views/attendanceRule.vue'),
    meta: { roles: [ROLE.ADMIN], title: '打卡规则' }
  },
  {
    path: '/boss/schedule',
    name: 'bossSchedule',
    component: () => import('./views/schedule.vue'),
    meta: { roles: [ROLE.ADMIN], title: '排班管理' }
  },
  {
    path: '/boss/attendance/records',
    name: 'bossAttendanceRecords',
    component: () => import('./views/attendanceRecords.vue'),
    meta: { roles: [ROLE.ADMIN], title: '打卡记录' }
  }
]
