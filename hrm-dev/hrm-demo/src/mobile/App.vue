<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TabbarLayout from './layout/TabbarLayout.vue'
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
