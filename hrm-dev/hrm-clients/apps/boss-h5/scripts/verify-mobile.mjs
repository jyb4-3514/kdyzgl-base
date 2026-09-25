/**
 * 管理端路径 T13–T16 移动端页面数据链路实测（`npm run verify:mobile`）· 管理端「驿站精灵」
 *
 * 来源：hrm-demo `scripts/verify-mobile-t13-t16.mjs`（先读后按「拆分前后断言清单对照表」取管理端归属子集，未改断言口径）。
 * 归属对照（test-cases.md 对照表 §二 / 主智能体裁定 2026-09-25）：管理端自有 12 条（原 #6–#16、#36）
 * + 跨端共用 8 条（原 #1–#5、#46–#48） = **20**（分端下限 20，两端合计 ≥48）。
 *
 * 与原脚本的差异（均非断言弱化）：
 * 1. 登录上报端类型改用新契约 `clientType=BOSS`（+ as=boss 兼容读），对齐新入口口径。
 * 2. 原脚本中 #6–#16 依赖 `openOrder`（由员工端流程造出的待处理工单）才能执行 #36 指派；
 *    本端不重跑员工端流程（那不是本端的断言），故 #36 直接从管理端可见的工单列表中取一条待处理工单再指派。
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
 * 管理端入口恒报 clientType=BOSS（另带 as=boss 兼容读）；否则会被 1110 拒绝。
 */
const login = async (username) => {
  const res = await call('post', '/auth/login', {
    data: { username, password: 'demo1234', clientType: 'BOSS', as: 'boss' }
  })
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

/* ==================== 管理端（ADMIN）：原 #6–#16 ==================== */
const adminToken = await login('admin')
check('管理端·登录', !!adminToken)

const globalSummary = await call('get', '/parcels/summary', { token: adminToken })
check(
  '管理端·全局包裹指标',
  globalSummary.ok && globalSummary.data.parcelTotal > 100000,
  globalSummary.ok ? JSON.stringify(globalSummary.data) : globalSummary.message
)

const trend7 = await call('get', '/parcels/trend', { token: adminToken, params: { days: 7 } })
const trend30 = await call('get', '/parcels/trend', { token: adminToken, params: { days: 30 } })
check('趋势·7 天返回 7 个点', trend7.ok && trend7.data.length === 7, `${trend7.ok && trend7.data.length}`)
check('趋势·30 天返回 30 个点', trend30.ok && trend30.data.length === 30, `${trend30.ok && trend30.data.length}`)
check(
  '趋势·点位含入库与取件字段',
  trend7.ok && trend7.data.every((p) => p.date && Number.isFinite(p.inbound) && Number.isFinite(p.pickup)),
  trend7.ok && trend7.data[0] && JSON.stringify(trend7.data[0])
)

const rankDefault = await call('get', '/parcels/ranking', { token: adminToken })
const rankPickup = await call('get', '/parcels/ranking', { token: adminToken, params: { sort: 'pickupRate' } })
const rankAbnormal = await call('get', '/parcels/ranking', { token: adminToken, params: { sort: 'abnormalRate' } })
const desc = (list, key) => list.every((item, i) => i === 0 || list[i - 1][key] >= item[key])
check(
  '排行·默认按包裹量降序（8 个驿站）',
  rankDefault.ok && rankDefault.data.length === 8 && desc(rankDefault.data, 'parcelTotal')
)
check(
  '排行·按取件率重排生效',
  rankPickup.ok &&
    desc(rankPickup.data, 'pickupRate') &&
    rankPickup.data[0].stationId !== rankDefault.data[0].stationId,
  rankPickup.ok ? rankPickup.data[0].stationName : rankPickup.message
)
check('排行·按异常率重排生效', rankAbnormal.ok && desc(rankAbnormal.data, 'abnormalRate'))

const slaPage = await call('get', '/work-orders', {
  token: adminToken,
  params: { overSla: '1', pageNum: 1, pageSize: 1 }
})
check(
  '预警·超 SLA 工单总数 6',
  slaPage.ok && slaPage.data.total === 6,
  slaPage.ok ? `${slaPage.data.total}` : slaPage.message
)
const failedPage = await call('get', '/sync-tasks', {
  token: adminToken,
  params: { status: 3, pageNum: 1, pageSize: 100 }
})
check(
  '预警·同步失败批次可查且带驿站名',
  failedPage.ok && failedPage.data.list.length >= 1 && !!failedPage.data.list[0].stationName,
  failedPage.ok ? `${failedPage.data.total} 条` : failedPage.message
)
const alertOverdue = await call('get', '/parcels', {
  token: adminToken,
  params: { status: 1, endTime: hoursAgo(48), pageNum: 1, pageSize: 100 }
})
check(
  '预警·超 48h 未取件接口可下钻（有 id 可跳详情）',
  alertOverdue.ok && alertOverdue.data.list.length === 100 && !!alertOverdue.data.list[0].id
)

/* ==================== 工单指派（原 #36，管理端动作） ==================== */
const adminOrders = await call('get', '/work-orders', { token: adminToken, params: { pageNum: 1, pageSize: 50 } })
const assignTarget = adminOrders.ok
  ? adminOrders.data.list.find((o) => o.status === 0 && !o.assigneeId)
  : null
const assign = assignTarget
  ? await call('put', `/work-orders/${assignTarget.id}/assign`, { token: adminToken, data: { assigneeId: 4 } })
  : { ok: false, code: 'NO_TARGET' }
check(
  '工单指派·ADMIN 指派给 st001_staff',
  assign.ok && assign.data.assigneeId === 4,
  assign.ok ? assign.data.assigneeName : `code=${assign.code}`
)

/* ==================== 重置演示数据（跨端共用；原 #46–#48） ==================== */
resetParcelStore()
resetDb()
check('重置·覆盖层已清空', overlay.get(demoPick.list[0].id) === null)
check(
  '重置·包裹回到种子状态（在库待取）',
  queryParcels({ stationId: 1, status: 1, waybillNo: demoPick.list[0].waybillNo, pageNum: 1, pageSize: 1 }).total === 1
)
const staleToken = await call('get', '/auth/me', { token: adminToken })
check('重置·旧 token 失效（强制重新登录）', !staleToken.ok && staleToken.code === 401, `code=${staleToken.code}`)

console.log(`\n管理端路径 T13–T16 数据链路实测（apps/boss-h5）：通过 ${pass} / 失败 ${fail}`)
if (failures.length) console.log(`失败项：\n- ${failures.join('\n- ')}`)
process.exitCode = fail ? 1 : 0
