<script setup>
import { computed, nextTick, onUnmounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import StationPicker from '@kdyzgl/shared/ui/StationPicker.vue'
import { sendRegisterSms } from '../../api/auth.js'
import { submitRegistration } from '../../api/registration.js'
import { REGISTRATION_AGREEMENT_VERSION, REGISTRATION_STATIONS } from '../../constants/registration.js'
import { maskPhone } from '@kdyzgl/shared/domain/mask.js'
import { copyText } from '@kdyzgl/shared/domain/text.js'
import { readDeviceId } from '@kdyzgl/shared/device.js'

/**
 * 员工自助注册页（registration-ui-design.md §3–§7）
 *
 * 两条红线：
 * 1. 存在性不泄露（M-2）：发码与提交对「手机号是否存在」响应恒定，全页无存在性分支文案，
 *    服务端任何可能揭示存在性的错误码统一映射为同一条中性兜底句。
 * 2. 不承诺即时可用（§11.8）：结果页如实说明「人工审批 → 管理员发口令 → 首登改密」，无「查看进度」按钮。
 *
 * 字段严格取白名单 6 项（表 A 去掉 Q2 裁定移除的 password）：姓名 / 手机号 / 验证码 / 意向驿站 / 意向岗位 / 条款。
 */
const CLIENT_TYPE = 'STAFF'
const PHONE_RE = /^1[3-9]\d{9}$/
const isPhone = (value) => PHONE_RE.test(String(value || '').trim())

const router = useRouter()

const form = reactive({ realName: '', phone: '', smsCode: '', intentStationId: null, intentPosition: '' })
const agree = ref(false)
const touched = reactive({ realName: false, phone: false, smsCode: false, intentStationId: false, intentPosition: false })
const fieldErrors = reactive({ realName: '', phone: '', smsCode: '', intentStationId: '', intentPosition: '' })
const agreementError = ref('')
/** 提交过一次后，未失焦过的字段也一并展示错误（提交前整体校验，§4） */
const attempted = ref(false)
const formError = ref('')

const stationPickerVisible = ref(false)
const smsSending = ref(false)
const smsError = ref('')
const smsSentTip = ref('')
const submitting = ref(false)
const submitted = ref(false)
const result = ref(null)
const copying = ref(false)

const realNameRef = ref(null)
const phoneRef = ref(null)
const codeRef = ref(null)

const stationName = computed(() => {
  const hit = REGISTRATION_STATIONS.find((item) => item.id === form.intentStationId)
  return hit ? hit.stationName : ''
})

/** 60s 发码倒计时（与登录页同一交互语义；倒计时用 --text-3，剩余秒数是有效信息，§5.1） */
function createCountdown(seconds = 60) {
  const left = ref(0)
  let timer = null
  const stop = () => {
    if (timer) clearInterval(timer)
    timer = null
    left.value = 0
  }
  const start = (from = seconds) => {
    stop()
    left.value = from
    timer = setInterval(() => {
      left.value -= 1
      if (left.value <= 0) stop()
    }, 1000)
  }
  onUnmounted(stop)
  return { left, start, stop }
}
const countdown = createCountdown(60)

const codeText = computed(() => {
  if (smsSending.value) return '发送中…'
  if (countdown.left.value > 0) return `重新获取（${countdown.left.value}s）`
  return '获取验证码'
})
const codeDisabled = computed(() => smsSending.value || submitting.value || countdown.left.value > 0)

/** 已填内容二次确认：返回（含壳内返回键）前拦截，避免误触丢单（§7） */
const isDirty = computed(
  () =>
    !!form.realName ||
    !!form.phone ||
    !!form.smsCode ||
    !!form.intentStationId ||
    !!form.intentPosition ||
    agree.value
)

function validateField(key) {
  if (key === 'realName') {
    const value = form.realName.trim()
    fieldErrors.realName = value.length >= 2 && value.length <= 20 ? '' : '请输入 2–20 字真实姓名'
  }
  if (key === 'phone') fieldErrors.phone = isPhone(form.phone) ? '' : '请输入正确的 11 位手机号'
  if (key === 'smsCode') fieldErrors.smsCode = /^\d{6}$/.test(form.smsCode.trim()) ? '' : '请输入 6 位数字验证码'
  if (key === 'intentStationId') fieldErrors.intentStationId = form.intentStationId ? '' : '请选择意向驿站'
  if (key === 'intentPosition') {
    fieldErrors.intentPosition = form.intentPosition.trim().length > 50 ? '意向岗位不超过 50 字' : ''
  }
}

/** 错误就地展示的条件：该字段已失焦过，或用户已点过提交（未点过就报错会打断输入） */
function errOf(key) {
  return touched[key] || attempted.value ? fieldErrors[key] : ''
}

function focusField(key) {
  const map = { realName: realNameRef, phone: phoneRef, smsCode: codeRef }
  const target = map[key]
  if (target && target.value && typeof target.value.focus === 'function') {
    try {
      target.value.focus()
    } catch (e) {
      // 聚焦失败不影响提交结果，静默降级
    }
  }
}

async function focusFirstError(key) {
  await nextTick()
  focusField(key)
}

async function onSendCode() {
  if (codeDisabled.value) return
  smsError.value = ''
  smsSentTip.value = ''
  if (!isPhone(form.phone)) {
    touched.phone = true
    validateField('phone')
    return
  }
  smsSending.value = true
  try {
    const res = await sendRegisterSms({
      phone: form.phone.trim(),
      clientType: CLIENT_TYPE,
      deviceId: readDeviceId()
    })
    // 发码成功文案对任意手机号完全一致，不按存在性分支（M-2）
    smsSentTip.value = `验证码已发送至 ${maskPhone(form.phone.trim())}`
    countdown.start(res && res.nextAllowedIn ? res.nextAllowedIn : 60)
  } catch (error) {
    const code = error && error.code
    if (code === 1101) {
      smsError.value = '验证码发送过于频繁，请稍后再试'
      // 频控命中按服务端建议间隔覆盖本地倒计时（§5.1）
      countdown.start(60)
    } else if (code === 1105 || code === 1106) {
      smsError.value = '短信服务暂不可用，请稍后重试'
    } else if (code === 429) {
      smsError.value = '操作过于频繁，请稍后再试'
    } else if (typeof code !== 'number') {
      smsError.value = '网络异常，请检查网络后重试'
    } else {
      smsError.value = '短信服务暂不可用，请稍后重试'
    }
  } finally {
    smsSending.value = false
  }
}

function onBlur(key) {
  touched[key] = true
  validateField(key)
}

function onPickStation(id) {
  form.intentStationId = id
  touched.intentStationId = true
  validateField('intentStationId')
}

async function onSubmit() {
  if (submitting.value) return
  attempted.value = true
  ;['realName', 'phone', 'smsCode', 'intentStationId', 'intentPosition'].forEach(validateField)
  agreementError.value = agree.value ? '' : '请先阅读并同意《服务条款》与《隐私与安全说明》'

  const firstError = ['realName', 'phone', 'smsCode', 'intentStationId', 'intentPosition'].find((key) => fieldErrors[key])
  if (firstError) {
    focusFirstError(firstError)
    return
  }
  if (agreementError.value) return

  submitting.value = true
  formError.value = ''
  try {
    const payload = {
      realName: form.realName.trim(),
      phone: form.phone.trim(),
      smsCode: form.smsCode.trim(),
      intentStationId: form.intentStationId,
      agreementVersion: REGISTRATION_AGREEMENT_VERSION
    }
    const position = form.intentPosition.trim()
    if (position) payload.intentPosition = position

    const data = await submitRegistration(payload)
    result.value = { applyNo: data.applyNo, createTime: data.createTime }
    submitted.value = true
    // 同路由内替换为结果页：清空表单状态，防止返回时残留已提交数据（§6.1）
    Object.assign(form, { realName: '', phone: '', smsCode: '', intentStationId: null, intentPosition: '' })
    agree.value = false
  } catch (error) {
    mapSubmitError(error)
  } finally {
    submitting.value = false
  }
}

/** 提交错误 → 文案（§7）：字段级就地渲染；凭据类 / 未知码统一中性兜底，不做存在性分支 */
function mapSubmitError(error) {
  const code = error && error.code
  if (typeof code !== 'number') {
    formError.value = '网络异常，请检查网络后重试'
    return
  }
  if (code === 9307) {
    formError.value = '该手机号已有进行中的入职申请，请勿重复提交'
    return
  }
  if (code === 1102) {
    touched.smsCode = true
    fieldErrors.smsCode = '验证码不正确或已过期，请重新获取'
    focusFirstError('smsCode')
    return
  }
  if (code === 1103) {
    touched.smsCode = true
    fieldErrors.smsCode = '验证码错误次数过多，请重新获取验证码'
    focusFirstError('smsCode')
    return
  }
  if (code === 4001 || code === 4004) {
    touched.intentStationId = true
    fieldErrors.intentStationId = '所选意向驿站暂不可选，请重新选择'
    return
  }
  if (code === 429) {
    formError.value = '操作过于频繁，请稍后再试'
    return
  }
  formError.value = '提交未成功，请稍后重试；如多次失败请联系管理员'
}

async function onCopy() {
  if (copying.value || !result.value) return
  copying.value = true
  try {
    const ok = await copyText(result.value.applyNo)
    if (ok) showSuccessToast('已复制')
    else showFailToast('复制失败，请长按选中文本手动复制')
  } finally {
    copying.value = false
  }
}

function backToLogin() {
  router.replace('/login')
}

onBeforeRouteLeave(async () => {
  if (submitted.value || !isDirty.value) return true
  try {
    await showConfirmDialog({ title: '确认返回', message: '已填写的内容将不会保存，确定返回？' })
    return true
  } catch (e) {
    return false
  }
})
</script>

<template>
  <div class="register">
    <!-- 结果页：同路由内替换，不承诺即时可用，无「查看进度」入口（§6.2） -->
    <div v-if="submitted" class="page page--loose register-result">
      <div class="register-result__icon" aria-hidden="true"><van-icon name="passed" /></div>
      <h2 class="register-result__title">申请已提交</h2>
      <div class="register-result__no-row">
        <span class="register-result__label">申请编号：</span>
        <span class="register-result__no tabular-nums">{{ result.applyNo }}</span>
        <button type="button" class="register-result__copy" aria-label="复制申请编号" :disabled="copying" @click="onCopy">
          <van-icon name="description-o" aria-hidden="true" />
        </button>
      </div>
      <div class="register-result__steps">
        <p class="register-result__steps-title">后续流程</p>
        <p class="register-result__step">① 管理员审核：人工审批，请耐心等待</p>
        <p class="register-result__step">② 审核通过后，由管理员发放初始口令</p>
        <p class="register-result__step">③ 首次登录需修改密码，之后即可正常使用</p>
      </div>
      <p class="register-result__note">审核进度由管理员线下告知</p>
      <div class="register-result__back">
        <van-button block plain type="primary" @click="backToLogin">返回登录</van-button>
      </div>
    </div>

    <template v-else>
      <PageNav title="员工注册" back-fallback="/login" />
      <div class="page page--bar">
        <p class="register__intro">
          提交后需管理员人工审核。审核通过后，账号由管理员发放初始口令，首次登录需修改密码。
        </p>

        <p v-if="formError" class="register__form-error" role="alert">{{ formError }}</p>

        <div class="card register__card">
          <h3 class="register__section">基本信息</h3>

          <van-field
            ref="realNameRef"
            v-model="form.realName"
            label="姓名"
            placeholder="请输入真实姓名"
            maxlength="20"
            autocomplete="name"
            :readonly="submitting"
            @blur="onBlur('realName')"
          />
          <p v-if="errOf('realName')" class="register__err" role="alert">{{ errOf('realName') }}</p>

          <van-field
            ref="phoneRef"
            v-model="form.phone"
            type="tel"
            inputmode="numeric"
            autocomplete="tel"
            label="手机号"
            placeholder="请输入 11 位手机号"
            maxlength="11"
            :readonly="submitting"
            @blur="onBlur('phone')"
          />
          <p v-if="errOf('phone')" class="register__err" role="alert">{{ errOf('phone') }}</p>

          <van-field
            ref="codeRef"
            v-model="form.smsCode"
            inputmode="numeric"
            autocomplete="one-time-code"
            label="验证码"
            placeholder="请输入 6 位验证码"
            maxlength="6"
            :readonly="submitting"
            @blur="onBlur('smsCode')"
          >
            <template #button>
              <button
                type="button"
                class="register__code-btn"
                :class="{ 'register__code-btn--counting': countdown.left.value > 0 && !smsSending }"
                :disabled="codeDisabled"
                :aria-disabled="codeDisabled"
                :aria-busy="smsSending || undefined"
                @click="onSendCode"
              >
                {{ codeText }}
              </button>
            </template>
          </van-field>
          <p v-if="errOf('smsCode')" class="register__err" role="alert">{{ errOf('smsCode') }}</p>
          <p v-if="smsError" class="register__err" role="alert">{{ smsError }}</p>
          <p v-if="smsSentTip" class="register__sent" role="status">{{ smsSentTip }}</p>
        </div>

        <div class="card register__card">
          <h3 class="register__section">意向信息</h3>

          <van-field
            :model-value="stationName"
            label="意向驿站"
            readonly
            is-link
            placeholder="请选择意向驿站"
            @click="!submitting && (stationPickerVisible = true)"
          />
          <p v-if="errOf('intentStationId')" class="register__err" role="alert">{{ errOf('intentStationId') }}</p>

          <van-field
            v-model="form.intentPosition"
            label="意向岗位"
            maxlength="50"
            :readonly="submitting"
            @blur="onBlur('intentPosition')"
          />
          <p class="register__hint">如：分拣员、快递员、客服</p>
          <p v-if="errOf('intentPosition')" class="register__err" role="alert">{{ errOf('intentPosition') }}</p>
        </div>

        <div class="card register__card">
          <van-checkbox v-model="agree" class="register__agree" @change="agreementError = ''">
            我已阅读并同意《服务条款》与《隐私与安全说明》
          </van-checkbox>
          <p v-if="agreementError" class="register__err" role="alert">{{ agreementError }}</p>
        </div>
      </div>

      <ActionBar
        :actions="[
          { key: 'submit', label: submitting ? '提交中…' : '提交注册', plain: false, loading: submitting, disabled: submitting }
        ]"
        :submitting="submitting"
        @select="onSubmit"
      />

      <StationPicker
        v-model:show="stationPickerVisible"
        :stations="REGISTRATION_STATIONS"
        :model-value="form.intentStationId"
        :allow-all="false"
        title="选择意向驿站"
        empty-text="暂无可选驿站，请联系管理员"
        @select="onPickStation"
      />
    </template>
  </div>
