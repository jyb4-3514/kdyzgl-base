import {
  createRandom,
  randomInt,
  randomWeighted,
  pickOne,
  formatDate,
  formatDateTime,
  maskName,
  maskPhone,
  parseTime
} from './util.js'
import { db, stationName } from './db.js'
import { overlay } from './overlay.js'
import { DEMO_CODE } from '../constants/errorCode.js'

/**
 * 包裹索引层（T05）
 *
 * 为什么用 TypedArray 索引 + 按需水合而不实例化 20 万个对象：20 万完整对象含字符串约 40-60 MB，
 * 且每次刷新重建会产生可感知的启动卡顿；索引层只存 3 个 TypedArray（stationId Int8 + status Int8 + inboundTs Int32）
 * 约 1.2 MB，查询时只把命中页的 20 条水合成完整对象，内存恒定、不随数据量增长（demo-design.md 7.5.1）。
 * 运单号由 id 反解（`SF` + 10 位序号），故不需要第四个 waybillSeq 数组，反查运单号可 O(1) 定位。
 *
 * 时间基准：`inboundTs` 存「相对今天 0 点的秒偏移」（负=过去，正=今天），趋势/近 7 天筛选都以它为口径，
 * 同一天内多次刷新结果稳定；跨天时数据仍相对新的一天分布，符合「近 N 天」语义。
 *
 * 已知性能边界（Y2）：全量聚合与筛选都按行调 effectiveStatus / pickupTs，两者各含一次 overlay.get，
 * 覆盖层变大即线性劣化（实测全站 summary median：0 条 6.88ms → 1 万条 21.93ms → 5 万条 58.91ms）。
 * 上限评估与建议方案登记在 overlay.js 头部，本轮不改聚合实现。
 */

const STATION_WEIGHTS = [18, 14, 12, 12, 12, 12, 10] // 7 个启用驿站权重，城东最忙便于演示（8 号停用不投包裹）
const STATUS_WEIGHTS = [1, 30, 60, 6, 3] // 历史包裹状态权重：待入库/在库待取/已取件/异常/已退回
const RECENT_HOURS = 72 // 近 72h 入库的包裹视为未到取件窗口，只生成待入库/在库待取，避免「今天入库却已取件」的时序矛盾

const RECEIVER_SURNAMES = '赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜'
const RECEIVER_GIVEN = [
  '伟',
  '芳',
  '娜',
  '敏',
  '静',
  '丽',
  '强',
  '磊',
  '军',
  '洋',
  '勇',
  '艳',
  '杰',
  '娟',
  '涛',
  '明',
  '超',
  '霞',
  '平',
  '刚',
  '文',
  '辉',
  '建华',
  '雪',
  '婷',
  '波',
  '斌',
  '宇',
  '晨',
  '阳',
  '琳',
  '楠',
  '鑫',
  '倩',
  '浩然',
  '思远',
  '子涵'
]
const PHONE_PREFIX = [
  '130',
  '131',
  '132',
  '133',
  '134',
  '135',
  '136',
  '137',
  '138',
  '139',
  '150',
  '151',
  '152',
  '157',
  '158',
  '159',
  '180',
  '181',
  '182',
  '183',
  '184',
  '187',
  '188',
  '189'
]

let built = false
let count = 0
let baseTs = 0 // 今天 0 点时间戳（毫秒）
let stationIdArr = null // Int8Array
let statusArr = null // Int8Array
let inboundTs = null // Int32Array
let orderArr = null // Int32Array：入库时间倒序的下标序列，查询用，见 ensureOrder()

/** 单次查询耗时观测点：供 verify-mock.mjs 读取并回写 README（demo-design.md 7.5.1 要求实测） */
export const parcelPerf = { lastQueryMs: 0 }

function envCount() {
  // VITE_MOCK_PARCEL_COUNT 可配置（demo-design.md 7.5.1）；Node 校验脚本无 Vite 环境，回退默认 20 万
  const fromEnv = Number(import.meta.env?.VITE_MOCK_PARCEL_COUNT)
  return Number.isFinite(fromEnv) && fromEnv > 0 ? Math.floor(fromEnv) : 200000
}

/**
 * 入库时间倒序索引（Y1 修复）
 *
 * 为什么需要：查询口径固定按入库时间倒序取页，而排序键与命中集合无关。
 * 旧实现每次查询都「收集命中下标 → 全量 sort」，无筛选时可命中全部 20 万条，
 * 实测 median 103ms（设计目标 10–16ms），是唯一的路径级性能缺陷。
 * 排序只做一次后，每次查询退化为「顺序遍历 + 过滤 + 按位次取页」，不再有 O(n log n)。
 *
 * 比较器显式带下标次序作 tie-break：与旧实现 Array.sort 的稳定序完全一致（同时间按下标升序），
 * 不依赖引擎稳定性，保证分页切片与历史结果逐条相同。
 * 代价：额外一个 20 万 Int32Array（约 0.76MB，索引层 1.14MB → 约 1.9MB）。
 */
