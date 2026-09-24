// S8 · 通知与日志限频：① 令牌桶按「机器人/员工」维度限频 + 发送窗口排队；② 客户端日志指纹去重 + 时间窗口聚合的服务端化。
// 运行：node s8-ratelimit.mjs
//
// 基线来源：
//   通知：hrm-demo/src/shared/mock/routes/notification.js publish（范围 ALL/STATION/EMPLOYEE，收件人取「在职且启用」，
//         一次发布即全员写入，无任何限频/排队概念）；
//   日志：clientLogStore.js（环形缓冲 200 + 指纹 message|route|code + 10s 窗口内累加 count，但「窗口查找」是对 ≤200 条线性 find）。
// 目标：把限频做成令牌桶（容量/速率/等待上限全可配），把日志去重做成 O(1) 的窗口聚合，并用固定种子仿真量化。

import { createRandom } from './lib/rng.mjs'
import { mean, nowMs, quantile, round, std } from './lib/stats.mjs'

export const RATE_LIMIT_DEFAULT_CONFIG = {
  bots: 3,
  requestsPerBot: 600,
  horizonSeconds: 200,
  bucketCapacity: 10, // 突发容量（桶深）
  refillPerSecond: 5, // 稳态速率
  maxQueueWaitSeconds: 30, // 排队等待上限，超时丢弃
  botWeight: 1 // TODO(扩展): 按机器人优先级给多桶分级（如客服群 > 通知群）
}

export const LOG_DEFAULT_CONFIG = {
  rows: 100000,
  fingerprintPool: 2000,
  dedupeWindowSeconds: 10,
  horizonSeconds: 1000,
  ringBufferCap: 200 // 基线环形缓冲上限（与 clientLogStore.MAX_LOGS 一致）
}

/* ============ ① 令牌桶 ============ */

/**
 * 单桶仿真：按时间推进，先补桶再取令牌；不足则入队，桶空则等待。
 * 复杂度 O(R log R)（事件排序）/ 队列操作为 O(R)。
 * 令牌桶正确性依据：burst ≤ capacity，长期平均速率 ≤ refillRate（RFC 2697/2698 令牌桶语义）。
 */
export function simulateBucket(botId, arrivals, cfg) {
  const events = arrivals.slice().sort((a, b) => a - b)
  let tokens = cfg.bucketCapacity
  let lastRefill = 0
  const queue = []
  const sends = []
  let dropped = 0

  const refillTo = (t) => {
    const dt = t - lastRefill
    if (dt > 0) {
      tokens = Math.min(cfg.bucketCapacity, tokens + dt * cfg.refillPerSecond)
      lastRefill = t
    }
  }
  const drain = (t) => {
    while (queue.length && tokens >= 1) {
      const req = queue.shift()
      tokens -= 1
      sends.push({ arrival: req, send: t, wait: t - req })
    }
  }

  for (const t of events) {
    refillTo(t)
    drain(t)
    if (tokens >= 1) {
      tokens -= 1
      sends.push({ arrival: t, send: t, wait: 0 })
    } else {
      queue.push(t)
    }
  }
  // 尾部排空：把剩余队列按需时间推进
  let t = lastRefill
  while (queue.length) {
    const need = (1 - tokens) / cfg.refillPerSecond
    t += Math.max(need, 1 / cfg.refillPerSecond)
    refillTo(t)
    drain(t)
    // 超等待上限的请求丢弃
    while (queue.length && t - queue[0] > cfg.maxQueueWaitSeconds) {
      queue.shift()
      dropped += 1
    }
    if (t > cfg.horizonSeconds * 10) break // 兜底防死循环
  }

  // 每秒发送峰值（限频核心指标）
  const perSecond = new Array(Math.ceil(cfg.horizonSeconds) + 1).fill(0)
  sends.forEach((s) => {
    const sec = Math.min(perSecond.length - 1, Math.floor(s.send))
    perSecond[sec] += 1
  })
  const waits = sends.map((s) => s.wait)
  return {
    botId,
    到达数: arrivals.length,
    发送数: sends.length,
    丢弃数: dropped,
    未发送数: arrivals.length - sends.length,
    峰值每秒发送: Math.max(...perSecond),
    平均等待秒: round(mean(waits), 3),
    'P95等待秒': round(quantile(waits, 0.95), 3),
    '峰值受桶约束': Math.max(...perSecond) <= cfg.bucketCapacity + cfg.refillPerSecond
  }
}

