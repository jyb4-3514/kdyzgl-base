<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import BossMetricDelta from '../components/BossMetricDelta.vue'
import { bossConfirm } from '../components/bossConfirm.js'
import { getHrProfile, getHrSalary, updateHrSalary } from '@/api/hr.js'
import { moneyText, nextMonthFirstDay } from '@/utils/format.js'

/**
 * B8 员工档案与定薪（ADMIN 可写）
 *
 * 需求 8 的核心交互是「调薪」，三件事必须同时看得见：当前构成、调整后合计、历史留痕。
 * 因此不用弹层，改成一页（弹层里做金额对比会挤成两屏）。
 *
 * 降薪是唯一的危险动作：调整后合计 < 调整前时二次确认（B8.6），升薪不打扰。
 * 注意：契约里薪资标准只是派生模板，改档案不会影响已建档员工 —— 本页只改员工本人的定薪。
 *
 * 津贴明细在本页可增删改（数据口径与校验以 PC SalaryEditorDrawer 为准）：
 * 合计与差额一律由 form.allowances 求和派生，且在提交时**必传 allowances**，
 * 否则后端/Mock 缺省即保留原值，出现「界面有反馈、津贴没落库」的静默失败。
 */
const route = useRoute()
const employeeId = computed(() => Number(route.params.employeeId))

const loading = ref(true)
const error = ref('')
const profile = ref(null)
const salary = ref(null)

const form = ref({
  basicSalary: 0,
  postSalary: 0,
  performanceBase: 0,
  allowances: [],
  effectiveDate: nextMonthFirstDay(),
  reason: ''
})
const saving = ref(false)
const formError = ref('')

const resigned = computed(() => !!(profile.value && profile.value.leaveDate))
/**
 * 津贴合计：编辑态由 form.allowances 求和派生（否则改金额时合计纹丝不动）；
 * 离职只读态直接取当前档案值，与明细和等价，避免两处取值分叉。
 */
const allowancesTotal = computed(() => {
  if (resigned.value) return salary.value ? Number(salary.value.current.allowancesTotal || 0) : 0
  return form.value.allowances.reduce((sum, item) => sum + Number(item.amount || 0), 0)
})
const currentTotal = computed(() => (salary.value ? salary.value.current.totalSalary : 0))
/** 调整后合计：三项工资 + 派生津贴合计（津贴明细在本页可编辑） */
const nextTotal = computed(
  () =>
    Number(form.value.basicSalary || 0) +
    Number(form.value.postSalary || 0) +
    Number(form.value.performanceBase || 0) +
    Number(allowancesTotal.value || 0)
)
const diff = computed(() => nextTotal.value - currentTotal.value)

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
      // 深拷贝预填：编辑态改副本，未提交不影响 salary.current
      allowances: (salaryData.current.allowances || []).map((item) => ({ ...item })),
      effectiveDate: nextMonthFirstDay(),
      reason: ''
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 金额校验：非负整数且整数部分 ≤6 位（取 PC 客户端加固口径，比契约「非负数字」更严） */
function amountError(value, label) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) return `${label}须为不小于 0 的数字`
  if (!Number.isInteger(num)) return `${label}须为整数金额`
  if (String(Math.trunc(num)).length > 6) return `${label}数额过大，请核对`
  return ''
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
  // 津贴逐行校验：名称留空统一收口到提交时（先填金额再补名称是常见顺序）；短路顺序与 PC formError 一致
  for (const item of form.value.allowances) {
    if (!String(item.name || '').trim()) return '津贴项名称不可为空'
    const err = amountError(item.amount, `津贴「${item.name}」`)
    if (err) return err
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.value.effectiveDate)) return '生效日期格式须为 YYYY-MM-DD'
  const reason = form.value.reason.trim()
  if (reason.length < 2 || reason.length > 50) return '调整原因须为 2–50 字，会写入调薪留痕'
  return ''
}

const nameFieldRefs = ref([])
function setNameRef(el, index) {
  nameFieldRefs.value[index] = el
}

/** 新增行：金额用空串而非 PC 的 0，靠 placeholder 引导输入；提交映射 Number('' || 0) = 0，落库口径与 PC 一致 */
async function addAllowance() {
  if (resigned.value) return
  form.value.allowances.push({ key: '', name: '', amount: '' })
  await nextTick()
  const last = nameFieldRefs.value[form.value.allowances.length - 1]
  if (last) {
    if (last.$el && typeof last.$el.scrollIntoView === 'function') last.$el.scrollIntoView({ block: 'nearest' })
    last.focus?.()
  }
}

/** 金额错误只在失焦后落到行下（边输边报红会误伤「先填金额再补名称」的正常顺序）；只读态不报错 */
function onAmountBlur(item) {
  if (resigned.value) return
  item.error = amountError(item.amount, `津贴「${item.name}」`)
}

/**
 * 删除：有内容的行须 1 次确认（移动端误触率高）；空行无数据丢失直接删，避免高频小范围操作也弹窗。
 * 不置 irreversible：本轮改动在提交前仍是本地态，真正不可逆的是「提交」（已由降薪确认覆盖）。
 */
