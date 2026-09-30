<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import { bossConfirm } from '../components/bossConfirm.js'
import { createEmployee, getEmployees, getStationList, resetEmployeePassword, updateEmployee, updateEmployeeStatus } from '@/api/org.js'
import { ASSIGNABLE_ROLE_OPTIONS } from '@/constants/accounts.js'
import { useAuthStore } from '@/stores/auth.js'

/**
 * 新增 / 编辑账号表单（共用一页）· 设计 ④.5
 *
 * 口令红线（本页不含任何真实口令）：
 * - 新增：管理员在表单内「设置」初始口令；提交 toast「账号已创建，首次登录须修改口令」；
 *   不回显、不提供「复制口令」按钮、不写入任何日志/备注/页面。
 * - 编辑：**无口令字段**，改口令走独立动作「重置口令」（二次确认 + 弹层输入，不回显明文）。
 * 自我保护 2001（改自己角色/状态/重置口令）与最后管理员 2002 由服务端裁决，前端就地回显。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const stationId = computed(() => Number(route.params.id))
const employeeId = computed(() => (route.params.employeeId ? Number(route.params.employeeId) : null))
const isEdit = computed(() => employeeId.value != null)
const ROUTE_BASE = '/boss/station'

const loading = ref(true)
const error = ref('')
const saved = ref(null)
const station = ref(null)

const form = ref({
  username: '',
  password: '',
  realName: '',
  phone: '',
  role: 'STAFF',
  entryDate: '',
  remark: ''
})
const showPwd = ref(false)
const saving = ref(false)
const formError = ref('')
const nameError = ref('')
const phoneError = ref('')
const usernameError = ref('')
const passwordError = ref('')
const entryDateError = ref('')

const showReset = ref(false)
const newPassword = ref('')
const resetError = ref('')
const resetting = ref(false)

/** 自我保护：编辑自己时角色/状态/重置口令禁用（不隐藏），与契约 2001 同源 */
const isSelf = computed(() => !!saved.value && saved.value.id === auth.user.id)
const resetDisabled = computed(() => isSelf.value)

const statusValue = computed(() => (saved.value ? saved.value.status : 1))

const actions = computed(() => {
  if (isEdit.value) {
    return [
      { key: 'save', label: '保存', plain: false, loading: saving.value },
      { key: 'reset', label: '重置口令', type: 'danger', disabled: resetDisabled.value }
    ]
  }
  return [{ key: 'save', label: '创建账号', plain: false, loading: saving.value }]
})

const barNote = computed(() => (isSelf.value ? '不能在此修改自己的角色/状态；如需变更请联系其他管理员' : ''))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const stations = await getStationList()
    const hitStation = stations.find((item) => item.id === stationId.value)
    if (!hitStation) {
      error.value = '驿站不存在，请刷新后重试'
      return
    }
    station.value = hitStation

    if (isEdit.value) {
      const page = await getEmployees({ stationId: stationId.value, pageNum: 1, pageSize: 100 })
      const hit = (page.list || []).find((item) => item.id === employeeId.value)
      if (!hit) {
        error.value = '账号不存在，请刷新后重试'
        return
      }
      saved.value = hit
      form.value = {
        username: hit.username || '',
        password: '',
        realName: hit.realName || '',
        phone: hit.phone || '',
        role: hit.role || 'STAFF',
        entryDate: hit.entryDate || '',
        remark: hit.remark || ''
      }
    } else {
      // 身份由入口预置（员工=STAFF / 站长=STATION_ADMIN）；非法值回落到 STAFF。
      // 口径：站长只是员工账号上的身份，非独立账号体系，故与员工共用本表单
      const preset = String(route.query.role || '')
      form.value.role = ASSIGNABLE_ROLE_OPTIONS.some((item) => item.value === preset) ? preset : 'STAFF'
    }
  } catch (e) {
    error.value = e.message || '账号信息加载失败'
  } finally {
    loading.value = false
  }
}

