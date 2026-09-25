import axios from 'axios'
import { formatDateTime } from './domain/time.js'

/**
 * 前端运行日志采集器（M11 D6，设计规范第 7 章）
 *
 * 为什么放 shared 层：PC / 移动 H5 / 安卓壳三端都要上报（§7.4），
 * 采集口径（白名单脱敏、环形缓冲、批量限流）必须单点，否则端与端之间会长出第二份脱敏实现。
 *
 * 三条硬约束：
 *   1. 脱敏是「白名单复制」而不是黑名单删除 —— 只复制明确允许的字段，其余全丢；
 *      并对 message / stack 里的凭据片段做二次擦除（白名单挡不住写在错误文案里的 ?token=xxx）。
 *   2. 环形缓冲 200 条，超出 FIFO 丢最旧。
 *   3. 上报失败一律静默：不重试、不弹提示、不向上抛，直接丢弃该批（告警风暴时再雪崩才是真事故）。
 *
 * 采集范围（§7.1）：未捕获异常 / Promise 未处理 / 接口失败（含业务码失败）/ Vue 渲染异常。
 * 不采：401（登录态失效属正常流转）、请求被取消、ResizeObserver 已知无害告警、静态资源加载失败。
 * 指纹去重（同 message + route + code 10s 内累加 count）刻意只做在服务端：前端合并会把 count 压成 1，
 * 反而丢掉「同一错误反复出现 5 次」这个最有价值的信号（§9.4 有对应断言）。
 *
 * 生效环境：§7.1 未做 dev/prod 区分，且 §9.4 的验收要在演示态复现，故三端全环境生效。
 */

/** 环形缓冲上限（§7.3），超出 FIFO */
const BUFFER_MAX = 200
/** 批量上报触发：满 20 条或 30 秒，先到者（§7.3） */
const FLUSH_SIZE = 20
const FLUSH_INTERVAL_MS = 30 * 1000
/** 单条载荷 ≤8KB：message / stack 截断到 2000 字，其余丢弃（§7.3） */
const TEXT_MAX = 2000
const REPORT_TIMEOUT = 10000
/** 上报端（dict.CLIENT_LOG_SOURCE 的键集合，这里不 import 字典，避免采集器被展示层字典反向约束） */
const SOURCES = ['PC', 'H5', 'SHELL']
const LEVELS = ['INFO', 'WARN', 'ERROR']
/** 请求耗时靠请求拦截器打时间戳，键名带前缀避免与 axios 自身字段撞名 */
const START_AT = '__clientLogStartAt'
/** 浏览器已知无害告警（§7.1-3） */
const NOISE_PATTERN = /ResizeObserver loop/

const buffer = []
const state = { inited: false, flushing: false, timer: null, source: 'PC', userId: null, transport: null }

