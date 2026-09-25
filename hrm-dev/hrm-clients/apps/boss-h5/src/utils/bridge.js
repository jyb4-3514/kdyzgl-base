/**
 * 安卓 H5 壳桥接（demo-design.md 8.2 约定）
 *
 * 为什么全部方法都做「空实现兜底」：浏览器（npm run dev / build preview）下没有 HrmBridge，
 * 页面必须零报错、状态栏高度取 0、布局不塌陷 —— 这是 T13 的验收项。
 * 调用细节：H5 → 原生的入参一律字符串（addJavascriptInterface 只可靠支持基本类型），复杂对象走 JSON 字符串。
 */
const STATUS_BAR_VAR = '--status-bar-height'

const nativeBridge = () => (typeof window === 'undefined' ? null : window.HrmBridge)

export const isShell = () => !!nativeBridge()

function call(name, ...args) {
  const api = nativeBridge()
  return api && typeof api[name] === 'function' ? api[name](...args) : null
}

/** 壳注入的设备信息，返回对象；非壳环境或解析失败返回 null */
export function getDeviceInfo() {
  const raw = call('getDeviceInfo')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

/** 状态栏图标深浅（深色背景壳用 false） */
export function setStatusBarStyle(dark) {
  call('setStatusBarStyle', JSON.stringify({ dark: !!dark }))
}

/** 原生 Toast 兜底：H5 弹层被 WebView 遮挡时使用 */
export function shellToast(text) {
  call('toast', String(text))
}

/**
 * 读取当前连接的 WiFi（打卡的 WiFi 校验项用）
 *
 * 为什么必须区分壳内 / 浏览器：W3C 从未标准化「网页读取当前 SSID」的能力，
 * 浏览器下任何库都拿不到真实 SSID —— 因此浏览器分支只能返回模拟值，
 * 并由调用方在界面上明确标注「模拟」，不得伪装成真实能力。
 *
 * @param {string} fallbackSsid 浏览器下的模拟值来源（取打卡规则白名单中的一项），
 *                              使纯前端演示能走通 WiFi 校验分支
 * @returns {{ ssid: string, bssid: string, mock: boolean }}
 */
export function getWifiInfo(fallbackSsid = '') {
  const raw = call('getWifiInfo')
  if (raw) {
    try {
      const info = JSON.parse(raw)
      if (info && info.ssid) {
        return { ssid: String(info.ssid), bssid: info.bssid ? String(info.bssid) : '', mock: false }
      }
    } catch (e) {
      /* 壳侧返回非 JSON：按「取不到」处理，走下面的模拟分支，页面依旧可用 */
    }
  }
  return { ssid: String(fallbackSsid || ''), bssid: '', mock: true }
}

export function closeShell() {
  call('close')
}

/** 写入状态栏高度变量；无壳、值为 0 或非法时统一写 0px（布局不塌陷） */
export function setStatusBarHeight(px) {
  const height = Number(px)
  document.documentElement.style.setProperty(STATUS_BAR_VAR, `${Number.isFinite(height) && height > 0 ? height : 0}px`)
}

/**
 * 返回键消费：非 Tabbar 根页（详情/表单）回退并返回 true；
 * 已在根页返回 false，交原生执行「再按一次退出应用」
 */
function consumeBack(router) {
  const route = router.currentRoute.value
  if (!route.meta.tabbar && window.history.length > 1) {
    router.back()
    return true
  }
  return false
}

/**
 * 注册壳回调并同步状态栏高度，返回是否运行在壳内
 * 浏览器下同样注册 HrmShell：原生不存在也不会调用它，注册可让壳侧逻辑与浏览器端行为一致
 */
export function initShell(router) {
  setStatusBarHeight(0)
  const info = getDeviceInfo()
  if (info && info.statusBarHeight) setStatusBarHeight(info.statusBarHeight)
  setStatusBarStyle(true)

  window.HrmShell = {
    onBackPressed: () => consumeBack(router),
    // 页面恢复（切回前台）时刷新未读角标等易变数据
    onResume: () => window.dispatchEvent(new CustomEvent('hrm:shell-resume')),
    setStatusBarHeight
  }
  return isShell()
}
