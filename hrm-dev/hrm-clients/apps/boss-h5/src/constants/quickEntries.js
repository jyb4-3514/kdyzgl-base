/**
 * 首页快捷功能宫格配置 · 管理端「驿站精灵」
 *
 * 只放静态项（名称 / 图标 / 落点 / 形态），实时值由首页按 `key` 注入。
 * 为什么不在配置里写取数函数：宫格的数据源与首页其余区块同源（如待办计数同时供宫格角标与消息页），
 * 配置里再取一次会违背 B6-3 的「同一接口取一次共享」。
 *
 * 形态三选一（B4-1，互斥不叠加）：count 计数型 / status 状态型 / plain 纯入口型。
 *
 * v1.1（boss-management-ui-design.md ②）：宫格**收敛为 6 项（4 列 2 行）**。
 * 删除 4 项（`makeups` 补卡审批 / `payrolls` 工资单审核 / `flows` 入离职审批 / `leaves` 请假审批）：
 * 审批入口统一由「审批中心」承接，首页不再直挂分散入口；这 4 类业务页与路由**全部保留**，
 * 仍是审批中心组头 / 明细行的跳转目标，信息不丢失（数量并入 approvalTotal 与审批中心汇总条）。
 *
 * 排序按 B1：第 1 行 = 待办与概览（计数型，审批中心前移第 1）；
 * 第 2 行 = 管理与配置（plain / status），与「我的 → 管理与配置」同域呼应。
 * 端固定化（B4）：本端只保留管理端 6 项，路径全在 /boss/* 内。
 * 图标名均在安装的 vant@4.10.2 中核实存在，非臆造。
 */
export const BOSS_QUICK_ENTRIES = [
  { key: 'approvals', text: '审批中心', icon: 'orders-o', to: '/boss/approval', type: 'count' },
  { key: 'orders', text: '工单管理', icon: 'todo-list-o', to: '/boss/workorder', type: 'count' },
  { key: 'attendance', text: '考勤概览', icon: 'records', to: '/boss/attendance', type: 'count' },
  { key: 'alerts', text: '异常预警', icon: 'warning-o', to: '/boss/alerts', type: 'count' },
  { key: 'finance', text: '财务管理', icon: 'balance-o', to: '/boss/finance', type: 'plain' },
  { key: 'stations', text: '驿站管理', icon: 'shop-o', to: '/boss/station', type: 'status' }
]
