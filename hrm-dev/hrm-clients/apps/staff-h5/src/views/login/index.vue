<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { resolveAppName } from '../../constants/appName.js'
import { roleLabel } from '../../constants/accounts.js'
import { useAuthStore } from '../../stores/auth.js'
import { sendSms } from '../../api/auth.js'
import { collectDevicePayload, readDeviceId } from '@kdyzgl/shared/device.js'
import { maskPhone } from '@kdyzgl/shared/domain/mask.js'

/**
 * 员工端「驿站助手」登录页（自 hrm-demo 移动端登录页复制式迁移，B3）
 * 依据：demo-login-redesign.md（§2 状态机 / §4 交互 / §5 视觉 / §7 契约）+ multi-client-architecture.md §4.1.2。
 *
 * 五项能力：① 双通道登录（密码 / 短信）② 60s 发码倒计时 ③ 新设备二次验证（卡片内第 2 步，非弹窗）
 *          ④ 内联可恢复错误（不用 Toast 挡表单）⑤ 3 天到期强制重登（?expired=1 顶部提示条 + redirect）。
 *
 * 端固定化（ADR §3.3）：本入口恒上报 clientType=STAFF（后端 fail-closed：缺省/未知端 → 1110 拒登）；
 * 同时保留 as=station 兼容读（B-4：旧书签/旧壳 ≥1 个发布周期内仍可读）。
 *
 * 安全红线（security-auth-review §4.2/§4.4）：
 * - 验证码绝不回显在页面（页面只显示「已发送至 138****0000」）；固定码只存在于 Mock 实现内部
 * - 前端不持久化任何「设备受信」标志：deviceId 仅作弱信号上报，是否放行由服务端裁定
 */

/** 端类型：员工端入口恒为 STAFF（不再依赖 ?as 派生）；as 仅作兼容读随请求带上，冲突时以入口为准 */
const CLIENT_TYPE = 'STAFF'
/** 兼容读：旧书签 / 旧壳带 ?as=station，迁移期继续接受（B-4 保留 ≥1 个发布周期） */
const COMPAT_AS = 'station'
/** 手机号格式（与 Mock validate.isPhone 同口径；页面不 import Mock 层，避免页面依赖假后端） */
const PHONE_RE = /^1[3-9]\d{9}$/
const isPhone = (value) => PHONE_RE.test(String(value || '').trim())

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

/**
 * 端品牌：员工端入口固定展示「驿站助手」；?as 仅作兼容读（station / staff 均落员工端口径），
 * 出现异端取值（如 boss）时回落系统名，不泄漏另一端品牌。
 */
const appName = computed(() => resolveAppName({ as: route.query.as, role: auth.role }))

/** S9 到期强制重登提示（1108）：守卫/接口带 ?expired=1 进入登录页时给出 warning 提示条 */
const expiredTip = ref(route.query.expired === '1' ? '登录已到期，请重新登录' : '')

/** 通道：默认「密码登录」；两份独立状态保证切换不清空已输入内容（§4.1） */
const activeChannel = ref('password')
/** 步骤：credentials 双通道表单 / device 设备二次验证（卡片内第 2 步，不是新页面也不是弹窗） */
const step = ref('credentials')

const form = reactive({ username: '', password: '' })
const smsForm = reactive({ phone: '', code: '' })
const deviceForm = reactive({ code: '' })

/** 提交级错误（表单顶部内联，role=alert）：切换通道时清除（§4.1） */
const errorMsg = ref('')
/** 字段级错误：验证码通道 / 设备步各自持有，切换通道时保留（§4.1） */
const smsError = ref('')
const deviceError = ref('')
/** 发码成功提示（脱敏手机号，§4.2） */
const smsSentTip = ref('')

const loading = ref(false)
const smsSending = ref(false)
const deviceSending = ref(false)
const showPassword = ref(false)
/** 二次验证上下文：票据 + 服务端返回的员工（手机号已脱敏） */
const deviceInfo = ref(null)
const devicePhone = ref('')
const deviceCodeRef = ref(null)

/** 60s 发码倒计时（§4.2）：两个场景各自独立计时，不复用同一枚 */
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

const smsCodeText = computed(() =>
  smsCountdown.left.value > 0 ? `重新获取（${smsCountdown.left.value}s）` : '获取验证码'
)
const deviceCodeText = computed(() =>
  deviceCountdown.left.value > 0 ? `重新获取（${deviceCountdown.left.value}s）` : '获取验证码'
)

