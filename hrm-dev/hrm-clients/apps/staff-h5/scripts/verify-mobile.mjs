/**
 * 员工路径 T13–T16 移动端页面数据链路实测（`npm run verify:mobile`）· 员工端「驿站助手」
 *
 * 来源：hrm-demo `scripts/verify-mobile-t13-t16.mjs`（先读后按「拆分前后断言清单对照表」取员工端归属子集，未改断言口径）。
 * 归属对照（test-cases.md 对照表 §二）：员工端自有 28 条（原 #17–#35、#37–#45）+ 跨端共用 8 条（原 #1–#5、#46–#48）
 * = **36**（分端下限 36，两端合计 ≥48）。
 *
 * 与原脚本的两处差异（均非断言弱化）：
 * 1. 原 #36（工单指派·ADMIN）属管理端，本端不计入断言；但 #41「指派联动生成通知」依赖该动作，
 *    故保留其**作为前置动作**（不计数），见「跨端联动前置」段。
 * 2. 登录上报端类型改用新契约 `clientType=STAFF`（+ as=station 兼容读），对齐新入口口径。
 */
import axios from 'axios'
import { createMockAdapter } from '@kdyzgl/mock/engine.js'
import { db, resetDb } from '@kdyzgl/mock/db.js'
import { queryParcels, resetParcelStore } from '@kdyzgl/mock/parcelStore.js'
import { overlay } from '@kdyzgl/mock/overlay.js'

const service = axios.create({ baseURL: '/api/v1' })
service.defaults.adapter = createMockAdapter({ delay: false })
service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      const err = new Error(body.message)
      err.code = body.code
      return Promise.reject(err)
    }
    return body
  },
  (error) => {
    const body = (error.response && error.response.data) || {}
    const err = new Error(body.message || error.message)
    err.code = body.code || (error.response && error.response.status)
    return Promise.reject(err)
  }
)

let pass = 0
let fail = 0
const failures = []
function check(name, condition, extra = '') {
  if (condition) pass += 1
  else {
    fail += 1
    failures.push(`${name}${extra ? ` → ${extra}` : ''}`)
  }
}
async function call(method, url, { data, params, token } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  try {
    return { ok: true, data: await service.request({ method, url, data, params, headers }) }
  } catch (error) {
    return { ok: false, code: error.code, message: error.message }
  }
}

/**
 * 登录助手：端准入 fail-closed 后登录必须上报端类型。
 * 员工端入口恒报 clientType=STAFF（另带 as=station 兼容读）；管理员属异端，仅作跨端联动前置（报 WEB）。
 */
const login = async (username) => {
  const employee = db.employees.find((e) => e.is_deleted === 0 && e.username === username)
  const endFields = employee && employee.role === 'ADMIN' ? { clientType: 'WEB' } : { clientType: 'STAFF', as: 'station' }
  const res = await call('post', '/auth/login', { data: { username, password: 'demo1234', ...endFields } })
  return res.ok ? res.data.token : null
}

