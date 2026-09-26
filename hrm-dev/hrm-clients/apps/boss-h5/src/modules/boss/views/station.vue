<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StationPicker from '@kdyzgl/shared/ui/StationPicker.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { getEmployees, getStationList } from '@/api/org.js'
import { roleLabel } from '@/constants/accounts.js'

/**
 * 站点管理（只读骨架）：先选驿站，再看该站名下的员工名册，点开复用既有员工详情页。
 *
 * 数据源用 GET /employees?stationId=（名册完整、含 role/status），而不是 /hr/profiles：
 * 「某驿站有哪些人」是组织事实，不该由人事建档进度决定（设计规范 §11.2 裁决 A）。
 * 为什么与「人事管理」不重复：人事管理从「员工」出发查档案、可调薪；本页从「驿站」出发查人，纯只读。
 *
 * 本批零写操作：不渲染分配 / 调整 / 调动 / 新增 / 停用等任何按钮（含置灰版本），也不渲染 ActionBar。
 * TODO(扩展): 站点管理写操作（分配/调整/跨站调动）—— 前置条件：① 后端提供站点级员工写接口
 *   （当前 /employees 写操作仅 ADMIN 且无批量分配/跨站接口）；② 口径确认（谁能分配、跨站是否需审批）；
 *   ③ 命中 R04/R05（分配规则/人员分配）须先经算法工程师出方案；④ 过 P0.6 技术评审闸门。
 */
const PAGE_SIZE = 20

/**
 * 员工在职状态字典（1=在职，0=已停用）。
 * 为什么写在本页：shared/constants/dict.js 无员工状态字典（既有字典只有驿站/包裹/工单等），
 * 只在建站名册这一处消费，先按页内常量收口；若后续第二处消费再上移共享包。
 */
const EMPLOYEE_STATUS = {
  1: { label: '在职', type: 'success' },
  0: { label: '已停用', type: 'info', variant: 'outline' }
}

const router = useRouter()

/* 驿站选择 */
const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const stationId = ref(null)
const showStation = ref(false)
const currentStationName = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '-'
})

/* 员工名册 */
const keyword = ref('')
const firstLoading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
/** van-list 的加载位：初始 false，触底时由 List 置位并回调 onLoadMore */
const loadingMore = ref(false)

/** 空态文案与「搜索无命中」分离（设计 §5.2 R1-3：空态不得出现"失败/错误/网络"） */
const emptyText = computed(() => {
  if (stationId.value == null) return '暂无可选驿站，请先在 PC 端维护驿站'
  return keyword.value.trim() ? '没有匹配的员工' : '该站点暂无员工'
})

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    const listData = await getStationList()
    stations.value = listData
    // 与打卡规则页同口径：默认选中首个驿站，避免出现「未选站」的空列表
    if (stationId.value == null && listData.length) stationId.value = listData[0].id
  } catch (e) {
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

/** 首屏/切站/换词统一走整表替换：pageNum=0 时 van-list 的触底回调直接短路，防止旧词的结果续到新词后面 */
async function loadFirst() {
  if (stationId.value == null) {
    firstLoading.value = false
    list.value = []
    total.value = 0
    return
  }
  firstLoading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getEmployees({
      stationId: stationId.value,
      keyword: keyword.value.trim() || undefined,
      pageNum: 1,
      pageSize: PAGE_SIZE
    })
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
    finished.value = list.value.length >= page.total
  } catch (e) {
    error.value = e.message || '员工名册加载失败'
  } finally {
    firstLoading.value = false
  }
}

/** 触底加载下一页：首屏由 loadFirst 负责，这里只处理 pageNum >= 1 的追加 */
async function onLoadMore() {
  if (!pageNum.value) {
    loadingMore.value = false
    return
  }
  const next = pageNum.value + 1
  try {
    const page = await getEmployees({
      stationId: stationId.value,
      keyword: keyword.value.trim() || undefined,
      pageNum: next,
      pageSize: PAGE_SIZE
    })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    // 已有数据时不打断列表：翻页失败只提示，用户可继续滚动重试
    showFailToast('加载更多失败，请稍后重试')
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

function selectStation(id) {
  if (stationId.value === id) return
  stationId.value = id
  loadFirst()
}

/** 详情沿用既有员工档案页，不新造详情页 */
function openEmployee(item) {
  router.push(`/boss/hr/${item.id}`)
}

onMounted(async () => {
  await loadStations()
  await loadFirst()
})
</script>

<template>
  <div class="station-page">
    <PageNav title="站点管理" />
    <div class="page page--loose">
      <!-- 驿站选择：复用 StationPicker；allow-all=false，本页必须选定一个驿站 -->
      <van-cell class="station-cell" title="当前驿站" :value="currentStationName" is-link @click="showStation = true" />
      <StationPicker
        v-model:show="showStation"
        :stations="stations"
        :model-value="stationId"
        :loading="stationsLoading"
        :error="stationsError"
        :allow-all="false"
        empty-text="暂无可选驿站，请先在 PC 端维护驿站"
        @retry="loadStations"
        @select="selectStation"
      />

      <!-- 搜索：仅在选定驿站后渲染，避免对空列表搜索 -->
      <van-search
        v-if="stationId != null"
        v-model="keyword"
        placeholder="搜索姓名或登录账号"
        shape="round"
        @search="loadFirst"
        @clear="loadFirst"
      />

      <p class="tool-row tabular-nums">共 {{ total }} 名员工</p>

      <!-- 首屏单独走骨架（高度对齐 list-item--rich）；van-list 需真实挂载才会触发触底，不能藏在骨架分支里 -->
      <template v-if="firstLoading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <button
            v-for="item in list"
            :key="item.id"
            type="button"
            class="list-item list-item--rich emp-row"
            @click="openEmployee(item)"
          >
            <div class="list-item__title">
              <span>{{ item.realName }}</span>
              <van-icon name="arrow" aria-hidden="true" />
            </div>
            <div class="list-item__meta">{{ item.username }} · {{ item.deptName || '未分配部门' }}</div>
            <div class="list-item__meta tabular-nums">
              {{ roleLabel(item.role) }} · 入职 {{ item.entryDate || '-' }}
            </div>
            <div class="list-item__tags">
              <StatusTag :dict="EMPLOYEE_STATUS" :value="String(item.status)" />
            </div>
          </button>
        </van-list>
      </PageState>

      <p class="tip">本页仅查看；员工分配与调整暂未开放。</p>
    </div>
  </div>
</template>

<style scoped>
.station-cell {
  margin-top: var(--sp-3);
  border-radius: var(--r-lg);
}

.sk-row {
  height: 100px;
  margin-top: var(--sp-3);
}

.emp-row {
  display: block;
  width: 100%;
  margin-top: var(--sp-3);
  text-align: left;
  border: none;
}
</style>
