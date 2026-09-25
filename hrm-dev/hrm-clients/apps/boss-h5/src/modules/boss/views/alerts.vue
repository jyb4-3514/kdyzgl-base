<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageNav from '@kdyzgl/shared/ui/PageNav.vue'
import PageState from '@kdyzgl/shared/ui/PageState.vue'
import SlaTag from '@/components/SlaTag.vue'
import StatusTag from '@kdyzgl/shared/ui/StatusTag.vue'
import BossInlineEmpty from '../components/BossInlineEmpty.vue'
import BossScopeNote from '../components/BossScopeNote.vue'
import BossShareBar from '../components/BossShareBar.vue'
import { getParcels } from '@/api/parcel.js'
import { getSyncOverview, getSyncTasks } from '@/api/syncTask.js'
import { getWorkOrders } from '@/api/workOrder.js'
import { COLLECT_STATE, COLLECT_STATE_ORDER, WORK_ORDER_PRIORITY, WORK_ORDER_TYPE } from '@kdyzgl/shared/constants/dict.js'
import { hoursAgoParam, numberText, relativeTime } from '@/utils/format.js'

/**
 * B5 异常预警（ADMIN · 四组）
 * 1) 超 48h 未取件 TOP10 —— 走包裹列表接口（status=1 + 入库时间上限），把最久的 10 件排在最前
 * 2) 同步失败驿站 —— 按驿站聚合失败批次，点击展开该驿站失败明细
 * 3) 超时未处理工单 —— 走工单接口的 overdueUnhandled 筛选（后端已排除已解决/已关闭）
 * 4) 采集状态（需求1，只读）—— 四态计数 + 驿站明细，配置能力只留 PC
 * 四组都能下钻：包裹 → 包裹详情、工单 → 工单详情（这两个详情页允许 ADMIN 进入）。
 */
const router = useRouter()
const OVERDUE_HOURS = 48

const loading = ref(true)
const error = ref('')
const overdueTotal = ref(0)
const groups = reactive({
  overdue: { open: true, list: [] },
  sync: { open: false, list: [] },
  sla: { open: false, list: [] }
})
const syncDetail = ref(null)
const showSyncDetail = ref(false)

/** 采集状态（需求1）：独立取数，接口挂了不该把另外三组一起判成错误页 */
const collectLoading = ref(true)
const collectError = ref('')
const collect = reactive({ open: true, total: 0, counts: {}, stations: [], filter: '' })

/** 服务端 counts 的键名 → 字典键，避免两处各写一套映射 */
const COUNT_KEY = { NORMAL: 'normal', ABNORMAL: 'abnormal', UNCONFIGURED: 'unconfigured', DISABLED: 'disabled' }

/** 四态 → 语义色族：与四态计数按钮的数字配色同口径（异常红 / 未配置橙 / 正常绿 / 停用中性） */
const COLLECT_TONE = { ABNORMAL: 'danger', UNCONFIGURED: 'warning', NORMAL: 'success', DISABLED: 'neutral' }

const counts = computed(() => ({
  overdue: overdueTotal.value,
  sync: groups.sync.list.reduce((sum, item) => sum + item.count, 0),
  sla: groups.sla.list.length
}))

/** 四态计数卡：某态为 0 也照常显示 0（0 是结论，不是缺数据） */
const collectCounts = computed(() =>
  COLLECT_STATE_ORDER.map((state) => ({
    state,
    label: COLLECT_STATE[state].label,
    count: collect.counts[COUNT_KEY[state]] || 0
  }))
)
const collectTodo = computed(() => (collect.counts.abnormal || 0) + (collect.counts.unconfigured || 0))

