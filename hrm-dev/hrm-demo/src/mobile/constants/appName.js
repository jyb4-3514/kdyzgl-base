/**
 * 应用名真源（唯一）
 *
 * 为什么单独成文件：登录页与 App.vue 都要按「登录前 ?as 提示 / 登录后 auth.role」判端，
 * 两处各写一份判断必然随迭代漂移，故把取值与判据收敛到这里。
 * 约束：本文件必须零依赖（constants/ 禁止 import api / stores，见 ESLint L3），只做纯函数。
 */
export const APP_NAME_STAFF = '驿站助手'
export const APP_NAME_BOSS = '快递驿站智汇系统'

// TODO(扩展): 后续由 vite define 注入 package.json version，消除硬编码
export const APP_VERSION = 'v1.0.0'

/** 未登录时入口页透出的端标识（views/login/index.vue 读 route.query.as，取值见 src/demo/accounts.js） */
const STAFF_AS = new Set(['staff', 'station'])

/**
 * 解析当前应展示的应用名
 *
 * 判据优先级：登录后 role > 登录前 as。两者都无（as 缺失或非预期值）时**不预判角色**，回落老板端名 ——
 * 鉴权前无从判断访客身份，默认按「三端演示」主名展示比猜错端更稳。
 *
 * @param {{ as?: string, role?: string }} [ctx]
 * @returns {string} 应用名
 */
export function resolveAppName({ as, role } = {}) {
  if (role) return role === 'ADMIN' ? APP_NAME_BOSS : APP_NAME_STAFF
  if (STAFF_AS.has(as)) return APP_NAME_STAFF
  return APP_NAME_BOSS
}