<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import MyPayrollCard from '@/components/MyPayrollCard.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import PayrollStatusSteps from '@/components/PayrollStatusSteps.vue'
import { PAYROLL_LOG_ACTION, PAYROLL_STATUS } from '@kdyzgl/shared/constants/dict.js'
import { FINANCE_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import {
  addPayrollItem,
  approvePayroll,
  getPayroll,
  getPayrollLogs,
  payPayroll,
  publishPayrolls,
  submitPayrolls,
  updatePayrollItems
} from '@/api/finance.js'

/**
 * B9 工资单详情（管理端：审核 / 发布 / 改金额 / 加扣款 / 发放归档 / 留痕）
 *
 * 金额可编辑判据与服务端 isItemEditable 同口径（DRAFT / REJECTED / PENDING_APPROVAL / OBJECTED），
 * 且仅 source=MANUAL 项可改；PAID 为终态完全冻结（写入口全部隐藏，服务端 9413 兜底）。
 * 留痕字段由服务端按角色裁剪：本端为 ADMIN，渲染操作人 / 状态变化 / 金额快照。
 */
const route = useRoute()
const id = computed(() => Number(route.params.id))

/** 与服务端 isItemEditable 镜像：只有这些状态能改人工项金额 / 加扣款 */
const EDITABLE_STATUS = ['DRAFT', 'REJECTED', 'PENDING_APPROVAL', 'OBJECTED']

const loading = ref(true)
const error = ref('')
const payroll = ref(null)
const submitting = ref(false)

/* ---- 驳回弹层 ---- */
const showReject = ref(false)
const rejectReason = ref('')
const rejectError = ref('')

/* ---- 人工项金额编辑（C-3） ---- */
const itemEdits = ref({})
const showItemReason = ref(false)
const itemReason = ref('')
const itemReasonError = ref('')

/* ---- 加扣款（I-6） ---- */
const showItemAdd = ref(false)
const addForm = ref({ itemType: 'ADDITION', itemName: '', amount: '', reason: '' })
const addError = ref('')

/* ---- 留痕（I-7） ---- */
const logs = ref([])
const logsLoading = ref(true)
const logsError = ref('')

function resetItemEdits(data) {
  const next = {}
  ;(data.items || [])
    .filter((item) => item.source === 'MANUAL')
    .forEach((item) => {
      next[item.key] = String(item.amount)
    })
  itemEdits.value = next
}

const canEditItems = computed(() => !!payroll.value && EDITABLE_STATUS.includes(payroll.value.status))
const manualItems = computed(() =>
  payroll.value ? (payroll.value.items || []).filter((item) => item.source === 'MANUAL') : []
)
/** 有实际改动才允许保存人工项 */
const itemChanges = computed(() =>
  manualItems.value
    .filter((item) => Number(itemEdits.value[item.key]) !== Number(item.amount))
    .map((item) => ({ key: item.key, amount: Number(itemEdits.value[item.key]) }))
)
const itemEditNote = computed(() => {
  if (!payroll.value) return ''
  if (canEditItems.value) return '仅人工填写项可改金额；保存时须填写变更事由（2–200 字）'
  return `当前状态（${(PAYROLL_STATUS[payroll.value.status] || {}).label || ''}）不允许修改金额`
})

const steps = computed(() => {
  const data = payroll.value
  if (!data) return []
  const reviewed = ['APPROVED', 'PUBLISHED', 'CONFIRMED', 'PAID', 'REJECTED'].includes(data.status)
  const published = ['PUBLISHED', 'CONFIRMED', 'PAID'].includes(data.status)
  const objected = data.status === 'OBJECTED'
  const reviewState =
    objected || data.status === 'REJECTED'
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
      state: published
        ? 'done'
        : data.status === 'APPROVED' || data.status === 'OBJECTED'
          ? 'current'
          : 'pending',
      time: data.publishTime
    },
    {
      key: 'CONFIRM',
      label: '员工确认',
      state:
        data.status === 'CONFIRMED' || data.status === 'PAID'
          ? 'done'
          : data.status === 'PUBLISHED'
            ? 'current'
            : 'pending',
      time: data.confirmTime
    },
    {
      key: 'PAY',
      label: '确认发放',
      state: data.status === 'PAID' ? 'done' : data.status === 'CONFIRMED' ? 'current' : 'pending',
      time: data.paidTime
    }
  ]
})

