<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import ActionBar from '@kdyzgl/shared/ui/ActionBar.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { STATION_STATUS } from '@kdyzgl/shared/constants/dict.js'
import { bossConfirm } from '../components/bossConfirm.js'
import FilterChips from '@/components/FilterChips.vue'
import MiniChip from '@/components/MiniChip.vue'
import { deleteStation, getEmployees, getStationList, updateStationStatus } from '@/api/org.js'
import { EMPLOYEE_STATUS, roleLabel } from '@/constants/accounts.js'

/**
 * 驿站详情 · 管理能力扩展 ②（boss-management-ui-design.md ④.4）
 *
 * 取数口径：驿站详情复用 GET /stations（全量）内存按 id 定位，不新增 GET /stations/{id}（架构 D-2）；
 * 账号区复用 GET /employees?stationId=，一次 pageSize=100 拉全站在内存过滤（设计 ④.4.1）。
 *
 * 口径（用户澄清）：站长（STATION_ADMIN）只是员工账号上的一个身份，不是独立账号体系；
 * 故站点账号为**一个统一列表**，身份（员工 / 站长）作为列表的筛选与标识维度，不再拆两个区块。
 * 计数按过滤后的账号数计，不用 employeeCount 冒充（后者含全角色）。
 */
const route = useRoute()
const router = useRouter()
const stationId = computed(() => Number(route.params.id))
const ROUTE_BASE = '/boss/station'

const loading = ref(true)
const error = ref('')
const station = ref(null)

const accountsLoading = ref(true)
const accountsError = ref('')
const employees = ref([])
const keyword = ref('')
const roleFilter = ref('')

/** 身份筛选：站长与员工同列一表，身份是筛选维度（用户口径：站长非独立账号体系） */
const IDENTITY_FILTERS = [
  { value: '', label: '全部' },
  { value: 'STAFF', label: '员工' },
  { value: 'STATION_ADMIN', label: '站长' }
]

/** 单搜索入口：拆两个搜索框会让用户分不清「搜的是谁」 */
const matchKeyword = (item) => {
  const text = keyword.value.trim()
  if (!text) return true
  return String(item.realName || '').includes(text) || String(item.username || '').includes(text)
}

/** 仅员工账号（含站长身份）；ADMIN 属系统管理员账号，不在驿站账号维护范围 */
const accounts = computed(() =>
  employees.value.filter(
    (e) =>
      (e.role === 'STAFF' || e.role === 'STATION_ADMIN') &&
      (roleFilter.value === '' || e.role === roleFilter.value) &&
      matchKeyword(e)
  )
)

/** 是否处于筛选态：空态文案据此二选一（空态不得出现「失败/错误/网络」字样） */
const isFiltering = computed(() => !!keyword.value.trim() || roleFilter.value !== '')

const barNote = computed(() => {
  if (!station.value) return ''
  const count = station.value.employeeCount || 0
  return count > 0 ? `该驿站下仍有 ${count} 名员工，无法删除` : ''
})