function ensureOrder() {
  if (orderArr) return orderArr
  const order = new Int32Array(count)
  for (let i = 0; i < count; i += 1) order[i] = i
  order.sort((a, b) => inboundTs[b] - inboundTs[a] || a - b)
  orderArr = order
  return orderArr
}

function ensureBuilt() {
  if (built) return
  count = envCount()
  const random = createRandom(0x5f3759df) // 固定种子，与 db.js 的 20260917 区分开，避免互相干扰
  stationIdArr = new Int8Array(count)
  statusArr = new Int8Array(count)
  inboundTs = new Int32Array(count)

  const d = new Date()
  d.setHours(0, 0, 0, 0)
  baseTs = d.getTime()
  const recentCut = RECENT_HOURS * 3600

  for (let i = 0; i < count; i += 1) {
    stationIdArr[i] = randomWeighted(random, STATION_WEIGHTS) + 1
    // 6% 落在今天，其余均匀分布在过去 29 天（至少 1 小时前），保证趋势首日有数据
    const offset = random() < 0.06 ? randomInt(random, 0, 18 * 3600) : -randomInt(random, 3600, 29 * 86400)
    inboundTs[i] = offset
    statusArr[i] = offset > -recentCut ? (random() < 0.1 ? 0 : 1) : randomWeighted(random, STATUS_WEIGHTS)
  }
  built = true
}

/** 秒偏移 → 绝对毫秒时间戳 */
function absoluteTime(offsetSeconds) {
  return baseTs + offsetSeconds * 1000
}

/** 已取件包裹的取件耗时（秒）：入库后 2~72 小时，乘法散列保证确定性与分布均匀，不占用额外 TypedArray */
function pickupDelaySeconds(i) {
  return 2 * 3600 + ((i * 2654435761) % (70 * 3600))
}

/**
 * 今日取件比例（千分之几）：让「今日取件」与历史日均取件同量级。
 * 为什么必须补：种子里取件时间 = 入库时间 + 2~72h，而「已取件」的入库时间一律早于 72h 前，
 * 算出来的取件时间必然早于今天 0 点 —— 首页「今日取件」恒为 0、趋势末点恒为 0，
 * 管理端还会显示「↓100.0%」，演示时看起来像功能坏了。
 */
const TODAY_PICKUP_PER_MILLE = 26

/** 是否为「今日取件」样本：乘法散列取模，同一天内多次刷新结果稳定 */
const isTodayPickup = (i) => (i * 2654435761) % 1000 < TODAY_PICKUP_PER_MILLE

/**
 * 已取件包裹的取件时间戳（毫秒）：覆盖层（演示中真实取件）优先，其次按种子派生；未取件返回 null。
 * 抽成同一函数是为了让 summary 与 trend 口径一致 —— 原先 summary 只认覆盖层、trend 认派生时间，
 * 会出现「趋势里有今日取件、指标卡里是 0」的自相矛盾。
 * @param {number} i 包裹下标
 * @param {number} [status] 调用方已算出的有效状态，传入可省一次覆盖层查询
 */
function pickupTs(i, status) {
  const o = overlay.get(i + 1)
  if (o && o.pickup_time) return parseTime(o.pickup_time)
  const st = status == null ? effectiveStatus(i) : status
  if (st !== 2) return null
  if (isTodayPickup(i)) {
    // 落在今天 0 点至当前时刻之间；下限留 1 分钟，避免刚过零点时全部压在同一时刻
    const elapsed = Math.max(60000, Date.now() - baseTs)
    return baseTs + ((i * 40503) % elapsed)
  }
  return absoluteTime(inboundTs[i]) + pickupDelaySeconds(i) * 1000
}

/** 覆盖层优先的有效状态（取件后 status 变化须体现在筛选与聚合口径里） */
function effectiveStatus(i) {
  const o = overlay.get(i + 1)
  return o && 'status' in o ? o.status : statusArr[i]
}

function shelfCode(i) {
  const zone = String((i % 12) + 1).padStart(2, '0')
  const rack = String((Math.floor(i / 12) % 40) + 1).padStart(2, '0')
  return `A${zone}-${rack}-${(i % 5) + 1}`
}

