<script setup>
import { computed } from 'vue'

/**
 * 请假单审批回显（设计规范 §8.1「已审批只读回显」）
 *
 * 三个消费方（我的请假列表、站长初审、管理员终审）都要把「谁批的、什么时候、什么意见」一次讲清；
 * 字段分支（初审槽 / 终审槽 / 撤回 / 撤销）是同一套判断，散在三处必然漂移。
 * 初审与终审字段在契约里分槽存放（stationApprover* / approver*），不会互相覆盖。
 */
const props = defineProps({
  /** LeaveVO（含 stationApprover* / approver* / revoke* / cancel* 字段） */
  item: { type: Object, required: true }
})

/** 未审批的单不渲染空的回显块，避免列表里多一条无内容的分隔线 */
const hasAudit = computed(
  () => !!(props.item.stationApproveTime || props.item.approveTime || props.item.revokeTime || props.item.cancelTime)
)
</script>

<template>
  <div v-if="hasAudit" class="audit">
    <template v-if="item.stationApproveTime">
      <div class="audit__line tabular-nums">
        初审：{{ item.stationApproverName || '站长' }} · {{ item.stationApproveTime }}
      </div>
      <div class="audit__line">初审意见：{{ item.stationApproveRemark || '未填写' }}</div>
    </template>
    <template v-if="item.approveTime">
      <div class="audit__line tabular-nums">终审：{{ item.approverName || '管理员' }} · {{ item.approveTime }}</div>
      <div class="audit__line">终审意见：{{ item.approveRemark || '未填写' }}</div>
    </template>
    <template v-if="item.revokeTime">
      <div class="audit__line tabular-nums">撤回：{{ item.revokerName || '管理员' }} · {{ item.revokeTime }}</div>
      <div class="audit__line">撤回原因：{{ item.revokeReason || '未填写' }}</div>
    </template>
    <div v-if="item.cancelTime" class="audit__line tabular-nums">撤销时间：{{ item.cancelTime }}</div>
  </div>
</template>

<style scoped>
.audit {
  padding-top: var(--sp-2);
  margin-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}

.audit__line {
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.audit__line + .audit__line {
  margin-top: var(--sp-1);
}
</style>
