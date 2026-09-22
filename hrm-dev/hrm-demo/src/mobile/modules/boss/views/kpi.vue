<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import MonthPicker from '@/mobile/components/MonthPicker.vue'
import PageNav from '@/mobile/components/PageNav.vue'
import PageState from '@/mobile/components/PageState.vue'
import StationPicker from '@/mobile/components/StationPicker.vue'
import StatCard from '@/mobile/components/StatCard.vue'
import StatusTag from '@/mobile/components/StatusTag.vue'
import { KPI_LEVEL } from '@/shared/constants/dict.js'
import {
  calculateKpiScores,
  getKpiMetrics,
  getKpiRanking,
  saveKpiMetricBatch,
  updateKpiMetric
} from '@/mobile/api/kpi.js'
import { getStationList } from '@/mobile/api/org.js'
import { recentMonths } from '@/mobile/utils/format.js'
import { KPI_CODE } from '@/shared/constants/errorCode.js'

/**
 * B7 老板端 · KPI 考核（ADMIN）
 *
 * 两个 Tab 对应两种动作：**看结果**（谁该谈绩效）与**配指标**（考核口径）。
 * 契约口径：/kpi/metrics 与 /kpi/scores/calculate 都只对 ADMIN 开放，故本页即「老板端可写」的唯一入口。
 *
 * 权重校验是本页最不能做砸的地方，规则见 B7.4：合计 ≠ 100% 时**保存按钮禁用并给出差额**，
 * 而不是等提交后拿 9202 报错 —— 权重错了会导致一整月工资算错，必须在改的时候就看得见。
 * 保存按整组权重 / 启用态走 PUT /kpi/metrics/batch 一次原子提交（逐条提交会被中间态合计卡死），
 * 目标值等不参与合计的字段再由单指标接口补提交。
 */
const router = useRouter()

const tab = ref('result')
const month = ref(recentMonths()[0])

/** 指标启用态：局部小字典，只有本页消费，不进 shared/constants/dict.js 避免污染通用字典 */
const ENABLE_TAG = {
  1: { label: '启用中', type: 'success' },
  0: { label: '已停用', type: 'info' }
}

/* ---------- Tab1 考核结果 ---------- */
const PAGE_SIZE = 20
const loading = ref(true)
const error = ref('')
const list = ref([])
const summary = ref({ count: 0, avgScore: 0, topScore: 0 })
const total = ref(0)
const pageNum = ref(0)
const finished = ref(false)
const loadingMore = ref(false)
const generating = ref(false)

const stationId = ref(null)
const stations = ref([])
const stationsLoading = ref(true)
const stationsError = ref('')
const showStation = ref(false)
const stationText = computed(() => {
  const hit = stations.value.find((item) => item.id === stationId.value)
  return hit ? hit.stationName : '全部驿站'
})

async function loadStations() {
  stationsLoading.value = true
  stationsError.value = ''
  try {
    stations.value = await getStationList()
  } catch (e) {
    // 筛选取数失败不阻断结果列表（默认就是全部驿站），但弹层里必须能看出是「取不到」
    stations.value = []
    stationsError.value = e.message || '驿站列表加载失败'
  } finally {
    stationsLoading.value = false
  }
}

async function loadResult() {
  loading.value = true
  error.value = ''
  list.value = []
  pageNum.value = 0
  finished.value = false
  try {
    const page = await getKpiRanking({
      month: month.value,
      stationId: stationId.value,
      pageNum: 1,
      pageSize: PAGE_SIZE
    })
    pageNum.value = 1
    list.value = page.list
    total.value = page.total
    summary.value = { count: page.count, avgScore: page.avgScore, topScore: page.topScore }
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
    const page = await getKpiRanking({
      month: month.value,
      stationId: stationId.value,
      pageNum: next,
      pageSize: PAGE_SIZE
    })
    pageNum.value = next
    list.value = list.value.concat(page.list)
    finished.value = list.value.length >= page.total
  } catch (e) {
    finished.value = true
  } finally {
    loadingMore.value = false
  }
}

function onPickStation(id) {
  stationId.value = id
  loadResult()
}

/** 生成/重算本期考核：二次确认（B7.7），9205 已生成不可重复的兜底由服务端给 */
async function onGenerate() {
  try {
    await showConfirmDialog({
      title: '生成本期考核',
      message: `将按当前启用的指标重算 ${month.value} 全体在职员工的考核结果，已有结果会被覆盖。`,
      confirmButtonText: '确认生成',
      cancelButtonText: '再想想'
    })
  } catch (e) {
    return
  }
  generating.value = true
  try {
    const result = await calculateKpiScores({ month: month.value, stationId: stationId.value })
    showSuccessToast(`已生成 ${result.employeeCount} 人 / ${result.scoreCount} 条评分`)
    await loadResult()
  } catch (e) {
    error.value = e.message || '生成失败'
  } finally {
    generating.value = false
  }
}

