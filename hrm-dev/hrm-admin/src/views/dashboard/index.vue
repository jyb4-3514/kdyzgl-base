<template>
  <div class="dashboard-page">
    <!-- 4 指标卡片：员工总数/驿站数/部门数/今日登录数（api.md 4.2） -->
    <el-row v-loading="loading" :gutter="16">
      <el-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :class="`icon-${card.theme}`">
              <el-icon :size="28"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-meta">
              <div class="stat-value">{{ summary ? summary[card.key] : '-' }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 错误态：可重试 -->
    <el-card v-if="error" shadow="never" class="error-card">
      <el-empty :description="error">
        <el-button type="primary" @click="loadSummary">重试</el-button>
      </el-empty>
    </el-card>

    <el-card v-else-if="!loading && summary" shadow="never" class="note-card">
      <el-alert
        type="info"
        :closable="false"
        title="统计口径说明"
        description="员工总数含禁用账号；驿站数含停用驿站；今日登录数按去重员工数统计（截至当日零点起）。"
        show-icon
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { User, OfficeBuilding, Share, DataLine } from '@element-plus/icons-vue'
import { getSummary } from '../../api/dashboard'

const loading = ref(false)
const error = ref('')
const summary = ref(null)

const cards = [
  { key: 'employeeTotal', label: '员工总数', icon: User, theme: 'blue' },
  { key: 'stationTotal', label: '驿站数', icon: OfficeBuilding, theme: 'green' },
  { key: 'departmentTotal', label: '部门数', icon: Share, theme: 'orange' },
  { key: 'todayLoginCount', label: '今日登录数', icon: DataLine, theme: 'purple' }
]

async function loadSummary() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await getSummary()
  } catch (err) {
    // 拦截器已弹出错误提示，此处仅渲染错误态
    error.value = (err && err.message) || '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadSummary)
</script>

<style scoped lang="scss">
.dashboard-page {
  .stat-card {
    margin-bottom: 16px;
    border-radius: 8px;

    .stat-content {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .stat-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 12px;
      color: var(--c-white);
      flex-shrink: 0;

      &.icon-blue {
        background: linear-gradient(135deg, var(--c-stat-blue) 0%, var(--c-brand-600) 100%);
      }

      &.icon-green {
        background: linear-gradient(135deg, var(--c-stat-green) 0%, var(--c-stat-teal) 100%);
      }

      &.icon-orange {
        background: linear-gradient(135deg, var(--c-stat-orange) 0%, var(--c-stat-orange-deep) 100%);
      }

      &.icon-purple {
        background: linear-gradient(135deg, var(--c-stat-purple) 0%, var(--c-stat-indigo) 100%);
      }
    }

    .stat-meta {
      .stat-value {
        font-size: 28px;
        font-weight: 600;
        color: var(--c-text-primary);
        line-height: 1.2;
      }

      .stat-label {
        margin-top: 4px;
        font-size: 13px;
        color: var(--text-3);
      }
    }
  }

  .error-card,
  .note-card {
    border-radius: 8px;
  }
}
</style>
