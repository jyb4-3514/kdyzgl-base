<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import { getMySchedules } from '../../api/attendance.js'
import { addDays, dayText, formatDate, mondayOf } from '../../utils/attendance.js'

/**
 * S12 我的排班（员工端）
 * 为什么按周而不是按月：排班的最小决策单位是「这周哪天上班」，一周一屏不用滚动即可看完，
 * 也避免月视图在 375px 宽下被压成看不清的格子。
 */
const loading = ref(true)
const error = ref('')
const weekStart = ref(formatDate(mondayOf(new Date())))
const week = ref(null)

const thisWeekStart = computed(() => formatDate(mondayOf(new Date())))
const today = formatDate(new Date())
const days = computed(() => (week.value ? week.value.list : []))
const workDays = computed(() => days.value.filter((day) => day.shiftId).length)
const rangeText = computed(() => (week.value ? `${week.value.weekStart} ~ ${week.value.weekEnd}` : ''))

async function load() {
  loading.value = true
  error.value = ''
  try {
    week.value = await getMySchedules({ weekStart: weekStart.value })
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 整周平移：周首固定为周一（与 Mock 的 mondayOf 同口径），避免用「今天 ±7 天」把周首算歪 */
function shiftWeek(offset) {
  weekStart.value = formatDate(addDays(new Date(`${weekStart.value}T00:00:00`), offset * 7))
  load()
}

function backToThisWeek() {
  weekStart.value = thisWeekStart.value
  load()
}

onMounted(load)
</script>

<template>
  <div class="schedule-page">
    <PageNav title="我的排班" />
    <div class="page page--loose">
      <div class="card week-nav">
        <button type="button" class="week-nav__btn" aria-label="上一周" @click="shiftWeek(-1)">
          <van-icon name="arrow-left" aria-hidden="true" />
        </button>
        <div class="week-nav__center">
          <p class="week-nav__range tabular-nums">{{ rangeText }}</p>
          <p class="week-nav__sub">本周排班 {{ workDays }} 天 · 休息 {{ 7 - workDays }} 天</p>
        </div>
        <button type="button" class="week-nav__btn" aria-label="下一周" @click="shiftWeek(1)">
          <van-icon name="arrow" aria-hidden="true" />
        </button>
      </div>

      <button v-if="weekStart !== thisWeekStart" type="button" class="back-today" @click="backToThisWeek">
        回到本周
      </button>

      <PageState
        :loading="loading"
        :error="error"
        :rows="6"
        :empty="!days.length"
        empty-text="本周暂无排班数据"
        @retry="load"
      >
        <div
          v-for="day in days"
          :key="day.workDate"
          class="list-item shift-item"
          :class="{ 'shift-item--today': day.workDate === today }"
        >
          <span
            class="shift-item__bar"
            :style="{ background: day.color || 'var(--border-line)' }"
            aria-hidden="true"
          ></span>
          <div class="shift-item__body">
            <div class="list-item__title">
              <span>{{ dayText(day.workDate) }}</span>
              <span v-if="day.workDate === today" class="shift-item__today">今天</span>
            </div>
            <div v-if="day.shiftName" class="list-item__meta">
              {{ day.shiftName }} · {{ day.startTime }} - {{ day.endTime }}
              <template v-if="day.restMinutes"> · 休息 {{ day.restMinutes }} 分钟</template>
            </div>
            <div v-else class="list-item__meta">休息（未排班）</div>
          </div>
        </div>
        <p class="tip">排班由管理员统一维护，本页只读；未排班当天按打卡规则的标准工时判定</p>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.week-nav {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  margin-top: var(--sp-3);
}

.week-nav__btn {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: var(--fs-h2);
  color: var(--text-1);
  background: var(--surface-subtle);
  border: 1px solid var(--border-line);
  border-radius: var(--r-sm);
}

.week-nav__center {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.week-nav__range {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.week-nav__sub {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.back-today {
  display: block;
  width: 100%;
  min-height: 44px;
  margin-top: var(--sp-3);
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--r-sm);
}

.shift-item {
  display: flex;
  gap: var(--sp-3);
  align-items: stretch;
  margin-top: var(--sp-3);
}

/* 今天用「主色描边 + 角标」双通道，不只靠颜色 */
.shift-item--today {
  border: 1px solid var(--color-primary-icon);
}

.shift-item__bar {
  flex: none;
  width: 4px;
  border-radius: var(--r-xs);
}

.shift-item__body {
  flex: 1;
  min-width: 0;
}

.shift-item__today {
  flex: none;
  font-size: var(--fs-caption);
  color: var(--color-primary);
}
</style>
