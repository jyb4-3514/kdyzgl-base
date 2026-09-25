<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { useAuthStore } from '../stores/auth.js'
import { useNotifyStore } from '../stores/notify.js'
import { useTodoStore } from '../stores/todo.js'

/**
 * 演示身份切换（C-M7，管理员 / 站长 / 员工）
 * 管理端与员工端「我的」页都要用，逻辑（重新登录 → 清角标 → 跳对应首页）只实现一份。
 * 选中态用「浅蓝底 + 主色描边 + 勾选图标」三重通道，不只靠颜色（修 P27/P33）。
 */
const auth = useAuthStore()
const notify = useNotifyStore()
const todo = useTodoStore()
const router = useRouter()
const switching = ref('')

/** 演示态才有身份可切；关闭后本组件整体不渲染（含账号清单，生产构建不进包） */
const demoEnabled = import.meta.env.VITE_MOCK_ENABLED === 'true'
const accountList = ref([])
if (import.meta.env.VITE_MOCK_ENABLED === 'true') {
  import('@/demo/accounts.js').then(({ DEMO_ACCOUNT_LIST }) => {
    accountList.value = DEMO_ACCOUNT_LIST
  })
}

const currentUsername = computed(() => (auth.user && auth.user.username) || '')

async function onSwitch(account) {
  if (switching.value || account.username === currentUsername.value) return
  switching.value = account.key
  try {
    const home = await auth.switchTo(account.key)
    notify.clear()
    todo.clear() // 待办是「按角色收敛」的快照，换身份后必须清空，否则会短暂显示上一个身份的条数
    await notify.refresh()
    showSuccessToast(`已切换为${account.label}`)
    // 用 replace 避免演示中反复切换把 history 堆满，返回键行为可预期
    router.replace(home)
  } catch (error) {
    showFailToast(error.message || '切换失败')
  } finally {
    switching.value = ''
  }
}
</script>

<template>
  <div
    v-if="demoEnabled"
    class="identity-switcher"
    role="radiogroup"
    aria-label="演示身份切换"
    :aria-busy="switching ? 'true' : undefined"
  >
    <button
      v-for="account in accountList"
      :key="account.key"
      type="button"
      role="radio"
      class="identity-switcher__item"
      :class="{ 'identity-switcher__item--active': account.username === currentUsername }"
      :aria-checked="account.username === currentUsername"
      :disabled="!!switching"
      @click="onSwitch(account)"
    >
      <span class="identity-switcher__body">
        <span class="identity-switcher__label">{{ account.label }}</span>
        <span class="identity-switcher__desc">{{ account.desc }}</span>
      </span>
      <van-loading v-if="switching === account.key" size="14" />
      <van-icon
        v-else-if="account.username === currentUsername"
        name="success"
        class="identity-switcher__check"
        aria-hidden="true"
      />
    </button>
  </div>
</template>

<style scoped>
.identity-switcher__item {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 56px;
  padding: var(--sp-3) var(--sp-4);
  margin-bottom: var(--sp-2);
  text-align: left;
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-lg);
}

.identity-switcher__item:last-child {
  margin-bottom: 0;
}

.identity-switcher__item--active {
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.identity-switcher__item:disabled {
  opacity: 0.6;
}

.identity-switcher__body {
  min-width: 0;
}

.identity-switcher__label {
  display: block;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.identity-switcher__desc {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.identity-switcher__check {
  flex: none;
  font-size: 16px;
  color: var(--color-primary);
}
</style>
