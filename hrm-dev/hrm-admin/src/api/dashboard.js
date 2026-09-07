import request from '../utils/request'

/**
 * 看板接口（api.md 4.2，1 个，仅 ADMIN）
 */

// GET /api/v1/dashboard/summary 看板统计：员工总数/驿站数/部门数/今日登录数（去重员工数）
export function getSummary() {
  return request.get('/dashboard/summary')
}
