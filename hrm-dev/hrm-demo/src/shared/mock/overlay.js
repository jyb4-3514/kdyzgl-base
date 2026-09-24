/**
 * 包裹写操作覆盖层
 *
 * 为什么独立成模块：20 万包裹明细不落 localStorage（体积与每次刷新重建成本不可接受），
 * 只有取件/异常等写操作产生的小量覆盖数据（预估千条级）需要持久化，查询时叠加到水合结果上。
 * 为什么兼容无 localStorage 环境：verify-mock.mjs 在 Node 下运行没有 window，回退为进程内 Map，
 * 保证「同一标签/进程内刷新后数据不变」的验收不受运行环境影响。
 *
 * 已知性能边界（Y2 实测）：覆盖层没有任何容量约束，而全量聚合（parcelSummary / parcelTrend /
 * parcelRanking）是按行读它的 —— 每行 1~2 次 overlay.get，Map 变大后命中成本随之上升。
 * 全站 summary median 实测：0 条 6.88ms → 1 千 13.60ms → 5 千 15.07ms → 1 万 21.93ms → 5 万 58.91ms，
 * 即每增加 1 万条约 +1.5ms，线性劣化；演示时长拉长后管理端看板会从「瞬时」变成「可感知卡顿」。
 * TODO(扩展): 覆盖层规模接近万级时二选一 —— ① 给覆盖层设容量上限（超出按时间淘汰）；
 * ② 拆出「覆盖层变更计数 + 仅对变更行重算」的增量聚合，或按 status/驿站分桶缓存，
 * 避免每次请求都做 20 万行 × overlay.get。当前演示数据量下不构成阻塞，故本轮只登记不改实现。
 */

const STORAGE_KEY = 'hrm_demo_parcel_overlay_v1'

/** 内存态覆盖层：Map<parcelId, patch>，写操作先改内存再同步 localStorage */
let cache = null

function load() {
  if (typeof localStorage === 'undefined') return new Map()
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return new Map()
    return new Map(Object.entries(JSON.parse(raw)))
  } catch (e) {
    return new Map()
  }
}

function persist(map) {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(Object.fromEntries(map)))
  } catch (e) {
    // 覆盖层体积超 localStorage 配额时降级为仅内存（demo-design.md 7.5.1 的兜底决策）
    // 此时跨标签一致性降级为「同一标签内一致」，不影响演示主流程
  }
}

function ensure() {
  if (cache == null) cache = load()
  return cache
}

export const overlay = {
  /** 取某包裹的覆盖 patch，无覆盖返回 null */
  get(id) {
    return ensure().get(Number(id)) || null
  },
  /** 合并写入某包裹的覆盖 patch（浅合并，已存在字段被新值覆盖） */
  set(id, patch) {
    const map = ensure()
    const next = { ...(map.get(Number(id)) || {}), ...patch }
    map.set(Number(id), next)
    persist(map)
    return next
  },
  /** 清空全部覆盖（T16「重置演示数据」复用） */
  reset() {
    cache = new Map()
    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.removeItem(STORAGE_KEY)
      } catch (e) {
        /* 忽略清理失败，内存态已重置 */
      }
    }
  }
}
