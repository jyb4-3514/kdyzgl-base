<script setup>
/**
 * 页面标题（C-P1 / T06，修 P6：包裹、同步、工单、通知 4 页原本完全没有标题）
 * 结构固定为「H1 标题 + 副信息行 + 右侧操作区」，避免每页各写一套标题样式
 */
defineProps({
  title: { type: String, required: true },
  // 副信息：数据范围 / 结果总数 / 更新时间，各页口径不同，由页面拼好传进来
  sub: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  // 页面主标题的 tabindex：404 这类"整页无其他可聚焦内容"的页需要把焦点直接落到 h1（SC 2.4.3）。
  // h1 在组件内部渲染，父级无法直接挂属性，故由调用方声明；不传则不输出该属性，对既有页面零影响
  titleTabindex: { type: [String, Number], default: undefined }
})
</script>

<template>
  <header class="page-header">
    <div class="page-header__main">
      <h1 class="page-header__title" :tabindex="titleTabindex">{{ title }}</h1>
      <!-- loading 时副信息换成骨架条：标题立刻可见，数据到了再补详情，避免整块闪一下 -->
      <div v-if="loading" class="page-header__skeleton" aria-hidden="true" />
      <p v-else-if="sub || $slots.sub" class="page-header__sub">
        <slot name="sub">{{ sub }}</slot>
      </p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);

  /* 窄窗（768–1200）操作区放不下时整块换到第二行，而不是把标题块压成三行（P2-3）。
   * 宽屏下不会触发，视觉与改前一致 */
  flex-wrap: wrap;

  &__main {
    /* 允许收缩：不给 min-width:0 时标题块按内容最小宽度撑住，反而把操作区挤出容器 */
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: var(--fs-h1);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-h1);
    color: var(--text-1);
  }

  &__sub {
    margin: var(--sp-1) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__skeleton {
    width: 120px;
    height: var(--lh-caption);
    margin-top: var(--sp-1);
    border-radius: var(--r-xs);
    background-color: var(--surface-sunken);
  }

  &__actions {
    display: flex;

    /* 排班/包裹这类 5 个以上控件的页头，空间不够时按钮组内部再换行，避免互相挤压 */
    flex-wrap: wrap;
    justify-content: flex-end;
    align-items: center;
    gap: var(--sp-2);
    flex-shrink: 0;
  }
}
</style>
