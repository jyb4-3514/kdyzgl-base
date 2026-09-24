<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { APP_NAME } from '../../constants/brand.js'
import { useAuthStore } from '@admin/stores/auth'
import { login, sendSms, smsLogin, verifyDevice } from '../../api/auth.js'
import { collectDevicePayload, readDeviceId } from '@/shared/device.js'
import { maskPhone } from '@/shared/domain/mask.js'

/**
 * 三端登录页 · 网页端（PC）
 * 依据：demo-login-redesign.md §3.2（PC 布局）/ §2（状态机）/ §7（契约）+ multi-client-architecture.md §4.1.2。
 * 落地口径（设计规范 §9-A1 已裁定）：Demo 侧新建本页，一期 @admin/views/login 保持零改动；
 * 端准入由服务端判定（clientType=WEB → 仅 ADMIN，否则 1110），前端拦截仅作体验。
 */

/** 端类型：网页端恒为 WEB（服务端据此做端准入，非 ADMIN → 1110） */
const CLIENT_TYPE = 'WEB'
/** 手机号格式（与 Mock validate.isPhone 同口径；页面不 import Mock 层） */
const PHONE_RE = /^1[3-9]\d{9}$/
const isPhone = (value) => PHONE_RE.test(String(value || '').trim())

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeChannel = ref('password')
/** 步骤：credentials 双通道表单 / device 设备二次验证（卡片内切步） */
const step = ref('credentials')

const pwdFormRef = ref(null)
const form = reactive({ username: '', password: '' })
const smsForm = reactive({ phone: '', code: '' })
const deviceForm = reactive({ code: '' })

const errorMsg = ref('')
const smsError = ref('')
const deviceError = ref('')
const smsSentTip = ref('')
/** S9 到期强制重登提示（1108）：redirect 回跳原路径，登录成功后回原页 */
const expiredTip = ref(route.query.expired === '1' ? '登录已到期，请重新登录' : '')

const loading = ref(false)
const smsSending = ref(false)
const deviceSending = ref(false)
const deviceInfo = ref(null)
const devicePhone = ref('')
const deviceCodeRef = ref(null)

const pwdRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const smsRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: (_r, v, cb) => (isPhone(v) ? cb() : cb(new Error('请输入正确的 11 位手机号'))), trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

/** 发码倒计时 60s（两个场景各自独立计时） */
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
const smsCountdown = createCountdown(60)
const deviceCountdown = createCountdown(60)
const smsCodeText = computed(() => (smsCountdown.left.value > 0 ? `重新获取（${smsCountdown.left.value}s）` : '获取验证码'))
const deviceCodeText = computed(() =>
  deviceCountdown.left.value > 0 ? `重新获取（${deviceCountdown.left.value}s）` : '获取验证码'
)

/** 演示态标识：仅 Mock 构建展示（生产构建 VITE_MOCK_ENABLED≠true 时为 false） */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'

watch(activeChannel, () => {
  errorMsg.value = ''
})
watch([() => form.username, () => form.password], () => {
  if (errorMsg.value) errorMsg.value = ''
})
watch([() => smsForm.phone, () => smsForm.code], () => {
  if (smsError.value) smsError.value = ''
})
watch(
  () => deviceForm.code,
  () => {
    if (deviceError.value) deviceError.value = ''
  }
)

/** 登录错误 → 文案（1001 沿用既有文案，保持既有回归断言口径） */
function loginErrorMessage(error) {
  if (!error) return '网络异常，请检查网络后重试'
  if (error.code === 1001) return '用户名或密码错误，请重新输入'
  return error.message || '网络异常，请检查网络后重试'
}

/** 登录成功落态并按角色/redirect 跳转（首登强制改密沿用一期规则） */
function succeed(data) {
  authStore.setAuth(data.token, data.employee)
  if (data.employee && data.employee.pwdChanged === false) {
    ElMessage.warning('首次登录，请先修改初始密码')
    router.replace('/profile')
    return
  }
  ElMessage.success(`欢迎，${data.employee.realName}`)
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    router.replace(redirect)
  } else {
    // 网页端仅 ADMIN 可登录（服务端端准入），落地页固定看板
    router.replace('/dashboard')
  }
}

