import { db, findSyncTaskById, pushSyncLog, stationName } from '../db.js'
import { CODE, DEMO_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { fail, formatDateTime, ok, paginate } from '../util.js'
import { pageSizeInvalid } from '../validate.js'

/**
 * 同步任务接口（T07，demo-design.md 7.4.3/7.4.4）
 * 状态机四态：待领取(0) → 执行中(1) → 成功(2)/失败(3)；仅「失败」可重试（回到待领取并 retry_count+1）。
 * STAFF 不可见（T15：员工端无同步状态页），故仅 ADMIN / STATION_ADMIN 可访问。
 */

function toSyncTaskVO(task) {
  return {
    id: task.id,
    stationId: task.station_id,
    stationName: stationName(task.station_id),
    batchNo: task.batch_no,
    status: task.status,
    parcelTotal: task.parcel_total,
    successCount: task.success_count,
    failCount: task.fail_count,
    retryCount: task.retry_count,
    errorMsg: task.error_msg,
    assignTime: task.assign_time,
    startTime: task.start_time,
    finishTime: task.finish_time,
    createTime: task.create_time,
    updateTime: task.update_time
  }
}

function list({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  let rows = db.syncTasks
  // stationId 已在 engine 按角色收敛（非 ADMIN 强制本站），这里只做空值归一
  const stationId = params.stationId
  if (stationId != null && stationId !== '') rows = rows.filter((t) => t.station_id === Number(stationId))
  if (params.status !== undefined && params.status !== '') rows = rows.filter((t) => t.status === Number(params.status))
  const keyword = String(params.keyword || '').trim()
  if (keyword) rows = rows.filter((t) => t.batch_no.includes(keyword))
  rows = rows.slice().sort((a, b) => (a.create_time < b.create_time ? 1 : -1))
  const page = paginate(rows, params.pageNum, params.pageSize)
  page.list = page.list.map(toSyncTaskVO)
  return ok(page)
}

function detail({ pathParams, user }) {
  const task = findSyncTaskById(pathParams.id)
  if (!task) return fail(CODE.NOT_FOUND, '同步任务不存在')
  if (user.role !== 'ADMIN' && task.station_id !== user.station_id) return fail(CODE.NOT_FOUND, '同步任务不存在')
  return ok(toSyncTaskVO(task))
}

function logs({ pathParams, user }) {
  const task = findSyncTaskById(pathParams.id)
  if (!task) return fail(CODE.NOT_FOUND, '同步任务不存在')
  if (user.role !== 'ADMIN' && task.station_id !== user.station_id) return fail(CODE.NOT_FOUND, '同步任务不存在')
  const list = db.syncLogs
    .filter((l) => l.task_id === task.id)
    .sort((a, b) => a.id - b.id)
    .map((l) => ({
      id: l.id,
      taskId: l.task_id,
      batchNo: l.batch_no,
      level: l.level,
      message: l.message,
      logTime: l.log_time
    }))
  return ok(list)
}

/** 手动触发：仅待领取可触发，模拟一次完整同步（领取 → 执行中 → 成功） */
function trigger({ pathParams, user }) {
  const task = findSyncTaskById(pathParams.id)
  if (!task) return fail(CODE.NOT_FOUND, '同步任务不存在')
  if (user.role !== 'ADMIN' && task.station_id !== user.station_id) return fail(CODE.FORBIDDEN)
  if (task.status !== 0) return fail(DEMO_CODE.SYNC_RETRY_NOT_ALLOWED, '仅待领取状态可触发执行')

  const now = formatDateTime(new Date())
  task.status = 1
  task.start_time = now
  task.update_time = now
  pushSyncLog(task.id, task.batch_no, 0, '开始执行同步')

  task.status = 2
  task.success_count = task.parcel_total
  task.fail_count = 0
  task.finish_time = formatDateTime(new Date())
  task.update_time = task.finish_time
  pushSyncLog(task.id, task.batch_no, 0, `同步完成，成功 ${task.parcel_total} 条`)
  return ok(toSyncTaskVO(task))
}

function retry({ pathParams, user }) {
  const task = findSyncTaskById(pathParams.id)
  if (!task) return fail(CODE.NOT_FOUND, '同步任务不存在')
  if (user.role !== 'ADMIN' && task.station_id !== user.station_id) return fail(CODE.FORBIDDEN)
  if (task.status !== 3) return fail(DEMO_CODE.SYNC_RETRY_NOT_ALLOWED) // 6001：仅失败可重试

  task.status = 0
  task.retry_count += 1
  task.error_msg = null
  task.start_time = null
  task.finish_time = null
  task.update_time = formatDateTime(new Date())
  pushSyncLog(task.id, task.batch_no, 1, `重试第 ${task.retry_count} 次，重新进入待领取队列`)
  return ok(toSyncTaskVO(task))
}

export const syncTaskRoutes = [
  { method: 'get', path: '/sync-tasks', roles: ['ADMIN', 'STATION_ADMIN'], handler: list },
  { method: 'get', path: '/sync-tasks/:id/logs', roles: ['ADMIN', 'STATION_ADMIN'], handler: logs },
  { method: 'get', path: '/sync-tasks/:id', roles: ['ADMIN', 'STATION_ADMIN'], handler: detail },
  { method: 'post', path: '/sync-tasks/:id/trigger', roles: ['ADMIN', 'STATION_ADMIN'], handler: trigger },
  { method: 'post', path: '/sync-tasks/:id/retry', roles: ['ADMIN', 'STATION_ADMIN'], handler: retry }
]
