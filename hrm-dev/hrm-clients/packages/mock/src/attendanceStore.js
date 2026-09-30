import { activeEmployees, db, stationName } from './db.js'
// 与 leaveStore 互引（它查排班算计薪天数、这里查已批请假天数算缺勤），
// 双向引用只发生在函数体内、无顶层取值，ESM 循环导入按惰性绑定解析，不存在初始化竞态。
import { approvedLeaveDays, isLeaveDeductEnabled } from './leaveStore.js'
import {
  addDays,
  clone,
  createRandom,
  formatDate,
  formatDateTime,
  mondayOf,
  paginate,
  randomInt,
  shiftDays
} from './util.js'
import { ATTENDANCE_CODE, CODE } from '@kdyzgl/shared/constants/errorCode.js'

/**
 * 考勤与排班数据层（T17）
 *
 * 为什么独立成模块而不并入 db.js：本模块含「打卡规则 / 班次 / 排班 / 打卡记录 / 补卡申请」五张表，
 * 且打卡判定（时间窗 + Haversine 围栏 + ALL/ANY 组合）是一套完整业务算法，
 * 与 db.js「纯种子实体」的定位不同。沿用 parcelStore.js 的「领域 store + 懒构建 + 内存写操作」写法，
 * 依赖方向单向（store → db），不反向引入循环依赖；db.resetDb() 不清本模块，
 * 重置演示数据时需由调用方一并调用 resetAttendanceStore()（与 resetParcelStore 同口径）。
 *
 * 时间基准：排班与打卡记录均以「今天」为锚点由固定种子 PRNG 生成，同一天内刷新结果稳定；
 * 跨天时数据相对新的一天重排（与 parcelStore 的「近 N 天」口径一致）。
 *
 * 时段真源统一（打卡时间真源 = 班次）：打卡时段 checkPeriods、上下班时间 workStartTime / workEndTime、
 * 频次 checkFrequency **不再是规则侧的独立配置**，而是读取时由「该驿站启用班次」实时派生的只读值
 * （见 attendancePeriodsOf）；规则侧即便提交这些字段也一律忽略（U-4 / U-5），
 * 保证「排班改哪里、打卡就跟哪里，永远一致」。打卡记录按 periodIndex + periodName 归属时段。
 *
 * TODO(扩展): 打卡/排班/规则的写操作目前只存活于当前会话，刷新即回到种子态。
 * 若演示需要「三端刷新后写操作仍在」，参照 overlay.js 增加考勤写操作覆盖层（localStorage + 版本化 KEY）。
 */

/** 演示数据归属驿站：排班表与打卡记录只投给城东驿站，避免 8 站满量数据拖慢首屏 */
const STATION_ID = 1
/** 地球平均半径（米），Haversine 公式与围栏半径同单位 */
const EARTH_RADIUS = 6371000
/** 上班卡最早可打时间：班次开始前 30 分钟 */
const OPEN_AHEAD_MIN = 30
/** 下班卡最晚可打时间：班次结束后 60 分钟 */
const CLOSE_DELAY_MIN = 60
/** 打卡回看窗口（近 30 天），排班窗口必须覆盖它 */
const SCHEDULE_WINDOW_DAYS = 30
/** 每 6 天安排 1 个休息日，保证「无排班」样本存在 */
const REST_CYCLE_DAYS = 6
/** 固定演示账号（st001_admin / st001_staff）：今日不预置打卡卡，留出移动端打卡演示入口 */
const DEMO_EMPLOYEE_IDS = [3, 4]
/** 电子围栏中心：示例市内虚构坐标（各站按 id 偏移约 1.2km，互不重叠），非真实地点 */
const FENCE_ORIGIN = { longitude: 117.201, latitude: 31.821 }

/**
 * 班次预设：早/中/晚三条记录，覆盖「正常出勤 / 跨零点收班 / 停用态」演示场景。
 * 配色全部取自 demo-ui-redesign.md 2.2 设计 Token：
 * 早班 #0958D9（blue-700）、中班 #FA8C16（orange-500）、晚班 #1F2937（neutral-800）
 * 晚班为何用中性深色：夜间班次的语义即「深色」，且该文档明确要求「删除紫色 AI 默认风」，
 * 原契约给的 #722ED1 不在 Token 色板内，故改用色板内的 neutral-800。
 *
 * 种子收敛（设计 ⑫-31）：中班 status=0（停用）。定义侧站点级校验要求「启用班次数 ≤2 且一早一晚」
 * （后端 9114），早/中/晚三班全启用会同时撞「超上限」与「ordinal 冲突」；保留三条记录而不删，
 * 是为了不打断依赖 shiftId 1/2/3 与站点 2/3/4 班次编号的既有排班 / 时段演示与断言。
 * 启用集合 = 早班（ordinal 0）+ 晚班（ordinal 1），正是一早一晚且互不重叠。
 */
const SHIFT_SEED = [
  { shiftName: '早班', startTime: '08:00', endTime: '16:00', color: '#0958D9', status: 1 },
  { shiftName: '中班', startTime: '12:00', endTime: '20:00', color: '#FA8C16', status: 0 },
  { shiftName: '晚班', startTime: '16:00', endTime: '24:00', color: '#1F2937', status: 1 }
]

/** 启用班次上限：打卡时段最多 2 段（一早一晚），超限服务端回 9114 */
const MAX_ENABLED_SHIFTS = 2
/** 午前 / 午后界值（分钟）：与后端 middayBoundaryMinute 默认值同口径（12:00） */
const MIDDAY_BOUNDARY_MIN = 12 * 60

const WEEK_DAYS = 7

/**
 * 站点班次计划：决定该驿站参与打卡的时段数（单班 1 段 / 双班 2 段）。
 * 为什么要显式列计划而不从「启用班次数」推：演示站点仍保留早/中/晚三班用于排班演示，
 * 而打卡时段真源要求「互不重叠、上限 2」（后端 9114）。故此处按站点声明段数，
 * 具体时段的时间与名称**全部取自班次种子**，mock 不再独立维护一套时段时间（消除时间分裂）。
 */
const DUAL_SHIFT_STATIONS = [2, 3]
/** 时间窗左右余量默认值：与旧单班次模型的「提前 30 分钟 / 延后 60 分钟」同口径，避免升级后打卡手感突变 */
const ALLOW_EARLY_MIN = 30
const ALLOW_LATE_MIN = 60

/* ==================== 通用时间 / 距离计算 ==================== */

/** 'HH:mm' → 当日分钟数；'24:00' 是收班时间的常见写法，按 1440 处理 */
export function minutesOfDay(text) {
  const [h, m] = String(text || '')
    .split(':')
    .map(Number)
  if (!Number.isFinite(h) || !Number.isFinite(m)) return NaN
  return h * 60 + m
}

/** 'YYYY-MM-DD' → 本地 0 点（避免 new Date('YYYY-MM-DD') 按 UTC 解析造成跨时区错位） */
function localDate(text) {
  const [y, m, d] = String(text).split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** 「日期 + 当日分钟数」→ 标准时间串，分钟数溢出（如 24:10）自动进位到次日 */
function timeAt(workDate, minutes) {
  const d = localDate(workDate)
  d.setMinutes(minutes)
  return formatDateTime(d)
}

/** 当日的 HH:mm（打卡判定的时间基准，只到分钟，与规则阈值口径一致） */
const clockOf = (date) => `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`

/** 当日分钟数 → 'HH:mm'；跨日溢出（如 07:00-30 或 23:00+60）按 00:00 / 24:00 截断，避免前端展示非法时刻 */
function clockOfMinutes(minutes) {
  const value = Math.min(24 * 60, Math.max(0, Number(minutes) || 0))
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
}

/**
 * Haversine 大圆距离（米）：围栏判定用真实球面距离而非经纬度差值，
 * 后者在高纬度或经度方向会产生明显误差（同一 0.001° 在两方向的米数并不相等）。
 */
export function haversine(lng1, lat1, lng2, lat2) {
  const toRad = (deg) => (deg * Math.PI) / 180
  const dLat = toRad(Number(lat2) - Number(lat1))
  const dLng = toRad(Number(lng2) - Number(lng1))
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(Number(lat1))) * Math.cos(toRad(Number(lat2))) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  return 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(a)))
}

/* ==================== 内存库与种子构建 ==================== */

let built = false
let rules = []
let shifts = []
let schedules = []
let records = []
let makeups = []
const seq = { rule: 0, shift: 0, schedule: 0, record: 0, makeup: 0 }

function ensureBuilt() {
  if (!built) build()
}

function build() {
  // 先建班次再建规则：规则里的打卡时段是「由该驿站班次派生」的只读值，派生时必须已有班次可查
  shifts = buildShifts()
  rules = buildRules()
  seq.rule = rules.length
  seq.shift = shifts.length
  seq.schedule = 0
  seq.record = 0
  const random = createRandom(0x51ed270b) // 独立种子：不与 db.js / parcelStore.js 的随机序列互相干扰
  buildSchedules()
  buildRecords(random)
  buildMakeups()
  built = true
}

/** 重建考勤数据（T16「重置演示数据」复用；采懒构建，下次查询时再生成） */
export function resetAttendanceStore() {
  built = false
  rules = []
  shifts = []
  schedules = []
  records = []
  makeups = []
}

function buildRules() {
  return db.stations.map((station) => ({
    id: station.id,
    ...ruleSeed(station.id),
    updateTime: formatDateTime(shiftDays(-200 + station.id, 9, 0, 0))
  }))
}

