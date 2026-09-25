<script setup>
import { computed } from 'vue'

/**
 * 工资单状态流转步骤条（C4 Molecule，B9.5 ①）
 * 为什么不自绘：连接线、居中排版、步骤自适应宽度都由 el-steps 负责，
 * 本组件只做「状态 → active / process-status」的映射与 --step-* 尺寸覆盖，不再造第二套步骤条。
 *
 * 状态口径以 shared/mock/financeStore.js 的 PAYROLL_ACTIONS 为准：
 * DRAFT → PENDING_APPROVAL → APPROVED → PUBLISHED → CONFIRMED，REJECTED 是由「待审核」驳回产生的分支状态，
 * 员工提异议（objection）会让单据退回「待审核」，故异议标记也落在该步上。
 */
const props = defineProps({
  // 契约状态值（financeStore.PAYROLL_STATUS_LABEL 的键）
  status: { type: String, default: 'DRAFT' },
  // 工资单详情：时间与经办人从中取，有哪条渲染哪条
  payroll: { type: Object, default: null }
})

const STEPS = [
  { key: 'DRAFT', label: '草稿' },
  { key: 'PENDING_APPROVAL', label: '待审核' },
  { key: 'APPROVED', label: '已通过' },
  { key: 'PUBLISHED', label: '已发布' },
  { key: 'CONFIRMED', label: '已确认' }
]

/**
 * active 取「当前状态所在步」：已确认时取 5（越过末步），否则末步会被 el-steps 当成进行中，
 * 已完成的终态看起来像「还在跑」。
 */
const activeIndex = computed(() => {
  if (props.status === 'REJECTED') return 1
  if (props.status === 'CONFIRMED') return STEPS.length
  const found = STEPS.findIndex((step) => step.key === props.status)
  return found < 0 ? 0 : found
})

// 驳回让「待审核」步变成 error；异议不改步骤色，用文字标签表达（SC 1.4.1：状态不只靠颜色）
const processStatus = computed(() => (props.status === 'REJECTED' ? 'error' : 'process'))

const hasObjection = computed(() => !!(props.payroll && props.payroll.objectionReason))

/** 流转留痕：只列出已发生的事实，未发生的步骤不占位（避免出现「审核时间 —」这种噪声） */
const timeline = computed(() => {
  const row = props.payroll
  if (!row) return []
  const items = []
  if (row.createTime) items.push({ label: '生成', text: row.createTime })
  if (row.objectionReason)
    items.push({ label: '员工提异议', text: `${row.objectionTime || ''} ${row.objectionReason}`.trim() })
  if (row.approveTime)
    items.push({
      label: row.status === 'REJECTED' ? '驳回' : '审核通过',
      text: `${row.approveTime} ${row.approverName || ''}`.trim()
    })
  if (row.approveRemark) items.push({ label: '审核意见', text: row.approveRemark })
  if (row.publishTime) items.push({ label: '发布', text: `${row.publishTime} ${row.publisherName || ''}`.trim() })
  if (row.confirmTime) items.push({ label: '员工确认', text: row.confirmTime })
  return items
})
</script>

<template>
  <section class="payroll-steps">
    <el-steps class="payroll-steps__bar" :active="activeIndex" :process-status="processStatus" align-center role="list">
      <el-step v-for="step in STEPS" :key="step.key" :title="step.label">
        <template #description>
          <span v-if="step.key === 'PENDING_APPROVAL' && status === 'REJECTED'" class="payroll-steps__mark is-danger"
            >已驳回</span
          >
          <span v-else-if="step.key === 'PENDING_APPROVAL' && hasObjection" class="payroll-steps__mark is-warning"
            >员工提异议</span
          >
        </template>
      </el-step>
    </el-steps>

    <!-- 时间线文字：步骤条只表达「走到哪一步」，具体时间与经办人必须可读，不能只靠点位形状 -->
    <p v-if="timeline.length" class="payroll-steps__timeline">
      <span v-for="item in timeline" :key="item.label" class="payroll-steps__record">
        <span class="payroll-steps__record-label">{{ item.label }}</span
        >{{ item.text }}
      </span>
    </p>
  </section>
</template>

<style scoped lang="scss">
.payroll-steps {
  &__bar {
    // 尺寸取 C3-3 的步骤条 Token：--step-dot 是组件默认 24px 的显式来源，改 Token 即同步
    :deep(.el-step__icon) {
      width: var(--step-dot);
      height: var(--step-dot);
      font-size: var(--fs-caption);
    }

    :deep(.el-step__line) {
      height: var(--step-line);
    }

    :deep(.el-step__title) {
      font-size: var(--fs-body);
      line-height: var(--lh-h3);
    }

    // 步骤间距：居中排版下描述块的左右留白就是相邻步骤文字之间的间距
    :deep(.el-step__description) {
      padding-left: var(--step-gap);
      padding-right: var(--step-gap);
      margin-top: 0;
    }

    // 未开始/已完成用既有语义色，不用 Element 默认的 placeholder 灰
    :deep(.el-step__head.is-wait),
    :deep(.el-step__title.is-wait) {
      color: var(--text-disabled);
    }

    :deep(.el-step__line) {
      background-color: var(--border-line);
    }

    :deep(.el-step__head.is-finish .el-step__line) {
      background-color: var(--color-primary-icon);
    }
  }

  &__mark {
    display: inline-block;
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);

    &.is-danger {
      color: var(--state-danger-fg);
    }

    &.is-warning {
      color: var(--state-warning-fg);
    }
  }

  &__timeline {
    display: flex;
    flex-wrap: wrap;
    gap: var(--sp-2) var(--sp-4);
    margin: var(--sp-4) 0 0;
    padding-top: var(--sp-3);
    border-top: 1px solid var(--border-line);
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
    color: var(--text-3);
  }

  &__record-label {
    margin-right: var(--sp-1);
    color: var(--text-2);
  }
}
</style>