/* ---------- Tab2 指标与权重配置 ---------- */
const metricsLoading = ref(true)
const metricsError = ref('')
const metrics = ref([])
const showEdit = ref(false)
const saving = ref(false)
const editError = ref('')
const draft = ref(null)

/** 合计按「启用指标」口径计算，与 kpiStore.weightError 的判断范围一致 */
const weightSum = computed(() =>
  metrics.value.reduce((sum, item) => {
    const enabled = draft.value && draft.value.id === item.id ? draft.value.enabled : item.enabled
    const weight = draft.value && draft.value.id === item.id ? Number(draft.value.weight) : Number(item.weight)
    return enabled === 1 ? sum + (Number.isFinite(weight) ? weight : 0) : sum
  }, 0)
)
const weightDiff = computed(() => weightSum.value - 100)
const weightTone = computed(() => (weightSum.value === 100 ? 'success' : weightSum.value < 100 ? 'warning' : 'danger'))
const weightText = computed(() => {
  if (weightSum.value === 100) return '✓ 已平衡'
  return weightDiff.value < 0 ? `还差 ${Math.abs(weightDiff.value)}%` : `超出 ${weightDiff.value}%`
})
/** 保存闸门：启用指标权重合计必须恰为 100%；服务端同样以 9202 兜底，前端先挡一层避免无谓请求 */
const weightValid = computed(() => weightSum.value === 100)
const saveHint = computed(() => (weightValid.value ? '' : `权重合计需为 100%（当前 ${weightSum.value}%）`))

async function loadMetrics() {
  metricsLoading.value = true
  metricsError.value = ''
  try {
    const data = await getKpiMetrics()
    metrics.value = data.list
  } catch (e) {
    metricsError.value = e.message || '加载失败'
  } finally {
    metricsLoading.value = false
  }
}

function openEdit(item) {
  draft.value = {
    id: item.id,
    metricName: item.metricName,
    weight: Number(item.weight),
    targetValue: Number(item.targetValue),
    enabled: Number(item.enabled)
  }
  editError.value = ''
  showEdit.value = true
}

