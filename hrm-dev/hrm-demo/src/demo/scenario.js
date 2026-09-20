import { db, resetDb } from '@/shared/mock/db.js'
import { queryParcels, resetParcelStore } from '@/shared/mock/parcelStore.js'
import { resetLeaveStore } from '@/shared/mock/leaveStore.js'
import { resetClientLogStore } from '@/shared/mock/clientLogStore.js'
import { MOBILE_TOKEN_KEY, MOBILE_USER_KEY } from '@/shared/constants/storageKey.js'
import { hoursAgoParam, parseTime } from '@/shared/domain/time.js'

/**
 * 演示剧本预置与重置（T16）
 *
 * 剧本科目 4 项里有 3 项由 db.js 的确定性种子天然满足（1 条失败同步任务、6 条超时未处理工单、上千条超 48h 未取件），
 * 本模块不重复造这些数据，只做两件事：
 * 1) 按验收口径**核对**种子是否符合（不符合就在入口页显性标红，避免演示现场才发现）
 * 2) 挑一条「城东驿站 + 在库待取」的包裹作为**指定演示运单号**，写入 localStorage 供取件核销页一键填入
 *
 * 为什么放在 demo/：整个模块依赖 Mock 数据层，属演示资产；生产构建不打包本模块。
 */
// v2：需求 6 把「超 SLA」统一为「超时未处理」并同步字段名，旧缓存缺少新字段会渲染成 undefined，故升版令其失效重建
export const SCENARIO_KEY = 'hrm_demo_scenario_v2'
const OVERDUE_HOURS = 48
const DEMO_STATION_ID = 1 // 城东驿站：演示账号 st001_admin / st001_staff 的归属驿站

const hasStorage = () => typeof localStorage !== 'undefined'

/** 超时未处理判定与后端口径一致：仅统计待处理/处理中 */
const isOverdueUnhandled = (order) => {
  if (order.is_deleted !== 0 || (order.status !== 0 && order.status !== 1)) return false
  const deadline = parseTime(order.sla_deadline)
  return !!deadline && deadline < Date.now()
}

/** 生成（或重新生成）剧本并落 localStorage */
export function applyScenario() {
  const overdue = queryParcels({
    stationId: null,
    status: 1,
    endTime: hoursAgoParam(OVERDUE_HOURS),
    pageNum: 1,
    pageSize: 100
  })
  const demo = queryParcels({ stationId: DEMO_STATION_ID, status: 1, pageNum: 1, pageSize: 1 })
  const first = demo.list[0] || null

  const scenario = {
    failedSyncCount: db.syncTasks.filter((task) => task.status === 3).length,
    overdueUnhandledCount: db.workOrders.filter(isOverdueUnhandled).length,
    overdueCount: overdue.total,
    demoWaybillNo: first ? first.waybillNo : '',
    demoParcelId: first ? first.id : null,
    demoStationName: first ? first.stationName : ''
  }

  if (hasStorage()) localStorage.setItem(SCENARIO_KEY, JSON.stringify(scenario))
  return scenario
}

/** 读取剧本；本地没有（例如直接打开 mobile.html）时按需生成，保证取件页始终有演示运单号可用 */
export function readScenario() {
  if (hasStorage()) {
    try {
      const cached = JSON.parse(localStorage.getItem(SCENARIO_KEY) || 'null')
      if (cached && cached.demoWaybillNo) return cached
    } catch (e) {
      // 脏数据按未预置处理，下面重新生成
    }
  }
  return applyScenario()
}

/** 剧本自检结果：入口页按此渲染勾选项 */
export function checkScenario() {
  const data = readScenario()
  return {
    data,
    items: [
      { label: '失败同步任务（城东驿站昨日批次）', value: `${data.failedSyncCount} 条`, ok: data.failedSyncCount >= 1 },
      {
        label: '超时未处理工单（待处理/处理中）',
        value: `${data.overdueUnhandledCount} 条`,
        ok: data.overdueUnhandledCount >= 6
      },
      { label: `超 ${OVERDUE_HOURS}h 未取件包裹`, value: `${data.overdueCount} 件`, ok: data.overdueCount >= 10 },
      { label: '指定演示运单号', value: data.demoWaybillNo || '未生成', ok: !!data.demoWaybillNo }
    ]
  }
}

/**
 * 重置演示数据（入口页按钮）
 * 四件事：重建包裹索引并清空写操作覆盖层 → 重建种子实体（含请假与运行日志的持久化桶）并清会话
 * （旧 token 立即失效，强制重新登录）→ 清掉移动端登录态与本页剧本缓存后重新预置，使三端回到同一个初始态。
 */
export function resetDemoData() {
  resetParcelStore() // 内部已包含覆盖层（取件/异常）清空
  resetDb()
  // 请假与运行日志各自带持久化桶（createPersistBucket），不显式重建就会出现
  // 「点了重置、请假数据与日志还挂在界面上」的假重置 —— 这两个模块是跨刷新保留的业务数据
  resetLeaveStore()
  resetClientLogStore()
  if (hasStorage()) {
    localStorage.removeItem(MOBILE_TOKEN_KEY)
    localStorage.removeItem(MOBILE_USER_KEY)
    localStorage.removeItem(SCENARIO_KEY)
  }
  return checkScenario()
}
