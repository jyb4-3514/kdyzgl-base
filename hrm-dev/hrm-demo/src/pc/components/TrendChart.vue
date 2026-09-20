<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Box } from '@element-plus/icons-vue'

/**
 * 趋势折线（入库 / 取件双系列，C-P5 / T12 / 6.1 参数表）
 *
 * 重写原因：
 * 1. 旧版固定 viewBox 720×180 + width:100% 等比缩放，容器 1200px 时轴文字被放大到约 18px（P10）；
 *    现在按 ResizeObserver 实测宽绘制，配合 non-scaling-stroke，文字恒为 12px、线宽恒为 2px。
 * 2. Y 轴刻度取整后会重复（最大值 3 时算出 3,2,2,1,1,0），这是可复现缺陷（P11），改用 nice-number 步长。
 * 3. 旧版只有一条 polyline，没有数据点 / hover 读数 / 图例开关，能力反而弱于移动端（P12）；本次补齐。
 *
 * 为什么不引图表库：见 6.7，需求只有一条双系列折线，引 ECharts 属超范围且违反"不引新依赖"。
 */
const props = defineProps({
  // [{ date: 'yyyy-MM-dd', inbound, pickup }]
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false }
})

const emit = defineEmits(['retry'])

const H = 220
const PAD = { top: 12, right: 16, bottom: 28, left: 44 }

const containerRef = ref(null)
const boxWidth = ref(0)
const hiddenSeries = ref({ inbound: false, pickup: false })
const hoverIndex = ref(null)

/** 30 天以上的数据必须先在数据层做采样聚合，这里只做兜底截断，避免拖垮渲染（6.6） */
const points = computed(() => (props.data.length > 31 ? props.data.slice(-31) : props.data))

const chartW = computed(() => Math.max(boxWidth.value || 720, 240))
const plotW = computed(() => Math.max(chartW.value - PAD.left - PAD.right, 40))
const plotH = H - PAD.top - PAD.bottom

const seriesVisible = (key) => !hiddenSeries.value[key]

const rawMax = computed(() => points.value.reduce((acc, item) => Math.max(acc, item.inbound || 0, item.pickup || 0), 0))

/**
 * nice-number 步长（1/2/2.5/5/10 × 10ⁿ）
 * 数据契约里两条系列都是"件数"，故刻度不允许出现小数：2.5 档落到小数时提升到 5 档，
 * 下限再兜到 1，这样 5 档刻度既不重复（P11）也不会出现 0.5 件这种读不通的标签。
 */
function niceStep(raw) {
  if (!(raw > 0)) return 1
  const base = 10 ** Math.floor(Math.log10(raw))
  const fraction = raw / base
  const picked = [1, 2, 2.5, 5, 10].find((item) => fraction <= item) || 10
  let step = picked * base
  if (!Number.isInteger(step)) step = (picked === 2.5 ? 5 : 10) * base
  return Math.max(1, step)
}

const tickStep = computed(() => niceStep(rawMax.value / 4))
const yMax = computed(() => tickStep.value * 4)

const ticks = computed(() =>
  [0, 1, 2, 3, 4].map((index) => {
    const value = tickStep.value * index
    return { value, label: String(value), y: yAt(value) }
  })
)

const xAt = (index) =>
  points.value.length <= 1 ? PAD.left + plotW.value / 2 : PAD.left + (plotW.value * index) / (points.value.length - 1)

function yAt(value) {
  const ratio = Math.min(Math.max((value || 0) / yMax.value, 0), 1)
  return Number((PAD.top + plotH * (1 - ratio)).toFixed(1))
}

const lastIndex = computed(() => points.value.length - 1)

/** 数据点：≤14 点逐点画，>14 点只在末点画并加外环（点多时逐点会糊成一条带） */
const dense = computed(() => points.value.length > 14)

const positions = computed(() =>
  points.value.map((item, index) => ({
    x: Number(xAt(index).toFixed(1)),
    inbound: yAt(item.inbound),
    pickup: yAt(item.pickup)
  }))
)

const seriesMeta = [
  { key: 'inbound', label: '入库' },
  { key: 'pickup', label: '取件' }
]

const linePath = (key) =>
  positions.value.map((point, index) => `${index ? 'L' : 'M'}${point.x} ${point[key]}`).join(' ')

/** 仅入库带 8% 面积：两条面积互相遮挡后谁也读不出来（6.2） */
const areaPath = computed(() => {
  if (!points.value.length || !seriesVisible('inbound')) return ''
  const baseline = yAt(0)
  const line = positions.value.map((point, index) => `${index ? 'L' : 'M'}${point.x} ${point.inbound}`).join(' ')
  return `${line} L${positions.value[positions.value.length - 1].x} ${baseline} L${positions.value[0].x} ${baseline} Z`
})

