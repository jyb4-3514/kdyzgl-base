<template>
  <el-container class="app-layout">
    <!-- 侧边栏：菜单项由 shared/constants/role.js 的 MENU_WHITELIST 过滤，角色差异只体现在数据上 -->
    <el-aside :width="isCollapse ? 'var(--aside-w-collapsed)' : 'var(--aside-w)'" class="app-aside">
      <div class="app-logo">
        <el-icon :size="22" class="app-logo__icon"><Box /></el-icon>
        <span v-show="!isCollapse" class="app-logo__title">快递驿站智汇</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="menuGroupKeys"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        class="app-menu"
      >
        <template v-for="menu in menus" :key="menu.key">
          <el-sub-menu v-if="menu.children" :index="menu.key">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.key" :index="child.path">
              <el-icon><component :is="child.icon" /></el-icon>
              <template #title>{{ child.title }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <template #title>{{ menu.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container class="app-body">
      <el-header class="app-header">
        <div class="header-left">
          <!-- 折叠按钮改用 <button>：原先是绑了 click 的 el-icon，键盘用户根本无法展开/折叠侧栏（修 P20） -->
          <button
            type="button"
            class="collapse-btn"
            :aria-label="isCollapse ? '展开侧边栏' : '折叠侧边栏'"
            :aria-expanded="!isCollapse"
            @click="isCollapse = !isCollapse"
          >
            <el-icon :size="18"><Expand v-if="isCollapse" /><Fold v-else /></el-icon>
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <el-dropdown @command="handleCommand">
          <!-- 同样改 <button> 承载，并保留 aria-haspopup；删除原先的 outline:none（修 P20 / SC 2.4.7） -->
          <button type="button" class="header-user" aria-haspopup="menu" aria-label="用户菜单">
            <span class="header-user__avatar">{{ avatarText }}</span>
            <span class="header-user__name">{{ displayName }}</span>
            <StatusTag :dict="roleTagDict" value="role" variant="outline" />
            <el-icon :size="12"><ArrowDown /></el-icon>
          </button>
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

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 路由切换进度条：Element 无内置，2px 主色条 300ms 走完一次（T19） -->
    <div v-if="progressKey" :key="progressKey" class="route-progress" aria-hidden="true" />
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@admin/stores/auth'
import { ROLE_LABEL } from '@/shared/constants/role'
import { useLogout } from '../composables/useLogout.js'
import { buildMenus, MENU_GROUPS, MENU_ITEMS } from '../config/menu.js'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
// 布局层只依赖本 composable，不直连 @admin/api/auth（P1-7）
const logout = useLogout()

/** 窄屏（<1200px）自动折叠侧栏：改前只能手动折叠，1200 以下内容区被侧栏挤压 */
const NARROW_QUERY = '(max-width: 1199px)'
const isCollapse = ref(false)
let mediaQuery = null

function applyNarrow(event) {
  isCollapse.value = event.matches
}

onMounted(() => {
  if (typeof window.matchMedia !== 'function') return
  mediaQuery = window.matchMedia(NARROW_QUERY)
  applyNarrow(mediaQuery)
  // Safari 14 以下只有已废弃的 addListener，这里做兼容而非直接放弃窄屏降级
  if (mediaQuery.addEventListener) mediaQuery.addEventListener('change', applyNarrow)
  else mediaQuery.addListener(applyNarrow)
})

onBeforeUnmount(() => {
  if (!mediaQuery) return
  if (mediaQuery.removeEventListener) mediaQuery.removeEventListener('change', applyNarrow)
  else mediaQuery.removeListener(applyNarrow)
})

const menus = computed(() => buildMenus((authStore.user && authStore.user.role) || ''))

/**
 * 分组默认全展开：Element 的 initMenu 只会展开「当前页面所在分组」，
 * 老板看首页时找不到「财务管理」在哪个分组下，得逐个点开。默认全展后 4 个分组的项一次可见，
 * 分组标题仍承担扫读锚点的作用；用户手动折叠某一组属于显式操作，不被覆盖。
 */
const menuGroupKeys = computed(() => menus.value.filter((menu) => menu.children).map((menu) => menu.key))

/**
 * 菜单高亮（A3-5）：详情页路径（如 /employee/detail/3、/finance/payroll/12）在菜单里没有同名项，
 * 直接用 route.path 会让整个菜单失去高亮，故取「与当前路径前缀匹配的最长菜单路径」；
 * 路由也可用 meta.activeMenu 显式指定父路径（多级详情页用）。
 */
const activeMenu = computed(() => {
  if (route.meta && route.meta.activeMenu) return route.meta.activeMenu
  const path = route.path
  return (
    MENU_ITEMS.map((item) => item.path)
      .filter((item) => path === item || path.startsWith(`${item}/`))
      .sort((a, b) => b.length - a.length)[0] || path
  )
})

const displayName = computed(
  () => (authStore.user && (authStore.user.realName || authStore.user.username)) || '未知用户'
)
const avatarText = computed(() => displayName.value.slice(0, 1))
const roleLabel = computed(() => ROLE_LABEL[(authStore.user && authStore.user.role) || ''] || '未知角色')
const roleTagDict = computed(() => ({ role: { label: roleLabel.value } }))

