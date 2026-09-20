<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { getHrProfile, getHrSalary, updateHrSalary } from '../../api/index.js'
import { moneyText, nextMonthFirstDay } from '../../utils/format.js'

/**
 * B8 员工档案与定薪（ADMIN 可写）
 *
 * 需求 8 的核心交互是「调薪」，三件事必须同时看得见：当前构成、调整后合计、历史留痕。
 * 因此不用弹层，改成一页（弹层里做金额对比会挤成两屏）。
 *
 * 降薪是唯一的危险动作：调整后合计 < 调整前时二次确认（B8.6），升薪不打扰。
 * 注意：契约里薪资标准只是派生模板，改档案不会影响已建档员工 —— 本页只改员工本人的定薪。
 */
const route = useRoute()
const employeeId = computed(() => Number(route.params.employeeId))

const loading = ref(true)
const error = ref('')
const profile = ref(null)
const salary = ref(null)

const form = ref({ basicSalary: 0, postSalary: 0, performanceBase: 0, effectiveDate: nextMonthFirstDay(), reason: '' })
const saving = ref(false)
const formError = ref('')

const resigned = computed(() => !!(profile.value && profile.value.leaveDate))
const allowancesTotal = computed(() => (salary.value ? salary.value.current.allowancesTotal : 0))
const currentTotal = computed(() => (salary.value ? salary.value.current.totalSalary : 0))
/** 调整后合计：津贴项不在移动端编辑（项名/金额多列，PC 更合适），沿用当前值参与合计 */
const nextTotal = computed(
  () =>
    Number(form.value.basicSalary || 0) +
    Number(form.value.postSalary || 0) +
    Number(form.value.performanceBase || 0) +
    Number(allowancesTotal.value || 0)
)
const diff = computed(() => nextTotal.value - currentTotal.value)
const diffText = computed(() => {
  if (!diff.value) return '与当前持平'
  return `${diff.value < 0 ? '降薪' : '涨薪'} ${moneyText(Math.abs(diff.value))}`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileData, salaryData] = await Promise.all([getHrProfile(employeeId.value), getHrSalary(employeeId.value)])
    profile.value = profileData
    salary.value = salaryData
    form.value = {
      basicSalary: salaryData.current.basicSalary,
      postSalary: salaryData.current.postSalary,
      performanceBase: salaryData.current.performanceBase,
      effectiveDate: nextMonthFirstDay(),
      reason: ''
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function validate() {
  for (const [key, label] of [
    ['basicSalary', '基本工资'],
    ['postSalary', '岗位工资'],
    ['performanceBase', '绩效基数']
  ]) {
    const value = Number(form.value[key])
    if (!Number.isFinite(value) || value < 0) return `${label}须为不小于 0 的数字`
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.value.effectiveDate)) return '生效日期格式须为 YYYY-MM-DD'
  const reason = form.value.reason.trim()
  if (reason.length < 2 || reason.length > 50) return '调整原因须为 2–50 字，会写入调薪留痕'
  return ''
}

async function submit() {
  const message = validate()
  if (message) {
    formError.value = message
    return
  }
  formError.value = ''
  if (diff.value < 0) {
    try {
      await showConfirmDialog({
        title: '降薪调整',
        message: `本次为降薪调整（${moneyText(diff.value)}），合计将从 ${moneyText(currentTotal.value)} 变为 ${moneyText(nextTotal.value)}，确认提交？`,
        confirmButtonText: '确认提交',
        cancelButtonText: '再想想'
      })
    } catch (e) {
      return
    }
  }
  saving.value = true
  try {
    const result = await updateHrSalary(employeeId.value, {
      basicSalary: Number(form.value.basicSalary),
      postSalary: Number(form.value.postSalary),
      performanceBase: Number(form.value.performanceBase),
      effectiveDate: form.value.effectiveDate,
      reason: form.value.reason.trim()
    })
    salary.value = result
    form.value.reason = ''
    showSuccessToast(`薪资已保存，${result.current.effectiveDate} 生效`)
  } catch (e) {
    // 9302 已离职 / 9305 无定薪档案：这两类都要说清「为什么不能改」，不能只给「操作失败」
    formError.value = e.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="hr-detail">
    <PageNav title="员工档案" />
    <div class="page page--loose">
      <PageState :loading="loading" :error="error" @retry="load">
        <section class="hero hero--deep profile-hero">
          <p class="hero__title">{{ profile.employeeName }}</p>
          <p class="hero__sub">
            {{ profile.stationName || '总部' }} · {{ profile.deptName || '未分配部门' }} · 工号 {{ profile.employeeId }}
          </p>
        </section>

        <div class="section-title">基本信息</div>
        <van-cell-group inset>
          <van-cell title="登录账号" :value="profile.username || '-'" />
          <van-cell title="手机号" :value="profile.phone || '-'" />
          <van-cell title="入职日期" :value="profile.entryDate || '-'" />
          <van-cell title="学历" :value="profile.educationLabel || '-'" />
          <van-cell title="在职状态" :value="resigned ? `已离职（${profile.leaveDate}）` : '在职'" />
        </van-cell-group>

        <div class="section-title">合同与试用</div>
        <van-cell-group inset>
          <van-cell title="合同类型" :value="profile.contractTypeLabel || '-'" />
          <van-cell
            title="合同期限"
            :value="
              profile.contractStart && profile.contractEnd
                ? `${profile.contractStart} ~ ${profile.contractEnd}`
                : '无固定期限'
            "
          />
          <van-cell title="试用期" :value="`${profile.probationMonths} 个月`" />
          <van-cell title="转正日期" :value="profile.regularDate || '-'" />
          <van-cell title="社保基数" :value="moneyText(profile.socialSecurityBase)" />
        </van-cell-group>

        <div class="section-title">紧急联系人与银行卡</div>
        <van-cell-group inset>
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
          <!-- 契约出参已脱敏（maskBankAccount）；此处只做展示，不再拼回完整卡号 -->
          <van-cell title="银行卡号" :value="profile.bankAccount || '-'" />
        </van-cell-group>

        <template v-if="salary">
          <div class="section-title">
            调薪调整<span class="section-title__extra">当前合计 {{ moneyText(currentTotal) }}</span>
          </div>
          <div class="card">
            <van-notice-bar
              v-if="resigned"
              class="notice"
              left-icon="info-o"
              wrapable
              text="已离职员工不可调整薪资，以下表单只读"
              color="var(--color-info-text)"
              background="var(--color-info-surface)"
            />

            <van-field
              v-model="form.basicSalary"
              type="number"
              label="基本工资"
              input-align="right"
              :readonly="resigned"
            />
            <van-field
              v-model="form.postSalary"
              type="number"
              label="岗位工资"
              input-align="right"
              :readonly="resigned"
            />
            <van-field
              v-model="form.performanceBase"
              type="number"
              label="绩效基数"
              input-align="right"
              :readonly="resigned"
            />
            <van-cell title="津贴合计" :value="moneyText(allowancesTotal)" />
            <van-field
              v-model="form.effectiveDate"
              label="生效日期"
              placeholder="YYYY-MM-DD"
              input-align="right"
              :readonly="resigned"
            />
            <van-field
              v-model="form.reason"
              type="textarea"
              rows="2"
              maxlength="50"
              show-word-limit
              label="调整原因"
              placeholder="2–50 字，例如：转正调薪"
              :readonly="resigned"
            />

            <p class="diff-line tabular-nums">
              调整后合计 {{ moneyText(nextTotal) }} ·
              <span :class="diff < 0 ? 'diff-line--down' : diff > 0 ? 'diff-line--up' : ''">{{ diffText }}</span>
            </p>
            <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

            <van-button class="submit" block type="primary" :disabled="resigned" :loading="saving" @click="submit">
              {{ diff < 0 ? '提交降薪调整' : '保存薪资' }}
            </van-button>
            <p class="tip">此处的合计只是标准薪资；实发金额以财务模块的工资单为准，两处口径不互相覆盖</p>
          </div>

          <div class="section-title">
            调薪留痕<span class="section-title__extra">最近 {{ salary.histories.length }} 条</span>
          </div>
          <div class="card">
            <div v-for="log in salary.histories" :key="log.id" class="timeline__item">
              <span
                class="timeline__dot"
                :class="log.changeType === 'ENTRY' ? '' : 'timeline__dot--success'"
                aria-hidden="true"
              />
              <p class="timeline__time tabular-nums">
                {{ log.effectiveDate }} · {{ log.changeTypeLabel }} · {{ log.operatorName || '-' }}
              </p>
              <p class="timeline__text tabular-nums">
                {{ moneyText(log.basicSalary) }} + {{ moneyText(log.postSalary) }} +
                {{ moneyText(log.performanceBase) }} + 津贴 {{ moneyText(log.allowancesTotal) }} =
                <strong>{{ moneyText(log.totalSalary) }}</strong>
              </p>
              <p class="timeline__time">原因：{{ log.reason || '-' }}</p>
            </div>
            <p v-if="!salary.histories.length" class="tip">暂无调薪记录</p>
          </div>
        </template>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.profile-hero {
  margin-top: var(--sp-3);
}

.diff-line {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.diff-line--down {
  color: var(--color-danger);
}

.diff-line--up {
  color: var(--color-success);
}

.form-error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.submit {
  min-height: 44px;
  margin-top: var(--sp-3);
}
</style>
