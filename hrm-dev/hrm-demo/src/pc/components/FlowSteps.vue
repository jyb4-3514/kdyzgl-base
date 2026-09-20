<script setup>
import { computed } from 'vue'

/**
 * 入离职流程步骤条（C4 Molecule，B10.3）
 * 横向 5–6 步、含责任方与状态，用 el-steps + --step-* 尺寸 Token 覆盖；
 * 步骤数据直接来自契约的 flow.steps（key/name/status/operatorName/operateTime/remark），前端不重算流程顺序。
 *
 * 契约缺口说明：契约的步骤行只有办理人（operatorName），没有「责任方」字段，
 * 而 B10.2 要求每步显示责任人，故责任方按步骤 key 在前端做一次展示映射（不改契约、不参与流程判断）。
 * TODO(扩展): 后端补 step.ownerRole / ownerName 后删除本映射，直接取契约值。
 */
const props = defineProps({
  steps: { type: Array, default: () => [] },
  // 流程状态：IN_PROGRESS / COMPLETED / REJECTED
  flowStatus: { type: String, default: 'IN_PROGRESS' },
  currentStepKey: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

/** 责任方展示映射（仅文案，不参与状态判定） */
const STEP_OWNER = {
  // 入职
  SUBMIT_MATERIALS: '候选人 / 人事',
  HR_REVIEW: '人事',
  CREATE_ACCOUNT: '人事',
  ASSIGN_STATION: '人事',
  SET_SALARY: '老板',
  DONE: '站长',
  // 离职
  MANAGER_APPROVE: '站长',
  HR_APPROVE: '人事',
  HANDOVER: '站长',
  ASSET_RETURN: '站长',
  SETTLEMENT: '老板',
  LEAVE: '人事'
}

const isRejected = computed(() => props.flowStatus === 'REJECTED')

/**
 * 已驳回时仅「被驳回到的那一步」是 error，其余待办步骤置灰；已完成步骤保持完成态
 * （B10.5：驳回不回滚数据，只回退进度，所以历史节点不能被抹掉）。
 * 每步都显式给 status，故 el-steps 的 active 不参与着色（Element 的 el-step 以显式 status 优先）。
 * TODO(扩展): 契约的步骤只有 PENDING / DONE 两态，「可跳过的步骤（如入职培训）」暂无处表达，
 *   待后端补 skipped 后在此处补「已跳过」标签。
 */
function statusOf(step) {
  if (props.flowStatus === 'COMPLETED') return 'finish'
  if (step.status === 'DONE') return 'finish'
  if (step.key === props.currentStepKey) return isRejected.value ? 'error' : 'process'
  return 'wait'
}

/** caption：已完成显示「经办人 · 时间」，待办显示责任方，驳回步额外标文字标签（SC 1.4.1） */
function captionOf(step) {
  if (step.status === 'DONE') return [step.operatorName, step.operateTime].filter(Boolean).join(' · ') || '已完成'
  if (step.key === props.currentStepKey) return `责任：${STEP_OWNER[step.key] || '待指派'}`
  return STEP_OWNER[step.key] ? `责任：${STEP_OWNER[step.key]}` : '未开始'
}
</script>

<template>
  <div class="flow-steps" :class="{ 'is-scroll': steps.length > 6 }">
    <el-steps
      v-loading="loading"
      class="flow-steps__bar"
      :process-status="isRejected ? 'error' : 'process'"
      align-center
      role="list"
    >
      <el-step v-for="step in steps" :key="step.key" :title="step.name" :status="statusOf(step)">
        <template #description>
          <span class="flow-steps__caption">{{ captionOf(step) }}</span>
          <span v-if="isRejected && step.key === currentStepKey" class="flow-steps__mark">已驳回</span>
        </template>
      </el-step>
    </el-steps>
  </div>
</template>

<style scoped lang="scss">
.flow-steps {
  padding: var(--sp-2) 0;

  // 边界：步骤超过 6 步时横向滚动，不压缩成不可读的等分窄列
  &.is-scroll {
    overflow-x: auto;
  }

  &.is-scroll &__bar {
    min-width: 720px;
  }

  &__bar {
    :deep(.el-step__icon) {
      width: var(--step-dot);
      height: var(--step-dot);
      font-size: var(--fs-caption);
    }

    :deep(.el-step__line) {
      height: var(--step-line);
      background-color: var(--border-line);
    }

    :deep(.el-step__title) {
      font-size: var(--fs-body);
      line-height: var(--lh-h3);
    }

    :deep(.el-step__description) {
      padding-left: var(--step-gap);
      padding-right: var(--step-gap);
      margin-top: 0;
    }

    :deep(.el-step__head.is-wait),
    :deep(.el-step__title.is-wait),
    :deep(.el-step__description.is-wait) {
      color: var(--text-disabled);
    }

    :deep(.el-step__head.is-finish .el-step__line) {
      background-color: var(--color-primary-icon);
    }

    :deep(.el-step__head.is-error) {
      color: var(--color-danger);
    }

    // 进行中：主色点 + 120ms 脉冲（动画属性即可被 tokens.scss 的 prefers-reduced-motion 全局降级捕获）
    :deep(.el-step__head.is-process .el-step__icon) {
      animation: flow-pulse var(--dur-base) var(--ease-std) infinite alternate;
    }
  }

  &__caption {
    font-size: var(--fs-caption);
    line-height: var(--lh-caption);
  }

  &__mark {
    display: inline-block;
    margin-left: var(--sp-1);
    padding: 0 var(--sp-1);
    border-radius: var(--r-xs);
    background-color: var(--state-danger-bg);
    color: var(--state-danger-fg);
    font-size: var(--fs-micro);
    line-height: var(--lh-micro);
  }
}

@keyframes flow-pulse {
  from {
    transform: scale(1);
  }

  to {
    transform: scale(1.12);
  }
}
</style>