function validateRealName() {
  const len = form.value.realName.trim().length
  nameError.value = len >= 1 && len <= 50 ? '' : '请输入 1–50 字真实姓名'
  return !nameError.value
}

function validatePhone() {
  phoneError.value = /^1[3-9]\d{9}$/.test(form.value.phone.trim()) ? '' : '请输入正确的 11 位手机号'
  return !phoneError.value
}

function validateUsername() {
  if (isEdit.value) return true
  usernameError.value = /^[a-zA-Z][a-zA-Z0-9_]{3,29}$/.test(form.value.username.trim())
    ? ''
    : '登录账号须为 4–30 位，以字母开头，仅含字母/数字/下划线'
  return !usernameError.value
}

function validatePassword() {
  if (isEdit.value) return true
  passwordError.value = /^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/.test(form.value.password)
    ? ''
    : '初始口令须为 8–20 位且同时包含字母和数字'
  return !passwordError.value
}

function validateEntryDate() {
  const value = form.value.entryDate.trim()
  entryDateError.value = !value || /^\d{4}-\d{2}-\d{2}$/.test(value) ? '' : '入职日期格式须为 yyyy-MM-dd'
  return !entryDateError.value
}

async function onSubmit() {
  if (saving.value) return
  const ok = validateRealName() && validatePhone() && validateUsername() && validatePassword() && validateEntryDate()
  if (!ok) return
  formError.value = ''
  saving.value = true
  try {
    if (isEdit.value) {
      await updateEmployee(employeeId.value, {
        realName: form.value.realName.trim(),
        phone: form.value.phone.trim(),
        role: form.value.role,
        stationId: stationId.value,
        entryDate: form.value.entryDate.trim() || null,
        remark: form.value.remark.trim() || null
      })
      showSuccessToast('账号信息已保存')
      router.replace(`${ROUTE_BASE}/${stationId.value}`)
    } else {
      await createEmployee({
        username: form.value.username.trim(),
        password: form.value.password,
        realName: form.value.realName.trim(),
        phone: form.value.phone.trim(),
        role: form.value.role,
        stationId: stationId.value,
        entryDate: form.value.entryDate.trim() || null,
        remark: form.value.remark.trim() || null
      })
      // 不回显口令；仅提示首登强制改密
      showSuccessToast('账号已创建，首次登录须修改口令')
      router.replace(`${ROUTE_BASE}/${stationId.value}`)
    }
  } catch (e) {
    // 1003 账号已占用 / 2003 手机号已占用 / 2001 自我保护 / 2002 最后管理员：逐项落到就近字段或页面级
    if (e.code === 1003) usernameError.value = e.message || '该账号已被使用'
    else if (e.code === 2003) phoneError.value = e.message || '该手机号已被使用'
    else if (e.code === 4004) formError.value = e.message || '该驿站已停用，无法归属员工'
    else formError.value = e.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

async function onToggleStatus(next) {
  if (!saved.value || isSelf.value) return
  const nextStatus = next ? 1 : 0
  if (nextStatus === 0) {
    const ok = await bossConfirm({
      action: '停用账号',
      target: `${saved.value.realName}（${saved.value.username}）`,
      impact: '该账号现有登录会话将立即失效，需管理员重新启用后才能登录',
      confirmText: '确认停用'
    })
    if (!ok) return
  }
  try {
    await updateEmployeeStatus(saved.value.id, nextStatus)
    saved.value = { ...saved.value, status: nextStatus }
    showSuccessToast(nextStatus === 1 ? '账号已启用' : '账号已停用')
  } catch (e) {
    formError.value = e.message || '状态更新失败'
  }
}

function openReset() {
  if (resetDisabled.value) return
  showReset.value = true
  newPassword.value = ''
  resetError.value = ''
}

async function onResetConfirm() {
  if (resetting.value) return
  if (!/^(?=.*[A-Za-z])(?=.*\d)\S{8,20}$/.test(newPassword.value)) {
    resetError.value = '新口令须为 8–20 位且同时包含字母和数字'
    return
  }
  const ok = await bossConfirm({
    action: '重置登录口令',
    target: `${saved.value.realName}（${saved.value.username}）`,
    impact: '该账号现有登录会话将立即失效，需用新口令重新登录，且首次登录后仍强制改密',
    irreversible: true,
    confirmText: '确认重置'
  })
  if (!ok) return
  resetting.value = true
  resetError.value = ''
  try {
    await resetEmployeePassword(saved.value.id, newPassword.value)
    showReset.value = false
    newPassword.value = ''
    showSuccessToast('口令已重置，该账号需重新登录')
  } catch (e) {
    if (e.code === 2001) resetError.value = '不能重置自己的口令，请走「我的 → 修改密码」'
    else resetError.value = e.message || '重置失败，请稍后重试'
  } finally {
    resetting.value = false
  }
}

function onAction(key) {
  if (key === 'save') onSubmit()
  else if (key === 'reset') openReset()
}

onMounted(load)
</script>

<template>
  <div class="account-form">
    <PageNav :title="isEdit ? '账号编辑' : '新增账号'" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="4" @retry="load">
        <template v-if="station">
          <van-notice-bar
            v-if="station.status !== 1"
            class="notice"
            left-icon="warning-o"
            text="该驿站已停用，新增账号无法归属本驿站。请先启用驿站。"
            wrapable
            color="var(--color-warning)"
            background="var(--color-warning-surface)"
          />

          <div class="section-title">账号信息</div>
          <div class="card">
            <van-field
              v-model="form.realName"
              label="姓名"
              placeholder="1–50 字真实姓名"
              maxlength="50"
              aria-label="姓名"
              @blur="validateRealName"
            />
            <p v-if="nameError" class="field-error" role="alert">{{ nameError }}</p>

            <van-field
              v-model="form.phone"
              type="tel"
              inputmode="numeric"
              label="手机号"
              placeholder="11 位手机号"
              maxlength="11"
              aria-label="手机号"
              @blur="validatePhone"
            />
            <p v-if="phoneError" class="field-error" role="alert">{{ phoneError }}</p>

            <!-- 登录账号：新增必填；编辑只读展示（契约编辑无 username） -->
            <van-field
              v-if="isEdit"
              :model-value="form.username"
              label="登录账号"
              readonly
              aria-label="登录账号（不可修改）"
            />
            <van-field
              v-else
              v-model="form.username"
              label="登录账号"
              placeholder="4–30 位，字母开头"
              maxlength="30"
              aria-label="登录账号"
              @blur="validateUsername"
            />
            <p v-if="usernameError" class="field-error" role="alert">{{ usernameError }}</p>
            <p v-if="isEdit" class="field-hint">登录账号创建后不可修改</p>

            <!-- 初始口令：仅新增出现；编辑无口令字段，改口令走「重置口令」 -->
            <template v-if="!isEdit">
              <van-field
                v-model="form.password"
                :type="showPwd ? 'text' : 'password'"
                label="初始口令"
                placeholder="8–20 位，含字母与数字"
                maxlength="20"
                aria-label="初始口令"
                @blur="validatePassword"
              >
                <template #right-icon>
                  <button
                    type="button"
                    class="pwd-toggle"
                    :aria-label="showPwd ? '隐藏口令' : '显示口令'"
                    @click="showPwd = !showPwd"
                  >
                    <van-icon :name="showPwd ? 'closed-eye' : 'eye-o'" aria-hidden="true" />
                  </button>
                </template>
              </van-field>
              <p v-if="passwordError" class="field-error" role="alert">{{ passwordError }}</p>
              <p class="field-hint">
                该口令仅用于账号首次登录，首次登录后系统强制要求修改；请通过线下安全渠道告知本人。
              </p>
            </template>

            <!-- 身份：员工账号上的角色标记（员工 / 站长），非独立账号体系。新增按入口预置；编辑可改（受 2001 / 2002 保护） -->
            <van-field label="身份" aria-label="选择身份">
              <template #input>
                <div class="role-chips" role="group" aria-label="选择身份">
                  <button
                    v-for="item in ASSIGNABLE_ROLE_OPTIONS"
                    :key="item.value"
                    type="button"
                    class="role-chip"
                    :class="{ 'role-chip--active': form.role === item.value }"
                    :disabled="isSelf"
                    :aria-pressed="form.role === item.value"
                    @click="form.role = item.value"
                  >
                    {{ item.label }}
                  </button>
                </div>
              </template>
            </van-field>
            <p class="field-hint">
              身份是员工账号上的角色标记（站长与员工共用同一套账号体系）；选「站长」须归属启用驿站，本页已锁定当前驿站。
            </p>

            <van-field label="驿站归属" :model-value="station.stationName" readonly aria-label="驿站归属（锁定当前驿站）" />

            <van-field
              v-model="form.entryDate"
              label="入职日期"
              placeholder="yyyy-MM-dd，可空"
              maxlength="10"
              aria-label="入职日期"
              @blur="validateEntryDate"
            />
            <p v-if="entryDateError" class="field-error" role="alert">{{ entryDateError }}</p>

            <van-field
              v-model="form.remark"
              type="textarea"
              rows="2"
              maxlength="255"
              show-word-limit
              label="备注"
              placeholder="可空，≤255 字符"
            />

            <!-- 状态开关仅编辑态出现（新增固定启用） -->
            <div v-if="isEdit" class="switch-row">
              <div class="switch-row__text">
                <p class="switch-row__label">账号状态</p>
                <p class="switch-row__hint">停用后该账号立即下线，需重新启用才能登录</p>
              </div>
              <van-switch
                :model-value="statusValue === 1"
                :disabled="isSelf"
                aria-label="账号启用状态"
                @update:model-value="onToggleStatus"
              />
            </div>
          </div>

          <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
        </template>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="barNote" :submitting="saving" @select="onAction" />

    <!-- 重置口令弹层：不回显明文 -->
    <van-popup v-model:show="showReset" round position="bottom" safe-area-inset-bottom>
      <div class="reset-pop">
        <div class="reset-pop__title">重置登录口令</div>
        <p class="reset-pop__sub">该账号现有登录会话将立即失效，需用新口令重新登录，且首次登录后仍强制改密。</p>
        <van-field
          v-model="newPassword"
          type="password"
          label="新口令"
          placeholder="8–20 位，含字母与数字"
          maxlength="20"
        />
        <p v-if="resetError" class="field-error" role="alert">{{ resetError }}</p>
        <div class="reset-pop__foot">
          <van-button block type="danger" :loading="resetting" @click="onResetConfirm">确认重置</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.notice {
  margin-bottom: var(--sp-2);
}

.field-error {
  margin: 0;
  padding: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.field-hint {
  margin: 0;
  padding: var(--sp-1) var(--sp-4) var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.form-error {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.role-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.role-chip {
  display: inline-flex;
  align-items: center;
  min-height: var(--touch-min);
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.role-chip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.role-chip:disabled {
  color: var(--text-disabled);
}

.pwd-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--touch-min);
  min-height: var(--touch-min);
  color: var(--text-3);
  background: none;
  border: none;
}

.switch-row {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  justify-content: space-between;
  min-height: var(--touch-min);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}

.switch-row__text {
  flex: 1;
  min-width: 0;
}

.switch-row__label {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.switch-row__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.reset-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.reset-pop__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.reset-pop__sub {
  padding: 0 var(--sp-4);
  margin: var(--sp-1) 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.reset-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
