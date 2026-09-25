/**
 * 消息 Tab「待办」子视图的分组配置（demo-mobile-nav-redesign.md A4-3 配置表）· 员工端
 *
 * 本文件只放静态描述（key / title / to / roles / params），取数与行文案在 stores/todo.js。
 * 为什么分开：配置层反向依赖 api，会让「改一个接口要跳两处」，constants 也不再是纯静态层（消 G3）。
 *
 * 组级角色白名单：`roles` 缺省 = 所有能看到本表的角色都可见；
 * 消费侧（stores/todo.js）用 shared/domain/permission.canAccess 按登录角色过滤。
 */
export const STAFF_TODO_GROUPS = [
  {
    key: 'orders',
    title: '待处理工单',
    to: '/staff/workorder',
    params: { status: 0, pageNum: 1, pageSize: 3 }
  },
  {
    key: 'myPayrolls',
    title: '待确认工资单',
    to: '/staff/payroll',
    params: { status: 'PUBLISHED', pageNum: 1, pageSize: 3 }
  },
  {
    key: 'myMakeups',
    title: '我的补卡申请',
    to: '/staff/attendance/makeup',
    params: { status: 'PENDING', pageNum: 1, pageSize: 3 }
  },
  {
    key: 'myLeaves',
    title: '我的请假申请',
    to: '/staff/leave',
    params: { status: 'PENDING', pageNum: 1, pageSize: 3 }
  },
  {
    key: 'leaveReview',
    title: '待初审请假',
    to: '/staff/leave/review',
    roles: ['STATION_ADMIN'],
    params: { status: 'PENDING_STATION', pageNum: 1, pageSize: 3 }
  }
]
