<script setup>
import { computed, onMounted, ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import FilterChips from './FilterChips.vue'
import LeaveAudit from './LeaveAudit.vue'
import PageNav from './PageNav.vue'
import PageState from './PageState.vue'
import StatusTag from './StatusTag.vue'
import { finalApproveLeave, getLeaveList, revokeLeave, stationApproveLeave } from '../api/leave.js'
import { LEAVE_FILTERS, LEAVE_STATUS } from '@/shared/constants/dict.js'
import { LEAVE_CODE } from '@/shared/constants/errorCode.js'
import { useAuthStore } from '../stores/auth.js'
import { leaveDaysText, leaveErrorHint, leaveRangeText, leaveStatusText, leaveTypeText } from '../utils/leave.js'

/**
 * P3/P4 请假审批列表（站长初审 / 管理员终审共用）
 *
 * 为什么做成一个组件：两页除了默认筛选、端点与标题，其余（状态 chip、无限滚动、计数条、
 * 参数化审批弹层、9602 就地对齐、已审批只读回显）逐行相同。两处各写一份，改一处必漏一处。
 * 端点差异由 `actor` 决定，动作可见性由服务端派生标志（canRevoke）与当前筛选状态决定。
 *
 * 为什么站长初审页落在 /staff/leave/review 而不在 /boss/*：设计规范 §3.3 已论证
 * （/boss/* 全部 roles:[ADMIN]；站长移动端定位在员工端；保持「站长 vs 员工差异最小化」）。
 */
const props = defineProps({
  title: { type: String, required: true },
  /** 本页默认处理的状态：同时决定「哪一行渲染通过/驳回」（只能处理本页职责内的状态） */
  defaultStatus: { type: String, required: true },
  /** STATION = 站长初审；FINAL = 管理员终审（并负责对已通过单的撤回） */
  actor: { type: String, required: true }
})

const PAGE_SIZE = 20

const auth = useAuthStore()

const status = ref(props.defaultStatus)
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)

/** 通过 / 驳回 / 撤回走同一个弹层，只有默认结论、字段约束与按钮文案不同（设计规范 §4.7） */
const showPopup = ref(false)
const mode = ref('approve')
const target = ref(null)
const remark = ref('')
const popupError = ref('')
const submitting = ref(false)