/** 规则默认项：新驿站首次配置时也走这里，避免从别的驿站复制出错误的 ssid 与围栏坐标 */
function ruleSeed(stationId) {
  const sid = Number(stationId)
  const station = db.stations.find((s) => s.id === sid)
  const offset = sid - 1
  // 打卡时段是派生值：直接取该驿站班次（不再独立维护时间），频次 = 时段数 × 2（U-4 只读派生）
  const periods = attendancePeriodsOf(sid)
  return {
    stationId: sid,
    ruleName: `${stationName(sid)}默认打卡规则`,
    enableWifi: true,
    enableLocation: true,
    enableTimeWindow: true,
    matchMode: 'ALL',
    // WiFi 白名单：ssid 与驿站编码绑定，演示时可走「正确 ssid / 错误 ssid」两条判定路径
    wifiList: [
      { ssid: `${station ? station.code : 'ST000'}-Express`, bssid: `AC:84:C6:00:00:${String(sid).padStart(2, '0')}` }
    ],
    longitude: Number((FENCE_ORIGIN.longitude + offset * 0.012).toFixed(6)),
    latitude: Number((FENCE_ORIGIN.latitude + (offset % 3) * 0.008).toFixed(6)),
    radius: 300,
    checkFrequency: periods.length * 2,
    checkPeriods: periods.map((p) => ({ name: p.name, startTime: p.startTime, endTime: p.endTime })),
    allowEarlyMin: ALLOW_EARLY_MIN,
    allowLateMin: ALLOW_LATE_MIN,
    workStartTime: periods.length ? periods[0].startTime : null,
    workEndTime: periods.length ? periods[periods.length - 1].endTime : null,
    lateThresholdMin: 30,
    earlyLeaveThresholdMin: 30,
    status: 1
  }
}

function buildShifts() {
  const list = []
  db.stations.forEach((station) => {
    SHIFT_SEED.forEach((seed) => {
      list.push({
        id: list.length + 1,
        stationId: station.id,
        shiftName: seed.shiftName,
        startTime: seed.startTime,
        endTime: seed.endTime,
        color: seed.color,
        restMinutes: 60,
        status: seed.status === undefined ? 1 : seed.status
      })
    })
  })
  return list
}

/**
 * 启用种子下标：只有启用班次才参与排班轮转（停用班次「不再被排班引用」，与设计 ⑭.3 一致）。
 * 早班（0）+ 晚班（2）即种子收敛后的启用集合。 */
const ENABLED_SEED_INDEXES = SHIFT_SEED.map((seed, index) => (seed.status === 0 ? -1 : index)).filter((i) => i >= 0)

/** 某驿站的第 k 条班次记录（k=0 早班 / 1 中班 / 2 晚班；编号口径保持不变以兼容既有排班/时段演示） */
const shiftIdOf = (stationId, k) => shifts[(stationId - 1) * SHIFT_SEED.length + k].id
const shiftById = (id) => shifts.find((s) => s.id === Number(id))

/** 演示账号今日班次：优先取「上班卡时间窗覆盖当前时刻」的启用班次，凌晨无班次覆盖时退回首个启用班次 */
function coveringShiftIndex() {
  const now = minutesOfDay(clockOf(new Date()))
  const hit = ENABLED_SEED_INDEXES.find(
    (i) => now >= minutesOfDay(SHIFT_SEED[i].startTime) - OPEN_AHEAD_MIN && now <= minutesOfDay(SHIFT_SEED[i].endTime)
  )
  return hit === undefined ? ENABLED_SEED_INDEXES[0] : hit
}

function weekDates(weekStart) {
  const dates = []
  for (let i = 0; i < WEEK_DAYS; i += 1) dates.push(formatDate(addDays(weekStart, i)))
  return dates
}

/**
 * 排班：只给城东驿站全员排。
 * 窗口取「近 30 天 + 本周剩余日期」而非仅本周：打卡记录必须逐条落在有排班的日子上，
 * 否则会出现「无排班却有打卡记录」的矛盾数据；多出的未来几天供排班表演示「未到日期也可排」。
 */
function buildSchedules() {
  const today = new Date()
  const todayText = formatDate(today)
  const first = addDays(today, -(SCHEDULE_WINDOW_DAYS - 1))
  // 归零到当日 0 点：否则循环末尾会因「当前时刻 > 周日 0 点」少算本周最后一天，排班表出现整列空白
  first.setHours(0, 0, 0, 0)
  const last = addDays(mondayOf(today), WEEK_DAYS - 1)
  const staff = activeEmployees().filter((e) => e.station_id === STATION_ID)
  const demoShiftIndex = coveringShiftIndex()

  let dayIndex = 0
  for (let day = new Date(first); day <= last; day = addDays(day, 1)) {
    const workDate = formatDate(day)
    const isToday = workDate === todayText
    staff.forEach((employee, employeeIndex) => {
      // 轮休按「员工序号 + 天序号」错峰：每天只休掉约 1/6 的人，避免整站同一天无排班
      // （整站同休会让「今日打卡记录/应到人数」在演示日为空，看板与打卡页直接失真）
      if (
        !DEMO_EMPLOYEE_IDS.includes(employee.id) &&
        (dayIndex + employeeIndex) % REST_CYCLE_DAYS === REST_CYCLE_DAYS - 1
      )
        return
      // 轮转排班：在启用班次间轮转（停用班次不参与），保证任意一天各启用班次都有人上，
      // 时间窗/迟到早退各分支都能取到样本
      const shiftIndex =
        isToday && DEMO_EMPLOYEE_IDS.includes(employee.id)
          ? demoShiftIndex
          : ENABLED_SEED_INDEXES[(employeeIndex + dayIndex) % ENABLED_SEED_INDEXES.length]
      seq.schedule += 1
      schedules.push({
        id: seq.schedule,
        stationId: STATION_ID,
        employeeId: employee.id,
        workDate,
        // 多班次：单日班次集合以 shiftIds 承载，shiftId 保留 = 首条（兼容旧读取方）
        shiftIds: [shiftIdOf(STATION_ID, shiftIndex)],
        shiftId: shiftIdOf(STATION_ID, shiftIndex),
        createTime: formatDateTime(addDays(day, -3))
      })
    })
    dayIndex += 1
  }
}

/** 单张卡的种子方案：正常约 62%、迟到/早退约 18%、校验异常约 5%、缺卡约 5% */
function cardPlan(random, checkType, rule) {
  const r = random()
  if (r < 0.05) return { kind: 'MISS' }
  if (r < 0.1) return { kind: 'ABNORMAL', offsetMin: randomInt(random, -10, 10) }
  if (checkType === 'ON') {
    if (r < 0.72) return { kind: 'NORMAL', offsetMin: randomInt(random, -10, 5) }
    // 迟到必须超过阈值才成立：偏移量从阈值之上取，保证种子里的 LATE 不是标签造假
    return { kind: 'LATE', offsetMin: rule.lateThresholdMin + randomInt(random, 1, 35) }
  }
  if (r < 0.72) return { kind: 'NORMAL', offsetMin: randomInt(random, -5, 10) }
  return { kind: 'EARLY_LEAVE', offsetMin: -(rule.earlyLeaveThresholdMin + randomInt(random, 1, 40)) }
}

/**
 * 打卡记录的校验字段：正常卡坐标取围栏中心附近的真实抖动（距离由 Haversine 反算，与接口口径一致），
 * 异常卡按「一半 WiFi 未命中、一半超出围栏」生成，便于异常预警与详情页演示两类失败原因。
 */
function verifyFields(random, plan, rule) {
  const center = { longitude: rule.longitude, latitude: rule.latitude }
  if (plan.kind === 'ABNORMAL') {
    const wifiMissed = random() < 0.5
    const longitude = wifiMissed ? center.longitude : center.longitude + 0.01
    const distance = Number(haversine(center.longitude, center.latitude, longitude, center.latitude).toFixed(1))
    return {
      checkMode: wifiMissed ? 'LOCATION' : 'WIFI',
      wifiSsid: wifiMissed ? 'Other-WiFi-5G' : rule.wifiList[0].ssid,
      wifiMatched: !wifiMissed,
      longitude,
      latitude: center.latitude,
      distance,
      locationMatched: !wifiMissed,
      remark: wifiMissed ? '演示异常卡：WiFi 未命中' : '演示异常卡：超出电子围栏'
    }
  }
  const longitude = Number((center.longitude + (random() - 0.5) * 0.002).toFixed(6))
  const latitude = Number((center.latitude + (random() - 0.5) * 0.002).toFixed(6))
  return {
    checkMode: 'WIFI+LOCATION',
    wifiSsid: rule.wifiList[0].ssid,
    wifiMatched: true,
    longitude,
    latitude,
    distance: Number(haversine(center.longitude, center.latitude, longitude, latitude).toFixed(1)),
    locationMatched: true,
    remark: null
  }
}

/**
 * 打卡时段（判定期真源）：由该驿站启用班次派生，mock 不再独立维护一套时段时间。
 * 取数规则：启用班次按开始时间升序 → 逐个纳入「与已选时段互不重叠」的班次 → 段数由站点班次计划封顶（≤2）。
 * periodIndex = 段序号（0 早 / 1 晚），name = 班次名，起止 = 班次起止（与后端「班次为唯一时间真源」同口径）。
 */
function attendancePeriodsOf(stationId) {
  const sid = Number(stationId)
  const limit = DUAL_SHIFT_STATIONS.includes(sid) ? 2 : 1
  const ordered = shifts
    .filter((s) => s.stationId === sid && s.status === 1)
    .slice()
    .sort((a, b) => minutesOfDay(a.startTime) - minutesOfDay(b.startTime))
  const chain = []
  for (const shift of ordered) {
    if (chain.length >= limit) break
    const cur = shiftInterval(shift)
    // 与已选时段重叠的班次跳过：periodIndex 定位与时间窗判定都要求时段互不重叠
    const overlap = chain.some((picked) => {
      const iv = shiftInterval(picked)
      return iv.start < cur.end && cur.start < iv.end
    })
    if (overlap) continue
    chain.push(shift)
  }
  return chain.map((shift, index) => ({
    periodIndex: index,
    name: shift.shiftName,
    startTime: shift.startTime,
    endTime: shift.endTime
  }))
}

