import { clockOf, coordText, distanceText } from '@/utils/attendance.js'

/**
 * 打卡页展示常量与判定状态键
 *
 * 为什么把「状态键」集中在这里：itemState 的产出、CheckSlotRow 的样式分支、单测三处都要用同一组字符串，
 * 散成字面量后改一个键就会出现「有状态无样式」的静默漏，页面上表现为状态文案变色失效。
 * 为什么 periodWindowText 不在此文件：它属跨页共用的时间窗推导（记录页/排班页同用），留在 utils/attendance.js。
 */

/** 单个打卡槽位的 6 态键 */
export const ITEM_STATE = {
  DONE: 'done',
  PENDING: 'pending',
  WAIT: 'wait',
  TODO: 'todo',
  OVERDUE: 'overdue',
  MISSED: 'missed'
}

/** 槽位只认 ON/OFF：与 /attendance/status 下发的 periods 契约一致，页面不自行推演时段数量 */
export const CHECK_TYPES = ['ON', 'OFF']

/** 卡类型文案（按钮文案与 dict 的「上班卡/下班卡」刻意不同，故不共用一条常量） */
export const CHECK_ACTION_TEXT = { ON: '上班打卡', OFF: '下班打卡' }

/** 补卡理由长度门槛：与弹层 maxlength 同源，避免模板与校验各写一个数 */
export const MAKEUP_REASON_MIN = 2
export const MAKEUP_REASON_MAX = 200

/** 槽位定位键 = 时段序号 + 卡类型；补卡唯一性口径「员工 + 日期 + 时段 + 卡类型」在页面内的表达 */
export const slotKey = (periodIndex, checkType) => `${periodIndex}-${checkType}`

/** 该时段已完成卡数（0/1/2） */
export const periodDoneCount = (period) => (period && period.onChecked ? 1 : 0) + (period && period.offChecked ? 1 : 0)

/** 6 态文案：含数据插值的做成函数，避免一句话被拆成模板拼接而漏改一处 */
export const slotText = {
  done: (time) => `${clockOf(time)} 已打卡`,
  pending: () => '已提交补卡申请，待管理员审批',
  wait: (openAt) => `未到打卡时间（${openAt} 开放）`,
  todo: () => '未打卡',
  overdue: (due, windowEnd) => `已过 ${due} 未打卡（${windowEnd} 前可补打）`,
  missed: () => '已过期未打卡，待补卡'
}

/** 驿站归属兜底文案（演示账号未挂驿站时也要能看出「未归属」而不是空白） */
export const STATION_FALLBACK = '未归属驿站'

/** WiFi 自查卡文案：未取到真实 SSID 时如实给「未获取到」+ 需客户端提示，绝不回填白名单值冒充当前 WiFi（安全评估 A 档） */
export const WIFI_EMPTY = '未获取到'
export const WIFI_UNAVAILABLE_BADGE = '需客户端'
export const WIFI_WHITELIST_EMPTY = '未配置'
export const WIFI_WHITELIST_PREFIX = '规则白名单：'
export const WIFI_NO_CLIENT_HINT =
  '浏览器/未实现壳无法读取真实 SSID，未获取到当前 WiFi；需安装客户端（安卓壳）才能完成 WiFi 校验打卡'
export const WIFI_REAL_HINT = '由安卓壳读取的真实 WiFi'
/** 演示构建专用：Mock 后端无真实壳，提交时按规则白名单首项模拟，仅用于演示打卡链路（生产构建不可达） */
export const WIFI_DEMO_HINT = '演示构建：浏览器无法读取真实 WiFi，打卡提交时按规则白名单首项模拟，仅用于演示'

/** 定位自查卡文案（生产口径：不引导演示手段；「演示辅助」入口只在演示态页面上出现，见 attendance.vue） */
export const LOCATE_BTN_TEXT = '重新定位'
export const LOCATE_NO_VALUE = '尚未获取到定位'
export const LOCATE_IN_FENCE = '在围栏内'
export const LOCATE_OUT_FENCE = '超出围栏'
export const LOCATE_UNSUPPORTED = '当前环境不提供定位能力，请联系管理员'
export const LOCATE_DENIED = '定位未授权：请在浏览器/系统设置中允许定位后重试'
export const LOCATE_FAILED = '定位获取失败，请稍后重试'

/** 演示辅助定位标记（仅演示态可达）：显式标注来源，不与真实定位混淆（硬约束，不做隐形后门） */
export const DEMO_ASSIST_BADGE = '演示辅助 · 围栏中心'

/**
 * 班次 VO → ShiftCard props 的展示映射
 * 唯一差异是契约字段 color 要落到组件的 colorKey（组件内再经「契约色 → Token」映射落色，P1-1）；
 * 映射属展示适配，按 §4.2 的裁决放 model，不进 store、也不让组件直读接口字段名。
 */