// 最多 7 个 X 标签，首尾必留，避免 30 天时糊成一团
const xLabels = computed(() => {
  const step = Math.max(1, Math.ceil(points.value.length / 7))
  return positions.value
    .map((point, index) => ({ x: point.x, text: String(points.value[index].date).slice(5) }))
    .filter((_item, index) => index % step === 0 || index === lastIndex.value)
})

const lastLabel = computed(() => {
  if (!points.value.length) return null
  const point = positions.value[lastIndex.value]
  const text = `${points.value[lastIndex.value].inbound ?? 0} 件`
  // 靠右时左移避让，保证末值不被容器裁掉
  const flip = point.x + text.length * 7 + 12 > chartW.value - PAD.right
  return { x: point.x + (flip ? -8 : 8), y: point.inbound + 4, text, flip }
})

const totals = computed(() =>
  points.value.reduce(
    (acc, item) => ({ inbound: acc.inbound + (item.inbound || 0), pickup: acc.pickup + (item.pickup || 0) }),
    { inbound: 0, pickup: 0 }
  )
)

const ariaLabel = computed(
  () =>
    `近 ${points.value.length} 天包裹入库与取件趋势；入库合计 ${totals.value.inbound.toLocaleString('zh-CN')} 件，` +
    `取件合计 ${totals.value.pickup.toLocaleString('zh-CN')} 件`
)

const hoverPoint = computed(() => {
  if (hoverIndex.value == null) return null
  const item = points.value[hoverIndex.value]
  const point = positions.value[hoverIndex.value]
  if (!item || !point) return null
  const rows = seriesMeta
    .filter((meta) => seriesVisible(meta.key))
    .map((meta) => ({
      key: meta.key,
      label: meta.label,
      value: (item[meta.key] || 0).toLocaleString('zh-CN')
    }))
  // 浮层靠右时整体左移，避免溢出容器
  const left = point.x + 12 + 168 > chartW.value ? point.x - 180 : point.x + 12
  return { date: item.date, rows, left, top: PAD.top }
})

function toggleSeries(key) {
  hiddenSeries.value = { ...hiddenSeries.value, [key]: !hiddenSeries.value[key] }
}

function onMove(event) {
  if (!points.value.length) return
  const rect = event.currentTarget.getBoundingClientRect()
  const offset = event.clientX - rect.left - PAD.left
  const ratio = plotW.value ? offset / plotW.value : 0
  hoverIndex.value = Math.min(Math.max(Math.round(ratio * (points.value.length - 1)), 0), lastIndex.value)
}

/**
 * 尺寸监听：拖拽窗口时高频触发，debounce 100ms 后再重算路径（6.6）
 * TODO(扩展): 若将来出现第 3 种图表类型或需要缩放/导出图片，再按 6.7 的触发条件重新评估图表库
 */
let observer = null
let resizeTimer = null

onMounted(() => {
  if (!containerRef.value) return
  if (typeof ResizeObserver === 'undefined') {
    // 兜底：老浏览器不支持时按初始宽度绘制一次，功能可用只是不再自适应
    boxWidth.value = containerRef.value.clientWidth
    return
  }
  observer = new ResizeObserver((entries) => {
    const width = entries[0] && entries[0].contentRect ? entries[0].contentRect.width : 0
    clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      boxWidth.value = width
    }, 100)
  })
  observer.observe(containerRef.value)
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  clearTimeout(resizeTimer)
})
</script>

