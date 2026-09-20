import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@admin/stores/auth'

/**
 * Demo 扩展路由表（T10，页面清单见 demo-design.md 5.1）
 *
 * 与一期 router 的差异：
 * 1. 新增包裹/同步/工单/通知 4 页与 5 个一期占位页（占位页仍指向 @admin 对应视图，仅保留导航结构，不填业务内容）；
 * 2. 守卫复用一期的 4 条规则（登录白名单 / 未登录带 redirect / 强制改密锁定 / 越权重定向），
 *    但把「非 ADMIN 一律锁 /profile」改成按 meta.roles 白名单重定向到各自落地页——
 *    STATION_ADMIN 需要进入包裹/同步/工单，直接改一期守卫会污染一期基线（demo-design.md 10.1）。
 */

/** 各角色落地页：越权或直接访问根路径时按角色分流 */
const ROLE_LANDING = { ADMIN: '/dashboard', STATION_ADMIN: '/parcel', STAFF: '/profile' }
export const landingPath = (user) => (user && ROLE_LANDING[user.role]) || '/login'

const ALL_ROLES = ['ADMIN', 'STATION_ADMIN', 'STAFF']
const STATION_ROLES = ['ADMIN', 'STATION_ADMIN']

/**
 * 未登录时的回跳参数：只有目标**命中应用内路由**才写 redirect。
 * 为什么：/pc.html、/mobile.html、/index.html 这类入口文件（端选择页「网页端」卡片就指向 pc.html）
 * 不匹配任何业务路由，会被 catch-all 兜成 NotFound；把这种路径写进 redirect，
 * 登录后就会跳回一个不存在的路径、直接停在 404 页（e2e A1-7 探针）。
 * 不写 redirect 时，登录页按一期既有逻辑回 ADMIN 看板 / 其他角色个人中心，是有效落点。
 */
const redirectQuery = (to) => (to.name && to.name !== 'NotFound' ? { redirect: to.fullPath } : {})

