import { showFailToast } from 'vant'
import { createHttp } from '@kdyzgl/api-client'
import { clearAuth, readToken } from './authStorage.js'

/**
 * 员工端 HTTP 封装 —— 消费共享工厂 @kdyzgl/api-client（ADR §3.5 第 1/12 项，B3 真正接线）
 *
 * 与演示工程移动端 utils/http.js 语义逐条等价（baseURL / Bearer 注入 / 按 body.code 分发 /
 * GET 网络错重试一次 / 401 与 1108 幂等广播），差异只在「端注入项」：
 * 1. 端类型：恒上报 clientType=STAFF（X-Client-Type 头，审计维度；后端 fail-closed 认此值）
 * 2. 错误提示载体：Vant showFailToast（移动端不引 Element Plus 的 Message）
 * 3. 401 不直接 import router（会与 router → 页面 → api → http 形成循环引用），
 *    改为广播自定义事件，由 App.vue 统一做登录态清理与跳转
 *
 * Mock 适配器不在这里装配：本模块保持对 mock 零依赖，由 main.js 在挂载前按开关动态装配。
 */
export const UNAUTHORIZED_EVENT = 'hrm:staff-unauthorized'

const http = createHttp({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  clientType: 'STAFF',
  readToken,
  clearAuth,
  onUnauthorized: (code) => {
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT, { detail: { code } }))
  },
  notifyError: (message) => showFailToast(message)
})

export default http
