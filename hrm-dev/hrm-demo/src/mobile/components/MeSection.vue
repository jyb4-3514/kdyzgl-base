<script setup>
import { ref } from 'vue'
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
    <van-cell-group inset>
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
