// S7 · 包裹域：① 趋势预测（MA / Holt 线性 / Holt-Winters 加性季节）② 驿站容量与热力（IQR 离群 + 利用率预警）
//        ③ 20 万级查询性能建模（索引选择、游标分页 vs 深分页）+ 数据盘容量估算。
// 运行：node s7-parcel.mjs
//
// 基线来源：hrm-demo/src/shared/mock/parcelStore.js
//   分布权重硬编码（STATION_WEIGHTS / STATUS_WEIGHTS / TODAY_PICKUP_PER_MILLE）；趋势 = 纯按天分桶计数（无预测）；
//   排行 = 按包裹量降序；查询 = 预排倒序 + 单遍扫描（无容量预警、无异常站点识别）。
// 目标：把「预测 / 容量 / 离群」做成可解释、可配阈值的算法，并给出 20 万级耗时曲线与落盘容量估算。

import { createRandom, gaussian, randomWeighted } from './lib/rng.mjs'
import { mae, mape, mean, nowMs, quantile, round, std } from './lib/stats.mjs'

export const PARCEL_DEFAULT_CONFIG = {
  stations: 7,
  stationWeights: [18, 14, 12, 12, 12, 12, 10], // 与 Mock 一致（可配）
  trendDays: 30,
  horizonDays: 7,
  // 指数平滑参数（可配）
  alpha: 0.5,
  beta: 0.2,
  gamma: 0.4,
  seasonPeriod: 7,
  // capacity：站均货架位（件/日周转上限）
  shelfCapacity: 900,
  utilWarn: 0.8,
  utilCritical: 0.95,
  iqrK: 1.5,
  scales: [1000, 10000, 50000, 200000],
  pageSize: 20,
  deepPageNum: 1000
}

/* ============ ① 趋势预测 ============ */

/** 简单移动平均：预测 = 最近 w 天均值；O(w) */
export function forecastMA(series, w, horizon) {
  const out = []
  for (let h = 1; h <= horizon; h += 1) {
    const slice = series.slice(series.length - w + (h - 1), series.length + (h - 1))
    out.push(mean(slice))
  }
  return out
}

/** Holt 线性趋势（Holt's linear / 二次指数平滑）；O(n)；来源：FPP3 §8.2 */
export function forecastHolt(series, alpha, beta, horizon) {
  let level = series[0]
  let trend = series[1] - series[0]
  for (let t = 1; t < series.length; t += 1) {
    const prevLevel = level
    level = alpha * series[t] + (1 - alpha) * (level + trend)
    trend = beta * (level - prevLevel) + (1 - beta) * trend
  }
  return Array.from({ length: horizon }, (_, i) => level + (i + 1) * trend)
}

/** Holt-Winters 加性季节；O(n)；来源：FPP3 §8.3 / statsmodels ExponentialSmoothing(additive) */
export function forecastHoltWinters(series, alpha, beta, gamma, m, horizon) {
  const seasons = Math.floor(series.length / m)
  // 初始季节指数 = 各周期同位置均值 − 全局均值
  const overall = mean(series)
  const season = new Array(m).fill(0)
  for (let i = 0; i < seasons; i += 1) for (let j = 0; j < m; j += 1) season[j] += series[i * m + j]
  for (let j = 0; j < m; j += 1) season[j] = season[j] / seasons - overall

  let level = mean(series.slice(0, m)) - mean(season)
  let trend = (mean(series.slice(m, 2 * m)) - mean(series.slice(0, m))) / m
  for (let t = 0; t < series.length; t += 1) {
    const sIdx = t % m
    const prevLevel = level
    level = alpha * (series[t] - season[sIdx]) + (1 - alpha) * (level + trend)
    trend = beta * (level - prevLevel) + (1 - beta) * trend
    season[sIdx] = gamma * (series[t] - level) + (1 - gamma) * season[sIdx]
  }
  return Array.from({ length: horizon }, (_, i) => level + (i + 1) * trend + season[(series.length + i) % m])
}

/** 季节性朴素（对照基线）：预测 h 天前同星期值 */
export function forecastSeasonalNaive(series, m, horizon) {
  return Array.from({ length: horizon }, (_, i) => series[series.length - m + (i % m)])
}

