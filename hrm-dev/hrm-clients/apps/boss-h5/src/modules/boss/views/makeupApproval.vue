<script setup>
import { onMounted, ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { approveMakeup, getMakeupList } from '@/api/attendance.js'
import { ATTENDANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { MAKEUP_FILTERS, MAKEUP_STATUS } from '@/constants/makeup.js'
import { periodLabel } from '@/utils/attendance.js'

/**
 * B12 补卡审批（ADMIN · 全驿站）
 *
 * 默认落在「待审批」而不是全部：管理员进这一页的动作是「把待办清掉」，历史记录是查询而不是默认视图。
 * 通过即由服务端补录打卡记录（attendance_record.source = MAKEUP），所以界面上明确写出这一点，
 * 避免管理员以为「只是改个状态、出勤还是缺卡」。
 */
const PAGE_SIZE = 20

const status = ref('PENDING')
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)

/** 审批弹层：一次只处理一单，故用单个对象而不是数组 */
const showApprove = ref(false)
const target = ref(null)
const approved = ref(true)
const approveRemark = ref('')
const approveError = ref('')
const approving = ref(false)

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getMakeupList({ status: status.value, pageNum: 1, pageSize: PAGE_SIZE })
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
    const page = await getMakeupList({ status: status.value, pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
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
    const page = await getMakeupList({
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

/** 打开审批弹层：通过 / 驳回走同一个弹层，只有默认结论与按钮文案不同 */
function openApprove(item, pass) {
  target.value = item
  approved.value = pass
  approveRemark.value = ''
  approveError.value = ''
  showApprove.value = true
}

async function submitApprove() {
  if (approving.value) return
  approving.value = true
  approveError.value = ''
  try {
    const updated = await approveMakeup(target.value.id, {
      approved: approved.value,
      approveRemark: approveRemark.value.trim() || null
    })
    showApprove.value = false
    showSuccessToast(approved.value ? '已通过，系统已补录打卡记录' : '已驳回该补卡申请')
    // 就地更新列表：审批常连着做几单，整表重拉会把管理员刚看过的位置闪掉；
    // 若当前筛选已不含新状态（在「待审批」里批完一单），直接把该行移出，避免残留在筛选结果里
    const index = list.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      if (status.value && status.value !== updated.status) {
        list.value.splice(index, 1)
        total.value = Math.max(total.value - 1, 0)
      } else {
        list.value.splice(index, 1, updated)
      }
    }
  } catch (e) {
    if (e.code === ATTENDANCE_CODE.MAKEUP_STATUS_INVALID) {
      // 双人同时审批的常见情形：本地列表已过期，说清原因并就地对齐
      approveError.value = '该申请已被处理，列表已刷新，请关闭弹层查看最新状态'
      await syncList()
    } else {
      approveError.value = e.message || '审批失败，请稍后重试'
    }
  } finally {
    approving.value = false
  }
}

onMounted(loadFirst)
</script>

<template>
  <div class="makeup-approval">
    <PageNav title="补卡审批" />
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

      <p class="tool-row tabular-nums">共 {{ total }} 条</p>

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState
        v-else
        :error="error"
        :empty="!list.length"
        :empty-text="status === 'PENDING' ? '没有审批中的补卡申请' : '当前筛选条件下没有补卡申请'"
        @retry="loadFirst"
      >
        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <div v-for="item in list" :key="item.id" class="list-item list-item--rich mk-item">
            <div class="list-item__title">
              <span>{{ item.employeeName }}</span>
              <StatusTag :dict="MAKEUP_STATUS" :value="item.status" />
            </div>
            <div class="list-item__meta tabular-nums">
              {{ item.stationName }} · 补卡 {{ item.workDate }} · {{ periodLabel(item.periodName, item.checkType) }}
            </div>
            <div class="list-item__meta tabular-nums">申请时间：{{ item.applyTime }}</div>
            <div class="reason">理由：{{ item.reason }}</div>

            <!-- 已审批的单子只读回显，把「谁批的、什么时候、什么意见」一次讲清 -->
            <div v-if="item.status !== 'PENDING'" class="audit tabular-nums">
              <div class="list-item__meta">
                审批：{{ item.approverName || '管理员' }} · {{ item.approveTime || '-' }}
              </div>
              <div class="list-item__meta">审批意见：{{ item.approveRemark || '未填写' }}</div>
            </div>

            <div v-else class="mk-actions">
              <van-button class="mk-actions__btn" plain type="danger" @click="openApprove(item, false)"
                >驳回</van-button
              >
              <van-button class="mk-actions__btn" type="primary" @click="openApprove(item, true)">通过</van-button>
            </div>
          </div>
        </van-list>
      </PageState>

      <p class="tip">审批通过后由系统按该时段规定时间补录打卡记录，员工端「我的补卡申请」同步可见审批意见</p>
    </div>

    <van-popup v-model:show="showApprove" round position="bottom" safe-area-inset-bottom>
      <div v-if="target" class="approve-pop">
        <div class="approve-pop__title">{{ approved ? '通过补卡申请' : '驳回补卡申请' }}</div>
        <p class="approve-pop__sub tabular-nums">
          {{ target.employeeName }} · {{ target.stationName }} · {{ target.workDate }}
          {{ periodLabel(target.periodName, target.checkType) }}
        </p>
        <div class="approve-pop__form">
          <van-field
            v-model="approveRemark"
            type="textarea"
            rows="3"
            maxlength="200"
            show-word-limit
            label="审批意见"
            placeholder="选填，例如：情况属实，予以补卡"
          />
          <p v-if="approveError" class="approve-pop__error" role="alert">{{ approveError }}</p>
        </div>
        <div class="approve-pop__foot">
          <van-button block :type="approved ? 'primary' : 'danger'" :loading="approving" @click="submitApprove">
            {{ approved ? '确认通过' : '确认驳回' }}
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
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
  height: 132px;
  margin-top: var(--sp-3);
}

.mk-item {
  margin-top: var(--sp-3);
}

.reason {
  margin-top: var(--sp-2);
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-2);
}

.audit {
  padding-top: var(--sp-2);
  margin-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}

/* 审批动作是行内高频操作，两个按钮各占一半、高 44（7.4） */
.mk-actions {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.mk-actions__btn {
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
