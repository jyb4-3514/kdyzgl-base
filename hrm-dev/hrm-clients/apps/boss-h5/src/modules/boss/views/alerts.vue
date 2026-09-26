<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import SlaTag from '@/components/SlaTag.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import BossInlineEmpty from '../components/BossInlineEmpty.vue'
import BossScopeNote from '../components/BossScopeNote.vue'
import { getWorkOrders } from '@/api/workOrder.js'
import { WORK_ORDER_PRIORITY, WORK_ORDER_TYPE } from '@kdyzgl/shared/constants/dict.js'

/**
 * B5 异常预警（ADMIN）
 *
 * MVP 裁剪：原四组里的「超 48h 未取件包裹」「同步失败驿站」「采集状态」三组随包裹族整体下架，
 * 本页只保留「超时未处理工单」一组（唯一不依赖包裹/同步数据的异常口径）。
 * 可下钻：工单 → 工单详情（ADMIN 允许进入）。
 */
const router = useRouter()

const loading = ref(true)
const error = ref('')
const orders = ref([])

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 超过 SLA 且仍为待处理/处理中的工单（后端已排除已解决/已关闭）
    const page = await getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 50 })
    orders.value = page.list
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 下钻跳转抽成方法：点击与键盘（Enter/Space）三个触发点共用，避免模板里重复三份路由字符串 */
function openWorkOrder(id) {
  router.push(`/boss/workorder/${id}`)
}

onMounted(load)
</script>

<template>
  <div class="page page--loose">
    <PageNav title="异常预警" />
    <PageState :loading="loading" :error="error" :rows="8" @retry="load">
      <section class="group">
        <button type="button" class="group__head">
          <span><b>超时未处理工单</b></span>
          <span class="group__right">
            <van-tag :type="orders.length ? 'danger' : 'success'" plain>{{ orders.length }} 条</van-tag>
          </span>
        </button>
        <div class="group__body">
          <BossInlineEmpty v-if="!orders.length" text="暂无超时未处理工单" />
          <div
            v-for="item in orders"
            :key="item.id"
            class="list-item"
            role="button"
            tabindex="0"
            @click="openWorkOrder(item.id)"
            @keydown.enter="openWorkOrder(item.id)"
            @keydown.space.prevent="openWorkOrder(item.id)"
          >
            <div class="list-item__title">
              <span>{{ item.orderNo }}</span>
              <SlaTag :deadline="item.slaDeadline" :active="item.status === 0 || item.status === 1" />
            </div>
            <div class="list-item__meta">{{ item.stationName }} · {{ item.title }}</div>
            <div class="list-item__tags">
              <StatusTag :dict="WORK_ORDER_TYPE" :value="item.type" />
              <StatusTag :dict="WORK_ORDER_PRIORITY" :value="item.priority" />
            </div>
          </div>
        </div>
      </section>

      <BossScopeNote text="口径：超时未处理 = 已过 SLA 且仍为待处理 / 处理中的工单（后端已排除已解决与已关闭）" />
    </PageState>
  </div>
</template>

<style scoped>
/* 本页原先引用了一组从未定义的 --hrm-* 变量与 #fff 硬编码，声明全部被浏览器丢弃；
   这里统一收敛到 tokens.scss，色值/间距/字号不再出现裸值（9.2 验收项） */
.group {
  margin-top: var(--sp-3);
  overflow: hidden;
  background: var(--surface-card);
  border-radius: var(--r-lg);
}

.group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  padding: var(--sp-3);
  font-size: var(--fs-body);
  background: none;
  border: none;
}

/* <b> 默认 700，中文字重只允许 400 / 500 / 600 三档 */
.group__head b {
  font-weight: var(--fw-semibold);
}

.group__right {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: center;
  color: var(--text-3);
}

.group__body {
  padding: 0 var(--sp-3) var(--sp-3);
}

/* 分组内的明细卡用浅底与白底分组卡片区分层次 */
.group__body .list-item {
  background: var(--surface-subtle);
  box-shadow: none;
}

.group__body .list-item + .list-item {
  margin-top: var(--sp-2);
}

.list-item__tags {
  display: flex;
  gap: var(--sp-1);
  margin-top: var(--sp-2);
}
</style>
