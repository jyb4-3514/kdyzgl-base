import { createRouter, createWebHashHistory } from 'vue-router'
import { showFailToast } from 'vant'
import { ROLE } from '@kdyzgl/shared/constants/role.js'
import { canAccess } from '@kdyzgl/shared/domain/permission.js'
import { useAuthStore } from '../stores/auth.js'
import { readToken } from '../utils/authStorage.js'
import { APP_NAME_STAFF } from '../constants/appName.js'

const STAFF_ROLES = [ROLE.STATION_ADMIN, ROLE.STAFF]

/**
 * 移动端路由 · 员工端「驿站助手」（端固定化，B3 拆分自 hrm-demo 移动端聚合路由）
 *
 * 为什么用 hash 模式：静态托管 + 安卓壳远程加载下无服务端 rewrite，hash 模式不需要任何服务端兜底即可直接打开子页面。
 *
 * meta 约定：
 * - public  ：免登录（登录页）
 * - roles   ：页面级角色白名单，与 Mock 的 roles 校验口径一致（前端只做体验拦截，真正拦截在 Mock 层）
 * - tabbar  ：需要底部 Tabbar 的 Tab 页；不标的即详情/表单页（自带返回 NavBar）
 * - title   ：NavBar 标题
 *
 * 端隔离（ADR §3.5 第 8 项）：本路由只含员工域路径；管理端 /boss/* 不存在于此工程（编译期即断）。
 *
 * MVP 裁剪：/staff/kpi、/staff/sync、/staff/parcel、/staff/parcel/:id、/staff/pickup 五条记录整体下架
 * （KPI 考核、同步状态、包裹族含取件核销），路由直接不存在（不加 redirect 兜底，未命中走 404），
 * 页面源码保留在磁盘待后续恢复。
 */
const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'mobileLogin',
    component: () => import('../views/login/index.vue'),
    meta: { public: true, title: '登录' }
  },
  // 员工自助注册（registration-ui-design §2/§3.1）：公开路由，独立页；已登录访问由下方守卫重定向回首页
  {
    path: '/register',
    name: 'staffRegister',
    component: () => import('../views/register/index.vue'),
    meta: { public: true, title: '员工注册' }
  },

  /* ==================== 员工端（STATION_ADMIN / STAFF） ==================== */
  { path: '/staff', redirect: '/staff/home' },
  {
    path: '/staff/home',
    name: 'staffHome',
    component: () => import('../views/staff/home.vue'),
    meta: { tabbar: 'staff', roles: STAFF_ROLES, title: '工作台' }
  },
  // 消息 Tab：通知与待办两个子视图共用中立页（@kdyzgl/shared/ui/MessagePage）+ 本端容器
  {
    path: '/staff/message',
    name: 'staffMessage',
    component: () => import('../views/message/MessagePage.vue'),
    meta: { tabbar: 'staff', roles: STAFF_ROLES, title: '消息' }
  },
  // 通知阅读页：无 tabbar（二级页自带返回，D2.1）
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
  // 旧通知路由重定向，避免历史链接 404（端固定化：一律落员工端消息页）
  { path: '/staff/notification', redirect: { path: '/staff/message', query: { tab: 'notice' } } },

  // 以下两页原为 Tabbar 一级页，本轮降为首页宫格进入的二级页，自带返回 NavBar
  {
    path: '/staff/attendance',
    name: 'staffAttendance',
    component: () => import('../views/staff/attendance.vue'),
    meta: { roles: STAFF_ROLES, title: '打卡' }
  },
  {
    path: '/staff/workorder',
    name: 'staffWorkOrder',
    component: () => import('../views/staff/workorder.vue'),
    meta: { roles: STAFF_ROLES, title: '工单' }
  },

  /* 非 Tab 页：作业操作与详情 */
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
    meta: { roles: STAFF_ROLES, title: '工单详情' }
  },
  {
    path: '/staff/me/password',
    name: 'staffPassword',
    component: () => import('../views/staff/password.vue'),
    meta: { roles: STAFF_ROLES, title: '修改密码' }
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

  /* 需求 8–10 员工端：我的数据类入口（宫格放高频作业，低频查询进「我的」） */
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
    showFailToast('当前身份无权访问该页面')
    return auth.homePath
  }
  return true
})

/** 页签标题：员工端品牌恒定（端固定化后无管理端分支） */
router.afterEach(() => {
  document.title = APP_NAME_STAFF
})

export default router
