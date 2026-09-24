<script setup>
import { computed } from 'vue'
import { numberText, percent } from '@/mobile/utils/format.js'
import BossInlineEmpty from './BossInlineEmpty.vue'

/**
 * N-01 排行榜条形（管理端专属）
 * 为什么单独立：排行行原先在 rank.vue 与 home.vue 各手写一份，
 * 名次配色还分叉过（旧铜牌色与真源名次色两套，AP-06 / D-1）。
 * 名次徽标一律取 `--rank-*` 族，条形色只允许走既有图表/语义 Token，不新增色板。
 */
const props = defineProps({
  /** [{ key, name, value, subText?, tone? }]；tone 覆盖本行条形色，缺省用 barTone */
  items: { type: Array, default: () => [] },
  /** count = 件数（千分位 + 「件」）；rate = 0–1 小数（百分比） */
  metric: { type: String, default: 'count' },
  /** 归一化基准；缺省取本批最大值，为 0 时兜底 1 避免除零 */
  maxValue: { type: Number, default: null },
  /** 条形色语义：primary 入库口径 / success 取件口径 / danger 异常口径 */
  barTone: { type: String, default: 'primary' },
  /** 超出不渲染，避免窄屏把列表无限拉长 */
  maxVisible: { type: Number, default: 8 },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyText: { type: String, default: '暂无排行数据' }
})

const emit = defineEmits(['retry', 'select'])

const TONE_COLOR = {
  primary: 'var(--chart-inbound)',
  success: 'var(--chart-pickup)',
  danger: 'var(--color-danger-icon)'
}

const visibleItems = computed(() => props.items.slice(0, props.maxVisible))
const hiddenCount = computed(() => Math.max(0, props.items.length - props.maxVisible))

const maxValue = computed(() => {
  if (Number.isFinite(props.maxValue) && props.maxValue > 0) return props.maxValue
  return Math.max(...props.items.map((item) => Number(item.value) || 0), 1)
})

function valueText(item) {
  return props.metric === 'rate' ? percent(item.value) : `${numberText(item.value)} 件`
}

function barPercent(item) {
  return Math.min(100, Math.max(0, Math.round(((Number(item.value) || 0) / maxValue.value) * 100)))
}

function barColor(item) {
  return TONE_COLOR[item.tone] || TONE_COLOR[props.barTone] || TONE_COLOR.primary
}

/** 1/2/3 名实底白字（≥5:1），4 名起中性浅底深字 */
function rankTone(index) {
  return index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : ''
}

function onSelect(item) {
  emit('select', item.key)
}
</script>

<template>
  <div class="boss-rank-bar">
    <!-- 骨架行与真实行同构（徽标 24 + 名称行 21 + 条形 8 + 上下 8 外边距），数据到达不跳版 -->
    <template v-if="loading">
      <div v-for="i in maxVisible" :key="`sk-${i}`" class="list-item boss-rank-bar__row" aria-hidden="true">
        <span class="boss-rank-bar__no boss-rank-bar__no--sk" />
        <div class="boss-rank-bar__body">
          <span class="boss-rank-bar__sk boss-rank-bar__sk--name" />
          <span class="boss-rank-bar__sk boss-rank-bar__sk--bar" />
        </div>
      </div>
    </template>

    <!-- 区块级失败不整页替换：行内错误条 + 重试（§4.3 例外二） -->
    <div v-else-if="error" class="boss-rank-bar__error" role="alert">
      <p class="boss-rank-bar__error-text">{{ error }}</p>
      <button type="button" class="boss-rank-bar__retry" @click="emit('retry')">重新加载</button>
    </div>

    <BossInlineEmpty v-else-if="!items.length" :text="emptyText" />

    <template v-else>
      <div
        v-for="(item, index) in visibleItems"
        :key="item.key"
        class="list-item boss-rank-bar__row"
        role="button"
        tabindex="0"
        @click="onSelect(item)"
        @keydown.enter="onSelect(item)"
        @keydown.space.prevent="onSelect(item)"
      >
        <span class="boss-rank-bar__no" :class="rankTone(index) ? `boss-rank-bar__no--${rankTone(index)}` : ''">{{
          index + 1
        }}</span>
        <div class="boss-rank-bar__body">
          <div class="flex-between">
            <span class="boss-rank-bar__name">{{ item.name }}</span>
            <span class="boss-rank-bar__value tabular-nums">{{ valueText(item) }}</span>
          </div>
          <!-- 条形只表达相对量，数值由右侧文字承载；role=img + aria-label 供读屏取到「占最高值几成」 -->
          <div class="boss-rank-bar__track" role="img" :aria-label="`${item.name} 占最高值的 ${barPercent(item)}%`">
            <van-progress :percentage="barPercent(item)" :show-pivot="false" :color="barColor(item)" stroke-width="8" />
          </div>
          <p v-if="item.subText" class="list-item__meta">{{ item.subText }}</p>
        </div>
        <!-- TODO(扩展): 行右侧附加标签位（如超时 SlaTag），当前消费方未使用 -->
        <slot name="extra" :item="item" />
      </div>
      <!-- TODO(扩展): 「查看全部」入口留待接入可跳转的独立页后再补，当前只做数量提示 -->
      <p v-if="hiddenCount" class="boss-rank-bar__more">共 {{ items.length }} 项，仅展示前 {{ maxVisible }} 项</p>
    </template>
  </div>
</template>

<style scoped>
.boss-rank-bar__row {
  display: flex;
  gap: var(--sp-3);
  align-items: flex-start;
}

/* 徽标 24×24：1/2/3 名实底白字，配色取真源 --rank-*（禁用单色值另起一套） */
.boss-rank-bar__no {
  flex: none;
  width: 24px;
  height: 24px;
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  line-height: 24px;
  color: var(--rank-rest-fg);
  text-align: center;
  background: var(--rank-rest-bg);
  border-radius: var(--r-xs);
}

.boss-rank-bar__no--gold {
  color: var(--text-on-dark);
  background: var(--rank-1-bg);
}

.boss-rank-bar__no--silver {
  color: var(--text-on-dark);
  background: var(--rank-2-bg);
}

.boss-rank-bar__no--bronze {
  color: var(--text-on-dark);
  background: var(--rank-3-bg);
}

.boss-rank-bar__no--sk {
  background: var(--surface-sunken);
}

.boss-rank-bar__body {
  flex: 1;
  min-width: 0;
}

/* 长驿站名单行省略，数值不参与压缩 */
.boss-rank-bar__name {
  min-width: 0;
  overflow: hidden;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.boss-rank-bar__value {
  flex: none;
  margin-left: var(--sp-2);
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-primary);
}

.boss-rank-bar__track {
  margin: var(--sp-2) 0;
}

.boss-rank-bar__sk {
  display: block;
  background: var(--surface-sunken);
  border-radius: var(--r-xs);
  animation: skeleton-pulse 1.2s var(--ease-std) infinite;
}

.boss-rank-bar__sk--name {
  width: 40%;
  height: 21px;
}

.boss-rank-bar__sk--bar {
  width: 100%;
  height: 8px;
  margin: var(--sp-2) 0;
}

.boss-rank-bar__error {
  padding: var(--sp-4) var(--sp-3);
  text-align: center;
  background: var(--surface-card);
  border-radius: var(--r-lg);
}

.boss-rank-bar__error-text {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

/* 次要控件：命中区 ≥44px，用下划线而不是再叠一层实底按钮 */
.boss-rank-bar__retry {
  min-height: 44px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
  text-decoration: underline;
}

.boss-rank-bar__more {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-align: center;
}
</style>