function buildTrendSeries(cfg, rnd) {
  const seasonShape = [0, 50, 80, 30, -20, -60, -40] // 周一..周日
  const series = []
  for (let d = 0; d < cfg.trendDays; d += 1) {
    series.push(Math.max(0, Math.round(800 + d * 3 + seasonShape[d % 7] + gaussian(rnd) * 40)))
  }
  return series
}

/* ============ ② 驿站容量与热力 ============ */

/** IQR 离群：返回上下界与命中项；O(n log n) */
export function iqrOutliers(rows, valueOf, k) {
  const values = rows.map(valueOf)
  const q1 = quantile(values, 0.25)
  const q3 = quantile(values, 0.75)
  const iqr = q3 - q1
  const low = q1 - k * iqr
  const high = q3 + k * iqr
  return { q1: round(q1, 2), q3: round(q3, 2), iqr: round(iqr, 2), low: round(low, 2), high: round(high, 2), outliers: rows.filter((r) => valueOf(r) < low || valueOf(r) > high) }
}

function buildStationStats(cfg, rnd) {
  return Array.from({ length: cfg.stations }, (_, i) => {
    const inbound = Math.round(cfg.stationWeights[i] * 60 + gaussian(rnd) * 30)
    const pending = Math.round(inbound * (0.5 + rnd() * 0.45))
    const abnormal = Math.round(inbound * (rnd() * 0.08))
    return { stationId: i + 1, inbound, pending, abnormal, shelfCapacity: cfg.shelfCapacity }
  })
}

/* ============ ③ 20 万级查询性能建模 ============ */

function buildParcelIndex(N, rnd) {
  const stationId = new Int8Array(N)
  const status = new Int8Array(N)
  const inboundTs = new Int32Array(N) // 相对今天 0 点的秒偏移
  const recentCut = 72 * 3600
  const STATUS_WEIGHTS = [1, 30, 60, 6, 3]
  for (let i = 0; i < N; i += 1) {
    stationId[i] = randomWeighted(rnd, PARCEL_DEFAULT_CONFIG.stationWeights) + 1
    const offset = rnd() < 0.06 ? Math.floor(rnd() * 18 * 3600) : -Math.floor(3600 + rnd() * 29 * 86400)
    inboundTs[i] = offset
    status[i] = offset > -recentCut ? (rnd() < 0.1 ? 0 : 1) : randomWeighted(rnd, STATUS_WEIGHTS)
  }
  const order = new Int32Array(N)
  for (let i = 0; i < N; i += 1) order[i] = i
  const arr = Array.from(order)
  arr.sort((a, b) => inboundTs[b] - inboundTs[a] || a - b)
  for (let i = 0; i < N; i += 1) order[i] = arr[i]
  return { N, stationId, status, inboundTs, order }
}

/** 基线（Mock 现状）：预排倒序 + 单遍扫描 O(N)，命中位次取页 */
function queryFullScan(idx, { stationId, status, pageNum, pageSize }) {
  const start = (pageNum - 1) * pageSize
  let total = 0
  const list = []
  for (let j = 0; j < idx.N; j += 1) {
    const i = idx.order[j]
    if (stationId != null && idx.stationId[i] !== stationId) continue
    if (status != null && idx.status[i] !== status) continue
    if (total >= start && list.length < pageSize) list.push(i)
    total += 1
  }
  return { total, list }
}

/** 游标分页：按 (inboundTs,id) 二分定位起点，只取一页 O(log N + pageSize) */
function queryCursor(idx, { stationId, status, fromTs, fromId, pageSize }) {
  // order 已按 inboundTs 降序；二分找第一个 (ts,id) < (fromTs,fromId)
  let lo = 0
  let hi = idx.N
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    const i = idx.order[mid]
    const before = idx.inboundTs[i] < fromTs || (idx.inboundTs[i] === fromTs && i < fromId)
    if (before) hi = mid
    else lo = mid + 1
  }
  const list = []
  let scan = 0
  for (let j = lo; j < idx.N && list.length < pageSize; j += 1) {
    scan += 1
    const i = idx.order[j]
    if (stationId != null && idx.stationId[i] !== stationId) continue
    if (status != null && idx.status[i] !== status) continue
    list.push(i)
  }
  return { list, scanned: scan }
}