const toNumberOrNull = (value) => {
  if (value == null || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

const clip = (value, max = TEXT_MAX) => (value == null ? null : String(value).slice(0, max))

/** 敏感串二次擦除：与 Mock 侧 clientLogStore 的 scrub 同款规则，两端口径必须一致 */
const SCRUB_PATTERN = /(token=|accessToken=|refreshToken=|password=|pwd=)([^\s&#,;)]+)/gi
const scrub = (value) => (value == null ? null : String(value).replace(SCRUB_PATTERN, '$1***'))

/** path 去 query 与 hash：避免 ?token=xxx 随路径进日志（§7.2） */
const stripQuery = (url) => (url ? String(url).split('#')[0].split('?')[0] : null)

/** 当前路由：PC 是 history 模式 / 移动是 hash 模式，两种都要保留路由段、只砍 query */
function currentRoute() {
  if (typeof location === 'undefined') return null
  return clip(`${location.pathname}${location.hash}`.split('?')[0], 200)
}

/**
 * 白名单复制：只把允许的字段复制到待上报对象，其余全部丢弃
 * （含 token / password / 身份证 / 银行卡 / 请求体原文 / Cookie / localStorage 快照）。
 */
function sanitize(entry) {
  const raw = entry && typeof entry === 'object' ? entry : {}
  return {
    time: raw.time ? String(raw.time) : formatDateTime(new Date()),
    level: LEVELS.includes(raw.level) ? raw.level : 'ERROR',
    source: SOURCES.includes(raw.source) ? raw.source : state.source,
    employeeId: toNumberOrNull(raw.employeeId == null ? state.userId : raw.employeeId),
    route: clip(raw.route || currentRoute(), 200),
    message: scrub(clip(raw.message)) || '未提供错误信息',
    stack: scrub(clip(raw.stack)),
    method: raw.method ? String(raw.method).toUpperCase().slice(0, 10) : null,
    path: stripQuery(raw.path),
    status: toNumberOrNull(raw.status),
    code: toNumberOrNull(raw.code),
    duration: toNumberOrNull(raw.duration),
    ua: typeof navigator === 'undefined' ? null : clip(navigator.userAgent, 300)
  }
}

/** 级别判定：HTTP 5xx 或拿不到 HTTP 状态（网络中断）→ ERROR；4xx 与业务码 → WARN（§7.1） */
function levelOf(status, code) {
  if (typeof status === 'number') return status >= 500 ? 'ERROR' : 'WARN'
  if (typeof code === 'number' && code >= 400 && code < 600) return code >= 500 ? 'ERROR' : 'WARN'
  // 业务码（91xx / 96xx 等）是预期内的业务失败，不该按系统故障告警
  if (typeof code === 'number') return 'WARN'
  return 'ERROR'
}

const isCanceled = (error) => {
  if (!error) return false
  if (error.name === 'AbortError' || error.name === 'CanceledError') return true
  if (error.code === 'ERR_CANCELED') return true
  return typeof axios.isCancel === 'function' && axios.isCancel(error)
}

/** 入缓冲；满 20 条立即触发一次上报 */
function push(entry) {
  if (!state.inited) return
  buffer.push(sanitize(entry))
  if (buffer.length > BUFFER_MAX) buffer.splice(0, buffer.length - BUFFER_MAX)
  if (buffer.length >= FLUSH_SIZE) flush()
}

/**
 * 上报通道自建 axios 实例，刻意不复用业务实例（PC 端业务实例、移动的 utils/http）：
 * 业务实例的 401 拦截器会把一次上报失败当成「登录失效」强制登出（PC 还会跳登录页），
 * 而且它自身会采集上报请求，形成「采集→上报→失败→再采集」的递归。
 * 自建实例只借用业务实例的 Mock 适配器 —— installMock 只改 defaults.adapter，因此演示态下依然打到假后端。
 */
function createTransport(instance, getToken) {
  const client = axios.create({
    baseURL: (instance && instance.defaults.baseURL) || '/api/v1',
    timeout: REPORT_TIMEOUT
  })
  return async (logs) => {
    const token = typeof getToken === 'function' ? getToken() : ''
    // 未登录不上报：上报端点虽不限角色，但 Mock 引擎仍要求有效 token，发出去只会白拿一个 401
    if (!token) return
    if (instance && instance.defaults.adapter) client.defaults.adapter = instance.defaults.adapter
    await client.post('/system/client-logs', { logs }, { headers: { Authorization: `Bearer ${token}` } })
  }
}

/** 先摘出再发送：失败即丢弃该批（§7.3），不让失败批次卡在缓冲里反复重试 */
async function flush() {
  if (state.flushing || !buffer.length || !state.transport) return
  state.flushing = true
  const batch = buffer.splice(0, buffer.length)
  try {
    await state.transport(batch)
  } catch (e) {
    // 静默：日志上报失败不能反过来影响业务。
    // TODO(扩展): 需要排障「上报本身失败」时，可在此累计一个失败计数暴露给运行日志页
  } finally {
    state.flushing = false
  }
}

function captureWindowError(event) {
  // 资源加载失败（img/script/link）只在捕获阶段到达 window；这里再挡一次，防止将来改成捕获阶段把噪音带进来
  if (event && event.target && event.target !== window) return
  push({ level: 'ERROR', message: event && event.message, stack: event && event.error && event.error.stack })
}

function captureRejection(event) {
  const reason = event && event.reason
  if (isCanceled(reason)) return
  const message = (reason && reason.message) || String(reason)
  if (NOISE_PATTERN.test(message)) return
  push({ level: 'ERROR', message, stack: reason && reason.stack })
}

/** 接口失败：业务实例的响应拦截器已把 body.code 分发归一，这里拿到的就是带 code 的 Error */
function captureApiError(error) {
  if (!error || isCanceled(error)) return
  const response = error.response
  const status = response ? response.status : null
  const code = toNumberOrNull(error.code)
  if (code === 401 || status === 401) return
  const message = error.message || '接口请求失败'
  if (NOISE_PATTERN.test(message)) return
  const config = error.config || {}
  push({
    level: levelOf(status, code),
    message,
    stack: error.stack,
    method: config.method,
    path: config.url,
    status,
    code,
    duration: config[START_AT] ? Date.now() - config[START_AT] : null
  })
}

function attachAxios(instance) {
  // 请求侧只打时间戳，拿接口耗时；放在业务实例既有的 token 拦截器之后，不改变其行为
  instance.interceptors.request.use((config) => {
    config[START_AT] = Date.now()
    return config
  })
  instance.interceptors.response.use(undefined, (error) => {
    captureApiError(error)
    return Promise.reject(error)
  })
}

/** Vue 渲染/生命周期异常：链式保留既有 errorHandler，不抢占业务已有的兜底处理 */
function attachVue(app) {
  const prev = app.config.errorHandler
  app.config.errorHandler = (err, instance, info) => {
    const message = (err && err.message) || String(err)
    push({ level: 'ERROR', message: `[Vue ${info || 'error'}] ${message}`, stack: err && err.stack })
    if (typeof prev === 'function') prev(err, instance, info)
  }
}

/**
 * 初始化采集器（三端各调一次，重复调用直接忽略）
 * @param {string} source 上报端：PC / H5 / SHELL
 * @param {object} instance 业务 axios 实例（借其 Mock 适配器）
 * @param {Function} getToken 取当前 token，未登录时不上报
 * @param {Function} getUserId 取当前登录人 id，用于运行日志按员工筛选
 * @param {object} app Vue 应用实例，传入则同时接管渲染异常
 */
export function initClientLog({ source = 'PC', instance = null, getToken, getUserId, app = null } = {}) {
  if (state.inited || typeof window === 'undefined') return
  state.inited = true
  state.source = SOURCES.includes(source) ? source : 'PC'
  state.userId = typeof getUserId === 'function' ? getUserId() : null
  state.transport = createTransport(instance, getToken)

  window.addEventListener('error', captureWindowError)
  window.addEventListener('unhandledrejection', captureRejection)
  if (instance && instance.interceptors) attachAxios(instance)
  if (app && app.config) attachVue(app)
  state.timer = setInterval(flush, FLUSH_INTERVAL_MS)
}

/** 手动冲刷（供演示「制造一次异常后立刻查看」用：不必等 30 秒窗口） */
export function flushClientLogs() {
  return flush()
}
