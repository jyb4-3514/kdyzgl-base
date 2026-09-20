<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { getHrProfile, getHrSalary } from '../../api/index.js'
import { useAuthStore } from '../../stores/auth.js'
import { moneyText } from '../../utils/format.js'

/**
 * B8 员工端 · 我的档案（只读）
 *
 * 三条硬约束：
 * 1. 只展示本人 —— 员工号取登录身份，契约侧也会拦（越权 403），前端不传 employeeId 之外的参数
 * 2. 银行卡号必须是脱敏值：契约出参已用 maskBankAccount；这里再兜一层，上游口径若退回完整卡号也不会渲染出来
 * 3. 契约未返回岗位/职级字段，不臆造：只有部门与驿站，岗位相关扩展点标 TODO
 */
const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const profile = ref(null)
const salary = ref(null)

const resigned = computed(() => !!(profile.value && profile.value.leaveDate))

/** 卡号脱敏兜底：12–25 位纯数字视为完整卡号，只保留首 4 后 4 */
function maskCard(value) {
  const text = String(value || '')
  if (!/^\d{12,25}$/.test(text)) return text || '-'
  return `${text.slice(0, 4)} **** **** ${text.slice(-4)}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileData, salaryData] = await Promise.all([getHrProfile(auth.user.id), getHrSalary(auth.user.id)])
    profile.value = profileData
    salary.value = salaryData
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="staff-profile">
    <PageNav title="我的档案" />
    <div class="page page--loose">
      <PageState :loading="loading" :error="error" @retry="load">
        <section class="hero hero--brand profile-hero">
          <div class="flex-between">
            <span class="hero__title">{{ profile.employeeName }}</span>
            <span class="hero__chip">{{ resigned ? '已离职' : '在职' }}</span>
          </div>
          <p class="hero__sub">
            {{ profile.stationName || '总部' }} · {{ profile.deptName || '未分配部门' }} · 入职
            {{ profile.entryDate || '-' }}
          </p>
        </section>

        <div class="section-title">劳动关系</div>
        <van-cell-group inset>
          <van-cell title="合同类型" :value="profile.contractTypeLabel || '-'" />
          <van-cell title="合同起始" :value="profile.contractStart || '-'" />
          <van-cell title="合同到期" :value="profile.contractEnd || '无固定期限'" />
          <van-cell title="试用期" :value="`${profile.probationMonths} 个月`" />
          <van-cell title="转正日期" :value="profile.regularDate || '-'" />
          <van-cell title="学历" :value="profile.educationLabel || '-'" />
          <!-- TODO(扩展): 契约未返回岗位/职级（hr_profile 无这两个字段），补齐后在「劳动关系」组内展示 -->
        </van-cell-group>

        <div class="section-title">联系方式（脱敏展示）</div>
        <van-cell-group inset>
          <van-cell title="手机号" :value="profile.phone || '-'" />
          <van-cell
            title="紧急联系人"
            :value="
              profile.emergencyContactName
                ? `${profile.emergencyContactName}（${profile.emergencyContactRelation || '-'}）`
                : '-'
            "
          />
          <van-cell title="联系人电话" :value="profile.emergencyContactPhone || '-'" />
          <van-cell title="开户行" :value="profile.bankName || '-'" />
          <van-cell title="银行卡号" :value="maskCard(profile.bankAccount)" />
        </van-cell-group>

        <template v-if="salary">
          <div class="section-title">我的薪资构成<span class="section-title__extra">标准薪资</span></div>
          <div class="card">
            <van-cell title="基本工资" :value="moneyText(salary.current.basicSalary)" />
            <van-cell title="岗位工资" :value="moneyText(salary.current.postSalary)" />
            <van-cell title="绩效基数" :value="moneyText(salary.current.performanceBase)" />
            <van-cell
              v-for="item in salary.current.allowances"
              :key="item.key || item.name"
              :title="item.name"
              :value="moneyText(item.amount)"
            />
            <div class="salary-total">
              <span class="list-item__meta">合计</span>
              <span class="salary-total__value tabular-nums">{{ moneyText(salary.current.totalSalary) }}</span>
            </div>
            <p class="tip tabular-nums">
              生效日期 {{ salary.current.effectiveDate }} · {{ salary.histories.length }} 条调薪留痕
            </p>
            <van-button class="salary-link" block plain type="primary" @click="router.push('/staff/payroll')"
              >查看我的工资单</van-button
            >
          </div>

          <div class="section-title">最近调薪</div>
          <div class="card">
            <div v-for="log in salary.histories.slice(0, 3)" :key="log.id" class="timeline__item">
              <span class="timeline__dot" aria-hidden="true" />
              <p class="timeline__time tabular-nums">{{ log.effectiveDate }} · {{ log.changeTypeLabel }}</p>
              <p class="timeline__text tabular-nums">
                合计 {{ moneyText(log.totalSalary) }} · 原因 {{ log.reason || '-' }}
              </p>
            </div>
            <p v-if="!salary.histories.length" class="tip">暂无调薪记录</p>
          </div>
        </template>

        <p class="tip">档案由人事维护，如有异议请联系人事；薪资构成与实发工资单的口径不同，以工资单为准</p>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.profile-hero {
  margin-top: var(--sp-3);
}

.salary-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-top: var(--sp-3);
  margin-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}

.salary-total__value {
  font-size: var(--fs-num-md);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-md);
  color: var(--text-1);
}

.salary-link {
  min-height: 44px;
  margin-top: var(--sp-3);
}
</style>
