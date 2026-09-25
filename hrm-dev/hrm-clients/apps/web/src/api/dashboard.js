import request from '../utils/http.js'

/** 看板接口薄壳（api.md 4.2，仅 ADMIN）：GET /dashboard/summary 员工总数/驿站数/部门数/今日登录数 */
export function getSummary() {
  return request.get('/dashboard/summary')
}
