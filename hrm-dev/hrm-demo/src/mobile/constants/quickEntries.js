/**
 * 首页快捷功能宫格配置（demo-mobile-nav-redesign.md B2 / B3）
 *
 * 只放静态项（名称 / 图标 / 落点 / 形态），实时值由首页按 `key` 注入。
 * 为什么不在配置里写取数函数：宫格的数据源与首页其余区块同源（如 `/parcels/summary` 同时供指标卡与「包裹趋势」），
 * 配置里再取一次会违背 B6-3 的「同一接口取一次共享」。
 *
 * 形态三选一（B4-1，互斥不叠加）：
 * - count  计数型：待办队列，角标显示数值
 * - status 状态型：第二行数据行显示状态值
 * - plain  纯入口型：无对应实时数据
 *
 * 排序按 B1「待办优先」：计数型在前，状态型/纯入口在后；员工端固定「打卡」为第 1 项（决策已确认）。
 * 图标名均在安装的 vant@4.10.2 中核实存在，非臆造。
 *
 * 项级角色白名单（M11 新增，向后兼容）：`roles` 缺省 = 所有能看到本表的角色都可见，
 * 既有 8 + 8 项无须改动；消费侧（components/HomeQuickGrid.vue）用 shared/domain/permission.canAccess
 * 按登录角色过滤，规则与路由 meta.roles 同一份判定。
 */
export const BOSS_QUICK_ENTRIES = [
  { key: 'orders', text: '工单管理', icon: 'todo-list-o', to: '/boss/workorder', type: 'count' },
  { key: 'makeups', text: '补卡审批', icon: 'clock-o', to: '/boss/attendance/makeup', type: 'count' },
  { key: 'payrolls', text: '工资单审核', icon: 'bill-o', to: '/boss/payroll', type: 'count' },
  { key: 'flows', text: '入离职审批', icon: 'friends-o', to: '/boss/flow', type: 'count' },
  { key: 'leaves', text: '请假审批', icon: 'notes-o', to: '/boss/leave', type: 'count' },
  { key: 'attendance', text: '考勤概览', icon: 'records', to: '/boss/attendance', type: 'count' },
  { key: 'alerts', text: '异常预警', icon: 'warning-o', to: '/boss/alerts', type: 'count' },
  { key: 'trend', text: '包裹趋势', icon: 'chart-trending-o', to: '/boss/trend', type: 'status' },
  { key: 'rank', text: '驿站排行', icon: 'bar-chart-o', to: '/boss/rank', type: 'status' }
]

export const STAFF_QUICK_ENTRIES = [
  { key: 'attendance', text: '打卡', icon: 'clock-o', to: '/staff/attendance', type: 'status' },
  { key: 'orders', text: '工单', icon: 'todo-list-o', to: '/staff/workorder', type: 'count' },
  { key: 'myPayrolls', text: '我的工资单', icon: 'bill-o', to: '/staff/payroll', type: 'count' },
  { key: 'myMakeups', text: '我的补卡申请', icon: 'records', to: '/staff/attendance/makeup', type: 'count' },
  // 站长专属：初审是待办队列（有实时条数），故用 count 而非 plain，且排在 count 段末尾
  {
    key: 'leaveReview',
    text: '请假初审',
    icon: 'notes-o',
    to: '/staff/leave/review',
    type: 'count',
    roles: ['STATION_ADMIN']
  },
  // 员工端「请假」用 plain：发起申请不属于待办队列，待办由「我的请假申请」分组承担（§3.4-A）
  { key: 'leave', text: '请假', icon: 'notes-o', to: '/staff/leave/apply', type: 'plain' },
  { key: 'parcel', text: '本站包裹', icon: 'logistics', to: '/staff/parcel', type: 'plain' },
  { key: 'pickup', text: '取件核销', icon: 'scan', to: '/staff/pickup', type: 'plain' },
  { key: 'schedule', text: '我的排班', icon: 'calendar-o', to: '/staff/schedule', type: 'status' },
  { key: 'kpi', text: '我的 KPI', icon: 'bar-chart-o', to: '/staff/kpi', type: 'status' }
]
