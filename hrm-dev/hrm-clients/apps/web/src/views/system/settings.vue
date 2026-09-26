<template>
  <div class="settings-page">
    <PageHeader title="系统设置" :sub="headerSub" />

    <!-- S1 系统信息：全部来自构建期常量，同步即得，恒为 normal（不随骨架屏闪动） -->
    <el-card shadow="never" class="settings-card">
      <h2 class="settings-card__title">系统信息</h2>
      <el-descriptions :column="descColumns" border>
        <el-descriptions-item label="系统名称">
          <span class="settings-value">{{ appName }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="appVersion" label="版本号">
          <span class="settings-value">{{ appVersion }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="运行模式">
          <StatusTag :dict="runtimeTagDict" value="mode" :aria-label="`运行模式：${runtimeMode.label}`" />
        </el-descriptions-item>
        <el-descriptions-item v-if="buildMode" label="构建模式">
          <span class="settings-value">{{ buildMode }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- S2 运行环境：navigator / screen 快照，同步即得，恒为 normal -->
    <el-card v-if="envRows.length" shadow="never" class="settings-card">
      <h2 class="settings-card__title">运行环境</h2>
      <el-descriptions :column="descColumns" border>
        <el-descriptions-item v-for="row in envRows" :key="row.label" :label="row.label">
          <span class="settings-value">{{ row.value }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- S3 数据预置：全部为构建期常量 / 环境变量派生，同步即得。
         MVP 裁剪：原「已预置包裹总数」异步块（四态）随包裹族下架移除，本卡不再有异步取数 -->
    <el-card shadow="never" class="settings-card">
      <h2 class="settings-card__title">数据预置</h2>
      <el-descriptions :column="descColumns" border>
        <el-descriptions-item v-if="presetScale" label="预置规模（声明）">
          <span class="settings-value">{{ presetScale }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Mock 装载状态">
          <StatusTag :dict="runtimeTagDict" value="mode" :aria-label="`Mock 装载状态：${runtimeMode.label}`" />
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- S4 账号与权限：登录态 + 常量映射，同步即得，恒为 normal -->
    <el-card shadow="never" class="settings-card">
      <h2 class="settings-card__title">账号与权限</h2>
      <el-descriptions :column="descColumns" border>
        <el-descriptions-item v-if="accountName" label="当前账号">
          <span class="settings-value">{{ accountName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前角色">
          <StatusTag :dict="roleTagDict" value="role" variant="outline" :aria-label="`当前角色：${roleLabel}`" />
        </el-descriptions-item>
        <el-descriptions-item label="数据范围">
          <span class="settings-value">{{ dataScope }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="权限说明" :span="descColumns">
          <ul class="settings-notes">
            <li v-for="note in permissionNotes" :key="note">{{ note }}</li>
          </ul>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ROLE_LABEL } from '@kdyzgl/shared/constants/role'
import { APP_NAME } from '../../constants/brand.js'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import {
  APP_VERSION,
  PERMISSION_NOTES,
  filterRows,
  formatSize,
  parseBrowser,
  resolveDataScope,
  resolvePresetScale,
  resolveRuntimeMode,
  resolveTimezone
} from './model/settingsMeta.js'

/**
 * 系统设置（只读的「关于本系统」信息页，仅 ADMIN，与同组「运行日志」同口径）
 *
 * 四个分区：系统信息 / 运行环境 / 数据预置 / 账号与权限；全页不可编辑 ——
 * 系统名、版本、模式都是构建期常量或运行时事实，本工程是纯前端 + Mock，没有写入通道，
 * 摆一个"能改但保存无效"的控件只会误导用户（设计规范 §2.6）。
 *
 * MVP 裁剪：原 S3 的「已预置包裹总数」异步块随包裹族下架移除，本页已无异步取数，全页恒为 normal。
 */
const authStore = useAuthStore()

const MOCK_ENABLED = import.meta.env.VITE_MOCK_ENABLED === 'true'

const appName = APP_NAME
const appVersion = APP_VERSION
const buildMode = import.meta.env.MODE || ''
const runtimeMode = resolveRuntimeMode(MOCK_ENABLED)
const runtimeTagDict = { mode: { label: runtimeMode.label, type: runtimeMode.type } }
const permissionNotes = PERMISSION_NOTES

const now = new Date()
const pad = (value) => String(value).padStart(2, '0')
const headerSub = `仅 ADMIN 可见 · 只读信息 · 更新于 ${pad(now.getHours())}:${pad(now.getMinutes())}`

const accountName = computed(() => {
  const user = authStore.user
  if (!user) return ''
  return user.realName || user.username || ''
})
const roleLabel = computed(() => ROLE_LABEL[(authStore.user && authStore.user.role) || ''] || '未知角色')
// 角色标签与顶栏同形态（描边、中性色），口径不分裂
const roleTagDict = computed(() => ({ role: { label: roleLabel.value } }))
const dataScope = computed(() => resolveDataScope(authStore.user && authStore.user.role))

// 窄窗单列：两列在 1200 以下会把标签列挤成折行（与抽屉列数策略同源）
const descColumns = ref(2)
const viewport = ref({ width: window.innerWidth, height: window.innerHeight })
// 预置规模是同步派生值，生产态给「不适用」，演示态未配置数量时为空串（该行不渲染）
const presetScale = resolvePresetScale(MOCK_ENABLED, import.meta.env.VITE_MOCK_PARCEL_COUNT)

const envRows = computed(() =>
  filterRows([
    { label: '浏览器', value: parseBrowser(navigator.userAgent) },
    { label: '分辨率', value: formatSize(screen.width, screen.height) },
    { label: '视口', value: formatSize(viewport.value.width, viewport.value.height) },
    { label: '时区', value: resolveTimezone(Intl) },
    { label: '语言', value: navigator.language }
  ])
)

function syncLayout() {
  viewport.value = { width: window.innerWidth, height: window.innerHeight }
  descColumns.value = window.innerWidth >= 1200 ? 2 : 1
}

onMounted(() => {
  syncLayout()
  window.addEventListener('resize', syncLayout)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncLayout)
})
</script>

<style scoped lang="scss">
.settings-page {
  .settings-card {
    margin-bottom: var(--sp-4);

    &:last-child {
      margin-bottom: 0;
    }

    &__title {
      margin: 0 0 var(--sp-4);
      font-size: var(--fs-h3);
      font-weight: var(--fw-semibold);
      line-height: var(--lh-h3);
      color: var(--text-1);
    }
  }

  .settings-value {
    font-size: var(--fs-body);
    font-weight: var(--fw-medium);
    color: var(--text-1);
  }

  .settings-notes {
    margin: 0;
    padding-left: var(--sp-5);
    font-size: var(--fs-caption);
    line-height: var(--lh-body);
    color: var(--text-2);
  }

  :deep(.el-descriptions__label) {
    font-size: var(--fs-caption);
    color: var(--text-3);
  }
}
</style>