const pad = (n) => String(n).padStart(2, '0')
const hoursAgo = (h) => {
  const d = new Date(Date.now() - h * 3600000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/* ==================== 剧本数据（跨端共用，各持一份；原 #1–#5） ==================== */
const overdue = queryParcels({ stationId: null, status: 1, endTime: hoursAgo(48), pageNum: 1, pageSize: 100 })
const demoPick = queryParcels({ stationId: 1, status: 1, pageNum: 1, pageSize: 1 })
const failedSync = db.syncTasks.filter((t) => t.status === 3)
const overSlaSeed = db.workOrders.filter(
  (o) =>
    o.is_deleted === 0 &&
    (o.status === 0 || o.status === 1) &&
    new Date(o.sla_deadline.replace(' ', 'T')).getTime() < Date.now()
)
check('剧本·失败同步任务 ≥1', failedSync.length >= 1, `${failedSync.length} 条`)
check('剧本·城东驿站存在失败批次（S5 演示点）', failedSync.some((t) => t.station_id === 1))
check('剧本·超 SLA 工单 6 条', overSlaSeed.length === 6, `${overSlaSeed.length} 条`)
check('剧本·超 48h 未取件 ≥10', overdue.total >= 10, `${overdue.total} 件`)
check(
  '剧本·指定演示运单号（城东驿站 + 在库待取）',
  !!demoPick.list[0] && demoPick.list[0].stationId === 1 && demoPick.list[0].status === 1,
  demoPick.list[0] && demoPick.list[0].waybillNo
)

/* ==================== 员工端（STATION_ADMIN）：原 #17–#35 ==================== */
// 跨端前置（不计入本端断言）：管理员登录取全局指标，供「本站收敛 < 全局」对照
const adminToken = await login('admin')
const globalSummary = await call('get', '/parcels/summary', { token: adminToken })

const stationToken = await login('st001_admin')
check('员工端·站长登录且归属城东驿站', !!stationToken)

const stationSummary = await call('get', '/parcels/summary', { token: stationToken })
check(
  '员工端·指标收敛为本站（小于全局）',
  stationSummary.ok &&
    globalSummary.ok &&
    stationSummary.data.parcelTotal > 0 &&
    stationSummary.data.parcelTotal < globalSummary.data.parcelTotal,
  stationSummary.ok && globalSummary.ok
    ? `${stationSummary.data.parcelTotal} / 全局 ${globalSummary.data.parcelTotal}`
    : stationSummary.message
)

const stationParcels = await call('get', '/parcels', {
  token: stationToken,
  params: { status: 1, pageNum: 1, pageSize: 20 }
})
check(
  '员工端·包裹列表限定本站',
  stationParcels.ok && stationParcels.data.list.length === 20 && stationParcels.data.list.every((p) => p.stationId === 1)
)
const crossStation = await call('get', '/parcels', {
  token: stationToken,
  params: { stationId: 5, pageNum: 1, pageSize: 20 }
})
check('员工端·传其它驿站 stationId 被强制覆盖', crossStation.ok && crossStation.data.list.every((p) => p.stationId === 1))
const searchParcel = await call('get', '/parcels', {
  token: stationToken,
  params: { waybillNo: demoPick.list[0].waybillNo, pageNum: 1, pageSize: 1 }
})
check(
  '员工端·运单号精确搜索命中 1 条',
  searchParcel.ok && searchParcel.data.total === 1,
  searchParcel.ok ? `${searchParcel.data.total}` : searchParcel.message
)
const searchMiss = await call('get', '/parcels', {
  token: stationToken,
  params: { waybillNo: 'SF0000000000', pageNum: 1, pageSize: 1 }
})
check('员工端·不存在的运单号返回 0 条（页面提示）', searchMiss.ok && searchMiss.data.total === 0)

const stationRank = await call('get', '/parcels/ranking', { token: stationToken })
check('员工端·排行收敛为 1 个驿站', stationRank.ok && stationRank.data.length === 1 && stationRank.data[0].stationId === 1)
const stationOrders = await call('get', '/work-orders', { token: stationToken, params: { pageNum: 1, pageSize: 20 } })
check(
  '员工端·工单限定本站',
  stationOrders.ok && stationOrders.data.list.every((o) => o.stationId === 1),
  stationOrders.ok ? `total=${stationOrders.data.total}` : stationOrders.message
)
const stationSync = await call('get', '/sync-tasks', { token: stationToken, params: { pageNum: 1, pageSize: 30 } })
check(
  '员工端·同步批次限定本站',
  stationSync.ok && stationSync.data.list.every((t) => t.stationId === 1),
  stationSync.ok ? `total=${stationSync.data.total}` : stationSync.message
)
const syncLogs = await call('get', `/sync-tasks/${stationSync.data.list[0].id}/logs`, { token: stationToken })
check('员工端·批次日志可查（只读弹层）', syncLogs.ok && syncLogs.data.length >= 3, syncLogs.ok ? `${syncLogs.data.length} 条` : syncLogs.message)

/* ---- 取件核销闭环（原 #27–#30） ---- */
const demoParcel = demoPick.list[0]
const beforeSummary = stationSummary.data
const pickResult = await call('put', `/parcels/${demoParcel.id}/pickup`, { token: stationToken })
check(
  '取件核销·成功并返回已取件包裹',
  pickResult.ok && pickResult.data.status === 2 && !!pickResult.data.pickupTime,
  pickResult.ok ? `${pickResult.data.waybillNo} ${pickResult.data.pickupTime}` : pickResult.message
)
check(
  '取件核销·取件员工留痕（st001_admin id=3）',
  pickResult.ok && pickResult.data.pickupEmployeeId === 3,
  pickResult.ok ? `${pickResult.data.pickupEmployeeId}` : ''
)
const afterSummary = (await call('get', '/parcels/summary', { token: stationToken })).data
check(
  '取件核销·今日取件 +1',
  afterSummary.todayPickup === beforeSummary.todayPickup + 1,
  `${beforeSummary.todayPickup} → ${afterSummary.todayPickup}`
)
const pickAgain = await call('put', `/parcels/${demoParcel.id}/pickup`, { token: stationToken })
check('取件核销·重复取件返回 7002', !pickAgain.ok && pickAgain.code === 7002, `code=${pickAgain.code}`)

/* ---- 工单四态流转（原 #31–#35） ---- */
const openOrder = stationOrders.data.list.find((o) => o.status === 0 && !o.assigneeId)
check('工单·存在待处理工单可接单', !!openOrder, openOrder && openOrder.orderNo)
const accept = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 1, remark: '接单处理' }
})
check('工单流转·待处理 → 处理中', accept.ok && accept.data.status === 1, accept.ok ? `${accept.data.status}` : `code=${accept.code}`)
const resolve = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 2, remark: '已联系客户并补偿' }
})
check(
  '工单流转·处理中 → 已解决（含处理记录）',
  resolve.ok && resolve.data.status === 2 && !!resolve.data.resolvedTime && resolve.data.handleLog.some((l) => l.action === 'resolve'),
  resolve.ok ? `${resolve.data.resolvedTime}` : `code=${resolve.code}`
)
const close = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 3, remark: '客户确认无误' }
})
check('工单流转·已解决 → 已关闭', close.ok && close.data.status === 3 && !!close.data.closedTime, close.ok ? `${close.data.closedTime}` : `code=${close.code}`)
const illegal = await call('put', `/work-orders/${openOrder.id}/status`, {
  token: stationToken,
  data: { status: 1, remark: '重开' }
})
check('工单流转·已关闭再流转返回 8001', !illegal.ok && illegal.code === 8001, `code=${illegal.code}`)

