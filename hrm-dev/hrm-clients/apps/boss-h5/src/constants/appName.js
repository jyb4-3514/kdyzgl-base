/**
 * 应用名真源（唯一）· 管理端「驿站精灵」
 *
 * 为什么单独成文件：登录页与 App.vue 都要解析应用名，两处各写一份判断必然随迭代漂移。
 * 约束：本文件必须零依赖（constants/ 禁止 import api / stores，见 ESLint L3），只做纯函数。
 * 端固定化（ADR §3.5 第 11 项）：本端品牌恒定「驿站精灵」，不出现另一端品牌名。
 */
export const APP_NAME_BOSS = '驿站精灵'
/** 系统级名称：既非登录后身份、也非本端入口取值时的中性回落（不越界透出任何端品牌） */
export const APP_NAME_SYSTEM = '快递驿站智汇系统'

// TODO(扩展): 后续由 vite define 注入 package.json version，消除硬编码
export const APP_VERSION = 'v1.0.0'

/** 本端入口标识（兼容读）：旧书签 / 旧壳带 ?as=boss */
const BOSS_AS = new Set(['boss'])

/**
 * 解析当前应展示的应用名
 *
 * 判据优先级：登录后 role > 登录前 as（本端取值）> 系统名。
 * role 存在即本端身份（本端不存在员工端角色），一律返回「驿站精灵」；
 * as 命中 {boss} 亦为本端入口；其余（含缺失 / 异端取值）回落系统名，不泄漏另一端品牌。
 *
 * @param {{ as?: string, role?: string }} [ctx]
 * @returns {string} 应用名
 */
export function resolveAppName({ as, role } = {}) {
  if (role) return APP_NAME_BOSS
  if (BOSS_AS.has(as)) return APP_NAME_BOSS
  return APP_NAME_SYSTEM
}
