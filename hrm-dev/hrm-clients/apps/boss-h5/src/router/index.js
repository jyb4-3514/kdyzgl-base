import { createRouter, createWebHashHistory } from 'vue-router'
import { showFailToast } from 'vant'
import { ROLE } from '@kdyzgl/shared/constants/role.js'
import { canAccess } from '@kdyzgl/shared/domain/permission.js'
import { useAuthStore } from '../stores/auth.js'
import { readToken } from '../utils/authStorage.js'
import { APP_NAME_BOSS } from '../constants/appName.js'

const BOSS_ROLES = [ROLE.ADMIN]

/**
 * 管理端路由 · 驿站精灵（端固定化，B4 拆分自 hrm-demo 移动端聚合路由）
 *
 * 为什么用 hash 模式：静态托管 + 安卓壳远程加载下无服务端 rewrite，hash 模式不需要任何服务端兜底即可直接打开子页面。
 *
 * meta 约定：
 * - public  ：免登录（登录页）
 * - roles   ：页面级角色白名单，与 Mock 的 roles 校验口径一致（前端只做体验拦截，真正拦截在 Mock 层）
 * - tabbar  ：需要底部 Tabbar 的 Tab 页；不标的即详情/表单页（自带返回 NavBar）
 * - title   ：NavBar 标题
 *
 * 端隔离（ADR §3.5 第 8 项 / §3.7 B4 ③）：本路由只含管理端 /boss/* 路径；
 * - 员工端 /staff/* 不存在于此工程（编译期即断）；
 * - /boss/kpi/:employeeId 的页面本体为中立页 @kdyzgl/shared/ui/KpiDetail.vue（B-3，容器在 views/kpi/），
 *   不再直引员工端视图 —— 跨域直引残留 = 0。
 */
