<script setup>
import { computed, getCurrentInstance } from 'vue'
import { numberText } from '@/mobile/utils/format.js'
import BossInlineEmpty from './BossInlineEmpty.vue'

/**
 * N-02 占比摘要（100% 堆叠条，老板端专属）
 * 为什么不用环图：窄屏（≤480px）图例易折行；且构成下钻已由调用页既有入口承担（UI 规范 §8.2）。
 * 为什么本体不当下钻入口：它是视觉摘要，下钻入口必须留在调用页；
 * 故只有调用方真的绑定了 select 监听时图例才升级为可点按钮，否则渲染为纯文本，不制造假入口。
 * 色值一律取既有 Token（--chart-* / --color-*-icon / --state-neutral-fg），不新增色板、不出现十六进制。
 */
const props = defineProps({
  /** [{ key, label, value, tone? }]；tone ∈ primary | success | warning | danger | neutral */
  segments: { type: Array, default: () => [] },
  /** 分母，缺省按 segments 求和 */
  total: { type: Number, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyText: { type: String, default: '暂无占比数据' },
  showLegend: { type: Boolean, default: true }
})

const emit = defineEmits(['retry', 'select'])

const TONE_COLOR = {
  primary: 'var(--chart-inbound)',
  success: 'var(--chart-pickup)',
  warning: 'var(--color-warning-icon)',
  danger: 'var(--color-danger-icon)',
  neutral: 'var(--state-neutral-fg)'
}

/** 最多 5 段，第 6 段起并入「其他」：窄屏图例不折行（UI 规范 §1.3 N-02） */
const MAX_SEGMENTS = 5

/**
 * 读父级 vnode 上的 onSelect 判断有无监听（与 Element Plus 同做法），无监听则图例保持纯文本。
 * TODO(扩展): 首个真要下钻的消费页接入时，再给图例按钮补 aria-pressed 与选中态描边（当前按需渲染，未做选中态）
 */
const selectable = !!getCurrentInstance()?.vnode?.props?.onSelect

const list = computed(() =>
  props.segments.map((item) => ({
    key: item.key,
    label: item.label,
    value: Number(item.value) || 0,
    tone: item.tone || 'primary'
  }))
)

const columns = computed(() => {
  if (list.value.length <= MAX_SEGMENTS) return list.value
  const head = list.value.slice(0, MAX_SEGMENTS - 1)
  const tail = list.value.slice(MAX_SEGMENTS - 1)
  const other = {
    key: '__other__',
    label: '其他',
    value: tail.reduce((sum, item) => sum + item.value, 0),
    tone: 'neutral'
  }
  return [...head, other]
})

const sum = computed(() => columns.value.reduce((acc, item) => acc + item.value, 0))

/** 分母取显式 total；它与段和不一致时回落段和，否则会出现「条形没满 + 占比合计 100%」的自相矛盾 */
const denom = computed(() => {
  const total = Number(props.total)
  if (!Number.isFinite(total) || total <= 0) return sum.value
  return Math.abs(total - sum.value) > 1e-6 ? sum.value : total
})

/**
 * 最大余数法：各段占比先向下取整，再把剩下的整数个百分点依次补给小数部分最大的段。
 * 为什么必须这样：逐段四舍五入会出现「四段加起来 101%」，而堆叠条本身恒为 100%，数字与图形会互相打脸。
 */
function splitPercents(values, base) {
  const raw = values.map((value) => (base > 0 ? (value / base) * 100 : 0))
  const out = raw.map((value) => Math.floor(value))
  let rest = 100 - out.reduce((acc, value) => acc + value, 0)
  const byRest = raw
    .map((value, index) => ({ index, frac: value - Math.floor(value) }))
    .sort((a, b) => b.frac - a.frac || a.index - b.index)
  for (const item of byRest) {
    if (rest <= 0) break
    out[item.index] += 1
    rest -= 1
  }
  return out
}

const rows = computed(() => {
  const percents = splitPercents(
    columns.value.map((item) => item.value),
    denom.value
  )
  return columns.value.map((item, index) => ({
    ...item,
    percent: percents[index],
    color: TONE_COLOR[item.tone] || TONE_COLOR.primary
  }))
})

/** 零值段不占条块，但仍列进图例：「0 站已停用」是结论而不是缺数据（与四态计数按钮同口径） */
const barRows = computed(() => rows.value.filter((item) => item.value > 0))

/** 全 0 / 空数组走空态，不画一条全灰的空条 */
const isEmpty = computed(() => denom.value <= 0)

/** 骨架项数与真实图例项数一致（图例两列一行），数据到达不跳版 */
const skeletonCount = computed(() => Math.max(columns.value.length, 2))

const ariaLabel = computed(() => {
  const detail = rows.value.map((item) => `${item.label} ${numberText(item.value)}（${item.percent}%）`).join('、')
  return `占比构成，合计 ${numberText(denom.value)}：${detail}`
})

function onSelect(key) {
  emit('select', key)
}
</script>

<template>
  <div class="boss-share-bar">
    <!-- 骨架与真实内容同构（条 8px + 两列图例），高度一致，数据到达不跳版 -->
    <template v-if="loading">
      <div class="boss-share-bar__track">
        <span class="boss-share-bar__sk-bar" />
      </div>
      <div class="boss-share-bar__legend">
        <span v-for="i in skeletonCount" :key="`sk-${i}`" class="boss-share-bar__sk-legend" />
      </div>
    </template>

    <!-- 区块级失败不整页替换：行内错误条 + 重试 -->
    <div v-else-if="error" class="boss-share-bar__error" role="alert">
      <p class="boss-share-bar__error-text">{{ error }}</p>
      <button type="button" class="boss-share-bar__retry" @click="emit('retry')">重新加载</button>
    </div>

    <BossInlineEmpty v-else-if="isEmpty" :text="emptyText" />

    <template v-else>
      <!-- 图的视觉本体：role=img 兜住整段语义（与 LineChart 同构）。
           图例与等价表必须留在它之外，否则会被 img 角色一并吞掉，读屏反而取不到明细 -->
      <div class="boss-share-bar__figure" role="img" :aria-label="ariaLabel">
        <div class="boss-share-bar__track">
          <span
            v-for="item in barRows"
            :key="item.key"
            class="boss-share-bar__seg"
            :style="{ flexGrow: item.percent, background: item.color }"
          />
        </div>
      </div>

      <!-- 图例带文字与数值：段与段不能只靠颜色区分（§8.6 红线） -->
      <ul v-if="showLegend" class="boss-share-bar__legend">
        <li v-for="item in rows" :key="item.key" class="boss-share-bar__legend-item">
          <component
            :is="selectable ? 'button' : 'span'"
            class="boss-share-bar__legend-btn"
            :type="selectable ? 'button' : null"
            v-on="selectable ? { click: () => onSelect(item.key) } : {}"
          >
            <i class="boss-share-bar__dot" :style="{ background: item.color }" aria-hidden="true" />
            <span class="boss-share-bar__label">{{ item.label }}</span>
            <span class="boss-share-bar__value tabular-nums">{{ numberText(item.value) }}（{{ item.percent }}%）</span>
          </component>
        </li>
      </ul>

      <!-- 读屏等价明细表（§8.6 红线）：caption + th scope。
           外面必须再包一层 visually-hidden —— <table> 自身吃不到 width:1px（表格以最小内容宽为准，
           且 nowrap 的 caption 抬高 CAPMIN），实测会把 1px 占位撑成 436px、给页面加出横向滚动条 -->
      <div class="visually-hidden">
        <table>
          <caption>
            {{
              ariaLabel
            }}
          </caption>
          <thead>
            <tr>
              <th scope="col">分类</th>
              <th scope="col">数量</th>
              <th scope="col">占比</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rows" :key="item.key">
              <th scope="row">{{ item.label }}</th>
              <td>{{ numberText(item.value) }}</td>
              <td>{{ item.percent }}%</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 条高 8px 与 BossRankBar 的条形同规格，同页出现时视觉重量一致 */
.boss-share-bar__track {
  display: flex;
  height: 8px;
  overflow: hidden;
  background: var(--surface-sunken);
  border-radius: var(--r-xs);
}

/* flex-grow 直接用取整后的占比：图形与图例数字同源，不会各说一套。
   宽度过渡在 tokens.base.scss 的 prefers-reduced-motion 全局降级下自动变静态，此处不重复声明媒体查询 */
.boss-share-bar__seg {
  flex-basis: 0;
  min-width: 2px;

  /* 段间用内阴影留 1px 白缝：相邻语义色（如 warning / danger）在窄屏上更易分辨，且不占布局 */
  box-shadow: inset -1px 0 0 0 var(--surface-card);
  transition: flex-grow var(--dur-base) var(--ease-std);
}

.boss-share-bar__legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-1) var(--sp-3);
  padding: 0;
  margin: var(--sp-2) 0 0;
  list-style: none;
}

