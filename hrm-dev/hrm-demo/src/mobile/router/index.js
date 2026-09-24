import { createRouter, createWebHashHistory } from 'vue-router'
import { showFailToast } from 'vant'
import { ROLE } from '@/shared/constants/role.js'
import { canAccess } from '@/shared/domain/permission.js'
import { useAuthStore } from '../stores/auth.js'
import { readToken, readUser } from '../utils/authStorage.js'
import { bossRoutes } from '../modules/boss/router.js'

const ALL_ROLES = [ROLE.ADMIN, ROLE.STATION_ADMIN, ROLE.STAFF]
const STAFF_ROLES = [ROLE.STATION_ADMIN, ROLE.STAFF]

/**
 * 移动端路由
 *
 * 为什么用 hash 模式：MPA 静态托管 + 安卓壳离线包（file:// 或本地 assets）下无服务端 rewrite，
 * hash 模式不需要任何服务端兜底即可直接打开子页面。
 *
 * meta 约定：
 * - public  ：免登录（登录页）
 * - roles   ：页面级角色白名单，与 Mock 的 roles 校验口径一致（前端只做体验拦截，真正拦截在 Mock 层）
 * - tabbar  ：需要底部 Tabbar 的 Tab 页；不标的即详情/表单页（自带返回 NavBar）
 * - title   ：NavBar 标题
 *
 * 说明：/staff/parcel/:id、/staff/workorder/:id 允许 ADMIN 进入 —— 管理端「异常预警」需要下钻到
 * 具体包裹与工单明细，属同一页面的第二种入口，不另写管理端详情页（避免同一逻辑两份实现）。
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
  // 管理端域子表（../modules/boss/router.js）：/boss 重定向 + 22 个管理端页面 + 2 个中立共享页（/boss/message、/boss/message/notice）
  ...bossRoutes,
  // 考核明细复用员工端同页（A12-7：同一业务对象两端优先复用，仅按角色改标题与入口）
  // 跨域复用，刻意留在聚合点；彻底去耦路径见方案 §3.2 的 TODO(扩展)（提升为中立共享页）
  {
    path: '/boss/kpi/:employeeId',
    name: 'bossKpiDetail',
    component: () => import('../views/staff/kpi.vue'),
    meta: { roles: [ROLE.ADMIN], title: '考核明细' }
  },

  /* ==================== 员工端（STATION_ADMIN / STAFF） ==================== */
  { path: '/staff', redirect: '/staff/home' },
  {
    path: '/staff/home',
    name: 'staffHome',
    component: () => import('../views/staff/home.vue'),
    meta: { tabbar: 'staff', roles: STAFF_ROLES, title: '工作台' }
  },
  // 消息 Tab：与管理端复用同一页（A4-1「同一业务对象两端复用同页 + 按角色渲染」）
  {
    path: '/staff/message',
    name: 'staffMessage',
    component: () => import('../views/message/MessagePage.vue'),
    meta: { tabbar: 'staff', roles: STAFF_ROLES, title: '消息' }
  },
  // 通知阅读页：管理端 /boss/message/notice 指向同一组件；无 tabbar（二级页自带返回，D2.1）
  {
    path: '/staff/message/notice',
    name: 'staffNoticeReader',
    component: () => import('../views/message/NoticeReader.vue'),
    meta: { roles: STAFF_ROLES, title: '通知详情' }
  },
  {
    path: '/staff/me',
    name: 'staffMe',
    component: () => import('../views/staff/me.vue'),
    meta: { tabbar: 'staff', roles: STAFF_ROLES, title: '我的' }
  },
  // 旧通知路由重定向，避免历史链接 404；管理端按角色分流到自己的消息页（原路由为 ALL_ROLES）
  {
    path: '/staff/notification',
    redirect: () => {
      const user = readUser()
      const path = user && user.role === ROLE.ADMIN ? '/boss/message' : '/staff/message'
      return { path, query: { tab: 'notice' } }
    }
  },
  // 以下三页原为 Tabbar 一级页，本轮降为首页宫格进入的二级页，自带返回 NavBar
  {
    path: '/staff/attendance',
    name: 'staffAttendance',
    component: () => import('../views/staff/attendance.vue'),
    meta: { roles: STAFF_ROLES, title: '打卡' }
  },
  {
    path: '/staff/parcel',
    name: 'staffParcel',
    component: () => import('../views/staff/parcel.vue'),
    meta: { roles: STAFF_ROLES, title: '本站包裹' }
  },
  {
    path: '/staff/workorder',
    name: 'staffWorkOrder',
    component: () => import('../views/staff/workorder.vue'),
    meta: { roles: STAFF_ROLES, title: '工单' }
  },

  /* 非 Tab 页：作业操作与详情 */
  {
    path: '/staff/pickup',
    name: 'staffPickup',
    component: () => import('../views/staff/pickup.vue'),
    meta: { roles: STAFF_ROLES, title: '取件核销' }
  },
  {
    path: '/staff/parcel/:id',
    name: 'staffParcelDetail',
    component: () => import('../views/staff/parcelDetail.vue'),
    meta: { roles: ALL_ROLES, title: '包裹详情' }
  },
  {
    path: '/staff/workorder/create',
    name: 'staffWorkOrderCreate',
    component: () => import('../views/staff/workorderCreate.vue'),
    meta: { roles: STAFF_ROLES, title: '新建工单' }
  },
  {
    path: '/staff/workorder/:id',
    name: 'staffWorkOrderDetail',
    component: () => import('../views/staff/workorderDetail.vue'),
    meta: { roles: ALL_ROLES, title: '工单详情' }
  },
  // S9 同步状态：STAFF 不可见（T15 验收项），故白名单只放站长
  {
    path: '/staff/sync',
    name: 'staffSync',
    component: () => import('../views/staff/sync.vue'),
    meta: { roles: [ROLE.STATION_ADMIN], title: '同步状态' }
  },
  {
    path: '/staff/me/password',
    name: 'staffPassword',
    component: () => import('../views/staff/password.vue'),
    meta: { roles: ALL_ROLES, title: '修改密码' }
  },

  /* 考勤非 Tab 页：排班、打卡记录与补卡申请（打卡页已降为二级页，见上） */
  {
    path: '/staff/schedule',
    name: 'staffSchedule',
    component: () => import('../views/staff/schedule.vue'),
    meta: { roles: STAFF_ROLES, title: '我的排班' }
  },
  {
    path: '/staff/attendance/records',
    name: 'staffAttendanceRecords',
    component: () => import('../views/staff/attendanceRecords.vue'),
    meta: { roles: STAFF_ROLES, title: '我的打卡记录' }
  },
  {
    path: '/staff/attendance/makeup',
    name: 'staffMakeupList',
    component: () => import('../views/staff/makeupList.vue'),
    meta: { roles: STAFF_ROLES, title: '我的补卡申请' }
  },
  // 请假（M11）：申请表单与我的列表员工/站长共用；初审页为站长专属（§3.3 的关键判断）
  {
    path: '/staff/leave/apply',
    name: 'staffLeaveApply',
    component: () => import('../views/staff/leaveApply.vue'),
    meta: { roles: STAFF_ROLES, title: '请假申请' }
  },
  {
    path: '/staff/leave/review',
    name: 'staffLeaveReview',
    component: () => import('../views/staff/leaveReview.vue'),
    meta: { roles: [ROLE.STATION_ADMIN], title: '请假初审' }
  },
  {
    path: '/staff/leave',
    name: 'staffLeaveList',
    component: () => import('../views/staff/leaveList.vue'),
    meta: { roles: STAFF_ROLES, title: '我的请假' }
  },

  /* 需求 7–10 员工端：我的数据类入口（宫格放高频作业，低频查询进「我的」） */
  {
    path: '/staff/kpi',
    name: 'staffKpi',
    component: () => import('../views/staff/kpi.vue'),
    meta: { roles: STAFF_ROLES, title: '我的 KPI' }
  },
  {
    path: '/staff/payroll',
    name: 'staffPayroll',
    component: () => import('../views/staff/payroll.vue'),
    meta: { roles: STAFF_ROLES, title: '我的工资单' }
  },
  {
    path: '/staff/payroll/:id',
    name: 'staffPayrollDetail',
    component: () => import('../views/staff/payrollDetail.vue'),
    meta: { roles: STAFF_ROLES, title: '工资单详情' }
  },
  {
    path: '/staff/profile',
    name: 'staffProfile',
    component: () => import('../views/staff/profile.vue'),
    meta: { roles: STAFF_ROLES, title: '我的档案' }
  },
  // 员工端入离职：契约未开放流程接口，本页为只读降级（见 staff/flow.vue 头部说明）
  {
    path: '/staff/flow',
    name: 'staffFlow',
    component: () => import('../views/staff/flow.vue'),
    meta: { roles: STAFF_ROLES, title: '我的入离职' }
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
    showFailToast('当前演示身份无权访问该页面')
    return auth.homePath
  }
  return true
})

/**
 * 页签标题按域切换（B8）：mobile.html 的静态 <title> 是三端共享入口，无法按角色区分，
 * 故在路由出口单点改写 —— 只有管理端域显示「驿站精灵」，员工端与登录页保持原值，避免品牌越界。
 */
const DEFAULT_DOC_TITLE = '移动端 · 快递驿站智汇系统'
router.afterEach((to) => {
  document.title = to.path.startsWith('/boss') ? '驿站精灵' : DEFAULT_DOC_TITLE
})

export default router
