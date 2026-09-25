<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showFailToast } from 'vant'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StationPicker from '@/components/StationPicker.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { getAttendanceRecords } from '@/api/attendance.js'
import { getStationList } from '@/api/org.js'
import { ATTENDANCE_STATUS, CHECK_MODE, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import { clockOf, formatDate, addDays, periodLabel } from '@/utils/attendance.js'

/**
 * B10 打卡记录（ADMIN · 列表 + 筛选）
 * 筛选口径：日期（预设区间） / 驿站 / 状态，三者都收敛到 Mock 层的同一组查询参数，前端不重复过滤。
 * 异常卡（ABNORMAL）单列一个状态项：它不计入出勤统计，但是排障时最需要看的一类。
 */
const PAGE_SIZE = 20

/** 日期只给预设区间：自定义区间需要日历组件，且驿站日常查看就是「今天/这周/这个月」三种口径 */
const DATE_OPTIONS = [
  { key: 'today', label: '今日' },
  { key: 'week', label: '近 7 天' },
  { key: 'month', label: '近 30 天' },
  { key: 'all', label: '全部' }
]
const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'NORMAL', label: '正常' },
  { value: 'LATE', label: '迟到' },
  { value: 'EARLY_LEAVE', label: '早退' },
  { value: 'ABNORMAL', label: '异常' }
]

const route = useRoute()

const firstLoading = ref(true)
const error = ref('')
const list = ref([])
const pageNum = ref(0)
const finished = ref(false)
/** van-list 的加载位：初始 false，由 List 触底时置位并回调 onLoad */
const loadingMore = ref(false)

const dateKey = ref('today')
const status = ref(STATUS_OPTIONS.some((item) => item.value === route.query.status) ? String(route.query.status) : '')
const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const stationId = ref(null)
const showStation = ref(false)

const stationLabel = computed(() => {
  if (stationId.value == null) return '全部驿站'
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '全部驿站'
})

/** 预设区间 → startDate/endDate（'全部' 时都不传，服务端按全量返回） */
function rangeOf(key) {
  const now = new Date()
  if (key === 'today') return { startDate: formatDate(now), endDate: formatDate(now) }
  if (key === 'week') return { startDate: formatDate(addDays(now, -6)), endDate: formatDate(now) }
  if (key === 'month') return { startDate: formatDate(addDays(now, -29)), endDate: formatDate(now) }
  return { startDate: '', endDate: '' }
}

/** 查询参数收口：筛选条件只有这一处组装，避免分页与首次加载两套口径 */
const params = computed(() => {
  const { startDate, endDate } = rangeOf(dateKey.value)
  return {
    stationId: stationId.value == null ? '' : stationId.value,
    status: status.value,
    startDate,
    endDate
  }
})

async function loadFirst() {
  firstLoading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getAttendanceRecords({ ...params.value, pageNum: 1, pageSize: PAGE_SIZE })
    pageNum.value = 1
    list.value = page.list
    finished.value = list.value.length >= page.total
  } catch (e) {
    error.value = e.message || '加载失败'
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
    const page = await getAttendanceRecords({ ...params.value, pageNum: next, pageSize: PAGE_SIZE })
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

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    stations.value = await getStationList()
  } catch (e) {
    // 驿站筛选项失败不影响主列表，但仍要把「取不到」告诉用户，不能静默成「没有驿站」
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

function selectDate(key) {
  if (dateKey.value === key) return
  dateKey.value = key
  loadFirst()
}

function selectStatus(value) {
  if (status.value === value) return
  status.value = value
  loadFirst()
}

function selectStation(id) {
  if (stationId.value === id) return
  stationId.value = id
  loadFirst()
}

onMounted(async () => {
  await Promise.all([loadStations(), loadFirst()])
})
</script>

<template>
  <div class="rec-page">
    <PageNav title="打卡记录" />
    <div class="page page--loose">
      <div class="card filters">
        <div class="filter-row">
          <span class="filter-row__label">日期</span>
          <div class="filter-row__chips">
            <button
              v-for="item in DATE_OPTIONS"
              :key="item.key"
              type="button"
              class="fchip"
              :class="{ 'fchip--active': dateKey === item.key }"
              :aria-pressed="dateKey === item.key"
              @click="selectDate(item.key)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-row__label">状态</span>
          <div class="filter-row__chips">
            <button
              v-for="item in STATUS_OPTIONS"
              :key="item.value"
              type="button"
              class="fchip"
              :class="{ 'fchip--active': status === item.value }"
              :aria-pressed="status === item.value"
              @click="selectStatus(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>
        <div class="filter-row">
          <span class="filter-row__label">驿站</span>
          <div class="filter-row__chips">
            <button type="button" class="fchip fchip--wide" @click="showStation = true">
              {{ stationLabel }}<van-icon name="arrow-down" aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>

      <p class="tool-row tabular-nums">共 {{ list.length }} 条{{ finished ? '（已全部加载）' : '' }}</p>

      <!-- 首屏单独走骨架：van-list 需要真实挂载才会触发触底加载，不能藏在骨架分支里 -->
      <template v-if="firstLoading">
        <div v-for="i in 4" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else-if="error" :error="error" @retry="loadFirst" />

      <PageState v-else-if="!list.length" :empty="true" empty-text="当前筛选条件下没有打卡记录" />

      <van-list v-else v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
        <div v-for="item in list" :key="item.id" class="list-item list-item--rich rec-item">
          <div class="list-item__title">
            <span>{{ item.employeeName || `员工 #${item.employeeId}` }}</span>
            <StatusTag :dict="ATTENDANCE_STATUS" :value="item.status" />
          </div>
          <!-- 多时段场景必须带时段名，否则同一天的「上班卡」分不清是上午还是下午 -->
          <div class="list-item__meta tabular-nums">
            {{ item.workDate }} · {{ periodLabel(item.periodName, item.checkType) }} {{ clockOf(item.checkTime) }}
          </div>
          <div class="list-item__meta">
            {{ dictLabel(CHECK_MODE, item.checkMode) }} · 距围栏
            {{ item.distance == null ? '未知' : `${item.distance} 米` }} · WiFi {{ item.wifiSsid || '未上报' }}
          </div>
          <p v-if="item.remark" class="list-item__meta list-item__meta--danger">{{ item.remark }}</p>
        </div>
      </van-list>

      <p class="tip">打卡记录按打卡时间倒序；异常卡为校验未通过的尝试，不计入出勤统计</p>
    </div>

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
.filters {
  margin-top: var(--sp-3);
}

.filter-row {
  display: flex;
  gap: var(--sp-2);
  align-items: flex-start;
}

.filter-row + .filter-row {
  margin-top: var(--sp-2);
}

.filter-row__label {
  flex: none;
  width: 32px;
  padding-top: 12px;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.filter-row__chips {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: var(--sp-2);
  min-width: 0;
}

/* 筛选 chip：主触控目标 ≥44（7.4），字号取 Caption 以在 375px 下放下 5 项 */
.fchip {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  min-height: 44px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.fchip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.fchip--wide {
  min-width: 140px;
  justify-content: space-between;
}

.sk-row {
  height: 84px;
  margin-top: var(--sp-3);
}

.rec-item {
  margin-top: var(--sp-3);
}
</style>
