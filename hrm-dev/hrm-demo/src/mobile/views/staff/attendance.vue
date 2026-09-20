<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import PageNav from '../../components/PageNav.vue'
import PageState from '../../components/PageState.vue'
import StatusTag from '../../components/StatusTag.vue'
import { applyMakeup, checkIn, getAttendanceStatus, getMyMakeups } from '../../api/index.js'
import { ATTENDANCE_CODE } from '@/shared/constants/errorCode.js'
import { ATTENDANCE_STATUS, CHECK_MODE, CHECK_TYPE, MATCH_MODE, dictLabel } from '@/shared/constants/dict.js'
import { useAuthStore } from '../../stores/auth.js'
import { getWifiInfo } from '../../utils/bridge.js'
import {
  checkErrorHint,
  clockOf,
  coordText,
  distanceText,
  formatDate,
  haversine,
  makeupErrorHint,
  minutesOfDay,
  periodLabel,
  periodWindowText,
  WEEKDAYS
} from '../../utils/attendance.js'

/**
 * 员工端打卡页（核心）
 *
 * 为什么按「时段」而不是按「上班/下班」渲染：规则支持每日 2 次或 4 次打卡，
 * 4 次时上午班与下午班各有上下班卡，若只给两个按钮，员工根本分不清打的是哪一段。
 * 页面只认 /attendance/status 下发的 periods 数组，不自己按 checkFrequency 推演时段数量与时间窗。
 *
 * 打卡按钮为什么排在时段卡内而不是固定底栏：4 个打卡项是平级操作，固定底栏最多承载 2 个，
 * 收敛到「当前时段」又会在时间窗重叠时丢掉入口；按时段分组的 44px 内联按钮是唯一不丢操作入口的方案。
 *
 * 补卡入口为什么挂在时段项上：补卡的唯一性口径是「员工 + 日期 + 时段 + 卡类型」这一个槽位，
 * 挂在时段项上天然带齐四个定位信息，员工不必再在弹层里选日期和时段（少一次选错的机会）。
 * 今日已提交且待审批的槽位由「我的补卡申请」回读，刷新页面也不会退化回可重复提交状态。
 *
 * 诚实标注（硬约束，不许伪装）：
 * 1. WiFi —— 浏览器没有读取真实 SSID 的标准能力，非壳环境显示规则白名单里的模拟值并打「模拟」标记；
 * 2. 定位 —— Mock 围栏坐标是虚构值，手机真实定位必然在围栏外，故提供显式标注的「演示辅助」开关，
 *    开启后按围栏中心坐标提交，仅用于演示「打卡通过」这条路径。
 */
const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const status = ref(null)
const now = ref(new Date())
const wifi = ref({ ssid: '', mock: true })
const wifiListText = ref('')
const position = ref(null)
const locationError = ref('')
const locating = ref(false)
/** 演示辅助：开启后用围栏中心坐标提交（界面上显式标注用途，不做成隐形后门） */
const demoAssist = ref(false)
/** 提交中的打卡项 key（时段序号-卡片类型）：同一时刻只允许一个提交，防连点重复落卡 */
const submitting = ref('')
const result = ref(null)
/**
 * 今日已提交且待审批的槽位 key 集合
 * 为什么由「我的补卡申请」反推而不是本地记一个标记：补卡是异步审批，刷新/换设备后本地标记会丢，
 * 从服务端回读才能保证界面与「我的补卡申请」页永远一致。
 */
const pendingKeys = ref(new Set())
const showMakeup = ref(false)
const makeupReason = ref('')
const makeupError = ref('')
const makeupSubmitting = ref(false)
/** 补卡弹层的上下文：日期取接口下发的 workDate，避免用本地日期与服务端判定错位 */
const makeupTarget = ref({ workDate: '', periodIndex: 0, periodName: '', checkType: 'ON' })

let ticker = null