/** 对照：无任何限频（基线语义） */
export function simulateUnlimited(arrivals, cfg) {
  const perSecond = new Array(Math.ceil(cfg.horizonSeconds) + 1).fill(0)
  arrivals.forEach((t) => {
    perSecond[Math.min(perSecond.length - 1, Math.floor(t))] += 1
  })
  return { 到达数: arrivals.length, 发送数: arrivals.length, 峰值每秒发送: Math.max(...perSecond), 平均等待秒: 0, 'P95等待秒': 0 }
}

/* ============ ② 日志指纹去重 ============ */

function buildLogs(cfg, rnd) {
  const rows = []
  for (let i = 0; i < cfg.rows; i += 1) {
    // 幂律式热点：少数指纹占多数（复现报错风暴）
    const fp = Math.floor((rnd() ** 2) * cfg.fingerprintPool)
    const t = rnd() * cfg.horizonSeconds
    rows.push({ fingerprint: `FP${fp}`, time: t, message: `错误文本 ${fp}`, route: `/api/v${1 + (fp % 2)}/x`, code: 500 + (fp % 5) })
  }
  return rows.sort((a, b) => a.time - b.time)
}

/** 基线：环形缓冲 + 线性指纹查找 O(缓冲)（与 clientLogStore.pushClientLogs 同法，但服务端缓冲放大到 2000 以体现代价） */
export function aggregateBaseline(rows, cfg, findBufferCap = 2000) {
  const buffer = []
  let seq = 0
  for (const row of rows) {
    const hit = buffer.find((l) => l.fingerprint === row.fingerprint && row.time - l.lastTime <= cfg.dedupeWindowSeconds)
    if (hit) {
      hit.count += 1
      hit.lastTime = row.time
      continue
    }
    seq += 1
    buffer.push({ id: seq, fingerprint: row.fingerprint, count: 1, firstTime: row.time, lastTime: row.time })
    if (buffer.length > findBufferCap) buffer.splice(0, buffer.length - findBufferCap)
  }
  return { 聚合后条数: buffer.length, 总条数: buffer.reduce((s, l) => s + l.count, 0) }
}

/**
 * 算法化：Map 指纹 → 条目，O(1) 查找；惰性清扫过期窗口（每 sweepEvery 行一次）。
 * 复杂度均摊 O(rows)；内存上界 ≈ 窗口内独立指纹数。
 */
export function aggregateWindowed(rows, cfg, sweepEvery = 2000) {
  const map = new Map()
  let seq = 0
  let evicted = 0
  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i]
    const hit = map.get(row.fingerprint)
    if (hit && row.time - hit.lastTime <= cfg.dedupeWindowSeconds) {
      hit.count += 1
      hit.lastTime = row.time
    } else {
      if (hit) evicted += 1
      seq += 1
      map.set(row.fingerprint, { id: seq, fingerprint: row.fingerprint, count: 1, firstTime: row.time, lastTime: row.time })
    }
    if (i % sweepEvery === sweepEvery - 1) {
      for (const [k, v] of map) {
        if (row.time - v.lastTime > cfg.dedupeWindowSeconds) {
          map.delete(k)
          evicted += 1
        }
      }
    }
  }
  let total = 0
  map.forEach((v) => {
    total += v.count
  })
  return { 聚合后条数: map.size, 存活条目总量: total, 淘汰次数: evicted }
}

/* ============ 演练 ============ */

