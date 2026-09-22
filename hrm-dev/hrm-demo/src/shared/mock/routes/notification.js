import { activeEmployees, db, findStationById, pushNotification } from '../db.js'
import { CODE, DEMO_CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { fail, formatDateTime, ok, paginate } from '../util.js'
import { isBlank, pageSizeInvalid, textLen } from '../validate.js'

/**
 * 通知接口（T09，demo-design.md 7.4.6 契约草案 + 需求4 发布通知）
 * 只返回当前登录人的通知；工单指派/解决由 workOrder.js 联动写入，这里只做读、已读标记与手工发布。
 */

function toNotificationVO(n) {
  return {
    id: n.id,
    type: n.type,
    title: n.title,
    content: n.content,
    bizType: n.biz_type,
    bizId: n.biz_id,
    isRead: n.is_read === 1,
    readTime: n.read_time,
    createTime: n.create_time,
    // 手工发布标记：系统联动通知 isPublished=false 且无发布人信息
    isPublished: n.is_published === 1,
    publisherId: n.publisher_id,
    publisherName: n.publisher_name,
    publishScope: n.publish_scope
  }
}

function list({ params, user }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  let rows = db.notifications.filter((n) => n.employee_id === user.id)
  if (params.isRead !== undefined && params.isRead !== '')
    rows = rows.filter((n) => n.is_read === Number(params.isRead))
  rows = rows.slice().sort((a, b) => (a.create_time < b.create_time ? 1 : -1))
  const page = paginate(rows, params.pageNum, params.pageSize)
  page.list = page.list.map(toNotificationVO)
  return ok(page)
}

function unreadCount({ user }) {
  const count = db.notifications.filter((n) => n.employee_id === user.id && n.is_read === 0).length
  return ok({ count })
}

function markRead({ pathParams, user }) {
  const n = db.notifications.find((x) => x.id === Number(pathParams.id) && x.employee_id === user.id)
  if (!n) return fail(DEMO_CODE.NOTIFICATION_NOT_EXISTS) // 9001
  if (n.is_read === 0) {
    n.is_read = 1
    n.read_time = formatDateTime(new Date())
  }
  return ok(toNotificationVO(n))
}

function readAll({ user }) {
  const now = formatDateTime(new Date())
  db.notifications.forEach((n) => {
    if (n.employee_id === user.id && n.is_read === 0) {
      n.is_read = 1
      n.read_time = now
    }
  })
  return ok(null)
}

/* ==================== 发布通知（需求4） ==================== */

const PUBLISH_SCOPES = ['ALL', 'STATION', 'EMPLOYEE']
/** 放行的通知类型：与 dict.NOTIFICATION_TYPE 同版（5/6 为 M11 请假类型，新增值必须同步放行） */
const PUBLISH_TYPES = [1, 2, 3, 4, 5, 6]

/**
 * 手工发布通知（ADMIN）：按范围批量生成通知记录，返回生成条数。
 * 三种范围的收件人一律取「在职且启用」员工——给停用账号发通知等于发进了黑洞。
 * ALL=全员 / STATION=指定驿站 / EMPLOYEE=指定员工；范围参数不合法统一回 9002。
 */
function publish({ body, user }) {
  if (!textLen(body.title, 1, 100)) return fail(CODE.BAD_REQUEST, '标题长度须为 1-100 字符')
  if (!textLen(body.content, 1, 500)) return fail(CODE.BAD_REQUEST, '内容长度须为 1-500 字符')
  const type = body.type === undefined ? 4 : Number(body.type)
  if (!PUBLISH_TYPES.includes(type)) return fail(CODE.BAD_REQUEST, '通知类型非法')

  const scope = String(body.scope || '').toUpperCase()
  if (!PUBLISH_SCOPES.includes(scope)) return fail(DEMO_CODE.NOTIFICATION_PUBLISH_SCOPE_INVALID) // 9002
  const active = activeEmployees().filter((e) => e.status === 1)
  let targets = []
  if (scope === 'ALL') {
    targets = active
  } else if (scope === 'STATION') {
    const stationId = isBlank(body.stationId) ? null : Number(body.stationId)
    if (stationId == null || !findStationById(stationId)) return fail(DEMO_CODE.NOTIFICATION_PUBLISH_SCOPE_INVALID)
    targets = active.filter((e) => e.station_id === stationId)
  } else {
    if (!Array.isArray(body.employeeIds) || body.employeeIds.length === 0)
      return fail(DEMO_CODE.NOTIFICATION_PUBLISH_SCOPE_INVALID)
    const ids = new Set(body.employeeIds.map(Number))
    targets = active.filter((e) => ids.has(e.id))
    if (!targets.length) return fail(DEMO_CODE.NOTIFICATION_PUBLISH_SCOPE_INVALID)
  }

  const title = String(body.title).trim()
  const content = String(body.content).trim()
  targets.forEach((employee) => {
    pushNotification({
      employeeId: employee.id,
      type,
      title,
      content,
      bizType: null,
      bizId: null,
      publisherId: user.id,
      publisherName: user.real_name,
      publishScope: scope,
      isPublished: 1
    })
  })
  return ok({ count: targets.length })
}

export const notificationRoutes = [
  { method: 'get', path: '/notifications', roles: ALL_ROLES, handler: list },
  { method: 'get', path: '/notifications/unread-count', roles: ALL_ROLES, handler: unreadCount },
  { method: 'put', path: '/notifications/read-all', roles: ALL_ROLES, handler: readAll },
  { method: 'post', path: '/notifications/publish', roles: ['ADMIN'], handler: publish },
  { method: 'put', path: '/notifications/:id/read', roles: ALL_ROLES, handler: markRead }
]
