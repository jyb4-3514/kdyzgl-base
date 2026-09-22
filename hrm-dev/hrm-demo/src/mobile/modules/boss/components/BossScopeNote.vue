<script setup>
/**
 * N-04 口径说明条（老板端专属）
 * 为什么必须收口：口径文案是「这串数字怎么来的」的唯一表达，
 * 散写必然出现同一数字两种口径（工程内已有先例：同步成功率的前端样本聚合 vs 权威计数）。
 */
defineProps({
  /** 完整口径文案，含「口径：」前缀由调用方给出 */
  text: { type: String, required: true },
  tone: { type: String, default: 'default' },
  /** 动态口径（如筛选条件变化后同时说明）才需要播报，默认不打扰读屏 */
  ariaLive: { type: String, default: 'off' }
})
</script>

<template>
  <p
    class="boss-scope-note"
    :class="`boss-scope-note--${tone}`"
    :aria-live="ariaLive === 'polite' ? 'polite' : undefined"
  >
    {{ text }}
  </p>
</template>

<style scoped>
/* 浅底块内一律 --text-2：--text-3 对浅底只有 4.23:1（3.1 硬规则 2） */
.boss-scope-note {
  padding: var(--sp-2) var(--sp-3);
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
  background: var(--surface-subtle);
  border-radius: var(--r-sm);
}

.boss-scope-note--warning {
  color: var(--color-warning);
  background: var(--color-warning-surface);
}
</style>
