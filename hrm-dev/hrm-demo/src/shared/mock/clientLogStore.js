import { createPersistBucket } from './persist.js'
import { formatDateTime, paginate } from './util.js'

/**
 * 前端运行日志数据层（M11 D6，设计规范第 7 章）
 *
 * 为什么单独一个 store：运行日志是「三端上报、仅 PC 查看」的排障数据，
 * 与 db.js 的业务种子实体不是一类东西（没有业务归属、没有状态机），
 * 但同样需要跨刷新保留，故走 createPersistBucket（Node 校验环境自动降级为进程内存态）。
 *
 * 两条硬约束：
 *   1. 环形缓冲上限 200 条，超出 FIFO 丢最旧；
 *   2. 脱敏采用白名单复制（只复制明确允许的字段），不是黑名单删除 —— 黑名单在新增字段时必然漏脱敏。
 */

/** 环形缓冲上限（设计规范 §7.3） */
const MAX_LOGS = 200
/** 同一指纹（message + route + code）在该窗口内重复出现只累加 count，避免报错风暴写爆缓冲 */
const DEDUPE_WINDOW_MS = 10 * 1000
/** 单条载荷 ≤ 8KB：message / stack 超长截断，其余丢弃（§7.3） */
const TEXT_MAX = 2000
/** 允许的级别与来源：级别与 SYNC_LOG_LEVEL 同口径，来源见 dict.CLIENT_LOG_SOURCE */
const LEVELS = ['INFO', 'WARN', 'ERROR']
const SOURCES = ['PC', 'H5', 'SHELL']

const bucket = createPersistBucket('client_log')
let state = null

function ensure() {
  if (state) return state
  state = bucket.read() || { seq: 0, logs: [] }
  return state
}

/** 重建运行日志（与其余 store 的 reset 同口径，供校验脚本复用） */
export function resetClientLogStore() {
  state = null
  bucket.clear()
}

const persist = () => bucket.write(state)

const text = (value, max = TEXT_MAX) => (value == null ? null : String(value).slice(0, max))

/**
 * 敏感串二次擦除：白名单挡得住「多出来的字段」，挡不住「写进 message / stack 里的凭据片段」
 * （例如错误信息里带了 /api/v1/x?token=xxx）。只擦凭据形态，不改动其余可读内容。
 */
const SCRUB_PATTERN = /(token=|accessToken=|refreshToken=|password=|pwd=)([^\s&#,;)]+)/gi
const scrub = (value) => (value == null ? null : String(value).replace(SCRUB_PATTERN, '$1***'))

/** path 去掉 query 与 hash：避免 ?token=xxx 这类凭据随路径带进日志 */
const stripQuery = (url) => (url ? String(url).split('#')[0].split('?')[0] : null)

const toNumberOrNull = (value) => {
  if (value == null || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/**
 * 白名单复制：只把允许的字段复制到待入库对象，其余全部丢弃（含 token / password / 身份证 / 银行卡等）。
 * 请求体原文一律不记，只留 method / path（去 query）/ status / 业务 code / 耗时。
 */
function sanitizeLog(raw) {
  const item = raw && typeof raw === 'object' ? raw : {}
  return {
    time: item.time ? String(item.time) : formatDateTime(new Date()),
    level: LEVELS.includes(item.level) ? item.level : 'ERROR',
    source: SOURCES.includes(item.source) ? item.source : 'PC',
    employeeId: toNumberOrNull(item.employeeId),
    route: text(item.route, 200),
    message: scrub(text(item.message)) || '未提供错误信息',
    stack: scrub(text(item.stack)),
    method: item.method ? String(item.method).toUpperCase().slice(0, 10) : null,
    path: stripQuery(item.path),
    status: toNumberOrNull(item.status),
    code: toNumberOrNull(item.code),
    duration: toNumberOrNull(item.duration),
    ua: text(item.ua, 300)
  }
}

const fingerprintOf = (log) => `${log.message}|${log.route || ''}|${log.code == null ? '' : log.code}`

/** 出参剔除内部字段 lastTimeMs（去重窗口用的时间戳，不对外暴露） */
const toClientLogVO = (log) => {
  const vo = { ...log }
  delete vo.lastTimeMs
  return vo
}

/** 批量入库（上报端点），返回实际接收条数；指纹命中的按累加处理而不是新增一条 */
export function pushClientLogs(list) {
  ensure()
  const rows = Array.isArray(list) ? list : []
  rows.forEach((raw) => {
    const log = sanitizeLog(raw)
    const now = Date.now()
    const fingerprint = fingerprintOf(log)
    const hit = state.logs.find((l) => l.fingerprint === fingerprint && now - l.lastTimeMs <= DEDUPE_WINDOW_MS)
    if (hit) {
      hit.count += 1
      hit.lastTime = log.time
      hit.lastTimeMs = now
      return
    }
    state.seq += 1
    state.logs.push({
      id: state.seq,
      ...log,
      count: 1,
      fingerprint,
      firstTime: log.time,
      lastTime: log.time,
      lastTimeMs: now
    })
    if (state.logs.length > MAX_LOGS) state.logs.splice(0, state.logs.length - MAX_LOGS)
  })
  persist()
  return rows.length
}

const isBlankId = (value) => value == null || value === ''

/** 运行日志查询（仅 ADMIN）：筛选 + 分页 + 统计计数（计数口径 = 当前筛选结果，不含分页） */
export function queryClientLogs(filters) {
  ensure()
  let rows = state.logs
  if (filters.level) rows = rows.filter((l) => l.level === filters.level)
  if (filters.source) rows = rows.filter((l) => l.source === filters.source)
  if (!isBlankId(filters.employeeId)) rows = rows.filter((l) => l.employeeId === Number(filters.employeeId))
  if (filters.startTime) rows = rows.filter((l) => l.time >= String(filters.startTime))
  if (filters.endTime) rows = rows.filter((l) => l.time <= String(filters.endTime))
  if (filters.keyword) {
    const keyword = String(filters.keyword).toLowerCase()
    rows = rows.filter((l) =>
      String(l.message || '')
        .toLowerCase()
        .includes(keyword)
    )
  }
  const page = paginate(
    rows.slice().sort((a, b) => (a.time < b.time ? 1 : -1)),
    filters.pageNum,
    filters.pageSize
  )
  page.list = page.list.map(toClientLogVO)
  page.counts = {
    total: rows.length,
    error: rows.filter((l) => l.level === 'ERROR').length,
    warn: rows.filter((l) => l.level === 'WARN').length,
    info: rows.filter((l) => l.level === 'INFO').length,
    sourceCount: new Set(rows.map((l) => l.source)).size
  }
  return page
}

/** 清空（仅 ADMIN），返回清空条数 */
export function clearClientLogs() {
  ensure()
  const cleared = state.logs.length
  state.logs = []
  persist()
  return cleared
}
