/**
 * WiFi 白名单校验与权限分支（打卡规则页 · 可编辑区）
 *
 * 为什么抽成纯函数：判定口径必须与后端（Mock）对齐 —— 打卡只按 SSID 精确比对（`w.ssid === wifiSsid`，
 * 区分大小写），BSSID 不参与判定；抽出来才能单测锁死，页面只负责把结果落到行内 error-message 与 ActionBar note。
 * 文案取自设计规范 boss-wifi-and-station-design.md §7（T14–T17 等），不得在页面里另写一份。
 */

/** SSID 长度上限：IEEE 802.11 的 SSID 上限即 32 字节（客观依据，非设计随手取值） */
export const SSID_MAX = 32

/** MAC 地址：AA:BB:CC:DD:EE:FF（6 组两位十六进制，冒号分隔） */
export const BSSID_RE = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/

/** 新增行的哨兵 key：与已保存行的 key 区分，用于「新增行取消即不写入列表」 */
export const NEW_WIFI_KEY = '__new__'

export const SSID_EMPTY_TEXT = '请填写 WiFi 名称（SSID）'
export const SSID_TOO_LONG_TEXT = 'WiFi 名称最长 32 个字符'
export const BSSID_INVALID_TEXT = 'MAC 地址格式应为 AA:BB:CC:DD:EE:FF'
export const SSID_DUPLICATE_TEXT = '该 WiFi 已存在，请勿重复添加'

/** SSID 单值校验：空 → 必填；>32 → 超长；与同站其它项重复（精确比对，区分大小写，后端口径同此） */
export function validateSsid(value, { list = [], selfKey = null } = {}) {
  const text = String(value == null ? '' : value).trim()
  if (!text) return SSID_EMPTY_TEXT
  if (text.length > SSID_MAX) return SSID_TOO_LONG_TEXT
  const duplicated = list.some((item) => item && item.key !== selfKey && String(item.ssid || '').trim() === text)
  if (duplicated) return SSID_DUPLICATE_TEXT
  return ''
}

/** BSSID 选填：留空放行；填了必须是合法 MAC（仅留痕，不参与打卡判定） */
export function validateBssid(value) {
  const text = String(value == null ? '' : value).trim()
  if (!text) return ''
  return BSSID_RE.test(text) ? '' : BSSID_INVALID_TEXT
}

/** 行内错误汇总：{ ssid, bssid }，空串表示该字段无错 */
export function rowErrors(draft, { list = [], selfKey = null } = {}) {
  return {
    ssid: validateSsid(draft && draft.ssid, { list, selfKey }),
    bssid: validateBssid(draft && draft.bssid)
  }
}

/** 未填 SSID 的条数：ActionBar note 只给结论，不重复行内细节（设计 §3.1.6） */
export function countEmptySsid(list = []) {
  return list.filter((item) => !String((item && item.ssid) || '').trim()).length
}

/**
 * 权限分支：本端（驿站精灵）BOSS_ROLES = [ADMIN]，登录者恒为 ADMIN → 可编辑是主路径；
 * 非 ADMIN 属防御位（只读、不渲染任何写控件），不得据此宣称站长已可在本端查看。
 */
export function isWifiEditable(isAdmin) {
  return isAdmin === true
}

/** 卡标题 extra：{n} 条 · 可编辑 / 只读（T1 / T2） */
export function wifiExtraLabel(count, editable) {
  return `${count} 条 · ${editable ? '可编辑' : '只读'}`
}

/**
 * 「读取当前 WiFi」自动回填判据：仅当壳侧真的返回非 mock 且 ssid 非空才允许回填。
 * 当前安卓壳未实现 HrmBridge.getWifiInfo，桥接恒返回 mock:true → 一律走手动输入。
 * 硬约束：判据用 mock === false，不得用「是否在壳内」（壳内也可能返回 mock）。
 */
export function canAutoFillWifi(info) {
  return !!info && info.mock === false && !!info.ssid
}