/* ==================== 演示资产（仅 Mock 态动态加载，生产构建剔除） ==================== */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'
const demoAccounts = ref([])
const demoPassword = ref('')

if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@/demo/accounts.js').then(({ DEMO_ACCOUNT_LIST, DEMO_PASSWORD }) => {
    // 只列员工端可登的演示身份（站点 / 员工）：端准入互斥后，员工端入口不得出现管理员账号（点了必被 1110 拒）
    demoAccounts.value = DEMO_ACCOUNT_LIST.filter((item) => item.end === COMPAT_AS)
    demoPassword.value = DEMO_PASSWORD
    // 入口页卡片带 ?as=station|staff 直达对应演示身份，省掉演示现场手输账号（兼容读）
    const account = DEMO_ACCOUNT_LIST.find((item) => item.key === route.query.as)
    if (account) fill(account)
  })
}

function fill(account) {
  activeChannel.value = 'password'
  form.username = account.username
  form.password = demoPassword.value
  errorMsg.value = ''
}

/* ==================== 通道切换 ==================== */
watch(activeChannel, () => {
  // 已提交级错误在切换时清除；字段级错误保留在各自通道（§4.1）
  errorMsg.value = ''
})
// 输入即清除该错误，错误可恢复（§4.7）
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

/** 登录错误 → 文案（§2.5 逐条定稿；1001 沿用既有文案以保持既有回归断言口径） */
function loginErrorMessage(error) {
  if (!error) return '网络异常，请检查网络后重试'
  if (error.code === 1001) return '用户名或密码错误，请重新输入'
  return error.message || '网络异常，请检查网络后重试'
}

function succeed(employee) {
  showSuccessToast(`欢迎，${employee.realName}（${roleLabel(employee.role)}）`)
  router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : auth.homePath)
}