const shift = computed(() => (status.value ? status.value.shift : null))
const rule = computed(() => (status.value ? status.value.rule : null))
const periods = computed(() => (status.value && status.value.periods) || [])
const hasSchedule = computed(() => !!(status.value && status.value.hasSchedule))
/** 已完成卡数 / 应打总数：一眼看出今天还剩几次没打 */
const doneCount = computed(
  () => periods.value.filter((p) => p.onChecked).length + periods.value.filter((p) => p.offChecked).length
)
const requireTotal = computed(() => periods.value.length * 2)

const clockText = computed(() => {
  const d = now.value
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})
const dateText = computed(() => `${formatDate(now.value)} ${WEEKDAYS[now.value.getDay()]}`)
const matchModeLabel = computed(() => (rule.value ? dictLabel(MATCH_MODE, rule.value.matchMode) : '-'))
const nowMinutes = computed(() => now.value.getHours() * 60 + now.value.getMinutes())

/** 围栏距离：演示辅助开启时按围栏中心取 0，否则按真实定位算（服务端判定仍为唯一准绳） */
const fenceDistance = computed(() => {
  if (!rule.value) return null
  if (demoAssist.value) return 0
  if (!position.value) return null
  return haversine(rule.value.longitude, rule.value.latitude, position.value.longitude, position.value.latitude)
})
const inFence = computed(() => fenceDistance.value != null && rule.value && fenceDistance.value <= rule.value.radius)

/** 提交用的坐标：演示辅助 → 围栏中心；否则真实定位（取不到则提交 null，由服务端按未通过判定） */
const submitCoord = computed(() => {
  if (demoAssist.value && rule.value) return { longitude: rule.value.longitude, latitude: rule.value.latitude }
  if (position.value) return { longitude: position.value.longitude, latitude: position.value.latitude }
  return { longitude: null, latitude: null }
})

const checkKey = (periodIndex, checkType) => `${periodIndex}-${checkType}`

/**
 * 单个打卡项状态（6 态）：done 已打卡 / pending 补卡审批中 / wait 未到开放时间 / todo 可打 / overdue 过点未打 / missed 已过期待补卡
 * 判定用页面时钟（秒级刷新），只做界面提示，最终以服务端判定为准。
 *
 * 为什么要把 overdue 单列一态：全天班这类单时段规则，打卡窗口一直开到时段结束后（08:00-18:00 → 07:30-19:00），
 * 只看「窗口是否关闭」的话，整个白天都不会出现补卡入口；但 08:00 该打没打、人正在外面取件，本来就是补卡场景。
 * 故「已过规定打卡时刻且未打卡」也给出补卡入口，只是主操作仍是补打 —— 补打能落真实卡（迟到/早退如实记录），
 * 补卡要等审批，优先级天然更低。
 * 「已打卡」优先于「审批中」：审批通过后服务端会补录记录，两者同时命中时应显示确定态而非过程态。
 */
function itemState(period, checkType) {
  if (checkType === 'ON' ? period.onChecked : period.offChecked) {
    const time = checkType === 'ON' ? period.onTime : period.offTime
    return { key: 'done', text: `${clockOf(time)} 已打卡` }
  }
  if (pendingKeys.value.has(checkKey(period.periodIndex, checkType))) {
    return { key: 'pending', text: '已提交补卡申请，待老板审批' }
  }
  const from = minutesOfDay(period.windowStart)
  const to = minutesOfDay(period.windowEnd)
  if (Number.isFinite(from) && nowMinutes.value < from)
    return { key: 'wait', text: `未到打卡时间（${period.windowStart} 开放）` }
  if (Number.isFinite(to) && nowMinutes.value > to) return { key: 'missed', text: '已过期未打卡，待补卡' }
  // 规定时刻由服务端下发；取不到时 now >= NaN 为 false，自然落到「未打卡」，不会误报过点
  const due = checkType === 'ON' ? period.startTime : period.endTime
  if (nowMinutes.value >= minutesOfDay(due))
    return { key: 'overdue', text: `已过 ${due} 未打卡（${period.windowEnd} 前可补打）` }
  return { key: 'todo', text: '未打卡' }
}