/* ============ 容量估算（行/索引字节模型） ============ */

/** InnoDB 行字节估算（utf8mb4，典型值；精确值须在服务器 SHOW TABLE STATUS 复核） */
export const ROW_MODEL = {
  字段: [
    { name: 'id', bytes: 8 },
    { name: 'station_id', bytes: 8 },
    { name: 'waybill_no', bytes: 16 }, // VARCHAR(32) 实际 14 字符 + 1 长度字节
    { name: 'status', bytes: 1 },
    { name: 'receiver_name', bytes: 10 },
    { name: 'receiver_phone', bytes: 12 },
    { name: 'shelf_code', bytes: 12 },
    { name: 'inbound_time', bytes: 5 },
    { name: 'pickup_employee_id', bytes: 8 },
    { name: 'pickup_time', bytes: 5 },
    { name: 'sync_batch_no', bytes: 12 },
    { name: 'remark', bytes: 4 },
    { name: 'create_time', bytes: 5 },
    { name: 'update_time', bytes: 5 },
    { name: 'ifnull 标志位', bytes: 1 },
    { name: 'InnoDB 行头(6)+事务id(6)+回滚指针(7)', bytes: 19 }
  ],
  索引: [
    { name: 'PRIMARY(聚簇)', bytesPerEntry: 0, 说明: '与行数据同页，不额外计' },
    { name: 'uk_station_waybill', bytesPerEntry: 8 + 16 + 8, 说明: 'station_id + waybill_no + 主键' },
    { name: 'idx_station_status', bytesPerEntry: 8 + 1 + 8, 说明: 'station_id + status + 主键' },
    { name: 'idx_inbound_time', bytesPerEntry: 5 + 8, 说明: 'inbound_time + 主键' }
  ]
}

export function estimateStorage(N) {
  const rowBytes = ROW_MODEL.字段.reduce((s, f) => s + f.bytes, 0)
  const dataBytes = rowBytes * N
  const indexBytes = ROW_MODEL.索引.reduce((s, ix) => s + ix.bytesPerEntry * N, 0)
  const btreeOverhead = 1.15 // 页填充/分裂损耗
  const total = (dataBytes + indexBytes) * btreeOverhead
  const withBinlogAndUndo = total * 2.5 // 近似：binlog 1x + undo/redo 与临时空间 0.5x
  return {
    行字节估算: rowBytes,
    数据字节: dataBytes,
    索引字节: indexBytes,
    '合计(含B+树开销)': Math.round(total),
    '合计(含 binlog/undo 近似)': Math.round(withBinlogAndUndo),
    '数据MB': round(dataBytes / 1048576, 2),
    '索引MB': round(indexBytes / 1048576, 2),
    '总计MB_保守': round(withBinlogAndUndo / 1048576, 2),
    '占46G数据盘': `${round((withBinlogAndUndo / (46 * 1024 ** 3)) * 100, 4)}%`
  }
}

/* ============ 演练 ============ */

