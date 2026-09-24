<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@/mobile/components/PageNav.vue'
import PageState from '@/mobile/components/PageState.vue'
import StatCard from '@/mobile/components/StatCard.vue'
import BossScopeNote from '../components/BossScopeNote.vue'
import BossShareBar from '../components/BossShareBar.vue'
import { getAttendanceSummary } from '@/mobile/api/attendance.js'
import { clockText, numberText, shortDateText } from '@/mobile/utils/format.js'

/**
 * B7 考勤概览（ADMIN · 管理端）
 * 口径：一屏看清「今天该来多少、来了多少、多少不正常」，异常明细给一键下钻。
 * 为什么不用日期切换：summary 支持 date 参数，但移动端首屏只服务「今天」这一个高频问题，
 * 历史数据统一走「打卡记录」页的日期筛选。
 * TODO(扩展): 需要「昨日/自定义日期」时，在 Hero 右侧加日期选择并把 date 传给 getAttendanceSummary。
 */
const router = useRouter()

const loading = ref(true)
const error = ref('')
const summary = ref(null)

const dateText = computed(() => shortDateText())
const updatedText = computed(() => `数据截止 ${clockText()}`)

/** 出勤率：无排班时不给 0%（会被误读成「全员未到」），返回 undefined 让 StatCard 走空值展示 */
const attendanceRate = computed(() => {
  const data = summary.value
  if (!data || !data.shouldCount) return null
  return `${Math.round((data.actualCount / data.shouldCount) * 1000) / 10}%`
})

/** 异常 = 迟到 + 早退 + 缺卡（校验未通过的异常卡不计入出勤，另有入口单独看） */
const abnormalTotal = computed(() => {
  const data = summary.value
  return data ? data.lateCount + data.earlyLeaveCount + data.absentCount : 0
})

const abnormalText = computed(() => {
  const data = summary.value
  if (!data) return ''
  return `今日 ${abnormalTotal.value} 项考勤异常：迟到 ${data.lateCount} · 早退 ${data.earlyLeaveCount} · 缺卡 ${data.absentCount}`
})

/** 可点卡的读屏补充语义（§14.9）：明示点开能看名单，避免只播报一个数字 */
const DETAIL_HINT = '可查看名单'

/** 六卡统一下钻到明细页（§14.8）：维度码与 summary 的六个计数字段一一对应 */
function openDetail(dim) {
  router.push({ path: '/boss/attendance/detail', query: { dim } })
}

/**
 * 出勤构成占比摘要：正常 + 迟到 + 缺卡 恰为应到（异常卡不计入实到与迟到/早退）。
 * 早退发生在到达之后，与到达状态重叠，并进来会重复计数，故不进构成 —— 它仍有独立指标卡与提示条。
 */
const attendanceSegments = computed(() => {
  const data = summary.value
  if (!data) return []
  return [
    { key: 'NORMAL', label: '正常', value: data.normalCount, tone: 'success' },
    { key: 'LATE', label: '迟到', value: data.lateCount, tone: 'warning' },
    { key: 'ABSENT', label: '缺卡', value: data.absentCount, tone: 'danger' }
  ]
})