<template>
  <div ref="containerRef" class="trend" :style="{ height: `${H}px` }">
    <!-- 加载态：同高度灰块 + 3 条水平脉冲，避免数据到达时整块跳变 -->
    <div v-if="loading" class="trend-state" aria-hidden="true">
      <span v-for="row in 3" :key="row" class="trend-state__pulse" />
    </div>

    <div v-else-if="error" class="trend-state trend-state--error" role="alert">
      <p class="trend-state__text">包裹趋势加载失败</p>
      <el-button link type="primary" @click="emit('retry')">重试</el-button>
    </div>

    <div v-else-if="!points.length" class="trend-state">
      <el-icon :size="32" class="trend-state__icon" aria-hidden="true"><Box /></el-icon>
      <p class="trend-state__text">近 7 天暂无入库数据</p>
    </div>

    <template v-else>
      <div class="trend-legend">
        <button
          v-for="meta in seriesMeta"
          :key="meta.key"
          type="button"
          class="trend-legend__btn"
          :class="[`is-${meta.key}`, { 'is-off': !seriesVisible(meta.key) }]"
          :aria-pressed="seriesVisible(meta.key)"
          @click="toggleSeries(meta.key)"
        >
          <i class="trend-legend__dot" aria-hidden="true" />
          {{ meta.label }}
        </button>
      </div>

      <svg
        class="trend-svg"
        :width="chartW"
        :height="H"
        :viewBox="`0 0 ${chartW} ${H}`"
        role="img"
        :aria-label="ariaLabel"
        @mousemove="onMove"
        @mouseleave="hoverIndex = null"
      >
        <g class="trend-grid">
          <line
            v-for="tick in ticks"
            :key="`g-${tick.value}`"
            :x1="PAD.left"
            :x2="chartW - PAD.right"
            :y1="tick.y"
            :y2="tick.y"
          />
        </g>

        <!-- 0 轴基准线：图表要有"地面"，否则最低刻度线与横轴标签视觉上会漂浮（修 P41） -->
        <line class="trend-baseline" :x1="PAD.left" :x2="chartW - PAD.right" :y1="yAt(0)" :y2="yAt(0)" />

        <g class="trend-axis">
          <text v-for="tick in ticks" :key="`y-${tick.value}`" :x="PAD.left - 8" :y="tick.y + 4" text-anchor="end">
            {{ tick.label }}
          </text>
          <text
            v-for="label in xLabels"
            :key="`x-${label.x}-${label.text}`"
            :x="label.x"
            :y="H - 8"
            text-anchor="middle"
          >
            {{ label.text }}
          </text>
        </g>

        <path v-if="areaPath" class="trend-area" :d="areaPath" />

        <path
          v-for="meta in seriesMeta"
          v-show="seriesVisible(meta.key)"
          :key="`line-${meta.key}`"
          class="trend-line"
          :class="`series-${meta.key}`"
          :d="linePath(meta.key)"
        />

        <g v-if="!dense" class="trend-dots">
          <template v-for="meta in seriesMeta" :key="`dots-${meta.key}`">
            <circle
              v-for="(point, index) in seriesVisible(meta.key) ? positions : []"
              :key="`${meta.key}-${index}`"
              class="trend-dot"
              :class="`series-${meta.key}`"
              :cx="point.x"
              :cy="point[meta.key]"
              r="2.5"
            />
          </template>
        </g>

        <!-- 点密集时只在末点标注，外环 10% 透明做视觉锚点 -->
        <template v-else>
          <template v-for="meta in seriesMeta" :key="`last-${meta.key}`">
            <template v-if="seriesVisible(meta.key)">
              <circle
                class="trend-last-ring"
                :class="`series-${meta.key}`"
                :cx="positions[lastIndex].x"
                :cy="positions[lastIndex][meta.key]"
                r="5"
              />
              <circle
                class="trend-dot"
                :class="`series-${meta.key}`"
                :cx="positions[lastIndex].x"
                :cy="positions[lastIndex][meta.key]"
                r="3"
              />
            </template>
          </template>
        </template>

        <text
          v-if="lastLabel"
          class="trend-last-label"
          :x="lastLabel.x"
          :y="lastLabel.y"
          :text-anchor="lastLabel.flip ? 'end' : 'start'"
        >
          {{ lastLabel.text }}
        </text>

        <!-- hover 十字线 + 该点强调：把"只能看形状"补成"能读数"（修 P12） -->
        <g v-if="hoverPoint">
          <line
            class="trend-cross"
            :x1="positions[hoverIndex].x"
            :x2="positions[hoverIndex].x"
            :y1="PAD.top"
            :y2="PAD.top + plotH"
          />
          <circle
            v-for="meta in seriesMeta"
            v-show="seriesVisible(meta.key)"
            :key="`hover-${meta.key}`"
            class="trend-dot is-active"
            :class="`series-${meta.key}`"
            :cx="positions[hoverIndex].x"
            :cy="positions[hoverIndex][meta.key]"
            r="4"
          />
        </g>

        <rect class="trend-hit" :x="PAD.left" :y="PAD.top" :width="plotW" :height="plotH" />
      </svg>

      <div v-if="hoverPoint" class="trend-tip" :style="{ left: `${hoverPoint.left}px`, top: `${hoverPoint.top}px` }">
        <div class="trend-tip__title">{{ hoverPoint.date }}</div>
        <div v-for="row in hoverPoint.rows" :key="row.key" class="trend-tip__row">
          <i class="trend-tip__dot" :class="`series-${row.key}`" aria-hidden="true" />
          <span>{{ row.label }}</span>
          <strong>{{ row.value }}</strong>
        </div>
      </div>

      <!-- 数据表替代：读屏用户拿不到 SVG 的视觉编码，给等价的两列数据（6.5 第 2 条） -->
      <table class="visually-hidden">
        <caption>
          {{
            ariaLabel
          }}
        </caption>
        <thead>
          <tr>
            <th>日期</th>
            <th>入库</th>
            <th>取件</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in points" :key="`sr-${item.date}`">
            <td>{{ item.date }}</td>
            <td>{{ item.inbound }}</td>
            <td>{{ item.pickup }}</td>
          </tr>
        </tbody>
      </table>
    </template>
  </div>
