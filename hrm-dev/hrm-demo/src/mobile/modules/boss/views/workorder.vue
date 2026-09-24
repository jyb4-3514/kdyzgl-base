<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import PageNav from '@/mobile/components/PageNav.vue'
import PageState from '@/mobile/components/PageState.vue'
import SlaTag from '@/mobile/components/SlaTag.vue'
import StationPicker from '@/mobile/components/StationPicker.vue'
import StatusTag from '@/mobile/components/StatusTag.vue'
import WorkOrderCopyButton from '@/mobile/components/WorkOrderCopyButton.vue'
import { getStationList } from '@/mobile/api/org.js'
import { getWorkOrders } from '@/mobile/api/workOrder.js'
import { WORK_ORDER_PRIORITY, WORK_ORDER_STATUS, WORK_ORDER_TYPE } from '@/shared/constants/dict.js'
import { numberText, relativeTime } from '@/mobile/utils/format.js'

/**
 * B11 工单管理（ADMIN · 跨驿站全局视角）
 *
 * 与员工端 S5 工单列表的分工：员工端看「本站派给我的活儿」，本页看「全局谁没处理好」。
 * 故维度不同：本页有驿站筛选与关键字搜索，没有新建工单入口（管理员只做督办与调度，上报由一线发起）。
 * 超时未处理不是一种状态，而是独立筛选维度（Mock 已把「已过 SLA 且仍为待处理/处理中」的判定做在服务端），
 * 所以它作为第 5 个 Tab 与其余四个状态 Tab 互斥，避免出现「状态=已关闭 且 超时未处理」的空结果。
 *
 * TODO(扩展): 管理端「模拟派单」（企微群消息→自动派单）本轮不做，理由见 demo-ux-improvement.md B3.5：
 *   移动端规则表必然退化成卡片堆叠，且契约没有 dry-run 接口，无法在不落单的前提下给出「命中哪条规则」的预览；
 *   完整形态只做 PC。若后续要补，须整片使用 --state-simulate-* 区分演示区，并保留工单契约里的
 *   「接入企微回调时替换为真实签名校验与消息解密」TODO
 */
const TABS = [
  { label: '待处理', status: 0, overdueUnhandled: '' },
  { label: '处理中', status: 1, overdueUnhandled: '' },
  { label: '已解决', status: 2, overdueUnhandled: '' },
  { label: '已关闭', status: 3, overdueUnhandled: '' },
  { label: '超时未处理', status: '', overdueUnhandled: '1' }
]
const PAGE_SIZE = 20

const router = useRouter()

const activeTab = ref(0)
const keyword = ref('')
/** 提交给接口的关键字：与输入框分离，否则每敲一个字都会触发一次查询 */
const searchText = ref('')
const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const stationId = ref(null)
const showStation = ref(false)

const firstLoading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)

const stationLabel = computed(() => {
  if (stationId.value == null) return '全部驿站'
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '全部驿站'
})
const emptyText = computed(() =>
  TABS[activeTab.value].overdueUnhandled ? '没有超时未处理的工单' : '当前筛选条件下没有工单'
)
const overdueOnly = computed(() => !!TABS[activeTab.value].overdueUnhandled)

/** 查询条件只有这一处组装：首屏与翻页共用，避免两套口径 */
const params = computed(() => {
  const tab = TABS[activeTab.value]
  return {
    status: tab.status,
    overdueUnhandled: tab.overdueUnhandled,
    stationId: stationId.value == null ? '' : stationId.value,
    keyword: searchText.value
  }
})

async function loadFirst() {
  firstLoading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getWorkOrders({ ...params.value, pageNum: 1, pageSize: PAGE_SIZE })
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
    finished.value = list.value.length >= page.total
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    firstLoading.value = false
  }
}

