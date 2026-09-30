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
 * - MVP 裁剪：/boss/kpi、/boss/kpi/:employeeId、/boss/trend、/boss/rank、/boss/parcel/:id 五条记录整体下架，
 *   路由直接不存在（不加 redirect 兜底，未命中走 404），页面源码保留在磁盘待后续恢复。
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

  // 以下两页原为 Tabbar 一级页，三 Tab 改造后降为首页宫格进入的二级页，自带返回 NavBar
  {
    path: '/boss/attendance',
    name: 'bossAttendance',
    component: () => import('../modules/boss/views/attendance.vue'),
    meta: { roles: BOSS_ROLES, title: '考勤概览' }
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

  /* 需求 8–10 管理端：不进 Tabbar，入口在首页宫格与「我的 · 管理与配置」 */
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
  // 驿站管理（管理能力扩展）：列表 → 详情 → 表单 / 账号维护。
  // 路由注册顺序：静态路径（create）先于路径参数（:id），与 mock engine「首个命中」规则保持一致。
  {
    path: '/boss/station',
    name: 'bossStation',
    component: () => import('../modules/boss/views/station.vue'),
    meta: { roles: BOSS_ROLES, title: '驿站管理' }
  },
  {
    path: '/boss/station/create',
    name: 'bossStationCreate',
    component: () => import('../modules/boss/views/stationForm.vue'),
    meta: { roles: BOSS_ROLES, title: '新增驿站' }
  },
  {
    path: '/boss/station/:id/edit',
    name: 'bossStationEdit',
    component: () => import('../modules/boss/views/stationForm.vue'),
    meta: { roles: BOSS_ROLES, title: '编辑驿站' }
  },
  // 账号新增（角色由 ?role= 预置：STAFF / STATION_ADMIN）
  {
    path: '/boss/station/:id/account/create',
    name: 'bossAccountCreate',
    component: () => import('../modules/boss/views/accountForm.vue'),
    meta: { roles: BOSS_ROLES, title: '新增账号' }
  },
  // 账号编辑（改资料 / 状态 / 重置口令；不含用户名与口令字段）
  {
    path: '/boss/station/:id/account/:employeeId',
    name: 'bossAccountEdit',
    component: () => import('../modules/boss/views/accountForm.vue'),
    meta: { roles: BOSS_ROLES, title: '账号编辑' }
  },
  {
    path: '/boss/station/:id',
    name: 'bossStationDetail',
    component: () => import('../modules/boss/views/stationDetail.vue'),
    meta: { roles: BOSS_ROLES, title: '驿站详情' }
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
  // 财务管理（含员工工资设置）：原地升级自算薪日设置列表；/boss/finance 为规范别名（宫格入口用它），
  // 保留 /boss/payroll-settings 兼容既有深链与「我的」跳转（设计 ③.5，不新增独立路由记录）
  {
    path: '/boss/payroll-settings',
    name: 'bossPayrollSettings',
    component: () => import('../modules/boss/views/payrollSettings.vue'),
    alias: '/boss/finance',
    meta: { roles: BOSS_ROLES, title: '财务管理' }
  },
  {
    path: '/boss/payroll-settings/:stationId',
    name: 'bossPayrollSettingEdit',
    component: () => import('../modules/boss/views/payrollSettingEdit.vue'),
    meta: { roles: BOSS_ROLES, title: '员工工资设置' }
  },
  // 审批中心（管理能力扩展）：分组列表视图，复用 stores/todo.js，不含工单
  {
    path: '/boss/approval',
    name: 'bossApproval',
    component: () => import('../modules/boss/views/approval.vue'),
    meta: { roles: BOSS_ROLES, title: '审批中心' }
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
  // 班次管理（设计 ⑭）：考勤概览「考勤管理」置首入口。同一复数资源段 shifts 贯串三条路由，
  // 注册顺序沿用「静态 create 先于 :id/edit」（与 /boss/station 系列同构，engine 取首个命中）。
  {
    path: '/boss/shifts',
    name: 'bossShifts',
    component: () => import('../modules/boss/views/shift.vue'),
    meta: { roles: BOSS_ROLES, title: '班次管理' }
  },
  {
    path: '/boss/shifts/create',
    name: 'bossShiftCreate',
    component: () => import('../modules/boss/views/shiftForm.vue'),
    meta: { roles: BOSS_ROLES, title: '新增班次' }
  },
  {
    path: '/boss/shifts/:id/edit',
    name: 'bossShiftEdit',
    component: () => import('../modules/boss/views/shiftForm.vue'),
    meta: { roles: BOSS_ROLES, title: '编辑班次' }
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
