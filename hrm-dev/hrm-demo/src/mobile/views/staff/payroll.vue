<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MyPayrollCard from '../../components/MyPayrollCard.vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { getMyPayrolls } from '../../api/finance.js'

/**
 * B9 员工端 · 我的工资单
 *
 * 只列本人已发布 / 已确认的单据（未发布前不给本人看，口径在契约层强制）。
 * 列表只给摘要（showItems=false）：员工进列表是「哪个月发了多少钱」，逐项构成点进去看。
 * 待确认的单据在行内高亮，避免员工漏点确认导致管理员那边一直挂在「等待确认」。
 */
const PAGE_SIZE = 20

const router = useRouter()
const loading = ref(true)
const error = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)

async function loadFirst() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getMyPayrolls({ pageNum: 1, pageSize: PAGE_SIZE })
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
    finished.value = list.value.length >= page.total
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
    const page = await getMyPayrolls({ pageNum: next, pageSize: PAGE_SIZE })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

onMounted(loadFirst)
</script>

<template>
  <div class="staff-payroll">
    <PageNav title="我的工资单" />
    <div class="page page--loose">
      <p class="tool-row tabular-nums">共 {{ total }} 张已发布工资单</p>

      <template v-if="loading">
        <div v-for="i in 3" :key="i" class="skeleton-block sk-row" />
      </template>

      <PageState v-else :error="error" :empty="!list.length" empty-text="本月工资单尚未发布" @retry="loadFirst">
        <template #empty-action>
          <p class="tip">工资单由管理员审核并发布后可见，如已过期未收到请联系人事</p>
        </template>

        <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
          <div
            v-for="item in list"
            :key="item.id"
            class="pay-wrap"
            role="button"
            tabindex="0"
            @click="router.push(`/staff/payroll/${item.id}`)"
            @keydown.enter="router.push(`/staff/payroll/${item.id}`)"
          >
            <!-- 待确认提示放在卡片上方：卡片自身有圆角与投影，外部加竖条会被卡片底遮住 -->
            <p v-if="item.status === 'PUBLISHED'" class="pay-wrap__todo">待确认 · 点开核对明细后确认</p>
            <MyPayrollCard :payroll="item" :show-items="false" />
          </div>
        </van-list>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.sk-row {
  height: 120px;
  margin-top: var(--sp-3);
}

.pay-wrap {
  margin-top: var(--sp-3);
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

/* 待确认提示：文字 + 位置双通道标记待办，不只靠颜色（C6 SC 1.4.1） */
.pay-wrap__todo {
  margin: 0 0 var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-primary);
}
</style>