const isRevoke = computed(() => mode.value === 'revoke')
const popupTitle = computed(() =>
  mode.value === 'revoke' ? '撤回已批准的请假' : mode.value === 'approve' ? '通过请假申请' : '驳回请假申请'
)
const popupPlaceholder = computed(() =>
  mode.value === 'approve' ? '选填，例如：情况属实，准假' : '必填，请说明原因（2-100 字）'
)
const popupDisabled = computed(() => (mode.value === 'approve' ? false : remark.value.trim().length < 2))
const emptyText = computed(() =>
  status.value === props.defaultStatus
    ? props.defaultStatus === 'PENDING_STATION'
      ? '没有待初审的请假申请'
      : '没有待终审的请假申请'
    : '当前筛选条件下没有请假申请'
)

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getLeaveList({ status: status.value, pageNum: 1, pageSize: PAGE_SIZE })
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
    const page = await getLeaveList({ status: status.value, pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    total.value = page.total
    finished.value = list.value.length >= page.total
  } catch (e) {
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

/** 静默对齐：不重置列表也不切骨架，仅用于「这单已被别人处理」后刷新可见数据 */
async function syncList() {
  try {
    const page = await getLeaveList({
      status: status.value,
      pageNum: 1,
      pageSize: Math.max(list.value.length, PAGE_SIZE)
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    /* 对齐失败保留当前列表：弹层里已提示手动刷新，不叠加第二个错误态 */
  }
}

/**
 * 可动性：状态必须是本页职责内的待审态，且不能审自己。
 * 站长的单按 D1 直进 PENDING_BOSS，正常不会出现在初审列表；这里兜住构造请求与流程变更（§2.1 纵深防御）。
 */
const canAct = (item) => item.status === props.defaultStatus && item.employeeId !== auth.user.id

function openPopup(item, nextMode) {
  target.value = item
  mode.value = nextMode
  remark.value = ''
  popupError.value = ''
  showPopup.value = true
}

/** 单条操作后就地更新，避免整表重拉闪掉审批人刚看过的位置 */
function applyUpdated(updated) {
  const index = list.value.findIndex((item) => item.id === updated.id)
  if (index < 0) return
  if (status.value && status.value !== updated.status) {
    list.value.splice(index, 1)
    total.value = Math.max(total.value - 1, 0)
  } else {
    list.value.splice(index, 1, updated)
  }
}

async function submit() {
  if (submitting.value || popupDisabled.value) return
  submitting.value = true
  popupError.value = ''
  const id = target.value.id
  try {
    const updated = isRevoke.value
      ? await revokeLeave(id, { reason: remark.value.trim() })
      : await (props.actor === 'STATION' ? stationApproveLeave : finalApproveLeave)(id, {
          approved: mode.value === 'approve',
          remark: remark.value.trim() || null
        })
    showPopup.value = false
    showSuccessToast(
      isRevoke.value
        ? '已撤回，考勤与算薪口径已回滚'
        : mode.value === 'approve'
          ? '已通过该请假申请'
          : '已驳回该请假申请'
    )
    applyUpdated(updated)
  } catch (e) {
    // 9602：两人同时审 / 申请人先撤销 —— 本地列表已过期，说完原因再就地对齐（R1 / R2）
    // 9606：撤回被账期工资单硬阻断，服务端 message 已含月份与下一步，原样呈现
    popupError.value = leaveErrorHint(e.code, { message: e.message })
    if (e.code === LEAVE_CODE.STATUS_INVALID) await syncList()
  } finally {
    submitting.value = false
  }
}

onMounted(loadFirst)
</script>

<template>
  <div class="leave-approval">
    <PageNav :title="title" />
    <div class="page page--loose">
      <FilterChips :items="LEAVE_FILTERS" :active="status" label="按请假状态筛选" @change="selectStatus" />

      <p class="tool-row tabular-nums">共 {{ total }} 条</p>

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" :empty-text="emptyText" @retry="loadFirst">
        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <div v-for="item in list" :key="item.id" class="list-item list-item--rich lv-item">
            <div class="list-item__title">
              <span>{{ item.employeeName }}</span>
              <StatusTag :dict="LEAVE_STATUS" :value="item.status" />
            </div>
            <div class="list-item__meta">{{ item.stationName }} · {{ leaveTypeText(item.leaveType) }}</div>
            <div class="list-item__meta tabular-nums">{{ leaveRangeText(item) }}</div>
            <div class="list-item__meta tabular-nums">{{ leaveDaysText(item) }}</div>
            <p class="reason">事由：{{ item.reason }}</p>
            <div class="list-item__meta tabular-nums">申请时间：{{ item.applyTime }}</div>
            <LeaveAudit :item="item" />

            <div v-if="canAct(item) || item.canRevoke" class="lv-actions">
              <van-button
                v-if="item.canRevoke"
                class="lv-actions__btn"
                plain
                type="danger"
                @click="openPopup(item, 'revoke')"
              >
                撤回
              </van-button>
              <template v-if="canAct(item)">
                <van-button class="lv-actions__btn" plain type="danger" @click="openPopup(item, 'reject')">
                  驳回
                </van-button>
                <van-button class="lv-actions__btn" type="primary" @click="openPopup(item, 'approve')">通过</van-button>
              </template>
            </div>
          </div>
        </van-list>
      </PageState>

      <p class="tip">审批人只能处理本站员工、且不能审批本人的申请；审批意见与时间对申请人可见，请写清判断依据</p>
      <p v-if="actor === 'FINAL'" class="tip">
        撤回仅对「已通过」的单开放：该账期工资单已生成时撤回会被拒绝，需先在 PC 端财务管理中作废工资单
      </p>
    </div>

    <van-popup v-model:show="showPopup" round position="bottom" safe-area-inset-bottom>
      <div v-if="target" class="approve-pop">
        <div class="approve-pop__title">{{ popupTitle }}</div>
        <p class="approve-pop__sub tabular-nums">{{ target.employeeName }} · {{ target.stationName }}</p>
        <p class="approve-pop__sub tabular-nums">
          {{ leaveTypeText(target.leaveType) }} {{ leaveRangeText(target) }} · {{ leaveDaysText(target) }}
        </p>
        <!-- 当前状态快照：两人同时审时，审批人能察觉这单已被处理（R2） -->
        <p class="approve-pop__sub tabular-nums">当前状态：{{ leaveStatusText(target.status) }}</p>
        <div class="approve-pop__form">
          <van-field
            v-model="remark"
            type="textarea"
            rows="3"
            maxlength="100"
            show-word-limit
            :required="!isRevoke && mode === 'reject'"
            :label="isRevoke ? '撤回原因' : '审批意见'"
            :placeholder="popupPlaceholder"
          />
          <p v-if="popupError" class="approve-pop__error" role="alert">{{ popupError }}</p>
        </div>
        <div class="approve-pop__foot">
          <van-button
            block
            :type="mode === 'approve' ? 'primary' : 'danger'"
            :loading="submitting"
            :disabled="popupDisabled"
            @click="submit"
          >
            {{ isRevoke ? '确认撤回' : mode === 'approve' ? '确认通过' : '确认驳回' }}
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.sk-row {
  height: 156px;
  margin-top: var(--sp-3);
}

.lv-item {
  margin-top: var(--sp-3);
}

/* 事由按 §8.2 截断 2 行；完整事由在 PC 端与详情里看 */
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

/* 审批动作是行内高频操作，每个按钮高 44（7.4） */
.lv-actions {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.lv-actions__btn {
  flex: 1;
  min-height: 44px;
}

.approve-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.approve-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.approve-pop__sub {
  margin: var(--sp-1) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-align: center;
}

.approve-pop__form {
  padding: var(--sp-3) var(--sp-4) 0;
}

.approve-pop__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.approve-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
