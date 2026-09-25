import { db, activeDepartments, activeEmployees } from '../db.js'
import { ok, todayStart } from '../util.js'

/**
 * 看板接口（api.md 4.2，1 个，仅 ADMIN）
 * 统计口径与 api.md 4.2.1 的 SQL 逐条对应；todayLoginCount 按去重员工数（生单人去重，防单人刷指标）
 */
function summary({ db: database }) {
  const todayStartTs = todayStart()
  const todayLoginEmployeeIds = new Set(
    database.loginLogs
      .filter(
        (log) =>
          log.login_result === 1 &&
          log.employee_id &&
          new Date(log.login_time.replace(' ', 'T')).getTime() >= todayStartTs
      )
      .map((log) => log.employee_id)
  )

  return ok({
    employeeTotal: activeEmployees().length, // 含禁用（api.md 4.2.1 口径）
    stationTotal: db.stations.filter((s) => s.is_deleted === 0).length, // 含停用
    departmentTotal: activeDepartments().length,
    todayLoginCount: todayLoginEmployeeIds.size
  })
}

export const dashboardRoutes = [{ method: 'get', path: '/dashboard/summary', roles: ['ADMIN'], handler: summary }]