const routes = [
  {
    path: '/login',
    name: 'Login',
    // 一期登录页原样复用（含 1001/1002 文案分支、首登改密跳转），Demo 不复制实现
    component: () => import('@admin/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue'),
        meta: { title: '数据看板', icon: 'DataLine', roles: ['ADMIN'] }
      },
      {
        path: 'employee',
        name: 'Employee',
        component: () => import('@admin/views/employee/index.vue'),
        meta: { title: '员工管理', icon: 'User', group: 'org', roles: ['ADMIN'] }
      },
      // 需求7：KPI 考核与一期员工页「同级并列」而非物理合并（A11-1）——一期页面冻结，KPI 走独立挂载点
      {
        path: 'employee/kpi',
        name: 'EmployeeKpi',
        component: () => import('../views/employee/kpi/index.vue'),
        meta: { title: 'KPI 考核', icon: 'TrendCharts', group: 'org', roles: ['ADMIN'] }
      },
      // 员工档案聚合页：KPI / 人事 / 入离职 / 工资单在一屏内回链，是「并入员工管理模块」的信息架构落点
      {
        path: 'employee/detail/:id',
        name: 'EmployeeDetail',
        component: () => import('../views/employee/detail/index.vue'),
        meta: {
          title: '员工档案',
          group: 'org',
          activeMenu: '/employee/kpi',
          breadcrumb: ['组织人事', 'KPI 考核', '员工档案'],
          roles: ['ADMIN']
        }
      },
      // 需求8 人事管理
      {
        path: 'hr',
        name: 'Hr',
        component: () => import('../views/hr/index.vue'),
        meta: { title: '人事管理', icon: 'Postcard', group: 'org', roles: ['ADMIN'] }
      },
      // 需求10 入离职流程
      {
        path: 'onboard',
        name: 'Onboard',
        component: () => import('../views/onboard/index.vue'),
        meta: { title: '入离职', icon: 'Promotion', group: 'org', roles: ['ADMIN'] }
      },
      {
        path: 'department',
        name: 'Department',
        component: () => import('@admin/views/department/index.vue'),
        meta: { title: '部门管理', icon: 'Share', group: 'org', roles: ['ADMIN'] }
      },
      {
        path: 'station',
        name: 'Station',
        component: () => import('@admin/views/station/index.vue'),
        meta: { title: '驿站管理', icon: 'Location', group: 'org', roles: ['ADMIN'] }
      },
      // 需求9 财务管理（工资单）
      {
        path: 'finance',
        name: 'Finance',
        component: () => import('../views/finance/index.vue'),
        meta: { title: '财务管理', icon: 'Money', group: 'pay', roles: ['ADMIN'] }
      },
      // 考勤/排班：ADMIN 与站长都可见可用，STAFF 不开放（打卡入口在移动端员工端）；数据范围由 Mock 强制收敛
      {
        path: 'attendance',
        name: 'Attendance',
        component: () => import('../views/attendance/index.vue'),
        meta: { title: '考勤管理', icon: 'AlarmClock', group: 'pay', roles: STATION_ROLES }
      },
      {
        path: 'schedule',
        name: 'Schedule',
        component: () => import('../views/schedule/index.vue'),
        meta: { title: '排班管理', icon: 'Calendar', group: 'pay', roles: STATION_ROLES }
      },
      // 请假管理：ADMIN 全域终审 + 站长本站初审，数据范围与动作由 Mock 二次收敛（设计规范 §2.1）
      {
        path: 'leave',
        name: 'Leave',
        component: () => import('../views/leave/index.vue'),
        meta: { title: '请假管理', icon: 'Notebook', group: 'pay', roles: STATION_ROLES }
      },
      // 运行日志：仅 ADMIN（移动端不做查看页，三端上报、PC 单点查看）
      {
        path: 'system/logs',
        name: 'SystemLogs',
        component: () => import('../views/system/logs.vue'),
        meta: { title: '运行日志', icon: 'Document', group: 'sys', roles: ['ADMIN'] }
      },
      {
        path: 'parcel',
        name: 'Parcel',
        component: () => import('../views/parcel/index.vue'),
        meta: { title: '包裹管理', icon: 'Box', group: 'biz', roles: STATION_ROLES }
      },
      {
        path: 'parcel/sync',
        name: 'ParcelSync',
        component: () => import('../views/sync/index.vue'),
        meta: { title: '同步任务', icon: 'Refresh', group: 'biz', roles: STATION_ROLES }
      },
      {
        path: 'work-order',
        name: 'WorkOrder',
        component: () => import('../views/workOrder/index.vue'),
        meta: { title: '工单管理', icon: 'Tickets', group: 'biz', roles: STATION_ROLES }
      },
      {
        path: 'notification',
        name: 'Notification',
        component: () => import('../views/notification/index.vue'),
        meta: { title: '通知中心', icon: 'Bell', group: 'biz', roles: STATION_ROLES }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@admin/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'UserFilled', group: 'sys', roles: ALL_ROLES }
      },
      // 以下 5 个为一期占位空壳（仅 <router-view/>），Demo 只让它们路由可达，不填内容（demo-design.md 5.1 脚注）
      // TODO(扩展): 二期规划落地后再补业务实现，届时同步更新 MENU_WHITELIST 与菜单配置
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@admin/views/knowledge/index.vue'),
        meta: { title: '知识库', roles: ['ADMIN'] }
      },
      {
        path: 'money',
        name: 'Money',
        component: () => import('@admin/views/money/index.vue'),
        meta: { title: '资金', roles: ['ADMIN'] }
      },
      {
        path: 'performance',
        name: 'Performance',
        component: () => import('@admin/views/performance/index.vue'),
        meta: { title: '绩效', roles: ['ADMIN'] }
      },
      {
        path: 'permission',
        name: 'Permission',
        component: () => import('@admin/views/permission/index.vue'),
        meta: { title: '权限', roles: ['ADMIN'] }
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@admin/views/system/index.vue'),
        meta: { title: '系统', roles: ['ADMIN'] }
      }
    ]
  },
  // 未匹配路径不再静默兜到看板：否则「菜单可见但路由被改」这类隐性 bug 永远看不见（P1-4）
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/error/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  const user = authStore.user

  // 0. 登录态自洽性自愈：有 token 无 user（旧版本残留 / 外部改写）时 isLoggedIn 会误判为已登录，
  //    而越权判定与落地页分流都要读 role，先清登录态走未登录分支，避免守卫在半残态下裸解引用 user.role
  if (authStore.isLoggedIn && !user) {
    authStore.clearAuth()
    if (to.path === '/login') return true
    return { path: '/login', query: redirectQuery(to) }
  }

  // 1. 登录页白名单：已登录且无需强制改密时直接进入角色落地页
  //    user 为空时不得走落地页分流，否则 landingPath 回落 '/login' 会与目标同址形成重定向环
  if (to.path === '/login') {
    return authStore.isLoggedIn && user && !authStore.needChangePwd ? landingPath(user) : true
  }

  // 2. 未登录：跳登录页并记录回跳地址（一期规则，保持登录后能回到原页面）
  if (!authStore.isLoggedIn) {
    return { path: '/login', query: redirectQuery(to) }
  }

  // 3. 首登强制改密：锁定在 /profile，改密完成前不可访问其他页面
  if (authStore.needChangePwd && to.path !== '/profile') return '/profile'

  // 4. 扩展点：按 meta.roles 判定越权，重定向到本角色落地页（STATION_ADMIN 因此能留在包裹/工单页）
  //    防御性兜底说明：走到这一步 user 必非空（半残态已在第 0 步清掉登录态并改走未登录分支），
  //    故 `!user ||` 在正常契约下不可达，只是避免将来有人绕过第 0 步时裸解引用 user.role。
  //    半残态白屏的真正自洽修复点是第 0 步，不要误读成「靠第 4 步拦住的」
  const roles = to.meta && to.meta.roles
  if (roles && (!user || !roles.includes(user.role))) return landingPath(user)

  return true
})

router.afterEach((to) => {
  document.title = to.meta && to.meta.title ? `${to.meta.title} - 快递驿站智汇系统` : '快递驿站智汇系统'
})

export default router
