<script setup>
import { onMounted, onUnmounted, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabbarLayout from './layout/TabbarLayout.vue'
import { APP_NAME_STAFF, resolveAppName } from './constants/appName.js'
import { useAuthStore } from './stores/auth.js'
import { useNotifyStore } from './stores/notify.js'
import { useTodoStore } from './stores/todo.js'
import { UNAUTHORIZED_EVENT } from './utils/http.js'

/**
 * 移动端根组件
 * 只做两件事：按路由 meta.tabbar 决定是否套 TabbarLayout；监听全局 401 广播做登录态清理与跳转
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notify = useNotifyStore()
const todo = useTodoStore()

/**
 * 浏览器标题：本文件只接管员工端
 * 为什么只写员工端：老板端标题已由 router/index.js 的 afterEach 单点维护（并发会话在维护），
 * 两处都写必然在员工端路径上互相覆盖，故这里仅当解析结果是员工端名时才落笔，老板端交还 afterEach。
 * 为什么 flush: 'post'：post 保证本 effect 晚于 afterEach 执行，否则刚写好的员工端标题会被 afterEach 的默认标题盖掉。
 * 静态标题仍留在 mobile.html 作首屏 / 无 JS 兜底。
 */
watchEffect(
  () => {
    if (resolveAppName({ as: route.query.as, role: auth.role }) === APP_NAME_STAFF) {
      document.title = APP_NAME_STAFF
    }
  },
  { flush: 'post' }
)

/** 401 统一出口（http.js 广播）：清理本地登录态并回登录页，避免在拦截器里 import router 形成循环引用 */
function handleUnauthorized() {
  // 幂等兜底：已在登录页说明清理与跳转都做过了，再 replace 一次只会重复触发导航
  if (route.path === '/login') return
  auth.clearSession()
  notify.clear()
  todo.clear()
  router.replace('/login')
}

/**
 * 壳恢复前台时同时刷新未读数与待办计数
 * 待办是「状态快照」，长时间驻留后必然过期（C5-6），角标比列表更早暴露过期问题，故一起刷
 */
function onShellResume() {
  notify.refresh()
  todo.refresh()
}

// 挂载时同步一次角标数据，之后靠 Tab 切换与壳 onResume 刷新
onMounted(() => {
  window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  window.addEventListener('hrm:shell-resume', onShellResume)
  notify.refresh()
  todo.refresh()
})

onUnmounted(() => {
  window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  window.removeEventListener('hrm:shell-resume', onShellResume)
})

watch(
  () => route.path,
  () => {
    if (route.meta.tabbar) notify.refresh()
  }
)
</script>

<template>
  <router-view v-slot="{ Component, route: current }">
    <TabbarLayout v-if="current.meta.tabbar">
      <component :is="Component" />
    </TabbarLayout>
    <component v-else :is="Component" />
  </router-view>
</template>
