import { DEVICE_ID_KEY } from './constants/storageKey.js'

/**
 * 设备弱信号采集（登录体系改造，三端登录页共用）
 *
 * 安全定位（security-auth-review §4.2）：以下字段全部**可被伪造**，仅作「降低误判」的弱信号上报；
 * 是否受信由服务端持有并签发，前端**不得**据此跳过二次验证，也不得持久化任何「设备受信」标志。
 * 为什么放 shared：PC 与移动端登录页都要采集同一组字段，各写一份必然随迭代漂移。
 */

/** 生成设备标识：优原生 UUID，降级时间戳+随机串（同机多次生成会变，故必须落盘复用） */
function createDeviceId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `dev-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

/** 读取（必要时生成并落盘）设备标识；无 localStorage 环境返回空串，不阻断登录 */
export function readDeviceId() {
  if (typeof localStorage === 'undefined') return ''
  try {
    let id = localStorage.getItem(DEVICE_ID_KEY)
    if (!id) {
      id = createDeviceId()
      localStorage.setItem(DEVICE_ID_KEY, id)
    }
    return id
  } catch (e) {
    // 隐私模式/配额异常：降级为进程内标识，登录链路不受影响
    return createDeviceId()
  }
}

/** 从 UA 粗提系统大类（服务端另有 UA 归一化，此处仅作展示与弱指纹补充） */
function parseOs(ua) {
  if (/Windows NT 10/.test(ua)) return 'Windows 10+'
  if (/Android/.test(ua)) return `Android ${(ua.match(/Android ([\d.]+)/) || [])[1] || ''}`.trim()
  if (/iPhone|iPad|Mac OS X/.test(ua)) return 'iOS/macOS'
  if (/Linux/.test(ua)) return 'Linux'
  return '未知'
}

/**
 * 采集登录页上报的设备信息
 * @param {'WEB'|'H5'} platform 端类型（网页端传 WEB，移动端传 H5）
 */
export function collectDevicePayload(platform) {
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent : ''
  return {
    deviceId: readDeviceId(),
    platform,
    model: typeof navigator !== 'undefined' ? navigator.platform || 'Web' : 'Unknown',
    osVersion: parseOs(ua),
    appVersion: '',
    screen: typeof screen !== 'undefined' ? `${screen.width}x${screen.height}` : '',
    timezone: (() => {
      try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || ''
      } catch (e) {
        return ''
      }
    })()
  }
}
