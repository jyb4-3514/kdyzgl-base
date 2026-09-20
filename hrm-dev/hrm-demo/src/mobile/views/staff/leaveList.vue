<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import FilterChips from '../../components/FilterChips.vue'
import LeaveAudit from '../../components/LeaveAudit.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { cancelLeave, getMyLeaves } from '../../api/index.js'
import { LEAVE_FILTERS, LEAVE_LOG_ACTION, LEAVE_STATUS, dictLabel } from '@/shared/constants/dict.js'
import {
  leaveDaysText,
  leaveErrorHint,
  leaveRangeText,
  leaveStatusHint,
  leaveStatusText,
  leaveTypeText
} from '../../utils/leave.js'

/**
 * P2 我的请假（STATION_ADMIN / STAFF）
 *
 * 页面职责是「查进度 + 处理自己的单」，与申请页的分工明确：申请页写、本页读与撤销。
 * 默认不过滤状态：员工进来最常看的是「我上次那单批了没」，全量倒序比先点一次筛选更快。
 *
 * 权限一律用服务端下发的派生标志（canEdit / canCancel），前端不重复推导：
 * 状态机与身份判定放在两处就等于给了两套口径，服务端改了前端会静默不跟。
 * 移动端不新增详情路由（§3.1），详情用底部弹层承载，审批链与操作留痕都在这里看。
 */
const PAGE_SIZE = 20

const router = useRouter()

const status = ref('')
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
/** van-list 的加载位：首屏由 loadFirst 负责，初始必须为 false，否则挂载即触发一次触底加载 */
const loadingMore = ref(false)

const showDetail = ref(false)
const detail = ref(null)

