<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '../../components/PageHeader.vue'
import { landingPath } from '../../router/index.js'

/**
 * 404 页（P1-4）：替换原来「未匹配路径就静默 redirect 到看板」的兜底。
 * 为什么返回首页按角色取落地页：站长/员工的落地页不是 /dashboard，写死会被路由守卫二次弹走，看起来像按钮没反应。
 */
const router = useRouter()
const authStore = useAuthStore()

// 焦点管理（SC 2.4.3）：404 页可聚焦内容只有「返回首页」，读屏与键盘用户进入后无从判断当前在哪一页，
// 故挂载后把焦点落到主标题。h1 带 tabindex="-1"（由 PageHeader 的 titleTabindex 声明）才可编程聚焦
const panelRef = ref(null)
onMounted(() => {
  if (panelRef.value) panelRef.value.querySelector('h1')?.focus()
})

function goHome() {
  router.replace(landingPath(authStore.user))
}
</script>

<template>
  <main ref="panelRef" class="not-found">
    <div class="not-found__panel">
      <!-- PageHeader 的标题本身就是 h1，页面主标题语义天然成立 -->
      <PageHeader title="页面不存在" sub="链接可能已失效，或页面已被移动" :title-tabindex="-1">
        <template #actions>
          <el-button type="primary" @click="goHome">返回首页</el-button>
        </template>
      </PageHeader>
    </div>
  </main>
</template>

<style scoped lang="scss">
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--sp-4);
  background-color: var(--surface-page);

  &__panel {
    width: 100%;
    max-width: 480px;
    padding: var(--sp-6);
    background-color: var(--surface-card);
    border: 1px solid var(--border-line);
    border-radius: var(--r-lg);
    box-shadow: var(--e2);

    // PageHeader 自带下间距，面板里会多出一条空底边
    :deep(.page-header) {
      margin-bottom: 0;
    }
  }
}
</style>