/* ==================== 密码通道（S1/S2/S3） ==================== */
async function onPasswordSubmit() {
  if (loading.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await auth.loginByPassword({
      username: form.username,
      password: form.password,
      clientType: CLIENT_TYPE,
      as: COMPAT_AS,
      device: collectDevicePayload(CLIENT_TYPE)
    })
    // 新设备：服务端返回分流字段（无 token），进入卡片内第 2 步（1104 为分流码，不显示为红色错误）
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
    succeed(data.employee)
  } catch (error) {
    errorMsg.value = loginErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/* ==================== 验证码通道（S10/S11/S12） ==================== */
async function sendLoginCode() {
  if (smsSending.value || smsCountdown.left.value > 0) return
  errorMsg.value = ''
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
      // 频控时按服务端建议间隔覆盖本地倒计时（§4.2）
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
  loading.value = true
  try {
    const data = await auth.loginBySms({
      phone: smsForm.phone.trim(),
      code: smsForm.code.trim(),
      clientType: CLIENT_TYPE,
      as: COMPAT_AS,
      device: collectDevicePayload(CLIENT_TYPE)
    })
    succeed(data.employee)
  } catch (error) {
    smsError.value = loginErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/* ==================== 设备二次验证（S3/S7） ==================== */
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
  loading.value = true
  deviceError.value = ''
  try {
    const data = await auth.verifyLoginDevice({
      twoFactorTicket: deviceInfo.value ? deviceInfo.value.ticket : '',
      code: deviceForm.code.trim()
    })
    succeed(data.employee)
  } catch (error) {
    deviceError.value = loginErrorMessage(error)
  } finally {
    loading.value = false
  }
}

/** 退路（§4.3）：返回 S0 并清空密码、保留账号 */
function backToCredentials() {
  step.value = 'credentials'
  activeChannel.value = 'password'
  form.password = ''
  deviceForm.code = ''
  deviceError.value = ''
  deviceInfo.value = null
  deviceCountdown.stop()
}

/* ==================== 辅助与合规 ==================== */
const forgotTip = ref('')
function onForgot() {
  forgotTip.value = '请联系管理员重置密码'
}
</script>

<template>
  <div class="login">
    <header class="login__header">
      <h1 class="login__title">{{ appName }}</h1>
      <p class="login__subtitle">员工端</p>
    </header>

    <!-- S9 到期强制重登（1108）：warning 提示条，表单仍可用 -->
    <p v-if="expiredTip" class="login__expired" role="status">{{ expiredTip }}</p>

    <section class="login__card">
      <!-- 演示态一键体验：仅 Mock 构建加载演示账号，生产构建整块不渲染 -->
      <div v-if="demoEnabled && step === 'credentials'" class="login__quick">
        <span class="login__quick-label">一键体验：</span>
        <button
          v-for="account in demoAccounts"
          :key="account.key"
          type="button"
          class="login__quick-btn"
          @click="fill(account)"
        >
          {{ account.label }}
        </button>
      </div>

      <!-- 提交级错误（表单顶部内联，切换通道时清除） -->
      <p v-if="errorMsg" class="login__error" role="alert">{{ errorMsg }}</p>

      <!-- 第 1 步：双通道表单 -->
      <template v-if="step === 'credentials'">
        <van-tabs v-model:active="activeChannel" class="login__tabs" type="line" shrink>
          <van-tab title="密码登录" name="password">
            <van-form class="login__form" @submit="onPasswordSubmit">
              <van-field
                v-model="form.username"
                name="username"
                label="账号"
                placeholder="请输入登录账号"
                autocomplete="username"
                :rules="[{ required: true, message: '请输入登录账号' }]"
              />
              <van-field
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                name="password"
                label="密码"
                placeholder="请输入密码"
                autocomplete="current-password"
                :rules="[{ required: true, message: '请输入密码' }]"
              >
                <template #right-icon>
                  <button
                    type="button"
                    class="login__eye"
                    :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                    :aria-pressed="showPassword"
                    @click="showPassword = !showPassword"
                  >
                    <van-icon :name="showPassword ? 'eye-o' : 'closed-eye'" aria-hidden="true" />
                  </button>
                </template>
              </van-field>
              <div class="login__submit">
                <van-button block type="primary" native-type="submit" :loading="loading">登录</van-button>
              </div>
            </van-form>
          </van-tab>

          <van-tab title="验证码登录" name="sms">
            <van-form class="login__form" @submit="onSmsSubmit">
              <van-field
                v-model="smsForm.phone"
                type="tel"
                inputmode="numeric"
                autocomplete="tel"
                name="phone"
                label="手机号"
                placeholder="请输入手机号"
                :rules="[{ required: true, message: '请输入手机号' }]"
              />
              <van-field
                v-model="smsForm.code"
                type="password"
                inputmode="numeric"
                autocomplete="one-time-code"
                name="code"
                label="验证码"
                placeholder="请输入 6 位验证码"
                :rules="[{ required: true, message: '请输入验证码' }]"
              >
                <template #button>
                  <button
                    type="button"
                    class="login__code-btn"
                    :disabled="smsSending || smsCountdown.left.value > 0"
                    :aria-disabled="smsSending || smsCountdown.left.value > 0"
                    @click="sendLoginCode"
                  >
                    {{ smsCodeText }}
                  </button>
                </template>
              </van-field>
              <p v-if="smsSentTip" class="login__sent" role="status">{{ smsSentTip }}</p>
              <p v-if="smsError" class="login__field-error" role="alert">{{ smsError }}</p>
              <div class="login__submit">
                <van-button block type="primary" native-type="submit" :loading="loading">登录</van-button>
              </div>
            </van-form>
          </van-tab>
        </van-tabs>
      </template>

      <!-- 第 2 步：新设备短信二次验证（卡片内切步，非弹窗） -->
      <div v-else class="login__device">
        <h2 class="login__device-title">设备验证</h2>
        <p class="login__device-desc">检测到这是一台新设备。为保障账号安全，请完成短信验证。</p>
        <p class="login__device-phone">验证手机号：{{ devicePhone }}</p>
        <van-form class="login__form" @submit="onDeviceSubmit">
          <van-field
            ref="deviceCodeRef"
            v-model="deviceForm.code"
            type="password"
            inputmode="numeric"
            autocomplete="one-time-code"
            name="code"
            label="验证码"
            placeholder="请输入 6 位验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
          >
            <template #button>
              <button
                type="button"
                class="login__code-btn"
                :disabled="deviceSending || deviceCountdown.left.value > 0"
                :aria-disabled="deviceSending || deviceCountdown.left.value > 0"
                @click="sendDeviceCode"
              >
                {{ deviceCodeText }}
              </button>
            </template>
          </van-field>
          <p v-if="deviceError" class="login__field-error" role="alert">{{ deviceError }}</p>
          <div class="login__submit">
            <van-button block type="primary" native-type="submit" :loading="loading">验证并登录</van-button>
          </div>
        </van-form>
        <button type="button" class="login__back" @click="backToCredentials">这不是我的设备</button>
        <p class="login__safe">如非本人操作，请立即联系管理员</p>
      </div>

      <p v-if="demoEnabled" class="login__tip">
        演示密码统一为 {{ demoPassword }}，仅存在于 Mock 数据，非任何环境真实凭据
      </p>
    </section>

    <div class="login__aux">
      <button type="button" class="login__link" @click="onForgot">忘记密码？</button>
    </div>
    <p v-if="forgotTip" class="login__tip" role="status">{{ forgotTip }}</p>

    <footer class="login__compliance">
      <p v-if="demoEnabled" class="login__simulate">演示环境 · 短信不会真实发送</p>
      <p class="login__agree">
        登录即表示同意《服务条款》与《隐私与安全说明》<template v-if="demoEnabled">，本页仅为演示</template>。
      </p>
    </footer>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;

  /* 顶部与底部留出安全区，避免被状态栏 / Home 指示条遮挡（H5 壳 WebView） */
  padding: calc(var(--safe-top) + var(--sp-10)) var(--sp-5) calc(var(--safe-bottom) + var(--sp-8));

  /* 主色浅底渐入页面底：品牌感来自 Token，不再是手调色值 */
  background: linear-gradient(180deg, var(--color-primary-surface) 0%, var(--surface-page) 42%);
}

.login__header {
  padding-bottom: var(--sp-6);
}

.login__title {
  margin: 0;
  font-size: var(--fs-h1);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h1);
}

/* 品牌行落在渐变近顶区：--text-3 对合成底色约 4.33:1 不达 AA，故用 --text-2（设计 §5.3） */
.login__subtitle {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

/* 到期提示条（1108）：warning 语义，不复用 simulate（演示标识）那组变量 */
.login__expired {
  margin: 0 0 var(--sp-4);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--state-warning-fg);
  background: var(--state-warning-bg);
  border: 1px solid var(--state-warning-border);
  border-radius: var(--r-sm);
}

.login__card {
  padding: var(--sp-4);

  /* 卡片已给 16px 内边距；字段再缩一次会成 32px 的过度缩进 */
  --van-cell-horizontal-padding: 0px;

  /* 表单控件压到 44px：10×2 + 24 */
  --van-cell-vertical-padding: 10px;

  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-lg);
  box-shadow: var(--e0);
}