const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'mobileLogin',
    component: () => import('../views/login/index.vue'),
    meta: { public: true, title: '登录' }
  },

  /* ==================== 管理端（ADMIN） ==================== */
  { path: '/boss', redirect: '/boss/home' },
  {
    path: '/boss/home',
    name: 'bossHome',
    component: () => import('../modules/boss/views/home.vue'),
    meta: { tabbar: 'boss', roles: BOSS_ROLES, title: '经营总览' }
  },
  // 消息 Tab：通知与待办两个子视图共用中立页（@kdyzgl/shared/ui/MessagePage）+ 本端容器
  {
    path: '/boss/message',
    name: 'bossMessage',
    component: () => import('../views/message/MessagePage.vue'),
    meta: { tabbar: 'boss', roles: BOSS_ROLES, title: '消息' }
  },
  // 通知阅读页：无 tabbar（二级页自带返回，D2.1）
  {
    path: '/boss/message/notice',
    name: 'bossNoticeReader',
    component: () => import('../views/message/NoticeReader.vue'),
    meta: { roles: BOSS_ROLES, title: '通知详情' }
  },
  {
    path: '/boss/me',
    name: 'bossMe',
    component: () => import('../modules/boss/views/me.vue'),
    meta: { tabbar: 'boss', roles: BOSS_ROLES, title: '我的' }
  },
  {
    path: '/boss/me/password',
    name: 'bossPassword',
    component: () => import('../views/boss/password.vue'),
    meta: { roles: BOSS_ROLES, title: '修改密码' }
  },

  // 以下四页原为 Tabbar 一级页，三 Tab 改造后降为首页宫格进入的二级页，自带返回 NavBar
  {
    path: '/boss/attendance',
    name: 'bossAttendance',
    component: () => import('../modules/boss/views/attendance.vue'),
    meta: { roles: BOSS_ROLES, title: '考勤概览' }
  },
  {
    path: '/boss/trend',
    name: 'bossTrend',
    component: () => import('../modules/boss/views/trend.vue'),
    meta: { roles: BOSS_ROLES, title: '包裹趋势' }
  },
  {
    path: '/boss/rank',
    name: 'bossRank',
    component: () => import('../modules/boss/views/rank.vue'),
    meta: { roles: BOSS_ROLES, title: '驿站排行' }
  },
  {
    path: '/boss/alerts',
    name: 'bossAlerts',
    component: () => import('../modules/boss/views/alerts.vue'),
    meta: { roles: BOSS_ROLES, title: '异常预警' }
  },
  {
    path: '/boss/workorder',
    name: 'bossWorkOrder',
    component: () => import('../modules/boss/views/workorder.vue'),
    meta: { roles: BOSS_ROLES, title: '工单管理' }
  },
  // 工单详情：管理端自有页（原与员工端复用同页；拆端后本端各持一份，跨域直引残留 = 0）
  {
    path: '/boss/workorder/:id',
    name: 'bossWorkOrderDetail',
    component: () => import('../views/boss/workorderDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '工单详情' }
  },
  // 包裹详情：管理端「异常预警」下钻目标（同上，本端自有页）
  {
    path: '/boss/parcel/:id',
    name: 'bossParcelDetail',
    component: () => import('../views/boss/parcelDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '包裹详情' }
  },
  {
    path: '/boss/attendance/makeup',
    name: 'bossMakeupApproval',
    component: () => import('../modules/boss/views/makeupApproval.vue'),
    meta: { roles: BOSS_ROLES, title: '补卡审批' }
  },
  // 发布通知（需求4）：表单两段 + 实时人数预览，用整页而不是弹层（B4.6）
  {
    path: '/boss/notification/publish',
    name: 'bossNotificationPublish',
    component: () => import('../modules/boss/views/notificationPublish.vue'),
    meta: { roles: BOSS_ROLES, title: '发布通知' }
  },

  /* 需求 7–10 管理端：不进 Tabbar，入口在首页宫格与「我的 · 管理与配置」 */
  {
    path: '/boss/kpi',
    name: 'bossKpi',
    component: () => import('../modules/boss/views/kpi.vue'),
    meta: { roles: BOSS_ROLES, title: 'KPI 考核' }
  },
  // 考核明细：页面本体为中立页 @kdyzgl/shared/ui/KpiDetail.vue，容器在 views/kpi/（B-3 消解跨域直引）
  {
    path: '/boss/kpi/:employeeId',
    name: 'bossKpiDetail',
    component: () => import('../views/kpi/index.vue'),
    meta: { roles: BOSS_ROLES, title: '考核明细' }
  },
  {
    path: '/boss/hr',
    name: 'bossHr',
    component: () => import('../modules/boss/views/hr.vue'),
    meta: { roles: BOSS_ROLES, title: '人事管理' }
  },
  {
    path: '/boss/hr/:employeeId',
    name: 'bossHrDetail',
    component: () => import('../modules/boss/views/hrDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '员工档案' }
  },
  {
    path: '/boss/payroll',
    name: 'bossPayroll',
    component: () => import('../modules/boss/views/payroll.vue'),
    meta: { roles: BOSS_ROLES, title: '工资单审核' }
  },
  {
    path: '/boss/payroll/:id',
    name: 'bossPayrollDetail',
    component: () => import('../modules/boss/views/payrollDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '工资单详情' }
  },
  {
    path: '/boss/flow',
    name: 'bossFlow',
    component: () => import('../modules/boss/views/flow.vue'),
    meta: { roles: BOSS_ROLES, title: '入离职审批' }
  },
  {
    path: '/boss/flow/:type/:id',
    name: 'bossFlowDetail',
    component: () => import('../modules/boss/views/flowDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '流程办理' }
  },
  // 请假（M11）：终审 + 扣款设置，均仅 ADMIN（§2.1 权限矩阵）
  {
    path: '/boss/leave',
    name: 'bossLeave',
    component: () => import('../modules/boss/views/leaveApproval.vue'),
    meta: { roles: BOSS_ROLES, title: '请假审批' }
  },
  {
    path: '/boss/leave/settings',
    name: 'bossLeaveSettings',
    component: () => import('../modules/boss/views/leaveSettings.vue'),
    meta: { roles: BOSS_ROLES, title: '请假扣款设置' }
  },
  {
    path: '/boss/attendance/rule',
    name: 'bossAttendanceRule',
    component: () => import('../modules/boss/views/attendanceRule.vue'),
    meta: { roles: BOSS_ROLES, title: '打卡规则' }
  },
  {
    path: '/boss/schedule',
    name: 'bossSchedule',
    component: () => import('../modules/boss/views/schedule.vue'),
    meta: { roles: BOSS_ROLES, title: '排班管理' }
  },
  {
    path: '/boss/attendance/records',
    name: 'bossAttendanceRecords',
    component: () => import('../modules/boss/views/attendanceRecords.vue'),
    meta: { roles: BOSS_ROLES, title: '打卡记录' }
  },
  // 考勤明细（§14.2）：概览六卡的下钻目标，?dim= 决定维度；不进 tabbar，二级页自带返回
  {
    path: '/boss/attendance/detail',
    name: 'bossAttendanceDetail',
    component: () => import('../modules/boss/views/attendanceDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '考勤明细' }
  },

  // 未匹配路径不再静默回首页：菜单可见但路由不存在的隐性 bug 需要被直接暴露（P1-4）
  {
    path: '/:pathMatch(.*)*',
    name: 'mobileNotFound',
    component: () => import('../views/error/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  // token 与 store 状态可能不同步（另一标签页退出/被重置），以 storage 为准做一次兜底
  const logged = !!auth.token || !!readToken()

  if (to.meta.public) return logged ? auth.homePath : true
  if (!logged) return { path: '/login', query: { redirect: to.fullPath } }
  // 权限判定收敛到 shared/domain/permission.js：页面侧如需同口径判断，复用同一纯函数即可
  if (!canAccess(to.meta.roles, auth.user)) {
    showFailToast('当前身份无权访问该页面')
    return auth.homePath
  }
  return true
})

/** 页签标题：管理端品牌恒定（端固定化后无员工端分支） */
router.afterEach(() => {
  document.title = APP_NAME_BOSS
})

export default router