const actions = computed(() => {
  if (!station.value) return []
  return [
    { key: 'edit', label: '编辑驿站' },
    { key: 'status', label: station.value.status === 1 ? '停用驿站' : '启用驿站', type: 'danger' },
    { key: 'delete', label: '删除驿站', type: 'danger', disabled: (station.value.employeeCount || 0) > 0 }
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await getStationList()
    const hit = list.find((item) => item.id === stationId.value)
    if (!hit) {
      error.value = '驿站不存在，请刷新后重试'
      return
    }
    station.value = hit
  } catch (e) {
    error.value = e.message || '驿站详情加载失败'
  } finally {
    loading.value = false
  }
}

async function loadAccounts() {
  accountsLoading.value = true
  accountsError.value = ''
  try {
    const page = await getEmployees({ stationId: stationId.value, pageNum: 1, pageSize: 100 })
    employees.value = page.list || []
  } catch (e) {
    accountsError.value = e.message || '账号列表加载失败'
  } finally {
    accountsLoading.value = false
  }
}

async function onToggleStatus() {
  const target = station.value
  const next = target.status === 1 ? 0 : 1
  if (next === 0) {
    const ok = await bossConfirm({
      action: '停用驿站',
      target: target.stationName,
      impact: '新增与编辑员工时不可再选择该驿站，存量员工归属与登录不受影响',
      confirmText: '确认停用'
    })
    if (!ok) return
  }
  try {
    await updateStationStatus(target.id, next)
    showSuccessToast(next === 1 ? '驿站已启用' : '驿站已停用')
    await load()
  } catch (e) {
    error.value = e.message || '状态更新失败'
  }
}

async function onDelete() {
  const target = station.value
  const ok = await bossConfirm({
    action: '删除驿站',
    target: target.stationName,
    impact: '删除后不可恢复；该驿站下无员工，账号归属不受影响',
    irreversible: true,
    confirmText: '确认删除'
  })
  if (!ok) return
  try {
    await deleteStation(target.id)
    showSuccessToast('驿站已删除')
    router.replace(ROUTE_BASE)
  } catch (e) {
    error.value = e.message || '删除失败，请稍后重试'
  }
}

function onAction(key) {
  if (key === 'edit') router.push(`${ROUTE_BASE}/${stationId.value}/edit`)
  else if (key === 'status') onToggleStatus()
  else if (key === 'delete') onDelete()
}

function openAccount(item) {
  router.push(`${ROUTE_BASE}/${stationId.value}/account/${item.id}`)
}

function createAccount() {
  // 身份按当前筛选预置（站长筛选下默认建站长，否则员工）；具体身份在表单内可改
  const preset = roleFilter.value === 'STATION_ADMIN' ? 'STATION_ADMIN' : 'STAFF'
  router.push({ path: `${ROUTE_BASE}/${stationId.value}/account/create`, query: { role: preset } })
}

onMounted(() => {
  load()
  loadAccounts()
})
</script>

<template>
  <div class="station-detail">
    <PageNav title="驿站详情" />
    <div class="page page--bar">
      <PageState :loading="loading" :error="error" :rows="4" @retry="load">
        <template v-if="station">
          <section class="hero hero--deep st-hero">
            <div class="flex-between">
              <span class="hero__title">{{ station.stationName }}</span>
              <StatusTag :dict="STATION_STATUS" :value="String(station.status)" />
            </div>
            <p class="hero__sub tabular-nums">编号 {{ station.code }} · 员工 {{ station.employeeCount }} 人</p>
          </section>

          <van-notice-bar
            v-if="station.status !== 1"
            class="notice"
            left-icon="info-o"
            text="该驿站已停用：新增或编辑员工时不可再归属本驿站，存量员工归属与登录不受影响。"
            wrapable
            color="var(--color-warning)"
            background="var(--color-warning-surface)"
          />

          <div class="section-title">基本信息</div>
          <van-cell-group inset>
            <van-cell title="联系人" :value="station.contactPerson || '-'" />
            <van-cell title="联系电话" :value="station.contactPhone || '-'" />
            <van-cell title="地址" :value="station.address || '-'" />
            <van-cell title="备注" :value="station.remark || '-'" />
            <van-cell title="创建时间" :value="station.createTime || '-'" />
          </van-cell-group>

          <van-search
            v-model="keyword"
            class="search"
            placeholder="搜索姓名或登录账号"
            shape="round"
            aria-label="搜索账号姓名或登录账号"
          />

          <FilterChips
            :items="IDENTITY_FILTERS"
            :active="roleFilter"
            label="按身份筛选"
            @change="roleFilter = $event"
          />

          <!-- 统一账号列表（含站长身份）：加载/错误为区块级，不替换整页（设计 ④.4.1）；搜索与身份筛选联动过滤 -->
          <div class="section-title">
            <span>账号</span>
            <span class="section-title__extra tabular-nums">共 {{ accounts.length }} 人</span>
          </div>
          <div class="card account-card">
            <div v-if="accountsLoading" class="account-sk" aria-busy="true">
              <div v-for="i in 3" :key="i" class="skeleton-block account-sk__row" />
            </div>
            <div v-else-if="accountsError" class="account-error" role="alert">
              <p class="account-error__text">{{ accountsError }}</p>
              <button type="button" class="account-error__retry" @click="loadAccounts">重新加载</button>
            </div>
            <template v-else>
              <button
                v-for="item in accounts"
                :key="item.id"
                type="button"
                class="account-row"
                @click="openAccount(item)"
              >
                <div class="list-item__title">
                  <span>{{ item.realName }}</span>
                  <span class="account-row__tags">
                    <MiniChip :text="roleLabel(item.role)" :on="item.role === 'STATION_ADMIN'" tone="primary" />
                    <StatusTag :dict="EMPLOYEE_STATUS" :value="String(item.status)" />
                  </span>
                </div>
                <div class="list-item__meta">{{ item.username }}</div>
                <div class="list-item__meta tabular-nums">{{ item.phone }}</div>
                <div class="list-item__meta tabular-nums">入职 {{ item.entryDate || '—' }}</div>
              </button>
              <p v-if="!accounts.length" class="tip">
                {{ isFiltering ? '没有匹配的账号' : '该驿站还没有账号；点下方按钮新增' }}
              </p>
              <van-button class="account-add" size="small" plain type="primary" block @click="createAccount()">
                <template #icon><van-icon name="plus" aria-hidden="true" /></template>
                新增账号
              </van-button>
            </template>
          </div>

          <p class="tip">
            账号作用域为所属驿站；站长是员工账号上的身份（与员工共用同一套账号体系），可登录员工端并使用本站范围的功能，不开放驿站与账号管理。
          </p>
        </template>
      </PageState>
    </div>

    <ActionBar :actions="actions" :note="barNote" @select="onAction" />
  </div>
</template>

<style scoped>
.st-hero {
  margin-top: var(--sp-3);
}

.search {
  margin-top: var(--sp-3);
  border-radius: var(--r-lg);
}

.account-card {
  padding: 0 var(--sp-4);
}

.account-row {
  display: block;
  width: 100%;
  min-height: var(--row-h-3);
  padding: var(--sp-3) 0;
  text-align: left;
  background: none;
  border: none;
  border-bottom: 1px solid var(--border-line);
}

.account-row:last-of-type {
  border-bottom: none;
}

/* 身份胶囊 + 状态胶囊右对齐并排；身份是列表的标识维度（用户口径） */
.account-row__tags {
  display: inline-flex;
  flex: none;
  gap: var(--sp-2);
  align-items: center;
}

.account-sk {
  padding: var(--sp-3) 0;
}

.account-sk__row {
  height: var(--row-h-3);
  margin-top: var(--sp-2);
}

.account-error {
  padding: var(--sp-4) 0;
  text-align: center;
}

.account-error__text {
  margin: 0;
  font-size: var(--fs-body);
  color: var(--color-danger);
}

.account-error__retry {
  min-height: var(--touch-min);
  padding: 0 var(--sp-5);
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-icon);
  border-radius: var(--r-full);
}

.account-add {
  min-height: var(--touch-min);
  margin: var(--sp-3) 0;
}
</style>
