<script setup>
import LeaveApprovalList from '../../components/LeaveApprovalList.vue'

/**
 * P3 请假初审（STATION_ADMIN · 仅本站）
 *
 * 入口落点为什么在员工端域内 `/staff/leave/review`（设计规范 §3.3）：
 * `/boss/*` 全部 roles:[ADMIN]，站长进去会被守卫弹回；站长移动端已被定位在员工端
 * （HOME_BY_ROLE 与员工共用宫格/待办/Tabbar）；改造 `/boss/*` 的代价远大于收益，
 * 且项目对「站长 vs 员工」刻意只保留极少差异（/staff/sync 与 canSeeSync 两处）。
 *
 * 页面本体与老板终审共用一个组件（只有默认筛选与端点不同），此处只做路由挂载点。
 */
</script>

<template>
  <LeaveApprovalList title="请假初审" default-status="PENDING_STATION" actor="STATION" />
</template>