/** 四态占比摘要：与四态计数按钮共用同一份 counts，不新增接口请求 */
const collectSegments = computed(() =>
  collectCounts.value.map((item) => ({
    key: item.state,
    label: item.label,
    value: item.count,
    tone: COLLECT_TONE[item.state]
  }))
)
const collectRows = computed(() => {
  const rows = collect.filter
    ? collect.stations.filter((item) => item.collectState === collect.filter)
    : collect.stations
  return rows
    .slice()
    .sort((a, b) => COLLECT_STATE_ORDER.indexOf(a.collectState) - COLLECT_STATE_ORDER.indexOf(b.collectState))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [parcelPage, failedSyncs, overdueOrders] = await Promise.all([
      // 接口按入库时间倒序返回，取回一页后在本地按时间正序重排，得到「最久未取」的 TOP10
      getParcels({ status: 1, endTime: hoursAgoParam(OVERDUE_HOURS), pageNum: 1, pageSize: 100 }),
      getSyncTasks({ status: 3, pageNum: 1, pageSize: 100 }),
      getWorkOrders({ overdueUnhandled: '1', pageNum: 1, pageSize: 50 })
    ])

    overdueTotal.value = parcelPage.total
    groups.overdue.list = parcelPage.list
      .slice()
      .sort((a, b) => (a.inboundTime < b.inboundTime ? -1 : 1))
      .slice(0, 10)

    const grouped = new Map()
    failedSyncs.list.forEach((task) => {
      if (!grouped.has(task.stationId)) {
        grouped.set(task.stationId, {
          stationId: task.stationId,
          stationName: task.stationName,
          count: 0,
          latest: '',
          tasks: []
        })
      }
      const item = grouped.get(task.stationId)
      item.count += 1
      item.tasks.push(task)
      if (!item.latest || task.createTime > item.latest) item.latest = task.createTime
    })
    groups.sync.list = [...grouped.values()].sort((a, b) => b.count - a.count)

    groups.sla.list = overdueOrders.list
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadCollect() {
  collectLoading.value = true
  collectError.value = ''
  try {
    const data = await getSyncOverview()
    collect.total = data.total
    collect.counts = data.counts
    collect.stations = data.stations
    // 默认把视角落在要处理的那一档：有异常看异常，否则看未配置，都没有则看全部
    collect.filter = data.counts.abnormal ? 'ABNORMAL' : data.counts.unconfigured ? 'UNCONFIGURED' : ''
  } catch (e) {
    collectError.value = e.message || '采集状态加载失败'
  } finally {
    collectLoading.value = false
  }
}

function openStationSync(item) {
  syncDetail.value = item
  showSyncDetail.value = true
}

/** 下钻跳转抽成方法：点击与键盘（Enter/Space）三个触发点共用，避免模板里重复三份路由字符串 */
function openParcel(id) {
  router.push(`/boss/parcel/${id}`)
}

function openWorkOrder(id) {
  router.push(`/boss/workorder/${id}`)
}

onMounted(() => {
  load()
  loadCollect()
})
</script>

<template>
  <div class="page page--loose">
    <PageNav title="异常预警" />
    <PageState :loading="loading" :error="error" :rows="8" @retry="load">
      <!-- 分组一：超 48h 未取件 TOP10 -->
      <section class="group">
        <button type="button" class="group__head" @click="groups.overdue.open = !groups.overdue.open">
          <span><b>超 48h 未取件</b><span class="group__count">TOP10</span></span>
          <span class="group__right">
            <van-tag type="danger" plain>共 {{ numberText(counts.overdue) }} 件</van-tag>
            <van-icon :name="groups.overdue.open ? 'arrow-up' : 'arrow-down'" />
          </span>
        </button>
        <div v-show="groups.overdue.open" class="group__body">
          <BossInlineEmpty v-if="!groups.overdue.list.length" text="暂无超 48 小时未取件包裹" />
          <div
            v-for="item in groups.overdue.list"
            :key="item.id"
            class="list-item"
            role="button"
            tabindex="0"
            @click="openParcel(item.id)"
            @keydown.enter="openParcel(item.id)"
            @keydown.space.prevent="openParcel(item.id)"
          >
            <div class="list-item__title">
              <span>{{ item.waybillNo }}</span>
              <span class="danger-text">{{ relativeTime(item.inboundTime) }}入库</span>
            </div>
            <div class="list-item__meta">
              {{ item.stationName }} · 货架 {{ item.shelfCode }} · 收件人 {{ item.receiverName }}
              {{ item.receiverPhone }}
            </div>
          </div>
        </div>
      </section>

      <!-- 分组二：同步失败驿站 -->
      <section class="group">
        <button type="button" class="group__head" @click="groups.sync.open = !groups.sync.open">
          <span><b>同步失败驿站</b></span>
          <span class="group__right">
            <van-tag :type="counts.sync ? 'danger' : 'success'" plain>{{ counts.sync }} 个批次</van-tag>
            <van-icon :name="groups.sync.open ? 'arrow-up' : 'arrow-down'" />
          </span>
        </button>
        <div v-show="groups.sync.open" class="group__body">
          <BossInlineEmpty v-if="!groups.sync.list.length" text="近 100 个批次全部同步成功" />
          <div
            v-for="item in groups.sync.list"
            :key="item.stationId"
            class="list-item"
            role="button"
            tabindex="0"
            @click="openStationSync(item)"
            @keydown.enter="openStationSync(item)"
            @keydown.space.prevent="openStationSync(item)"
          >
            <div class="list-item__title">
              <span>{{ item.stationName }}</span>
              <span class="danger-text">{{ item.count }} 个批次失败</span>
            </div>
            <div class="list-item__meta">最近失败批次：{{ relativeTime(item.latest) }}</div>
          </div>
        </div>
      </section>

      <!-- 分组三：超时未处理工单 -->
      <section class="group">
        <button type="button" class="group__head" @click="groups.sla.open = !groups.sla.open">
          <span><b>超时未处理工单</b></span>
          <span class="group__right">
            <van-tag :type="counts.sla ? 'danger' : 'success'" plain>{{ counts.sla }} 条</van-tag>
            <van-icon :name="groups.sla.open ? 'arrow-up' : 'arrow-down'" />
          </span>
        </button>
        <div v-show="groups.sla.open" class="group__body">
          <BossInlineEmpty v-if="!groups.sla.list.length" text="暂无超时未处理工单" />
          <div
            v-for="item in groups.sla.list"
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

      <!-- 分组四：采集状态（需求1，只读）——管理员只关心「哪站没在采、哪站采挂了」，
           开关与频次的配置场景在 PC，移动端不提供编辑入口 -->
      <section class="group">
        <button type="button" class="group__head" @click="collect.open = !collect.open">
          <span
            ><b>采集状态</b><span class="group__count">共 {{ collect.total }} 站</span></span
          >
          <span class="group__right">
            <van-tag :type="collectTodo ? 'danger' : 'success'" plain>{{ collectTodo }} 站待处理</van-tag>
            <van-icon :name="collect.open ? 'arrow-up' : 'arrow-down'" />
          </span>
        </button>
        <div v-show="collect.open" class="group__body">
          <!-- 占比摘要（N-02）：与下方四态计数同一份 counts；不绑 select，下钻仍归四态按钮 -->
          <BossShareBar
            class="collect-share"
            :segments="collectSegments"
            :total="collect.total"
            :loading="collectLoading"
            :error="collectError"
            empty-text="暂无采集状态占比"
            @retry="loadCollect"
          />

          <template v-if="!collectLoading && !collectError">
            <BossScopeNote
              class="collect-scope"
              text="口径：采集四态以「共 N 站」为分母按站点计数，与上方四态计数按钮同源；占比按最大余数法取整，各段合计恒为 100%（单段与真实占比最多差 1 个百分点）"
            />

            <!-- 四态计数：点某一档即筛明细，再点一次取消筛选 -->
            <div class="collect-counts" role="group" aria-label="按采集状态筛选驿站">
              <button
                v-for="item in collectCounts"
                :key="item.state"
                type="button"
                class="collect-count"
                :class="`collect-count--${item.state.toLowerCase()}`"
                :aria-pressed="collect.filter === item.state"
                :title="`筛选「${item.label}」的驿站`"
                @click="collect.filter = collect.filter === item.state ? '' : item.state"
              >
                <span class="collect-count__num tabular-nums">{{ item.count }}</span>
                <span class="collect-count__label">{{ item.label }}</span>
              </button>
            </div>

            <BossInlineEmpty v-if="!collectRows.length" text="该状态暂无驿站" />
            <div v-for="item in collectRows" :key="item.stationId" class="list-item">
              <div class="list-item__title">
                <span>{{ item.stationName }}</span>
                <StatusTag :dict="COLLECT_STATE" :value="item.collectState" />
              </div>
              <div class="list-item__meta">
                数据源 {{ item.dataSource || '未配置' }} · 最后采集
                {{ item.lastCollectTime ? relativeTime(item.lastCollectTime) : '从未采集' }}
              </div>
              <div v-if="item.lastBatch" class="list-item__meta">
                最近批次 {{ item.lastBatch.batchNo }} · 失败 {{ item.lastBatch.failCount }} 条
              </div>
            </div>
          </template>
        </div>
      </section>

      <BossScopeNote
        :text="`口径：超 48h 判定基于包裹入库时间（当前时间往前推 ${OVERDUE_HOURS} 小时）；超时未处理判定：已过 SLA 且仍为待处理/处理中`"
      />
    </PageState>

    <van-popup v-model:show="showSyncDetail" round position="bottom" safe-area-inset-bottom>
      <div v-if="syncDetail" class="sync-pop">
        <div class="sync-pop__title">{{ syncDetail.stationName }} · 失败批次</div>
        <div class="sync-pop__list">
          <div v-for="task in syncDetail.tasks" :key="task.id" class="list-item">
            <div class="list-item__title">
              <span>{{ task.batchNo }}</span>
              <span class="muted">{{ relativeTime(task.createTime) }}</span>
            </div>
            <div class="list-item__meta">
              包裹 {{ task.parcelTotal }} · 失败 {{ task.failCount }} · 重试 {{ task.retryCount }} 次
            </div>
            <div class="list-item__meta danger-text">{{ task.errorMsg || '未记录失败原因' }}</div>
          </div>
        </div>
        <div class="sync-pop__foot">
          <van-button block round plain type="primary" @click="showSyncDetail = false">关闭</van-button>
        </div>
      </div>
    </van-popup>
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

.group__count {
  margin-left: var(--sp-1);
  font-size: var(--fs-micro);
  font-weight: var(--fw-regular);
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

.danger-text {
  font-size: var(--fs-caption);
  color: var(--color-danger);
}

.list-item__tags {
  display: flex;
  gap: var(--sp-1);
  margin-top: var(--sp-2);
}

/* 占比摘要与四态计数之间留一格间距（原区块三态骨架/错误条已由 BossShareBar 内置，故此处只补间距） */
.collect-share {
  margin-bottom: var(--sp-3);
}

/* 口径条自身只有上间距，后面紧跟四态计数会贴住，这里补下间距 */
.collect-scope {
  margin-bottom: var(--sp-3);
}

/* 采集状态四态计数（需求1）：2×2 排布，单格 ≥44 高满足触控；选中态用描边 + 主色底双通道表达 */
.collect-counts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-2);
}

.collect-count {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: var(--sp-1);
  min-height: 48px;
  font-size: var(--fs-caption);
  color: var(--text-2);
  background: var(--surface-card);
  border: 1px solid var(--border-line);
  border-radius: var(--r-sm);
}

.collect-count__num {
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.collect-count[aria-pressed='true'] {
  background: var(--color-primary-surface);
  border-color: var(--color-primary-icon);
}

.collect-count[aria-pressed='true'] .collect-count__num,
.collect-count[aria-pressed='true'] .collect-count__label {
  color: var(--color-primary);
}

/* 异常档常态即用危险色数字，一眼看到「有几站采挂了」 */
.collect-count--abnormal .collect-count__num {
  color: var(--color-danger);
}

.collect-count--unconfigured .collect-count__num {
  color: var(--color-warning);
}

.sync-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.sync-pop__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.sync-pop__list {
  max-height: 56vh;
  padding: 0 var(--sp-3);
  overflow-y: auto;
}

.sync-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
