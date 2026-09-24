<script setup>
import { computed, ref } from 'vue'
import ProfileHero from './ProfileHero.vue'
import DemoIdentityGroup from './DemoIdentityGroup.vue'
import AccountSecurityGroup from './AccountSecurityGroup.vue'
import LogoutAction from './LogoutAction.vue'
import { useAuthStore } from '../stores/auth.js'
import { getDeviceInfo, isShell } from '../utils/bridge.js'

/**
 * 「我的」页主体（仅服务管理端；员工端已迁至 views/staff/me.vue）
 * 管理端保留「账号信息」5 行与「运行环境」两块；Hero / 演示身份 / 账号安全 / 退出改用跨端共享件，
 * DOM 与类名保持拆分前一致。
 */
const auth = useAuthStore()
const device = ref(getDeviceInfo())

/** 副信息保持拆分前的单行口径（管理端只展示账号归属一行） */
const heroLines = computed(() => [`${auth.user.username || ''} · ${auth.user.stationName || '总部（不归属驿站）'}`])

/**
 * 账号信息区三态（G-05）：store 已暴露 userLoaded / userLoading / userError，这里只做渲染分流。
 * 为什么状态放 store 而不是让子块自己取数：/auth/me 目前没有页面级调用点（见 stores/auth.js 的 refreshMe），
 * 组件内自发请求就等于给接口加了一个新的调用时机；收进 store 后组件零新增请求，壳 onResume 与用户重试能复用同一份状态。
 */
const meState = computed(() => {
  if (auth.userError) return 'error'
  if (auth.userLoading || !auth.userLoaded) return 'loading'
  return 'ready'
})

/** 重试只走 store 的 refreshMe：不新增 /auth/me 的调用时机，仅用户主动触发 */
async function onRetryMe() {
  try {
    await auth.refreshMe()
  } catch (e) {
    // 失败原因已记入 userError 并由错误态回显，这里不再弹第二条提示
  }
}
</script>

<template>
  <div class="page">
    <!-- 个人信息卡：品牌 Hero 渐变 Token，白字副信息 5.4:1（修 P3 硬编码与 3.28:1） -->
    <ProfileHero :name="auth.user.realName" :role="auth.user.role" :lines="heroLines" />

    <!-- 待办队列不在这里：统一收进「消息」Tab（A2 B13-B16 / S15-S16），
         两个 Tab 各挂一份入口必然出现「我的说 3 条、消息说 2 条」的口径漂移（A1-1 原则 2） -->

    <!-- 管理端＝管理与配置（配置类二级页入口，无个人业务数据）；员工端「我的数据」已迁至 views/staff/me.vue -->
    <div class="section-title">管理与配置</div>
    <van-cell-group inset>
      <van-cell title="KPI 考核" label="全站考核结果、指标与权重配置" is-link to="/boss/kpi" />
      <van-cell title="人事管理" label="员工档案查询与调薪" is-link to="/boss/hr" />
      <van-cell title="排班管理" label="按驿站排班与批量铺排" is-link to="/boss/schedule" />
      <van-cell title="打卡规则" label="配置打卡时段与校验方式" is-link to="/boss/attendance/rule" />
      <van-cell title="打卡记录" label="全域打卡记录查询" is-link to="/boss/attendance/records" />
      <van-cell title="请假扣款设置" label="全局单开关：请假是否影响工资" is-link to="/boss/leave/settings" />
    </van-cell-group>

    <div class="section-title">账号信息</div>

    <!-- 取数中：整块等高骨架（5 行 van-cell 的实际高度），数据到达不跳版（AP-15） -->
    <div v-if="meState === 'loading'" class="acc-sk skeleton-block" aria-busy="true" />

    <!-- 取数失败：原因 + 下一步 + 重试；不再静默渲染空白或旧值（5.1 / 5.2） -->
    <div v-else-if="meState === 'error'" class="acc-error" role="alert">
      <p class="acc-error__text">{{ auth.userError }}</p>
      <p class="acc-error__hint">请检查网络后重试，若持续失败请联系管理员</p>
      <button type="button" class="acc-error__retry" @click="onRetryMe">重新加载</button>
    </div>

    <van-cell-group v-else inset>
      <van-cell title="登录账号" :value="auth.user.username" />
      <van-cell title="手机号" :value="auth.user.phone" />
      <van-cell title="所属驿站" :value="auth.user.stationName || '-'" />
      <van-cell title="所属部门" :value="auth.user.deptName || '-'" />
      <van-cell title="最后登录" :value="auth.user.lastLoginTime || '-'" />
    </van-cell-group>

    <DemoIdentityGroup />

    <AccountSecurityGroup />

    <!-- 运行环境仅 Demo 诊断用，降级为 Caption，不参与信息层级 -->
    <div class="section-title">运行环境</div>
    <van-cell-group inset class="env">
      <van-cell title="运行容器" :value="isShell() ? '安卓 H5 壳' : '浏览器（无壳）'" />
      <van-cell title="状态栏高度" :value="device ? `${device.statusBarHeight || 0} px` : '0 px'" />
    </van-cell-group>

    <LogoutAction />
  </div>
</template>

<style scoped>
/* 账号信息骨架：高度 = 5 行 van-cell（--row-h-1），与真实 cell-group 等高，数据到达不跳版（AP-15）。
 * 底色与脉冲动画复用全局 .skeleton-block，这里只定高宽 */
.acc-sk {
  height: calc(var(--row-h-1) * 5);
}

/* 取数失败块：与 cell-group 同宽同位，靠卡片底色保持版面一致 */
.acc-error {
  padding: var(--sp-5) var(--sp-4);
  text-align: center;
  background: var(--surface-card);
  border-radius: var(--r-lg);
}

.acc-error__text {
  margin: 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--color-danger);
}

.acc-error__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

/* 重试为次要控件：描边取 500 档，与 PageState / StationPicker 同口径；高度 44 满足触控（5.1） */
.acc-error__retry {
  display: block;
  width: 100%;
  min-height: var(--touch-min);
  margin-top: var(--sp-4);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-sm);
}

.env {
  --van-cell-font-size: var(--fs-caption);
  --van-cell-text-color: var(--text-3);
  --van-cell-value-color: var(--text-3);
}
</style>