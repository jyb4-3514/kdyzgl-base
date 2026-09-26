<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import { getHrProfiles } from '@/api/hr.js'

/**
 * B8 管理端 · 人事管理（员工档案查询 + 调薪入口）
 *
 * 为什么列表只做「查」、调薪放详情页：调薪要同时看到当前薪资、历史留痕与降薪差额，
 * 列表行里塞不下（塞进去就是一行四个数字，反而看不清）。
 *
 * 合同到期是本页唯一需要「提前知道」的事，因此按行业惯例在行内给标签 + 顶部计数。
 */
const PAGE_SIZE = 20

/** 合同预警阈值：30 天内到期（B8.3）；已过期为 danger */
const WARN_DAYS = 30
const CONTRACT_TAG = { soon: { label: '合同将至', type: 'warning' }, expired: { label: '合同已过期', type: 'danger' } }
const LEAVE_TAG = { yes: { label: '已离职', type: 'info' } }

const router = useRouter()
const keyword = ref('')
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)
/** 全量预警计数：列表分页只覆盖当前页，计数口径写清「已加载范围」，不冒充全量 */
const warnCount = ref({ soon: 0, expired: 0 })
const counted = ref(0)

function contractState(row) {
  if (!row.contractEnd) return ''
  const end = new Date(`${row.contractEnd}T23:59:59`).getTime()
  if (!Number.isFinite(end)) return ''
  if (end < Date.now()) return 'expired'
  if (end - Date.now() <= WARN_DAYS * 86400000) return 'soon'
  return ''
}

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  warnCount.value = { soon: 0, expired: 0 }
  counted.value = 0
  try {
    const page = await getHrProfiles({ keyword: keyword.value.trim() || undefined, pageNum: 1, pageSize: PAGE_SIZE })
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
    finished.value = list.value.length >= page.total
    countWarnings(list.value)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onLoadMore() {
  if (!pageNum.value) {
    loadingMore.value = false
    return
  }
  const next = pageNum.value + 1
  try {
    const page = await getHrProfiles({ keyword: keyword.value.trim() || undefined, pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
    countWarnings(page.list)
  } catch (e) {
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

function countWarnings(rows) {
  rows.forEach((row) => {
    const state = contractState(row)
    if (state) warnCount.value[state] += 1
  })
  counted.value += rows.length
}

onMounted(loadFirst)
</script>

<template>
  <div class="boss-hr">
    <PageNav title="人事管理" />
    <div class="page page--loose">
      <van-search v-model="keyword" placeholder="搜索姓名或登录账号" shape="round" @search="loadFirst" @clear="loadFirst" />

      <van-notice-bar
        v-if="warnCount.soon || warnCount.expired"
        class="notice"
        left-icon="warning-o"
        wrapable
        :text="`已加载 ${counted} 人中：合同 30 天内到期 ${warnCount.soon} 人，已过期 ${warnCount.expired} 人`"
        color="var(--color-warning)"
        background="var(--color-warning-surface)"
      />

      <p class="tool-row tabular-nums">共 {{ total }} 名在职员工</p>

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" empty-text="没有匹配的员工档案" @retry="loadFirst">
        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <button
            v-for="item in list"
            :key="item.employeeId"
            type="button"
            class="list-item list-item--rich hr-row"
            @click="router.push(`/boss/hr/${item.employeeId}`)"
          >
            <div class="list-item__title">
              <span>{{ item.employeeName }}</span>
              <van-icon name="arrow" aria-hidden="true" />
            </div>
            <div class="list-item__meta">
              {{ item.stationName || '总部' }} · {{ item.deptName || '未分配部门' }} · 入职 {{ item.entryDate || '-' }}
            </div>
            <div class="list-item__meta tabular-nums">
              {{ item.contractTypeLabel || '未建档'
              }}<template v-if="item.contractEnd"> · 到期 {{ item.contractEnd }}</template>
            </div>
            <div class="list-item__tags">
              <StatusTag v-if="contractState(item)" :dict="CONTRACT_TAG" :value="contractState(item)" />
              <StatusTag v-if="item.leaveDate" :dict="LEAVE_TAG" value="yes" />
            </div>
          </button>
        </van-list>
      </PageState>

      <p class="tip">点开员工可查看合同、定薪构成与调薪留痕；降薪调整会要求二次确认</p>
    </div>
  </div>
</template>

<style scoped>
.sk-row {
  height: 100px;
  margin-top: var(--sp-3);
}

.hr-row {
  display: block;
  width: 100%;
  margin-top: var(--sp-3);
  text-align: left;
  border: none;
}
</style>
