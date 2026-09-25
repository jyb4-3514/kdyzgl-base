<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <div class="login-logo">
          <el-icon :size="36" color="var(--el-color-primary)"><Box /></el-icon>
        </div>
        <h2 class="login-title">快递驿站智慧管理系统</h2>
        <p class="login-subtitle">一期 · 员工管理平台</p>
      </div>

      <!-- 登录失败提示：1001 用户名或密码错误 / 1002 账号已禁用 文案区分 -->
      <el-alert
        v-if="errorMsg"
        :title="errorMsg"
        type="error"
        show-icon
        :closable="false"
        class="login-error"
      />

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        size="large"
        @submit.prevent
      >
        <el-form-item prop="username">
          <el-input
            v-model.trim="loginForm.username"
            placeholder="登录账号"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model.trim="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item class="login-btn-item">
          <el-button
            class="login-btn"
            type="primary"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loginFormRef = ref()
const loading = ref(false)
const errorMsg = ref('')

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/**
 * 登录流程（requirement.md 5.3）：
 * - 失败：1001/1002 文案区分，停留登录页
 * - 成功且 pwdChanged=false：锁定 /profile 改密卡片
 * - 成功且 pwdChanged=true：ADMIN → /dashboard，STAFF → /profile（支持 redirect 回跳）
 */
async function handleLogin() {
  errorMsg.value = ''
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({
      username: loginForm.username,
      password: loginForm.password,
      // 端准入：PC 管理端恒上报 WEB（服务端 pc-allowed-roles 默认仅 ADMIN）。
      // 为什么必须显式上报：端类型缺失即 fail-closed 返回 1110，管理员会被自己的准入规则拒之门外。
      clientType: 'WEB'
    })
    authStore.setAuth(data.token, data.employee)

    // 首登强制改密：路由守卫将锁定在 /profile
    if (data.employee && data.employee.pwdChanged === false) {
      ElMessage.warning('首次登录，请先修改初始密码')
      router.replace('/profile')
      return
    }

    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
      router.replace(redirect)
    } else {
      router.replace(data.employee && data.employee.role === 'ADMIN' ? '/dashboard' : '/profile')
    }
  } catch (err) {
    // 拦截器已 silent，此处自行区分文案（api.md 2.2 错误码表）
    const msgMap = {
      1001: '用户名或密码错误',
      1002: '账号已禁用，请联系管理员'
    }
    errorMsg.value = msgMap[err.code] || err.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(
    135deg,
    var(--c-brand-800) 0%,
    var(--c-brand-600) 55%,
    var(--c-brand-400) 100%
  );

  .login-card {
    width: 400px;
    padding: 12px 8px 4px;
    border-radius: 10px;

    .login-header {
      margin-bottom: 24px;
      text-align: center;

      .login-logo {
        margin-bottom: 8px;
      }

      .login-title {
        margin: 0;
        font-size: 22px;
        font-weight: 600;
        color: var(--c-text-primary);
      }

      .login-subtitle {
        margin: 8px 0 0;
        font-size: 13px;
        color: var(--text-3);
      }
    }

    .login-error {
      margin-bottom: 16px;
    }

    .login-btn-item {
      margin-bottom: 6px;
    }

    .login-btn {
      width: 100%;
      letter-spacing: 6px;
    }
  }
}
</style>
