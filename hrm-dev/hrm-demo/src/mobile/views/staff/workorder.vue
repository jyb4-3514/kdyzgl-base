<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageState from '../../components/PageState.vue'
import SlaTag from '../../components/SlaTag.vue'
import StatusTag from '../../components/StatusTag.vue'
import WorkOrderCopyButton from '../../components/WorkOrderCopyButton.vue'
import { getWorkOrders } from '../../api/workOrder.js'
import { WORK_ORDER_PRIORITY, WORK_ORDER_TYPE } from '@/shared/constants/dict.js'
import { numberText, relativeTime } from '../../utils/format.js'

/**
 * S5 工单列表
 * 状态 Tab + 「仅看超时」筛选；新建完成后带 ?highlight=<id> 返回，列表高亮该条（1.6s 后自动取消）
 * 行高 ≥76（三行结构）、chip 44px、FAB 48px 均按 7.3/7.4 落地 —— 现场单手戴手套操作需放大热区。
 */
const TABS = [
  { label: '全部', value: '' },
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已解决', value: 2 },
  { label: '已关闭', value: 3 }
]
const PAGE_SIZE = 20
const HIGHLIGHT_DURATION = 1600

const route = useRoute()
const router = useRouter()
const activeTab = ref('')
const onlyOverdue = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const error = ref('')
const initialized = ref(false)
const highlightId = ref(Number(route.query.highlight) || 0)

let busy = false
let highlightTimer = null

/** 空态要能区分「本来就没工单」和「筛选后没有超时工单」（9.2 状态核对项） */
const emptyText = computed(() => (onlyOverdue.value ? '没有超时未处理的工单' : '暂无工单'))

async function fetchPage() {
  if (busy) return
  busy = true
  try {
    const page = await getWorkOrders({
      status: activeTab.value,
      overdueUnhandled: onlyOverdue.value ? '1' : '',
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    error.value = ''
    total.value = page.total
    list.value = pageNum.value === 1 ? page.list : list.value.concat(page.list)
    finished.value = list.value.length >= page.total
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

function reset() {
  pageNum.value = 1
  list.value = []
  total.value = 0
  finished.value = false
  error.value = ''
}

async function onLoad() {
  loading.value = true
  await fetchPage()
}

async function onRefresh() {
  reset()
  await fetchPage()
}

watch([activeTab, onlyOverdue], () => {
  reset()
  onLoad()
})

onMounted(async () => {
  await onLoad()
  // 高亮只做一次性提示，避免用户切 Tab 回来还在闪
  if (highlightId.value) {
    highlightTimer = setTimeout(() => {
      highlightId.value = 0
    }, HIGHLIGHT_DURATION)
  }
})

function goDetail(item) {
  router.push(`/staff/workorder/${item.id}`)
}

onUnmounted(() => clearTimeout(highlightTimer))
</script>

<template>
  <div class="page page--bar">
    <PageNav title="工单" />
    <van-tabs v-model:active="activeTab" class="bleed">
      <van-tab v-for="tab in TABS" :key="tab.label" :title="tab.label" :name="tab.value" />
    </van-tabs>

    <div class="tool-row">
      <span>共 {{ numberText(total) }} 条</span>
      <button
        type="button"
        class="chip chip--danger"
        :class="{ 'chip--active': onlyOverdue }"
        :aria-pressed="onlyOverdue"
        @click="onlyOverdue = !onlyOverdue"
      >
        <van-icon name="warning-o" aria-hidden="true" /> 仅看超时未处理
      </button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <PageState
        :loading="!initialized"
        :error="list.length ? '' : error"
        :empty="initialized && !list.length"
        :empty-text="emptyText"
        :rows="5"
        @retry="onRefresh"
      >
        <van-list
          v-model:loading="loading"
          :finished="finished"
          :immediate-check="false"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <div v-for="item in list" :key="item.id" class="wo-row">
            <div
              class="list-item list-item--rich"
              :class="{ 'is-highlight': item.id === highlightId }"
              role="button"
              tabindex="0"
              @click="goDetail(item)"
              @keydown.enter="goDetail(item)"
              @keydown.space.prevent="goDetail(item)"
            >
              <div class="list-item__title">
                <span>{{ item.orderNo }}</span>
                <StatusTag :dict="WORK_ORDER_TYPE" :value="item.type" />
              </div>
              <div class="wo-title">{{ item.title }}</div>
              <!-- 标签组一行承载优先级 + SLA + 相对时间，超时与高优先级都走实心（需要立刻行动） -->
              <div class="list-item__tags">
                <StatusTag :dict="WORK_ORDER_PRIORITY" :value="item.priority" />
                <SlaTag
                  :deadline="item.slaDeadline"
                  :active="item.status === 0 || item.status === 1"
                  :priority="item.priority"
                />
                <span class="list-item__meta wo-time">{{ relativeTime(item.createTime) }}</span>
              </div>
              <div class="list-item__meta">
                处理人：{{ item.assigneeName || '未指派' }} · 上报人 {{ item.reporterName }}
              </div>
            </div>
            <!-- 复制按钮放在可点击行之外：按钮嵌进 role=button 会让键盘与读屏语义打架 -->
            <WorkOrderCopyButton class="wo-row__copy" :order="item" label="复制该工单详情" />
          </div>
        </van-list>
      </PageState>
    </van-pull-refresh>

    <button type="button" class="fab" @click="router.push('/staff/workorder/create')">
      <van-icon name="add-o" aria-hidden="true" /> 新建工单
    </button>
  </div>
</template>

<style scoped>
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

.wo-time {
  margin-top: 0;
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

/* 降为二级页后不再有 Tabbar：FAB 让开安全区，页面用 --page-pad-bottom 留出与 FAB 等高的空档 */
.fab {
  /* 宽屏限宽（P2-1）：NavBar/Tabbar/ActionBar 已在 mobile.scss 按 480 / 640 收口居中，
   * FAB 只锚 right 会贴视口右缘、与居中内容列错位；按同一限宽口径内推，右边缘与内容列对齐。
   * max() 取「内推值」与「常规 16 边距」的较大者：窄屏下 (100vw-480)/2 为负，自动回落到 --sp-4 */
  position: fixed;
  right: max(var(--sp-4), calc((100vw - 480px) / 2 + var(--sp-4)));
  bottom: calc(var(--safe-bottom) + var(--sp-3));
  z-index: 10;
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  min-height: 48px;
  padding: 0 var(--sp-4);
  font-size: var(--fs-body);
  font-weight: var(--fw-semibold);
  color: var(--text-on-dark);
  background: var(--color-primary);
  border: none;
  border-radius: var(--r-full);
  box-shadow: var(--e2);
}

.fab:active {
  background: var(--color-primary-hover);
}

/* 横屏兜底：与 mobile.scss 末尾的 640 档同口径，否则横屏下 FAB 又会贴到视口右缘 */
@media (orientation: landscape) {
  .fab {
    right: max(var(--sp-4), calc((100vw - 640px) / 2 + var(--sp-4)));
  }
}
</style>