/**
 * 时段 × 卡片类型的扁平结果：模板里每一项要读 3 次状态（文案 / 样式 / 按钮形态），
 * 在这里一次算完，避免同一函数在模板中反复求值（模板里重复调用是最大的可读性坑）。
 */
const periodsWithState = computed(() =>
  periods.value.map((period) => ({
    ...period,
    cells: ['ON', 'OFF'].map((checkType) => ({ checkType, state: itemState(period, checkType) }))
  }))
)

const periodDoneCount = (period) => (period.onChecked ? 1 : 0) + (period.offChecked ? 1 : 0)

/** 校验未通过的尝试会在服务端落一条异常卡留痕，页面要说清楚，避免「界面说没打、记录里有卡」的困惑 */
const abnormalLogged = computed(
  () =>
    result.value &&
    !result.value.ok &&
    (result.value.code === ATTENDANCE_CODE.WIFI_MISMATCH || result.value.code === ATTENDANCE_CODE.LOCATION_MISMATCH)
)

/**
 * 回读今日待审批的补卡申请
 * 失败不打断打卡主流程：这个集合只决定「已过期项」显示成申请入口还是审批中，
 * 打卡本身不受影响，为此把整页判成错误态反而误导员工。
 */
async function loadPendingMakeups() {
  const workDate = status.value ? status.value.workDate : ''
  if (!workDate) return
  try {
    const page = await getMyMakeups({
      status: 'PENDING',
      startDate: workDate,
      endDate: workDate,
      pageNum: 1,
      pageSize: 20
    })
    pendingKeys.value = new Set(page.list.map((item) => checkKey(item.periodIndex, item.checkType)))
  } catch (e) {
    pendingKeys.value = new Set()
  }
}

/**
 * 拉取今日状态
 * @param {boolean} initial 首屏加载：走骨架屏与整页错误态；打卡后的刷新不切骨架，
 *        否则「判定结果卡」会在刷新瞬间被骨架顶掉，员工看不到自己刚打的卡
 */
async function load(initial = true) {
  if (initial) {
    loading.value = true
    error.value = ''
  }
  try {
    status.value = await getAttendanceStatus()
    const list = rule.value ? rule.value.wifiList : []
    wifiListText.value = list.map((item) => item.ssid).join('、')
    // WiFi 模拟值取自规则白名单首位：浏览器演示能走通 WiFi 校验分支，壳内则取真实 SSID
    wifi.value = getWifiInfo(list.length ? list[0].ssid : '')
    await loadPendingMakeups()
  } catch (e) {
    if (initial) error.value = e.message || '加载失败'
    else showFailToast('打卡状态刷新失败，请下拉重试')
  } finally {
    if (initial) loading.value = false
  }
}

/** 真实定位：localhost 属安全上下文，浏览器可直接取；壳内权限由原生声明 */
function locate() {
  if (!navigator.geolocation) {
    locationError.value = '当前环境不提供定位能力，可开启「演示辅助」开关跑通演示'
    return
  }
  locating.value = true
  locationError.value = ''
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      position.value = {
        longitude: Number(pos.coords.longitude.toFixed(6)),
        latitude: Number(pos.coords.latitude.toFixed(6)),
        accuracy: Math.round(pos.coords.accuracy)
      }
      locating.value = false
    },
    (err) => {
      locationError.value =
        err.code === 1
          ? '定位未授权：请在浏览器/系统设置中允许定位后重试'
          : '定位获取失败，可开启「演示辅助」开关跑通演示'
      locating.value = false
    },
    { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
  )
}

function toggleAssist() {
  demoAssist.value = !demoAssist.value
}

