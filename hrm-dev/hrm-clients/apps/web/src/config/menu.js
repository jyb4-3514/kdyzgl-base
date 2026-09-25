import { MENU_WHITELIST } from '@kdyzgl/shared/constants/role'

/**
 * PC 菜单配置（T10 / A3-1 分组化）
 * 为什么单独放一份：一期 layout 的菜单是硬编码的，承载不了包裹/同步/工单/通知；
 * 但「谁能看见哪个菜单」不能让 Demo 再定一套——可见性单一真源仍是 shared/constants/role.js 的
 * MENU_WHITELIST（三端共用，移动端 Tabbar 后续也收口到它），本文件只做「菜单键 → 路径/标题/图标」映射。
 *
 * A3-1：平铺 11 项在新增 4 个模块后达 16+ 项，扫读成本翻倍，故收敛为 4 组（每组 ≤6 项）。
 * MENU_ITEMS 的声明顺序即渲染顺序——buildMenus 靠「同 group 项首次出现时创建父节点」成组，
 * 因此带 group 的项必须连续排列，且组内顺序即子项顺序。
 */

/** 侧边栏分组标题（键与 MENU_ITEMS.group 对应）：面包屑取标题，图标由 MENU_GROUP_ICONS 供 */
export const MENU_GROUPS = {
  org: '组织人事',
  pay: '考勤薪酬',
  biz: '包裹作业',
  sys: '系统'
}

/** 分组图标：与分组标题一一对应，避免 buildMenus 里写死单一图标 */
export const MENU_GROUP_ICONS = {
  org: 'OfficeBuilding',
  pay: 'Money',
  biz: 'Box',
  sys: 'Setting'
}

/**
 * PC 端局部放行的菜单键（补 MENU_WHITELIST 未覆盖的项）
 * 为什么补在这里：MENU_WHITELIST 是三端共用真源，本轮不能改 shared 层，
 * 而考勤/排班对 ADMIN 与站长都要可见（STAFF 不补，天然不可见），故在 PC 菜单侧显式追加。
 * KPI / 人事 / 财务 / 入离职四键按 A3-2 只放行 ADMIN：四者的契约读写接口多为 roles:['ADMIN']，
 * 放行站长会造成「菜单可见但点进去被重定向」，故菜单可见性与路由 meta.roles 严格同口径。
 *
 * leave / logs 不进本表：二者已直接写入 MENU_WHITELIST 真源（设计规范 §3.2），
 * 再补一份会让「谁能看请假、谁能看日志」出现两个真源，权限口径迟早分裂。
 * settings 属本轮新增的只读信息页，同样因 shared 层本轮冻结而暂补在本表（口径与 logs 相同：仅 ADMIN），
 * 待 shared 解冻后与上面几个键一起并入 MENU_WHITELIST，并删除本处补充。
 * TODO(扩展): shared 层解冻后把 attendance / schedule / kpi / hr / finance / onboard / settings 并入 MENU_WHITELIST，删除本处补充
 */
const EXTRA_MENU_KEYS = {
  ADMIN: ['attendance', 'schedule', 'kpi', 'hr', 'finance', 'onboard', 'settings'],
  STATION_ADMIN: ['attendance', 'schedule']
}

export const MENU_ITEMS = [
  { key: 'dashboard', path: '/dashboard', title: '数据看板', icon: 'DataLine' },
  // 组织人事：员工管理与 KPI / 人事 / 入离职并列，KPI 通过「员工档案聚合页」回链实现「并入员工管理模块」（A11-1）
  { key: 'employee', path: '/employee', title: '员工管理', icon: 'User', group: 'org' },
  { key: 'kpi', path: '/employee/kpi', title: 'KPI 考核', icon: 'TrendCharts', group: 'org' },
  { key: 'hr', path: '/hr', title: '人事管理', icon: 'Postcard', group: 'org' },
  { key: 'onboard', path: '/onboard', title: '入离职', icon: 'Promotion', group: 'org' },
  { key: 'department', path: '/department', title: '部门管理', icon: 'Share', group: 'org' },
  { key: 'station', path: '/station', title: '驿站管理', icon: 'Location', group: 'org' },
  // 考勤薪酬
  { key: 'attendance', path: '/attendance', title: '考勤管理', icon: 'AlarmClock', group: 'pay' },
  { key: 'schedule', path: '/schedule', title: '排班管理', icon: 'Calendar', group: 'pay' },
  { key: 'finance', path: '/finance', title: '财务管理', icon: 'Money', group: 'pay' },
  // 请假管理：审批（站长初审 / 管理员终审）与扣款开关同屏，Q6 裁决放页内卡片而非独立菜单项
  // 图标不得与「工单管理」重复（原同为 Tickets）：Notebook 语义更贴请假，且未被其它菜单占用（已核实 icons-vue 导出）
  { key: 'leave', path: '/leave', title: '请假管理', icon: 'Notebook', group: 'pay' },
  // 包裹作业：采集配置是「同步任务」页内的第二个视图，不另开菜单键（B1.1）
  { key: 'parcel', path: '/parcel', title: '包裹管理', icon: 'Box', group: 'biz' },
  { key: 'sync', path: '/parcel/sync', title: '同步任务', icon: 'Refresh', group: 'biz' },
  { key: 'workOrder', path: '/work-order', title: '工单管理', icon: 'Tickets', group: 'biz' },
  { key: 'notification', path: '/notification', title: '通知中心', icon: 'Bell', group: 'biz' },
  // 系统：运行日志与系统设置均仅 ADMIN（移动端不做查看页，手机上读堆栈和"关于本机"都无价值）
  { key: 'logs', path: '/system/logs', title: '运行日志', icon: 'Document', group: 'sys' },
  { key: 'settings', path: '/system/settings', title: '系统设置', icon: 'Tools', group: 'sys' },
  { key: 'profile', path: '/profile', title: '个人中心', icon: 'UserFilled', group: 'sys' }
]

/** 按角色白名单过滤菜单，并把带 group 的项聚合为子菜单（顺序沿用 MENU_ITEMS 声明顺序） */
export function buildMenus(role) {
  const allowed = [...(MENU_WHITELIST[role] || []), ...(EXTRA_MENU_KEYS[role] || [])]
  const menus = []

  MENU_ITEMS.filter((item) => allowed.includes(item.key)).forEach((item) => {
    if (!item.group) {
      menus.push(item)
      return
    }
    const parent = menus.find((menu) => menu.key === item.group)
    if (parent) parent.children.push(item)
    else
      menus.push({
        key: item.group,
        title: MENU_GROUPS[item.group] || item.group,
        icon: MENU_GROUP_ICONS[item.group] || 'Box',
        children: [item]
      })
  })

  return menus
}
