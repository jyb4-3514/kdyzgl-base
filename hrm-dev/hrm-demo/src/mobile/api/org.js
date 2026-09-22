import http from '../utils/http.js'

/**
 * 组织主数据（员工 / 驿站）：移动端只用于候选与选择器，不做组织管理。
 * 数据可见范围由 Mock 层按角色收敛，前端不做归属过滤（避免两处口径）。
 */

/** 仅 ADMIN 可调；站长与员工身份的候选来源见 utils/workorder.js 的 fetchTransferTargets */
export const getEmployees = (params) => http.get('/employees', { params })
/** 老板端考勤模块的驿站选择（规则 / 排班 / 记录按驿站维度查看） */
export const getStationList = (params) => http.get('/stations', { params })