async function onCheck(period, checkType) {
  if (submitting.value) return
  submitting.value = checkKey(period.periodIndex, checkType)
  result.value = null
  try {
    const vo = await checkIn({
      checkType,
      periodIndex: period.periodIndex,
      wifiSsid: wifi.value.ssid || null,
      ...submitCoord.value
    })
    result.value = {
      ok: true,
      periodIndex: period.periodIndex,
      periodName: vo.periodName || period.name,
      checkType,
      ...vo
    }
    showSuccessToast(
      vo.status === 'NORMAL' ? `${period.name}打卡成功` : `打卡成功（${dictLabel(ATTENDANCE_STATUS, vo.status)}）`
    )
  } catch (e) {
    // 打卡失败文案由页面按错误码给出（checkIn 走 silent），比通用 Toast 更能指导下一步
    result.value = {
      ok: false,
      periodIndex: period.periodIndex,
      periodName: period.name,
      checkType,
      code: e.code,
      hint: checkErrorHint(e.code, {
        checkTypeLabel: dictLabel(CHECK_TYPE, checkType),
        periodName: period.name,
        window: periodWindowText(period, rule.value),
        ssid: wifi.value.ssid,
        distanceText: distanceText(fenceDistance.value),
        demoHint: !demoAssist.value,
        message: e.message
      })
    }
  } finally {
    submitting.value = ''
    // 成功与校验失败都会改变今日状态（失败还会落异常卡），必须重新拉取，避免界面与服务端不同步；
    // 走非 initial 分支不切骨架，页内不闪
    await load(false)
  }
}

/** 打开补卡弹层：日期 / 时段 / 卡类型全部取自接口数据，员工只填理由，避免手选错槽位 */
function openMakeup(period, checkType) {
  makeupTarget.value = {
    workDate: status.value.workDate,
    periodIndex: period.periodIndex,
    periodName: period.name,
    checkType
  }
  makeupReason.value = ''
  makeupError.value = ''
  showMakeup.value = true
}

async function submitMakeup() {
  const reason = makeupReason.value.trim()
  if (makeupSubmitting.value || reason.length < 2) return
  makeupSubmitting.value = true
  makeupError.value = ''
  try {
    await applyMakeup({
      workDate: makeupTarget.value.workDate,
      periodIndex: makeupTarget.value.periodIndex,
      checkType: makeupTarget.value.checkType,
      reason
    })
    showMakeup.value = false
    showSuccessToast('补卡申请已提交，等待老板审批')
  } catch (e) {
    // 9108 最常见（本人刚申请过，或期间又正常打了卡），提示要落到「去哪儿看进度 / 无需补卡」
    makeupError.value = makeupErrorHint(e.code, { message: e.message })
  } finally {
    // 成功与失败都回读一次：失败多因服务端已有申请或已有卡，刷新后界面与服务端对齐（弹层保留错误文案）
    await load(false)
    makeupSubmitting.value = false
  }
}

