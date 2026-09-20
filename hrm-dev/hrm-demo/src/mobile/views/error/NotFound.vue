<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth.js'

/**
 * 移动端 404（P1-4）：替换原来「未匹配路径就静默 redirect 到首页」的兜底。
 * 标题用真实 h1：van-empty 的 description 渲染成 <p>，读屏拿不到「页面主标题」的语义。
 */
const router = useRouter()
const auth = useAuthStore()

// 焦点管理（SC 2.4.3）：进入 404 后焦点落到主标题，读屏用户不必先 Tab 过整页才知道当前页无效。
// h1 是 heading 而非输入控件，focus() 不会唤起软键盘
const titleRef = ref(null)
onMounted(() => titleRef.value?.focus())

function goHome() {
  router.replace(auth.homePath)
}
</script>

<template>
  <main class="not-found">
    <van-empty image="search">
      <h1 ref="titleRef" class="not-found__title" tabindex="-1">页面不存在</h1>
      <p class="not-found__desc">链接可能已失效，或页面已被移动</p>
      <van-button type="primary" round block class="not-found__action" @click="goHome">返回首页</van-button>
    </van-empty>
  </main>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--sp-4);
  background: var(--surface-page);
}

.not-found__title {
  margin: 0;
  font-size: var(--fs-h1-m);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h1-m);
  color: var(--text-1);
}

.not-found__desc {
  margin: var(--sp-2) 0 var(--sp-5);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-3);
}

/* Vant 按钮本身 44px 高，这里只收窄宽度避免窄屏上顶到两侧 */
.not-found__action {
  max-width: 240px;
  margin: 0 auto;
}
</style>
