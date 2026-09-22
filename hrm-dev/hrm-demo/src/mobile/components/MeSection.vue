<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import IdentitySwitcher from './IdentitySwitcher.vue'
import { roleLabel } from '../constants/accounts.js'
import { useAuthStore } from '../stores/auth.js'
import { getDeviceInfo, isShell } from '../utils/bridge.js'

/**
 * 「我的」页主体（老板端 / 员工端共用，B6 / S10）
 * 两端此页差异只有身份与文案，抽成一个组件，避免两份几乎相同的页面各自漂移
 * （改密入口、退出确认、cell-group 缩进口径只写一次）。
 */
const auth = useAuthStore()
const router = useRouter()
const device = ref(getDeviceInfo())
/** 演示态才展示身份切换区块；生产构建下整块不渲染 */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'

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

async function onLogout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '退出后需重新登录，确定继续？' })
  } catch (e) {
    return // 用户取消
  }
  await auth.logout()
  showSuccessToast('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="page">
    <!-- 个人信息卡：改用品牌 Hero 渐变 Token，白字副信息 5.4:1（修 P3 硬编码与 3.28:1） -->
    <section class="hero hero--brand profile">
      <div class="flex-between">
        <span class="hero__title">{{ auth.user.realName }}</span>
        <span class="hero__chip">{{ roleLabel(auth.user.role) }}</span>
      </div>
      <p class="hero__sub">{{ auth.user.username }} · {{ auth.user.stationName || '总部（不归属驿站）' }}</p>
    </section>

    <!-- 待办队列不在这里：统一收进「消息」Tab（A2 B13-B16 / S15-S16），
         两个 Tab 各挂一份入口必然出现「我的说 3 条、消息说 2 条」的口径漂移（A1-1 原则 2） -->

    <!-- 老板端＝管理与配置（配置类二级页入口，无个人业务数据）；员工端＝我的数据（低频只读查询） -->
    <div class="section-title">{{ auth.isAdmin ? '管理与配置' : '我的数据' }}</div>
    <van-cell-group v-if="auth.isAdmin" inset>
      <van-cell title="KPI 考核" label="全站考核结果、指标与权重配置" is-link to="/boss/kpi" />
      <van-cell title="人事管理" label="员工档案查询与调薪" is-link to="/boss/hr" />
      <van-cell title="排班管理" label="按驿站排班与批量铺排" is-link to="/boss/schedule" />
      <van-cell title="打卡规则" label="配置打卡时段与校验方式" is-link to="/boss/attendance/rule" />
      <van-cell title="打卡记录" label="全域打卡记录查询" is-link to="/boss/attendance/records" />
      <van-cell title="请假扣款设置" label="全局单开关：请假是否影响工资" is-link to="/boss/leave/settings" />
    </van-cell-group>
    <van-cell-group v-else inset>
      <van-cell title="我的 KPI" label="本月得分、达成率与排名" is-link to="/staff/kpi" />
      <van-cell title="我的工资单" label="已发布工资单与确认" is-link to="/staff/payroll" />
      <van-cell title="我的档案" label="合同、岗位与薪资构成（只读）" is-link to="/staff/profile" />
      <van-cell title="我的排班" label="查看本周班次安排" is-link to="/staff/schedule" />
      <van-cell title="打卡记录" label="查看我的打卡明细" is-link to="/staff/attendance/records" />
      <!-- 「我的补卡申请」等四项同属 B5-4：宫格为一线高频直达，「我的」是低频兜底查询 -->
      <van-cell title="我的补卡申请" label="申请记录与审批进度" is-link to="/staff/attendance/makeup" />
      <van-cell title="我的请假" label="申请记录与审批进度" is-link to="/staff/leave" />
      <van-cell
        v-if="auth.role === 'STATION_ADMIN'"
        title="请假初审"
        label="本站员工请假待初审"
        is-link
        to="/staff/leave/review"
      />
      <van-cell title="我的入离职" label="在职状态与离职结算单" is-link to="/staff/flow" />
      <van-cell v-if="auth.canSeeSync" title="同步状态" label="本站采集状态与批次流水" is-link to="/staff/sync" />
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

    <template v-if="demoEnabled">
      <div class="section-title">切换演示身份<span class="section-title__extra">Demo 专用</span></div>
      <IdentitySwitcher />
    </template>

    <div class="section-title">账号安全</div>
    <van-cell-group inset>
      <van-cell title="修改密码" is-link to="/staff/me/password" />
    </van-cell-group>

    <!-- 运行环境仅 Demo 诊断用，降级为 Caption，不参与信息层级 -->
    <div class="section-title">运行环境</div>
    <van-cell-group inset class="env">
      <van-cell title="运行容器" :value="isShell() ? '安卓 H5 壳' : '浏览器（无壳）'" />
      <van-cell title="状态栏高度" :value="device ? `${device.statusBarHeight || 0} px` : '0 px'" />
    </van-cell-group>

    <div class="logout">
      <van-button block type="danger" plain @click="onLogout">退出登录</van-button>
    </div>
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

.profile {
  margin-top: var(--sp-3);
}

.env {
  --van-cell-font-size: var(--fs-caption);
  --van-cell-text-color: var(--text-3);
  --van-cell-value-color: var(--text-3);
}

.logout {
  margin: var(--sp-6) 0 var(--sp-2);
}
</style>