onMounted(() => {
  load()
  ticker = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onUnmounted(() => clearInterval(ticker))
</script>

<template>
  <div class="page page--loose">
    <PageNav title="打卡" />
    <PageState :loading="loading" :error="error" :rows="6" @retry="load">
      <!-- 未配规则属于「业务未就绪」而非无数据，单独给引导，不显示打卡按钮 -->
      <PageState v-if="!rule" :empty="true" empty-text="该驿站尚未配置打卡规则">
        <template #empty-action>
          <p class="tip">请联系站长或管理员在「打卡规则」中完成配置后再打卡</p>
        </template>
      </PageState>

      <template v-else>
        <section class="clock">
          <p class="clock__time tabular-nums" role="timer" :aria-label="`当前时间 ${clockText}`">{{ clockText }}</p>
          <p class="clock__date">{{ dateText }} · {{ auth.user.stationName || '未归属驿站' }}</p>
        </section>

        <div class="section-title">
          <span>今日班次</span>
          <span class="section-title__extra">{{ hasSchedule ? '来自排班表' : '未排班 · 按规则标准工时' }}</span>
        </div>
        <div class="card shift-card">
          <span class="shift-card__bar" :style="{ background: shift.color }" aria-hidden="true"></span>
          <div class="shift-card__body">
            <div class="shift-card__name">{{ shift.shiftName }}</div>
            <div class="list-item__meta">
              {{ shift.startTime }} - {{ shift.endTime }}
              <template v-if="shift.restMinutes"> · 休息 {{ shift.restMinutes }} 分钟</template>
            </div>
          </div>
        </div>

        <div class="section-title">
          <span>打卡时段</span>
          <span class="section-title__extra tabular-nums">已完成 {{ doneCount }}/{{ requireTotal }}</span>
        </div>

        <div v-if="!periods.length" class="card">
          <p class="verify__hint">规则未配置打卡时段，请联系站长在「打卡规则」中配置时段后再打卡。</p>
        </div>

        <div v-for="period in periodsWithState" :key="period.periodIndex" class="card period-card">
          <div class="period-card__head">
            <span class="period-card__name">{{ period.name }}</span>
            <span class="period-card__progress tabular-nums">{{ periodDoneCount(period) }}/2 已完成</span>
          </div>
          <p class="period-card__meta tabular-nums">
            {{ period.startTime }} - {{ period.endTime }} · 可打卡 {{ periodWindowText(period, rule) }}
          </p>

          <div v-for="cell in period.cells" :key="cell.checkType" class="check-item">
            <div class="check-item__text">
              <span class="check-item__label">{{ dictLabel(CHECK_TYPE, cell.checkType) }}</span>
              <span class="check-item__state" :class="`check-item__state--${cell.state.key}`">{{
                cell.state.text
              }}</span>
            </div>

            <!-- 已过期（窗口已关）：入口换成「申请补卡」——再点打卡必被 9102 打回 -->
            <van-button
              v-if="cell.state.key === 'missed'"
              class="check-item__btn"
              :aria-label="`${period.name}${dictLabel(CHECK_TYPE, cell.checkType)}申请补卡`"
              @click="openMakeup(period, cell.checkType)"
            >
              申请补卡
            </van-button>

            <!-- 审批中：按钮保留但禁用，员工一眼看出「这一格已提过单」，而不是入口凭空消失 -->
            <van-button v-else-if="cell.state.key === 'pending'" class="check-item__btn" disabled>审批中</van-button>

            <!-- 可打与过点未打：主操作都是补打，过点的额外给一个补卡次入口（补打优先，见 itemState 注释） -->
            <div v-else class="check-item__ops">
              <van-button
                class="check-item__btn"
                :plain="cell.checkType === 'OFF'"
                :loading="submitting === checkKey(period.periodIndex, cell.checkType)"
                :disabled="!!submitting"
                :aria-label="`${period.name}${dictLabel(CHECK_TYPE, cell.checkType)}`"
                @click="onCheck(period, cell.checkType)"
              >
                {{ cell.checkType === 'ON' ? '上班打卡' : '下班打卡' }}
              </van-button>
              <button
                v-if="cell.state.key === 'overdue'"
                type="button"
                class="check-item__link"
                :aria-label="`${period.name}${dictLabel(CHECK_TYPE, cell.checkType)}申请补卡`"
                @click="openMakeup(period, cell.checkType)"
              >
                申请补卡
              </button>
            </div>
          </div>

          <!-- 判定结果就近渲染在被操作的时段卡内，员工按完按钮不用滚动回顶部找结论 -->
          <div
            v-if="result && result.periodIndex === period.periodIndex"
            class="result"
            :class="result.ok ? 'result--ok' : 'result--fail'"
            :role="result.ok ? 'status' : 'alert'"
          >
            <div class="result__head">
              <van-icon :name="result.ok ? 'passed' : 'warning-o'" aria-hidden="true" />
              <span class="result__title"
                >{{ result.ok ? '打卡成功' : '打卡未通过' }} ·
                {{ periodLabel(result.periodName, result.checkType) }}</span
              >
              <StatusTag v-if="result.ok" :dict="ATTENDANCE_STATUS" :value="result.status" />
            </div>
            <p v-if="result.ok" class="result__meta tabular-nums">
              {{ result.checkTime }} · 命中 {{ dictLabel(CHECK_MODE, result.checkMode) }} · 距围栏
              {{ distanceText(result.distance) }}
              <template v-if="result.remark"> · {{ result.remark }}</template>
            </p>
            <p v-else class="result__meta">{{ result.hint }}</p>
            <p v-if="abnormalLogged" class="result__foot">
              本次尝试已在服务端记录为异常卡（不计入出勤），修正后可重新打卡
            </p>
          </div>
        </div>

        <div class="section-title">
          <span>校验状态</span>
          <span class="section-title__extra">打卡前自查</span>
        </div>

        <!-- WiFi 卡：壳内真实 SSID / 浏览器模拟值，模拟必须标注 -->
        <div class="card verify">
          <div class="verify__head">
            <span class="verify__label">当前 WiFi</span>
            <span v-if="wifi.mock" class="verify__badge" role="note">模拟</span>
          </div>
          <div class="verify__value">{{ wifi.ssid || '未获取到' }}</div>
          <p class="verify__hint">
            {{
              wifi.mock
                ? '浏览器没有读取真实 SSID 的标准能力，此处为按规则白名单填充的模拟值；安装安卓壳后由 HrmBridge.getWifiInfo() 读取真实 SSID'
                : '由安卓壳读取的真实 WiFi'
            }}
          </p>
          <p class="verify__hint">规则白名单：{{ wifiListText || '未配置' }}</p>
        </div>

        <!-- 定位卡 -->
        <div class="card verify">
          <div class="verify__head">
            <span class="verify__label">当前定位</span>
            <span class="verify__badge" :class="demoAssist || inFence ? 'verify__badge--ok' : 'verify__badge--warn'">
              {{ demoAssist ? '演示辅助 · 围栏中心' : inFence ? '在围栏内' : '超出围栏' }}
            </span>
          </div>
          <div class="verify__value tabular-nums">
            {{
              demoAssist
                ? `${coordText(rule.longitude)}, ${coordText(rule.latitude)}`
                : position
                  ? `${coordText(position.longitude)}, ${coordText(position.latitude)}`
                  : '尚未获取到定位'
            }}
          </div>
          <p class="verify__hint tabular-nums">
            围栏中心 {{ coordText(rule.longitude) }}, {{ coordText(rule.latitude) }} · 允许半径 {{ rule.radius }} 米
          </p>
          <p class="verify__hint">
            距围栏中心 {{ distanceText(fenceDistance) }}
            <template v-if="position && !demoAssist && position.accuracy">
              · 定位精度约 {{ position.accuracy }} 米</template
            >
          </p>
          <p v-if="locationError" class="verify__error" role="alert">{{ locationError }}</p>
          <van-button class="verify__btn" size="small" plain type="primary" :loading="locating" @click="locate"
            >重新定位</van-button
          >

          <!-- 演示辅助：显式标注为演示手段，不与真实能力混淆 -->
          <div class="assist" @click="toggleAssist">
            <div class="assist__text">
              <p class="assist__title">演示辅助（非真实能力）</p>
              <p class="assist__hint">
                Mock
                围栏坐标是虚构值，手机真实定位必然在围栏外。开启后按围栏中心坐标提交，仅用于演示打卡通过路径；关闭则提交真实定位。
              </p>
            </div>
            <van-switch
              :model-value="demoAssist"
              size="24px"
              aria-label="演示辅助开关：开启后按围栏中心坐标提交打卡"
              @keydown.enter.prevent="toggleAssist"
              @keydown.space.prevent="toggleAssist"
            />
          </div>
        </div>

        <!-- 规则要求摘要 -->
        <div class="card verify">
          <div class="verify__head">
            <span class="verify__label">规则要求</span>
            <span class="verify__hint">{{ rule.ruleName }}</span>
          </div>
          <p v-if="status.requireSummary" class="verify__hint">{{ status.requireSummary }}</p>
          <div class="verify__chips">
            <span class="mini-chip" :class="{ 'mini-chip--on': rule.enableWifi }">WiFi</span>
            <span class="mini-chip" :class="{ 'mini-chip--on': rule.enableLocation }">定位</span>
            <span class="mini-chip" :class="{ 'mini-chip--on': rule.enableTimeWindow }">时间窗</span>
            <span class="mini-chip mini-chip--on">组合：{{ matchModeLabel }}</span>
          </div>
          <p class="verify__hint">
            迟到阈值 {{ rule.lateThresholdMin }} 分钟 · 早退阈值 {{ rule.earlyLeaveThresholdMin }} 分钟
          </p>
          <p class="verify__hint">
            时间窗 = 时段开始提前 {{ rule.allowEarlyMin }} 分钟开放、时段结束延后 {{ rule.allowLateMin }} 分钟关闭
          </p>
          <p v-if="!rule.enableWifi || !rule.enableLocation || !rule.enableTimeWindow" class="verify__hint">
            未启用的校验项不参与判定；三项全关时为免校验打卡
          </p>
        </div>

        <!-- 排班与记录不是打卡页的职责，但员工到打卡页时最可能顺手要看，故就近给入口 -->
        <div class="more-links">
          <button type="button" class="more-links__btn" @click="router.push('/staff/schedule')">我的排班</button>
          <button type="button" class="more-links__btn" @click="router.push('/staff/attendance/records')">
            打卡记录
          </button>
          <button type="button" class="more-links__btn" @click="router.push('/staff/attendance/makeup')">
            补卡申请
          </button>
        </div>
      </template>
    </PageState>

    <van-popup v-model:show="showMakeup" round position="bottom" safe-area-inset-bottom>
      <div class="makeup-pop">
        <div class="makeup-pop__title">申请补卡</div>
        <van-cell-group :border="false">
          <van-cell title="补卡日期" :value="makeupTarget.workDate" />
          <van-cell title="打卡时段" :value="makeupTarget.periodName" />
          <van-cell title="卡类型" :value="dictLabel(CHECK_TYPE, makeupTarget.checkType)" />
        </van-cell-group>
        <div class="makeup-pop__form">
          <van-field
            v-model="makeupReason"
            type="textarea"
            rows="3"
            maxlength="200"
            show-word-limit
            label="补卡理由"
            placeholder="请说明漏卡原因（2-200 字），例如：外出取件错过下班打卡"
          />
          <!-- 提交失败就在弹层内说清原因，员工不必关掉弹层再去猜（9108 会带上查看进度的指引） -->
          <p v-if="makeupError" class="makeup-pop__error" role="alert">{{ makeupError }}</p>
          <p class="tip">提交后需老板审批，通过后系统自动补录该时段打卡记录</p>
        </div>
        <div class="makeup-pop__foot">
          <van-button
            block
            type="primary"
            :loading="makeupSubmitting"
            :disabled="makeupReason.trim().length < 2"
            @click="submitMakeup"
          >
            提交申请
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.clock {
  padding: var(--sp-6) 0 var(--sp-4);
  text-align: center;
}

.clock__time {
  margin: 0;
  font-size: var(--fs-clock);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-clock);
  color: var(--text-1);
}

.clock__date {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.shift-card {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
}

.shift-card__bar {
  flex: none;
  width: 4px;
  height: 32px;
  border-radius: var(--r-xs);
}

.shift-card__body {
  min-width: 0;
}

.shift-card__name {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  line-height: var(--lh-body);
}

.period-card {
  margin-top: var(--sp-3);
}

.period-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.period-card__name {
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-semibold);
  color: var(--text-1);
}

.period-card__progress {
  font-size: var(--fs-caption);
  color: var(--text-3);
}

.period-card__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
  word-break: break-all;
}

