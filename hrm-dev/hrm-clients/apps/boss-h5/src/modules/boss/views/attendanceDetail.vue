<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ListItemCard from '@/components/ListItemCard.vue'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import BossScopeNote from '../components/BossScopeNote.vue'
import { getAttendanceDetail } from '@/api/attendance.js'
import { clockOf } from '@/utils/attendance.js'
import { numberText } from '@/utils/format.js'
import { DAY_ATTENDANCE_STATE } from '@kdyzgl/shared/constants/dict.js'

/**
 * B11 考勤明细（ADMIN · 出勤维度 × 人员名单）
 *
 * 与「打卡记录」页的语义分工：本页只回答「这个维度上有哪些人」，日期/驿站/状态三维筛选留给打卡记录页，
 * 两者不重叠（§14.1）。名单与计数由 Mock 侧同一个 attendanceScope 产出，故明细人数必然与概览卡一致。
 */
const DIMS = [
  { value: 'SHOULD', label: '应到', tone: 'neutral', hit: '', caliber: '当日有排班的人', empty: '今日无排班，应到 0 人' },
  {
    value: 'ACTUAL',
    label: '实到',
    tone: 'primary',
    hit: '',
    caliber: '当日有有效上班卡的人',
    empty: '今日暂无有效打卡，实到 0 人'
  },
  {
    value: 'NORMAL',
    label: '正常',
    tone: 'success',
    hit: '',
    caliber: '有效上班卡且状态为正常',
    empty: '今日暂无正常打卡记录'
  },
  { value: 'LATE', label: '迟到', tone: 'warning', hit: 'ON', caliber: '有效上班卡且状态为迟到', empty: '今日无迟到' },
  {
    value: 'EARLY_LEAVE',
    label: '早退',
    tone: 'warning',
    hit: 'OFF',
    caliber: '有效下班卡且状态为早退',
    empty: '今日无早退'
  },
  {
    value: 'ABSENT',
    label: '缺卡',
    tone: 'danger',
    hit: '',
    // 缺卡是差集而非打卡事实，页顶必须先说清，否则用户会以为系统漏数据（§14.5-A）
    caliber: '缺卡 = 应到 − 实到，名单中的人当天没有有效打卡记录',
    empty: '今日无缺卡，全员出勤正常'
  }
]
const DIM_MAP = Object.fromEntries(DIMS.map((item) => [item.value, item]))
const DEFAULT_DIM = 'SHOULD'

/** 六维度共同边界（§14.3 四条），写在页顶而不是逐行重复 */
const SCOPE_BOUNDARY =
  '校验未通过的异常卡不计入实到/正常/迟到/早退，六个维度都不承载它，请到「打卡记录」按「异常」查看；' +
  '早退发生在到达之后、与到达状态重叠，不并入出勤构成；缺卡 = 应到 − 实到，是差集而非异常卡；' +
  '演示数据仅城东驿站有排班与打卡，故全域口径与城东驿站一致'

/** 维度级固定说明：无 remark 时补一句，正常/实到不硬凑说明避免噪音（§14.5-C） */
const DIM_REMARK = {
  LATE: '晚于班次上班时间打卡',
  EARLY_LEAVE: '早于班次下班时间打卡',
  ABSENT: '当日无有效打卡记录（应到未到）'
}

const route = useRoute()
const router = useRouter()

/** 非法/缺省 dim 回落 SHOULD：与打卡记录页对 status 的回落同口径，服务端仍严校验 */
const dim = computed(() => (DIM_MAP[route.query.dim] ? String(route.query.dim) : DEFAULT_DIM))
const meta = computed(() => DIM_MAP[dim.value])

const loading = ref(true)
const error = ref('')
const total = ref(0)
const list = ref([])
const date = ref('')

const pageTitle = computed(() => `${meta.value.label}明细`)
const scopeText = computed(() => `口径：${date.value || '今日'} ${meta.value.caliber}。${SCOPE_BOUNDARY}`)
/** 维度切换后的新人数由这里播报，避免读屏只听到卡片重排（§14.9） */
const liveText = computed(() => `${meta.value.label}人数 ${numberText(total.value)} 人`)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getAttendanceDetail({ dim: dim.value })
    total.value = data.total
    list.value = data.list
    date.value = data.date || ''
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 二次切换维度走 replace：不新增历史条目，返回仍直接回概览页（§14.6.1） */
function selectDim(value) {
  if (value === dim.value) return
  router.replace({ query: { dim: value } })
}

/** 身份行：驿站 · 班次（无班次退时段名）；两者都空则不渲染整行 */
const identityText = (row) => [row.stationName, row.shiftName || row.periodName].filter(Boolean).join(' · ')

/** 'YYYY-MM-DD HH:mm:ss' → 合法 datetime（HTML 规范要求日期与时间用 T 分隔） */
const datetimeOf = (time) => (time ? String(time).replace(' ', 'T') : '')

const remarkText = (row) => row.remark || DIM_REMARK[dim.value] || ''