export function run() {
  const out = {}

  // ① 令牌桶
  const cfg = RATE_LIMIT_DEFAULT_CONFIG
  const rnd = createRandom(0x6c8d9e0f)
  const perBot = []
  for (let b = 1; b <= cfg.bots; b += 1) {
    const arrivals = []
    let t = 0
    const target = cfg.horizonSeconds
    const n = Math.round(cfg.requestsPerBot * 1.6) // 到达略高于速率 → 制造真实积压
    for (let i = 0; i < n; i += 1) {
      // 指数间隔（均值 = target/n），叠加周期性峰值（每 20s 一次小高峰）
      const gap = -Math.log(Math.max(1e-9, rnd())) * (target / n)
      t += gap * (Math.floor(t / 20) % 2 === 0 ? 0.4 : 2.6)
      if (t > target) break
      arrivals.push(t)
    }
    const t0 = nowMs()
    const r = simulateBucket(b, arrivals, cfg)
    const t1 = nowMs()
    const u = simulateUnlimited(arrivals, cfg)
    perBot.push({ ...r, 仿真ms: round(t1 - t0, 3), '无限制_峰值每秒': u.峰值每秒发送 })
  }
  out.令牌桶 = {
    配置: { 桶深: cfg.bucketCapacity, 速率每秒: cfg.refillPerSecond, 最大等待秒: cfg.maxQueueWaitSeconds, 机器人: cfg.bots },
    各机器人: perBot,
    汇总: {
      峰值每秒发送_最大: Math.max(...perBot.map((b) => b.峰值每秒发送)),
      '基线无限制_峰值每秒': Math.max(...perBot.map((b) => b['无限制_峰值每秒'])),
      平均等待秒: round(mean(perBot.map((b) => b.平均等待秒)), 3),
      'P95等待秒': round(quantile(perBot.map((b) => b['P95等待秒']), 0.95), 3),
      丢弃总数: perBot.reduce((s, b) => s + b.丢弃数, 0)
    },
    结论: '限频后峰值被压到桶深量级，且平均速率受速率参数约束；无限制基线峰值即瞬时到达量'
  }

  // ② 日志聚合
  const lcfg = LOG_DEFAULT_CONFIG
  const lrnd = createRandom(0x7d8e9f10)
  const logs = buildLogs(lcfg, lrnd)
  const t2 = nowMs()
  const base = aggregateBaseline(logs, lcfg)
  const t3 = nowMs()
  const win = aggregateWindowed(logs, lcfg)
  const t4 = nowMs()
  // 内存代理：单条日志序列化字节 × 条数（基线受环形缓冲上限约束，算法化受窗口存活条目约束）
  const bytesPerRaw = JSON.stringify(logs[0]).length
  const bytesPerAgg = JSON.stringify({ id: 1, fingerprint: 'FP1', count: 1, firstTime: 1.5, lastTime: 2.5 }).length
  out.日志聚合 = {
    配置: { 原始条数: lcfg.rows, 指纹池: lcfg.fingerprintPool, 窗口秒: lcfg.dedupeWindowSeconds, 基线环缓冲: lcfg.ringBufferCap },
    基线线性查找: { ...base, 耗时ms: round(t3 - t2, 3) },
    窗口Map聚合: { ...win, 耗时ms: round(t4 - t3, 3) },
    加速比: round((t3 - t2) / Math.max(0.001, t4 - t3), 2),
    降噪比: `${round((win.聚合后条数 / logs.length) * 100, 3)}%（聚合后/原始）`,
    内存代理: {
      '原始_字节': bytesPerRaw * logs.length,
      '聚合后_字节': bytesPerAgg * win.聚合后条数,
      压缩比: round((bytesPerRaw * logs.length) / Math.max(1, bytesPerAgg * win.聚合后条数), 1)
    },
    结论: '基线受「线性查找 + 固定环缓冲」双重限制：查找 O(缓冲) 且超上限即丢最旧；窗口 Map 为 O(1) 查找 + 窗口内上界内存'
  }

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
