<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { roleLabel } from '../../constants/accounts.js'
import { useAuthStore } from '../../stores/auth.js'

/**
 * B1 登录页
 * 三个「一键体验」按钮是演示主线入口：老板 / 站长 / 员工 一键填表并登录，
 * 也支持手输账号密码（覆盖 1001 / 1002 错误文案分支）。
 */
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')
/** 密码明文/密文切换：只切 input type，不清空已输入内容，也不影响「一键体验」填充 */
const showPassword = ref(false)

/** 演示账号与密码只在 Mock 态动态加载；关闭后「一键体验」整块不渲染（生产构建剔除 demo 资产） */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'
const demoAccounts = ref([])
const demoPassword = ref('')

if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@/demo/accounts.js').then(({ DEMO_ACCOUNT_LIST, DEMO_PASSWORD }) => {
    demoAccounts.value = DEMO_ACCOUNT_LIST
    demoPassword.value = DEMO_PASSWORD
    // 入口页卡片带 ?as=boss|station|staff 直达对应演示身份，省掉演示现场手输账号
    const account = DEMO_ACCOUNT_LIST.find((item) => item.key === route.query.as)
    if (account) fill(account)
  })
}

function fill(account) {
  form.username = account.username
  form.password = demoPassword.value
  errorMsg.value = ''
}

async function onSubmit() {
  if (loading.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const employee = await auth.login({ ...form })
    showSuccessToast(`欢迎，${employee.realName}（${roleLabel(employee.role)}）`)
    router.replace(route.query.redirect || auth.homePath)
  } catch (error) {
    // 登录接口走 silent 模式，错误文案渲染在表单内（1001 / 1002 分支都必须可见）
    errorMsg.value = error.code === 1002 ? error.message : '用户名或密码错误，请重新输入'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <header class="login__header">
      <h1 class="login__title">快递驿站智汇系统</h1>
      <p class="login__subtitle">移动端演示 · 纯 Mock 数据，无需后端</p>
    </header>

    <section class="login__card">
      <div v-if="demoEnabled" class="login__quick">
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

      <van-form @submit="onSubmit">
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
          <!-- 明文/密文切换：aria-label 随状态变化，语义不全压在图标上（图标名已核对 vant 4.10 图标清单） -->
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
        <p v-if="errorMsg" class="login__error" role="alert">{{ errorMsg }}</p>
        <div class="login__submit">
          <van-button block type="primary" native-type="submit" :loading="loading">登录</van-button>
        </div>
      </van-form>

      <p v-if="demoEnabled" class="login__tip">
        演示密码统一为 {{ demoPassword }}，仅存在于 Mock 数据，非任何环境真实凭据
      </p>
    </section>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  padding: 0 var(--sp-5) var(--sp-8);

  /* 主色浅底渐入页面底：品牌感来自 Token，不再是手调 #e8f2ff */
  background: linear-gradient(180deg, var(--color-primary-surface) 0%, var(--surface-page) 42%);
}

.login__header {
  padding: var(--sp-10) 0 var(--sp-6);
}

.login__title {
  margin: 0;
  font-size: var(--fs-h1);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-h1);
}

.login__subtitle {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
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

/* 次要控件：高 32（≥24 的 AA 要求），主入口仍是下方「登录」按钮 */
.login__quick-btn {
  min-height: 32px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

.login__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
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

.login__submit {
  margin-top: var(--sp-5);
}

.login__tip {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}
</style>