/* ==================== 密码通道 ==================== */
async function onPasswordSubmit() {
  if (loading.value) return
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await login({
      username: form.username,
      password: form.password,
      clientType: CLIENT_TYPE,
      device: collectDevicePayload(CLIENT_TYPE)
    })
    if (data.needDeviceVerify) {
      deviceInfo.value = { ticket: data.twoFactorTicket }
      devicePhone.value = (data.employee && data.employee.phone) || ''
      deviceForm.code = ''
      deviceError.value = ''
      deviceCountdown.stop()
      step.value = 'device'
      await nextTick()
      deviceCodeRef.value && deviceCodeRef.value.focus && deviceCodeRef.value.focus()
      return
    }
    succeed(data)
  } catch (error) {
    errorMsg.value = loginErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/* ==================== 验证码通道 ==================== */
async function sendLoginCode() {
  if (smsSending.value || smsCountdown.left.value > 0) return
  smsError.value = ''
  if (!isPhone(smsForm.phone)) {
    smsError.value = smsForm.phone ? '请输入正确的 11 位手机号' : '请输入手机号'
    return
  }
  smsSending.value = true
  try {
    const res = await sendSms({
      phone: smsForm.phone.trim(),
      scene: 'LOGIN',
      clientType: CLIENT_TYPE,
      deviceId: readDeviceId()
    })
    smsSentTip.value = `验证码已发送至 ${maskPhone(smsForm.phone.trim())}`
    smsCountdown.start(res.nextAllowedIn || 60)
  } catch (error) {
    if (error.code === 1101) {
      smsError.value = '验证码发送过于频繁，请稍后再试'
      smsCountdown.start(60)
    } else {
      smsError.value = error.message || '短信服务暂不可用，请稍后重试'
    }
  } finally {
    smsSending.value = false
  }
}

async function onSmsSubmit() {
  if (loading.value) return
  smsError.value = ''
  if (!isPhone(smsForm.phone)) {
    smsError.value = '请输入正确的 11 位手机号'
    return
  }
  if (!smsForm.code.trim()) {
    smsError.value = '请输入验证码'
    return
  }
  loading.value = true
  try {
    const data = await smsLogin({
      phone: smsForm.phone.trim(),
      code: smsForm.code.trim(),
      clientType: CLIENT_TYPE,
      device: collectDevicePayload(CLIENT_TYPE)
    })
    succeed(data)
  } catch (error) {
    smsError.value = loginErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/* ==================== 设备二次验证 ==================== */
async function sendDeviceCode() {
  if (deviceSending.value || deviceCountdown.left.value > 0) return
  deviceError.value = ''
  deviceSending.value = true
  try {
    const res = await sendSms({
      scene: 'DEVICE_VERIFY',
      twoFactorTicket: deviceInfo.value ? deviceInfo.value.ticket : '',
      deviceId: readDeviceId()
    })
    deviceCountdown.start(res.nextAllowedIn || 60)
  } catch (error) {
    if (error.code === 1101) {
      deviceError.value = '验证码发送过于频繁，请稍后再试'
      deviceCountdown.start(60)
    } else {
      deviceError.value = error.message || '短信服务暂不可用，请稍后重试'
    }
  } finally {
    deviceSending.value = false
  }
}

async function onDeviceSubmit() {
  if (loading.value) return
  if (!deviceForm.code.trim()) {
    deviceError.value = '请输入验证码'
    return
  }
  loading.value = true
  deviceError.value = ''
  try {
    const data = await verifyDevice({
      twoFactorTicket: deviceInfo.value ? deviceInfo.value.ticket : '',
      code: deviceForm.code.trim()
    })
    succeed(data)
  } catch (error) {
    deviceError.value = loginErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/** 退路：返回 S0 并清空密码、保留账号 */
function backToCredentials() {
  step.value = 'credentials'
  activeChannel.value = 'password'
  form.password = ''
  deviceForm.code = ''
  deviceError.value = ''
  deviceInfo.value = null
  deviceCountdown.stop()
}

function onForgot() {
  ElMessage.info('请联系管理员重置密码')
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <header class="login-header">
        <h1 class="login-title">{{ APP_NAME }}</h1>
        <p class="login-subtitle">管理员登录</p>
      </header>

      <!-- S9 到期强制重登（1108） -->
      <el-alert
        v-if="expiredTip"
        :title="expiredTip"
        type="warning"
        show-icon
        :closable="false"
        class="login-notice"
      />
      <!-- 提交级错误（内联可恢复，不用 Toast 遮挡表单） -->
      <el-alert
        v-if="errorMsg"
        :title="errorMsg"
        type="error"
        show-icon
        :closable="false"
        class="login-error"
      />

      <template v-if="step === 'credentials'">
        <el-tabs v-model="activeChannel" class="login-tabs">
          <el-tab-pane label="密码登录" name="password">
            <el-form ref="pwdFormRef" :model="form" :rules="pwdRules" size="large" @submit.prevent>
              <el-form-item prop="username">
                <el-input
                  v-model.trim="form.username"
                  placeholder="请输入登录账号"
                  :prefix-icon="User"
                  autocomplete="username"
                />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model.trim="form.password"
                  type="password"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  show-password
                  autocomplete="current-password"
                  @keyup.enter="onPasswordSubmit"
                />
              </el-form-item>
              <el-form-item class="login-btn-item">
                <el-button class="login-btn" type="primary" :loading="loading" @click="onPasswordSubmit">
                  登 录
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="验证码登录" name="sms" lazy>
            <el-form :model="smsForm" :rules="smsRules" size="large" @submit.prevent>
              <el-form-item prop="phone">
                <el-input v-model.trim="smsForm.phone" placeholder="请输入手机号" maxlength="11" />
              </el-form-item>
              <el-form-item prop="code">
                <el-input
                  v-model.trim="smsForm.code"
                  placeholder="请输入 6 位验证码"
                  maxlength="6"
                  @keyup.enter="onSmsSubmit"
                >
                  <template #append>
                    <el-button :disabled="smsSending || smsCountdown.left.value > 0" @click="sendLoginCode">
                      {{ smsCodeText }}
                    </el-button>
                  </template>
                </el-input>
              </el-form-item>
              <p v-if="smsSentTip" class="login-sent" role="status">{{ smsSentTip }}</p>
              <p v-if="smsError" class="login-field-error" role="alert">{{ smsError }}</p>
              <el-form-item class="login-btn-item">
                <el-button class="login-btn-block" type="primary" :loading="loading" @click="onSmsSubmit">
                  登 录
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </template>

      <!-- 新设备短信二次验证（卡片内切步，非弹窗） -->
      <div v-else class="login-device">
        <h2 class="login-device-title">设备验证</h2>
        <p class="login-device-desc">检测到这是一台新设备。为保障账号安全，请完成短信验证。</p>
        <p class="login-device-phone">验证手机号：{{ devicePhone }}</p>
        <el-form @submit.prevent>
          <el-form-item>
            <el-input
              ref="deviceCodeRef"
              v-model.trim="deviceForm.code"
              placeholder="请输入 6 位验证码"
              maxlength="6"
              @keyup.enter="onDeviceSubmit"
            >
              <template #append>
                <el-button :disabled="deviceSending || deviceCountdown.left.value > 0" @click="sendDeviceCode">
                  {{ deviceCodeText }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>
          <p v-if="deviceError" class="login-field-error" role="alert">{{ deviceError }}</p>
          <el-form-item class="login-btn-item">
            <el-button class="login-btn" type="primary" :loading="loading" @click="onDeviceSubmit">
              验证并登录
            </el-button>
          </el-form-item>
        </el-form>
        <el-button link type="primary" @click="backToCredentials">这不是我的设备</el-button>
        <p class="login-device-safe">如非本人操作，请立即联系管理员</p>
      </div>

      <div class="login-aux">
        <el-button link type="primary" @click="onForgot">忘记密码？</el-button>
      </div>
      <p v-if="demoEnabled" class="login-demo">演示环境 · 数据为 Mock，短信不会真实发送</p>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  /* 深档品牌渐变承白字（--grad-hero：blue-700→800，≥6:1） */
  background: var(--grad-hero);
}

.login-card {
  width: var(--login-card-w);
  border-radius: var(--r-lg);
  box-shadow: var(--e3);

  /* 卡片内边距交给 Element 卡体变量，避免与 scoped 样式重复 */
  --el-card-padding: var(--sp-6);
}

.login-header {
  margin-bottom: var(--sp-6);
  text-align: center;
}

.login-title {
  margin: 0;
  font-size: var(--fs-h1);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h1);
  color: var(--text-1);
}

.login-subtitle {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.login-notice,
.login-error {
  margin-bottom: var(--sp-4);
}

.login-sent {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.login-field-error {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.login-btn-item {
  margin-bottom: var(--sp-2);
}

/* 主按钮：宽通栏。密码通道保留 .login-btn（既有 e2e 选择器），验证码通道用 .login-btn-block，
 * 避免同页出现两个 .login-btn 触发 Playwright 严格模式歧义（验证码面板已 lazy 渲染，双保险） */
.login-btn,
.login-btn-block {
  width: 100%;
  letter-spacing: 6px;
}

.login-device-title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h2);
  color: var(--text-1);
}

.login-device-desc {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.login-device-phone {
  margin: 0 0 var(--sp-4);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.login-device-safe {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.login-aux {
  margin-top: var(--sp-2);
  text-align: right;
}

/* 演示标识：复用既有 --state-simulate-*（与 warning 语义分离），不新增 Token */
.login-demo {
  margin: var(--sp-3) 0 0;
  padding: 2px var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--state-simulate-fg);
  background: var(--state-simulate-bg);
  border: 1px solid var(--state-simulate-border);
  border-radius: var(--r-sm);
}
</style>
