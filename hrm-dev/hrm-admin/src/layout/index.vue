<template>
  <el-container class="app-layout">
    <!-- 侧边栏：ADMIN 渲染全量菜单，STAFF 仅个人中心 -->
    <el-aside :width="isCollapse ? '64px' : '210px'" class="app-aside">
      <div class="app-logo">
        <el-icon :size="24" color="var(--el-color-primary)"><Box /></el-icon>
        <span v-show="!isCollapse" class="app-title">快递驿站智汇</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="var(--c-aside-bg)"
        text-color="rgba(255, 255, 255, 0.68)"
        active-text-color="var(--c-white)"
        class="app-menu"
      >
        <template v-if="authStore.isAdmin">
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <el-menu-item index="/employee">
            <el-icon><User /></el-icon>
            <template #title>员工管理</template>
          </el-menu-item>
          <el-sub-menu index="org">
            <template #title>
              <el-icon><OfficeBuilding /></el-icon>
              <span>组织管理</span>
            </template>
            <el-menu-item index="/department">
              <el-icon><Share /></el-icon>
              <template #title>部门管理</template>
            </el-menu-item>
            <el-menu-item index="/station">
              <el-icon><Location /></el-icon>
              <template #title>驿站管理</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
        <el-menu-item index="/profile">
          <el-icon><UserFilled /></el-icon>
          <template #title>个人中心</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="app-body">
      <!-- 顶栏：折叠按钮 + 面包屑 + 用户下拉 -->
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" :size="18" @click="isCollapse = !isCollapse">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">
              {{ item }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="header-user">
            <el-avatar :size="32" class="user-avatar">{{ avatarText }}</el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-tag v-if="authStore.isAdmin" size="small" effect="plain">管理员</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><UserFilled /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 内容区 -->
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { logout } from '../api/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapse = ref(false)

const activeMenu = computed(() => route.path)

const displayName = computed(
  () => (authStore.user && (authStore.user.realName || authStore.user.username)) || '未知用户'
)
const avatarText = computed(() => displayName.value.slice(0, 1))

// 面包屑：部门/驿站归属「组织管理」分组，其余按路由 meta.title
const GROUP_TITLES = {
  '/department': '组织管理',
  '/station': '组织管理'
}
const breadcrumbs = computed(() => {
  const items = []
  const group = GROUP_TITLES[route.path]
  if (group) items.push(group)
  if (route.meta && route.meta.title) items.push(route.meta.title)
  return items.length ? items : ['首页']
})

function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    // 会话可能已失效（401 已被拦截器处理），退出接口失败不阻断本地登出
    await logout()
  } catch (e) {
    /* 忽略 */
  }
  authStore.clearAuth()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<style scoped lang="scss">
.app-layout {
  height: 100%;
}

.app-aside {
  display: flex;
  flex-direction: column;
  background-color: var(--c-aside-bg);
  transition: width 0.2s;
  overflow: hidden;

  .app-logo {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    height: 60px;
    flex-shrink: 0;
    color: var(--c-white);

    .app-title {
      font-size: 16px;
      font-weight: 600;
      white-space: nowrap;
    }
  }

  .app-menu {
    flex: 1;
    border-right: none;
    overflow-y: auto;

    &:not(.el-menu--collapse) {
      width: 210px;
    }
  }
}

.app-body {
  height: 100%;
  min-width: 0;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background-color: var(--c-white);
  border-bottom: 1px solid var(--c-border-base);

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      cursor: pointer;
      color: var(--c-text-regular);

      &:hover {
        color: var(--el-color-primary);
      }
    }
  }

  .header-user {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    color: var(--c-text-primary);
    outline: none;

    .user-avatar {
      background-color: var(--el-color-primary);
      color: var(--c-white);
      font-size: 14px;
    }

    .user-name {
      font-size: 14px;
    }
  }
}

.app-main {
  padding: 16px;
  overflow-y: auto;
}
</style>
