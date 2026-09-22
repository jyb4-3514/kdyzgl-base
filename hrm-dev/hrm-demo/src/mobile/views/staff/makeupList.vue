<script setup>
import { computed, onMounted, ref } from 'vue'
import { showFailToast } from 'vant'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { getMyMakeups } from '../../api/attendance.js'
import { dictLabel } from '@/shared/constants/dict.js'
import { MAKEUP_FILTERS, MAKEUP_STATUS } from '../../constants/makeup.js'
import { periodLabel } from '../../utils/attendance.js'

/**
 * 我的补卡申请（员工端）
 * 与打卡页的分工：打卡页负责「发起申请」，本页负责「查进度」——审批意见与审批时间只有这里看得到，
 * 审批中给出「等老板审批」的明确预期，避免员工因为看不到进度而反复提交（服务端 9108 会拦住重复申请）。
 * 默认不过滤状态：员工进这一页最常看的是「我上次申请过了吗、结果怎样」，全量倒序比先点一次筛选更快。
 */
const PAGE_SIZE = 20

const loading = ref(true)
const error = ref('')
const list = ref([])
const pageNum = ref(0)
const finished = ref(false)
/** van-list 的加载位：首屏由 loadFirst 负责，初始必须为 false，否则挂载即触发一次触底加载 */
const loadingMore = ref(false)
const status = ref('')

const emptyText = computed(() =>
  status.value ? `没有${dictLabel(MAKEUP_STATUS, status.value)}的补卡申请` : '还没有补卡申请'
)

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getMyMakeups({ status: status.value, pageNum: 1, pageSize: PAGE_SIZE })
    pageNum.value = 1
    list.value = page.list
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
    const page = await getMyMakeups({ status: status.value, pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    // 已有数据时不整页报错：翻页失败只提示，用户继续滚动即可重试
    showFailToast('加载更多失败，请稍后重试')
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

function selectStatus(value) {
  if (status.value === value) return
  status.value = value
  loadFirst()
}

onMounted(loadFirst)
</script>

<template>
  <div class="makeup-list">
    <PageNav title="我的补卡申请" />
    <div class="page page--loose">
      <div class="filter-row" role="group" aria-label="按审批状态筛选">
        <button
          v-for="item in MAKEUP_FILTERS"
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

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
        <template #empty-action>
          <p class="tip">已过期的打卡时段可在「打卡」页对应时段上点「申请补卡」</p>
        </template>

        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <div v-for="item in list" :key="item.id" class="list-item list-item--rich mk-item">
            <div class="list-item__title">
              <span class="tabular-nums">{{ item.workDate }} · {{ periodLabel(item.periodName, item.checkType) }}</span>
              <StatusTag :dict="MAKEUP_STATUS" :value="item.status" />
            </div>
            <div class="list-item__meta">补卡理由：{{ item.reason }}</div>
            <div class="list-item__meta tabular-nums">申请时间：{{ item.applyTime }}</div>
            <div v-if="item.status === 'APPROVED' || item.status === 'REJECTED'" class="audit">
              <div class="list-item__meta tabular-nums">
                审批：{{ item.approverName || '管理员' }} · {{ item.approveTime || '-' }}
              </div>
              <div class="list-item__meta">审批意见：{{ item.approveRemark || '未填写' }}</div>
            </div>
            <p v-else class="list-item__meta">等待老板审批，通过后系统会自动补录该时段打卡记录</p>
          </div>
        </van-list>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

/* 筛选 chip 是主触控目标，高度 44 达标（7.4） */
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

.sk-row {
  height: 108px;
  margin-top: var(--sp-3);
}

.mk-item {
  margin-top: var(--sp-3);
}

.audit {
  padding-top: var(--sp-2);
  margin-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}
</style>
