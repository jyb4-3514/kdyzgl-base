<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import BossRankBar from '../components/BossRankBar.vue'
import BossScopeNote from '../components/BossScopeNote.vue'
import { getParcelRanking } from '@/api/parcel.js'
import { numberText, percent } from '@/utils/format.js'

/**
 * B4 驿站排行（ADMIN · 8 个驿站）
 * 关键交互：按包裹量 / 取件率 / 异常率三种口径重排（排序参数透传给 Mock，前端不重复排序）
 * 点击某驿站 → 弹层看三项指标明细（B4 的「下钻」在移动端用弹层承载，避免再开一层页面）
 * 排行行渲染已收口到 BossRankBar（N-01）：名次徽标与条形色一律取真源 Token，
 * 修跨端「同一名次两种颜色」（P18）与「条形色各写一套」（AP-06）。
 */
const SORTS = [
  { key: 'parcelTotal', label: '包裹量', api: '', tone: 'primary' },
  { key: 'pickupRate', label: '取件率', api: 'pickupRate', tone: 'success' },
  { key: 'abnormalRate', label: '异常率', api: 'abnormalRate', tone: 'danger' }
]

const sortKey = ref('parcelTotal')
const loading = ref(true)
const error = ref('')
const list = ref([])
const detail = ref(null)
const showDetail = ref(false)

const activeSort = computed(() => SORTS.find((item) => item.key === sortKey.value))

function metricOf(item) {
  return Number(item[sortKey.value]) || 0
}

/** 传给 BossRankBar 的行数据：value 为当前口径值，条形按批内最大值归一化（相对值，不跨口径比较） */
const rankItems = computed(() =>
  list.value.map((item) => ({
    key: item.stationId,
    name: item.stationName,
    value: metricOf(item),
    subText: `包裹 ${numberText(item.parcelTotal)} · 取件率 ${percent(item.pickupRate)} · 异常率 ${percent(
      item.abnormalRate
    )}`
  }))
)

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

/** BossRankBar 只上报行 key，行对象由页面持有，避免把业务对象塞进通用组件 */
function openDetailByKey(key) {
  const item = list.value.find((row) => row.stationId === key)
  if (item) openDetail(item)
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
      <BossRankBar
        :items="rankItems"
        :metric="sortKey === 'parcelTotal' ? 'count' : 'rate'"
        :bar-tone="activeSort.tone"
        @select="openDetailByKey"
      />
      <BossScopeNote :text="`口径：按${activeSort.label}排序；进度条为该口径下的相对值`" />
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
