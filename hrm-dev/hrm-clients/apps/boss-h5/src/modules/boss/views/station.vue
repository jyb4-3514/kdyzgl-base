<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { STATION_STATUS } from '@kdyzgl/shared/constants/dict.js'
import { getStationList } from '@/api/org.js'

/**
 * 驿站管理（列表）· 管理能力扩展 ②（boss-management-ui-design.md ④.2）
 *
 * 为什么由「只读骨架」重构为列表：需求要求驿站可增可改，账号在驿站内维护；
 * 原「先选站再看名册」的入口级选站弹层移除（详情页已由路由上下文确定站点）。
 * 数据源 GET /stations（ADMIN，全量不分页，驿站基数 <100）：关键字 + 状态筛选在前端做，
 * 不新增分页请求（契约事实，设计 ⑫#9）。
 */
const ROUTE_BASE = '/boss/station'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const list = ref([])
const keyword = ref('')
const statusFilter = ref('')

const STATUS_FILTERS = [
  { value: '', label: '全部' },
  { value: '1', label: '启用' },
  { value: '0', label: '停用' }
]

const filtered = computed(() => {
  const text = keyword.value.trim()
  return list.value.filter((item) => {
    const matchStatus = statusFilter.value === '' || String(item.status) === statusFilter.value
    const matchText =
      !text || String(item.stationName || '').includes(text) || String(item.code || '').includes(text)
    return matchStatus && matchText
  })
})

/** 是否处于筛选态：空态文案据此二选一（空态不得出现「失败/错误/网络」字样） */
const isFiltering = computed(() => !!keyword.value.trim() || statusFilter.value !== '')
const emptyText = computed(() => (isFiltering.value ? '没有匹配的驿站' : '还没有驿站'))

const actions = computed(() => [{ key: 'create', label: '新增驿站' }])

async function load() {
  loading.value = true
  error.value = ''
  try {
    list.value = await getStationList()
  } catch (e) {
    error.value = e.message || '驿站列表加载失败'
  } finally {
    loading.value = false
  }
}

function selectStatus(value) {
  statusFilter.value = value
}

function openDetail(item) {
  router.push(`${ROUTE_BASE}/${item.id}`)
}

function onCreate() {
  router.push(`${ROUTE_BASE}/create`)
}

onMounted(load)
</script>

<template>
  <div class="station-page">
    <PageNav title="驿站管理" />
    <div class="page page--bar">
      <van-search
        v-model="keyword"
        class="search"
        placeholder="搜索驿站名或编号"
        shape="round"
        aria-label="搜索驿站名或编号"
      />

      <div class="filter-row" role="group" aria-label="按启用状态筛选">
        <button
          v-for="item in STATUS_FILTERS"
          :key="item.value"
          type="button"
          class="fchip"
          :class="{ 'fchip--active': statusFilter === item.value }"
          :aria-pressed="statusFilter === item.value"
          @click="selectStatus(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <p class="tool-row tabular-nums">共 {{ filtered.length }} 个驿站</p>

      <!-- 首屏单独走骨架（高度对齐 list-item--rich）；主操作在 loading 期间保持可点（不依赖列表数据） -->
      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!filtered.length" :empty-text="emptyText" @retry="load">
        <template #empty-action>
          <p class="tip">
            {{ isFiltering ? '换个关键字或清除筛选' : '点下方「新增驿站」创建第一个驿站' }}
          </p>
        </template>

        <button
          v-for="item in filtered"
          :key="item.id"
          type="button"
          class="list-item list-item--rich st-row"
          @click="openDetail(item)"
        >
          <div class="list-item__title">
            <span>{{ item.stationName }}</span>
            <span class="st-row__right">
              <StatusTag :dict="STATION_STATUS" :value="String(item.status)" />
              <van-icon name="arrow" aria-hidden="true" />
            </span>
          </div>
          <div class="list-item__meta tabular-nums">编号 {{ item.code }}</div>
          <div class="list-item__meta">{{ item.contactPerson || '—' }} · {{ item.contactPhone || '—' }}</div>
          <div class="list-item__meta tabular-nums">员工 {{ item.employeeCount }} 人</div>
        </button>
      </PageState>
    </div>

    <ActionBar :actions="actions" @select="onCreate" />
  </div>
</template>

<style scoped>
.search {
  margin-top: var(--sp-3);
  border-radius: var(--r-lg);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.sk-row {
  height: 100px;
  margin-top: var(--sp-3);
}

.st-row {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
}

.st-row__right {
  display: inline-flex;
  flex: none;
  gap: var(--sp-2);
  align-items: center;
  color: var(--text-3);
}
</style>
