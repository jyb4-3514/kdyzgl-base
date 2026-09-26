/**
 * WiFi 白名单校验与权限分支（打卡规则页 · 单条配置区）
 *
 * 为什么抽成纯函数：判定口径必须与后端对齐 —— 打卡只按 SSID 精确比对（区分大小写），BSSID 不参与判定；
 * 抽出来才能单测锁死，页面只负责把结果落到行内 error-message 与 ActionBar note。
 * 文案取自设计规范 boss-wifi-and-station-design.md §12.4 / §12.6，不得在页面里另写一份。
 *
 * 单条化（设计 §12）：每站白名单至多一条；「SSID 重复 / 多条未填 / 条数上限」随口径变更作废（§12.6）。
 */

/** SSID 长度上限：32 个字符（对应 IEEE 802.11 的 32 octets，本实现按字符数校验，与 maxlength=32 对齐） */
export const SSID_MAX = 32

/** MAC 地址：AA:BB:CC:DD:EE:FF（6 组两位十六进制，冒号分隔） */
export const BSSID_RE = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/

export const SSID_EMPTY_TEXT = '请填写 WiFi 名称（SSID）'
export const SSID_TOO_LONG_TEXT = 'WiFi 名称最长 32 个字符'
export const BSSID_INVALID_TEXT = 'MAC 地址格式应为 AA:BB:CC:DD:EE:FF'

/** SSID 单值校验：空 → 必填；>32 → 超长（重复校验已随「每站仅一条」作废，见设计 §12.6） */
export function validateSsid(value) {
  const text = String(value == null ? '' : value).trim()
  if (!text) return SSID_EMPTY_TEXT
  if (text.length > SSID_MAX) return SSID_TOO_LONG_TEXT
  return ''
}

/** BSSID 选填：留空放行；填了必须是合法 MAC（仅留痕，不参与打卡判定） */
export function validateBssid(value) {
  const text = String(value == null ? '' : value).trim()
  if (!text) return ''
  return BSSID_RE.test(text) ? '' : BSSID_INVALID_TEXT
}

/** 行内错误汇总：{ ssid, bssid }，空串表示该字段无错 */
export function rowErrors(draft) {
  return {
    ssid: validateSsid(draft && draft.ssid),
    bssid: validateBssid(draft && draft.bssid)
  }
}

/**
 * 单条化收敛（设计 §12.12①）：服务端若返回多条历史数据，只取首条渲染，并回传「存在多条」标记用于 T39 提示。
 * 返回 { entry, hasLegacyMultiple }；entry 为 null 表示本站未配置白名单。
 */
export function pickWifiEntry(list) {
  const arr = Array.isArray(list) ? list : []
  const first = arr[0]
  return {
    entry: first
      ? { ssid: first.ssid ? String(first.ssid) : '', bssid: first.bssid ? String(first.bssid) : '' }
      : null,
    hasLegacyMultiple: arr.length > 1
  }
}

/**
 * 权限分支：本端（驿站精灵）BOSS_ROLES = [ADMIN]，登录者恒为 ADMIN → 可编辑是主路径；
 * 非 ADMIN 属防御位（只读、不渲染任何写控件），不得据此宣称站长已可在本端查看。
 */
export function isWifiEditable(isAdmin) {
  return isAdmin === true
}

/** 卡标题 extra：可编辑 / 只读（T30；单条化后不再显示条数） */
export function wifiExtraLabel(editable) {
  return editable ? '可编辑' : '只读'
}

/**
 * 「读取当前 WiFi」自动回填判据：仅当壳侧真的返回非 mock 且 ssid 非空才允许回填。
 * 当前安卓壳未实现 HrmBridge.getWifiInfo，桥接恒返回 mock:true → 一律走手动输入。
 * 硬约束：判据用 mock === false，不得用「是否在壳内」（壳内也可能返回 mock）。
 */
export function canAutoFillWifi(info) {
  return !!info && info.mock === false && !!info.ssid
}
