import { ROLE_LABEL } from '@/shared/constants/role.js'

/**
 * 移动端账号相关常量（非演示资产）
 * 演示账号清单与密码已上移到 src/demo/accounts.js（仅 Mock 态加载，生产构建剔除），此处只留业务常量。
 */

/** 角色 → 登录后首页（老板端 / 员工端分流） */
export const HOME_BY_ROLE = {
  ADMIN: '/boss/home',
  STATION_ADMIN: '/staff/home',
  STAFF: '/staff/home'
}

/** 角色 → 中文名（与 PC 端口径一致，直接复用角色常量表） */
export const roleLabel = (role) => ROLE_LABEL[role] || role || '-'
