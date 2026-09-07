<template>
  <div class="profile-page">
    <!-- 首登强制改密锁定态：仅展示改密卡片（requirement.md 5.3） -->
    <el-alert
      v-if="authStore.needChangePwd"
      class="lock-alert"
      type="warning"
      title="首次登录：请先修改初始密码，完成前无法访问系统其他功能"
      show-icon
      :closable="false"
    />

    <el-row :gutter="16">
      <!-- 本人信息卡（手机号脱敏由后端出参保证） -->
      <el-col v-if="!authStore.needChangePwd" :xs="24" :md="10">
        <el-card shadow="never" class="info-card">
          <template #header>
            <div class="card-header">
              <span>个人信息</span>
              <el-tag :type="authStore.isAdmin ? 'danger' : 'info'" effect="plain">
                {{ authStore.isAdmin ? '管理员' : '员工' }}
              </el-tag>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="登录账号">{{ userInfo.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ userInfo.realName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userInfo.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ genderMap[userInfo.gender] || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ userInfo.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="驿站">{{ userInfo.stationName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入职日期">{{ userInfo.entryDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">{{ userInfo.lastLoginTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 修改密码卡片 -->
      <el-col :xs="24" :md="authStore.needChangePwd ? 24 : 14">
        <el-card shadow="never" class="pwd-card">
          <template #header>
            <span>{{ authStore.needChangePwd ? '修改初始密码' : '修改密码' }}</span>
          </template>
          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-width="100px"
            class="pwd-form"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="pwdForm.oldPassword"
                type="password"
                show-password
                placeholder="请输入当前使用的密码"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwdForm.newPassword"
                type="password"
                show-password
                placeholder="8-20 位，须包含字母和数字"
              />
              <div class="form-tip">新密码须为 8-20 位，且同时包含字母和数字；修改成功后需重新登录</div>
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                show-password
                placeholder="请再次输入新密码"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="handleSubmitPassword">
                {{ authStore.needChangePwd ? '修改初始密码' : '确认修改' }}
              </el-button>
              <el-button v-if="!authStore.needChangePwd" @click="resetPwdForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMe, updatePassword } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const genderMap = { 0: '未知', 1: '男', 2: '女' }

// 本人信息：优先取 /auth/me 最新数据，失败时回退 store 缓存
const meInfo = ref(null)
const userInfo = computed(() => meInfo.value || authStore.user || {})

onMounted(async () => {
  try {
    const me = await getMe()
    meInfo.value = me
    // 同步刷新 store 中的用户信息（如 pwdChanged 状态）
    authStore.updateUser(me)
  } catch (err) {
    /* 已提示；保留 store 缓存展示 */
  }
})

/* ==================== 修改密码 ==================== */
const pwdFormRef = ref()
const submitting = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function validatePasswordStrength(rule, value, callback) {
  if (!value) {
    callback(new Error('请输入新密码'))
    return
  }
  if (value.length < 8 || value.length > 20 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('密码须为 8-20 位，且同时包含字母和数字'))
    return
  }
  callback()
}

function validateConfirmPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validatePasswordStrength, trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value && pwdFormRef.value.clearValidate()
}

/**
 * 修改本人密码（api.md 4.1.4）：
 * 成功后当前会话失效 → 清登录态回登录页，用新密码重新登录
 */
async function handleSubmitPassword() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await updatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    authStore.clearAuth()
    router.replace('/login')
  } catch (err) {
    // 1004 原密码错误 / 400 强度不足，已由拦截器按后端 message 提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.profile-page {
  .lock-alert {
    margin-bottom: 16px;
  }

  .info-card,
  .pwd-card {
    border-radius: 8px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .pwd-form {
    max-width: 460px;
  }
}
</style>
