<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { FLOW_STATUS } from '@kdyzgl/shared/constants/dict.js'
import { getOffboardingFlows, getOnboardingFlows } from '@/api/hr.js'

/**
 * B10 管理端 · 入离职审批
 *
 * 两个 Tab 而不是一个混合列表：入职与离职的字段、当前步骤名完全不同，
 * 混在一起列表要按类型分叉渲染（表格列的经典难题），移动端不如直接分开展示。
 * 默认「进行中」：管理员进这页是清待办，已办结的是查询。
 *
 * 契约口径：入离职流程接口仅 ADMIN 开放 —— 移动端即管理端「审批」，
 * 员工端（apps/staff-h5）为无权限降级，只展示本人可解释的信息。
 */
const PAGE_SIZE = 20
const FLOW_TABS = [
  { name: 'onboarding', label: '入职', path: '/boss/flow/onboarding' },
  { name: 'offboarding', label: '离职', path: '/boss/flow/offboarding' }
]
const STATUS_FILTERS = [
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' },
  { value: '', label: '全部' }
]

const router = useRouter()
const tab = ref('onboarding')
const status = ref('IN_PROGRESS')
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)

const emptyText = computed(() => (status.value === 'IN_PROGRESS' ? '没有待审批的入离职流程' : '当前条件下没有流程记录'))

function fetch(page) {
  const params = { status: status.value || undefined, pageNum: page, pageSize: PAGE_SIZE }
  return tab.value === 'onboarding' ? getOnboardingFlows(params) : getOffboardingFlows(params)
}

function detailPath(row) {
  const type = tab.value === 'onboarding' ? 'onboarding' : 'offboarding'
  return `/boss/flow/${type}/${row.id}`
}

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await fetch(1)
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
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
    const page = await fetch(next)
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

function selectTab(name) {
  tab.value = name
  loadFirst()
}

function selectStatus(value) {
  if (status.value === value) return
  status.value = value
  loadFirst()
}

onMounted(loadFirst)
</script>

<template>
  <div class="boss-flow">
    <PageNav title="入离职审批" />
    <div class="page page--loose">
      <van-tabs :active="tab" class="flow-tabs" @change="selectTab">
        <van-tab v-for="item in FLOW_TABS" :key="item.name" :name="item.name" :title="item.label" />
      </van-tabs>

      <div class="filter-row" role="group" aria-label="按流程状态筛选">
        <button
          v-for="item in STATUS_FILTERS"
          :key="item.value || 'all'"
          type="button"
          class="fchip"
          :class="{ 'fchip--active': status === item.value }"
          :aria-pressed="status === item.value"
          @click="selectStatus(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <p class="tool-row tabular-nums">共 {{ total }} 条</p>

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <button
            v-for="item in list"
            :key="item.id"
            type="button"
            class="list-item list-item--rich flow-row"
            @click="router.push(detailPath(item))"
          >
            <div class="list-item__title">
              <span>{{ item.employeeName }}</span>
              <StatusTag :dict="FLOW_STATUS" :value="item.status" />
            </div>
            <div class="list-item__meta tabular-nums">
              {{ item.flowNo }} · {{ item.stationName || '总部' }}
              <template v-if="item.typeLabel"> · {{ item.typeLabel }}</template>
            </div>
            <div class="list-item__meta tabular-nums">
              当前：{{ item.currentStepName || (item.status === 'COMPLETED' ? '全部步骤已办结' : '无待办步骤') }} · 进度
              {{ item.progress.done }}/{{ item.progress.total }}
            </div>
            <div class="list-item__meta tabular-nums">发起于 {{ item.createTime }}</div>
            <div v-if="item.status === 'REJECTED'" class="list-item__meta list-item__meta--danger">
              驳回原因：{{ item.rejectReason || '-' }}
            </div>
          </button>
        </van-list>
      </PageState>

      <p class="tip">点开流程可办理当前步骤或驳回；驳回不会撤销已产生的数据（如离职结算单），需人工核对</p>
    </div>
  </div>
</template>

<style scoped>
.flow-tabs {
  margin-top: var(--sp-2);

  --van-tabs-line-height: 44px;
}

.fchip {
  display: inline-flex;
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

.sk-row {
  height: 128px;
  margin-top: var(--sp-3);
}

.flow-row {
  display: block;
  width: 100%;
  margin-top: var(--sp-3);
  text-align: left;
  border: none;
}
</style>
