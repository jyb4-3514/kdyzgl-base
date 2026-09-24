<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { numberText } from '../utils/format.js'

/**
 * 手写 SVG 折线图（6.1 统一参数表，与 PC 端共用同一份视觉参数；不引 ECharts 的理由见 6.7）
 *
 * 关键实现决策：按容器实测宽绘制 + vector-effect="non-scaling-stroke"，
 * 取代原来的「固定 viewBox 等比拉伸」—— 后者会让 11px 轴文字随容器放大到 18px（修 P10/P36）。
 */
const props = defineProps({
  /** [{ date, inbound, pickup }] */
  points: { type: Array, default: () => [] },
  /** 管理端首屏迷你图：96px、无网格无图例、必显末值 */
  compact: { type: Boolean, default: false },
  unit: { type: String, default: '件' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyText: { type: String, default: '暂无趋势数据' }
})

/** 图例开关状态由页面持有（B3：同页切 7/30 天不能把用户的选择重置掉） */
const visible = defineModel('visible', { type: Object, default: () => ({ inbound: true, pickup: true }) })

const emit = defineEmits(['retry'])

const SERIES = [
  { key: 'inbound', label: '入库' },
  { key: 'pickup', label: '取件' }
]
const DATE_SLICE = 5 // 'MM-DD' 取 'YYYY-MM-DD' 的第 5 位起，避免再引入一份日期格式化

const H = computed(() => (props.compact ? 96 : 200))
const PAD = computed(() =>
  props.compact ? { top: 8, right: 8, bottom: 8, left: 8 } : { top: 12, right: 12, bottom: 24, left: 32 }
)

/* ---------- 容器宽度：ResizeObserver + 100ms 防抖（6.6） ---------- */
const wrapRef = ref(null)
const width = ref(0)
let observer = null
let resizeTimer = null

onMounted(() => {
  if (!wrapRef.value) return
  width.value = Math.round(wrapRef.value.clientWidth)
  if (typeof ResizeObserver === 'undefined') return
  observer = new ResizeObserver((entries) => {
    const next = Math.round(entries[0].contentRect.width)
    clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      width.value = next
    }, 100)
  })
  observer.observe(wrapRef.value)
})

onUnmounted(() => {
  if (observer) observer.disconnect()
  clearTimeout(resizeTimer)
})

const W = computed(() => width.value || 340)
const chartH = computed(() => H.value - PAD.value.top - PAD.value.bottom)
const stepX = computed(() =>
  props.points.length > 1 ? (W.value - PAD.value.left - PAD.value.right) / (props.points.length - 1) : 0
)

/* ---------- 量纲：1/2/5 × 10ⁿ 的整齐上限，保证 3 档刻度取整后不重复（修 P11） ---------- */
const maxValue = computed(() => {
  const values = props.points.flatMap((item) => [Number(item.inbound) || 0, Number(item.pickup) || 0])
  return Math.max(1, ...values)
})

function niceMax(max) {
  const exp = Math.floor(Math.log10(max))
  const base = 10 ** exp
  const norm = max / base
  const step = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10
  return step * base
}

const topTick = computed(() => niceMax(maxValue.value))
const axisMax = computed(() => topTick.value)