export const shiftProps = (shift) => {
  const s = shift || {}
  return {
    shiftName: s.shiftName,
    startTime: s.startTime,
    endTime: s.endTime,
    restMinutes: s.restMinutes,
    colorKey: s.color
  }
}

/** 规则启停标记的静态文案（顺序即展示顺序） */
export const RULE_CHIP_LABELS = ['WiFi', '定位', '时间窗']
export const RULE_MATCH_PREFIX = '组合：'

/**
 * 打卡页就近入口：排班与记录不是打卡页的职责，但员工到打卡页时最可能顺手要看；
 * 与宫格/我的页同属「就近入口」，按 demo-mobile-nav-redesign B5-4 不视为重复入口。
 */
export const MORE_LINKS = [
  { label: '我的排班', to: '/staff/schedule' },
  { label: '打卡记录', to: '/staff/attendance/records' },
  { label: '补卡申请', to: '/staff/attendance/makeup' }
]

/* ==================== 纯展示映射（不依赖响应式，供 composable 直调） ==================== */

/** WiFi 标记：未取到真实值给「需客户端」提示（替代原「模拟」标记），真实值不给标记以免误读为「已校验」 */
export const wifiBadgeOf = (wifi) => (wifi.mock ? { text: WIFI_UNAVAILABLE_BADGE, tone: 'warning', note: true } : null)

/** 未取到 SSID 给「未获取到」而不是空白：空白会被读成加载中，与「确实没取到」不是一回事 */
export const wifiTextOf = (wifi) => wifi.ssid || WIFI_EMPTY

/**
 * WiFi 自查卡明细：未取到真实值给「需安装客户端」提示；演示构建额外说明提交时会按白名单模拟（生产不可达）。
 * 白名单始终单列一行并明示为「规则白名单」，不作为「当前 WiFi」展示。
 */
export const wifiHintsOf = (wifi, wifiListText, demoEnabled = false) => {
  const hints = [{ text: wifi.mock ? WIFI_NO_CLIENT_HINT : WIFI_REAL_HINT }]
  if (wifi.mock && demoEnabled) hints.push({ text: WIFI_DEMO_HINT })
  hints.push({ text: `${WIFI_WHITELIST_PREFIX}${wifiListText || WIFI_WHITELIST_EMPTY}` })
  return hints
}

/** 定位主值：演示辅助取围栏中心，否则按真实定位；两者都没有时给「尚未获取到」 */
export const positionTextOf = (rule, position, demoAssist) => {
  if (demoAssist && rule) return `${coordText(rule.longitude)}, ${coordText(rule.latitude)}`
  if (!position) return LOCATE_NO_VALUE
  return `${coordText(position.longitude)}, ${coordText(position.latitude)}`
}

/** 定位标记：演示辅助与围栏内同为「可提交」的确定态（ok 样式），超出围栏才是 warn */
export const locateBadgeOf = (inFence, demoAssist) => ({
  text: demoAssist ? DEMO_ASSIST_BADGE : inFence ? LOCATE_IN_FENCE : LOCATE_OUT_FENCE,
  tone: demoAssist || inFence ? 'success' : 'danger'
})

export const fenceHintsOf = (rule, fenceDistance, position, demoAssist) => {
  if (!rule) return []
  const accuracy = position && !demoAssist && position.accuracy ? ` · 定位精度约 ${position.accuracy} 米` : ''
  return [
    {
      text: `围栏中心 ${coordText(rule.longitude)}, ${coordText(rule.latitude)} · 允许半径 ${rule.radius} 米`,
      numeric: true
    },
    { text: `距围栏中心 ${distanceText(fenceDistance)}${accuracy}`, numeric: true }
  ]
}

export const ruleChipsOf = (rule, matchModeLabel) => {
  if (!rule) return []
  return [
    ...RULE_CHIP_LABELS.map((text, index) => ({
      text,
      on: [rule.enableWifi, rule.enableLocation, rule.enableTimeWindow][index] === true,
      tone: 'primary'
    })),
    { text: `${RULE_MATCH_PREFIX}${matchModeLabel}`, on: true, tone: 'primary' }
  ]
}

export const ruleHintsOf = (rule, requireSummary) => {
  if (!rule) return []
  const hints = []
  if (requireSummary) hints.push({ text: requireSummary })
  hints.push({ text: `迟到阈值 ${rule.lateThresholdMin} 分钟 · 早退阈值 ${rule.earlyLeaveThresholdMin} 分钟` })
  hints.push({
    text: `时间窗 = 时段开始提前 ${rule.allowEarlyMin} 分钟开放、时段结束延后 ${rule.allowLateMin} 分钟关闭`
  })
  if (!rule.enableWifi || !rule.enableLocation || !rule.enableTimeWindow) {
    hints.push({ text: '未启用的校验项不参与判定；三项全关时为免校验打卡' })
  }
  return hints
}
