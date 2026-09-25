import { ROLE_LABEL } from '@kdyzgl/shared/constants/role.js'

/**
 * 管理端账号相关常量（非演示资产）· 驿站精灵
 * 演示账号清单与密码已放到 src/demo/accounts.js（仅 Mock 态加载，生产构建剔除），此处只留业务常量。
 * 端固定化：本端只服务 ADMIN，故 HOME_BY_ROLE 只登记管理端首页。
 */

/** 角色 → 登录后首页（管理端） */
export const HOME_BY_ROLE = {
  ADMIN: '/boss/home'
}

/** 角色 → 中文名（与 PC 端口径一致，直接复用角色常量表） */
export const roleLabel = (role) => ROLE_LABEL[role] || role || '-'
