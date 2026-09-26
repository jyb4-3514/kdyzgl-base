<script setup>
/**
 * 员工端「我的数据」导航（两个二级群）
 *
 * 为什么分群：一级标题下原有 9~10 项一字排开，扫读成本高；按「薪酬与档案 / 考勤与流程」两群收拢后
 * 高频项仍在上方，站长专属项统一置群末。
 * 权限布尔由壳注入（展示组件不得直连 store），群内仅做显隐，判定口径不下沉。
 *
 * MVP 裁剪：原「我的 KPI」入口随 KPI 模块下架、「同步状态」入口随同步模块下架一并移除，
 * 群名由「薪酬与考核」改为「薪酬与档案」（不再含「考核」字样）。
 */
defineProps({
  /** 请假初审仅站长可见 */
  showLeaveReview: { type: Boolean, default: false }
})
</script>

<template>
  <div class="section-title">我的数据</div>

  <section role="group" aria-label="薪酬与档案">
    <div class="section-title section-title--sub">薪酬与档案</div>
    <van-cell-group inset>
      <van-cell title="我的工资单" label="已发布工资单与确认" is-link to="/staff/payroll" />
      <van-cell title="我的档案" label="合同、岗位与薪资构成（只读）" is-link to="/staff/profile" />
    </van-cell-group>
  </section>

  <section role="group" aria-label="考勤与流程">
    <div class="section-title section-title--sub">考勤与流程</div>
    <van-cell-group inset>
      <van-cell title="我的排班" label="查看本周班次安排" is-link to="/staff/schedule" />
      <van-cell title="打卡记录" label="查看我的打卡明细" is-link to="/staff/attendance/records" />
      <!-- 「我的补卡申请」等四项同属 B5-4：宫格为一线高频直达，「我的」是低频兜底查询 -->
      <van-cell title="我的补卡申请" label="申请记录与审批进度" is-link to="/staff/attendance/makeup" />
      <van-cell title="我的请假" label="申请记录与审批进度" is-link to="/staff/leave" />
      <van-cell title="我的入离职" label="在职状态与离职结算单" is-link to="/staff/flow" />
      <!-- 站长附加项一律置群末，不插队到个人项之间 -->
      <van-cell
        v-if="showLeaveReview"
        title="请假初审"
        label="本站员工请假待初审"
        is-link
        to="/staff/leave/review"
      />
    </van-cell-group>
  </section>
</template>
