import axios from 'axios'
import { routes } from './routes/index.js'
import { CODE, codeMessage } from '../constants/errorCode.js'
import { db, findEmployeeById } from './db.js'

/**
 * Mock 引擎：axios 自定义适配器
 *
 * 为什么用 adapter 而不是 Vite 中间件 / MSW：见 demo-design.md 7.1（中间件在 build 产物与安卓壳离线包里失效；MSW 依赖 Service Worker）
 * 挂载点：`instance.defaults.adapter = mockAdapter`，一期 utils/request.js 零改动即可被拦截
 *
 * ===== 7.3 三处待验证点的本地实测结论（Node v24.19.0 + axios 1.13.x，脚本 scripts/verify-mock.mjs）=====
 * V1 config.url 是否已含 baseURL → 【已实测：否，与设计稿预期不同】。axios 1.x 的 dispatchRequest 不做 baseURL 合并，
 *    buildFullPath 是在具体 adapter（xhr/http）内部执行的，因此自定义 adapter 收到的 config.url 就是调用方传入的
 *    相对路径（实测 `POST /auth/login` → url = "/auth/login"，baseURL 单独在 config.baseURL 中）。
 *    本引擎的归一化按「绝对地址去 origin → 已含 baseURL 前缀则剥离 → 已含 /api/v1 则剥离 → 原样使用」兼容两种形态，
 *    因此无论后续 axios 版本是否改为提前合并，路由匹配都不受影响；切勿做无条件的 baseURL 拼接（会得到 /api/v1/api/v1/...）。
 * V2 config.data 是否为 JSON 字符串 → 【已实测：是】。默认 transformRequest 对普通对象调用 JSON.stringify，
 *    adapter 内 typeof config.data === 'string'；GET 请求为 undefined；FormData（Excel 导入）保持 FormData 实例，
 *    故解析需按「字符串 → JSON.parse（失败则原样保留）／FormData／对象」三路分支，不能无脑 JSON.parse。
 * V3 adapter 返回对象能否触发响应拦截器 → 【已实测：能】。适配器 resolve/reject 的 Promise 仍会走
 *    dispatchRequest → 响应拦截器链，因此 request.js 的 code 分发、401 跳登录、blob 解析全部按原样生效。
 *    注意：自定义 adapter 必须自己完成「非 2xx 是否 reject」的判定（内置 xhr/http 适配器由 settle 完成），
 *    本引擎对 401/403/404 主动 reject，使 request.js 的 error.response 分支与 body.code 分支都能覆盖。
 */

const API_PREFIX = '/api/v1'
/** 随机延迟区间：让页面 loading/骨架屏可见，又不拖慢演示节奏（demo-design.md 7.2 步骤 6） */
const DELAY_MIN = 120
const DELAY_MAX = 350

/** 预编译路由：路径参数 `:id` → 正则捕获组，避免每次请求重复编译 */
const compiledRoutes = routes.map((route) => {
  const keys = []
  const pattern = route.path
    .replace(/\/:([A-Za-z0-9_]+)/g, (_, key) => {
      keys.push(key)
      return '/([^/]+)'
    })
    .replace(/\//g, '\\/')
  return { ...route, keys, regex: new RegExp(`^${pattern}$`) }
})

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 归一化请求路径：剥离 origin、query 与 baseURL 前缀 */
export function normalizePath(config) {
  let url = config.url || ''
  if (/^https?:\/\//i.test(url)) {
    try {
      url = new URL(url).pathname
    } catch (e) {
      /* 非法绝对地址按原串处理，交给路由匹配失败兜底 */
    }
  }
  url = url.split('?')[0]
  const baseURL = (config.baseURL || '').replace(/\/$/, '')
  if (baseURL && url.startsWith(baseURL)) url = url.slice(baseURL.length)
  if (url.startsWith(API_PREFIX)) url = url.slice(API_PREFIX.length)
  return url || '/'
}

/** 解析请求体（V2 结论的落地）：JSON 字符串 / FormData / 普通对象三路处理 */
function normalizeBody(config) {
  const raw = config.data
  if (raw == null || raw === '') return {}
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch (e) {
      return {}
    }
  }
  // FormData（multipart/form-data）：保留实例，导入接口按 file.size / file.name 做演示级校验
  return raw
}

