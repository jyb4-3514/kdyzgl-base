import { authRoutes } from './auth.js'
import { dashboardRoutes } from './dashboard.js'
import { employeeRoutes } from './employee.js'
import { departmentRoutes } from './department.js'
import { stationRoutes } from './station.js'
import { parcelRoutes } from './parcel.js'
import { syncTaskRoutes } from './syncTask.js'
import { syncConfigCenterRoutes } from './syncConfigCenter.js'
import { syncConfigRoutes } from './syncConfig.js'
import { workOrderRoutes } from './workOrder.js'
import { notificationRoutes } from './notification.js'
import { attendanceRoutes } from './attendance.js'
import { kpiRoutes } from './kpi.js'
import { hrRoutes } from './hr.js'
import { financeRoutes } from './finance.js'
import { leaveRoutes } from './leave.js'
import { systemLogRoutes } from './systemLogs.js'

/**
 * 路由注册表（engine 按本数组顺序取首个命中，路径参数与静态路径冲突时以先注册者为准）
 * 一期 24 接口 + 二三期包裹/同步/工单/通知（T06-T09，demo-design.md 7.4）+ 考勤/排班（T17）
 * + KPI 考核（需求7）/ 人事与入离职（需求8、10）/ 财务工资单（需求9）/ 请假（M11）
 * TODO(扩展): 二期爬虫侧 /api/v1/crawler/* 不纳入 Demo（面向机器接口，见 demo-design.md 1.2）
 */
export const routes = [
  ...authRoutes,
  ...dashboardRoutes,
  ...employeeRoutes,
  ...departmentRoutes,
  ...stationRoutes,
  ...parcelRoutes,
  ...syncTaskRoutes,
  // 配置中心必须早于 syncConfigRoutes：/sync/configs/global 与 /sync/configs/export 是静态路径，
  // 不能落到 syncConfigRoutes 的 /sync/configs/:stationId 上（engine 取首个命中）
  ...syncConfigCenterRoutes,
  ...syncConfigRoutes,
  ...workOrderRoutes,
  ...notificationRoutes,
  ...attendanceRoutes,
  ...kpiRoutes,
  ...hrRoutes,
  ...financeRoutes,
  // 请假路由内部已按「静态路径在前」排好序（/leave/preview 等不能被 /leave/:id 命中）
  ...leaveRoutes,
  ...systemLogRoutes
]
