import axios from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY } from '../stores/auth'

/**
 * Axios 统一封装（契约见 docs/api.md 第 1 章）
 * - baseURL=/api/v1，开发经 Vite proxy、生产经 Nginx 反代，前后端始终同源（决策 D9）
 * - 请求拦截器：自动附加 Authorization: Bearer {token}
 * - 响应拦截器按 body.code 分发：
 *   401 → 清登录态并跳 /login；403 → 无权限提示；其余非 200 → message 提示
 * - 业务错误返回 HTTP 200 + code；401/403/404 同步 HTTP 状态码，两条路径均已覆盖
 * - 支持 responseType=blob 的文件流请求（模板下载/导出），若后端返回 JSON 错误则解析后统一报错
 * - config.silent=true 时不弹通用错误 toast（登录页需要自行区分 1001/1002 文案）
 */
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

// 防止并发请求同时 401 时重复跳转登录
let redirecting401 = false

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/** 构造携带 code/data 的业务错误对象，便于调用方按 code 分支处理（如导入 5003） */
function buildBizError(body) {
  const err = new Error((body && body.message) || '操作失败')
  err.code = body ? body.code : undefined
  err.data = body ? body.data : undefined
  return err
}

/** 401 统一处理：清登录态 → 跳登录页（携带回跳地址） */
async function handleUnauthorized() {
  if (redirecting401) return
  redirecting401 = true
  try {
    const { useAuthStore } = await import('../stores/auth')
    useAuthStore().clearAuth()
    const { default: router } = await import('../router')
    if (router.currentRoute.value.path !== '/login') {
      await router.replace({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    }
  } finally {
    // 延迟复位，吸收同一时间窗口内的并发 401
    setTimeout(() => {
      redirecting401 = false
    }, 1000)
  }
  ElMessage.error('登录已失效，请重新登录')
}

/** 业务码统一提示（HTTP 200 + code!=200 场景） */
async function handleBizErrorTip(body, config) {
  if (body && body.code === 401) {
    await handleUnauthorized()
    return
  }
  if (body && body.code === 403) {
    ElMessage.error(body.message || '无权限访问该资源')
    return
  }
  // silent 模式不弹通用提示，由调用方自行展示（如登录页 1001/1002 文案区分）
  if (config && config.silent) return
  ElMessage.error((body && body.message) || '操作失败')
}

service.interceptors.response.use(
  async (response) => {
    // 文件流请求：若实际返回 JSON（业务错误），解析后走统一错误流程
    if (response.config.responseType === 'blob') {
      const blob = response.data
      if (blob && blob.type && blob.type.includes('application/json')) {
        let body = {}
        try {
          body = JSON.parse(await blob.text())
        } catch (e) {
          body = {}
        }
        await handleBizErrorTip(body, response.config)
        return Promise.reject(buildBizError(body))
      }
      return response
    }

    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200) {
        // 统一返回 data 部分，调用方直接拿业务数据
        return res.data
      }
      await handleBizErrorTip(res, response.config)
      return Promise.reject(buildBizError(res))
    }
    return res
  },
  async (error) => {
    const resp = error.response
    if (resp) {
      // blob 错误响应需先转文本再解析 JSON
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
      if (resp.status === 401) {
        await handleUnauthorized()
        return Promise.reject(buildBizError(body))
      }
      if (resp.status === 403) {
        ElMessage.error(body.message || '无权限访问该资源')
        return Promise.reject(buildBizError(body))
      }
      const silent = error.config && error.config.silent
      if (!silent) {
        ElMessage.error(body.message || `请求失败（HTTP ${resp.status}）`)
      }
      return Promise.reject(buildBizError(body))
    }
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default service
