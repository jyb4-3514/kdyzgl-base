import { CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { CLIENT_LOG_SOURCE } from '../../constants/dict.js'
import { clearClientLogs, pushClientLogs, queryClientLogs } from '../clientLogStore.js'
import { fail, ok } from '../util.js'
import { isBlank, isDate, pageSizeInvalid } from '../validate.js'

/**
 * 前端运行日志接口（M11 D6，设计规范第 7 章）
 * 上报端点不限角色（任何端都可能出错，包括未登录页的脚本异常），查看与清空仅 ADMIN。
 */

const SYNC_LOG_LEVELS = ['INFO', 'WARN', 'ERROR']
/** 单批上限：前端缓冲满 20 条即上报，这里放宽到 100 只用于挡异常放大，不改变前端批次大小 */
const BATCH_MAX = 100

function ingest({ body }) {
  const logs = body.logs
  if (!Array.isArray(logs) || !logs.length) return fail(CODE.BAD_REQUEST, 'logs 须为非空数组')
  if (logs.length > BATCH_MAX) return fail(CODE.BAD_REQUEST, `单批日志不超过 ${BATCH_MAX} 条`)
  return ok({ accepted: pushClientLogs(logs) })
}

function list({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isBlank(params.level) && !SYNC_LOG_LEVELS.includes(params.level)) return fail(CODE.BAD_REQUEST, 'level 取值非法')
  if (!isBlank(params.source) && !CLIENT_LOG_SOURCE[params.source]) return fail(CODE.BAD_REQUEST, 'source 取值非法')
  for (const key of ['startTime', 'endTime']) {
    if (!isBlank(params[key]) && !isDate(String(params[key]).slice(0, 10)))
      return fail(CODE.BAD_REQUEST, `${key} 格式须为 YYYY-MM-DD`)
  }
  return ok(
    queryClientLogs({
      level: params.level,
      source: params.source,
      keyword: params.keyword,
      startTime: params.startTime,
      endTime: params.endTime,
      employeeId: params.employeeId,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

const clear = () => ok({ cleared: clearClientLogs() })

export const systemLogRoutes = [
  { method: 'post', path: '/system/client-logs', roles: ALL_ROLES, handler: ingest },
  { method: 'get', path: '/system/client-logs', roles: ['ADMIN'], handler: list },
  { method: 'post', path: '/system/client-logs/clear', roles: ['ADMIN'], handler: clear }
]
