<script setup>
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { useAuthStore } from '../stores/auth.js'

/**
 * 退出登录（跨端共享）
 * 二次确认 + 登出 + 回登录页，与 IdentitySwitcher 同型自成一件，两端复用同一份文案与跳转，避免各写一份。
 */
const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '退出后需重新登录，确定继续？' })
  } catch (e) {
    return // 用户取消
  }
  await auth.logout()
  showSuccessToast('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="logout">
    <van-button block type="danger" plain @click="onLogout">退出登录</van-button>
  </div>
</template>

<style scoped>
.logout {
  margin: var(--sp-6) 0 var(--sp-2);
}
</style>