/** 该驿站是否已配置启用班次：供 status 下发 shiftConfigured，前端据此渲染「无班次不可打卡」空态 */
export function stationShiftConfigured(stationId) {
  ensureBuilt()
  return shifts.some((s) => s.stationId === Number(stationId) && s.status === 1)
}

/**
 * 打卡记录：只在与排班对应的日子上产生，且今天只生成「时刻已到」的卡（防时序矛盾）
 *
 * 时段切分口径：记录必须与员工当天的排班自洽，故时段数量与名称取自「该驿站班次派生的打卡时段」，
 * 记录时间按员工当日排班班次均分——保证「排的哪一班、卡就落在对应的时段序号与名称上」。
 */
function buildRecords(random) {
  const todayText = formatDate(new Date())
  const nowMinutes = minutesOfDay(clockOf(new Date()))

  schedules.forEach((schedule) => {
    if (schedule.workDate > todayText) return // 未来排班不产生记录
    const isToday = schedule.workDate === todayText
    if (isToday && DEMO_EMPLOYEE_IDS.includes(schedule.employeeId)) return // 留出今日打卡演示入口

    const rule = rules.find((r) => r.stationId === schedule.stationId)
    const shift = shiftById(schedule.shiftId)
    const employee = db.employees.find((e) => e.id === schedule.employeeId)
    // 段数与段名取自「该驿站班次派生的打卡时段」，不再读规则里已废弃的 checkPeriods
    const periods = attendancePeriodsOf(schedule.stationId)
    const periodCount = Math.max(1, periods.length || 1)
    const startMin = minutesOfDay(shift.startTime)
    const span = (minutesOfDay(shift.endTime) - startMin) / periodCount

    for (let periodIndex = 0; periodIndex < periodCount; periodIndex += 1) {
      const periodName = (periods[periodIndex] && periods[periodIndex].name) || `第 ${periodIndex + 1} 段`
      const onBase = startMin + span * periodIndex
      const offBase = startMin + span * (periodIndex + 1)

      ;['ON', 'OFF'].forEach((checkType) => {
        const plan = cardPlan(random, checkType, rule)
        if (plan.kind === 'MISS') return // 缺卡：该类型不落记录，由概况的「缺卡」口径体现
        const cardMinutes = (checkType === 'ON' ? onBase : offBase) + plan.offsetMin
        if (isToday && cardMinutes > nowMinutes) return
        seq.record += 1
        records.push({
          id: seq.record,
          employeeId: schedule.employeeId,
          employeeName: employee ? employee.real_name : null,
          stationId: schedule.stationId,
          workDate: schedule.workDate,
          periodIndex,
          periodName,
          checkType,
          checkTime: timeAt(schedule.workDate, cardMinutes),
          status: plan.kind,
          source: 'NORMAL',
          ...verifyFields(random, plan, rule)
        })
      })
    }
  })
}

/* ==================== VO 组装 ==================== */

function toRuleVO(rule) {
  // 时段 / 上下班时间 / 频次一律实时由该驿站班次派生（只读），不再回显规则里已废弃的写入值
  const periods = attendancePeriodsOf(rule.stationId)
  return {
    id: rule.id,
    stationId: rule.stationId,
    stationName: stationName(rule.stationId),
    ruleName: rule.ruleName,
    enableWifi: rule.enableWifi,
    enableLocation: rule.enableLocation,
    enableTimeWindow: rule.enableTimeWindow,
    matchMode: rule.matchMode,
    wifiList: rule.wifiList.map((w) => ({ ...w })),
    longitude: rule.longitude,
    latitude: rule.latitude,
    radius: rule.radius,
    checkFrequency: periods.length * 2,
    checkPeriods: periods.map((p) => ({ name: p.name, startTime: p.startTime, endTime: p.endTime })),
    // 只读派生标记：时段与上下班时间由该驿站班次决定，规则侧不再可写（U-4 / U-5）
    checkPeriodsReadonly: true,
    allowEarlyMin: rule.allowEarlyMin,
    allowLateMin: rule.allowLateMin,
    workStartTime: periods.length ? periods[0].startTime : null,
    workEndTime: periods.length ? periods[periods.length - 1].endTime : null,
    lateThresholdMin: rule.lateThresholdMin,
    earlyLeaveThresholdMin: rule.earlyLeaveThresholdMin,
    status: rule.status,
    updateTime: rule.updateTime
  }
}

function toShiftVO(shift) {
  return {
    id: shift.id,
    stationId: shift.stationId,
    stationName: stationName(shift.stationId),
    shiftName: shift.shiftName,
    startTime: shift.startTime,
    endTime: shift.endTime,
    color: shift.color,
    restMinutes: shift.restMinutes,
    status: shift.status
  }
}

/**
 * 未排班时的兜底班次：取该驿站首个启用班次（不再按规则上下班时间合成）。
 * 无启用班次时返回 null —— 此时站点不具备打卡时间基准，由调用方按「未配置班次」给出空态/9113。
 */
function defaultShiftOf(rule) {
  const first = shifts
    .filter((s) => s.stationId === rule.stationId && s.status === 1)
    .slice()
    .sort((a, b) => minutesOfDay(a.startTime) - minutesOfDay(b.startTime))[0]
  if (!first) return null
  return {
    id: null,
    stationId: rule.stationId,
    stationName: stationName(rule.stationId),
    shiftName: first.shiftName,
    startTime: first.startTime,
    endTime: first.endTime,
    color: first.color,
    restMinutes: first.restMinutes,
    status: 1
  }
}

/* ==================== 打卡规则 ==================== */

/**
 * 可写字段白名单：时段相关（checkPeriods / checkFrequency / workStartTime / workEndTime）已改为
 * 由班次派生（只读），故不在可写集内——即便请求体带上也会被忽略（U-4 / U-5），
 * 防止出现「管理员以为改了时段、实际被派生值覆盖」的静默失效。
 */
const RULE_WRITABLE = [
  'ruleName',
  'enableWifi',
  'enableLocation',
  'enableTimeWindow',
  'matchMode',
  'wifiList',
  'longitude',
  'latitude',
  'radius',
  'allowEarlyMin',
  'allowLateMin',
  'lateThresholdMin',
  'earlyLeaveThresholdMin',
  'status'
]

export function findRule(stationId) {
  ensureBuilt()
  const rule = rules.find((r) => r.stationId === Number(stationId))
  return rule ? toRuleVO(rule) : null
}

export function listRules() {
  ensureBuilt()
  return rules.map(toRuleVO)
}

/** 保存规则：按驿站覆盖式保存，未配置的驿站首存即创建（新驿站建完可直接配规则） */
export function saveRule(stationId, payload = {}) {
  ensureBuilt()
  const sid = Number(stationId)
  let rule = rules.find((r) => r.stationId === sid)
  if (!rule) {
    seq.rule += 1
    rule = { id: seq.rule, ...ruleSeed(sid), updateTime: formatDateTime(new Date()) }
    rules.push(rule)
  }
  // 白名单写入：请求体的 id / stationId / updateTime 一律不接受，防止越权改归属
  RULE_WRITABLE.forEach((key) => {
    if (payload[key] !== undefined) rule[key] = payload[key]
  })
  rule.updateTime = formatDateTime(new Date())
  return toRuleVO(rule)
}

/* ==================== 班次 ==================== */

export function listShifts(stationId) {
  ensureBuilt()
  return shifts
    .filter((s) => s.stationId === Number(stationId))
    .sort((a, b) => minutesOfDay(a.startTime) - minutesOfDay(b.startTime))
    .map(toShiftVO)
}

export function createShift({ stationId, shiftName, startTime, endTime, color, restMinutes, status = 1 }) {
  ensureBuilt()
  seq.shift += 1
  const shift = {
    id: seq.shift,
    stationId: Number(stationId),
    shiftName,
    startTime,
    endTime,
    color,
    restMinutes,
    status
  }
  shifts.push(shift)
  return toShiftVO(shift)
}

export function updateShift(id, patch) {
  ensureBuilt()
  const shift = shiftById(id)
  if (!shift) return null
  Object.assign(shift, patch)
  return toShiftVO(shift)
}

/** 班次开始时间落点：午前(0) / 午后(1)，与后端 ordinal 口径一致（一早一晚才能区分打卡时段） */
const ordinalOfShift = (startTime) => (minutesOfDay(startTime) < MIDDAY_BOUNDARY_MIN ? 0 : 1)

/**
 * 班次定义侧站点级校验（validateShiftSet，违反回 9114）：与后端 AttendanceShiftServiceImpl 同口径。
 * - 启用班次数（含本次，编辑时排除被编辑自身）≤ MAX_ENABLED_SHIFTS；
 * - 启用班次 ordinal（一早一晚）须互异。
 * 停用（status=0）班次不计入；单条字段级校验（名称/时间/颜色）由路由层 validateShift 承担。
 * @returns {null | { code:number, message:string }}
 */
export function validateShiftSet({ shiftId = null, shift }) {
  ensureBuilt()
  const id = shiftId == null ? null : Number(shiftId)
  const existing = id == null ? null : shiftById(id)
  const sid = existing ? existing.stationId : Number(shift && shift.stationId)
  const others = shifts.filter((s) => s.stationId === sid && s.status === 1 && s.id !== id)
  const nextEnabled = shift && Number(shift.status) === 1 ? others.concat([shift]) : others
  if (nextEnabled.length > MAX_ENABLED_SHIFTS) {
    return {
      code: ATTENDANCE_CODE.SHIFT_DEFINITION_INVALID,
      message: `启用班次数不能超过 ${MAX_ENABLED_SHIFTS} 个（当前将达 ${nextEnabled.length} 个），请先停用多余班次`
    }
  }
  const ordinals = nextEnabled.map((s) => ordinalOfShift(s.startTime))
  if (new Set(ordinals).size !== ordinals.length) {
    return {
      code: ATTENDANCE_CODE.SHIFT_DEFINITION_INVALID,
      message: '启用班次需一早一晚（存在同落午前/午后的班次，打卡时段无法区分）'
    }
  }
  return null
}