async function removeAllowance(index) {
  if (resigned.value) return
  const item = form.value.allowances[index]
  const hasContent = !!String(item.name || '').trim() || Number(item.amount || 0) !== 0
  if (hasContent) {
    const confirmed = await bossConfirm({
      action: '移除津贴项',
      target: `「${item.name}」`,
      impact: '该项不再计入调整后合计',
      confirmText: '删除该津贴'
    })
    if (!confirmed) return
  }
  form.value.allowances.splice(index, 1)
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
      // 必传 allowances：缺省时后端/Mock 保留原值，会出现「界面有反馈、津贴没落库」的静默失败
      allowances: form.value.allowances.map((item) => ({
        key: item.key || null,
        name: String(item.name).trim(),
        amount: Number(item.amount || 0)
      })),
      effectiveDate: form.value.effectiveDate,
      reason: form.value.reason.trim()
    })
    salary.value = result
    // 以服务端回参重填，顺带清掉行上的失焦错误等本地态
    form.value.allowances = (result.current.allowances || []).map((item) => ({ ...item }))
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
            <!-- 行头合计随明细实时更新；tabular-nums 让数字等宽，输入过程中宽度不跳 -->
            <van-cell class="tabular-nums" title="津贴合计" :value="moneyText(allowancesTotal)" />
            <!-- 津贴明细：与三项工资同卡片同左边界，靠行分隔线区分行，不新开卡片、不折叠 -->
            <div class="allowance-rows" role="group" aria-label="津贴明细">
              <div
                v-for="(item, index) in form.allowances"
                :key="index"
                class="allowance-row"
                role="group"
                :aria-label="`津贴项 ${index + 1}：${item.name || '未命名'}`"
              >
                <label class="visually-hidden" :for="`allowance-name-${employeeId}-${index}`">津贴名称</label>
                <van-field
                  :id="`allowance-name-${employeeId}-${index}`"
                  :ref="(el) => setNameRef(el, index)"
                  v-model="item.name"
                  :border="false"
                  maxlength="20"
                  placeholder="津贴名称"
                  :readonly="resigned"
                />
                <label class="visually-hidden" :for="`allowance-amount-${employeeId}-${index}`">津贴金额</label>
                <van-field
                  :id="`allowance-amount-${employeeId}-${index}`"
                  v-model="item.amount"
                  :border="false"
                  type="number"
                  inputmode="numeric"
                  input-align="right"
                  placeholder="金额"
                  :readonly="resigned"
                  @blur="onAmountBlur(item)"
                />
                <van-button
                  class="allowance-del"
                  size="small"
                  plain
                  type="danger"
                  :disabled="resigned"
                  :aria-label="`删除津贴项 ${item.name || '第 ' + (index + 1) + ' 项'}`"
                  @click="removeAllowance(index)"
                >
                  <template #icon><van-icon name="cross" aria-hidden="true" /></template>
                </van-button>
                <p v-if="item.error" class="allowance-error" role="alert">{{ item.error }}</p>
              </div>
            </div>
            <p v-if="!form.allowances.length" class="tip">暂无津贴项，合计 = 基本工资 + 岗位工资 + 绩效基数</p>
            <van-button
              class="allowance-add"
              size="small"
              plain
              type="primary"
              block
              :disabled="resigned"
              @click="addAllowance"
            >
              <template #icon><van-icon name="plus" aria-hidden="true" /></template>
              添加津贴
            </van-button>
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

            <!-- 差额徽标收口到 BossMetricDelta（N-03）：符号 + 文字 + 颜色三通道，零差额不渲染徽标 -->
            <p class="diff-line tabular-nums">
              调整后合计 {{ moneyText(nextTotal) }} ·
              <BossMetricDelta v-if="diff" :value="diff" mode="money" />
              <span v-else>与当前持平</span>
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

.allowance-rows {
  margin-top: var(--sp-2);
}

.allowance-row {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr) var(--touch-min);
  gap: var(--sp-2);
  align-items: center;
  min-height: var(--row-h-1);
}

/* 分隔线只出现在行与行之间：首行不画线，行头 van-cell 自带底线，避免双线 */
.allowance-row + .allowance-row {
  border-top: 1px solid var(--border-line);
}

/* 列内 van-field 必须收掉自身水平内边距：否则两列各带 16px，320px 下可输区不足 44px（规范 §1.2） */
.allowance-row .van-field {
  min-height: var(--touch-min);
  padding: 0;
}

/* 金额错误落在本行下方：跨满 3 列，不挤占名称/金额列，也不打断行分隔线的相邻选择器 */
.allowance-error {
  grid-column: 1 / -1;
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.allowance-del {
  width: var(--touch-min);
  height: var(--touch-min);
  padding: 0;
  touch-action: manipulation;
}

.allowance-add {
  min-height: var(--touch-min);
  margin-top: var(--sp-2);
  touch-action: manipulation;
}

/* 即时点击反馈（规范 §7 #12）：plain 按钮默认无底色，按下补一层浅底 */
.allowance-del:active,
.allowance-add:active {
  background-color: var(--surface-subtle);
}
</style>
