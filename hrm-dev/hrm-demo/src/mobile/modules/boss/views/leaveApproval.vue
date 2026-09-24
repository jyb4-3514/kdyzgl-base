<script setup>
import LeaveApprovalList from '@/mobile/components/LeaveApprovalList.vue'

/**
 * P4 请假审批（ADMIN · 全域终审）
 *
 * 管理端域内、roles 一律 [ROLE.ADMIN]；stationId 缺省即全量，服务端不收敛（设计规范 §2.1）。
 * 与站长初审的区别只有三处：默认筛选 PENDING_BOSS、终审端点、以及对「已通过」单的撤回入口
 * （撤回由服务端派生标志 canRevoke 决定，站长拿不到该标志，故组件内无需再判角色）。
 */
</script>

<template>
  <LeaveApprovalList title="请假审批" default-status="PENDING_BOSS" actor="FINAL" />
</template>
