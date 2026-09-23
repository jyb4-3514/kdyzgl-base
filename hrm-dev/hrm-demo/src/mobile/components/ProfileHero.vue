<script setup>
import { roleLabel } from '../constants/accounts.js'

/**
 * 用户信息 Hero 卡（跨端共享）
 * 老板端与员工端只有副信息行数不同，结构与类名完全一致，故抽出来避免两端各自漂移。
 */
defineProps({
  name: { type: String, default: '' },
  role: { type: String, default: '' },
  /** 每项一行副信息（.hero__sub）：员工端两行、老板端一行 */
  lines: { type: Array, default: () => [] }
})
</script>

<template>
  <section class="hero hero--brand profile">
    <div class="flex-between">
      <span class="hero__title">{{ name }}</span>
      <span class="hero__chip">{{ roleLabel(role) }}</span>
    </div>
    <p v-for="(line, index) in lines" :key="index" class="hero__sub">{{ line }}</p>
  </section>
</template>

<style scoped>
.profile {
  margin-top: var(--sp-3);
}
</style>