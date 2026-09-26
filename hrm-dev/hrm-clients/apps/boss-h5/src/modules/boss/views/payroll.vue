<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import MonthPicker from '@kdyzgl/shared/ui/MonthPicker.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StationPicker from '@kdyzgl/shared/ui/StationPicker.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { bossConfirm } from '../components/bossConfirm.js'
import { PAYROLL_FILTERS, PAYROLL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import { getPayrolls, publishPayrolls } from '@/api/finance.js'
import { getStationList } from '@/api/org.js'
import { moneyText, recentMonths } from '@/utils/format.js'

/**
 * B9 管理端 · 工资单审核
 *
 * 默认落在「待审核」：管理员进这一页的动作就是把待办清掉，历史月份是查询而不是默认视图。
 * 批量发布是本页唯一的不可逆动作，按 B0.3 做二次确认：标题动词短语、正文写清人数与不可撤回、按钮用具体动词。
 *
 * 契约提示：一张工资单对应一名员工一个月（不是「一个站点一张总单」），
 * 所以「一键发布」走 month(+stationId) 口径，一次把当月已通过的单子全部发出。
 */
const PAGE_SIZE = 20

const router = useRouter()
const month = ref(recentMonths()[0])
const status = ref('PENDING_APPROVAL')
const stationId = ref(null)
const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const showStation = ref(false)

const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const counts = ref({})
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)
const publishing = ref(false)

const stationText = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '全部驿站'
})
const approvedCount = computed(() => counts.value.APPROVED || 0)
const emptyText = computed(() => {
  if (status.value === 'PENDING_APPROVAL') return `${month.value} 没有待审核的工资单`
  const hit = PAYROLL_FILTERS.find((item) => item.value === status.value)
  return `当前条件下没有「${hit ? hit.label : ''}」的工资单`
})

function query(page) {
  return getPayrolls({
    month: month.value,
    stationId: stationId.value,
    status: status.value,
    pageNum: page,
    pageSize: PAGE_SIZE
  })
}

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    stations.value = await getStationList()
  } catch (e) {
    // 筛选项失败不阻断列表，但弹层里要能区分「取不到」与「确实没有」
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await query(1)
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
    counts.value = page.counts || {}
    finished.value = list.value.length >= page.total
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onLoadMore() {
  if (!pageNum.value) {
    loadingMore.value = false
    return
  }
  const next = pageNum.value + 1
  try {
    const page = await query(next)
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

function onMonthChange(value) {
  month.value = value
  loadFirst()
}

function onPickStation(id) {
  stationId.value = id
  loadFirst()
}

function selectStatus(value) {
  if (status.value === value) return
  status.value = value
  loadFirst()
}

async function onPublishAll() {
  if (!approvedCount.value) {
    showConfirmDialog({
      title: '暂无可发布工资单',
      message: '仅「已通过」状态的工资单可以发布。',
      showCancelButton: false,
      confirmButtonText: '知道了'
    }).catch(() => {})
    return
  }
  // 发布不可撤回且影响全站可见性，走 BossConfirm 四要素（对象 + 影响面 + 不可逆声明）
  const ok = await bossConfirm({
    action: '发布工资单',
    target: `${month.value} · ${stationText.value}`,
    impact: `${approvedCount.value} 名员工将可见并需确认`,
    irreversible: true,
    confirmText: '确认发布'
  })
  if (!ok) return
  publishing.value = true
  try {
    const result = await publishPayrolls({ month: month.value, stationId: stationId.value })
    if (result.published) showSuccessToast(`已发布 ${result.published} 张工资单`)
    else showSuccessToast('没有可发布的工资单（仅「已通过」状态可发布）')
    await loadFirst()
  } catch (e) {
    error.value = e.message || '发布失败'
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  loadStations()
  loadFirst()
})
</script>

<template>
  <div class="boss-payroll">
    <PageNav title="工资单审核" />
    <div class="page page--loose">
      <MonthPicker :model-value="month" label="工资月份" :disabled="loading" @update:model-value="onMonthChange" />

      <div class="tool-row">
        <button type="button" class="chip station-chip" @click="showStation = true">
          <van-icon name="location-o" aria-hidden="true" />
          {{ stationText }}
        </button>
        <span class="tabular-nums">共 {{ total }} 张</span>
      </div>

      <div class="filter-row" role="group" aria-label="按工资单状态筛选">
        <button
          v-for="item in PAYROLL_FILTERS"
          :key="item.value"
          type="button"
          class="fchip"
          :class="{ 'fchip--active': status === item.value }"
          :aria-pressed="status === item.value"
          @click="selectStatus(item.value)"
        >
          {{ item.label
          }}<span v-if="counts[item.value]" class="fchip__count tabular-nums">{{ counts[item.value] }}</span>
        </button>
      </div>

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
        <template #empty-action>
          <p class="tip">工资单由财务端生成草稿后进入审核流；手机端负责审核与发布</p>
        </template>

        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <button
            v-for="item in list"
            :key="item.id"
            type="button"
            class="list-item list-item--rich pay-row"
            @click="router.push(`/boss/payroll/${item.id}`)"
          >
            <div class="list-item__title">
              <span>{{ item.employeeName }}</span>
              <StatusTag :dict="PAYROLL_STATUS" :value="item.status" />
            </div>
            <div class="list-item__meta tabular-nums">
              {{ item.month }} · {{ item.stationName || '总部' }} · {{ item.payrollNo }}
            </div>
            <div class="list-item__meta tabular-nums">
              实发 {{ moneyText(item.netAmount) }}（应发 {{ moneyText(item.grossAmount) }} · 扣款
              {{ moneyText(item.deductionTotal) }}）
            </div>
            <div class="list-item__meta tabular-nums">生成于 {{ item.createTime }}</div>
            <van-icon class="pay-row__arrow" name="arrow" aria-hidden="true" />
          </button>
        </van-list>

        <van-button
          class="publish-all"
          block
          type="primary"
          :loading="publishing"
          :disabled="!approvedCount"
          :title="approvedCount ? '' : '仅「已通过」状态的工资单可发布'"
          @click="onPublishAll"
        >
          一键发布本月已通过（{{ approvedCount }}）
        </van-button>
        <p class="tip">发布是不可撤回动作：发布后员工立即可见并需确认；驳回需在详情页填写原因</p>
      </PageState>
    </div>

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="stationId"
      :loading="stationsLoading"
      :error="stationsError"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="loadStations"
      @select="onPickStation"
    />
  </div>
</template>

<style scoped>
.station-chip {
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

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

.fchip__count {
  font-size: var(--fs-micro);
  color: var(--text-3);
}

.fchip--active .fchip__count {
  color: var(--color-primary);
}

.sk-row {
  height: 128px;
  margin-top: var(--sp-3);
}

.pay-row {
  position: relative;
  display: block;
  width: 100%;
  margin-top: var(--sp-3);
  text-align: left;
  border: none;
}

.pay-row__arrow {
  position: absolute;
  top: var(--sp-4);
  right: var(--sp-4);
  color: var(--text-3);
}

.publish-all {
  min-height: 44px;
  margin-top: var(--sp-5);
}
</style>
