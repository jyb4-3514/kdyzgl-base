/**
 * 演示数据持久化桶（overlay.js 的通用化版本）
 *
 * 为什么需要：KPI 评分 / 工资单 / 人事与入离职流程都是「写一次、三端都要看到」的数据，
 * 只活在内存里的话，管理端审批发布的工资单，员工端刷新就消失（演示会当场穿帮）。
 * 为什么整表快照而不是 parcel 的逐条 patch：本模块实体量级在千条内（KPI 评分约 660、工资单约 112），
 * 快照 JSON 两百 KB 上下，实现最简且天然自洽；包裹 20 万条才必须走 patch 覆盖层（见 overlay.js）。
 * 为什么兼容无 localStorage：verify-mock.mjs 在 Node 下运行没有 window，回退为进程内内存态，
 * 保证校验脚本在同一进程内可复现。
 *
 * 约定：read() 返回 null 表示「无快照」，由调用方按种子重建；种子本身不落盘，只在首次写操作时固化整表。
 */

const PREFIX = 'hrm_demo_'
const VERSION = '_v1'

export function createPersistBucket(name) {
  const key = `${PREFIX}${name}${VERSION}`
  let cache = null
  let loaded = false

  const storage = () => (typeof localStorage === 'undefined' ? null : localStorage)

  return {
    read() {
      if (loaded) return cache
      const store = storage()
      if (store) {
        try {
          const raw = store.getItem(key)
          cache = raw ? JSON.parse(raw) : null
        } catch (e) {
          // 脏快照按「无快照」处理：一条坏数据不该把整个模块挡在门外
          cache = null
        }
      }
      loaded = true
      return cache
    },
    /** 先落内存再落盘；落盘失败降级为仅内存（超配额场景，与 overlay.js 同口径） */
    write(snapshot) {
      cache = snapshot
      loaded = true
      const store = storage()
      if (!store) return
      try {
        store.setItem(key, JSON.stringify(snapshot))
      } catch (e) {
        /* 忽略：内存态已更新，本标签内数据仍然一致 */
      }
    },
    /** 清空快照，下次查询回到种子态（T16「重置演示数据」复用） */
    clear() {
      cache = null
      loaded = true
      const store = storage()
      if (!store) return
      try {
        store.removeItem(key)
      } catch (e) {
        /* 忽略清理失败，内存态已重置 */
      }
    }
  }
}