/** 删除班次：被排班引用时拒绝（删掉会让历史排班指向空班次，打卡判定失去时间基准） */
export function removeShift(id) {
  ensureBuilt()
  const index = shifts.findIndex((s) => s.id === Number(id))
  if (index < 0) return false
  if (schedules.some((s) => shiftIdsOf(s).includes(Number(id)))) return 'IN_USE'
  shifts.splice(index, 1)
  return true
}

/* ==================== 排班 ==================== */

/** 单日班次上限（架构 A-② 裁定 = 2，与计薪序号编码 epochDay×2+ordinal 绑定） */
const MAX_SHIFTS_PER_DAY = 2

/** 某排班行的班次集合：多班次以 shiftIds 承载，历史行回落到单值 shiftId */
function shiftIdsOf(schedule) {
  if (!schedule) return []
  if (Array.isArray(schedule.shiftIds)) return schedule.shiftIds.slice()
  return schedule.shiftId == null ? [] : [schedule.shiftId]
}

/** 班次集合按开始时间升序（展示与「首条」口径一致：最早班次排在前） */
function orderedShiftIds(schedule) {
  return shiftIdsOf(schedule).sort((a, b) => {
    const sa = shiftById(a)
    const sb = shiftById(b)
    return (sa ? minutesOfDay(sa.startTime) : 0) - (sb ? minutesOfDay(sb.startTime) : 0)
  })
}

/** 班次区间（半开 [start, end)，00:00 作为 24:00 收班时分钟数为 1440），用于重叠判定 */
function shiftInterval(shift) {
  return { start: minutesOfDay(shift.startTime), end: minutesOfDay(shift.endTime) }
}

/** 集合内是否存在时间重叠（重叠判定口径由架构 §2.4 定为半开区间 [s,e)） */
function hasOverlap(ids) {
  const intervals = ids
    .map((id) => shiftById(id))
    .filter(Boolean)
    .map(shiftInterval)
  for (let i = 0; i < intervals.length; i += 1) {
    for (let j = i + 1; j < intervals.length; j += 1) {
      if (intervals[i].start < intervals[j].end && intervals[j].start < intervals[i].end) return true
    }
  }
  return false
}

/** 目标班次集合逐个校验：存在 / 同驿站 / 启用（否则 9106，与单值口径一致） */
function validateShiftTarget(stationId, ids) {
  for (const id of ids) {
    const shift = shiftById(id)
    if (!shift || shift.stationId !== stationId || shift.status !== 1) return { code: ATTENDANCE_CODE.SHIFT_UNAVAILABLE }
  }
  return null
}

/** 按周查询排班矩阵：7 天 × 员工，供 PC 排班表直接铺表格 */
export function querySchedules({ stationId, weekStart }) {
  ensureBuilt()
  const sid = Number(stationId)
  const start = mondayOf(weekStart ? localDate(weekStart) : new Date())
  const dates = weekDates(start)
  const rowOf = (employeeId, workDate) => schedules.find((s) => s.employeeId === employeeId && s.workDate === workDate)
  const employees = activeEmployees()
    .filter((e) => e.station_id === sid)
    .map((employee) => ({
      employeeId: employee.id,
      employeeName: employee.real_name,
      days: dates.map((workDate) => {
        const schedule = rowOf(employee.id, workDate)
        const ids = orderedShiftIds(schedule)
        return {
          workDate,
          scheduleId: schedule ? schedule.id : null,
          // shiftId 保留 = 首条（最早班次），新增 shiftIds = 当日班次集合（只增不减，向后兼容）
          shiftId: ids.length ? ids[0] : null,
          shiftIds: ids
        }
      })
    }))
  return { weekStart: dates[0], weekEnd: dates[WEEK_DAYS - 1], dates, shifts: listShifts(sid), employees }
}

/** 我的排班（员工端）：按周返回本人 7 天的班次明细；shifts 为当日班次数组，旧字段保留 = 首条 */
export function mySchedules(employeeId, weekStart) {
  ensureBuilt()
  const eid = Number(employeeId)
  const start = mondayOf(weekStart ? localDate(weekStart) : new Date())
  const dates = weekDates(start)
  const list = dates.map((workDate) => {
    const schedule = schedules.find((s) => s.employeeId === eid && s.workDate === workDate)
    const ids = orderedShiftIds(schedule)
    const first = ids.length ? shiftById(ids[0]) : null
    const shifts = ids
      .map((id) => shiftById(id))
      .filter(Boolean)
      .map((shift) => ({
        scheduleId: schedule ? schedule.id : null,
        shiftId: shift.id,
        shiftName: shift.shiftName,
        startTime: shift.startTime,
        endTime: shift.endTime,
        color: shift.color,
        restMinutes: shift.restMinutes
      }))
    return {
      workDate,
      scheduleId: schedule ? schedule.id : null,
      shiftId: first ? first.id : null,
      shiftName: first ? first.shiftName : null,
      startTime: first ? first.startTime : null,
      endTime: first ? first.endTime : null,
      color: first ? first.color : null,
      restMinutes: first ? first.restMinutes : null,
      shifts
    }
  })
  return { weekStart: dates[0], weekEnd: dates[WEEK_DAYS - 1], dates, list }
}

/**
 * 批量保存排班（多班次）：唯一性 = employeeId + workDate（一格一条集合）。
 * - 新契约：item.shiftIds 存在即为准（含空数组 = 清空该天）；该天目标集合**整体覆盖**当前集合（A-④）。
 *   返回 saved = 覆盖后班次行数（|T|），removed = 本次被移除的班次行数（|C\T|），与架构 §2.4.1 ARCH-C-2b 一致。
 * - 旧契约：仅传单值 shiftId 时保持旧语义（saved∈{0,1}、removed∈{0,1}），与旧客户端可观察等价。
 * - 校验：存在 / 同驿站 / 启用（9106）；单日上限 2；时间重叠拒绝（半开区间 [s,e)）；重复静默去重。
 */
export function saveSchedules({ stationId, items }) {
  ensureBuilt()
  const sid = Number(stationId)
  const result = { saved: 0, removed: 0 }
  const employees = activeEmployees()

  for (const item of items) {
    const employeeId = Number(item.employeeId)
    const workDate = String(item.workDate || '')
    const employee = employees.find((e) => e.id === employeeId)
    if (!employee || employee.station_id !== sid) return { code: 400, message: `员工 ${employeeId} 不属于该驿站` }

    const index = schedules.findIndex((s) => s.employeeId === employeeId && s.workDate === workDate)
    const current = shiftIdsOf(index >= 0 ? schedules[index] : null)

    if (Array.isArray(item.shiftIds)) {
      // 集合语义：去重（重复静默去重）→ 上限 → 逐个存在性 → 重叠
      const target = [...new Set(item.shiftIds.map(Number))].filter((id) => Number.isFinite(id))
      if (target.length > MAX_SHIFTS_PER_DAY)
        return { code: 400, message: `单日最多选择 ${MAX_SHIFTS_PER_DAY} 个班次` }
      const invalid = validateShiftTarget(sid, target)
      if (invalid) return invalid
      if (hasOverlap(target)) return { code: 400, message: '所选班次时间有重叠，请调整' }

      if (!target.length) {
        if (index >= 0) {
          schedules.splice(index, 1)
          result.removed += current.length
        }
        continue
      }
      if (index >= 0) {
        schedules[index].shiftIds = target
        schedules[index].shiftId = target[0]
      } else {
        seq.schedule += 1
        schedules.push({
          id: seq.schedule,
          stationId: sid,
          employeeId,
          workDate,
          shiftIds: target,
          shiftId: target[0],
          createTime: formatDateTime(new Date())
        })
      }
      result.saved += target.length
      result.removed += current.filter((id) => !target.includes(id)).length
      continue
    }

    // 旧契约（单值）：shiftId 传空表示清空该天排班
    if (item.shiftId == null || item.shiftId === '') {
      if (index >= 0) {
        schedules.splice(index, 1)
        result.removed += 1
      }
      continue
    }
    const shift = shiftById(item.shiftId)
    if (!shift || shift.stationId !== sid || shift.status !== 1) return { code: ATTENDANCE_CODE.SHIFT_UNAVAILABLE }
    if (index >= 0) {
      schedules[index].shiftIds = [shift.id]
      schedules[index].shiftId = shift.id
    } else {
      seq.schedule += 1
      schedules.push({
        id: seq.schedule,
        stationId: sid,
        employeeId,
        workDate,
        shiftIds: [shift.id],
        shiftId: shift.id,
        createTime: formatDateTime(new Date())
      })
    }
    result.saved += 1
  }
  return { code: 200, data: result }
}

/**
 * 按驿站批量排班（需求2）：一次把「某驿站全部在职员工 × 日期范围」铺上同一班次，降低排班操作频次。
 * - employeeIds 缺省 = 该驿站全部在职员工（status=1）
 * - weekdays 缺省 = 日期范围内每天；传入则只保留命中星期的日期（0=周日 … 6=周六，与 JS getDay 一致）
 * - skipExisting 默认 true：已存在排班的「人 + 日」组合直接跳过，不覆盖；置 false 时覆盖为本次班次
 * 返回 { created, skipped, total }，total = 参与判定的「人 × 日」组合数 = created + skipped。
 * 铺排为**覆盖式**（该格 = [所选班次]），不追加多班次（架构 A-④：不开放追加模式）。
 */