async function saveMetric() {
  const current = draft.value
  if (!current || saving.value) return
  const weight = Number(current.weight)
  const target = Number(current.targetValue)
  if (!Number.isInteger(weight) || weight < 0 || weight > 100) {
    editError.value = '权重须为 0–100 的整数'
    return
  }
  if (!Number.isFinite(target) || target < 0) {
    editError.value = '目标值须为不小于 0 的数字'
    return
  }
  if (!weightValid.value) {
    editError.value = saveHint.value
    return
  }
  saving.value = true
  editError.value = ''
  try {
    // ① 整组权重与启用态一次原子提交：只改一项也会牵动合计，必须整组一起校验（否则中间态必被 9202 卡死）
    await saveKpiMetricBatch(
      metrics.value.map((item) => {
        const edited = item.id === current.id
        return {
          id: item.id,
          weight: edited ? weight : Number(item.weight),
          enabled: edited ? Number(current.enabled) : Number(item.enabled)
        }
      })
    )
    // ② 目标值不属于批量契约，待合计落定为 100% 后再单独提交，此时不会再触发权重校验
    const source = metrics.value.find((item) => item.id === current.id)
    if (source && Number(source.targetValue) !== target) {
      await updateKpiMetric(current.id, { targetValue: target })
    }
    showEdit.value = false
    showSuccessToast('指标已保存')
    await loadMetrics()
  } catch (e) {
    // 9202 是权重合计错，其余是字段级或状态问题；两类都在弹层内说清，便于就地改
    editError.value =
      e.code === KPI_CODE.WEIGHT_SUM_INVALID ? `${e.message}，请调整后再保存` : e.message || '保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

/** 指标配置按需加载：老板多数只看结果，避免每次进页多打一次配置请求 */
function onTabChange(name) {
  if (name === 'metric' && !metrics.value.length) loadMetrics()
}

function rankTone(index) {
  return index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : 'rest'
}

/** 账期切换即重查：先落值再发请求，避免两个 update 监听器的执行顺序影响取数月份 */
function onMonthChange(value) {
  month.value = value
  loadResult()
}

onMounted(() => {
  loadStations()
  loadResult()
})
</script>

<template>
  <div class="boss-kpi">
    <PageNav title="KPI 考核" />
    <div class="page page--loose">
      <van-tabs v-model:active="tab" class="kpi-tabs" @change="onTabChange">
        <van-tab title="考核结果" name="result" />
        <van-tab title="指标与权重" name="metric" />
      </van-tabs>

      <!-- ==================== Tab1 考核结果 ==================== -->
      <template v-if="tab === 'result'">
        <MonthPicker :model-value="month" label="考核周期" :disabled="loading" @update:model-value="onMonthChange" />

        <div class="tool-row">
          <button type="button" class="chip station-chip" @click="showStation = true">
            <van-icon name="location-o" aria-hidden="true" />
            {{ stationText }}
          </button>
          <span class="tabular-nums">参与 {{ summary.count }} 人</span>
        </div>

        <PageState
          :loading="loading"
          :error="error"
          :empty="!list.length"
          empty-text="该周期暂无考核结果"
          @retry="loadResult"
        >
          <template #empty-action>
            <!-- 空态给动作而不是干等：老板进这页就是想看结果，没有结果就该能生成 -->
            <van-button class="empty-btn" type="primary" :loading="generating" @click="onGenerate"
              >生成本期考核</van-button
            >
            <p class="tip">生成前请确认当月考勤已闭环，否则出勤类指标会按缺数据计算</p>
          </template>

          <div class="stat-grid stat-grid--roomy">
            <StatCard label="平均得分" :value="summary.avgScore" value-size="md" tone="primary" />
            <StatCard label="最高得分" :value="summary.topScore" value-size="md" tone="success" />
          </div>

          <div class="tool-row">
            <span>按综合得分降序</span>
            <span class="tabular-nums">共 {{ total }} 条</span>
          </div>

          <van-list v-model:loading="loadingMore" :finished="finished" finished-text="没有更多了" @load="onLoadMore">
            <button
              v-for="(item, index) in list"
              :key="item.employeeId"
              type="button"
              class="list-item rank-item"
              @click="router.push(`/boss/kpi/${item.employeeId}`)"
            >
              <span class="rank-item__no tabular-nums" :class="`rank-item__no--${rankTone(index)}`">{{
                item.rank
              }}</span>
              <span class="rank-item__body">
                <span class="list-item__title">
                  <span>{{ item.employeeName }}</span>
                  <span class="rank-item__score tabular-nums">{{ item.totalScore }}</span>
                </span>
                <span class="list-item__meta">
                  {{ item.stationName || '总部' }} · 指标 {{ item.metricCount }} 项 · 达标
                  {{ Math.round(item.achievementRate * 100) }}%
                </span>
                <span class="list-item__tags">
                  <StatusTag :dict="KPI_LEVEL" :value="item.level" />
                </span>
              </span>
              <van-icon name="arrow" aria-hidden="true" />
            </button>
          </van-list>
        </PageState>

        <van-button class="recalc" block plain :loading="generating" @click="onGenerate">重新生成本期考核</van-button>
      </template>

      <!-- ==================== Tab2 指标与权重 ==================== -->
      <template v-else>
        <!-- 合计条常驻不折叠：改权重时随时看得见是否平衡（B7.4） -->
        <div class="card weight-card" :class="`weight-card--${weightTone}`">
          <div class="weight-card__head">
            <span class="text-strong tabular-nums">权重合计 {{ weightSum }}%</span>
            <span class="weight-card__diff tabular-nums">{{ weightText }}</span>
          </div>
          <van-progress
            class="weight-card__bar"
            :percentage="Math.min(weightSum, 100)"
            :show-pivot="false"
            :color="
              weightTone === 'success'
                ? 'var(--color-success)'
                : weightTone === 'warning'
                  ? 'var(--color-warning)'
                  : 'var(--color-danger)'
            "
            stroke-width="8"
          />
          <p class="tip">仅统计启用中的指标；服务端算分按员工实际适用指标的权重归一</p>
        </div>

        <PageState
          :loading="metricsLoading"
          :error="metricsError"
          :empty="!metrics.length"
          empty-text="暂无指标配置"
          @retry="loadMetrics"
        >
          <div v-for="item in metrics" :key="item.id" class="list-item metric-item">
            <div class="list-item__title">
              <span>{{ item.metricName }}</span>
              <span class="tabular-nums">{{ item.weight }}%</span>
            </div>
            <div class="list-item__meta tabular-nums">
              {{ item.metricTypeLabel }} · 目标 {{ item.targetValue }}{{ item.unit }} · {{ item.scoreModeLabel }} ·
              {{ item.directionLabel }}
              <template v-if="item.roleScope"> · 仅考核 {{ item.roleScope.length }} 类角色</template>
            </div>
            <div class="list-item__tags">
              <StatusTag :dict="ENABLE_TAG" :value="item.enabled" />
            </div>
            <van-button class="metric-item__edit" size="small" plain type="primary" @click="openEdit(item)"
              >调整</van-button
            >
          </div>
        </PageState>

        <p class="tip">
          保存时整组权重与启用状态一次提交：启用指标合计须恰为 100%，否则保存按钮不可点。
          停用指标不参与合计，可自由调整；目标值不受权重约束。
        </p>
      </template>
    </div>

    <!-- 指标编辑弹层：字段少，弹层比整页更轻 -->
    <van-popup v-model:show="showEdit" round position="bottom" safe-area-inset-bottom>
      <div v-if="draft" class="edit-pop">
        <div class="edit-pop__title">{{ draft.metricName }}</div>
        <van-cell-group inset>
          <van-field label="权重(%)" input-align="right">
            <template #input>
              <van-stepper v-model="draft.weight" :min="0" :max="100" :step="5" input-width="64px" button-size="44px" />
            </template>
          </van-field>
          <van-field v-model="draft.targetValue" type="number" label="目标值" input-align="right" />
          <van-field label="启用考核" input-align="right">
            <template #input>
              <van-switch v-model="draft.enabled" :active-value="1" :inactive-value="0" size="20px" />
            </template>
          </van-field>
        </van-cell-group>

        <p class="edit-pop__diff" :class="`edit-pop__diff--${weightTone}`">
          保存后合计 {{ weightSum }}%（{{ weightText }}）
        </p>
        <p v-if="editError" class="edit-pop__error" role="alert">{{ editError }}</p>
        <p v-else-if="saveHint" class="edit-pop__error">{{ saveHint }}</p>

        <div class="edit-pop__foot">
          <van-button
            block
            type="primary"
            :disabled="!!saveHint"
            :loading="saving"
            :title="saveHint || '保存指标'"
            @click="saveMetric"
          >
            {{ saveHint ? '权重未平衡，暂不可保存' : '保存' }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <StationPicker
      v-model:show="showStation"
      :stations="stations"
      :model-value="stationId"
      :loading="stationsLoading"
      :error="stationsError"
      empty-text="暂无可选驿站，请先在 PC 端维护驿站"
      @retry="loadStations"
      @select="onPickStation"
    />
  </div>
</template>

<style scoped>
.kpi-tabs {
  margin-top: var(--sp-2);

  --van-tabs-line-height: 44px;
}

.station-chip {
  padding: 0 var(--sp-3);
  font-size: var(--fs-caption);
}

.empty-btn {
  min-height: 44px;
  margin-top: var(--sp-4);
}

.recalc {
  min-height: 44px;
  margin-top: var(--sp-5);
}

.rank-item {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  width: 100%;
  text-align: left;
  border: none;
}

.rank-item + .rank-item {
  margin-top: var(--sp-3);
}

.rank-item__no {
  flex: none;
  width: 28px;
  height: 28px;
  font-size: var(--fs-caption);
  font-weight: var(--fw-semibold);
  line-height: 28px;
  color: var(--text-on-dark);
  text-align: center;
  border-radius: var(--r-full);
}

.rank-item__no--gold {
  background: var(--rank-1-bg);
}

.rank-item__no--silver {
  background: var(--rank-2-bg);
}

.rank-item__no--bronze {
  background: var(--rank-3-bg);
}

.rank-item__no--rest {
  color: var(--text-2);

  /* 底色取 100 档，PC --rank-rest-bg 是 50 档；统一会改视觉，保留原值待决策（P2-4 已登记） */
  background: var(--c-neutral-100);
}

.rank-item__body {
  flex: 1;
  min-width: 0;
}

.rank-item__body .list-item__title,
.rank-item__body .list-item__meta,
.rank-item__body .list-item__tags {
  display: flex;
}

.rank-item__score {
  flex: none;
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.weight-card {
  margin-top: var(--sp-4);
  border-left: 3px solid var(--border-line);
}

.weight-card--success {
  border-left-color: var(--color-success);
}

.weight-card--warning {
  border-left-color: var(--color-warning);
}

.weight-card--danger {
  border-left-color: var(--color-danger);
}

.weight-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.weight-card__diff {
  font-size: var(--fs-caption);
}

.weight-card--success .weight-card__diff {
  color: var(--color-success);
}

.weight-card--warning .weight-card__diff {
  color: var(--color-warning);
}

.weight-card--danger .weight-card__diff {
  color: var(--color-danger);
}

.weight-card__bar {
  margin-top: var(--sp-2);
}

.metric-item {
  margin-top: var(--sp-3);
}

.metric-item__edit {
  min-height: 44px;
  margin-top: var(--sp-2);
}

.edit-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.edit-pop__title {
  padding: 0 var(--sp-4) var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.edit-pop__diff {
  margin: var(--sp-3) var(--sp-4) 0;
  font-size: var(--fs-caption);
  text-align: center;
}

.edit-pop__diff--success {
  color: var(--color-success);
}

.edit-pop__diff--warning {
  color: var(--color-warning);
}

.edit-pop__diff--danger {
  color: var(--color-danger);
}

.edit-pop__error {
  margin: var(--sp-2) var(--sp-4) 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.edit-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
