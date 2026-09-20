<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { getParcelRanking } from '../../api/index.js'
import { numberText, percent } from '../../utils/format.js'

/**
 * B4 驿站排行（ADMIN · 8 个驿站）
 * 关键交互：按包裹量 / 取件率 / 异常率三种口径重排（排序参数透传给 Mock，前端不重复排序）
 * 点击某驿站 → 弹层看三项指标明细（B4 的「下钻」在移动端用弹层承载，避免再开一层页面）
 * 进度条色、徽标色与 PC 端完全一致（6.3），修跨端「同一名次两种颜色」（P18）。
 * 异常率进度条与 PC 看板同取 500 档 --color-danger-icon（P2-4，进度条属非文本内容，不受 500/700 分工限制）
 */
const SORTS = [
  { key: 'parcelTotal', label: '包裹量', api: '', color: 'var(--chart-inbound)' },
  { key: 'pickupRate', label: '取件率', api: 'pickupRate', color: 'var(--chart-pickup)' },
  { key: 'abnormalRate', label: '异常率', api: 'abnormalRate', color: 'var(--color-danger-icon)' }
]

const sortKey = ref('parcelTotal')
const loading = ref(true)
const error = ref('')
const list = ref([])
const detail = ref(null)
const showDetail = ref(false)

const activeSort = computed(() => SORTS.find((item) => item.key === sortKey.value))

/** 进度条按当前口径归一化到最大值，让 8 个驿站的长短差异一眼可见 */
const maxMetric = computed(() => Math.max(...list.value.map((item) => metricOf(item)), 1))

function metricOf(item) {
  return Number(item[sortKey.value]) || 0
}

function metricText(item) {
  return sortKey.value === 'parcelTotal' ? `${numberText(item.parcelTotal)} 件` : percent(item[sortKey.value])
}

function progressOf(item) {
  return Math.round((metricOf(item) / maxMetric.value) * 100)
}

/** 1/2/3 名用实底白字（≥5:1），4 名起中性浅底 */
function toneOf(index) {
  return index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = await getParcelRanking({ sort: activeSort.value.api })
  } catch (e) {
    error.value = e.message || '加载失败'
    list.value = []
  } finally {
    loading.value = false
  }
}

function changeSort(key) {
  if (key === sortKey.value) return
  sortKey.value = key
  load()
}

function openDetail(item) {
  detail.value = item
  showDetail.value = true
}

onMounted(load)
</script>

<template>
  <div class="page page--loose">
    <PageNav title="驿站排行" />
    <div class="chips">
      <button
        v-for="item in SORTS"
        :key="item.key"
        type="button"
        class="chip"
        :class="{ 'chip--active': item.key === sortKey }"
        :aria-pressed="item.key === sortKey"
        @click="changeSort(item.key)"
      >
        {{ item.label }}
      </button>
    </div>

    <PageState
      :loading="loading"
      :error="error"
      :empty="!list.length"
      empty-text="暂无驿站数据"
      :rows="6"
      @retry="load"
    >
      <div
        v-for="(item, index) in list"
        :key="item.stationId"
        class="list-item rank-item"
        role="button"
        tabindex="0"
        @click="openDetail(item)"
        @keydown.enter="openDetail(item)"
        @keydown.space.prevent="openDetail(item)"
      >
        <span class="rank-item__no" :class="toneOf(index) ? `rank-item__no--${toneOf(index)}` : ''">{{
          index + 1
        }}</span>
        <div class="rank-item__body">
          <div class="flex-between">
            <span class="rank-item__name">{{ item.stationName }}</span>
            <span class="rank-item__value tabular-nums">{{ metricText(item) }}</span>
          </div>
          <van-progress
            :percentage="progressOf(item)"
            :show-pivot="false"
            :color="activeSort.color"
            stroke-width="8"
            :aria-label="`${item.stationName} 占最高值的 ${progressOf(item)}%`"
          />
          <div class="list-item__meta">
            包裹 {{ numberText(item.parcelTotal) }} · 取件率 {{ percent(item.pickupRate) }} · 异常率
            {{ percent(item.abnormalRate) }}
          </div>
        </div>
      </div>
      <p class="tip">当前口径：按{{ activeSort.label }}排序；进度条为该口径下的相对值</p>
    </PageState>

    <van-popup v-model:show="showDetail" round position="bottom" safe-area-inset-bottom>
      <div v-if="detail" class="detail-pop">
        <div class="detail-pop__title">{{ detail.stationName }}</div>
        <van-cell-group inset>
          <van-cell title="包裹总量" :value="`${numberText(detail.parcelTotal)} 件`" />
          <van-cell title="取件率" :value="percent(detail.pickupRate)" />
          <van-cell title="异常率" :value="percent(detail.abnormalRate)" />
        </van-cell-group>
        <div class="detail-pop__foot">
          <van-button block plain type="primary" @click="showDetail = false">关闭</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.rank-item {
  display: flex;
  gap: var(--sp-3);
  align-items: flex-start;
}

/* 徽标 24×24：1/2/3 名实底白字，配色与 PC 端一致（修 P18 的红/金语义冲突） */
.rank-item__no {
  flex: none;
  width: 24px;
  height: 24px;
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  line-height: 24px;
  color: var(--text-2);
  text-align: center;
  background: var(--rank-rest-bg);
  border-radius: var(--r-xs);
}

.rank-item__no--gold {
  color: var(--text-on-dark);
  background: var(--rank-1-bg);
}

.rank-item__no--silver {
  color: var(--text-on-dark);
  background: var(--rank-2-bg);
}

.rank-item__no--bronze {
  color: var(--text-on-dark);
  background: var(--rank-3-bg);
}

.rank-item__body {
  flex: 1;
  min-width: 0;
}

.rank-item__name {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
}

.rank-item__value {
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-primary);
}

.rank-item__body .van-progress {
  margin: var(--sp-2) 0;
}

.detail-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.detail-pop__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.detail-pop__foot {
  padding: var(--sp-5) var(--sp-4) 0;
}
</style>