export function saveSchedulesByStation({
  stationId,
  shiftId,
  startDate,
  endDate,
  employeeIds,
  skipExisting = true,
  weekdays
}) {
  ensureBuilt()
  const sid = Number(stationId)
  const shift = shiftById(shiftId)
  if (!shift || shift.stationId !== sid || shift.status !== 1) return { code: ATTENDANCE_CODE.SHIFT_UNAVAILABLE }

  const idFilter = Array.isArray(employeeIds) && employeeIds.length ? new Set(employeeIds.map(Number)) : null
  const staff = activeEmployees().filter(
    (e) => e.station_id === sid && e.status === 1 && (!idFilter || idFilter.has(e.id))
  )
  const dayFilter = Array.isArray(weekdays) && weekdays.length ? new Set(weekdays.map(Number)) : null
  const dates = []
  for (let day = localDate(startDate); formatDate(day) <= endDate; day = addDays(day, 1)) {
    if (dayFilter && !dayFilter.has(day.getDay())) continue
    dates.push(formatDate(day))
  }

  const result = { created: 0, skipped: 0, total: 0 }
  staff.forEach((employee) => {
    dates.forEach((workDate) => {
      result.total += 1
      const index = schedules.findIndex((s) => s.employeeId === employee.id && s.workDate === workDate)
      if (index >= 0 && skipExisting) {
        result.skipped += 1
        return
      }
      if (index >= 0) {
        schedules[index].shiftIds = [shift.id]
        schedules[index].shiftId = shift.id
      } else {
        seq.schedule += 1
        schedules.push({
          id: seq.schedule,
          stationId: sid,
          employeeId: employee.id,
          workDate,
          shiftIds: [shift.id],
          shiftId: shift.id,
          createTime: formatDateTime(new Date())
        })
      }
      result.created += 1
    })
  })
  return { code: 200, data: result }
}

/* ==================== 打卡记录 ==================== */

/** 记录筛选与排序：列表、导出共用一套口径，避免「导出数据与页面筛选结果对不上」 */
function filterRecords({ stationId, employeeId, status, startDate, endDate }) {
  let rows = records
  if (stationId != null && stationId !== '') rows = rows.filter((r) => r.stationId === Number(stationId))
  if (employeeId != null && employeeId !== '') rows = rows.filter((r) => r.employeeId === Number(employeeId))
  if (status) rows = rows.filter((r) => r.status === status)
  if (startDate) rows = rows.filter((r) => r.workDate >= String(startDate))
  if (endDate) rows = rows.filter((r) => r.workDate <= String(endDate))
  return rows.slice().sort((a, b) => (a.checkTime < b.checkTime ? 1 : -1))
}

export function queryRecords({ stationId, employeeId, status, startDate, endDate, pageNum, pageSize }) {
  ensureBuilt()
  const page = paginate(filterRecords({ stationId, employeeId, status, startDate, endDate }), pageNum, pageSize)
  page.list = page.list.map((r) => clone(r))
  return page
}

/** 导出：同一套筛选口径，不分页（走文件流，不做 LIMIT 截断） */
export function exportRecords(filters) {
  ensureBuilt()
  return filterRecords(filters).map((r) => clone(r))
}

/**
 * 出勤口径的公共取数：概况与明细共用，杜绝「明细人数与概况对不上」。
 * 口径（唯一真源，api.md §4.6.8）：应到 = 当天排班班次数（一天两班计 2）；有效卡 = 非 ABNORMAL。
 * 缺卡按**班次粒度**（absentGranularity=PER_SHIFT）：某班次无匹配有效上班卡即缺。
 * 单班次站点行为与改造前逐项一致；判定表达式只此一处，改口径必同时影响概况与明细。
 */
function attendanceScope(workDate, sid) {
  const inScope = (row) => row.workDate === workDate && (sid == null || row.stationId === sid)
  return {
    shouldRows: schedules.filter(inScope),
    onCards: records.filter((r) => inScope(r) && r.status !== 'ABNORMAL' && r.checkType === 'ON'),
    offCards: records.filter((r) => inScope(r) && r.status !== 'ABNORMAL' && r.checkType === 'OFF')
  }
}

/** 应到「班次行」：某排班行的每个班次算一行（应到 / 缺卡均按此粒度） */
function shouldShiftRows(shouldRows) {
  const rows = []
  shouldRows.forEach((schedule) => {
    orderedShiftIds(schedule).forEach((id) => {
      const shift = shiftById(id)
      if (shift) rows.push({ employeeId: schedule.employeeId, shiftId: id, shift })
    })
  })
  return rows
}

const pairKey = (employeeId, shiftId) => `${employeeId}|${shiftId}`

/**
 * 有效卡映射到「该员工当日的排班班次」：卡时间距哪个班次开始最近即认到该班（一卡只计一个班）。
 * 单班次场景必然命中唯一班次，行为与改造前一致；无排班（不在应到集合）的卡不入 A∩R。
 */
function mapCardsToShifts(cards, pairs) {
  const rows = []
  cards.forEach((card) => {
    const candidates = pairs.filter((p) => p.employeeId === card.employeeId)
    if (!candidates.length) return
    const at = minutesOfDay(String(card.checkTime).slice(11, 16))
    let hit = candidates[0]
    let gap = Math.abs(minutesOfDay(hit.shift.startTime) - at)
    candidates.forEach((c) => {
      const g = Math.abs(minutesOfDay(c.shift.startTime) - at)
      if (g < gap) {
        gap = g
        hit = c
      }
    })
    rows.push({ employeeId: card.employeeId, shiftId: hit.shiftId, shift: hit.shift, card })
  })
  return rows
}

/**
 * 打卡概况（api.md §4.6.8）：应到 = 当天排班班次数；实到 = 有效上班卡映射到班次后与应到取交 |A∩R|。
 * 异常卡（校验未通过）不计入实到与正常/迟到/早退，避免「校验没通过也算出勤」。
 */
export function attendanceSummary(stationId, date) {
  ensureBuilt()
  const workDate = date || formatDate(new Date())
  const sid = stationId == null || stationId === '' ? null : Number(stationId)
  const scope = attendanceScope(workDate, sid)
  const pairs = shouldShiftRows(scope.shouldRows)
  const onRows = mapCardsToShifts(scope.onCards, pairs)
  const offRows = mapCardsToShifts(scope.offCards, pairs)
  const attended = new Set(onRows.map((r) => pairKey(r.employeeId, r.shiftId)))
  const actualCount = pairs.filter((p) => attended.has(pairKey(p.employeeId, p.shiftId))).length
  return {
    date: workDate,
    shouldCount: pairs.length,
    actualCount,
    normalCount: onRows.filter((r) => r.card.status === 'NORMAL').length,
    lateCount: onRows.filter((r) => r.card.status === 'LATE').length,
    earlyLeaveCount: offRows.filter((r) => r.card.status === 'EARLY_LEAVE').length,
    absentCount: Math.max(0, pairs.length - actualCount)
  }
}

/** 明细维度白名单：六个码与 attendanceSummary 的六个计数字段一一对应 */
export const ATTENDANCE_DETAIL_DIMS = ['SHOULD', 'ACTUAL', 'NORMAL', 'LATE', 'EARLY_LEAVE', 'ABSENT']

/** 到达态优先级：无有效上班卡=缺卡 > 迟到 > 早退（到达后签退）> 正常，与员工端 dayStatusOf 同序（除异常） */
const dayStateOf = (on, off) => {
  if (!on) return 'MISS'
  if (on.status === 'LATE') return 'LATE'
  if (off && off.status === 'EARLY_LEAVE') return 'EARLY_LEAVE'
  return 'NORMAL'
}

/**
 * 每维度的「班次行」来源：只决定哪些（员工 × 班次）在名单里，行内容统一由 buildDetailRow 补齐。
 * 缺卡为班次粒度（api.md §4.6.9）：同一员工某班次无匹配有效上班卡即出一行，多班次站点可出多行。
 */
