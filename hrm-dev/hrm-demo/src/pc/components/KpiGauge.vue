<script setup>
import { computed } from 'vue'

/**
 * KPI 达成率环形图（C4 Atom）
 * 为什么手写 SVG：全站只有「一个环形进度」这一种图形，引图表库会为 112px 的图背上数百 KB 依赖；
 * 几何尺寸从 Design Token 读取（--gauge-size / --gauge-stroke），保证 PC 与移动端改 Token 时同步变化，
 * 而不是在两处各写一份私有尺寸（C3-2 明确要求共用一套）。
 */
const props = defineProps({
  // 达成率（可为 >1 的小数，如超额达成 1.12）；null 表示无考核数据
  rate: { type: Number, default: null },
  label: { type: String, default: '达成率' },
  // 中心文本：默认展示百分比；得分场景可传入真实分值（如 '92.5'）
  text: { type: String, default: '' }
})

// Token 只在 :root 声明一次、运行期不变化，故初始化读一次即可，不必订阅
const rootStyle = getComputedStyle(document.documentElement)
const size = parseFloat(rootStyle.getPropertyValue('--gauge-size')) || 112
const stroke = parseFloat(rootStyle.getPropertyValue('--gauge-stroke')) || 10
const radius = (size - stroke) / 2
const circumference = 2 * Math.PI * radius

const hasData = computed(() => props.rate != null && Number.isFinite(Number(props.rate)))
// 环封顶在 100%，但中心数字保留真实值——把 112% 画成满环 + 显示 112%，比截断成 100% 更利于判断超额程度
const progress = computed(() => (hasData.value ? Math.max(0, Math.min(1, Number(props.rate))) : 0))
const percentText = computed(() => (hasData.value ? `${Math.round(Number(props.rate) * 100)}%` : '—'))
const centerText = computed(() => props.text || percentText.value)
const ariaLabel = computed(() => `${props.label} ${hasData.value ? percentText.value : '暂无数据'}`)
</script>

<template>
  <figure class="kpi-gauge" :class="{ 'is-empty': !hasData }">
    <!-- 图表替代文本（SC 1.1.1）：环形图本身对读屏不可读，必须给出达成率的文字等价 -->
    <svg
      class="kpi-gauge__ring"
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      role="img"
      :aria-label="ariaLabel"
    >
      <circle class="kpi-gauge__track" :cx="size / 2" :cy="size / 2" :r="radius" :stroke-width="stroke" />
      <circle
        class="kpi-gauge__fill"
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        :stroke-width="stroke"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="circumference * (1 - progress)"
      />
    </svg>
    <span class="kpi-gauge__value" aria-hidden="true">{{ centerText }}</span>
    <figcaption class="kpi-gauge__label">{{ label }}</figcaption>
  </figure>
</template>

<style scoped lang="scss">
.kpi-gauge {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  margin: 0;

  &__ring {
    // 12 点方向起步：SVG 弧线默认从 3 点方向开始，不旋转会让进度看起来偏 90°
    transform: rotate(-90deg);
  }

  &__track {
    fill: none;
    stroke: var(--gauge-track);
  }

  &__fill {
    fill: none;
    stroke: var(--gauge-fill);
    stroke-linecap: round;
    transition: stroke-dashoffset var(--dur-slow) var(--ease-std);
  }

  // 无数据：灰环 + 破折号，与「达成率 0%」区分开
  &.is-empty .kpi-gauge__fill {
    stroke: var(--gauge-track);
    stroke-dashoffset: 0;
  }

  &__value {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    font-size: var(--fs-num-md);
    font-weight: var(--fw-semibold);
    line-height: var(--lh-num-md);
    color: var(--text-1);
    font-variant-numeric: tabular-nums;
  }

  &__label {
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }
}
</style>
