<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import MyPayrollCard from '../../components/MyPayrollCard.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import PayrollStatusSteps from '../../components/PayrollStatusSteps.vue'
import { confirmPayroll, getPayroll, objectPayroll } from '../../api/finance.js'
import { moneyText } from '../../utils/format.js'
import { FINANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'

/**
 * B9 员工端 · 我的工资单详情（确认 / 提异议）
 *
 * 两个动作都按 B0.3 二次确认，但成本不同：
 * - 确认无误：确认后不可撤销，故正文写清「确认后不可撤销，如有疑问请先提异议」
 * - 提异议：必填原因，提交后单据退回管理员重新核定，本人在重新发布前看不到该单 → 成功后回列表
 *
 * 9403（工资单尚未发布）不是系统故障：给「尚未发布」的说明而不是错误态 + 重试。
 */
const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const blocked = ref('')
const payroll = ref(null)
const submitting = ref(false)

const showObject = ref(false)
const objectReason = ref('')
const objectError = ref('')

const steps = computed(() => {
  const data = payroll.value
  if (!data) return []
  return [
    { key: 'DRAFT', label: '工资单生成', state: 'done', time: data.createTime },
    { key: 'REVIEW', label: '管理员审核', state: 'done', time: data.approveTime },
    { key: 'PUBLISH', label: '发布给我', state: 'done', time: data.publishTime },
    {
      key: 'CONFIRM',
      label: '我的确认',
      state: data.status === 'CONFIRMED' ? 'done' : 'current',
      time: data.confirmTime
    }
  ]
})

const actions = computed(() => {
  const status = payroll.value ? payroll.value.status : ''
  if (status === 'PUBLISHED') {
    return [
      { key: 'confirm', label: '确认无误' },
      { key: 'object', label: '提异议', type: 'danger' }
    ]
  }
  return []
})

const actionNote = computed(() =>
  payroll.value && payroll.value.status === 'CONFIRMED' ? '本单已确认；如仍有疑问请联系人事' : ''
)

async function load() {
  loading.value = true
  error.value = ''
  blocked.value = ''
  try {
    payroll.value = await getPayroll(id.value)
  } catch (e) {
    if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) blocked.value = e.message || '工资单尚未发布，暂不可查看'
    else error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onAction(key) {
  if (submitting.value) return
  if (key === 'confirm') {
    try {
      await showConfirmDialog({
        title: '确认工资单',
        message: `确认 ${payroll.value.month} 工资单（实发 ${payroll.value.netAmount} 元）无误？确认后不可撤销，如有疑问请先提异议。`,
        confirmButtonText: '确认无误',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return
    }
    submitting.value = true
    try {
      payroll.value = await confirmPayroll(payroll.value.id)
      showSuccessToast('已确认')
    } catch (e) {
      showFailToast(e.message || '确认失败，请稍后重试')
    } finally {
      submitting.value = false
    }
    return
  }
  if (key === 'object') {
    objectReason.value = ''
    objectError.value = ''
    showObject.value = true
  }
}

async function submitObject() {
  const reason = objectReason.value.trim()
  if (reason.length < 2 || reason.length > 200) {
    objectError.value = '异议原因须为 2–200 字，会同步给管理员'
    return
  }
  submitting.value = true
  objectError.value = ''
  try {
    await objectPayroll(payroll.value.id, { reason })
    showObject.value = false
    showSuccessToast('已提交异议，等待管理员重新核定')
    // 异议后单据状态回到待审核，员工端不可见 → 直接回列表，避免停在无法访问的详情页
    router.replace('/staff/payroll')
  } catch (e) {
    objectError.value = e.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="pay-detail">
    <PageNav title="我的工资单" />
    <div class="page" :class="actions.length ? 'page--bar' : 'page--loose'">
      <PageState
        :loading="loading"
        :error="error"
        :empty="!payroll"
        :empty-text="blocked || '工资单不存在'"
        @retry="load"
      >
        <template #empty-action>
          <p class="tip">工资单由管理员发布后才可见；重新核定期间会暂时不可查看</p>
        </template>

        <section class="hero hero--brand pay-hero">
          <div class="flex-between">
            <span class="hero__title tabular-nums">{{ payroll.month }} 工资单</span>
            <span class="hero__chip">{{ payroll.statusLabel }}</span>
          </div>
          <p class="hero__sub">
            {{ payroll.employeeName }} · {{ payroll.stationName || '总部' }} · {{ payroll.payrollNo }}
          </p>
          <p class="pay-hero__amount tabular-nums">{{ moneyText(payroll.netAmount) }}</p>
          <p class="hero__sub">
            实发合计（应发 {{ moneyText(payroll.grossAmount) }} · 扣款 {{ moneyText(payroll.deductionTotal) }}）
          </p>
        </section>

        <div class="section-title">流转状态</div>
        <div class="card">
          <PayrollStatusSteps :steps="steps" />
        </div>

        <div class="section-title">构成明细<span class="section-title__extra">点「展开剩余」看全部</span></div>
        <MyPayrollCard :payroll="payroll" :max-visible="5" />

        <div class="section-title">计算说明</div>
        <van-cell-group inset>
          <van-cell title="计薪规则" :value="payroll.ruleName || '-'" />
          <van-cell title="生成时间" :value="payroll.createTime" />
          <van-cell title="发布时间" :value="payroll.publishTime || '-'" />
          <van-cell title="确认时间" :value="payroll.confirmTime || '待确认'" />
        </van-cell-group>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="actionNote" :submitting="submitting" @select="onAction" />

    <van-popup v-model:show="showObject" round position="bottom" safe-area-inset-bottom>
      <div class="object-pop">
        <div class="object-pop__title">提交异议</div>
        <p class="object-pop__sub">提交后单据会退回管理员重新核定，重新发布前你将暂时看不到该单</p>
        <van-field
          v-model="objectReason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          label="异议原因"
          placeholder="必填，2–200 字，例如：8 月考勤缺卡已补卡但未计入"
        />
        <p v-if="objectError" class="object-pop__error" role="alert">{{ objectError }}</p>
        <div class="object-pop__foot">
          <van-button block type="danger" :loading="submitting" @click="submitObject">确认提交异议</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.pay-hero__amount {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-num-lg-staff);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-lg);
}

.object-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.object-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.object-pop__sub {
  padding: 0 var(--sp-4);
  margin: var(--sp-1) 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.object-pop__error {
  margin: var(--sp-2) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.object-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
