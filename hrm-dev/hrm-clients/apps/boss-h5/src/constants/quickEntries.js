/**
 * 首页快捷功能宫格配置（demo-mobile-nav-redesign.md B2 / B3）· 管理端「驿站精灵」
 *
 * 只放静态项（名称 / 图标 / 落点 / 形态），实时值由首页按 `key` 注入。
 * 为什么不在配置里写取数函数：宫格的数据源与首页其余区块同源（如待办计数同时供宫格角标与消息页），
 * 配置里再取一次会违背 B6-3 的「同一接口取一次共享」。
 *
 * 形态三选一（B4-1，互斥不叠加）：count 计数型 / status 状态型 / plain 纯入口型。
 * 排序按 B1「待办优先」：计数型在前，状态型 / 纯入口在后。
 * 端固定化（B4）：本端只保留管理端 7 项（MVP 裁剪下架「包裹趋势」「驿站排行」两项），路径全在 /boss/* 内。
 * 图标名均在安装的 vant@4.10.2 中核实存在，非臆造。
 */
export const BOSS_QUICK_ENTRIES = [
  { key: 'orders', text: '工单管理', icon: 'todo-list-o', to: '/boss/workorder', type: 'count' },
  { key: 'makeups', text: '补卡审批', icon: 'clock-o', to: '/boss/attendance/makeup', type: 'count' },
  { key: 'payrolls', text: '工资单审核', icon: 'bill-o', to: '/boss/payroll', type: 'count' },
  { key: 'flows', text: '入离职审批', icon: 'friends-o', to: '/boss/flow', type: 'count' },
  { key: 'leaves', text: '请假审批', icon: 'notes-o', to: '/boss/leave', type: 'count' },
  { key: 'attendance', text: '考勤概览', icon: 'records', to: '/boss/attendance', type: 'count' },
  { key: 'alerts', text: '异常预警', icon: 'warning-o', to: '/boss/alerts', type: 'count' }
]
