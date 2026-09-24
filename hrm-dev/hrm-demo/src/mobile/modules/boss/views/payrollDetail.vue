<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import ActionBar from '@/mobile/components/ActionBar.vue'
import MyPayrollCard from '@/mobile/components/MyPayrollCard.vue'
import PageNav from '@/mobile/components/PageNav.vue'
import PageState from '@/mobile/components/PageState.vue'
import PayrollStatusSteps from '@/mobile/components/PayrollStatusSteps.vue'
import { approvePayroll, getPayroll, publishPayrolls } from '@/mobile/api/finance.js'
import { FINANCE_CODE } from '@/shared/constants/errorCode.js'

/**
 * B9 工资单详情（管理端：审核 / 驳回 / 发布）
 *
 * 步骤条按契约可解释的四个节点呈现（生成草稿 → 审核 → 发布 → 员工确认）：
 * 契约没有单独的「提交审核时间」字段，硬凑第五步会出现空时间节点。
 *
 * 员工提异议后单据会退回「待审核」并清空发布时间，所以异议信息挂在审核节点上，而不是新增一个状态。
 */
const route = useRoute()
const id = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const payroll = ref(null)
const submitting = ref(false)

const showReject = ref(false)
const rejectReason = ref('')
const rejectError = ref('')

const steps = computed(() => {
  const data = payroll.value
  if (!data) return []
  const reviewed = ['APPROVED', 'PUBLISHED', 'CONFIRMED', 'REJECTED'].includes(data.status)
  const published = ['PUBLISHED', 'CONFIRMED'].includes(data.status)
  const objected = !!data.objectionReason && data.status === 'PENDING_APPROVAL'
  const reviewState = objected
    ? 'danger'
    : data.status === 'REJECTED'
      ? 'danger'
      : reviewed
        ? 'done'
        : data.status === 'PENDING_APPROVAL'
          ? 'current'
          : 'pending'
  return [
    { key: 'DRAFT', label: '生成草稿', state: 'done', time: data.createTime, desc: `规则「${data.ruleName}」` },
    {
      key: 'REVIEW',
      label: objected ? '员工提异议' : data.status === 'REJECTED' ? '审核驳回' : '审核',
      state: reviewState,
      stateLabel: objected ? '待重新核定' : undefined,
      time: data.approveTime,
      desc: objected ? `异议：${data.objectionReason}` : data.approveRemark ? `意见：${data.approveRemark}` : ''
    },
    {
      key: 'PUBLISH',
      label: '发布给员工',
      state: published ? 'done' : data.status === 'APPROVED' ? 'current' : 'pending',
      time: data.publishTime
    },
    {
      key: 'CONFIRM',
      label: '员工确认',
      state: data.status === 'CONFIRMED' ? 'done' : data.status === 'PUBLISHED' ? 'current' : 'pending',
      time: data.confirmTime
    }
  ]
})

const actions = computed(() => {
  const status = payroll.value ? payroll.value.status : ''
  if (status === 'PENDING_APPROVAL') {
    return [
      { key: 'approve', label: '审核通过' },
      { key: 'reject', label: '驳回', type: 'danger' }
    ]
  }
  if (status === 'APPROVED') return [{ key: 'publish', label: '发布给员工' }]
  return []
})

const actionNote = computed(() => {
  const status = payroll.value ? payroll.value.status : ''
  if (status === 'DRAFT') return '草稿尚未提交审核，需在财务端提交后才能审核'
  if (status === 'REJECTED') return '已驳回，需财务端修改后重新提交审核'
  if (status === 'PUBLISHED') return '已发布，等待员工确认或提异议'
  if (status === 'CONFIRMED') return '员工已确认，本单流程结束'
  return ''
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    payroll.value = await getPayroll(id.value)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onAction(key) {
  if (submitting.value) return
  const data = payroll.value
  if (key === 'reject') {
    rejectReason.value = ''
    rejectError.value = ''
    showReject.value = true
    return
  }
  if (key === 'approve') {
    submitting.value = true
    try {
      // 与 mock 的「审核通过 → 已通过」一致：审核与发布是两步，避免一次点击越过管理员的复核动作
      payroll.value = await approvePayroll(data.id, { approved: true })
      showSuccessToast('已通过，可在详情页或列表发布给员工')
    } catch (e) {
      if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) {
        showFailToast('该单状态已变化，正在刷新')
        await load()
      } else {
        showFailToast(e.message || '审核失败')
      }
    } finally {
      submitting.value = false
    }
    return
  }
  if (key === 'publish') {
    try {
      await showConfirmDialog({
        title: '发布工资单',
        message: `将发布给 ${data.employeeName}（${data.month}），发布后员工可见并需确认，不可撤回。`,
        confirmButtonText: '确认发布',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return
    }
    submitting.value = true
    try {
      const result = await publishPayrolls({ ids: [data.id] })
      showSuccessToast(result.published ? '已发布' : '该单当前不可发布')
      await load()
    } catch (e) {
      showFailToast(e.message || '发布失败')
    } finally {
      submitting.value = false
    }
  }
}

async function submitReject() {
  const reason = rejectReason.value.trim()
  if (reason.length < 2 || reason.length > 200) {
    rejectError.value = '审核意见须为 2–200 字，会随驳回一并记录'
    return
  }
  submitting.value = true
  rejectError.value = ''
  try {
    payroll.value = await approvePayroll(payroll.value.id, { approved: false, approveRemark: reason })
    showReject.value = false
    showSuccessToast('已驳回，员工看不到该单')
  } catch (e) {
    rejectError.value =
      e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID ? '该单状态已变化，请关闭后刷新查看' : e.message || '驳回失败'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="pay-detail">
    <PageNav title="工资单详情" />
    <div class="page" :class="actions.length ? 'page--bar' : 'page--loose'">
      <PageState :loading="loading" :error="error" @retry="load">
        <div class="section-title">流转状态</div>
        <div class="card">
          <PayrollStatusSteps :steps="steps" />
        </div>

        <div class="section-title">金额明细</div>
        <MyPayrollCard :payroll="payroll" />

        <p class="tip">审核通过后仍需「发布」一步，员工才会看到该单；已发布的单据员工可确认或提异议</p>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="actionNote" :submitting="submitting" @select="onAction" />

    <van-popup v-model:show="showReject" round position="bottom" safe-area-inset-bottom>
      <div class="reject-pop">
        <div class="reject-pop__title">驳回工资单</div>
        <p class="reject-pop__sub">驳回后单据回到「已驳回」，员工不可见，需财务端修改后重新提交</p>
        <van-field
          v-model="rejectReason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          label="审核意见"
          placeholder="必填，2–200 字，例如：绩效数据与业务口径不符"
        />
        <p v-if="rejectError" class="reject-pop__error" role="alert">{{ rejectError }}</p>
        <div class="reject-pop__foot">
          <van-button block type="danger" :loading="submitting" @click="submitReject">确认驳回</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.reject-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.reject-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.reject-pop__sub {
  padding: 0 var(--sp-4);
  margin: var(--sp-1) 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.reject-pop__error {
  margin: var(--sp-2) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.reject-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
