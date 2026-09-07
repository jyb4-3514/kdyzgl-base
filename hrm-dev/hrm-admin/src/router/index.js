import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * 路由表与 requirement.md 5.1 页面清单一致：
 * /login、/dashboard、/employee、/department、/station、/profile
 *
 * 守卫规则（requirement.md 5.3 / TASK C02）：
 * 1. /login 为白名单，已登录且无需强制改密时自动跳落地页；
 * 2. 未登录访问任意受保护页面 → /login（携带 redirect 回跳地址）；
 * 3. pwdChanged=false → 锁定在 /profile 改密流程，访问其他页面一律重定向；
 * 4. meta.admin 标记 ADMIN 专属页面，STAFF 越权访问重定向 /profile。
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue'),
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
        meta: { title: '数据看板', admin: true }
      },
      {
        path: 'employee',
        name: 'Employee',
        component: () => import('../views/employee/index.vue'),
        meta: { title: '员工管理', admin: true }
      },
      {
        path: 'department',
        name: 'Department',
        component: () => import('../views/department/index.vue'),
        meta: { title: '部门管理', admin: true }
      },
      {
        path: 'station',
        name: 'Station',
        component: () => import('../views/station/index.vue'),
        meta: { title: '驿站管理', admin: true }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/profile/index.vue'),
        meta: { title: '个人中心' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  // 1. 登录页白名单：已登录且无需强制改密时直接进入落地页
  if (to.path === '/login') {
    if (authStore.isLoggedIn && !authStore.needChangePwd) {
      return authStore.isAdmin ? '/dashboard' : '/profile'
    }
    return true
  }

  // 2. 未登录：跳登录页并记录回跳地址
  if (!authStore.isLoggedIn) {
    return {
      path: '/login',
      query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : {}
    }
  }

  // 3. 首登强制改密：锁定在 /profile，改密完成前不可访问其他页面
  if (authStore.needChangePwd && to.path !== '/profile') {
    return '/profile'
  }

  // 4. STAFF 越权访问 ADMIN 专属页面：重定向个人中心
  if (to.meta && to.meta.admin && !authStore.isAdmin) {
    return '/profile'
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta && to.meta.title
    ? `${to.meta.title} - 快递驿站智汇系统`
    : '快递驿站智汇系统'
})

export default router