</template>

<style scoped>
.register__intro {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.register__form-error {
  padding: var(--sp-2) var(--sp-3);
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
  background: var(--color-danger-surface);
  border-radius: var(--r-sm);
}

.register__card {
  margin-top: var(--sp-3);

  /* 卡片已给 16px 内边距；字段再缩一次会成 32px 的过度缩进（对齐登录页口径） */
  --van-cell-horizontal-padding: 0px;
}

.register__section {
  margin: 0 0 var(--sp-1);
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h3);
  color: var(--text-1);
}

.register__err {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.register__sent {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

/* 辅助说明用 --text-3（纯白卡上 4.83:1 达 AA），示例不依赖低对比度占位符（§11.2） */
.register__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

/* 发码按钮：禁用/倒计时态保持 44px 热区（不置 display:none），靠负外边距回填字段行高（§5.1） */
.register__code-btn {
  flex: none;
  min-width: 44px;
  height: 44px;
  padding: 0 var(--sp-3);
  margin: calc(-1 * var(--sp-4)) 0;
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-sm);
  white-space: nowrap;
}

.register__code-btn:disabled {
  color: var(--text-disabled);
  border-color: var(--border-line);
}

/* 倒计时的剩余秒数是有效信息，用 --text-3 而非 --text-disabled（§5.1） */
.register__code-btn--counting {
  color: var(--text-3);
  border-color: var(--border-line);
}

.register__agree {
  --van-checkbox-label-color: var(--text-2);
  --van-checkbox-size: 20px;

  align-items: flex-start;
  min-height: var(--touch-min);
}

.register-result {
  padding-top: calc(var(--safe-top) + var(--sp-8));
  text-align: center;
}

.register-result__icon {
  font-size: 56px;
  color: var(--color-success-icon);
}

.register-result__title {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-h1);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h1);
  color: var(--text-1);
}

.register-result__no-row {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--sp-4);
}

.register-result__label {
  font-size: var(--fs-body);
  color: var(--text-2);
}

.register-result__no {
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-num-sm);
  color: var(--text-1);
}

.register-result__copy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: var(--fs-h2);
  color: var(--color-primary-icon);
  background: none;
  border: none;
}

.register-result__copy:disabled {
  color: var(--text-disabled);
}

.register-result__steps {
  padding: var(--sp-4);
  margin-top: var(--sp-4);
  text-align: left;
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-lg);
}

.register-result__steps-title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.register-result__step {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-body);
  line-height: var(--lh-body);
  color: var(--text-2);
}

.register-result__note {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.register-result__back {
  margin-top: var(--sp-5);
}
</style>