/** 解析 Bearer token：Mock 令牌格式 mock.{employeeId}.{jti}，jti 与 db.sessions 比对实现互踢/强制下线 */
function resolveUser(config) {
  const header = (config.headers && (config.headers.Authorization || config.headers.authorization)) || ''
  const token = String(header)
    .replace(/^Bearer\s+/i, '')
    .trim()
  if (!token) return { error: CODE.UNAUTHORIZED }
  const [, idPart, jti] = token.split('.')
  const employee = findEmployeeById(idPart)
  if (!employee || employee.status !== 1) return { error: CODE.UNAUTHORIZED }
  if (db.sessions.get(employee.id) !== jti) return { error: CODE.UNAUTHORIZED }
  return { employee, token }
}

/** 业务码 → HTTP 状态码：仅 401/403/404 同步（api.md 1.3，业务错误一律 HTTP 200） */
function httpStatusOf(code) {
  return code === CODE.UNAUTHORIZED || code === CODE.FORBIDDEN || code === CODE.NOT_FOUND ? code : 200
}

/** 构造可被 axios 响应拦截器识别为「HTTP 错误」的 rejection（覆盖 error.response 分支） */
function buildHttpError(response) {
  const message = (response.data && response.data.message) || codeMessage(response.status)
  if (typeof axios.AxiosError === 'function') {
    return new axios.AxiosError(message, String(response.status), response.config, null, response)
  }
  const error = new Error(message)
  error.isAxiosError = true
  error.config = response.config
  error.response = response
  return error
}

/** 组装标准 axios 响应对象（blob 请求单独处理 body 形态） */
function buildResponse(config, { code, message, data, headers }) {
  const status = httpStatusOf(code)
  const body = { code, message: message || codeMessage(code), data: data === undefined ? null : data }
  let responseData
  const responseHeaders = { 'content-type': 'application/json', ...(headers || {}) }
  if (config.responseType === 'blob' && code === CODE.SUCCESS) {
    responseData = data
  } else if (config.responseType === 'blob') {
    // 文件流请求遇到业务错误：返回 JSON Blob，由 request.js 的 blob 分支解析后统一报错
    responseData = new Blob([JSON.stringify(body)], { type: 'application/json' })
  } else {
    responseData = body
  }
  return { data: responseData, status, statusText: codeMessage(code), headers: responseHeaders, config, request: {} }
}

/** 创建 Mock 适配器（可用 options.delay=false 关闭延迟，供自动化校验脚本使用） */
export function createMockAdapter(options = {}) {
  const delayRange = options.delay === false ? [0, 0] : [DELAY_MIN, DELAY_MAX]
  const onRequest = typeof options.onRequest === 'function' ? options.onRequest : null

  return async function mockAdapter(config) {
    if (onRequest) onRequest(config)

    const method = String(config.method || 'get').toLowerCase()
    const path = normalizePath(config)
    const matched = compiledRoutes.find((route) => route.method === method && route.regex.test(path))

    if (!matched) {
      const response = buildResponse(config, {
        code: CODE.NOT_FOUND,
        message: `接口未实现(演示)：${method.toUpperCase()} ${path}`
      })
      await sleep(delayRange[0])
      return Promise.reject(buildHttpError(response))
    }

    const hit = matched.regex.exec(path)
    const pathParams = {}
    matched.keys.forEach((key, index) => {
      pathParams[key] = decodeURIComponent(hit[index + 1])
    })

    let result
    if (matched.auth === false) {
      result = await matched.handler({
        db,
        params: config.params || {},
        body: normalizeBody(config),
        pathParams,
        user: null
      })
    } else {
      const { employee, error } = resolveUser(config)
      if (error) {
        const response = buildResponse(config, { code: error })
        await sleep(delayRange[0])
        return Promise.reject(buildHttpError(response))
      }
      if (matched.roles && !matched.roles.includes(employee.role)) {
        const response = buildResponse(config, { code: CODE.FORBIDDEN })
        await sleep(delayRange[0])
        return Promise.reject(buildHttpError(response))
      }
      result = await matched.handler({
        db,
        params: config.params || {},
        body: normalizeBody(config),
        pathParams,
        user: employee
      })
    }

    await sleep(delayRange[0] + Math.random() * (delayRange[1] - delayRange[0]))
    const response = buildResponse(config, result)
    return response.status >= 400 ? Promise.reject(buildHttpError(response)) : response
  }
}
