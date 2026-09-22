<script setup>
/**
 * N-05 组内空态（老板端专属）
 * 为什么单独立：alerts.vue 原先手写 4 处 `<p class="empty muted">`，文案样式各行其是。
 * 与 PageState 的分工：PageState 管整页三态，本组件只做「卡片内某一区块」的轻量空态，
 * 所以尺寸比 PageState 小一档（图标 28 / 文案 caption），不抢整页空态的位置。
 */
defineProps({
  text: { type: String, required: true },
  /** 图标名走 Vant 内置图标集，默认与 PageState 空态同图标 */
  icon: { type: String, default: 'logistics' },
  /** 给出路动作的文案；不传则不渲染按钮 */
  actionText: { type: String, default: '' }
})

const emit = defineEmits(['action'])
</script>

<template>
  <div class="boss-inline-empty">
    <van-icon :name="icon" class="boss-inline-empty__icon" aria-hidden="true" />
    <p class="boss-inline-empty__text">{{ text }}</p>
    <button v-if="actionText" type="button" class="boss-inline-empty__action" @click="emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.boss-inline-empty {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  align-items: center;
  justify-content: center;
  padding: var(--sp-5) var(--sp-3);
  text-align: center;
}

/* 空态图标不承载语义，用「不可用」灰阶即可，不套语义色造成误读 */
.boss-inline-empty__icon {
  font-size: 28px;
  color: var(--text-disabled);
}

.boss-inline-empty__text {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

/* 描边式次要按钮：高度 44 满足触控，描边取 500 档（与 .page-state__action 同口径） */
.boss-inline-empty__action {
  min-height: 44px;
  padding: 0 var(--sp-5);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}
</style>
