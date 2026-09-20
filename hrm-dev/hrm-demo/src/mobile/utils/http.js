import axios from 'axios'
import { showFailToast } from 'vant'
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

const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/api/v1', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = readToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/** config.silent = true 时由调用方自行渲染错误文案（避免 Toast 与页内提示重复） */
function rejectWith(error, config) {
  if (!config || !config.silent) showFailToast(error.message)
  return Promise.reject(error)
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      const error = new Error(body.message || '请求失败')
      error.code = body.code
      error.data = body.data
      return rejectWith(error, response.config)
    }
    return body
  },
  (error) => {
    const body = (error.response && error.response.data) || {}
    const wrapped = new Error(body.message || error.message || '网络异常，请稍后重试')
    wrapped.code = body.code || (error.response && error.response.status)
    if (wrapped.code === 401) {
      clearAuth()
      // 401 不弹 Toast：首屏可能并发多个请求，逐个弹窗会刷屏
      window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT))
      return Promise.reject(wrapped)
    }
    return rejectWith(wrapped, error.config)
  }
)

export default http
