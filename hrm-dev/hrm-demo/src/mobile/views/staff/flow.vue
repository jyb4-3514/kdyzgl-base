<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import MyPayrollCard from '../../components/MyPayrollCard.vue'
import { getMyPayrolls } from '../../api/finance.js'
import { getHrProfile } from '../../api/hr.js'
import { useAuthStore } from '../../stores/auth.js'

/**
 * B10 员工端 · 我的入离职
 *
 * 契约口径：入离职流程接口（/hr/onboarding、/hr/offboarding 及其步骤办理）**仅对 ADMIN 开放**，
 * 员工端拿不到流程实例与步骤进度。按 B0.2「无权限」态处理：只读降级 + 一行说明，**不渲染不可用按钮**，
 * 更不伪造一个点了没反应的「提交资料」入口。
 *
 * 员工真正能自证的信息仍有两条，且都是自己的数据：
 * 1. 在职状态与合同到期（人事档案本人可读）
 * 2. 离职结算单（我的工资单里 billType = SETTLEMENT 的那张）
 * TODO(扩展): 契约开放 GET /hr/flows/my 后，本页改为展示本人流程步骤条（复用 PayrollStatusSteps），
 * 并把「提交资料 / 确认交接」改为本人在对应步骤上执行
 */
const auth = useAuthStore()

const loading = ref(true)
const error = ref('')
const profile = ref(null)
const settlementList = ref([])

const resigned = computed(() => !!(profile.value && profile.value.leaveDate))
const contractWarn = computed(() => {
  const end = profile.value && profile.value.contractEnd
  if (!end) return ''
  const ts = new Date(`${end}T23:59:59`).getTime()
  if (!Number.isFinite(ts)) return ''
  if (ts < Date.now()) return '合同已过期'
  if (ts - Date.now() <= 30 * 86400000) return '合同 30 天内到期'
  return ''
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileData, payrollPage] = await Promise.all([
      getHrProfile(auth.user.id),
      getMyPayrolls({ pageNum: 1, pageSize: 100 })
    ])
    profile.value = profileData
    settlementList.value = payrollPage.list.filter((item) => item.billType === 'SETTLEMENT')
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="staff-flow">
    <PageNav title="我的入离职" />
    <div class="page page--loose">
      <PageState :loading="loading" :error="error" @retry="load">
        <section class="hero hero--brand flow-hero">
          <div class="flex-between">
            <span class="hero__title">{{ profile.employeeName }}</span>
            <span class="hero__chip">{{ resigned ? '已离职' : '在职' }}</span>
          </div>
          <p class="hero__sub">{{ profile.stationName || '总部' }} · {{ profile.deptName || '未分配部门' }}</p>
        </section>

        <div class="section-title">我的在职信息</div>
        <van-cell-group inset>
          <van-cell title="在职状态" :value="resigned ? `已离职（${profile.leaveDate}）` : '在职'" />
          <van-cell title="入职日期" :value="profile.entryDate || '-'" />
          <van-cell
            title="合同到期"
            :value="contractWarn ? `${profile.contractEnd}（${contractWarn}）` : profile.contractEnd || '无固定期限'"
          />
        </van-cell-group>

        <div class="section-title">我的离职结算单</div>
        <template v-if="settlementList.length">
          <MyPayrollCard v-for="item in settlementList" :key="item.id" :payroll="item" :show-items="false" />
        </template>
        <div v-else class="card">
          <p class="tip">暂无离职结算单</p>
        </div>

        <!-- 流程进度：接口仅对 ADMIN 开放，走 PageState 的 denied 变体做只读降级（不收手写卡片，状态组件全域唯一） -->
        <div class="section-title">流程进度</div>
        <PageState
          variant="denied"
          denied-text="入职与离职流程的步骤进度、资料审核与交接确认由人事端办理，员工端暂不展示流程实例。"
          denied-hint="如需查询进度或提交资料，请联系所在驿站站长或人事。"
        />
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.flow-hero {
  margin-top: var(--sp-3);
}
</style>
