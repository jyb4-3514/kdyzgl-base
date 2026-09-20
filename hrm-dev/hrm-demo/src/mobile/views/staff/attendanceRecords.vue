<script setup>
import { computed, onMounted, ref } from 'vue'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { getMyAttendance, getMySchedules } from '../../api/index.js'
import { ATTENDANCE_STATUS, CHECK_MODE, dictLabel } from '@/shared/constants/dict.js'
import { clockOf, dayStatusOf, dayText, formatDate, monthShiftMap } from '../../utils/attendance.js'

/**
 * S13 我的打卡记录（员工端 · 按月查询）
 * 后端记录按「时段 + 卡片类型」逐条返回，而员工看的是「这一天哪几段、每段几点上下班」，
 * 故页面层按 workDate → periodIndex 两级归组（时段归属由记录自带的 periodName 标识）。
 */
const pad = (n) => String(n).padStart(2, '0')

/** 一天的状态：多时段下按「异常 > 迟到 > 早退 > 正常」取最严重的一项，与单时段口径一致 */
const STATUS_PRIORITY = ['ABNORMAL', 'LATE', 'EARLY_LEAVE', 'NORMAL']

const loading = ref(true)
const error = ref('')
const month = ref(formatDate(new Date()).slice(0, 7))
const records = ref([])
const shiftMap = ref({})

const monthText = computed(() => `${month.value.slice(0, 4)} 年 ${month.value.slice(5, 7)} 月`)
const isThisMonth = computed(() => month.value === formatDate(new Date()).slice(0, 7))

const days = computed(() => {
  const grouped = new Map()
  records.value.forEach((row) => {
    if (!grouped.has(row.workDate)) grouped.set(row.workDate, { workDate: row.workDate, items: [], periods: new Map() })
    const day = grouped.get(row.workDate)
    day.items.push(row)
    // 无 periodIndex 的历史数据并入第 0 段，保证旧记录不丢展示
    const key = row.periodIndex == null ? 0 : row.periodIndex
    if (!day.periods.has(key)) day.periods.set(key, { key, name: row.periodName || '', on: null, off: null })
    day.periods.get(key)[row.checkType === 'ON' ? 'on' : 'off'] = row
  })
  return [...grouped.values()]
    .map((day) => {
      const periods = [...day.periods.values()].sort((a, b) => a.key - b.key)
      const states = periods.map(dayStatusOf).filter(Boolean)
      return {
        workDate: day.workDate,
        periods,
        state: STATUS_PRIORITY.find((key) => states.includes(key)) || '',
        // 打卡方式与围栏距离取当天第一条有效上报，避免每段重复展示同一份环境信息
        modeRow: day.items.find((row) => row.checkMode) || null
      }
    })
    .sort((a, b) => (a.workDate < b.workDate ? 1 : -1))
})

const stat = computed(() => {
  let late = 0
  let early = 0
  let abnormal = 0
  days.value.forEach((day) => {
    if (day.state === 'LATE') late += 1
    if (day.state === 'EARLY_LEAVE') early += 1
    if (day.state === 'ABNORMAL') abnormal += 1
  })
  return { attendance: days.value.length, late, early, abnormal }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getMyAttendance({ month: month.value })
    records.value = data.list
    // 记录不含班次字段（数据层已冻结），按周补一次「日期 → 班次」映射，页面层适配
    shiftMap.value = await monthShiftMap(month.value, getMySchedules)
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function shiftMonth(offset) {
  const [year, mon] = month.value.split('-').map(Number)
  const target = new Date(year, mon - 1 + offset, 1)
  month.value = `${target.getFullYear()}-${pad(target.getMonth() + 1)}`
  load()
}

function backToThisMonth() {
  month.value = formatDate(new Date()).slice(0, 7)
  load()
}

onMounted(load)
</script>

<template>
  <div class="records-page">
    <PageNav title="我的打卡记录" />
    <div class="page page--loose">
      <div class="card month-nav">
        <button type="button" class="month-nav__btn" aria-label="上一月" @click="shiftMonth(-1)">
          <van-icon name="arrow-left" aria-hidden="true" />
        </button>
        <div class="month-nav__center">
          <p class="month-nav__text tabular-nums">{{ monthText }}</p>
          <p class="month-nav__sub tabular-nums">
            出勤 {{ stat.attendance }} 天 · 迟到 {{ stat.late }} · 早退 {{ stat.early }} · 异常 {{ stat.abnormal }}
          </p>
        </div>
        <button type="button" class="month-nav__btn" aria-label="下一月" @click="shiftMonth(1)">
          <van-icon name="arrow" aria-hidden="true" />
        </button>
      </div>
      <button v-if="!isThisMonth" type="button" class="back-month" @click="backToThisMonth">回到本月</button>

      <PageState
        :loading="loading"
        :error="error"
        :rows="6"
        :empty="!days.length"
        empty-text="该月暂无打卡记录"
        @retry="load"
      >
        <div v-for="day in days" :key="day.workDate" class="list-item list-item--rich record">
          <div class="list-item__title">
            <span>{{ dayText(day.workDate) }}</span>
            <StatusTag v-if="day.state" :dict="ATTENDANCE_STATUS" :value="day.state" />
          </div>
          <div class="list-item__meta">
            <template v-if="shiftMap[day.workDate] && shiftMap[day.workDate].shiftName">
              {{ shiftMap[day.workDate].shiftName }} · {{ shiftMap[day.workDate].startTime }} -
              {{ shiftMap[day.workDate].endTime }}
            </template>
            <template v-else>当日无排班记录</template>
          </div>
          <!-- 按时段分段展示：双频次（4 次）时一天有两段，各自一对上下班卡 -->
          <div v-for="period in day.periods" :key="period.key" class="period">
            <div class="period__head">
              <span class="period__name">{{ period.name || `第 ${period.key + 1} 段` }}</span>
              <span class="period__times tabular-nums">
                上班 {{ period.on ? clockOf(period.on.checkTime) : '缺卡' }} · 下班
                {{ period.off ? clockOf(period.off.checkTime) : '缺卡' }}
              </span>
            </div>
            <p v-if="period.on && period.on.remark" class="list-item__meta list-item__meta--danger">
              上班卡：{{ period.on.remark }}
            </p>
            <p v-if="period.off && period.off.remark" class="list-item__meta list-item__meta--danger">
              下班卡：{{ period.off.remark }}
            </p>
          </div>
          <p v-if="day.modeRow" class="list-item__meta">
            打卡方式 {{ dictLabel(CHECK_MODE, day.modeRow.checkMode) }} · 距围栏
            {{ day.modeRow.distance == null ? '未知' : `${day.modeRow.distance} 米` }}
          </p>
        </div>
        <p class="tip">记录为演示数据，按「今天」为锚点生成；异常卡（校验未通过）不计入出勤</p>
      </PageState>
    </div>
  </div>
</template>

<style scoped>
.month-nav {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  margin-top: var(--sp-3);
}

.month-nav__btn {
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

.month-nav__center {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.month-nav__text {
  margin: 0;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
}

.month-nav__sub {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.back-month {
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

.record {
  margin-top: var(--sp-3);
}

/* 时段分组：段间一条分隔线，段内标签在左、时刻在右（窄屏可换行，不横向溢出） */
.period {
  padding-top: var(--sp-2);
  margin-top: var(--sp-2);
  border-top: 1px solid var(--border-line);
}

.period__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1) var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.period__name {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
  word-break: break-all;
}

.period__times {
  font-size: var(--fs-caption);
  color: var(--text-2);
}
</style>
