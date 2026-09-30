import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { showFailToast } from 'vant'
import { getMyMakeups } from '@/api/attendance.js'
import { getWifiInfo } from '@/utils/bridge.js'
import { useAttendanceStore } from '@/stores/attendance.js'
import { WEEKDAYS, formatDate, haversine, minutesOfDay } from '@/utils/attendance.js'
import { MATCH_MODE, dictLabel } from '@kdyzgl/shared/constants/dict.js'
import {
  CHECK_TYPES,
  ITEM_STATE,
  LOCATE_DENIED,
  LOCATE_FAILED,
  LOCATE_UNSUPPORTED,
  fenceHintsOf,
  locateBadgeOf,
  positionTextOf,
  ruleChipsOf,
  ruleHintsOf,
  slotKey,
  slotText,
  wifiBadgeOf,
  wifiHintsOf,
  wifiTextOf
} from '../model/attendanceUi.js'

/** 演示构建才在 WiFi 自查卡补「提交按白名单模拟」说明；生产构建编译期常量为 false，整块剔除 */
const DEMO_ENABLED = import.meta.env.VITE_MOCK_ENABLED === 'true'

/**
 * 打卡页状态中枢（取数 + 四态 + 时段/规则派生）
 *
 * 为什么把 WiFi / 定位 / 演示辅助放在这里而不是提交 composable：它们是「提交前的自查」，
 * 与提交本身分离，才能让首页复用的 useCheckIn 保持不承载演示手段（首页不塞演示开关）。
 * 纯展示映射（标记 / 明细行 / 主值文案）已下沉到 model，本文件只保留与响应式状态相关的派生。
 *
 * 时钟由 startClock/stopClock 显式开关（不在这里挂 onMounted）：composable 保持无组件生命周期依赖，
 * 纯逻辑可直测，生命周期由页面壳决定。
 *
 * 取数唯一来源是 stores/attendance.js（快照与失败语义在 store 收口），本文件不再直连接口，只做页面态适配与派生。
 */
