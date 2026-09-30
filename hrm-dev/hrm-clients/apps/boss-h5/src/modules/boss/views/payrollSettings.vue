<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import { getPayrollSettings } from '@/api/finance.js'

/**
 * 财务管理（含「员工工资设置」）· 由算薪日设置列表原地升级（boss-management-ui-design.md ③）
 *
 * 为什么原地升级而不是新建 finance hub：0 新文件/组件、入口收口（算薪日设置并入本页），
 * 同时承载「请假扣款设置」这个此前散落在「我的」的入口（设计 ③.1 决策）。
 * 「自动算薪运行」入口已移除（前端不再展示运行记录/手工触发，后端能力保留）。
 * 安全语义必须前置：enabled 默认 0（未启用不自动跑数），所以顶部提示条先讲清「启用后才会自动生成」，
 * 否则管理员会以为「配了算薪日就一定会跑」。
 * 驿站数量为个位数（种子 8 个），一次取全后在前端做关键字过滤，避免为一个可有可无的搜索再加一次分页请求。
 */
const ROUTE_BASE = '/boss/payroll-settings'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const list = ref([])
const keyword = ref('')
/** '' 全部 / '1' 已启用 / '0' 未启用；enabled 过滤由服务端收敛，关键字过滤在前端（站点量小） */
const enabledFilter = ref('')

const ENABLED_FILTERS = [
  { value: '', label: '全部' },
  { value: '1', label: '已启用' },
  { value: '0', label: '未启用' }
]

const filtered = computed(() => {
  const text = keyword.value.trim()
  if (!text) return list.value
  return list.value.filter((item) => String(item.stationName || '').includes(text))
})

/** 未配置（payrollDay 为空）显示「尚未配置」，对应 9406 语义 */
function planText(item) {
  if (item.payrollDay == null || !item.payrollTime) return '尚未配置'
  return `每月 ${item.payrollDay} 日 ${item.payrollTime}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { pageNum: 1, pageSize: 20 }
    if (enabledFilter.value !== '') params.enabled = enabledFilter.value
    const page = await getPayrollSettings(params)
    list.value = page.list || []
  } catch (e) {
    error.value = e.message || '算薪日设置加载失败'
  } finally {
    loading.value = false
  }
}

function selectEnabled(value) {
  if (enabledFilter.value === value) return
  enabledFilter.value = value
  load()
}

function openEdit(id) {
  router.push(`${ROUTE_BASE}/${id}`)
}

onMounted(load)
</script>

<template>
  <div class="payroll-settings">
    <PageNav title="财务管理" />
    <div class="page page--loose">
      <van-notice-bar
        class="notice"
        left-icon="info-o"
        text="启用后系统在每驿站各自的算薪日自动生成工资单草稿并提交审核；未启用的驿站不自动跑数。"
        wrapable
        color="var(--color-primary)"
        background="var(--color-primary-surface)"
      />

      <div class="section-title">
        <span>员工工资设置</span>
        <span class="section-title__extra tabular-nums">共 {{ filtered.length }} 个驿站</span>
      </div>

      <van-field
        v-model="keyword"
        class="search"
        label="驿站"
        placeholder="输入驿站名筛选"
        clearable
        aria-label="按驿站名筛选"
      />

      <div class="filter-row" role="group" aria-label="按启用状态筛选">
        <button
          v-for="item in ENABLED_FILTERS"
          :key="item.value"
          type="button"
          class="fchip"
          :class="{ 'fchip--active': enabledFilter === item.value }"
          :aria-pressed="enabledFilter === item.value"
          @click="selectEnabled(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <PageState
        :loading="loading"
        :error="error"
        :rows="3"
        :empty="!filtered.length"
        empty-text="还没有可配置的驿站"
        @retry="load"
      >
        <template #empty-action>
          <p class="tip">请先在 PC 端维护驿站</p>
        </template>

        <button
          v-for="item in filtered"
          :key="item.stationId"
          type="button"
          class="list-item list-item--rich set-row"
          @click="openEdit(item.stationId)"
        >
          <div class="list-item__title">
            <span>{{ item.stationName }}</span>
            <span class="state-pill" :class="item.enabled === 1 ? 'state-pill--on' : 'state-pill--off'">
              {{ item.enabled === 1 ? '已启用' : '未启用' }}
            </span>
          </div>
          <div class="list-item__meta tabular-nums" :class="{ 'list-item__meta--danger': item.payrollDay == null }">
            {{ planText(item) }}
          </div>
          <van-icon class="set-row__arrow" name="arrow" aria-hidden="true" />
        </button>
      </PageState>

      <div class="section-title">相关设置</div>
      <van-cell-group inset>
        <van-cell title="请假扣款设置" label="全局单开关：请假是否影响工资" is-link to="/boss/leave/settings" />
      </van-cell-group>

      <p class="tip">工资单由财务端生成草稿后进入审核流；算薪日设置仅决定是否自动跑数与跑数时间。</p>
    </div>
  </div>
</template>

<style scoped>
.search {
  margin-top: var(--sp-3);
  background: var(--surface-card);
  border-radius: var(--r-sm);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.fchip {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.fchip--active {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.set-row {
  position: relative;
  display: block;
  width: 100%;
  margin-top: var(--sp-3);
  text-align: left;
  border: none;
}

.set-row__arrow {
  position: absolute;
  top: var(--sp-4);
  right: var(--sp-4);
  color: var(--text-3);
}

.state-pill {
  display: inline-flex;
  align-items: center;
  height: var(--tag-h);
  padding: 0 var(--tag-pad-x);
  font-size: var(--fs-micro);
  border-radius: var(--r-full);
}

.state-pill--on {
  color: var(--color-success);
  background: var(--color-success-surface);
}

.state-pill--off {
  color: var(--text-3);
  background: var(--surface-card);
  border: 1px solid var(--state-outline-border);
}
</style>