function detailMembers(dim, scope) {
  const pairs = shouldShiftRows(scope.shouldRows)
  const onRows = mapCardsToShifts(scope.onCards, pairs)
  const offRows = mapCardsToShifts(scope.offCards, pairs)
  const attended = new Set(onRows.map((r) => pairKey(r.employeeId, r.shiftId)))
  const cardOfPair = (rows, employeeId, shiftId) =>
    rows.find((r) => r.employeeId === employeeId && r.shiftId === shiftId) || null
  const member = (employeeId, shift, onRow, offRow) => ({
    employeeId,
    shift,
    onCard: onRow ? onRow.card : null,
    offCard: offRow ? offRow.card : null
  })
  if (dim === 'SHOULD')
    return pairs.map((p) =>
      member(p.employeeId, p.shift, cardOfPair(onRows, p.employeeId, p.shiftId), cardOfPair(offRows, p.employeeId, p.shiftId))
    )
  // 缺卡是「应到差集」而非打卡事实：只标该班次无有效上班卡，不承载任何打卡时间
  // （与既有断言「缺卡明细每行无任何打卡时间」一致）
  if (dim === 'ABSENT')
    return pairs
      .filter((p) => !attended.has(pairKey(p.employeeId, p.shiftId)))
      .map((p) => member(p.employeeId, p.shift, null, null))
  if (dim === 'ACTUAL') {
    // 同一（员工 × 班次）只出一行：多时段同班多次上班卡去重
    const seen = new Set()
    return onRows
      .filter((r) => {
        const key = pairKey(r.employeeId, r.shiftId)
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
      .map((r) => member(r.employeeId, r.shift, r, cardOfPair(offRows, r.employeeId, r.shiftId)))
  }
  if (dim === 'NORMAL' || dim === 'LATE')
    return onRows
      .filter((r) => r.card.status === dim)
      .map((r) => member(r.employeeId, r.shift, r, cardOfPair(offRows, r.employeeId, r.shiftId)))
  if (dim === 'EARLY_LEAVE')
    return offRows
      .filter((r) => r.card.status === 'EARLY_LEAVE')
      .map((r) => member(r.employeeId, r.shift, cardOfPair(onRows, r.employeeId, r.shiftId), r))
  return []
}

/** 组装明细行（§4.6.9 契约）：一个「员工 × 班次」+ 当天在该维度的事实，六个维度结构一致 */
function buildDetailRow({ employeeId, shift, onCard, offCard }) {
  const employee = db.employees.find((e) => e.id === employeeId)
  const on = onCard || null
  const off = offCard || null
  const representative = on || off
  return {
    employeeId,
    employeeName: (employee && employee.real_name) || `员工 #${employeeId}`,
    stationId: employee ? employee.station_id : null,
    stationName: employee ? stationName(employee.station_id) || '' : '',
    shiftName: shift ? shift.shiftName : null,
    periodName: representative ? representative.periodName : null,
    onCheck: on ? { time: on.checkTime, status: on.status } : null,
    offCheck: off ? { time: off.checkTime, status: off.status } : null,
    dayState: dayStateOf(on, off),
    remark: representative ? representative.remark || null : null
  }
}

/** 排序（§4.6.9）：迟到/早退按命中卡时间倒序，缺卡按姓名，应到按风险优先，实到/正常按上班卡时间倒序 */
function sortDetailRows(dim, rows) {
  const byName = (a, b) => a.employeeName.localeCompare(b.employeeName, 'zh')
  const hitTime = (row) => (row.onCheck ? row.onCheck.time : row.offCheck ? row.offCheck.time : '')
  const byTimeDesc = (a, b) => (hitTime(a) < hitTime(b) ? 1 : -1)
  if (dim === 'ABSENT') rows.sort(byName)
  else if (dim === 'SHOULD') {
    const risk = (row) => (row.dayState === 'MISS' ? 0 : row.dayState === 'LATE' ? 1 : 2)
    rows.sort((a, b) => risk(a) - risk(b) || byName(a, b))
  } else rows.sort(byTimeDesc)
}

/**
 * 考勤明细：按维度返回「人 + 当天在该维度的事实」名单。
 * 口径与 attendanceSummary 完全同源（共用 attendanceScope），不在展示层组合排班与记录；
 * 缺卡 = 应到差集实到（不是异常卡），异常卡六个维度都不承载。
 */
export function attendanceDetail({ dim, stationId, date }) {
  ensureBuilt()
  const workDate = date || formatDate(new Date())
  const sid = stationId == null || stationId === '' ? null : Number(stationId)
  const scope = attendanceScope(workDate, sid)
  const list = detailMembers(dim, scope).map(buildDetailRow)
  sortDetailRows(dim, list)
  return { dim, date: workDate, total: list.length, list }
}

/**
 * 单员工考勤统计（工资单 ATTENDANCE 规则项的唯一数据来源）
 * 为什么收在这里而不是财务域自己筛记录：缺勤口径依赖排班（应到 = 有排班的天数），
 * 这套口径只有考勤域持有；散到财务域重写一遍必然与 attendanceSummary 的两个口径打架。
 * 口径与 attendanceSummary 完全一致：异常卡不计入迟到/早退/实到。
 *
 * M11 D3 请假联动（两处必须同版：本函数 + pc/utils/payrollPreview.js 的 loadAttendanceStat）：
 *   已批请假天数 = 该区间内 APPROVED 请假单的计薪天数（跨月单由调用方按月区间调用天然切分）；
 *   leaveDeductEnabled = true  → 请假按缺勤计（扣款），缺勤不减请假天数；
 *   leaveDeductEnabled = false → 默认，请假不扣，缺勤中剔除已批请假天数。
 *   注意这里的「true = 扣款」是开关名的字面语义（leaveDeductEnabled = 请假扣款已启用），
 *   与设计规范 §5.4 一致；前端展示层不要用「true = 不扣」的反向解释。
 */
export function employeeAttendanceStat(employeeId, startDate, endDate) {
  ensureBuilt()
  const eid = Number(employeeId)
  const inRange = (workDate) =>
    (!startDate || workDate >= String(startDate)) && (!endDate || workDate <= String(endDate))
  const rows = records.filter((r) => r.employeeId === eid && inRange(r.workDate))
  const valid = rows.filter((r) => r.status !== 'ABNORMAL')
  const scheduledDates = new Set(
    schedules.filter((s) => s.employeeId === eid && inRange(s.workDate)).map((s) => s.workDate)
  )
  const attendedDates = new Set(valid.filter((r) => r.checkType === 'ON').map((r) => r.workDate))
  const leaveDays = approvedLeaveDays(eid, startDate, endDate)
  const excludeLeaveDays = isLeaveDeductEnabled() ? 0 : leaveDays
  return {
    lateCount: valid.filter((r) => r.checkType === 'ON' && r.status === 'LATE').length,
    earlyLeaveCount: valid.filter((r) => r.checkType === 'OFF' && r.status === 'EARLY_LEAVE').length,
    abnormalCount: rows.filter((r) => r.status === 'ABNORMAL').length,
    scheduledDays: scheduledDates.size,
    attendedDays: attendedDates.size,
    leaveCount: leaveDays,
    absentCount: Math.max(0, scheduledDates.size - attendedDates.size - excludeLeaveDays)
  }
}

/**
 * 某员工区间内「有排班」的日期集合（升序数组）
 * 为什么单独开一个读口：请假模块的计薪天数必须逐日查排班真源（设计规范 §4.3.2），
 * 而排班真源只能由考勤域持有；开放读口而不是让请假模块直接读 schedules 数组，
 * 避免排班出现第二个入口（与「一期资产不动、口径单点」同一考虑）。
 */
export function scheduledDatesOf(employeeId, startDate, endDate) {
  ensureBuilt()
  const eid = Number(employeeId)
  const inRange = (workDate) =>
    (!startDate || workDate >= String(startDate)) && (!endDate || workDate <= String(endDate))
  const days = new Set(schedules.filter((s) => s.employeeId === eid && inRange(s.workDate)).map((s) => s.workDate))
  return [...days].sort()
}

/**
 * 有效卡：校验未通过的异常卡不算已完成打卡，否则一次 WiFi 未命中会把员工整天挡在门外
 * periodIndex 传 undefined 表示不限定时段（旧单班次模型与「今日是否打过卡」的总览口径）
 */
const validCard = (employeeId, workDate, checkType, periodIndex) => {
  const row = records.find(
    (r) =>
      r.employeeId === Number(employeeId) &&
      r.workDate === workDate &&
      r.checkType === checkType &&
      r.status !== 'ABNORMAL' &&
      (periodIndex === undefined || r.periodIndex === periodIndex)
  )
  return row ? clone(row) : null
}

/**
 * 今日打卡状态（员工端打卡页）：今日班次 + 规则要求摘要 + 按时段展开的打卡项
 * 为什么按时段展开：频次为 4 时员工端要渲染 4 个打卡按钮，页面只认时段数组即可，
 * 不必自己按 checkFrequency 推演时段数量与时间窗。
 */
export function todayStatus(employee, stationId) {
  ensureBuilt()
  const workDate = formatDate(new Date())
  const sid = Number(stationId)
  const rule = rules.find((r) => r.stationId === sid) || null
  const schedule = schedules.find((s) => s.employeeId === employee.id && s.workDate === workDate) || null
  const scheduled = schedule ? shiftById(schedule.shiftId) : null
  const onRecord = validCard(employee.id, workDate, 'ON')
  const offRecord = validCard(employee.id, workDate, 'OFF')

  // 打卡时段由该驿站班次派生（唯一时间真源）；规则未配置时不展开时段，但 shiftConfigured 仍如实反映班次有无
  const derived = attendancePeriodsOf(sid)
  const periods = rule
    ? derived.map((p) => {
        const on = validCard(employee.id, workDate, 'ON', p.periodIndex)
        const off = validCard(employee.id, workDate, 'OFF', p.periodIndex)
        return {
          periodIndex: p.periodIndex,
          name: p.name,
          startTime: p.startTime,
          endTime: p.endTime,
          // 时间窗直接下发，员工端不必再各存一份余量常量（与页面层现有 TODO 相呼应）
          windowStart: clockOfMinutes(minutesOfDay(p.startTime) - Number(rule.allowEarlyMin || 0)),
          windowEnd: clockOfMinutes(minutesOfDay(p.endTime) + Number(rule.allowLateMin || 0)),
          onChecked: !!on,
          offChecked: !!off,
          onTime: on ? on.checkTime : null,
          offTime: off ? off.checkTime : null
        }
      })
    : []
  const checkFrequency = periods.length * 2

  return {
    workDate,
    hasSchedule: !!schedule,
    // 未排班时回落该驿站首个启用班次（无启用班次则为 null，前端渲染「未配置班次」空态）
    shift: scheduled && scheduled.status === 1 ? toShiftVO(scheduled) : rule ? defaultShiftOf(rule) : null,
    // 该驿站是否配置了启用班次：false → 前端禁用打卡并提示「请联系管理员」（对应后端 9113）
    shiftConfigured: derived.length > 0,
    onChecked: !!onRecord,
    offChecked: !!offRecord,
    onRecord,
    offRecord,
    checkFrequency: rule ? checkFrequency : null,
    // 规则要求摘要：打卡页顶部一句话讲清「今天要打几次卡」
    requireSummary: rule
      ? `${rule.ruleName}｜每日 ${checkFrequency} 次打卡（${periods.map((p) => p.name).join('、')}）`
      : null,
    periods,
    rule: rule ? toRuleVO(rule) : null
  }
}

/** 我的打卡（员工端）：按月返回本人记录 + 今日状态 */
export function myAttendance(employeeId, month) {
  ensureBuilt()
  const eid = Number(employeeId)
  const prefix = month ? String(month) : formatDate(new Date()).slice(0, 7)
  const employee = db.employees.find((e) => e.id === eid)
  const list = records
    .filter((r) => r.employeeId === eid && r.workDate.startsWith(prefix))
    .sort((a, b) => (a.checkTime < b.checkTime ? 1 : -1))
    .map((r) => clone(r))
  return { month: prefix, list, todayStatus: employee ? todayStatus(employee, employee.station_id) : null }
}

/* ==================== 打卡（服务端式判定） ==================== */

/** 落一条打卡记录（返回 VO，与真实后端「写入后回显」的交互一致）；source 区分正常打卡与补卡补录 */
function pushRecord({
  employee,
  stationId,
  workDate,
  periodIndex,
  periodName,
  checkType,
  checkTime,
  status,
  remark,
  source = 'NORMAL',
  fields
}) {
  seq.record += 1
  const row = {
    id: seq.record,
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId,
    workDate,
    periodIndex,
    periodName,
    checkType,
    checkTime,
    status,
    remark,
    source,
    ...fields
  }
  records.push(row)
  return clone(row)
}

/**
 * 打卡判定（服务端位置执行，前端无法绕过）
 *
 * 为什么保留两条判定路径：
 * - 传 periodIndex → 时段模型（本轮新增）：时间窗 / 迟到 / 早退 / 去重全部以规则时段为基准，
 *   支持每日 2 次或 4 次打卡，时间窗 = [时段开始 - allowEarlyMin, 时段结束 + allowLateMin]；
 * - 不传 periodIndex → 单班次模型（历史行为）：以排班班次为基准。已交付的移动端打卡页尚未接入时段配置，
 *   页面本轮明确不改，若直接改成「必须传 periodIndex」会让现有打卡页当众打不了卡。
 * TODO(扩展): 移动端打卡页接入 periods 后删除旧路径，checkIn 只保留时段模型。
 *
 * 判定顺序：规则 → 时段/班次 → 时间窗 → 重复 → 校验项（ALL/ANY）→ 迟到/早退
 */
export function checkIn({ employee, stationId, checkType, wifiSsid, longitude, latitude, periodIndex }) {
  ensureBuilt()
  const sid = Number(stationId)
  const rule = rules.find((r) => r.stationId === sid)
  if (!rule) return { code: ATTENDANCE_CODE.RULE_NOT_CONFIGURED }

  const workDate = formatDate(new Date())
  const now = new Date()
  const nowMinutes = now.getHours() * 60 + now.getMinutes()

  const periods = attendancePeriodsOf(sid)
  const usePeriod = periodIndex !== undefined && periodIndex !== null
  // 非整数索引取不到时段，与越界同一处理（前端只可能是索引对不上，不必再分码）
  const period = usePeriod ? periods[periodIndex] : null
  if (usePeriod && !period) return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND }

  // 时段模型以班次派生的时段为时间基准，不依赖排班；单班次模型仍需班次，否则迟到早退无从判定
  let shift = null
  if (!usePeriod) {
    const schedule = schedules.find((s) => s.employeeId === employee.id && s.workDate === workDate) || null
    const scheduledShift = schedule ? shiftById(schedule.shiftId) : null
    // 排班存在但班次被删除/停用：时间基准已失效，不能静默按默认班次判定
    if (schedule && (!scheduledShift || scheduledShift.status !== 1)) return { code: ATTENDANCE_CODE.SHIFT_UNAVAILABLE }
    shift = scheduledShift || defaultShiftOf(rule)
    // 该驿站无任何启用班次：无打卡时间基准，按「未配置班次」拒绝（对应 9113）
    if (!shift) return { code: ATTENDANCE_CODE.NO_ACTIVE_SHIFT }
  }

  const startMin = minutesOfDay(usePeriod ? period.startTime : shift.startTime)
  const endMin = minutesOfDay(usePeriod ? period.endTime : shift.endTime)

  if (rule.enableTimeWindow) {
    const [from, to] = usePeriod
      ? [startMin - Number(rule.allowEarlyMin || 0), endMin + Number(rule.allowLateMin || 0)]
      : checkType === 'ON'
        ? [startMin - OPEN_AHEAD_MIN, endMin]
        : [startMin, endMin + CLOSE_DELAY_MIN]
    if (nowMinutes < from || nowMinutes > to) return { code: ATTENDANCE_CODE.OUT_OF_TIME_WINDOW }
  }

  // 去重粒度：时段模型按「员工 + 日期 + 时段 + 类型」，同一时段可各打一次上/下班卡
  if (validCard(employee.id, workDate, checkType, usePeriod ? periodIndex : undefined))
    return { code: ATTENDANCE_CODE.DUPLICATE_CHECK }

  const wifiMatched = rule.enableWifi ? rule.wifiList.some((w) => w.ssid === wifiSsid) : false
  const rawDistance = haversine(rule.longitude, rule.latitude, longitude, latitude)
  const distance = Number.isFinite(rawDistance) ? Number(rawDistance.toFixed(1)) : null
  const locationMatched = rule.enableLocation ? distance != null && distance <= rule.radius : false

  const enabled = []
  if (rule.enableWifi) enabled.push('WIFI')
  if (rule.enableLocation) enabled.push('LOCATION')
  const matched = []
  if (wifiMatched) matched.push('WIFI')
  if (locationMatched) matched.push('LOCATION')
  // checkMode 只允许契约中的三项取值；无任何启用项（免校验兜底）时回退为 WIFI+LOCATION
  const checkMode = matched.length ? matched.join('+') : enabled.length ? enabled.join('+') : 'WIFI+LOCATION'
  const pass =
    enabled.length === 0
      ? true
      : rule.matchMode === 'ALL'
        ? enabled.every((k) => matched.includes(k))
        : enabled.some((k) => matched.includes(k))
  const fields = { checkMode, wifiSsid, wifiMatched, longitude, latitude, distance, locationMatched }
  // 时段名取班次名（真源）；单班次模型（未传 periodIndex）回落到首个时段或本班次名
  const periodFields = {
    periodIndex: usePeriod ? periodIndex : 0,
    periodName: (usePeriod ? period : periods[0] || shift).name
  }

  if (!pass) {
    // 校验未通过的尝试仍落一条异常卡留痕，但错误码按「首个未通过项」返回，便于前端给出针对性提示
    const firstFail = enabled.find((k) => !matched.includes(k))
    pushRecord({
      employee,
      stationId: sid,
      workDate,
      ...periodFields,
      checkType,
      checkTime: formatDateTime(now),
      status: 'ABNORMAL',
      remark: '打卡校验未通过',
      fields
    })
    return { code: firstFail === 'WIFI' ? ATTENDANCE_CODE.WIFI_MISMATCH : ATTENDANCE_CODE.LOCATION_MISMATCH }
  }

  let status = 'NORMAL'
  let remark = null
  if (checkType === 'ON' && nowMinutes > startMin + rule.lateThresholdMin) {
    status = 'LATE'
    remark = `迟到超过 ${rule.lateThresholdMin} 分钟`
  }
  if (checkType === 'OFF' && nowMinutes < endMin - rule.earlyLeaveThresholdMin) {
    status = 'EARLY_LEAVE'
    remark = `早退超过 ${rule.earlyLeaveThresholdMin} 分钟`
  }

  return {
    code: 200,
    data: pushRecord({
      employee,
      stationId: sid,
      workDate,
      ...periodFields,
      checkType,
      checkTime: formatDateTime(now),
      status,
      remark,
      fields
    })
  }
}

/* ==================== 补卡申请与审批（T19） ==================== */

/**
 * 补卡样本方案：PENDING 8 条（多驿站 / 上下班卡 / 不同时段）+ APPROVED 5 条 + REJECTED 3 条。
 * 审批权在管理员（ADMIN）：样本里的审批人统一取首个 ADMIN，与接口的只有 ADMIN 能审批保持一致。
 */
const MAKEUP_PLAN = [
  { stationId: 1, status: 'PENDING' },
  { stationId: 1, status: 'PENDING' },
  { stationId: 1, status: 'PENDING' },
  { stationId: 2, status: 'PENDING' },
  { stationId: 2, status: 'PENDING' },
  { stationId: 3, status: 'PENDING' },
  { stationId: 4, status: 'PENDING' },
  { stationId: 5, status: 'PENDING' },
  { stationId: 1, status: 'APPROVED' },
  { stationId: 1, status: 'APPROVED' },
  { stationId: 1, status: 'APPROVED' },
  { stationId: 2, status: 'APPROVED' },
  { stationId: 3, status: 'APPROVED' },
  { stationId: 1, status: 'REJECTED' },
  { stationId: 2, status: 'REJECTED' },
  { stationId: 4, status: 'REJECTED' }
]

/** 补卡理由文案：覆盖「忘打卡 / 设备离线 / 外出取件」三类真实场景 */
const MAKEUP_REASONS = [
  '当班匆忙忘记打卡',
  '打卡时设备离线未记录',
  '外出取件错过下班打卡',
  '手机没电未能打卡',
  '系统故障未能打卡成功'
]

function toMakeupVO(makeup) {
  return { ...makeup, stationName: stationName(makeup.stationId) }
}

/** 补卡申请查询：员工端（employeeId）与管理端（stationId）共用一套筛选与分页，避免两套口径各写一遍 */
export function queryMakeups({ employeeId, stationId, status, startDate, endDate, pageNum, pageSize }) {
  ensureBuilt()
  let rows = makeups
  if (employeeId != null && employeeId !== '') rows = rows.filter((m) => m.employeeId === Number(employeeId))
  if (stationId != null && stationId !== '') rows = rows.filter((m) => m.stationId === Number(stationId))
  if (status) rows = rows.filter((m) => m.status === status)
  if (startDate) rows = rows.filter((m) => m.workDate >= String(startDate))
  if (endDate) rows = rows.filter((m) => m.workDate <= String(endDate))
  const page = paginate(
    rows.slice().sort((a, b) => (a.applyTime < b.applyTime ? 1 : -1)),
    pageNum,
    pageSize
  )
  page.list = page.list.map(toMakeupVO)
  return page
}

/**
 * 提交补卡申请（员工本人）
 * 校验顺序固定为「规则 → 时段 → 重复申请 → 已有正常打卡」：
 * 补卡与打卡共用「员工 + 日期 + 时段 + 类型」这一槽位口径，任何一步放过去都会与已有考勤数据打架。
 */
export function applyMakeup({ employee, stationId, workDate, periodIndex, checkType, reason }) {
  ensureBuilt()
  const sid = Number(stationId)
  const rule = rules.find((r) => r.stationId === sid)
  if (!rule) return { code: ATTENDANCE_CODE.RULE_NOT_CONFIGURED } // 9101
  // 站点无启用班次 → 无打卡时间基准，按「未配置班次」拒绝（9113），与打卡同一码
  if (!stationShiftConfigured(sid)) return { code: ATTENDANCE_CODE.NO_ACTIVE_SHIFT } // 9113
  const period = attendancePeriodsOf(sid)[periodIndex]
  if (!period) return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND } // 9107

  const duplicated = makeups.some(
    (m) =>
      m.employeeId === employee.id &&
      m.workDate === workDate &&
      m.periodIndex === periodIndex &&
      m.checkType === checkType &&
      m.status !== 'REJECTED'
  )
  // 已有正常卡说明本来就不用补，与「重复申请」同码：前端提示口径一致，不再额外占码
  if (duplicated || validCard(employee.id, workDate, checkType, periodIndex))
    return { code: ATTENDANCE_CODE.MAKEUP_DUPLICATE } // 9108

  seq.makeup += 1
  const makeup = {
    id: seq.makeup,
    employeeId: employee.id,
    employeeName: employee.real_name,
    stationId: sid,
    workDate,
    periodIndex,
    periodName: period.name,
    checkType,
    reason,
    status: 'PENDING',
    applyTime: formatDateTime(new Date()),
    approverId: null,
    approverName: null,
    approveTime: null,
    approveRemark: null
  }
  makeups.push(makeup)
  return { code: 200, data: toMakeupVO(makeup) }
}

