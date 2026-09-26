<script setup>
import { computed } from 'vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import ProfileHero from '@/components/ProfileHero.vue'
import DemoIdentityGroup from '@/components/DemoIdentityGroup.vue'
import AccountSecurityGroup from '@/components/AccountSecurityGroup.vue'
import LogoutAction from '@/components/LogoutAction.vue'
import MyDataGroups from './me/components/MyDataGroups.vue'
import AboutGroup from './me/components/AboutGroup.vue'
import { useMyProfile } from './me/composables/useMyProfile.js'
import { useAuthStore } from '@/stores/auth.js'

/**
 * S10 我的（员工端）
 * 壳只做编排：Hero 取数三态 → 数据群导航 → 演示身份 → 账号安全 → 关于 → 退出。
 * 跨端件（Hero / 账号安全 / 演示身份 / 退出）与管理端共用；「账号信息」5 行与「运行环境」员工端不展示。
 */
const auth = useAuthStore()
const { state, retry } = useMyProfile()

/** Hero 副信息两行：一行账号归属、一行联系方式；空值统一显示 '-' */
const heroLines = computed(() => [
  `${auth.user.username || '-'} · ${auth.user.stationName || '总部（不归属驿站）'}`,
  `${auth.user.phone || '-'} · ${auth.user.deptName || '-'}`
])
</script>

<template>
  <div class="page">
    <!-- 三态由 Hero 承担：删掉「账号信息」块后它是本页唯一取数区块，userError 也只能在这里回显；
         群导航为静态入口，不包进 PageState -->
    <PageState :loading="state === 'loading'" :error="state === 'error' ? auth.userError : ''" @retry="retry">
      <ProfileHero :name="auth.user.realName" :role="auth.user.role" :lines="heroLines" />
    </PageState>

    <MyDataGroups :show-leave-review="auth.role === 'STATION_ADMIN'" />
    <DemoIdentityGroup />
    <AccountSecurityGroup />
    <AboutGroup />
    <LogoutAction />
  </div>
</template>