export function useAttendanceStatus() {
  const store = useAttendanceStore()
  const { status } = storeToRefs(store)
  // loading / error 留页内：它们表达「首屏骨架 + 整页错误态」，只有 initial 才置位；
  // store 的同名态每次 refresh（含打卡后回读）都变，直接复用会让判定结果卡被骨架顶掉。
  const loading = ref(true)
  const error = ref('')
  const now = ref(new Date())
  const wifi = ref({ ssid: '', mock: true })
  const wifiListText = ref('')
  const position = ref(null)
  const locationError = ref('')
  const locating = ref(false)
  /** 演示辅助：开启后用围栏中心坐标提交（界面上显式标注用途，不做成隐形后门） */
  const demoAssist = ref(false)
  /**
   * 今日已提交且待审批的槽位键集合
   * 为什么由「我的补卡申请」反推而不是本地记标记：补卡是异步审批，刷新/换设备后本地标记会丢，
   * 从服务端回读才能保证界面与「我的补卡申请」页永远一致。
   */
  const pendingKeys = ref(new Set())

  let ticker = null

  const shift = computed(() => (status.value ? status.value.shift : null))
  const rule = computed(() => (status.value ? status.value.rule : null))
  const periods = computed(() => (status.value && status.value.periods) || [])
  const hasSchedule = computed(() => !!(status.value && status.value.hasSchedule))
  /**
   * 该驿站是否配置了启用班次：false（后端 shiftConfigured=false / 9113）时无打卡时间基准，
   * 页面须禁用打卡并提示联系管理员，而不是渲染一堆打不了的按钮。
   * 兼容老接口：字段缺失时按「有时段即视为已配置」兜底，避免升级期整页误判为空态。
   */
  const shiftConfigured = computed(() => {
    if (!status.value) return true
    if (typeof status.value.shiftConfigured === 'boolean') return status.value.shiftConfigured
    return periods.value.length > 0
  })
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

  /** 围栏距离：演示辅助开启时按围栏中心取 0，否则按真实定位算（服务端判定仍是唯一准绳） */
  const fenceDistance = computed(() => {
    if (!rule.value) return null
    if (demoAssist.value) return 0
    if (!position.value) return null
    return haversine(rule.value.longitude, rule.value.latitude, position.value.longitude, position.value.latitude)
  })
  const inFence = computed(() => fenceDistance.value != null && rule.value && fenceDistance.value <= rule.value.radius)

  /** 提交坐标：演示辅助 → 围栏中心；否则真实定位（取不到给 null，由服务端按未通过判定） */
  const submitCoord = computed(() => {
    if (demoAssist.value && rule.value) return { longitude: rule.value.longitude, latitude: rule.value.latitude }
    if (position.value) return { longitude: position.value.longitude, latitude: position.value.latitude }
    return { longitude: null, latitude: null }
  })

  /**
   * 单个打卡项状态（6 态）：done 已打卡 / pending 补卡审批中 / wait 未到开放时间 /
   * todo 可打 / overdue 过点未打 / missed 已过期待补卡。判定用页面时钟，只做界面提示，最终以服务端判定为准。
   *
   * 为什么 overdue 单列一态：全天班这类单时段规则窗口一直开到时段结束后，只看「窗口是否关闭」的话
   * 整个白天都没有补卡入口；但 08:00 该打没打、人正在外面取件，本来就是补卡场景，故「已过规定时刻且未打卡」
   * 也给出补卡入口，只是主操作仍是补打（能落真实卡，优先级天然更高）。
   * 「已打卡」优先于「审批中」：审批通过后服务端会补录记录，两者同时命中时应显示确定态而非过程态。
   */
  function itemState(period, checkType) {
    if (checkType === 'ON' ? period.onChecked : period.offChecked) {
      return { key: ITEM_STATE.DONE, text: slotText.done(checkType === 'ON' ? period.onTime : period.offTime) }
    }
    if (pendingKeys.value.has(slotKey(period.periodIndex, checkType))) {
      return { key: ITEM_STATE.PENDING, text: slotText.pending() }
    }
    const from = minutesOfDay(period.windowStart)
    const to = minutesOfDay(period.windowEnd)
    if (Number.isFinite(from) && nowMinutes.value < from) {
      return { key: ITEM_STATE.WAIT, text: slotText.wait(period.windowStart) }
    }
    if (Number.isFinite(to) && nowMinutes.value > to) return { key: ITEM_STATE.MISSED, text: slotText.missed() }
    // 规定时刻由服务端下发；取不到时 now >= NaN 为 false，自然落到「未打卡」，不会误报过点
    const due = checkType === 'ON' ? period.startTime : period.endTime
    if (nowMinutes.value >= minutesOfDay(due)) {
      return { key: ITEM_STATE.OVERDUE, text: slotText.overdue(due, period.windowEnd) }
    }
    return { key: ITEM_STATE.TODO, text: slotText.todo() }
  }

  /** 时段 × 卡类型的扁平结果：模板每项要读 3 次状态（文案 / 样式 / 按钮形态），一次算完避免反复求值 */
  const periodsWithState = computed(() =>
    periods.value.map((period) => ({
      ...period,
      cells: CHECK_TYPES.map((checkType) => ({ checkType, state: itemState(period, checkType) }))
    }))
  )

  const wifiBadge = computed(() => wifiBadgeOf(wifi.value))
  const wifiText = computed(() => wifiTextOf(wifi.value))
  const wifiHints = computed(() => wifiHintsOf(wifi.value, wifiListText.value, DEMO_ENABLED))
  const positionText = computed(() => positionTextOf(rule.value, position.value, demoAssist.value))
  const locateBadge = computed(() => locateBadgeOf(inFence.value, demoAssist.value))
  const fenceHints = computed(() => fenceHintsOf(rule.value, fenceDistance.value, position.value, demoAssist.value))
  const ruleChips = computed(() => ruleChipsOf(rule.value, matchModeLabel.value))
  const ruleHints = computed(() => ruleHintsOf(rule.value, status.value && status.value.requireSummary))

  /**
   * @param {boolean} initial 首屏加载：走骨架屏与整页错误态；打卡后的刷新不切骨架，
   *        否则「判定结果卡」会在刷新瞬间被骨架顶掉，员工看不到自己刚打的卡
   */
  async function load(initial = true) {
    if (initial) {
      loading.value = true
      error.value = ''
    }
    // 取数走 store.refresh（失败语义已收口），这里只读 store.error 判定成败，不再 try/catch 接口
    await store.refresh()
    if (store.error) {
      // 首屏失败 → 整页错误态 + 重试；非首屏失败 → 只提示，保留页面已有数据（store 已保住快照）
      if (initial) error.value = store.error
      else showFailToast('打卡状态刷新失败，请下拉重试')
    } else {
      const list = (rule.value && rule.value.wifiList) || []
      wifiListText.value = list.map((item) => item.ssid).join('、')
      // 只读壳侧真实 SSID：未取到时 getWifiInfo 返回空 ssid，卡片如实显示「未获取到」（不再回填白名单值）
      wifi.value = getWifiInfo()
      await loadPendingMakeups()
    }
    if (initial) loading.value = false
  }

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
      pendingKeys.value = new Set((page.list || []).map((item) => slotKey(item.periodIndex, item.checkType)))
    } catch (e) {
      pendingKeys.value = new Set()
    }
  }

  /** 真实定位：localhost 属安全上下文，浏览器可直接取；壳内权限由原生声明 */
  function locate() {
    if (!navigator.geolocation) {
      locationError.value = LOCATE_UNSUPPORTED
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
        locationError.value = err.code === 1 ? LOCATE_DENIED : LOCATE_FAILED
        locating.value = false
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 }
    )
  }

  function toggleAssist() {
    demoAssist.value = !demoAssist.value
  }

  /** 提交给 useCheckIn 的可选参数：坐标与演示引导来自本 composable 的自查结果，壳不必自己拼 */
  function submitOptions(period, checkType) {
    return {
      key: slotKey(period.periodIndex, checkType),
      coordinate: submitCoord.value,
      demoHint: !demoAssist.value
    }
  }

  /** 秒级时钟：用定时器而非 requestAnimationFrame —— 秒级刷新不需帧同步，且 rAF 在后台页不触发 */
  function startClock() {
    stopClock()
    ticker = setInterval(() => {
      now.value = new Date()
    }, 1000)
  }

  function stopClock() {
    if (ticker) clearInterval(ticker)
    ticker = null
  }

  return {
    loading,
    error,
    status,
    now,
    wifi,
    wifiListText,
    position,
    locationError,
    locating,
    demoAssist,
    pendingKeys,
    shift,
    rule,
    periods,
    hasSchedule,
    shiftConfigured,
    doneCount,
    requireTotal,
    clockText,
    dateText,
    matchModeLabel,
    fenceDistance,
    inFence,
    submitCoord,
    periodsWithState,
    wifiBadge,
    wifiText,
    wifiHints,
    positionText,
    locateBadge,
    fenceHints,
    ruleChips,
    ruleHints,
    load,
    locate,
    toggleAssist,
    submitOptions,
    startClock,
    stopClock
  }
}
