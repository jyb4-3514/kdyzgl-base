/**
 * @kdyzgl/api-client 公共出口
 *
 * 硬边界（ADR §3.5 共享包硬边界）：本包只依赖 `axios`（peer）与 `@kdyzgl/shared`，
 * 禁依赖 Element Plus / Vant / 任何端源码 —— 端相关载体一律由调用方注入。
 */
export {
  createHttp,
  UNAUTHORIZED_CODE,
  SESSION_EXPIRED_CODE,
  READ_TIMEOUT,
  WRITE_TIMEOUT
} from './createHttp.js'
export { createAuthStorage } from './authStorage.js'
