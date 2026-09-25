import { ROLE_LABEL } from '@kdyzgl/shared/constants/role.js'

/**
 * 员工端账号相关常量（非演示资产）· 驿站助手
 * 演示账号清单与密码已上移到 src/demo/accounts.js（仅 Mock 态加载，生产构建剔除），此处只留业务常量。
 * 端固定化：本端只服务 STATION_ADMIN / STAFF，ADMIN 不入本表（端准入 fail-closed 拒登）。
 */

/** 角色 → 登录后首页（本端固定为员工工作台） */
export const HOME_BY_ROLE = {
  STATION_ADMIN: '/staff/home',
  STAFF: '/staff/home'
}

/** 角色 → 中文名（与 PC 端口径一致，直接复用角色常量表） */
export const roleLabel = (role) => ROLE_LABEL[role] || role || '-'