/** compact 只标最高/最低两档文字刻度，full 三档带网格（6.1） */
const yTicks = computed(() => {
  const raw = props.compact ? [axisMax.value, 0] : [axisMax.value, axisMax.value / 2, 0]
  const seen = new Set()
  return raw
    .filter((value) => {
      const key = String(Math.round(value))
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .map((value) => ({ value, label: Number(Math.round(value)).toLocaleString('zh-CN') }))
})

/** 网格只画非 0 档，0 档由加粗的基准线承担，避免同一位置画两条线 */
const gridTicks = computed(() => yTicks.value.filter((tick) => tick.value > 0))

function x(index) {
  return PAD.value.left + index * stepX.value
}

function y(value) {
  return PAD.value.top + chartH.value - ((Number(value) || 0) / axisMax.value) * chartH.value
}

function lineOf(key) {
  return props.points.map((item, index) => `${x(index).toFixed(1)},${y(item[key]).toFixed(1)}`).join(' ')
}

/** 入库面积：视觉上区分「量」与「率」，取件只画线（两条面积会互相遮挡） */
const areaOf = computed(() => {
  if (!props.points.length) return ''
  const base = PAD.value.top + chartH.value
  return `${PAD.value.left},${base} ${lineOf('inbound')} ${x(props.points.length - 1).toFixed(1)},${base}`
})

/** X 轴最多 6 个标签，首尾必留（6.1） */
const xLabels = computed(() => {
  const total = props.points.length
  if (!total) return []
  const toText = (item) => String(item.date || '').slice(DATE_SLICE)
  if (total <= 6) return props.points.map((item, index) => ({ index, text: toText(item) }))
  const step = (total - 1) / 5
  const indexes = new Set()
  for (let k = 0; k < 6; k += 1) indexes.add(Math.round(k * step))
  return [...indexes].sort((a, b) => a - b).map((index) => ({ index, text: toText(props.points[index]) }))
})

/* ---------- 数据点：≤14 点逐点、>14 点仅末点（6.1） ---------- */
const pointsCount = computed(() => props.points.length)
const showAllDots = computed(() => !props.compact && pointsCount.value > 0 && pointsCount.value <= 14)
const showEndDot = computed(() => pointsCount.value > 0 && !showAllDots.value)
const showHalo = computed(() => !props.compact && pointsCount.value > 14)
const dotRadius = computed(() => (props.compact ? 2.5 : showAllDots.value ? 2.5 : 3))

const lastIndex = computed(() => pointsCount.value - 1)
/** 末值标注：靠右时左移避让，保证文字不出图（6.1） */
const endLabelAnchor = computed(() => (x(lastIndex.value) > W.value - 56 ? 'end' : 'start'))
const endLabelX = computed(() => x(lastIndex.value) + (endLabelAnchor.value === 'end' ? -6 : 6))

/* ---------- 触摸读数（6.1 交互行） ---------- */
const activeIndex = ref(-1)
const isVisible = (key) => !!(visible.value && visible.value[key])

function onTouchAt(event) {
  if (props.compact || !wrapRef.value || pointsCount.value < 2) return
  const touch = event.touches && event.touches[0]
  if (!touch) return
  const rect = wrapRef.value.getBoundingClientRect()
  const scale = rect.width ? W.value / rect.width : 1
  const relX = (touch.clientX - rect.left) * scale
  const raw = stepX.value ? (relX - PAD.value.left) / stepX.value : 0
  activeIndex.value = Math.min(lastIndex.value, Math.max(0, Math.round(raw)))
}

function clearActive() {
  activeIndex.value = -1
}

const tooltipStyle = computed(() => {
  if (activeIndex.value < 0) return null
  const left = x(activeIndex.value)
  // 浮层宽约 132px，贴近右边缘时改为左对齐定位，避免溢出容器
  return left > W.value - 140 ? { right: `${Math.max(0, W.value - left + 8)}px` } : { left: `${left + 8}px` }
})

const activePoint = computed(() => (activeIndex.value >= 0 ? props.points[activeIndex.value] : null))

/* ---------- 无障碍：role=img + aria-label + 读屏数据表（6.5） ---------- */
const totals = computed(() =>
  props.points.reduce(
    (acc, item) => {
      acc.inbound += Number(item.inbound) || 0
      acc.pickup += Number(item.pickup) || 0
      return acc
    },
    { inbound: 0, pickup: 0 }
  )
)

const ariaLabel = computed(
  () =>
    `近 ${pointsCount.value} 天包裹入库与取件趋势；入库合计 ${numberText(totals.value.inbound)} ${props.unit}，取件合计 ${numberText(
      totals.value.pickup
    )} ${props.unit}`
)

function toggleSeries(key) {
  visible.value = { ...visible.value, [key]: !isVisible(key) }
}

const state = computed(() => {
  if (props.loading) return 'loading'
  if (props.error) return 'error'
  if (!pointsCount.value) return 'empty'
  return 'ready'
})
</script>

<template>
  <div class="line-chart">
    <!-- 图例开关状态由页面持有，切 7/30 天不丢（B3） -->
    <div v-if="!compact && state === 'ready'" class="line-chart__legend">
      <button
        v-for="item in SERIES"
        :key="item.key"
        type="button"
        class="legend-item"
        :class="[`legend-item--${item.key}`, { 'legend-item--off': !isVisible(item.key) }]"
        :aria-pressed="isVisible(item.key)"
        @click="toggleSeries(item.key)"
      >
        <i class="legend-item__dot" aria-hidden="true" />
        {{ item.label }}
      </button>
    </div>

    <!-- 绘图区固定高度：状态切换时高度不变，页面不跳动 -->
    <div ref="wrapRef" class="line-chart__plot" :style="{ height: `${H}px` }">
      <div v-if="state === 'loading'" class="chart-fallback" aria-hidden="true">
        <span v-for="i in 3" :key="i" class="chart-fallback__bar" />
      </div>

      <div v-else-if="state === 'error'" class="chart-fallback chart-fallback--text" role="alert">
        <p class="chart-fallback__error">{{ error }}</p>
        <button type="button" class="chart-fallback__retry" @click="emit('retry')">重试</button>
      </div>

      <div v-else-if="state === 'empty'" class="chart-fallback chart-fallback--text">
        <van-icon name="chart-trending-o" class="chart-fallback__icon" aria-hidden="true" />
        <p class="chart-fallback__hint">{{ emptyText }}</p>
      </div>

      <svg
        v-else
        class="line-chart__svg"
        :style="{ height: `${H}px` }"
        :viewBox="`0 0 ${W} ${H}`"
        preserveAspectRatio="none"
        role="img"
        :aria-label="ariaLabel"
        @touchstart="onTouchAt"
        @touchmove="onTouchAt"
        @touchend="clearActive"
        @touchcancel="clearActive"
      >
        <!-- 横向网格（full 三档）+ 0 轴基准线加粗（修 P41） -->
        <template v-if="!compact">
          <line
            v-for="(tick, index) in gridTicks"
            :key="`grid-${index}`"
            class="chart-grid"
            :x1="PAD.left"
            :x2="W - PAD.right"
            :y1="y(tick.value)"
            :y2="y(tick.value)"
          />
          <line class="chart-baseline" :x1="PAD.left" :x2="W - PAD.right" :y1="y(0)" :y2="y(0)" />
        </template>

        <!-- 刻度文字：11px，不随容器缩放 -->
        <text
          v-for="tick in yTicks"
          :key="`ylab-${tick.label}`"
          class="chart-axis-text"
          :x="PAD.left - 6"
          :y="y(tick.value) + (tick.value === 0 ? -3 : 4)"
          text-anchor="end"
        >
          {{ tick.label }}
        </text>

        <polygon v-if="isVisible('inbound')" class="chart-area" :points="areaOf" />

        <polyline
          v-if="isVisible('inbound')"
          class="chart-line chart-line--inbound"
          :points="lineOf('inbound')"
          fill="none"
          vector-effect="non-scaling-stroke"
        />
        <polyline
          v-if="isVisible('pickup')"
          class="chart-line chart-line--pickup"
          :points="lineOf('pickup')"
          fill="none"
          vector-effect="non-scaling-stroke"
        />

        <!-- 数据点：稀疏时逐点，密集时只标末点，避免糊成一片 -->
        <template v-if="showAllDots">
          <circle
            v-for="(item, index) in points"
            v-show="isVisible('inbound')"
            :key="`dot-in-${index}`"
            class="chart-dot chart-dot--inbound"
            :cx="x(index)"
            :cy="y(item.inbound)"
            :r="dotRadius"
          />
          <circle
            v-for="(item, index) in points"
            v-show="isVisible('pickup')"
            :key="`dot-pk-${index}`"
            class="chart-dot chart-dot--pickup"
            :cx="x(index)"
            :cy="y(item.pickup)"
            :r="dotRadius"
          />
        </template>

        <template v-else-if="showEndDot">
          <circle
            v-if="showHalo && isVisible('inbound')"
            class="chart-halo chart-halo--inbound"
            :cx="x(lastIndex)"
            :cy="y(points[lastIndex].inbound)"
            r="5"
          />
          <circle
            v-if="showHalo && isVisible('pickup')"
            class="chart-halo chart-halo--pickup"
            :cx="x(lastIndex)"
            :cy="y(points[lastIndex].pickup)"
            r="5"
          />
          <circle
            v-if="isVisible('inbound')"
            class="chart-dot chart-dot--inbound"
            :cx="x(lastIndex)"
            :cy="y(points[lastIndex].inbound)"
            :r="dotRadius"
          />
          <circle
            v-if="isVisible('pickup')"
            class="chart-dot chart-dot--pickup"
            :cx="x(lastIndex)"
            :cy="y(points[lastIndex].pickup)"
            :r="dotRadius"
          />
        </template>

        <!-- 触摸十字线：与浮层同源，读数不依赖颜色 -->
        <template v-if="activeIndex >= 0">
          <line
            class="chart-crosshair"
            :x1="x(activeIndex)"
            :x2="x(activeIndex)"
            :y1="PAD.top"
            :y2="PAD.top + chartH"
          />
          <circle
            v-if="isVisible('inbound')"
            class="chart-dot chart-dot--inbound"
            :cx="x(activeIndex)"
            :cy="y(points[activeIndex].inbound)"
            r="4"
          />
          <circle
            v-if="isVisible('pickup')"
            class="chart-dot chart-dot--pickup"
            :cx="x(activeIndex)"
            :cy="y(points[activeIndex].pickup)"
            r="4"
          />
        </template>

        <text
          v-for="label in compact ? [] : xLabels"
          :key="`xlab-${label.index}`"
          class="chart-axis-text"
          :x="x(label.index)"
          :y="H - 8"
          text-anchor="middle"
        >
          {{ label.text }}
        </text>

        <!-- 末值标注：compact 必显，解决「只看到形状、不知量级」 -->
        <text
          v-if="isVisible('inbound')"
          class="chart-end-label"
          :class="{ 'chart-end-label--compact': compact }"
          :x="endLabelX"
          :y="y(points[lastIndex].inbound) - 6"
          :text-anchor="endLabelAnchor"
        >
          {{ numberText(points[lastIndex].inbound) }}
        </text>
        <text
          v-if="isVisible('pickup')"
          class="chart-end-label"
          :x="endLabelX"
          :y="y(points[lastIndex].pickup) + (compact ? 12 : -6)"
          :text-anchor="endLabelAnchor"
        >
          {{ numberText(points[lastIndex].pickup) }}
        </text>
      </svg>

      <!-- 读数浮层：白底 + 描边 + 系列名文字（不允许仅靠颜色区分系列） -->
      <div v-if="activePoint" class="chart-tip" :style="tooltipStyle">
        <div class="chart-tip__date">{{ String(activePoint.date || '').slice(DATE_SLICE) }}</div>
        <div v-for="item in SERIES" v-show="isVisible(item.key)" :key="item.key" class="chart-tip__row">
          <i class="chart-tip__dot" :class="`chart-tip__dot--${item.key}`" aria-hidden="true" />
          <span class="chart-tip__name">{{ item.label }}</span>
          <span class="chart-tip__value tabular-nums">{{ numberText(activePoint[item.key]) }}</span>
        </div>
      </div>
    </div>

    <!--
      读屏数据表替代（6.5），视觉隐藏。
      为什么多包一层 div：`.visually-hidden` 的 width:1px 加在 <table> 上会被表格的 min-content
      宽度击败（375×812 实测该表渲染宽 444px），绝对定位后仍撑出文档 —— boss/home 实测横向溢出 96px。
      包一层块级容器后，由容器承担 1px 与 overflow:hidden，表格被裁剪在内。
    -->
    <div v-if="state === 'ready'" class="visually-hidden">
      <table>
        <caption>
          {{
            ariaLabel
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">日期</th>
            <th scope="col">入库</th>
            <th scope="col">取件</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in points" :key="item.date">
            <th scope="row">{{ item.date }}</th>
            <td>{{ item.inbound }}</td>
            <td>{{ item.pickup }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.line-chart {
  width: 100%;
}

/* 绘图区：浮层定位与宽度测量都以它为基准 */
.line-chart__plot {
  position: relative;
}

.line-chart__svg {
  display: block;
  width: 100%;
}

/* ---------- 图例：热区 ≥44px（修 P34 的 17px），关闭态用文字 + 半透明圆点双通道 ---------- */
.line-chart__legend {
  display: flex;
  gap: var(--sp-2);
  justify-content: flex-end;
}

.legend-item {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  min-height: 44px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-2);
  background: none;
  border: none;
}

.legend-item--off {
  color: var(--text-3);
}

.legend-item__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--r-full);
}

.legend-item--inbound .legend-item__dot {
  background: var(--chart-inbound);
}

.legend-item--pickup .legend-item__dot {
  background: var(--chart-pickup);
}

.legend-item--off .legend-item__dot {
  opacity: 0.3;
}

/* ---------- SVG 元素统一走 Token（2.1 硬规则 4） ---------- */
.chart-grid {
  stroke: var(--border-line);
  stroke-width: 1;
}

.chart-baseline {
  stroke: var(--chart-axis);
  stroke-width: 1;
}

.chart-axis-text {
  font-size: var(--fs-micro);
  fill: var(--text-3);
}

.chart-end-label {
  font-size: var(--fs-caption);
  fill: var(--text-2);
  font-variant-numeric: tabular-nums;
}

.chart-end-label--compact {
  font-size: var(--fs-micro);
}

.chart-area {
  /* --chart-area 自带 0.08 alpha，与 PC TrendChart 同源；原先再叠一层 opacity 会二次衰减 */
  fill: var(--chart-area);
}

.chart-line {
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.chart-line--inbound {
  stroke: var(--chart-inbound);
}

/* 取件折线替代 #07c160：白底 3.46:1，满足 SC 1.4.11（修 P39） */
.chart-line--pickup {
  stroke: var(--chart-pickup);
}

.chart-dot {
  stroke-width: 2;
  fill: var(--surface-card);
}

.chart-dot--inbound {
  stroke: var(--chart-inbound);
}

.chart-dot--pickup {
  stroke: var(--chart-pickup);
}

.chart-halo--inbound {
  fill: var(--chart-inbound);
  opacity: 0.1;
}

.chart-halo--pickup {
  fill: var(--chart-pickup);
  opacity: 0.1;
}

.chart-crosshair {
  stroke: var(--chart-axis);
  stroke-width: 1;
  stroke-dasharray: 4 4;
}

/* ---------- 读数浮层 ---------- */
.chart-tip {
  position: absolute;
  top: var(--sp-4);
  z-index: 2;
  min-width: 120px;
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-sm);
  box-shadow: var(--e2);
}

.chart-tip__date {
  margin-bottom: 2px;
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.chart-tip__row {
  display: flex;
  gap: var(--sp-1);
  align-items: center;
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.chart-tip__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--r-full);
}

.chart-tip__dot--inbound {
  background: var(--chart-inbound);
}

.chart-tip__dot--pickup {
  background: var(--chart-pickup);
}

.chart-tip__name {
  flex: 1;
}

.chart-tip__value {
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

/* ---------- 加载 / 空 / 错误：高度固定，避免状态切换时页面跳动 ---------- */
.chart-fallback {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chart-fallback__bar {
  width: 100%;
  height: var(--sp-3);
  background: var(--chart-skeleton);
  border-radius: var(--r-xs);
  animation: skeleton-pulse 1.2s var(--ease-std) infinite;
}

.chart-fallback__bar:nth-child(2) {
  animation-delay: 0.2s;
}

.chart-fallback__bar:nth-child(3) {
  animation-delay: 0.4s;
}

.chart-fallback--text {
  gap: var(--sp-1);
}

.chart-fallback__icon {
  font-size: 32px;
  color: var(--chart-axis);
}

.chart-fallback__hint,
.chart-fallback__error {
  margin: 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.chart-fallback__error {
  color: var(--color-danger);
}

.chart-fallback__retry {
  min-height: 44px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
  text-decoration: underline;
}
</style>