// 面包屑（A3-3）：三级页（如「财务管理 → 工资单 → PAY-202608-0004」）的第三段含单号，靠常量拼不出来，
// 故优先用路由 meta.breadcrumb 显式声明；未声明时回退「分组名 + 页面标题」两段。
const breadcrumbs = computed(() => {
  const { breadcrumb, group, title } = route.meta || {}
  if (breadcrumb && breadcrumb.length) return breadcrumb
  const items = []
  if (group && MENU_GROUPS[group]) items.push(MENU_GROUPS[group])
  if (title) items.push(title)
  return items.length ? items : ['首页']
})

// 进度条靠 key 变化重放动画：每次路由变更 +1，动画结束就停在 opacity:0
const progressKey = ref(0)
watch(
  () => route.fullPath,
  () => {
    progressKey.value += 1
  }
)

function handleCommand(command) {
  if (command === 'profile') router.push('/profile')
  else if (command === 'logout') handleLogout()
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
  // 接口失败兜底与清登录态都在 useLogout 内，这里只负责确认与反馈
  await logout()
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
  background-color: var(--surface-aside);
  transition: width var(--dur-base) var(--ease-std);
  overflow: hidden;

  .app-logo {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--sp-2);
    height: var(--header-h);
    flex-shrink: 0;

    &__icon {
      /* logo 图标：图标用途按硬规则走 500 档别名，--color-primary 已改 700 档不再适用 */
      color: var(--color-primary-icon);
    }

    &__title {
      font-size: var(--fs-h3);
      font-weight: var(--fw-semibold);
      color: var(--text-inverse);
      white-space: nowrap;
    }
  }

  .app-menu {
    flex: 1;
    overflow-y: auto;
    border-right: none;
    background-color: transparent;

    // 深色侧栏靠这一组变量取色，模板里不再写 background-color / text-color 属性
    --el-menu-bg-color: transparent;
    --el-menu-text-color: rgba(255, 255, 255, 0.72);
    --el-menu-active-color: var(--text-inverse);
    --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
    --el-menu-item-height: 44px;
    --el-menu-sub-item-height: 40px;
    --el-menu-item-font-size: var(--fs-body);
    --el-menu-base-level-padding: var(--sp-4);
    --el-menu-level-padding: var(--sp-2);

    &:not(.el-menu--collapse) {
      width: var(--aside-w);
    }

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      position: relative;
    }

    // 激活项：3px 左主色条 + 白字，不靠背景色深浅区分
    :deep(.el-menu-item.is-active) {
      background-color: rgba(255, 255, 255, 0.08);
      font-weight: var(--fw-medium);

      &::before {
        content: '';
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        width: 3px;

        /* 激活项 3px 主色条：装饰性竖线不承载白字，按硬规则走 500 档 */
        background-color: var(--color-primary-icon);
      }
    }

    // 分组标题：Caption 级、更弱，避免与菜单项抢注意力
    :deep(.el-sub-menu__title) {
      font-size: var(--fs-caption);
      color: rgba(255, 255, 255, 0.45);
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
  height: var(--header-h);
  padding: 0 var(--sp-5);
  background-color: var(--surface-card);
  border-bottom: 1px solid var(--border-line);

  .header-left {
    display: flex;
    align-items: center;
    gap: var(--sp-4);
  }

  // 32×32 热区：图标本身只有 18px，鼠标与键盘都要点得中
  .collapse-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    border: none;
    border-radius: var(--r-sm);
    background-color: transparent;
    color: var(--text-2);
    cursor: pointer;
    transition:
      background-color var(--dur-fast) var(--ease-std),
      color var(--dur-fast) var(--ease-std);

    &:hover {
      background-color: var(--surface-sunken);
      color: var(--color-primary-strong);
    }
  }

  .header-user {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
    height: 36px;
    padding: 0 var(--sp-2);
    border: none;
    border-radius: var(--r-sm);
    background-color: transparent;
    font-family: inherit;
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-std);

    &:hover {
      background-color: var(--surface-subtle);
    }

    &__avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: var(--r-full);
      background-color: var(--color-primary-strong);
      color: var(--text-inverse);
      font-size: var(--fs-body);
    }

    &__name {
      font-size: var(--fs-body);
      color: var(--text-1);
    }
  }

  :deep(.el-breadcrumb__inner) {
    color: var(--text-3);
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: var(--text-1);
  }

  :deep(.el-breadcrumb__separator) {
    color: var(--text-3);
  }
}

.app-main {
  /* 宽屏（≥1280）内容不再无限撑开：1920 下表格列被拉散、行高视觉失衡（P2-3）。
   * 显式写 width:100% 再叠 max-width，避免 flex 交叉轴 stretch 与自动外边距互相干扰；
   * 两侧留出的空白与 body 同为 --surface-page，视觉上无缝 */
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: var(--sp-4);
  background-color: var(--surface-page);
  overflow-y: auto;
}
</style>