.boss-share-bar__legend-item {
  min-width: 0;
}

/* 图例文字一律 --text-2：--text-3 在页面浅底（neutral-50）上只有 4.23:1，不达 AA */
.boss-share-bar__legend-btn {
  display: flex;
  gap: var(--sp-1);
  align-items: center;
  width: 100%;
  padding: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
  text-align: left;
  background: none;
  border: none;
}

/* 仅当图例真被提升为按钮时才按触控下限撑高，纯文本态不白占 44px */
button.boss-share-bar__legend-btn {
  min-height: var(--touch-min);
}

.boss-share-bar__dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: var(--r-xs);
}

.boss-share-bar__label {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 值与占比合成一组右对齐：「4（50%）」比「4 50%」少一层歧义，且整组贴右不会左右抖动 */
.boss-share-bar__value {
  flex: none;
  margin-left: auto;
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.boss-share-bar__sk-bar {
  width: 100%;
  height: 100%;
  background: var(--chart-skeleton);
  animation: skeleton-pulse 1.2s var(--ease-std) infinite;
}

.boss-share-bar__sk-legend {
  height: var(--lh-caption);
  background: var(--surface-sunken);
  border-radius: var(--r-xs);
  animation: skeleton-pulse 1.2s var(--ease-std) infinite;
}

.boss-share-bar__error {
  padding: var(--sp-4) var(--sp-3);
  text-align: center;
  background: var(--surface-card);
  border-radius: var(--r-lg);
}

.boss-share-bar__error-text {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

/* 次要控件：命中区 ≥44px，用下划线而不是再叠一层实底按钮 */
.boss-share-bar__retry {
  min-height: var(--touch-min);
  padding: 0 var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
  text-decoration: underline;
}
</style>