.check-item {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  min-height: 56px;
  border-top: 1px solid var(--border-line);
}

.check-item:first-of-type,
.period-card__meta + .check-item {
  margin-top: var(--sp-2);
}

.check-item__text {
  flex: 1;
  min-width: 0;
}

.check-item__label {
  display: block;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.check-item__state {
  display: block;
  margin-top: var(--sp-1);
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.check-item__state--done {
  color: var(--color-success);
}

.check-item__state--todo,
.check-item__state--overdue {
  color: var(--color-warning);
}

.check-item__state--missed {
  color: var(--color-danger);
}

/* 审批中是「进行中」而非问题态，用品牌主色与红色的待补卡区分开 */
.check-item__state--pending {
  color: var(--color-primary);
}

/* 打卡主操作 ≥44×44（7.4），min-height 压过 Vant 默认按钮高度，保证触控达标 */
.check-item__btn {
  flex: none;
  min-width: 104px;
  min-height: 44px;
}

/* 一个槽位两档操作时纵向排列：横向并排会把「上班卡 / 已过 08:00 未打卡（19:00 前可补打）」挤成三行 */
.check-item__ops {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--sp-1);
  align-items: flex-end;
}

/* 次入口也是独立焦点目标，高 44 满足触控（7.4），视觉上只是主按钮下的文字链接 */
.check-item__link {
  min-height: 44px;
  padding: 0 var(--sp-2);
  font-size: var(--fs-caption);
  color: var(--color-primary);
  background: none;
  border: none;
}

.result {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
  border-left: 3px solid var(--border-line);
}

.result--ok {
  border-left-color: var(--color-success);
}

.result--fail {
  border-left-color: var(--color-danger);
}

.result__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  font-size: var(--fs-body-strong);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.result--ok .result__head {
  color: var(--color-success);
}

.result--fail .result__head {
  color: var(--color-danger);
}

.result__title {
  flex: 1;
  min-width: 0;
}

.result__meta {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-2);
}

