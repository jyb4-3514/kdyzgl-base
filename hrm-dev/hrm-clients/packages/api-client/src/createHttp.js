import axios from 'axios'
import { codeMessage } from '@kdyzgl/shared/constants/errorCode.js'

/**
 * 跨端请求层工厂（ADR §3.5 第 1 项）
 *
 * 为什么是「工厂 + 端注入」而不是搬一份现成实例：
 * 现状 PC 端走一期管理端的 `utils/request.js`（import Element Plus 的 ElMessage、动态 import 自己的 router），
 * 移动端走 `src/mobile/utils/http.js`（import Vant 的 showFailToast、用 window 事件广播跳转）。
 * 两者语义同构（baseURL / Bearer / 按 body.code 分发 / 401 清态），但与各自的 UI 与路由强绑定，
 * 直接搬任一份都会把端耦合带进共享包。故把「与端无关的契约」收在本文件，与端相关的一律注入：
 *   readToken / clearAuth / onUnauthorized / notifyError / clientType
 *
 * 本文件是 B1 骨架：契约与既有两端实现逐条对齐（含 GET 网络错重试一次、1108 幂等广播、blob 错误解析），
 * 但**尚未被任何端引用** —— 端侧切换见 B3/B4，期间既有实现保持不变。
 */

/** 被顶下线 / 未登录：必须清态并回登录页 */
export const UNAUTHORIZED_CODE = 401
/** 会话 3 天到期：与 401 同走「清态 + 回登录页」，但需带「到期」告知 */
export const SESSION_EXPIRED_CODE = 1108

/** 读请求 15s（弱网超时即给页内重试）；写请求 30s（打卡等提交需给足，且不自动重试） */
export const READ_TIMEOUT = 15000
export const WRITE_TIMEOUT = 30000
/** GET 网络抖动重试一次，退避 500ms，避免把刚恢复的链路再打挂 */
const RETRY_DELAY = 500

/**
 * @param {object} options
 * @param {string}   [options.baseURL='/api/v1']
 * @param {string}   [options.clientType]        端类型上报值（如 web/staff/boss/admin）→ X-Client-Type 头；空则不发
 * @param {Function} [options.readToken]         读当前 token：() => string
 * @param {Function} [options.clearAuth]         清本地登录态：() => void
 * @param {Function} [options.onUnauthorized]    401/1108 回调：(code) => void（端自行跳转，避免循环引用）
 * @param {Function} [options.notifyError]       通用错误提示：(message) => void（端注入 Toast / Message）
 * @param {number}   [options.readTimeout]
 * @param {number}   [options.writeTimeout]
 * @param {number}   [options.retryDelay]
 * @param {Function} [options.onRequest]         请求观测钩子：(config) => void（如运行日志采集）
 * @returns {import('axios').AxiosInstance}
 */
export function createHttp(options = {}) {
  const {
    baseURL = '/api/v1',
    clientType = '',
    readToken = () => '',
    clearAuth = () => {},
    onUnauthorized = () => {},
    notifyError = () => {},
    readTimeout = READ_TIMEOUT,
    writeTimeout = WRITE_TIMEOUT,
    retryDelay = RETRY_DELAY,
    onRequest,
    ...axiosOptions
  } = options

  const http = axios.create({ baseURL, ...axiosOptions })

  http.interceptors.request.use((config) => {
    const token = readToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
    // 端类型只作审计维度（后端明确其「非鉴权边界」，见 ClientAdmissionPolicy）；缺失即不发，避免写入空值
    if (clientType) config.headers['X-Client-Type'] = clientType
    // axios 默认 timeout=0 不限时，仅在调用方未显式指定时按方法兜底
    if (!config.timeout) config.timeout = config.method === 'get' ? readTimeout : writeTimeout
    if (onRequest) onRequest(config)
    return config
  })

  /** config.silent = true 时由调用方自行渲染错误文案（避免全局提示与页内提示重复） */
  const rejectWith = (error, config) => {
    if (!config || !config.silent) notifyError(error.message)
    return Promise.reject(error)
  }

  /**
   * 仅 GET 且「无 response 的网络错误」可重试一次：
   * 有 response 说明服务端已表态（业务码失败、401/403/404 都在其中），重试只会放大问题；
   * 写操作幂等性未保证，一律不重试。
   */
  const canRetry = (error) =>
    !!error.config && error.config.method === 'get' && !error.config.__retried && !error.response

  /**
   * 401 / 1108 幂等：首屏同一批并发请求会同时过期，只广播一次即可。
   * 复位放在下一宏任务，既覆盖同一批次并发，又保证下次会话过期仍能触发。
   */
  let unauthorizedNotified = false
  const notifyUnauthorized = (code) => {
    if (unauthorizedNotified) return
    unauthorizedNotified = true
    clearAuth()
    onUnauthorized(code)
    setTimeout(() => {
      unauthorizedNotified = false
    }, 0)
  }

  http.interceptors.response.use(
    async (response) => {
      // blob 请求：若实际返回 JSON（业务错误），先转文本再解析，走统一错误流程
      if (response.config.responseType === 'blob') {
        const blob = response.data
        if (blob && blob.type && blob.type.includes('application/json')) {
          let body = {}
          try {
            body = JSON.parse(await blob.text())
          } catch (e) {
            body = {}
          }
          if (body.code === UNAUTHORIZED_CODE) {
            notifyUnauthorized(UNAUTHORIZED_CODE)
            return Promise.reject(buildBizError(body))
          }
          return rejectWith(buildBizError(body), response.config)
        }
        return response
      }

      const body = response.data
      if (body && typeof body === 'object' && 'code' in body) {
        if (body.code === 200) return body.data
        // body.message 优先；服务端只给码时用码表兜底，不给用户一句无信息量的「请求失败」
        return rejectWith(buildBizError(body), response.config)
      }
      return body
    },
    async (error) => {
      if (canRetry(error)) {
        error.config.__retried = true
        await new Promise((resolve) => setTimeout(resolve, retryDelay))
        return http.request(error.config)
      }
      const resp = error.response
      if (resp) {
        let body = {}
        if (resp.data instanceof Blob) {
          try {
            body = JSON.parse(await resp.data.text())
          } catch (e) {
            body = {}
          }
        } else if (resp.data && typeof resp.data === 'object') {
          body = resp.data
        }
        const code = body.code || resp.status
        const wrapped = buildBizError(body, code)
        // 1108 与 401 同走「清态 + 回登录页」，且不弹 Toast（并发请求会刷屏）
        if (wrapped.code === UNAUTHORIZED_CODE || wrapped.code === SESSION_EXPIRED_CODE) {
          notifyUnauthorized(wrapped.code)
          return Promise.reject(wrapped)
        }
        return rejectWith(wrapped, error.config)
      }
      // 纯网络错误无 response、无业务码，回落到 axios 原始 message
      const wrapped = new Error(error.message || '网络异常，请稍后重试')
      wrapped.code = error.code
      return rejectWith(wrapped, error.config)
    }
  )

  return http
}

/** 统一构造携带 code/data 的业务错误对象，便于调用方按 code 分支处理（如导入 5003） */
function buildBizError(body, fallbackCode) {
  const err = new Error((body && body.message) || codeMessage(body && body.code) || '操作失败')
  err.code = (body && body.code) || fallbackCode
  err.data = body ? body.data : undefined
  return err
}