/** 审批补卡：仅 ADMIN 可调（路由层已限角色）；通过即补录打卡记录，驳回只改状态 */
export function approveMakeup({ id, approved, approveRemark, approver }) {
  ensureBuilt()
  const makeup = makeups.find((m) => m.id === Number(id))
  if (!makeup) return { code: CODE.NOT_FOUND, message: '补卡申请不存在' }
  if (makeup.status !== 'PENDING') return { code: ATTENDANCE_CODE.MAKEUP_STATUS_INVALID } // 9109
  // 时段被改配置导致原时段不存在时不能静默通过，否则会落下「审批通过却无打卡记录」的矛盾数据
  if (approved && !writeMakeupRecord(makeup, true)) return { code: ATTENDANCE_CODE.RULE_NOT_CONFIGURED }

  makeup.status = approved ? 'APPROVED' : 'REJECTED'
  makeup.approverId = approver.id
  makeup.approverName = approver.real_name
  makeup.approveTime = formatDateTime(new Date())
  makeup.approveRemark = approveRemark || null

  if (approved) writeMakeupRecord(makeup)
  return { code: 200, data: toMakeupVO(makeup) }
}

/**
 * 审批通过 → 补录打卡记录
 * 打卡时间取该时段的规定时间（上班卡取开始时间、下班卡取结束时间）；
 * 校验项（WiFi / 定位 / 距离）不是设备打卡产生的，统一置 null 而不是伪造命中值，前端按 source='MAKEUP' 区分展示。
 * dryRun 只做「能不能补录」的前置判断，不落库。
 */