/* ==================== 跨端联动前置（原 #36，管理端动作，不计入本端断言） ==================== */
await call('put', `/work-orders/${openOrder.id}/assign`, { token: adminToken, data: { assigneeId: 4 } })

/* ==================== STAFF 视角：权限与数据范围（原 #37–#40） ==================== */
const staffToken = await login('st001_staff')
const staffSync = await call('get', '/sync-tasks', { token: staffToken, params: { pageNum: 1, pageSize: 10 } })
check('STAFF·同步状态接口 403（看不到同步页）', !staffSync.ok && staffSync.code === 403, `code=${staffSync.code}`)
const staffParcels = await call('get', '/parcels', { token: staffToken, params: { pageNum: 1, pageSize: 20 } })
check('STAFF·包裹列表可读且限本站', staffParcels.ok && staffParcels.data.list.every((p) => p.stationId === 1))
const staffOrders = await call('get', '/work-orders', { token: staffToken, params: { pageNum: 1, pageSize: 20 } })
check('STAFF·工单列表可读', staffOrders.ok && staffOrders.data.total > 0, staffOrders.ok ? `total=${staffOrders.data.total}` : staffOrders.message)
const notMine = staffOrders.data.list.find((o) => o.assigneeId && o.assigneeId !== 4)
if (notMine) {
  const denied = await call('put', `/work-orders/${notMine.id}/status`, { token: staffToken, data: { status: 1 } })
  check('STAFF·非本人处理人工单流转 8002', !denied.ok && denied.code === 8002, `code=${denied.code}`)
} else {
  check('STAFF·非本人处理人工单流转 8002', false, '种子数据中未找到可用样本')
}

/* ==================== 通知角标与跳转联动（原 #41–#45） ==================== */
const unreadBefore = await call('get', '/notifications/unread-count', { token: staffToken })
const staffNotifs = await call('get', '/notifications', { token: staffToken, params: { pageNum: 1, pageSize: 50 } })
const newestAssign = staffNotifs.data.list.find((n) => n.type === 1 && n.bizType === 'work_order' && n.bizId === openOrder.id)
check('通知·指派联动生成通知且未读', !!newestAssign && !newestAssign.isRead, newestAssign && `${newestAssign.title} ${newestAssign.content}`)
check(
  '通知·未读数与列表一致',
  staffNotifs.data.list.filter((n) => !n.isRead).length === unreadBefore.data.count,
  `${unreadBefore.data.count}`
)
const readOne = await call('put', `/notifications/${newestAssign.id}/read`, { token: staffToken })
const unreadAfter = await call('get', '/notifications/unread-count', { token: staffToken })
check(
  '通知·标记已读后未读 -1',
  readOne.ok && readOne.data.isRead && unreadAfter.data.count === unreadBefore.data.count - 1,
  `${unreadBefore.data.count} → ${unreadAfter.data.count}`
)
const unreadFilter = await call('get', '/notifications', { token: staffToken, params: { isRead: 0, pageNum: 1, pageSize: 50 } })
check('通知·未读 Tab 过滤生效', unreadFilter.ok && unreadFilter.data.list.every((n) => !n.isRead))
const readAll = await call('put', '/notifications/read-all', { token: staffToken })
const unreadZero = await call('get', '/notifications/unread-count', { token: staffToken })
check('通知·全部已读后角标为 0', readAll.ok && unreadZero.data.count === 0, `${unreadZero.data.count}`)

/* ==================== 重置演示数据（跨端共用；原 #46–#48） ==================== */
resetParcelStore()
resetDb()
check('重置·覆盖层已清空', overlay.get(demoParcel.id) === null)
check(
  '重置·包裹回到种子状态（在库待取）',
  queryParcels({ stationId: 1, status: 1, waybillNo: demoParcel.waybillNo, pageNum: 1, pageSize: 1 }).total === 1
)
const staleToken = await call('get', '/auth/me', { token: stationToken })
check('重置·旧 token 失效（强制重新登录）', !staleToken.ok && staleToken.code === 401, `code=${staleToken.code}`)

console.log(`\n员工路径 T13–T16 数据链路实测（apps/staff-h5）：通过 ${pass} / 失败 ${fail}`)
if (failures.length) console.log(`失败项：\n- ${failures.join('\n- ')}`)
process.exitCode = fail ? 1 : 0
