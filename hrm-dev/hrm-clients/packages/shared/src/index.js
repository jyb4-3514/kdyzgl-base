/**
 * @kdyzgl/shared 公共出口
 *
 * 为什么建 barrel：各端与其它共享包统一按包名/子路径消费同一份纯函数与常量，
 * 避免相对路径穿透到 `src/` 之下（白名单式出口，便于后续按需裁剪与静态分析）。
 * 全部用 `export *`：命名冲突按 ES 规范被排除而非抛错，不引入运行时风险。
 */
export * from './domain/index.js'
export * from './constants/dict.js'
export * from './constants/errorCode.js'
export * from './constants/role.js'
export * from './constants/storageKey.js'
export * from './device.js'
export { initClientLog, flushClientLogs } from './clientLog.js'
