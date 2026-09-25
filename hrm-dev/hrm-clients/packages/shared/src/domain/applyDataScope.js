import { isAdmin } from '../constants/role.js'

/**
 * 数据级权限收口（demo-staff-refactor.md §6.3「数据级」）
 *
 * 为什么放在 domain：归属收敛是「谁能看哪些数据」的判定，属权限域，与 mock 实现无关；
 * 由 engine 在调用 handler 前统一执行一次，各 handler 不再各写一份 stationId 覆盖。
 *
 * 收敛口径（逐条对齐审计结果，不新增语义）：
 * - ADMIN：原样放行（可跨驿站筛选），返回新对象而非入参本身，避免调用方后续改动污染上游。
 * - 非 ADMIN（STATION_ADMIN / STAFF）：查询参数 stationId 一律强制为本人的归属驿站。
 * - 无 user（auth:false 的公开端点）：放行，公开端点不存在归属概念。
 *
 * 只收敛 stationId，不收敛 employeeId：审计全部 handler 后，现存唯一的「参数被归属覆盖」模式就是
 * stationId；「只看本人」的端点（/xxx/my、通知列表等）直接读 user.id 实现，不经查询参数，
 * 故不在此臆造 employeeId 覆盖（避免制造与前端本地判定并存的双口径）。
 * TODO(扩展): 若后续出现「按 employeeId 查询他人数据」的列表端点，在此补 employeeId 收敛，
 *   并同步单测与 demo-staff-refactor.md §6.3。
 */
export function applyDataScope(params, user) {
  const source = params && typeof params === 'object' ? params : {}
  if (!user || isAdmin(user.role)) return { ...source }
  return { ...source, stationId: user.station_id }
}