/**
 * 「早退」胶囊只在到达状态未表达早退时追加（§14.5-B 最多两枚）：
 * 迟到维度里「又迟到又早退」的人需要两枚并存；早退维度本身标题胶囊已是早退，再挂一枚就是重复。
 */
const needEarlyLeaveTag = (row) =>
  !!row.offCheck && row.offCheck.status === 'EARLY_LEAVE' && row.dayState !== 'EARLY_LEAVE'

watch(dim, load, { immediate: true })
</script>

<template>
  <div class="page page--loose">
    <PageNav :title="pageTitle" />
    <!-- 加载只走骨架：mock 秒回时不闪（PageState 内置 200ms 延迟），维度与摘要卡随数据一起出现 -->
    <PageState :loading="loading" :error="error" :rows="4" @retry="load">
      <p class="visually-hidden" aria-live="polite">{{ liveText }}</p>

      <div class="stat-grid stat-grid--roomy grid-single">
        <StatCard :label="`${meta.label}人数`" :value="numberText(total)" unit="人" :tone="meta.tone" />
      </div>

      <BossScopeNote :text="scopeText" />

      <div class="chips" role="group" aria-label="考勤维度切换">
        <button
          v-for="item in DIMS"
          :key="item.value"
          type="button"
          class="fchip"
          :class="{ 'fchip--active': item.value === dim }"
          :aria-pressed="item.value === dim"
          @click="selectDim(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <!-- 空态只替换列表区：维度切换仍可用，用户不会卡在「没数据也没入口」的死页 -->
      <PageState v-if="!list.length" :empty="true" :empty-text="meta.empty" />

      <div v-else class="detail-list">
        <ListItemCard v-for="row in list" :key="row.employeeId" :density="2">
          <template #title>
            <span class="detail-row__name">{{ row.employeeName }}</span>
            <StatusTag :dict="DAY_ATTENDANCE_STATE" :value="row.dayState" />
          </template>

          <div v-if="identityText(row)" class="detail-row__meta">{{ identityText(row) }}</div>

          <!-- 双时间槽常驻：跨维度版式不变；无卡槽给业务文字「未打卡」，与取数失败的「—」严格区分（§14.5-A） -->
          <div class="detail-row__slots tabular-nums">
            <span class="slot" :class="{ 'slot--hit': meta.hit === 'ON' }">
              <template v-if="row.onCheck">
                <span class="slot__label">上班</span>
                <time :datetime="datetimeOf(row.onCheck.time)">{{ clockOf(row.onCheck.time) }}</time>
                <span v-if="meta.hit === 'ON'" class="slot__flag">迟到</span>
              </template>
              <span v-else class="slot__none">未打上班卡</span>
            </span>
            <span class="slot" :class="{ 'slot--hit': meta.hit === 'OFF' }">
              <template v-if="row.offCheck">
                <span class="slot__label">下班</span>
                <time :datetime="datetimeOf(row.offCheck.time)">{{ clockOf(row.offCheck.time) }}</time>
                <span v-if="meta.hit === 'OFF'" class="slot__flag">早退</span>
              </template>
              <span v-else class="slot__none">未打下班卡</span>
            </span>
          </div>

          <p v-if="remarkText(row)" class="list-item__meta list-item__meta--danger">{{ remarkText(row) }}</p>

          <template v-if="needEarlyLeaveTag(row)" #tags>
            <StatusTag :dict="DAY_ATTENDANCE_STATE" value="EARLY_LEAVE" />
          </template>
        </ListItemCard>
      </div>

      <p class="tip">
        缺卡 = 应到减实到；校验未通过的异常卡不计入实到与迟到/早退，需在「打卡记录」中按「异常」状态查看。
        演示数据仅城东驿站有排班与打卡记录，故全域口径与城东驿站一致。
      </p>
    </PageState>
  </div>
</template>

<style scoped>
/* 摘要卡单列铺满（§14.6-2）：全局 .stat-grid 是两列，这里只改列数不改间距 */
.grid-single {
  grid-template-columns: 1fr;
}

/* 维度 chip：与打卡记录页筛选 chip 同口径，页内自绘不新造组件（§14.6.1）；flex-wrap 兜住 375px 换行 */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.fchip {
  display: inline-flex;
  gap: var(--sp-1);
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

.detail-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

/* 长姓名不撑破标题行（§14.10 风险位②），与 .list-item__title > span:first-child 同口径 */
.detail-row__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 身份行单行省略（§14.10 风险位③）：驿站名 + 班次名可能同时很长 */
.detail-row__meta {
  margin-top: var(--sp-1);
  overflow: hidden;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-row__slots {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.slot {
  display: inline-flex;
  gap: var(--sp-1);
  align-items: baseline;
}

.slot__label {
  color: var(--text-3);
}

/* 命中槽：色 + 文字双通道，色弱用户靠「迟到/早退」文字同样可辨（§14.9） */
.slot--hit {
  font-weight: var(--fw-medium);
  color: var(--color-warning);
}

.slot--hit .slot__label {
  color: var(--color-warning);
}

.slot__none {
  color: var(--text-3);
}
</style>