/** 下标 → 完整包裹 VO（收件人/手机号按 index 派生，与查询顺序无关，保证任意入口查到同一条数据一致） */
function hydrate(i) {
  const id = i + 1
  const o = overlay.get(id)
  const status = o && 'status' in o ? o.status : statusArr[i]
  const random = createRandom(0x9e3779b1 ^ id)
  const inboundTime = formatDateTime(new Date(absoluteTime(inboundTs[i])))
  // 已取件的取件时间由 pickupTs 统一给出（覆盖层优先 + 种子派生），未取件为 null
  const pickupTime = status === 2 ? formatDateTime(new Date(pickupTs(i, status))) : null

  return {
    id,
    stationId: stationIdArr[i],
    stationName: stationName(stationIdArr[i]),
    waybillNo: `SF${1000000000 + id}`,
    status,
    receiverName: maskName(`${pickOne(random, RECEIVER_SURNAMES.split(''))}${pickOne(random, RECEIVER_GIVEN)}`),
    receiverPhone: maskPhone(
      `${pickOne(random, PHONE_PREFIX)}${String(randomInt(random, 0, 99999999)).padStart(8, '0')}`
    ),
    shelfCode: shelfCode(i),
    inboundTime,
    pickupEmployeeId: o && o.pickup_employee_id != null ? o.pickup_employee_id : null,
    pickupTime,
    syncBatchNo: null,
    remark: status === 3 ? '演示异常件' : null,
    createTime: inboundTime,
    updateTime: pickupTime || inboundTime
  }
}

/** 组合筛选 + Top-K 分页（排序固定入库时间倒序，与驿站实际高频查询口径一致） */
export function queryParcels({ stationId, status, waybillNo, startTime, endTime, pageNum, pageSize }) {
  ensureBuilt()
  const t0 = performance.now()
  const num = Math.max(1, Number.parseInt(pageNum, 10) || 1)
  const size = Math.min(100, Math.max(1, Number.parseInt(pageSize, 10) || 10))
  const empty = { total: 0, pageNum: num, pageSize: size, list: [] }
  const finish = () => {
    parcelPerf.lastQueryMs = performance.now() - t0
    return empty
  }

  // 运单号精确搜索：运单号由 id 派生，可直接反解为下标，避免 20 万行扫描
  if (waybillNo) {
    const matched = /^SF(\d{10})$/.exec(String(waybillNo).trim())
    if (!matched) return finish()
    const id = Number(matched[1]) - 1000000000
    const i = id - 1
    if (i < 0 || i >= count) return finish()
    const vo = hydrate(i)
    const wantStation = stationId == null ? null : Number(stationId)
    const wantStatus = status == null || status === '' ? null : Number(status)
    const startTs = startTime ? parseTime(startTime) : null
    const endTs = endTime ? parseTime(endTime) : null
    const at = absoluteTime(inboundTs[i])
    const hit =
      (wantStation == null || stationIdArr[i] === wantStation) &&
      (wantStatus == null || vo.status === wantStatus) &&
      (startTs == null || at >= startTs) &&
      (endTs == null || at <= endTs)
    parcelPerf.lastQueryMs = performance.now() - t0
    return { total: hit ? 1 : 0, pageNum: num, pageSize: size, list: hit ? [vo] : [] }
  }

  const wantStation = stationId == null ? null : Number(stationId)
  const wantStatus = status == null || status === '' ? null : Number(status)
  const startTs = startTime ? parseTime(startTime) : null
  const endTs = endTime ? parseTime(endTime) : null

  const pass = (i) => {
    if (wantStation != null && stationIdArr[i] !== wantStation) return false
    if (wantStatus != null && effectiveStatus(i) !== wantStatus) return false
    const at = absoluteTime(inboundTs[i])
    if (startTs != null && at < startTs) return false
    if (endTs != null && at > endTs) return false
    return true
  }

  // 单遍扫描：既统计命中总数，又按已排好的倒序位次直接取当前页，不再收集命中下标 + 全量 sort（Y1）
  const order = ensureOrder()
  const start = (num - 1) * size
  let total = 0
  const list = []
  for (let j = 0; j < count; j += 1) {
    const i = order[j]
    if (!pass(i)) continue
    // total 是「已通过的条数」，等于该条在结果集中的位次
    if (total >= start && list.length < size) list.push(hydrate(i))
    total += 1
  }

  parcelPerf.lastQueryMs = performance.now() - t0
  return { total, pageNum: num, pageSize: size, list }
}

export function getParcelById(id) {
  ensureBuilt()
  const i = Number(id) - 1
  return i < 0 || i >= count ? null : hydrate(i)
}

/**
 * 取件核销写操作：返回 { code, vo }，业务码由路由层直接透传。
 * 7003 是并发防御分支：单会话下同一人取件走 7002，只有「他人已取」的场景才会命中（verify 用两账号复现）。
 */