const emptyText = computed(() => (status.value ? `没有${leaveStatusText(status.value)}的请假申请` : '还没有请假申请'))

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getMyLeaves({ status: status.value, pageNum: 1, pageSize: PAGE_SIZE })
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
    const page = await getMyLeaves({ status: status.value, pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    total.value = page.total
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

/** 单条操作后就地更新：整表重拉会把用户刚看过的位置闪掉，且审批常连着做几单 */
function applyUpdated(updated) {
  const index = list.value.findIndex((item) => item.id === updated.id)
  if (index < 0) return
  // 当前筛选已不含新状态（如在「待初审」里撤销），把该行移出，避免残留在筛选结果里
  if (status.value && status.value !== updated.status) {
    list.value.splice(index, 1)
    total.value = Math.max(total.value - 1, 0)
  } else {
    list.value.splice(index, 1, updated)
  }
}

function openDetail(item) {
  detail.value = item
  showDetail.value = true
}

function goApply(id, mode) {
  router.push({ path: '/staff/leave/apply', query: mode ? { id, mode } : {} })
}

/** 撤销无考勤副作用，但要二次确认：撤销后该时段可被他人重新占用，且要重新走一遍审批 */
async function onCancel(item) {
  try {
    await showConfirmDialog({
      title: '撤销请假申请',
      message: `撤销 ${leaveRangeText(item)} 的申请？撤销后不再占用该时间段，也不会产生考勤与工资影响。`,
      confirmButtonText: '确认撤销',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return // 用户取消
  }
  try {
    const updated = await cancelLeave(item.id)
    showSuccessToast('已撤销该请假申请')
    applyUpdated(updated)
    if (detail.value && detail.value.id === updated.id) detail.value = updated
  } catch (e) {
    // 9602（已被处理）等要在就地提示之外给下一步，故走 leaveErrorHint 而不是通用文案
    showFailToast(leaveErrorHint(e.code, { message: e.message }))
    loadFirst()
  }
}

onMounted(loadFirst)
</script>

<template>
  <div class="leave-list">
    <PageNav title="我的请假">
      <template #right>
        <button type="button" class="nav-action" @click="goApply()">申请请假</button>
      </template>
    </PageNav>
    <div class="page page--loose">
      <FilterChips :items="LEAVE_FILTERS" :active="status" label="按请假状态筛选" @change="selectStatus" />

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
        <template #empty-action>
          <p class="tip">点右上角「申请请假」发起，提交后可在这里看审批进度</p>
        </template>

        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <div v-for="item in list" :key="item.id" class="list-item list-item--rich lv-item">
            <div
              class="lv-item__body"
              role="button"
              tabindex="0"
              :aria-label="`查看${leaveTypeText(item.leaveType)}申请详情`"
              @click="openDetail(item)"
              @keydown.enter="openDetail(item)"
            >
              <div class="list-item__title">
                <span>{{ leaveTypeText(item.leaveType) }}</span>
                <StatusTag :dict="LEAVE_STATUS" :value="item.status" />
              </div>
              <div class="list-item__meta tabular-nums">{{ leaveRangeText(item) }}</div>
              <div class="list-item__meta tabular-nums">{{ leaveDaysText(item) }}</div>
              <div class="list-item__meta">{{ leaveStatusHint(item) }}</div>
              <p class="reason">{{ item.reason }}</p>
              <div class="list-item__meta tabular-nums">申请时间：{{ item.applyTime }}</div>
              <LeaveAudit :item="item" />
            </div>

            <!-- 行内操作：按服务端下发的派生标志渲染，不给用户点了必然失败的按钮 -->
            <div v-if="item.canCancel || item.canEdit || item.status === 'REJECTED'" class="lv-actions">
              <van-button v-if="item.canCancel" class="lv-actions__btn" plain type="danger" @click="onCancel(item)">
                撤销
              </van-button>
              <van-button
                v-if="item.canEdit"
                class="lv-actions__btn"
                plain
                type="primary"
                @click="goApply(item.id, 'edit')"
              >
                修改
              </van-button>
              <van-button
                v-if="item.status === 'REJECTED'"
                class="lv-actions__btn"
                type="primary"
                @click="goApply(item.id, 'resubmit')"
              >
                修改并重新提交
              </van-button>
            </div>
          </div>
        </van-list>
      </PageState>
    </div>

    <van-popup v-model:show="showDetail" round position="bottom" safe-area-inset-bottom>
      <div v-if="detail" class="detail-pop">
        <div class="detail-pop__title">请假详情</div>
        <div class="detail-pop__head">
          <span>{{ leaveTypeText(detail.leaveType) }}</span>
          <StatusTag :dict="LEAVE_STATUS" :value="detail.status" />
        </div>
        <div class="detail-pop__rows">
          <div class="detail-pop__row">
            <span>请假时间</span>
            <span class="tabular-nums">{{ leaveRangeText(detail) }}</span>
          </div>
          <div class="detail-pop__row">
            <span>时长</span>
            <span class="tabular-nums">{{ leaveDaysText(detail) }}</span>
          </div>
          <div class="detail-pop__row">
            <span>当前状态</span>
            <span>{{ leaveStatusHint(detail) }}</span>
          </div>
          <div class="detail-pop__row">
            <span>申请时间</span>
            <span class="tabular-nums">{{ detail.applyTime }}</span>
          </div>
        </div>
        <p class="detail-pop__reason">请假事由：{{ detail.reason }}</p>

        <div class="section-title">审批记录</div>
        <LeaveAudit :item="detail" />
        <p v-if="!detail.stationApproveTime && !detail.approveTime" class="list-item__meta">暂无审批记录</p>

        <!-- 操作留痕（D6 审计）：把「谁在什么时候改了什么」摊开，减少「我明明提过」这类争议 -->
        <div class="section-title">操作留痕</div>
        <ul class="timeline">
          <li v-for="log in detail.handleLog" :key="log.id" class="timeline__item">
            <span class="timeline__dot" aria-hidden="true" />
            <div class="timeline__time tabular-nums">{{ log.time }} · {{ log.operatorName || '系统' }}</div>
            <p class="timeline__text">
              {{ dictLabel(LEAVE_LOG_ACTION, log.action) }}{{ log.remark ? `：${log.remark}` : '' }}
            </p>
          </li>
        </ul>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.nav-action {
  min-height: 44px;
  padding: 0;
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: none;
  border: none;
}

.sk-row {
  height: 148px;
  margin-top: var(--sp-3);
}

.lv-item {
  margin-top: var(--sp-3);
}

.lv-item__body {
  outline-offset: 2px;
}

/* 事由按 §8.2 截断 2 行；全文在详情弹层看 */
.reason {
  display: -webkit-box;
  margin: var(--sp-2) 0 0;
  overflow: hidden;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-2);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 行内操作是高频点击目标，每个按钮高 44（7.4） */
.lv-actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.lv-actions__btn {
  flex: 1;
  min-height: 44px;
}

.detail-pop {
  max-height: 80vh;
  padding: var(--sp-5) var(--sp-4) var(--sp-6);
  overflow-y: auto;
}

.detail-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.detail-pop__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-4);
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
}

.detail-pop__rows {
  margin-top: var(--sp-3);
}

.detail-pop__row {
  display: flex;
  gap: var(--sp-3);
  justify-content: space-between;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.detail-pop__row + .detail-pop__row {
  margin-top: var(--sp-2);
}

.detail-pop__row > span:last-child {
  color: var(--text-1);
  text-align: right;
}

.detail-pop__reason {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-2);
}

.timeline {
  padding: 0;
  margin: 0;
  list-style: none;
}
</style>