async function onLoadMore() {
  if (!pageNum.value) {
    loadingMore.value = false
    return
  }
  const next = pageNum.value + 1
  try {
    const page = await getWorkOrders({ ...params.value, pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    // 已有数据时不整页报错：翻页失败只提示，继续滚动即可重试
    showFailToast('加载更多失败，请稍后重试')
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    stations.value = await getStationList()
  } catch (e) {
    // 筛选项失败不影响主列表，但不能静默成「没有驿站」：弹层里给错误与重试
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

function goDetail(item) {
  // 复用员工端详情页：ADMIN 在同一页上获得指派与跨站转单能力（见 router 的角色白名单说明）
  router.push(`/staff/workorder/${item.id}`)
}

function selectStation(id) {
  if (stationId.value === id) return
  stationId.value = id
  loadFirst()
}

function onSearch() {
  searchText.value = keyword.value.trim()
  loadFirst()
}

watch(activeTab, loadFirst)

onMounted(async () => {
  await Promise.all([loadStations(), loadFirst()])
})
</script>

<template>
  <div class="page page--loose">
    <PageNav title="工单管理" />
    <van-tabs v-model:active="activeTab" class="bleed">
      <van-tab v-for="tab in TABS" :key="tab.label" :title="tab.label" />
    </van-tabs>

    <div class="bleed">
      <van-search v-model="keyword" placeholder="搜索工单号或标题" @search="onSearch" @clear="onSearch" />
    </div>

    <div class="tool-row">
      <span class="tabular-nums">共 {{ numberText(total) }} 条{{ overdueOnly ? '（超时未处理）' : '' }}</span>
      <button type="button" class="chip" :aria-pressed="stationId != null" @click="showStation = true">
        {{ stationLabel }}<van-icon name="arrow-down" aria-hidden="true" />
      </button>
    </div>

    <template v-if="firstLoading">
      <div v-for="i in 4" :key="i" class="skeleton-block sk-row" />
    </template>

    <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
      <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
        <div v-for="item in list" :key="item.id" class="wo-row">
          <div
            class="list-item list-item--rich"
            :class="{ 'list-item--failed': item.overdueUnhandled }"
            role="button"
            tabindex="0"
            @click="goDetail(item)"
            @keydown.enter="goDetail(item)"
            @keydown.space.prevent="goDetail(item)"
          >
            <div class="list-item__title">
              <span>{{ item.orderNo }}</span>
              <StatusTag :dict="WORK_ORDER_STATUS" :value="item.status" />
            </div>
            <div class="wo-title">{{ item.title }}</div>
            <!-- 一行承载优先级 + SLA 倒计时：管理员扫列表只关心「哪些快炸了」 -->
            <div class="list-item__tags">
              <StatusTag :dict="WORK_ORDER_TYPE" :value="item.type" />
              <StatusTag :dict="WORK_ORDER_PRIORITY" :value="item.priority" />
              <SlaTag
                :deadline="item.slaDeadline"
                :active="item.status === 0 || item.status === 1"
                :priority="item.priority"
              />
            </div>
            <div class="list-item__meta">{{ item.stationName }} · 处理人：{{ item.assigneeName || '未指派' }}</div>
            <div class="list-item__meta">上报人 {{ item.reporterName }} · {{ relativeTime(item.createTime) }}</div>
          </div>
          <!-- 复制按钮放在可点击行之外：按钮嵌进 role=button 会让键盘与读屏语义打架 -->
          <WorkOrderCopyButton class="wo-row__copy" :order="item" label="复制该工单详情" />
        </div>
      </van-list>
    </PageState>

    <p class="tip">超时未处理只统计待处理与处理中的工单；点任意一行进入详情页可指派、转单或流转</p>

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="stationId"
      :loading="stationsLoading"
      :error="stationsError"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="loadStations"
      @select="selectStation"
    />
  </div>
</template>

<style scoped>
.sk-row {
  height: 108px;
  margin-top: var(--sp-3);
}

.wo-title {
  margin-top: var(--sp-1);
  overflow: hidden;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
  color: var(--text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ==================== 行内复制 ==================== */

/* 复制按钮独立于可点击行之外（避免 role=button 嵌套），绝对定位不改变行高 */
.wo-row {
  position: relative;
}

/* 行间距改由外层包裹接管：原 .list-item + .list-item 在同级判断，包一层后不再命中 */
.wo-row + .wo-row {
  margin-top: var(--sp-3);
}

/* 为右侧 44×44 复制热区预留位置，避免压住状态标签与处理人信息 */
.wo-row .list-item {
  padding-right: 52px;
}

.wo-row__copy {
  position: absolute;
  top: 50%;
  right: var(--sp-1);
  transform: translateY(-50%);
}
</style>