/** 考勤管理入口：四宫格（与员工端宫格同规格，图标 24 / 文字 12 / 单元高 88） */
const entries = [
  { icon: 'setting-o', text: '打卡规则', to: '/boss/attendance/rule' },
  { icon: 'calendar-o', text: '排班管理', to: '/boss/schedule' },
  { icon: 'records', text: '打卡记录', to: '/boss/attendance/records' },
  { icon: 'warning-o', text: '异常明细', to: '/boss/attendance/records?status=ABNORMAL' }
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await getAttendanceSummary()
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page page--loose">
    <PageNav title="考勤概览" />
    <!-- 骨架按真实版面占位（Hero + 6 卡），数据到达时不跳 -->
    <template v-if="loading">
      <div class="skeleton-block sk-hero" />
      <div class="stat-grid stat-grid--roomy">
        <div v-for="i in 6" :key="i" class="skeleton-block sk-card" />
      </div>
    </template>

    <PageState v-else :error="error" @retry="load">
      <section class="hero hero--deep">
        <div class="flex-between">
          <span class="hero__title">今日考勤</span>
          <span class="hero__chip">口径：全域</span>
        </div>
        <p class="hero__sub">{{ dateText }} · {{ updatedText }}</p>
      </section>

      <div class="stat-grid stat-grid--roomy">
        <StatCard
          label="应到"
          :value="numberText(summary.shouldCount)"
          unit="人"
          tone="neutral"
          :hint="DETAIL_HINT"
          @click="openDetail('SHOULD')"
        />
        <StatCard
          label="实到"
          :value="numberText(summary.actualCount)"
          unit="人"
          tone="primary"
          :hint="DETAIL_HINT"
          @click="openDetail('ACTUAL')"
        />
        <StatCard
          label="正常"
          :value="numberText(summary.normalCount)"
          unit="人"
          tone="success"
          :hint="DETAIL_HINT"
          @click="openDetail('NORMAL')"
        />
        <StatCard
          label="迟到"
          :value="numberText(summary.lateCount)"
          unit="人"
          tone="warning"
          :hint="DETAIL_HINT"
          @click="openDetail('LATE')"
        />
        <StatCard
          label="早退"
          :value="numberText(summary.earlyLeaveCount)"
          unit="人"
          tone="warning"
          :hint="DETAIL_HINT"
          @click="openDetail('EARLY_LEAVE')"
        />
        <StatCard
          label="缺卡"
          :value="numberText(summary.absentCount)"
          unit="人"
          tone="danger"
          :hint="DETAIL_HINT"
          @click="openDetail('ABSENT')"
        />
      </div>

      <!-- 出勤构成占比（N-02）：与上方 6 张指标卡同一份 summary，不新增接口请求 -->
      <div class="section-title">
        考勤构成<span class="section-title__extra tabular-nums">应到 {{ numberText(summary.shouldCount) }} 人</span>
      </div>
      <BossShareBar
        :segments="attendanceSegments"
        :total="summary.shouldCount"
        :loading="loading"
        :error="error"
        empty-text="今日无排班，暂无出勤构成"
      />
      <BossScopeNote
        text="口径：构成以今日应到人数为分母，正常 / 迟到 / 缺卡 三段合计即应到；早退是到达后的签退行为、与到达状态重叠，不并入构成；占比按最大余数法取整，各段合计恒为 100%（单段与真实占比最多差 1 个百分点）"
      />

      <van-notice-bar
        v-if="abnormalTotal"
        class="notice"
        left-icon="warning-o"
        :text="abnormalText"
        wrapable
        color="var(--color-danger)"
        background="var(--color-danger-surface)"
        @click="router.push('/boss/attendance/records')"
      />
      <van-notice-bar
        v-else
        class="notice"
        left-icon="passed"
        text="今日无迟到、早退与缺卡"
        color="var(--color-success)"
        background="var(--color-success-surface)"
      />

      <div class="section-title">
        考勤管理<span class="section-title__extra tabular-nums">出勤率 {{ attendanceRate || '—' }}</span>
      </div>
      <van-grid :column-num="4" :border="false" clickable class="entry-grid">
        <van-grid-item
          v-for="item in entries"
          :key="item.to"
          :icon="item.icon"
          :text="item.text"
          :to="item.to"
          @keydown.enter="router.push(item.to)"
        />
      </van-grid>

      <p class="tip">
        缺卡 = 应到减实到；校验未通过的异常卡不计入实到与迟到/早退，需在「打卡记录」中按「异常」状态查看。
        演示数据仅城东驿站有排班与打卡记录，故全域口径与城东驿站一致。
      </p>
    </PageState>
  </div>
</template>

<style scoped>
.sk-hero {
  height: 88px;
  margin-top: var(--sp-4);
}

.sk-card {
  height: 88px;
}
</style>