.result__foot {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.verify + .verify {
  margin-top: var(--sp-3);
}

.verify__head {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  justify-content: space-between;
}

.verify__label {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--text-1);
}

.verify__badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  font-size: var(--fs-micro);
  white-space: nowrap;
  color: var(--color-warning);
  background: var(--color-warning-surface);
  border-radius: var(--r-full);
}

.verify__badge--ok {
  color: var(--color-success);
  background: var(--color-success-surface);
}

.verify__badge--warn {
  color: var(--color-danger);
  background: var(--color-danger-surface);
}

.verify__value {
  margin-top: var(--sp-2);
  font-size: var(--fs-num-sm);
  font-weight: var(--fw-semibold);
  word-break: break-all;
}

.verify__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.verify__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.verify__btn {
  min-height: 44px;
  margin-top: var(--sp-3);
}

.verify__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  margin-top: var(--sp-2);
}

.mini-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  font-size: var(--fs-micro);
  color: var(--text-3);
  background: var(--surface-subtle);
  border: 1px solid var(--border-line);
  border-radius: var(--r-full);
}

.mini-chip--on {
  color: var(--color-primary);
  background: var(--color-primary-surface);
  border-color: var(--color-primary-border);
}

/* 演示辅助整行可点（≥44 高），开关自身仍是可聚焦的 role=switch，键盘可操作 */
.assist {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  min-height: 44px;
  padding-top: var(--sp-3);
  margin-top: var(--sp-3);
  border-top: 1px solid var(--border-line);
  cursor: pointer;
}

.assist__text {
  flex: 1;
  min-width: 0;
}

.assist__title {
  margin: 0;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-warning);
}

.assist__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--text-3);
}

.more-links {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-4);
}

.more-links__btn {
  flex: 1;
  min-height: 44px;
  font-size: var(--fs-body);
  color: var(--color-primary);
  background: var(--surface-card);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--r-sm);
}

/* 底部弹层：上下留白 20 / 24（2.5 弹层规范），底部安全区由 popup 的 safe-area-inset-bottom 处理 */
.makeup-pop {
  padding: var(--sp-5) 0 var(--sp-6);
}

.makeup-pop__title {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  text-align: center;
}

.makeup-pop__form {
  padding: var(--sp-3) var(--sp-4) 0;
}

.makeup-pop__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-caption);
  line-height: var(--lh-caption);
  color: var(--color-danger);
}

.makeup-pop__foot {
  padding: var(--sp-4) var(--sp-4) 0;
}
</style>
