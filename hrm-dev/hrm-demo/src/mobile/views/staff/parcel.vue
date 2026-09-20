<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { getParcels } from '../../api/index.js'
import { PARCEL_STATUS } from '@/shared/constants/dict.js'
import { numberText, olderThanHours, relativeTime } from '../../utils/format.js'

/**
 * S2 本站包裹（STAFF 只读，写操作在详情与取件核销页）
 * 关键交互：状态 Tab、运单号搜索、下拉刷新、上拉加载更多
 * 运单号搜索走接口的精确匹配（Mock 内部按 id 反解，不扫全表），此时分页无意义，列表只保留命中项。
 */
const TABS = [
  { label: '全部', value: '' },
  { label: '待取件', value: 1 },
  { label: '已取件', value: 2 },
  { label: '异常', value: 3 }
]
const PAGE_SIZE = 20
const OVERDUE_HOURS = 48

const router = useRouter()
const activeTab = ref('')
const keyword = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const error = ref('')
/** 首屏是否已出结果：为空态判断提供依据（未加载完不能显示「暂无数据」） */
const initialized = ref(false)
/** 待入库包裹（status=0）不在 Tab 内，用汇总行兜住，避免「无处可查」 */
const pendingInbound = ref(0)

let busy = false // van-list 会自动把 loading 置 true 再触发 load，故用独立标记做单飞保护

const isSearching = computed(() => !!keyword.value.trim())
const emptyText = computed(() => (isSearching.value ? '没有匹配该运单号的包裹' : '本站暂无包裹记录'))

async function fetchPage() {
  if (busy) return
  busy = true
  try {
    const page = await getParcels({
      status: activeTab.value,
      waybillNo: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    error.value = ''
    total.value = page.total
    list.value = pageNum.value === 1 ? page.list : list.value.concat(page.list)
    finished.value = isSearching.value || list.value.length >= page.total
    pageNum.value += 1
  } catch (e) {
    error.value = e.message || '加载失败'
    finished.value = true
  } finally {
    busy = false
    loading.value = false
    refreshing.value = false
    initialized.value = true
  }
}

/** 汇总行的辅助信息：失败不打断主列表，静默保持 0 */
async function loadPendingInbound() {
  try {
    const page = await getParcels({ status: 0, pageNum: 1, pageSize: 1 })
    pendingInbound.value = page.total
  } catch (e) {
    pendingInbound.value = 0
  }
}

function reset() {
  pageNum.value = 1
  list.value = []
  total.value = 0
  finished.value = false
  error.value = ''
}

/** van-list 触底回调：loading 由组件托管，这里只负责取数 */
async function onLoad() {
  loading.value = true
  await fetchPage()
}

async function onRefresh() {
  reset()
  await Promise.all([fetchPage(), loadPendingInbound()])
}

async function onSearch() {
  reset()
  await fetchPage()
  if (!list.value.length) showFailToast('该运单号在本站无记录')
}

function clearSearch() {
  keyword.value = ''
  onSearch()
}

watch(activeTab, () => {
  reset()
  onLoad()
})

onMounted(() => {
  onLoad()
  loadPendingInbound()
})

function goDetail(item) {
  router.push(`/staff/parcel/${item.id}`)
}

function isOverdue(item) {
  return item.status === 1 && olderThanHours(item.inboundTime, OVERDUE_HOURS)
}
</script>

<template>
  <div class="page page--loose">
    <PageNav title="本站包裹" />
    <van-search
      v-model="keyword"
      class="bleed"
      shape="round"
      placeholder="输入运单号精确查询"
      show-action
      @search="onSearch"
      @clear="onSearch"
    >
      <template #action>
        <button type="button" class="search-action" @click="onSearch">搜索</button>
      </template>
    </van-search>

    <van-tabs v-model:active="activeTab" class="bleed">
      <van-tab v-for="tab in TABS" :key="tab.label" :title="tab.label" :name="tab.value" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="tool-row">
        <span>
          {{ isSearching ? '搜索结果' : '当前筛选' }}共 {{ numberText(total) }} 件<span v-if="!isSearching">
            · 已加载 {{ list.length }}</span
          >
        </span>
        <span v-if="pendingInbound">待入库 {{ numberText(pendingInbound) }} 件</span>
      </div>

      <PageState
        :loading="!initialized"
        :error="list.length ? '' : error"
        :empty="initialized && !list.length"
        :empty-text="emptyText"
        :rows="5"
        @retry="onRefresh"
      >
        <template #empty-action>
          <button v-if="isSearching" type="button" class="empty-reset" @click="clearSearch">清除搜索</button>
        </template>

        <van-list
          v-model:loading="loading"
          :finished="finished"
          :immediate-check="false"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <div
            v-for="item in list"
            :key="item.id"
            class="list-item"
            role="button"
            tabindex="0"
            @click="goDetail(item)"
            @keydown.enter="goDetail(item)"
            @keydown.space.prevent="goDetail(item)"
          >
            <div class="list-item__title">
              <span>{{ item.waybillNo }}</span>
              <StatusTag :dict="PARCEL_STATUS" :value="item.status" />
            </div>
            <!-- 元信息拆两行：一行塞四个字段在 320px 下会溢出（7.5） -->
            <div class="list-item__meta">
              货架 {{ item.shelfCode }} · {{ item.receiverName }} {{ item.receiverPhone }}
            </div>
            <div class="list-item__meta">{{ relativeTime(item.inboundTime) }}入库</div>
            <div v-if="isOverdue(item)" class="list-item__meta list-item__meta--danger">
              <van-icon name="warning-o" aria-hidden="true" /> 入库已超 {{ OVERDUE_HOURS }} 小时未取件
            </div>
          </div>
        </van-list>
      </PageState>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.search-action {
  min-height: 44px;
  padding: 0 0 0 var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

/* 空态重试按钮：描边取 500 档，与 .chip--active 等既有描边控件同口径（P2-3） */
.empty-reset {
  min-height: 44px;
  padding: 0 var(--sp-5);
  margin-top: var(--sp-4);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

/* 列表项之间 12px（7.3），首项与汇总行之间也保留同一节奏 */
.list-item:first-child {
  margin-top: var(--sp-3);
}
</style>
