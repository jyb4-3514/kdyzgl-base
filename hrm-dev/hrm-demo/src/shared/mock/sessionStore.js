import { createPersistBucket } from './persist.js'

/**
 * 单会话表（employeeId → jti）的持久化实现
 *
 * 为什么必须落盘：一期设计里 token / user 本就持久化在 localStorage（「刷新页面不丢失」），
 * 而会话只在内存里会与之直接矛盾 —— 整页刷新后首个鉴权请求即 401，
 * 应用只能清登录态把人踢回登录页（e2e A2-5 / A3-6 探针）。
 *
 * 为什么用 Map 子类而不是散装函数：db.sessions 的既有写点分散在 auth / employee 三处路由，
 * 子类化后「写入即落盘」，既不必要求每个业务写点都记得保存，也不会漏掉将来新增的写点。
 * 语义约定：clear() 同时清内存与快照（重置演示数据必须让旧 token 立即失效）；
 * 模块加载时不调用 clear()，否则刷新即丢会话，持久化形同虚设。
 */
const bucket = createPersistBucket('sessions')

export class PersistedSessionMap extends Map {
  constructor() {
    super()
    const snapshot = bucket.read()
    if (snapshot && typeof snapshot === 'object') {
      // JSON 的键一律是字符串，还原成数字员工 id，保证 findEmployeeById 的比对口径不变
      Object.entries(snapshot).forEach(([id, jti]) => super.set(Number(id), jti))
    }
  }

  set(employeeId, jti) {
    super.set(employeeId, jti)
    this.#persist()
    return this
  }

  delete(employeeId) {
    const removed = super.delete(employeeId)
    if (removed) this.#persist()
    return removed
  }

  clear() {
    super.clear()
    bucket.clear()
  }

  #persist() {
    bucket.write(Object.fromEntries(this))
  }
}

/**
 * 会话元数据（employeeId → { deviceId, expireAt }）的持久化实现
 *
 * 为什么与 sessions 分开成两张表：sessions 的语义是「employeeId → jti」并被 interceptor 与 business 分支
 * 逐处比对（互踢判定），往值里塞对象会打破既有比对口径（`db.sessions.get(id) !== jti`）。
 * 元数据只服务登录体系改造新增的「3 天到期」判定，独立成表可让既有逻辑零改动。
 * 持久化理由同 sessions：会话落盘而元数据不落盘，刷新后「到期时间」丢失会让 3 天策略形同虚设。
 */
const metaBucket = createPersistBucket('sessionMeta')

export class PersistedSessionMetaMap extends Map {
  constructor() {
    super()
    const snapshot = metaBucket.read()
    if (snapshot && typeof snapshot === 'object') {
      Object.entries(snapshot).forEach(([id, meta]) => super.set(Number(id), meta))
    }
  }

  set(employeeId, meta) {
    super.set(employeeId, meta)
    this.#persist()
    return this
  }

  delete(employeeId) {
    const removed = super.delete(employeeId)
    if (removed) this.#persist()
    return removed
  }

  clear() {
    super.clear()
    metaBucket.clear()
  }

  #persist() {
    metaBucket.write(Object.fromEntries(this))
  }
}