export function pickupParcel(id, employeeId) {
  ensureBuilt()
  const i = Number(id) - 1
  if (i < 0 || i >= count) return { code: DEMO_CODE.PARCEL_NOT_EXISTS }
  const status = effectiveStatus(i)
  if (status !== 1) {
    if (status === 2) {
      const o = overlay.get(i + 1)
      if (o && o.pickup_employee_id != null && Number(o.pickup_employee_id) !== Number(employeeId)) {
        return { code: DEMO_CODE.PARCEL_PICKED_BY_OTHER }
      }
    }
    return { code: DEMO_CODE.PARCEL_STATUS_INVALID }
  }
  overlay.set(i + 1, { status: 2, pickup_employee_id: Number(employeeId), pickup_time: formatDateTime(new Date()) })
  return { code: 200, vo: hydrate(i) }
}

/** 包裹看板指标（demo-design.md 7.4.7）：todayPickup 按取件时间落在今日统计，种子与演示实时取件同一口径 */
export function parcelSummary(stationId) {
  ensureBuilt()
  let parcelTotal = 0
  let todayInbound = 0
  let todayPickup = 0
  let pendingPickup = 0
  let abnormalCount = 0

  for (let i = 0; i < count; i += 1) {
    if (stationId != null && stationIdArr[i] !== stationId) continue
    parcelTotal += 1
    const st = effectiveStatus(i)
    if (inboundTs[i] >= 0) todayInbound += 1
    if (st === 1) pendingPickup += 1
    if (st === 3) abnormalCount += 1
    if (st === 2 && pickupTs(i, st) >= baseTs) todayPickup += 1
  }
  return {
    parcelTotal,
    todayInbound,
    todayPickup,
    pendingPickup,
    abnormalCount,
    pickupRate: todayInbound === 0 ? 0 : Number((todayPickup / todayInbound).toFixed(4))
  }
}

/** 趋势：近 N 天按天聚合入库与取件数（取件时间与 summary 同口径：覆盖层优先 + 种子派生） */
export function parcelTrend(stationId, days) {
  ensureBuilt()
  const n = Math.min(30, Math.max(1, Number(days) || 7))
  const dayMs = 86400000
  const windowStart = baseTs - (n - 1) * dayMs
  const buckets = []
  for (let d = 0; d < n; d += 1) {
    buckets.push({ date: formatDate(new Date(windowStart + d * dayMs)), inbound: 0, pickup: 0 })
  }

  for (let i = 0; i < count; i += 1) {
    if (stationId != null && stationIdArr[i] !== stationId) continue
    const idx = Math.floor((absoluteTime(inboundTs[i]) - windowStart) / dayMs)
    if (idx >= 0 && idx < n) buckets[idx].inbound += 1
    const st = effectiveStatus(i)
    if (st === 2) {
      const pIdx = Math.floor((pickupTs(i, st) - windowStart) / dayMs)
      if (pIdx >= 0 && pIdx < n) buckets[pIdx].pickup += 1
    }
  }
  return buckets
}

/** 驿站排行：按包裹量降序（含停用的 8 号站，其包裹数为 0），排序切换由路由层按 pickupRate/abnormalRate 重排 */
export function parcelRanking() {
  ensureBuilt()
  const stats = {}
  db.stations.forEach((s) => {
    stats[s.id] = { stationId: s.id, stationName: s.station_name, parcelTotal: 0, picked: 0, abnormal: 0 }
  })
  for (let i = 0; i < count; i += 1) {
    const sid = stationIdArr[i]
    const st = effectiveStatus(i)
    stats[sid].parcelTotal += 1
    if (st === 2) stats[sid].picked += 1
    if (st === 3) stats[sid].abnormal += 1
  }
  return Object.values(stats)
    .map((s) => ({
      stationId: s.stationId,
      stationName: s.stationName,
      parcelTotal: s.parcelTotal,
      pickupRate: s.parcelTotal ? Number((s.picked / s.parcelTotal).toFixed(4)) : 0,
      abnormalRate: s.parcelTotal ? Number((s.abnormal / s.parcelTotal).toFixed(4)) : 0
    }))
    .sort((a, b) => b.parcelTotal - a.parcelTotal)
}

export const parcelTotalCount = () => {
  ensureBuilt()
  return count
}

/** 重建索引并清空覆盖层（T16「重置演示数据」复用；db.resetDb 不反向依赖本模块，由调用方一并调用） */
export function resetParcelStore() {
  built = false
  stationIdArr = null
  statusArr = null
  inboundTs = null
  orderArr = null
  count = 0
  overlay.reset()
}
