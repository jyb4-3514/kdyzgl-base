/**
 * 角色常量（对齐 db.md 3.3：ADMIN / STATION_ADMIN / STAFF）
 * STATION_ADMIN 一期未启用，Demo 按设计方案 3.2 提前启用用于员工端站长视角
 */
export const ROLE = {
  ADMIN: 'ADMIN',
  STATION_ADMIN: 'STATION_ADMIN',
  STAFF: 'STAFF'
}

export const ROLE_LABEL = {
  ADMIN: '超级管理员',
  STATION_ADMIN: '站长',
  STAFF: '员工'
}

/** 全局管理员判定：唯一享有无驿站过滤的全量数据可见范围 */
export function isAdmin(role) {
  return role === ROLE.ADMIN
}

/**
 * 菜单可见白名单（菜单键，非路由路径）
 * TODO(扩展): T10 建 PC 布局时把这里替换为带图标/排序的完整菜单配置，
 * 并把移动端 Tabbar 可见项也收口到本文件，保证三端权限口径一致。
 */
export const MENU_WHITELIST = {
  // leave：ADMIN 全域审批 + 站长本站初审；logs：运行日志仅 ADMIN（移动端不做查看页，故只在 PC 菜单出现）
  ADMIN: [
    'dashboard',
    'employee',
    'department',
    'station',
    'parcel',
    'sync',
    'workOrder',
    'notification',
    'leave',
    'logs',
    'profile'
  ],
  STATION_ADMIN: ['parcel', 'sync', 'workOrder', 'notification', 'leave', 'profile'],
  STAFF: ['profile']
}
