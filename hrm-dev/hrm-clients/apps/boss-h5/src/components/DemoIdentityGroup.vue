<script setup>
import IdentitySwitcher from './IdentitySwitcher.vue'

/** 只有演示态才有身份可切；生产构建下整块不渲染（连同账号清单一起不进包） */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'
</script>

<template>
  <!-- 根级 v-if 走片段，不额外包一层容器：管理端 DOM 与拆分前逐节点一致 -->
  <template v-if="demoEnabled">
    <div class="section-title">切换演示身份<span class="section-title__extra">Demo 专用</span></div>
    <IdentitySwitcher />
  </template>
</template>