</template>

<style scoped lang="scss">
.trend {
  position: relative;
  width: 100%;

  &-svg {
    display: block;
  }

  &-grid line {
    stroke: var(--border-line);
    stroke-width: 1;
  }

  &-baseline {
    stroke: var(--chart-axis);
    stroke-width: 1;
  }

  &-axis text {
    font-size: var(--fs-caption);
    fill: var(--text-3);
  }

  &-area {
    fill: var(--chart-area);
    stroke: none;
  }

  &-line {
    fill: none;
    stroke-width: 2;
    stroke-linejoin: round;
    stroke-linecap: round;
    vector-effect: non-scaling-stroke;
  }

  &-dot {
    /* 数据点用白色挖空：属图形绘制色而非「反色文字」，语义上不该走 --text-inverse，保留 L1 直引（P2-4 已登记） */
    fill: var(--c-neutral-0);
    stroke-width: 2;
    vector-effect: non-scaling-stroke;

    &.is-active {
      fill: var(--c-neutral-0);
    }
  }

  &-last-ring {
    fill: none;
    stroke-width: 2;
    opacity: 0.1;
    vector-effect: non-scaling-stroke;
  }

  &-last-label {
    font-size: var(--fs-caption);
    font-weight: var(--fw-medium);
    fill: var(--text-2);
    font-variant-numeric: tabular-nums;
  }

  &-cross {
    stroke: var(--chart-axis);
    stroke-width: 1;
    stroke-dasharray: 4 4;
    vector-effect: non-scaling-stroke;
  }

  &-hit {
    fill: transparent;
  }

  /* 系列色统一由这两个类给出，线条、数据点、外环、图例点、浮层圆点共用 */
  .series-inbound {
    stroke: var(--chart-inbound);
  }

  .series-pickup {
    stroke: var(--chart-pickup);
  }

  &-legend {
    display: flex;
    justify-content: flex-end;
    gap: var(--sp-2);
    margin-bottom: var(--sp-1);

    &__btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 8px;
      border: 1px solid var(--border-line);
      border-radius: var(--r-full);
      background-color: var(--surface-card);
      font-family: inherit;
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
      color: var(--text-2);
      cursor: pointer;
      transition: border-color var(--dur-fast) var(--ease-std);

      &:hover {
        border-color: var(--color-primary-border);
      }

      // 关闭态不只靠颜色：圆点降到 30% 透明 + 文字转三级灰
      &.is-off {
        color: var(--text-3);

        .trend-legend__dot {
          opacity: 0.3;
        }
      }
    }

    &__dot {
      width: 8px;
      height: 8px;
      border-radius: var(--r-full);
    }

    .is-inbound .trend-legend__dot {
      background-color: var(--chart-inbound);
    }

    .is-pickup .trend-legend__dot {
      background-color: var(--chart-pickup);
    }
  }

  &-tip {
    position: absolute;
    z-index: 2;
    min-width: 140px;
    padding: var(--sp-2);
    border: 1px solid var(--border-line);
    border-radius: var(--r-sm);
    background-color: var(--surface-card);
    box-shadow: var(--e2);
    pointer-events: none;

    &__title {
      margin-bottom: var(--sp-1);
      font-size: var(--fs-caption);
      font-weight: var(--fw-semibold);
      color: var(--text-1);
      font-variant-numeric: tabular-nums;
    }

    &__row {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: var(--fs-caption);
      line-height: var(--lh-caption);
      color: var(--text-3);

      strong {
        margin-left: auto;
        font-weight: var(--fw-medium);
        color: var(--text-1);
        font-variant-numeric: tabular-nums;
      }
    }

    &__dot {
      width: 8px;
      height: 8px;
      border-radius: var(--r-full);

      &.series-inbound {
        background-color: var(--chart-inbound);
      }

      &.series-pickup {
        background-color: var(--chart-pickup);
      }
    }
  }

  &-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--sp-2);
    height: 100%;

    &__icon {
      color: var(--chart-axis);
    }

    &__text {
      margin: 0;
      font-size: var(--fs-caption);
      color: var(--text-3);
    }

    &__pulse {
      width: 80%;
      height: 12px;
      border-radius: var(--r-xs);
      background-color: var(--chart-skeleton);
      animation: trend-pulse 1.2s var(--ease-std) infinite;

      &:nth-child(2) {
        animation-delay: 0.15s;
      }

      &:nth-child(3) {
        animation-delay: 0.3s;
      }
    }

    &--error .trend-state__text {
      color: var(--color-danger);
    }
  }
}

@keyframes trend-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.45;
  }
}
</style>
