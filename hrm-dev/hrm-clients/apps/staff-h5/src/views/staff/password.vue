<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import { useAuthStore } from '../../stores/auth.js'
import { useNotifyStore } from '../../stores/notify.js'

/**
 * S11 修改密码
 * 对齐 api.md 4.1.4：改密成功即强制下线（Mock 会清掉会话），前端清本地态并回登录页。
 * 校验规则与后端一致：8-20 位且同时包含字母和数字。
 * 为什么改字段级校验：原来只有一个全局 errorMsg，用户填完三个框才知道错在哪个字段。
 */
const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)[\s\S]{8,20}$/

/** 演示态才提示「改密写 Mock」；生产态不出现演示口径（编译期常量使演示分支整块剔除） */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'

const auth = useAuthStore()
const notify = useNotifyStore()
const router = useRouter()

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const submitting = ref(false)
const errorMsg = ref('')

const oldRules = [{ required: true, message: '请输入原密码' }]
const newRules = [
  { required: true, message: '请输入新密码' },
  { validator: (value) => PASSWORD_RULE.test(value), message: '新密码须为 8-20 位且同时包含字母和数字' },
  { validator: (value) => value !== form.oldPassword, message: '新密码不能与原密码相同' }
]
const confirmRules = [
  { required: true, message: '请再次输入新密码' },
  { validator: (value) => value === form.newPassword, message: '两次输入的新密码不一致' }
]

async function onSubmit() {
  if (submitting.value) return
  submitting.value = true
  errorMsg.value = ''
  try {
    await auth.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    notify.clear()
    showSuccessToast('密码已修改，请重新登录')
    router.replace('/login')
  } catch (e) {
    // 1004 原密码错误 / 400 密码强度不足：语义不同，文案直接透传（字段级校验兜不住的接口错误）
    errorMsg.value = e.message || '修改失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="password-page">
    <PageNav title="修改密码" />
    <div class="page page--loose">
      <div class="card form-card">
        <van-form @submit="onSubmit">
          <van-field
            v-model="form.oldPassword"
            type="password"
            name="oldPassword"
            label="原密码"
            placeholder="请输入当前密码"
            autocomplete="current-password"
            :rules="oldRules"
          />
          <van-field
            v-model="form.newPassword"
            type="password"
            name="newPassword"
            label="新密码"
            placeholder="8-20 位，含字母和数字"
            autocomplete="new-password"
            :rules="newRules"
          />
          <van-field
            v-model="form.confirmPassword"
            type="password"
            name="confirmPassword"
            label="确认密码"
            placeholder="再次输入新密码"
            autocomplete="new-password"
            :rules="confirmRules"
          />
          <p v-if="errorMsg" class="form-error" role="alert">{{ errorMsg }}</p>
          <div class="submit">
            <van-button block type="primary" native-type="submit" :loading="submitting">确认修改</van-button>
          </div>
        </van-form>
      </div>

      <p class="tip">
        修改成功后当前登录态立即失效，需用新密码重新登录。
        <template v-if="demoEnabled"
          ><br />演示提示：改密会写入 Mock 内存数据，回入口页点「重置演示数据」可恢复统一演示密码。</template
        >
      </p>
    </div>
  </div>
</template>

<style scoped>
.card {
  margin-top: var(--sp-3);
}

/* 字段自带 16px 左右内边距 + 卡片 16px 会成 32px 缩进 */
.form-card {
  --van-cell-horizontal-padding: 0px;
}

.form-error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.submit {
  margin-top: var(--sp-5);
}
</style>