const actions = computed(() => {
  const data = payroll.value
  if (!data) return []
  const list = []
  if (data.status === 'DRAFT' || data.status === 'REJECTED') {
    list.push({ key: 'submit', label: '提交审核' })
  }
  if (data.status === 'PENDING_APPROVAL') {
    list.push({ key: 'approve', label: '审核通过' })
    list.push({ key: 'reject', label: '驳回', type: 'danger' })
  }
  if (data.status === 'APPROVED') list.push({ key: 'publish', label: '发布给员工' })
  if (data.status === 'OBJECTED') list.push({ key: 'republish', label: '重新发布' })
  if (data.status === 'CONFIRMED') list.push({ key: 'pay', label: '确认工资已发放' })
  if (canEditItems.value) list.push({ key: 'addItem', label: '加款 / 扣款', plain: true })
  return list
})

const actionNote = computed(() => {
  const status = payroll.value ? payroll.value.status : ''
  if (status === 'DRAFT') return '草稿尚未提交审核；可直接加扣款或调整人工项金额'
  if (status === 'REJECTED') return '已驳回，修改后可重新提交审核'
  if (status === 'PENDING_APPROVAL') return '待审核：可审核通过、驳回，也可直接调整人工项金额'
  if (status === 'PUBLISHED') return '已发布，等待员工确认或提异议'
  if (status === 'OBJECTED') return '员工提出异议，已退回重新核定；可修改金额后重新发布（或走二次审批）'
  if (status === 'CONFIRMED') return '员工已确认，待管理员确认工资已发放（发放后不可修改）'
  if (status === 'PAID') return '该工资单已发放并归档，不可修改'
  return ''
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    payroll.value = await getPayroll(id.value)
    resetItemEdits(payroll.value)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  logsError.value = ''
  try {
    logs.value = await getPayrollLogs(id.value)
  } catch (e) {
    logsError.value = e.message || '留痕加载失败'
  } finally {
    logsLoading.value = false
  }
}

/** 状态已变化（9403）：刷新详情与留痕并提示，不静默 */
async function refreshOnConflict(message) {
  showFailToast(message || '该单状态已变化，正在刷新')
  await Promise.all([load(), loadLogs()])
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
  if (key === 'addItem') {
    addForm.value = { itemType: 'ADDITION', itemName: '', amount: '', reason: '' }
    addError.value = ''
    showItemAdd.value = true
    return
  }
  if (key === 'submit') {
    submitting.value = true
    try {
      await submitPayrolls({ ids: [data.id] })
      showSuccessToast('已提交审核')
      await Promise.all([load(), loadLogs()])
    } catch (e) {
      if (e.code === FINANCE_CODE.PAYROLL_ARCHIVED) showFailToast('该工资单已发放归档，不可修改')
      else if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) await refreshOnConflict()
      else showFailToast(e.message || '提交失败')
    } finally {
      submitting.value = false
    }
    return
  }
  if (key === 'approve') {
    submitting.value = true
    try {
      payroll.value = await approvePayroll(data.id, { approved: true })
      resetItemEdits(payroll.value)
      showSuccessToast('已通过，可在详情页或列表发布给员工')
      await loadLogs()
    } catch (e) {
      if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) await refreshOnConflict()
      else showFailToast(e.message || '审核失败')
    } finally {
      submitting.value = false
    }
    return
  }
  if (key === 'publish' || key === 'republish') {
    const republish = key === 'republish'
    try {
      await showConfirmDialog({
        title: republish ? '确认重新发布' : '发布工资单',
        message: republish
          ? `将重新发布给 ${data.employeeName}（${data.month}），发布后员工可见并需重新确认，不可撤回。`
          : `将发布给 ${data.employeeName}（${data.month}），发布后员工可见并需确认，不可撤回。`,
        confirmButtonText: republish ? '确认重新发布' : '确认发布',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return
    }
    submitting.value = true
    try {
      const result = await publishPayrolls({ ids: [data.id] })
      showSuccessToast(result.published ? (republish ? '已重新发布' : '已发布') : '该单当前不可发布')
      await Promise.all([load(), loadLogs()])
    } catch (e) {
      if (e.code === FINANCE_CODE.PAYROLL_ARCHIVED) showFailToast('该工资单已发放归档，不可修改')
      else if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) await refreshOnConflict('该单当前状态不允许发布')
      else showFailToast(e.message || '发布失败')
    } finally {
      submitting.value = false
    }
    return
  }
  if (key === 'pay') {
    try {
      await showConfirmDialog({
        title: '确认工资已发放',
        message: `对象：${data.employeeName} · ${data.month} · 实发 ${data.netAmount} 元\n影响：发放后单据进入「已发放」并归档冻结，员工可见该归档态\n本期不支持撤销 / 冲正，确认后不可修改。`,
        confirmButtonText: '确认已发放',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return
    }
    submitting.value = true
    try {
      payroll.value = await payPayroll(data.id, {})
      resetItemEdits(payroll.value)
      showSuccessToast('已标记发放，工资单已归档')
      await loadLogs()
    } catch (e) {
      if (e.code === FINANCE_CODE.PAYROLL_ARCHIVED) showFailToast('该工资单已发放归档，不可重复操作')
      else if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) await refreshOnConflict('该单当前状态不允许发放')
      else showFailToast(e.message || '发放失败')
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
    resetItemEdits(payroll.value)
    showReject.value = false
    showSuccessToast('已驳回，员工看不到该单')
    await loadLogs()
  } catch (e) {
    rejectError.value =
      e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID ? '该单状态已变化，请关闭后刷新查看' : e.message || '驳回失败'
  } finally {
    submitting.value = false
  }
}

/** 保存人工项：先校验改动，再弹事由弹层（C-3 事由必填 2-200） */
function openItemSave() {
  if (!itemChanges.value.length) return
  const invalid = itemChanges.value.some((row) => !Number.isFinite(row.amount))
  if (invalid) {
    showFailToast('金额须为数字')
    return
  }
  itemReason.value = ''
  itemReasonError.value = ''
  showItemReason.value = true
}

async function submitItemUpdate() {
  const reason = itemReason.value.trim()
  if (reason.length < 2 || reason.length > 200) {
    itemReasonError.value = '金额变更事由必填（2–200 字）'
    return
  }
  submitting.value = true
  try {
    payroll.value = await updatePayrollItems(payroll.value.id, itemChanges.value, reason)
    resetItemEdits(payroll.value)
    showItemReason.value = false
    showSuccessToast('已保存并重算合计')
    await loadLogs()
  } catch (e) {
    if (e.code === FINANCE_CODE.REASON_REQUIRED) itemReasonError.value = e.message
    else if (e.code === FINANCE_CODE.PAYROLL_ARCHIVED) {
      showItemReason.value = false
      showFailToast('该工资单已发放归档，不可修改')
    } else if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) {
      showItemReason.value = false
      await refreshOnConflict()
    } else itemReasonError.value = e.message || '保存失败'
  } finally {
    submitting.value = false
  }
}

async function submitItemAdd() {
  const form = addForm.value
  const name = form.itemName.trim()
  if (name.length < 2 || name.length > 20) {
    addError.value = '请填写名称（2–20 字）'
    return
  }
  const amount = Number(form.amount)
  if (!Number.isFinite(amount) || amount <= 0) {
    addError.value = '金额须为大于 0 的数字'
    return
  }
  const reason = form.reason.trim()
  if (reason.length < 2 || reason.length > 200) {
    addError.value = '加扣款事由必填（2–200 字）'
    return
  }
  submitting.value = true
  addError.value = ''
  try {
    payroll.value = await addPayrollItem(payroll.value.id, {
      itemType: form.itemType,
      itemName: name,
      amount,
      reason
    })
    resetItemEdits(payroll.value)
    showItemAdd.value = false
    showSuccessToast('已添加并重算合计')
    await loadLogs()
  } catch (e) {
    if (e.code === FINANCE_CODE.REASON_REQUIRED) addError.value = e.message
    else if (e.code === FINANCE_CODE.ITEM_KEY_EXISTS) addError.value = '该明细已存在，请刷新后重试'
    else if (e.code === FINANCE_CODE.PAYROLL_ARCHIVED) {
      showItemAdd.value = false
      showFailToast('该工资单已发放归档，不可修改')
    } else if (e.code === FINANCE_CODE.PAYROLL_STATUS_INVALID) {
      showItemAdd.value = false
      await refreshOnConflict()
    } else addError.value = e.message || '添加失败'
  } finally {
    submitting.value = false
  }
}

/* ==================== 留痕渲染（I-7，ADMIN 视角） ==================== */

const AMOUNT_FIELDS = [
  { key: 'grossAmount', label: '应发' },
  { key: 'deductionTotal', label: '扣项' },
  { key: 'netAmount', label: '实发' }
]

function actionText(action) {
  return (PAYROLL_LOG_ACTION[action] || {}).label || action
}

function statusText(status) {
  return (PAYROLL_STATUS[status] || {}).label || status
}

/** 状态变化：有 fromStatus 才渲染「A → B」，否则只渲染 toStatus */
function statusChange(log) {
  if (log.fromStatus && log.toStatus) return `${statusText(log.fromStatus)} → ${statusText(log.toStatus)}`
  if (log.toStatus) return statusText(log.toStatus)
  return ''
}

/** 金额对比：明细级逐项配对（仅变动项）+ 合计级（仅变动项）；before/after 缺失则不渲染该区块 */
function amountLines(log) {
  const lines = []
  const before = log.before || {}
  const after = log.after || {}
  const beforeItems = before.items || []
  const afterItems = after.items || []
  afterItems.forEach((item) => {
    const prev = beforeItems.find((row) => row.itemKey === item.itemKey)
    if (prev && Number(prev.amount) !== Number(item.amount)) {
      lines.push(`${item.itemName}：${prev.amount} → ${item.amount}`)
    }
  })
  AMOUNT_FIELDS.forEach((field) => {
    if (before[field.key] !== undefined && after[field.key] !== undefined && Number(before[field.key]) !== Number(after[field.key])) {
      lines.push(`${field.label}：${before[field.key]} → ${after[field.key]}`)
    }
  })
  return lines
}

onMounted(() => {
  load()
  loadLogs()
})
</script>

<template>
  <div class="pay-detail">
    <PageNav title="工资单详情" />
    <div class="page page--loose">
      <PageState :loading="loading" :error="error" @retry="load">
        <template v-if="payroll">
          <van-notice-bar
            v-if="payroll.status === 'PAID'"
            class="notice"
            left-icon="lock"
            :text="`该工资单已发放并归档，不可修改（发放人 ${payroll.paidByName || '-'} · ${payroll.paidTime || '-'}）`"
            wrapable
            color="var(--color-success)"
            background="var(--color-success-surface)"
          />

          <div class="section-title">流转状态</div>
          <div class="card">
            <PayrollStatusSteps :steps="steps" />
          </div>

          <div class="section-title">金额明细</div>
          <MyPayrollCard :payroll="payroll" />

          <template v-if="manualItems.length">
            <div class="section-title">人工项金额</div>
            <div class="card">
              <van-field
                v-for="item in manualItems"
                :key="item.key"
                v-model="itemEdits[item.key]"
                type="number"
                inputmode="decimal"
                :label="item.name"
                :readonly="!canEditItems"
                :disabled="!canEditItems"
              />
              <p class="tip">{{ itemEditNote }}</p>
              <van-button
                v-if="canEditItems"
                class="item-save"
                block
                type="primary"
                plain
                :disabled="!itemChanges.length || submitting"
                @click="openItemSave"
              >
                保存人工项
              </van-button>
            </div>
          </template>

          <div class="section-title">操作留痕</div>
          <div class="card">
            <div v-if="logsLoading" class="sk-row skeleton-block" aria-busy="true" />
            <div v-else-if="logsError" class="logs-error" role="alert">
              <p class="logs-error__text">{{ logsError }}</p>
              <button type="button" class="logs-error__retry" @click="loadLogs">重新加载</button>
            </div>
            <p v-else-if="!logs.length" class="tip">暂无操作留痕</p>
            <div v-for="log in logs" v-else :key="log.id" class="log-row">
              <div class="log-row__head">
                <span class="log-tag">{{ actionText(log.action) }}</span>
                <span class="log-row__time tabular-nums">{{ log.time }}</span>
              </div>
              <p class="log-row__operator">{{ log.operatorName }}（{{ log.operatorRole }}）</p>
              <p v-if="statusChange(log)" class="log-row__status">{{ statusChange(log) }}</p>
              <ul v-if="amountLines(log).length" class="log-row__amounts">
                <li v-for="(line, index) in amountLines(log)" :key="index" class="tabular-nums">{{ line }}</li>
              </ul>
              <p v-if="log.reason" class="log-row__reason">事由：{{ log.reason }}</p>
            </div>
          </div>

          <p class="tip">审核通过后仍需「发布」一步，员工才会看到该单；已发布的单据员工可确认或提异议</p>
        </template>
      </PageState>

      <!-- 操作区做进内容流（inline）：随页面滚动，滑到底即见，不再固定悬浮遮挡内容 -->
      <ActionBar
        inline
        :actions="actions"
        :note="actionNote"
        :submitting="submitting"
        @select="onAction"
      />
    </div>

    <!-- 驳回弹层 -->
    <van-popup v-model:show="showReject" round position="bottom" safe-area-inset-bottom>
      <div class="pop">
        <div class="pop__title">驳回工资单</div>
        <p class="pop__sub">驳回后单据回到「已驳回」，员工不可见，需财务端修改后重新提交</p>
        <van-field
          v-model="rejectReason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          label="审核意见"
          placeholder="必填，2–200 字，例如：绩效数据与业务口径不符"
        />
        <p v-if="rejectError" class="pop__error" role="alert">{{ rejectError }}</p>
        <div class="pop__foot">
          <van-button block type="danger" :loading="submitting" @click="submitReject">确认驳回</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 人工项事由弹层（C-3） -->
    <van-popup v-model:show="showItemReason" round position="bottom" safe-area-inset-bottom>
      <div class="pop">
        <div class="pop__title">填写金额变更事由</div>
        <p class="pop__sub">事由会写入操作留痕，作为审计依据</p>
        <van-field
          v-model="itemReason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          label="变更事由"
          placeholder="必填，2–200 字"
        />
        <p v-if="itemReasonError" class="pop__error" role="alert">{{ itemReasonError }}</p>
        <div class="pop__foot">
          <van-button block type="primary" :loading="submitting" @click="submitItemUpdate">确认保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 加款 / 扣款弹层（I-6） -->
    <van-popup v-model:show="showItemAdd" round position="bottom" safe-area-inset-bottom>
      <div class="pop">
        <div class="pop__title">加款 / 扣款</div>
        <p class="pop__sub">加款计入应发合计，扣款计入扣项合计；保存后自动重算四项合计。</p>
        <van-field label="方向">
          <template #input>
            <van-radio-group v-model="addForm.itemType" direction="horizontal">
              <van-radio name="ADDITION">加款</van-radio>
              <van-radio name="DEDUCTION">扣款</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="addForm.itemName" label="名称" placeholder="2–20 字，例如：设备赔偿" maxlength="20" />
        <van-field v-model="addForm.amount" type="number" inputmode="decimal" label="金额" placeholder="大于 0，最多 2 位小数" />
        <van-field
          v-model="addForm.reason"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          label="事由"
          placeholder="必填，2–200 字"
        />
        <p v-if="addError" class="pop__error" role="alert">{{ addError }}</p>
        <div class="pop__foot">
          <van-button block type="primary" :loading="submitting" @click="submitItemAdd">确认添加</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.pop__sub {
  padding: 0 var(--sp-4);
  margin: var(--sp-1) 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.pop__error {
  margin: var(--sp-2) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}

.item-save {
  min-height: 44px;
  margin-top: var(--sp-3);
}

.sk-row {
  height: 96px;
}

.logs-error {
  padding: var(--sp-4) 0;
  text-align: center;
}

.logs-error__text {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--color-danger);
}

.logs-error__retry {
  min-height: var(--touch-min);
  padding: 0 var(--sp-5);
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

.log-row {
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--border-line);
}

.log-row:last-child {
  border-bottom: none;
}

.log-row__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.log-tag {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  color: var(--text-2);
  background: var(--surface-subtle);
  border-radius: var(--r-full);
}

.log-row__time {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.log-row__operator,
.log-row__status {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-2);
}

.log-row__amounts {
  padding-left: var(--sp-4);
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.log-row__reason {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  color: var(--text-3);
}
</style>