.login__tabs {
  /* Tab 下划线随主色 Token（--van-tabs-bottom-bar-color 已在 tokens 覆盖） */
  --van-tabs-line-height: 44px;
}

.login__form {
  padding-top: var(--sp-2);
}

.login__quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  align-items: center;
  margin-bottom: var(--sp-3);
}

.login__quick-label {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

/* 次要控件：触控区压到项目硬下限 44×44（设计 P4 / §4.8），主入口仍是下方「登录」按钮 */
.login__quick-btn {
  min-height: var(--touch-min);
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

.login__error,
.login__field-error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.login__sent {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

/* 密码明文/密文切换：热区撑到 44×44，靠上下负外边距抵消，不把 44 高的字段撑高 */
.login__eye {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin: calc(-1 * var(--sp-4)) 0;
  font-size: var(--fs-h2);
  color: var(--text-3);
  background: none;
  border: none;
}

.login__eye:active {
  color: var(--color-primary);
}

/* 发码按钮：禁用态保持 44px 热区（不置 display:none，§4.2），靠负外边距回填字段行高 */
.login__code-btn {
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

.login__code-btn:disabled {
  color: var(--text-disabled);
  border-color: var(--border-line);
}

.login__device-title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h2);
}

.login__device-desc {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.login__device-phone {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

/* 次要链接（不是我的设备）：次要动作，热区 ≥44px */
.login__back {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0;
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}

/* 纯白卡上 --text-3 实测 4.83:1，达 AA */
.login__safe {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.login__submit {
  margin-top: var(--sp-5);
}

.login__tip {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.login__aux {
  margin-top: var(--sp-4);
  text-align: right;
}

.login__link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0;
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}

.login__compliance {
  margin-top: var(--sp-5);
}

/* 演示标识：复用既有 --state-simulate-*（与 warning 语义分离），不新增 Token */
.login__simulate {
  display: inline-block;
  margin: 0 0 var(--sp-2);
  padding: 2px var(--sp-2);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--state-simulate-fg);
  background: var(--state-simulate-bg);
  border: 1px solid var(--state-simulate-border);
  border-radius: var(--r-sm);
}

.login__agree {
  margin: 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