function writeMakeupRecord(makeup, dryRun = false) {
  const employee = db.employees.find((e) => e.id === makeup.employeeId)
  const rule = rules.find((r) => r.stationId === makeup.stationId)
  const period = rule ? attendancePeriodsOf(makeup.stationId)[makeup.periodIndex] : null
  if (!employee || !period) return null
  // 申请到审批期间本人又正常打了卡：同槽位不再补录，避免一个槽位出现两条正常卡
  if (validCard(makeup.employeeId, makeup.workDate, makeup.checkType, makeup.periodIndex)) return true
  if (dryRun) return true
  const specTime = makeup.checkType === 'ON' ? period.startTime : period.endTime
  return pushRecord({
    employee,
    stationId: makeup.stationId,
    workDate: makeup.workDate,
    periodIndex: makeup.periodIndex,
    periodName: makeup.periodName,
    checkType: makeup.checkType,
    checkTime: timeAt(makeup.workDate, minutesOfDay(specTime)),
    status: 'NORMAL',
    remark: '补卡通过（系统补录）',
    source: 'MAKEUP',
    fields: {
      checkMode: null,
      wifiSsid: null,
      wifiMatched: null,
      longitude: null,
      latitude: null,
      distance: null,
      locationMatched: null
    }
  })
}

/**
 * 补卡种子：只在「当日有排班且该槽位没有有效卡」的地方生成，
 * 通过的单子同步补录 attendance_record，避免出现「无排班却有补卡」「审批通过却无打卡记录」等矛盾数据。
 */
function buildMakeups() {
  const random = createRandom(0x2f4b8d16) // 独立种子：不与排班 / 打卡记录的随机序列互相干扰
  const admin = db.employees.find((e) => e.role === 'ADMIN') || null

  MAKEUP_PLAN.forEach((plan, index) => {
    const sid = plan.stationId
    const rule = rules.find((r) => r.stationId === sid)
    if (!rule) return
    const periods = attendancePeriodsOf(sid)
    if (!periods.length) return
    // 演示账号（st001_admin / st001_staff）不参与补卡种子：今日打卡演示入口不能被历史申请干扰
    const pool = activeEmployees().filter(
      (e) => e.station_id === sid && e.status === 1 && !DEMO_EMPLOYEE_IDS.includes(e.id)
    )
    if (!pool.length) return
    const periodIndex = index % periods.length
    const checkType = index % 2 === 0 ? 'ON' : 'OFF'
    // 轮转起点 + 依次顺延：单个员工近 20 天可能没有空闲槽位，换人重试以保证样本数量不被随机性吃掉
    const start = (index * 3 + 1) % pool.length
    let candidate = null
    for (let k = 0; k < pool.length && !candidate; k += 1) {
      const employee = pool[(start + k) % pool.length]
      const workDate = pickMakeupDate(random, employee, sid, periodIndex, checkType)
      if (workDate) candidate = { employee, workDate }
    }
    if (!candidate) return
    const { employee, workDate } = candidate

    const applyAt = addDays(localDate(workDate), 1 + (index % 2))
    applyAt.setHours(9, 10 + index, 0, 0)
    seq.makeup += 1
    makeups.push({
      id: seq.makeup,
      employeeId: employee.id,
      employeeName: employee.real_name,
      stationId: sid,
      workDate,
      periodIndex,
      periodName: periods[periodIndex].name,
      checkType,
      reason: MAKEUP_REASONS[index % MAKEUP_REASONS.length],
      status: plan.status,
      applyTime: formatDateTime(applyAt),
      approverId: plan.status === 'PENDING' || !admin ? null : admin.id,
      approverName: plan.status === 'PENDING' || !admin ? null : admin.real_name,
      approveTime: plan.status === 'PENDING' || !admin ? null : formatDateTime(addDays(applyAt, 1)),
      approveRemark:
        plan.status === 'APPROVED' ? '情况属实，予以补卡' : plan.status === 'REJECTED' ? '缺少证明材料，不予补卡' : null
    })
  })

  makeups.filter((m) => m.status === 'APPROVED').forEach((m) => writeMakeupRecord(m))
}

/** 挑一个「当日有排班且该槽位还没有有效卡」的补卡日期；近 20 天内打乱顺序取，保证样本分散 */
function pickMakeupDate(random, employee, stationId, periodIndex, checkType) {
  const offsets = []
  for (let day = 3; day <= 20; day += 1) offsets.push(day)
  const start = randomInt(random, 0, offsets.length - 1)
  for (let k = 0; k < offsets.length; k += 1) {
    const workDate = formatDate(shiftDays(-offsets[(start + k) % offsets.length], 0, 0, 0))
    if (validCard(employee.id, workDate, checkType, periodIndex)) continue
    if (!ensureScheduleFor(employee, stationId, workDate)) continue
    return workDate
  }
  return null
}

/**
 * 保证补卡当日有排班：
 * - 城东已有完整排班种子，只认既有排班（当天轮休就换一天，不额外补）；
 * - 其他驿站本轮没有排班种子（T17 排班只铺城东），就地补一条，避免出现「无排班却有补卡」的矛盾数据。
 * TODO(扩展): 排班种子覆盖全部驿站后删除本函数，补卡直接复用既有排班。
 */
function ensureScheduleFor(employee, stationId, workDate) {
  if (schedules.some((s) => s.employeeId === employee.id && s.workDate === workDate)) return true
  if (stationId === STATION_ID) return false
  seq.schedule += 1
  schedules.push({
    id: seq.schedule,
    stationId,
    employeeId: employee.id,
    workDate,
    shiftIds: [shiftIdOf(stationId, (employee.id + Number(workDate.slice(-2))) % SHIFT_SEED.length)],
    shiftId: shiftIdOf(stationId, (employee.id + Number(workDate.slice(-2))) % SHIFT_SEED.length),
    createTime: formatDateTime(addDays(localDate(workDate), -3))
  })
  return true
}
