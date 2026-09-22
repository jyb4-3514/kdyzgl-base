/**
 * 排班域纯函数（单元格键 / 日期解析 / 班次可用性 / 矩阵快照）
 *
 * 为什么单独一层：这些判定在矩阵渲染、保存增量、批量工具预览三处都要用，
 * 且全部以 Mock 的校验口径为准（例如「已停用班次提交会被 9106 拒」），
 * 放纯函数层便于单测钉住，组件与 composable 只做编排。
 */

/** 'YYYY-MM-DD' → 本地 0 点：new Date('YYYY-MM-DD') 按 UTC 解析，跨时区会算错一天 */
export function localDate(text) {
  const [y, m, d] = String(text).split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** 单元格键：员工 × 日期唯一确定一格 */
export const cellKey = (employeeId, workDate) => `${employeeId}_${workDate}`

/** 更新时间戳（HH:mm），页头副信息用 */
export function stamp() {
  const now = new Date()
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
}

/** 单元格左侧色条取所选班次配色；未排班不显色 */
export function shiftColorOf(shifts, shiftId) {
  if (shiftId == null) return 'transparent'
  const hit = shifts.find((item) => item.id === shiftId)
  // 班次已被删除时（不在列表里）用警告色左条，提示该格需要重选（A10-5）
  return hit ? hit.color : 'var(--state-warning-fg)'
}

/** 已停用或已删除的班次：提交时服务端会回 9106，故在提交前就把这些格标出来（A10-5） */
export function isUnavailableShift(shifts, shiftId) {
  if (shiftId == null) return false
  const hit = shifts.find((item) => item.id === shiftId)
  return !hit || hit.status !== 1
}

/** 服务端矩阵 → 本地可编辑行：深拷贝一层，避免单元格改本地副本时把返回对象改脏、无法判断增量 */
export function buildRows(employees = []) {
  return employees.map((item) => ({
    employeeId: item.employeeId,
    employeeName: item.employeeName,
    days: item.days.map((day) => ({ ...day }))
  }))
}

/** 服务端矩阵 → 原始值 Map（key = employeeId_workDate），用于判断某格是否真的改过、以及撤销 */
export function buildOriginal(employees = []) {
  return new Map(
    employees.flatMap((item) => item.days.map((day) => [cellKey(item.employeeId, day.workDate), day.shiftId ?? null]))
  )
}

/** 已排格数：分母 = 员工数 × 天数，分子 = 有班次的格数 */
export function filledCellsOf(rows = []) {
  return rows.reduce((sum, row) => sum + row.days.filter((day) => day.shiftId != null).length, 0)
}

/** 某天已排人数：列头据此判断该天是否排满 */
export function dayFilledCountOf(rows = [], index) {
  return rows.filter((row) => row.days[index] && row.days[index].shiftId != null).length
}
