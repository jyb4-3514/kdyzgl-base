// 固定种子伪随机数：与 hrm-demo/src/shared/mock/util.js 的 createRandom 逐位等价，
// 目的是让离线原型的基准数据集与 Mock 行为规格可对照（同种子同序列）。
// 依据：mulberry32 变体（公共领域算法，见 https://github.com/bryc/code/blob/master/jshash/PRNGs.md）。

/** 32 位整数种子 → [0,1) 均匀随机序列（与 Mock createRandom 同实现） */
export function createRandom(seed = 20260917) {
  let a = seed >>> 0
  return function random() {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** FNV-1a 字符串哈希：把「业务键」压成种子，保证「同键同值」可复现（与 kpiStore.seedOf 同实现） */
export function fnv1a(text) {
  let h = 2166136261
  const s = String(text)
  for (let i = 0; i < s.length; i += 1) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** [min,max] 闭区间整数 */
export const randomInt = (random, min, max) => min + Math.floor(random() * (max - min + 1))

/** 标准正态（Box-Muller），固定种子可复现 */
export function gaussian(random) {
  const u1 = Math.max(1e-12, random())
  const u2 = random()
  return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2)
}

/** 按权重取下标（weights 与结果下标一一对应） */
export function randomWeighted(random, weights) {
  const total = weights.reduce((sum, w) => sum + w, 0)
  let hit = random() * total
  for (let i = 0; i < weights.length; i += 1) {
    hit -= weights[i]
    if (hit <= 0) return i
  }
  return weights.length - 1
}
