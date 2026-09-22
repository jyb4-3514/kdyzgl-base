<script setup>
/**
 * 看板区块卡（标题 + 右侧操作 + 内容）
 * 为什么单独建：首屏 4 个区块卡的头样式原本是 4 份复制（标题字号/字重/左右分布），
 * 收成一层薄壳后，标题排布只有一处真源，区块组件只关心自己的内容。
 */
defineProps({
  title: { type: String, required: true }
})
</script>

<template>
  <el-card shadow="never" class="block-card">
    <template #header>
      <div class="block-head">
        <span>{{ title }}</span>
        <slot name="actions" />
      </div>
    </template>
    <slot />
  </el-card>
</template>

<style scoped lang="scss">
.block-card {
  height: 100%;
  margin-bottom: var(--sp-4);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}
</style>
