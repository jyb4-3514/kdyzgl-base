/**
 * 消息 Tab「待办」子视图的分组配置（demo-mobile-nav-redesign.md A4-3 配置表，逐条落地）· 管理端「驿站精灵」
 *
 * 本文件只放静态描述（key / title / to / params），取数与行文案在 stores/todo.js。
 * 为什么分开：配置层反向依赖 api，会让「改一个接口要跳两处」，constants 也不再是纯静态层（消 G3）。
 * 端固定化（B4）：本端只保留管理端 5 组（MVP 裁剪下架「采集异常」组，其属二期采集域），路径全在 /boss/* 内。
 */
export const BOSS_TODO_GROUPS = [
  {
    key: 'orders',
    title: '待处理工单',
    to: '/boss/workorder',
    params: { status: 0, pageNum: 1, pageSize: 3 }
  },
  {
    key: 'makeups',
    title: '待审批补卡',
    to: '/boss/attendance/makeup',
    params: { status: 'PENDING', pageNum: 1, pageSize: 3 }
  },
  {
    key: 'payrolls',
    title: '待审核工资单',
    to: '/boss/payroll',
    params: { status: 'PENDING_APPROVAL', pageNum: 1, pageSize: 3 }
  },
  {
    key: 'flows',
    title: '进行中入离职',
    to: '/boss/flow',
    // 入离职两个端点共用同一组筛选，合并口径见 stores/todo.js 的 flows loader
    params: { status: 'IN_PROGRESS', pageNum: 1, pageSize: 3 }
  },
  {
    key: 'leaves',
    title: '待终审请假',
    to: '/boss/leave',
    params: { status: 'PENDING_BOSS', pageNum: 1, pageSize: 3 }
  }
]