export function run() {
  const cfg = PARCEL_DEFAULT_CONFIG
  const rnd = createRandom(0x5f3759df)
  const out = {}

  // ① 预测
  const series = buildTrendSeries(cfg, rnd)
  const train = series.slice(0, cfg.trendDays - cfg.horizonDays)
  const actual = series.slice(cfg.trendDays - cfg.horizonDays)
  const models = {
    '季节朴素(last-week)': forecastSeasonalNaive(train, cfg.seasonPeriod, cfg.horizonDays),
    'MA(7)': forecastMA(train, 7, cfg.horizonDays),
    'Holt线性': forecastHolt(train, cfg.alpha, cfg.beta, cfg.horizonDays),
    'Holt-Winters(7)': forecastHoltWinters(train, cfg.alpha, cfg.beta, cfg.gamma, cfg.seasonPeriod, cfg.horizonDays)
  }
  const t0 = nowMs()
  const table = Object.entries(models).map(([name, pred]) => ({
    模型: name,
    MAE: round(mae(actual, pred), 2),
    MAPE: `${round(mape(actual, pred) * 100, 2)}%`,
    预测均值: round(mean(pred), 1)
  }))
  const t1 = nowMs()
  out.预测对比 = {
    训练天数: train.length,
    回测天数: actual.length,
    实际均值: round(mean(actual), 1),
    实际标准差: round(std(actual), 1),
    结果: table.sort((a, b) => a.MAE - b.MAE),
    '全部模型总耗时ms': round(t1 - t0, 3)
  }

  // ② 驿站容量与热力
  const stations = buildStationStats(cfg, rnd)
  const utilOf = (s) => s.pending / s.shelfCapacity
  const outRows = stations.map((s) => ({
    stationId: s.stationId,
    inbound: s.inbound,
    pending: s.pending,
    利用率: round(utilOf(s), 4),
    预警: utilOf(s) >= cfg.utilCritical ? 'CRITICAL' : utilOf(s) >= cfg.utilWarn ? 'WARN' : 'OK'
  }))
  const inb = iqrOutliers(stations, (s) => s.inbound, cfg.iqrK)
  const util = iqrOutliers(stations, utilOf, cfg.iqrK)
  out.驿站热力容量 = {
    容量口径: { 货架位: cfg.shelfCapacity, 预警阈值: cfg.utilWarn, 严重阈值: cfg.utilCritical, IQR系数: cfg.iqrK },
    明细: outRows.sort((a, b) => b.利用率 - a.利用率),
    入库量IQR: { q1: inb.q1, q3: inb.q3, low: inb.low, high: inb.high, 离群站点: inb.outliers.map((s) => s.stationId) },
    利用率IQR: { q1: util.q1, q3: util.q3, low: util.low, high: util.high, 离群站点: util.outliers.map((s) => s.stationId) },
    预警站点: outRows.filter((r) => r.预警 !== 'OK').map((r) => ({ stationId: r.stationId, 预警: r.预警 }))
  }

  // ③ 查询性能 + 容量估算
  const perf = []
  for (const N of cfg.scales) {
    const idx = buildParcelIndex(N, createRandom(0x1a2b3c4d ^ N))
    const q = { stationId: 2, status: 1, pageNum: 1, pageSize: cfg.pageSize }
    const loop = Math.max(3, Math.round(200000 / N)) // 固定总访问量做稳定均值
    const deep = { ...q, pageNum: cfg.deepPageNum }
    // 预热：消除 JIT/冷启动对首个样本的污染（真实压测同样必须先 warmup）
    for (let w = 0; w < 3; w += 1) {
      queryFullScan(idx, q)
      queryCursor(idx, { ...q, fromTs: 0, fromId: N + 1 })
    }
    const p0 = nowMs()
    for (let i = 0; i < loop; i += 1) queryFullScan(idx, q)
    const p1 = nowMs()
    const p2 = nowMs()
    for (let i = 0; i < loop; i += 1) queryCursor(idx, { ...q, fromTs: 0, fromId: N + 1 })
    const p3 = nowMs()
    const p4 = nowMs()
    for (let i = 0; i < loop; i += 1) queryFullScan(idx, deep)
    const p5 = nowMs()
    const offset = (cfg.deepPageNum - 1) * cfg.pageSize
    perf.push({
      数据量: N,
      '基线全扫_单次ms': round((p1 - p0) / loop, 4),
      '游标分页_单次ms': round((p3 - p2) / loop, 4),
      '内存深分页_单次ms': round((p5 - p4) / loop, 4),
      // MySQL 行数模型（真实耗时须服务器复核）：索引顺序 vs OFFSET
      'MySQL模型_首页扫描行': cfg.pageSize,
      'MySQL模型_游标扫描行': Math.ceil(Math.log2(N)) + cfg.pageSize,
      'MySQL模型_深分页扫描行': offset + cfg.pageSize,
      索引深度: Math.ceil(Math.log2(N)),
      '深分页放大倍数': round((offset + cfg.pageSize) / cfg.pageSize, 1)
    })
  }
  out.查询性能 = {
    口径: '内存曲线为同机同进程相对值（模拟 Mock 索引层）；MySQL 行数模型为索引扫描行数理论值，绝对耗时须在服务器复核',
    深分页偏移行数: (cfg.deepPageNum - 1) * cfg.pageSize,
    曲线: perf,
    容量估算: estimateStorage(200000)
  }

  console.log(JSON.stringify(out, null, 2))
  return out
}

run()
