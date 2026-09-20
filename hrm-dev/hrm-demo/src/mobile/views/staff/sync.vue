<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { getSyncLogs, getSyncOverview, getSyncTasks } from '../../api/index.js'
import { COLLECT_STATE, SYNC_LOG_LEVEL, SYNC_STATUS } from '@/shared/constants/dict.js'
import { durationText, parseTime, relativeTime } from '../../utils/format.js'

/**
 * S9 同步状态（仅 STATION_ADMIN，路由守卫已拦截 STAFF）
 * 只读：触发与重试留给 PC 端同步任务页，移动端现场只看「本站最近同步是否正常」。
 * 失败批次左侧加 3px 竖条：一眼扫出哪个批次要处理，不依赖逐行读文字。
 * 顶部采集状态卡（需求1）：站长只看本站，采集配置仍在 PC 维护，页面不提供编辑入口。
 */
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const showLogs = ref(false)
const logs = ref([])
const logsLoading = ref(false)
const currentTask = ref(null)

/** 采集状态独立取数与独立三态：它挂了不该把批次流水一起判成错误页 */
const collectLoading = ref(true)
const collectError = ref('')
const collectStation = ref(null)

const latest = computed(() => list.value[0] || null)

/** 单批耗时：start_time → finish_time（执行中/待领取无耗时） */
function costText(task) {
  const start = parseTime(task.startTime)
  const finish = parseTime(task.finishTime)
  if (!start || !finish) return '-'
  return durationText(finish - start)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getSyncTasks({ pageNum: 1, pageSize: 30 })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadCollect() {
  collectLoading.value = true
  collectError.value = ''
  try {
    // 服务端按登录人收敛到本站，这里取第一条即可（站长只会拿到本站一行）
    const data = await getSyncOverview()
    collectStation.value = data.stations[0] || null
  } catch (e) {
    collectError.value = e.message || '采集状态加载失败'
  } finally {
    collectLoading.value = false
  }
}

async function openLogs(task) {
  currentTask.value = task
  showLogs.value = true
  logsLoading.value = true
  try {
    logs.value = await getSyncLogs(task.id)
  } catch (e) {
    logs.value = []
  } finally {
    logsLoading.value = false
  }
}

onMounted(() => {
  load()
  loadCollect()
})
</script>

<template>
  <div class="sync-page">
    <PageNav title="同步状态" />
    <div class="page page--loose">
      <!-- 采集状态（需求1）：站长只看本站，1 张卡 + 3 行；四态口径由服务端派生，页面不做二次判定 -->
      <div class="card collect-card">
        <div class="flex-between">
          <span class="text-strong">本站采集状态</span>
          <StatusTag v-if="collectStation" :dict="COLLECT_STATE" :value="collectStation.collectState" />
        </div>
        <div v-if="collectLoading" class="skeleton-block collect-skeleton" />

        <div v-else-if="collectError" class="collect-error" role="alert">
          <p class="latest__meta">{{ collectError }}</p>
          <van-button size="small" plain type="primary" @click="loadCollect">重新加载</van-button>
        </div>

        <p v-else-if="!collectStation" class="latest__meta">
          本站暂无采集配置，请由管理员在 PC 端「数据同步 → 采集配置」中完成配置
        </p>

        <van-cell-group v-else :border="false" class="collect-cells">
          <van-cell title="数据源" :value="collectStation.dataSource || '未配置'" />
          <van-cell title="采集开关" :value="collectStation.enabled ? '已开启' : '已关闭'" />
          <van-cell
            title="最后采集"
            :value="collectStation.lastCollectTime ? relativeTime(collectStation.lastCollectTime) : '从未采集'"
          />
        </van-cell-group>
        <p v-if="collectStation" class="tip">采集开关、频次与数据源在 PC 端维护，移动端只读</p>
      </div>

      <PageState
        :loading="loading"
        :error="error"
        :empty="!list.length"
        empty-text="本站暂无同步批次"
        :rows="5"
        @retry="load"
      >
        <div class="card latest">
          <div class="flex-between">
            <span class="text-strong">最近同步批次</span>
            <StatusTag :dict="SYNC_STATUS" :value="latest.status" />
          </div>
          <div class="latest__batch">{{ latest.batchNo }}</div>
          <div class="latest__meta">
            创建 {{ relativeTime(latest.createTime) }} · 抓取 {{ latest.parcelTotal }} 条 · 成功
            {{ latest.successCount }} / 失败
            {{ latest.failCount }}
          </div>
          <div v-if="latest.errorMsg" class="latest__error">
            <van-icon name="warning-o" aria-hidden="true" /> {{ latest.errorMsg }}
          </div>
          <div class="latest__foot">
            <van-button size="small" plain type="primary" @click="openLogs(latest)">查看日志</van-button>
          </div>
        </div>

        <div class="section-title">
          历史批次<span class="section-title__extra">共 {{ total }} 个，展示最近 {{ list.length }} 个</span>
        </div>
        <div
          v-for="task in list"
          :key="task.id"
          class="list-item"
          :class="{ 'list-item--failed': task.status === 3 }"
          role="button"
          tabindex="0"
          @click="openLogs(task)"
          @keydown.enter="openLogs(task)"
          @keydown.space.prevent="openLogs(task)"
        >
          <div class="list-item__title">
            <span>{{ task.batchNo }}</span>
            <StatusTag :dict="SYNC_STATUS" :value="task.status" />
          </div>
          <div class="list-item__meta">
            {{ relativeTime(task.createTime) }} · 包裹 {{ task.parcelTotal }} · 耗时 {{ costText(task) }} · 重试
            {{ task.retryCount }} 次
          </div>
          <div v-if="task.errorMsg" class="list-item__meta list-item__meta--danger">{{ task.errorMsg }}</div>
        </div>

        <p class="tip">同步状态机为四态：待领取 → 执行中 → 成功 / 失败；仅失败可重试（重试在 PC 端操作）</p>
      </PageState>
    </div>

    <van-popup v-model:show="showLogs" round position="bottom" safe-area-inset-bottom>
      <div class="log-pop">
        <div class="log-pop__title">批次日志 · {{ currentTask && currentTask.batchNo }}</div>
        <div class="log-pop__body">
          <van-loading v-if="logsLoading" class="log-pop__loading">加载中…</van-loading>
          <p v-else-if="!logs.length" class="muted">该批次暂无日志</p>
          <div v-for="log in logs" :key="log.id" class="log-item">
            <StatusTag :dict="SYNC_LOG_LEVEL" :value="log.level" />
            <span class="log-item__msg">{{ log.message }}</span>
            <span class="log-item__time">{{ log.logTime }}</span>
          </div>
        </div>
        <div class="log-pop__foot">
          <van-button block plain type="primary" @click="showLogs = false">关闭</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.collect-card {
  margin-top: var(--sp-3);
}

/* 卡片已留 16px 内边距，cell 再给 16px 会把文字挤到中间 */
.collect-cells {
  margin-top: var(--sp-2);

  --van-cell-horizontal-padding: 0px;
}

.collect-skeleton {
  height: 96px;
  margin-top: var(--sp-3);
}

.collect-error {
  margin-top: var(--sp-2);
}

.latest {
  margin-top: var(--sp-3);
}

.latest__batch {
  margin-top: var(--sp-2);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
}

.latest__meta {
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.latest__error {
  display: flex;
  gap: var(--sp-1);
  align-items: center;
  margin-top: var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--color-danger);
}

.latest__foot {
  margin-top: var(--sp-3);
}

.log-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.log-pop__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.log-pop__body {
  max-height: 56vh;
  padding: 0 var(--sp-4);
  overflow-y: auto;
}

.log-pop__loading {
  justify-content: center;
  padding: var(--sp-4) 0;
}

.log-item {
  display: flex;
  gap: var(--sp-2);
  align-items: flex-start;
  padding: var(--sp-2) 0;
  font-size: var(--fs-caption);
  border-bottom: 1px solid var(--border-line);
}

.log-item__msg {
  flex: 1;
  min-width: 0;
  color: var(--text-2);
}

.log-item__time {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.log-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
