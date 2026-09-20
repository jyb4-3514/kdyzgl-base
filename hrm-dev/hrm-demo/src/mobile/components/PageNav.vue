<script setup>
import { useRouter } from 'vue-router'

/**
 * 统一顶部导航（C-M5）
 * back=false 用于 Tab 页（无返回语义）；详情页走 history 回退，
 * 壳内返回键由 bridge.js 的 HrmShell.onBackPressed 复用同一条 history 链路。
 * 返回热区为什么自己写按钮：Vant 默认箭头只是图标，读屏读不出「返回」，
 * 自绘 44×44 的 <button aria-label="返回"> 后键盘与读屏都可达（修 P33）。
 */
defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: true },
  fixed: { type: Boolean, default: true }
})

const router = useRouter()
function onBack() {
  router.back()
}
</script>

<template>
  <van-nav-bar class="page-nav" :title="title" :fixed="fixed" :placeholder="fixed" :border="false" @click-left="onBack">
    <!-- 点击事件交给 Vant 的左侧容器统一 emit，按钮只负责可聚焦与可朗读 -->
    <template v-if="back" #left>
      <button type="button" class="page-nav__back" aria-label="返回">
        <van-icon name="arrow-left" aria-hidden="true" />
      </button>
    </template>
    <template #right>
      <slot name="right" />
    </template>
  </van-nav-bar>
</template>

<style scoped>
.page-nav {
  /* 固定定位时下移到状态栏之下，避免被刘海/状态栏遮挡 */
  top: var(--safe-top);
}

.page-nav :deep(.van-nav-bar__title) {
  max-width: 60%;
  font-weight: var(--fw-semibold);
}

.page-nav :deep(.van-nav-bar__left),
.page-nav :deep(.van-nav-bar__right) {
  min-width: 44px;
  min-height: var(--navbar-h);
}

.page-nav__back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-left: calc(-1 * var(--sp-3));
  font-size: var(--fs-h2);
  color: var(--text-1);
  background: none;
  border: none;
}
</style>
