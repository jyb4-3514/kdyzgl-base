<script setup>
import MetricCard from './MetricCard.vue'
import StateBlock from './StateBlock.vue'

/**
 * 指标条栅格容器（C-P6 / T09）
 * 改前它自己算列宽（Math.round(24 / items.length)），指标条数一变就错位（P9），
 * 且卡片自带 margin-bottom 逼得包裹页用负 margin 抵消（P13）。这里退回纯栅格职责：
 * 卡片渲染全部委托给 MetricCard。
 *
 * A9-4：列数改为按容器宽度自适应 —— cols 从「实际列数」降级为「最大列数」上限，
 * 每列最小 140px，避免 992–1200 断点把「早退」「缺卡」这类中文标签压变形。
 */
const props = defineProps({
  items: { type: Array, required: true },
  data: { type: Object, default: null },
  // 最大列数（不再等于实际列数）：实际列数 = min(cols, 容器宽度 / 140px)
  cols: { type: Number, default: 4 },
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  errorText: { type: String, default: '指标加载失败' }
})

const emit = defineEmits(['retry'])

// 注意：这里不能把函数命名为 valueOf / toString 等 Object 原型上已有的名字，
// 模板取值会先命中原型方法，导致 "Cannot convert undefined or null to object"
const plain = (value) => (value == null ? '—' : value)
const statValue = (item) => (props.data == null ? null : (item.format || plain)(props.data[item.key]))
</script>

<template>
  <section class="mini-stats">
    <div v-if="title" class="mini-stats__head">
      <h2 class="mini-stats__title">{{ title }}</h2>
    </div>

    <StateBlock v-if="error" variant="error" :title="errorText" @action="emit('retry')" />

    <div v-else class="mini-stats__grid" role="list" :style="{ '--mini-stats-cols': cols }">
      <MetricCard
        v-for="item in items"
        :key="item.key"
        class="mini-stats__card"
        role="listitem"
        variant="inline"
        :label="item.label"
        :value="statValue(item)"
        :loading="loading"
      />
    </div>

    <p v-if="hint && !error" class="mini-stats__hint">{{ hint }}</p>
  </section>
</template>

<style scoped lang="scss">
// 上间距与区块间距由容器统一控制，页面不再需要负 margin 抵消（修 P13）
.mini-stats {
  margin-bottom: var(--sp-4);

  &__head {
    margin-bottom: var(--sp-3);
  }

  &__title {
    margin: 0;
    font-size: var(--fs-h3);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-h3);
    color: var(--text-1);
  }

  // 自适应列数：每列 ≥140px，且不超过 cols 指定的上限（max() 取两者较大值即可实现封顶）
  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(max(140px, calc(100% / var(--mini-stats-cols, 4))), 1fr));
    gap: var(--sp-4);
  }

  &__card {
    height: 100%;
  }

  &__hint {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
