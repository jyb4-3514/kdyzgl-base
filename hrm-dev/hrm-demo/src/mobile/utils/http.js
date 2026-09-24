import axios from 'axios'
import { showFailToast } from 'vant'
import { codeMessage } from '@/shared/constants/errorCode.js'
import { clearAuth, readToken } from './authStorage.js'

/**
 * 移动端 HTTP 封装
 *
 * 与 PC 端 @admin/utils/request.js 同构：统一 baseURL、Bearer 注入、按 body.code 分发、401 强制回登录页。
 * 两处刻意的差异：
 * 1. 错误提示载体换成 Vant Toast（移动端不引 Element Plus 的 Message）
 * 2. 401 不直接 import router（会与 router → 页面 → api → http 形成循环引用），
 *    改为广播事件，由 App.vue 统一做登录态清理与跳转
 *
 * Mock 适配器不在这里装配：本模块保持对 shared/mock 零依赖，由 mobile/main.js 在挂载前按开关动态装配。
 */
export const UNAUTHORIZED_EVENT = 'hrm:mobile-unauthorized'

/** 读请求 15s（弱网超时即给页内重试）；写请求 30s（打卡等提交需给足，且不自动重试） */
const READ_TIMEOUT = 15000
const WRITE_TIMEOUT = 30000
/** GET 网络抖动重试一次，退避 500ms，避免把刚恢复的链路再打挂 */
const RETRY_DELAY = 500

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api/v1' })

http.interceptors.request.use((config) => {
  const token = readToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  // 超时按方法区分（axios 默认 0 = 不限时，故仅在未显式指定时兜底）
  if (!config.timeout) config.timeout = config.method === 'get' ? READ_TIMEOUT : WRITE_TIMEOUT
  return config
})

/** config.silent = true 时由调用方自行渲染错误文案（避免 Toast 与页内提示重复） */
function rejectWith(error, config) {
  if (!config || !config.silent) showFailToast(error.message)
  return Promise.reject(error)
}

/**
 * 仅 GET 且「无 response 的网络错误」可重试一次：
 * 有 response 说明服务端已表态 —— 业务码失败、401/403/404 都在其中，重试只会放大问题；
 * 写操作幂等性未保证，一律不重试。
 */
function canRetry(error) {
  const config = error.config
  return !!config && config.method === 'get' && !config.__retried && !error.response
}

/**
 * 401 / 1108 幂等：首屏同一批并发请求会同时过期，只广播一次即可。
 * 1108（会话 3 天到期）与 401（被顶下线/禁用）都必须清态并回登录页，但前者要带「到期」告知与 redirect，
 * 故把业务码放进事件 detail，由 App.vue 分流（http.js 不 import router，避免循环引用）。
 * 复位放在下一宏任务，既覆盖同一批次的并发，又保证下次会话过期仍能触发。
 */
let unauthorizedNotified = false
function notifyUnauthorized(code) {
  if (unauthorizedNotified) return
  unauthorizedNotified = true
  window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT, { detail: { code } }))
  setTimeout(() => {
    unauthorizedNotified = false
  }, 0)
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      // body.message 优先；服务端只给码时用码表兜底，不给用户一句无信息量的「请求失败」
      const error = new Error(body.message || codeMessage(body.code))
      error.code = body.code
      error.data = body.data
      return rejectWith(error, response.config)
    }
    return body
  },
  async (error) => {
    if (canRetry(error)) {
      error.config.__retried = true
      await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY))
      return http.request(error.config)
    }
    const body = (error.response && error.response.data) || {}
    const status = error.response && error.response.status
    const code = body.code || status
    // 服务端已表态（有码）时用码表兜底；纯网络错误无码，回落到 axios 原始 message
    const message = body.message || (code ? codeMessage(code) : '') || error.message || '网络异常，请稍后重试'
    const wrapped = new Error(message)
    wrapped.code = code
    // 1108（会话 3 天到期）与 401 同走「清态 + 广播回登录页」；两者不弹 Toast（并发请求会刷屏）
    if (wrapped.code === 401 || wrapped.code === 1108) {
      clearAuth()
      notifyUnauthorized(wrapped.code)
      return Promise.reject(wrapped)
    }
    return rejectWith(wrapped, error.config)
  }
)

export default http
