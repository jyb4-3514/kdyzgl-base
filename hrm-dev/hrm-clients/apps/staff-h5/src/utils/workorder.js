import { getStationRoster } from '../api/attendance.js'
import { DEMO_CODE } from '@kdyzgl/shared/constants/errorCode.js'

/**
 * 工单转单 / 指派的页面层共用逻辑 · 员工端「驿站助手」
 *
 * 本端只服务站长 / 员工：候选来源为本站排班名册，并一次性执行「只在职、排除本人、限本站」，
 * 与 Mock 侧 8004 的判定同口径 —— 前端过滤只影响选择体验，防线仍在 Mock 层。
 *
 * TODO(扩展): 后端开放「按驿站查在职员工」接口后，删除本地兜底路径，统一走该接口。
 */

/** 转单校验只接受在职员工（status === 1），候选也必须同口径，否则选完必被 8004 打回 */
const ON_DUTY = 1

/** 站长名册：排班矩阵按驿站返回在职员工，取其中的人员列即可（一次请求，不需要额外的员工接口） */
async function stationRoster(stationId) {
  const matrix = await getStationRoster({ stationId })
  return matrix.employees.map((item) => ({ id: item.employeeId, name: item.employeeName, stationName: '' }))
}

/**
 * 取可转单 / 可指派的人选
 * @param {{ role: string, stationId: number, selfId: number }} ctx stationId 取工单归属驿站（本端只能在本站内消化）
 */
export async function fetchTransferTargets({ stationId, selfId }) {
  let rows
  try {
    rows = await stationRoster(stationId)
  } catch (e) {
    // 员工身份拿不到名册（403）：演示态回落到本地员工表，真实接口环境如实抛错。
    // Mock 关闭时不加载假名册，改由真实接口取；此处 dynamic import 让 Rollup 可在生产构建剔除 mock
    if (import.meta.env.VITE_MOCK_ENABLED !== 'true') throw e
    const { activeEmployees } = await import('@kdyzgl/mock/db.js')
    rows = activeEmployees()
      .filter((row) => row.station_id === Number(stationId) && row.status === ON_DUTY)
      .map((row) => ({ id: row.id, name: row.real_name, stationName: '' }))
  }
  return rows.filter((item) => item.id !== selfId)
}

/**
 * 转单 / 指派失败提示
 * 8002/8003/8004 的默认文案只说「不合法」，操作人需要知道下一步：是权限不够，还是选的人不对。
 */
export function transferErrorHint(code, ctx = {}) {
  switch (code) {
    case DEMO_CODE.WORK_ORDER_TRANSFER_NO_PERMISSION:
      return '当前身份无权转单该工单：仅管理员、本站站长或该工单当前处理人可转单'
    case DEMO_CODE.WORK_ORDER_NO_PERMISSION:
      return '当前身份无权指派该工单：仅管理员或本站站长可指派'
    case DEMO_CODE.WORK_ORDER_TRANSFER_TARGET_INVALID:
      return ctx.crossStation
        ? '转单对象不合法：对方须为在职员工，且不能转给自己'
        : `转单对象不合法：只能转给本站（${ctx.stationName || '本驿站'}）的在职员工，且不能转给自己`
    default:
      return ctx.message || '操作失败，请稍后重试'
  }
}
