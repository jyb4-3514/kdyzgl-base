import { db, findStationById, stationName } from '../db.js'
import { ATTENDANCE_CODE, CODE, STATION_CODE } from '../../constants/errorCode.js'
import { ALL_ROLES } from '../../constants/role.js'
import { CSV_TYPE, csvDisposition, fail, formatDate, ok, toCsvBlob } from '../util.js'
import { isBlank, isDate, pageSizeInvalid, textLen } from '../validate.js'
import {
  ATTENDANCE_DETAIL_DIMS,
  applyMakeup,
  approveMakeup,
  attendanceDetail,
  attendanceSummary,
  checkIn,
  createShift,
  exportRecords,
  findRule,
  listRules,
  listShifts,
  minutesOfDay,
  myAttendance,
  mySchedules,
  queryMakeups,
  queryRecords,
  querySchedules,
  removeShift,
  saveRule,
  saveSchedules,
  saveSchedulesByStation,
  todayStatus,
  updateShift
} from '../attendanceStore.js'

/**
 * 考勤与排班接口（T17，demo-design.md 7.4 契约风格）
 * 查询参数的数据范围：非 ADMIN 的 stationId 由 engine 统一收敛为本人归属驿站（见 domain/applyDataScope.js），
 * 前端传别的驿站不报错也不生效（不暴露「参数被忽略」的实现细节，避免试探出其他驿站是否存在数据）。
 */

/** 查询参数取值：空串 / null 归一为 null，避免 Number('') === 0 误命中不存在的驿站 */
const toIdOrNull = (value) => (value == null || value === '' ? null : Number(value))

const toNumberOrNull = (value) => {
  if (value == null || value === '') return null
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

/**
 * 写操作入参 body.stationId 的归属收敛（打卡）：body 不参与 engine 的查询参数收敛，
 * 故此处保留与 applyDataScope 同口径的覆盖，防止代他人向别的驿站打卡。
 */
const scopedStationId = (user, raw) => (user.role === 'ADMIN' ? toIdOrNull(raw) : user.station_id)

const isClock = (value) => /^([01]\d|2[0-3]):[0-5]\d$/.test(String(value))
/** 收班时间允许 24:00（晚班收在零点） */
const isEndClock = (value) => isClock(value) || String(value) === '24:00'
const isHexColor = (value) => /^#[0-9A-Fa-f]{6}$/.test(String(value))

/* ==================== 打卡规则 ==================== */

function getRule({ params }) {
  const stationId = toIdOrNull(params.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  const rule = findRule(stationId)
  if (!rule) return fail(ATTENDANCE_CODE.RULE_NOT_CONFIGURED)
  return ok(rule)
}

function ruleList() {
  return ok(listRules())
}

function validateRule(body) {
  if (body.ruleName !== undefined && !textLen(body.ruleName, 1, 50))
    return { code: CODE.BAD_REQUEST, message: '规则名称长度须为 1-50' }
  if (body.matchMode !== undefined && !['ALL', 'ANY'].includes(body.matchMode))
    return { code: CODE.BAD_REQUEST, message: 'matchMode 仅支持 ALL / ANY' }
  if (body.workStartTime !== undefined && !isClock(body.workStartTime))
    return { code: CODE.BAD_REQUEST, message: '上班时间格式须为 HH:mm' }
  if (body.workEndTime !== undefined && !isEndClock(body.workEndTime))
    return { code: CODE.BAD_REQUEST, message: '下班时间格式须为 HH:mm' }
  if (body.longitude !== undefined && !Number.isFinite(Number(body.longitude)))
    return { code: CODE.BAD_REQUEST, message: '经度须为数字' }
  if (body.latitude !== undefined && !Number.isFinite(Number(body.latitude)))
    return { code: CODE.BAD_REQUEST, message: '纬度须为数字' }
  if (body.radius !== undefined && !(Number(body.radius) > 0))
    return { code: CODE.BAD_REQUEST, message: '围栏半径须大于 0' }
  if (body.lateThresholdMin !== undefined && !(Number(body.lateThresholdMin) >= 0))
    return { code: CODE.BAD_REQUEST, message: '迟到阈值须不小于 0' }
  if (body.earlyLeaveThresholdMin !== undefined && !(Number(body.earlyLeaveThresholdMin) >= 0))
    return { code: CODE.BAD_REQUEST, message: '早退阈值须不小于 0' }
  if (body.allowEarlyMin !== undefined && !(Number(body.allowEarlyMin) >= 0))
    return { code: CODE.BAD_REQUEST, message: '允许提前打卡分钟数须不小于 0' }
  if (body.allowLateMin !== undefined && !(Number(body.allowLateMin) >= 0))
    return { code: CODE.BAD_REQUEST, message: '允许延后打卡分钟数须不小于 0' }
  if (
    body.wifiList !== undefined &&
    (!Array.isArray(body.wifiList) || body.wifiList.some((w) => !w || isBlank(w.ssid)))
  )
    return { code: CODE.BAD_REQUEST, message: 'WiFi 白名单须为 [{ssid, bssid}] 数组' }
  return null
}

/**
 * 时段配置校验：频次档位、时段数量、单段起止、段间重叠与顺序
 * 为什么统一返回 9107：这些错误都属于「时段」维度，前端拿到码即可跳到时段配置区，
 * 具体哪一条不合法由 message 说清（与打卡时索引越界同码，见 errorCode.js 说明）。
 */
function periodRuleError({ frequency, periods }) {
  if (frequency !== undefined && ![2, 4].includes(Number(frequency))) {
    return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND, message: 'checkFrequency 仅支持 2 或 4' }
  }
  if (periods === undefined || periods === null) return null
  if (!Array.isArray(periods) || periods.length === 0) {
    return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND, message: 'checkPeriods 须为非空数组' }
  }
  if (frequency !== undefined && periods.length !== Number(frequency) / 2) {
    return {
      code: ATTENDANCE_CODE.PERIOD_NOT_FOUND,
      message: `checkPeriods 长度须等于 checkFrequency / 2（本次应为 ${Number(frequency) / 2}）`
    }
  }
  let prevEnd = -1
  for (const period of periods) {
    const name = period && period.name ? `「${period.name}」` : ''
    if (!period || !textLen(period.name, 1, 20))
      return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND, message: '时段名称长度须为 1-20' }
    if (!isClock(period.startTime) || !isEndClock(period.endTime))
      return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND, message: `${name}起止时间格式须为 HH:mm` }
    const start = minutesOfDay(period.startTime)
    const end = minutesOfDay(period.endTime)
    if (start >= end) return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND, message: `${name}的结束时间须晚于开始时间` }
    // 必须升序且互不重叠：periodIndex 是打卡去重与记录归属的定位键，乱序会让「第 1 段」指向下午
    // TODO(扩展): 若后续要支持跨零点夜班时段（如 20:00-24:00 与 00:00-04:00），需放宽本约束并同步改造时段定位
    if (start < prevEnd)
      return { code: ATTENDANCE_CODE.PERIOD_NOT_FOUND, message: '打卡时段之间不允许重叠，且须按开始时间升序' }
    prevEnd = end
  }
  return null
}

/** 时段归一：非法结构原样返回，交给 periodRuleError 统一判错，避免两处各写一套文案 */
function normalizePeriods(list) {
  if (!Array.isArray(list)) return list
  return list.map((p) =>
    p && typeof p === 'object'
      ? { name: String(p.name == null ? '' : p.name).trim(), startTime: p.startTime, endTime: p.endTime }
      : p
  )
}

/** 组装白名单内的写入值：类型在此收口，避免字符串 '0'/'false' 之类被写进规则 */
function normalizeRule(body, current) {
  const payload = {}
  if (body.ruleName !== undefined) payload.ruleName = String(body.ruleName).trim()
  ;['enableWifi', 'enableLocation', 'enableTimeWindow'].forEach((key) => {
    if (body[key] !== undefined) payload[key] = Boolean(body[key])
  })
  if (body.matchMode !== undefined) payload.matchMode = body.matchMode
  if (body.wifiList !== undefined)
    payload.wifiList = body.wifiList.map((w) => ({
      ssid: String(w.ssid).trim(),
      bssid: w.bssid == null ? null : String(w.bssid)
    }))
  if (body.longitude !== undefined) payload.longitude = Number(body.longitude)
  if (body.latitude !== undefined) payload.latitude = Number(body.latitude)
  if (body.radius !== undefined) payload.radius = Number(body.radius)
  if (body.workStartTime !== undefined) payload.workStartTime = body.workStartTime
  if (body.workEndTime !== undefined) payload.workEndTime = body.workEndTime
  if (body.lateThresholdMin !== undefined) payload.lateThresholdMin = Number(body.lateThresholdMin)
  if (body.earlyLeaveThresholdMin !== undefined) payload.earlyLeaveThresholdMin = Number(body.earlyLeaveThresholdMin)
  if (body.allowEarlyMin !== undefined) payload.allowEarlyMin = Number(body.allowEarlyMin)
  if (body.allowLateMin !== undefined) payload.allowLateMin = Number(body.allowLateMin)
  if (body.status !== undefined) payload.status = Number(body.status)

  // 时段是唯一真源：未提交时段时沿用规则现值，避免「只改围栏半径」的提交把时段清空
  const basePeriods =
    body.checkPeriods !== undefined
      ? normalizePeriods(body.checkPeriods)
      : current && Array.isArray(current.checkPeriods)
        ? current.checkPeriods.map((p) => ({ ...p }))
        : null
  // 兼容旧客户端：管理端规则页当前只发上下班时间（不支持多时段），把它映射到首/末时段，
  // 否则页面上的时间改了却不生效（保存后被派生值覆盖回原样）
  // TODO(扩展): 管理端规则页支持多时段编辑后，删除这条兼容映射
  if (body.checkPeriods === undefined && Array.isArray(basePeriods) && basePeriods.length) {
    if (body.workStartTime !== undefined) basePeriods[0].startTime = body.workStartTime
    if (body.workEndTime !== undefined) basePeriods[basePeriods.length - 1].endTime = body.workEndTime
  }
  if (basePeriods !== null && basePeriods !== undefined) {
    payload.checkPeriods = basePeriods
    payload.checkFrequency =
      body.checkFrequency !== undefined ? Number(body.checkFrequency) : current ? current.checkFrequency : 2
  } else if (body.checkFrequency !== undefined) {
    payload.checkFrequency = Number(body.checkFrequency)
  }
  return payload
}

function saveRuleHandler({ body }) {
  const stationId = toIdOrNull(body.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  if (!findStationById(stationId)) return fail(STATION_CODE.NOT_EXISTS)
  const error = validateRule(body)
  if (error) return fail(error.code, error.message)
  const current = findRule(stationId)
  const payload = normalizeRule(body, current)
  // 归一化后再校验一次：旧客户端只发上下班时间时，时段是被映射出来的，必须仍然自洽
  const periodError = periodRuleError({ frequency: payload.checkFrequency, periods: payload.checkPeriods })
  if (periodError) return fail(periodError.code, periodError.message)
  return ok(saveRule(stationId, payload))
}

/* ==================== 班次 ==================== */

function shiftList({ params }) {
  const stationId = toIdOrNull(params.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  return ok(listShifts(stationId))
}

function validateShift(body) {
  if (!textLen(body.shiftName, 1, 20)) return '班次名称长度须为 1-20'
  if (!isClock(body.startTime)) return '开始时间格式须为 HH:mm'
  if (!isEndClock(body.endTime)) return '结束时间格式须为 HH:mm'
  if (minutesOfDay(body.startTime) >= minutesOfDay(body.endTime)) return '结束时间须晚于开始时间'
  if (!isHexColor(body.color)) return '班次颜色须为 #RRGGBB'
  if (body.restMinutes !== undefined && !(Number(body.restMinutes) >= 0)) return '休息时长须不小于 0'
  return null
}

function shiftVOData(body) {
  return {
    shiftName: String(body.shiftName).trim(),
    startTime: body.startTime,
    endTime: body.endTime,
    color: String(body.color).toUpperCase(),
    restMinutes: body.restMinutes === undefined ? 0 : Number(body.restMinutes)
  }
}

function createShiftHandler({ body }) {
  const stationId = toIdOrNull(body.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  if (!findStationById(stationId)) return fail(STATION_CODE.NOT_EXISTS)
  const error = validateShift(body)
  if (error) return fail(CODE.BAD_REQUEST, error)
  return ok(
    createShift({ stationId, ...shiftVOData(body), status: body.status === undefined ? 1 : Number(body.status) })
  )
}

function updateShiftHandler({ pathParams, body }) {
  const error = validateShift(body)
  if (error) return fail(CODE.BAD_REQUEST, error)
  const vo = updateShift(pathParams.id, {
    ...shiftVOData(body),
    status: body.status === undefined ? 1 : Number(body.status)
  })
  if (!vo) return fail(CODE.NOT_FOUND, '班次不存在')
  return ok(vo)
}

function deleteShiftHandler({ pathParams }) {
  const result = removeShift(pathParams.id)
  if (result === false) return fail(CODE.NOT_FOUND, '班次不存在')
  if (result === 'IN_USE') return fail(CODE.BAD_REQUEST, '该班次已被排班引用，不能删除')
  return ok(null)
}

/* ==================== 排班 ==================== */

function scheduleMatrix({ params }) {
  const stationId = toIdOrNull(params.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  if (!isBlank(params.weekStart) && !isDate(params.weekStart))
    return fail(CODE.BAD_REQUEST, 'weekStart 格式须为 YYYY-MM-DD')
  return ok(querySchedules({ stationId, weekStart: params.weekStart }))
}

function mySchedule({ params, user }) {
  if (!isBlank(params.weekStart) && !isDate(params.weekStart))
    return fail(CODE.BAD_REQUEST, 'weekStart 格式须为 YYYY-MM-DD')
  return ok(mySchedules(user.id, params.weekStart))
}

function saveScheduleBatch({ body }) {
  const stationId = toIdOrNull(body.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  if (!Array.isArray(body.items) || body.items.length === 0) return fail(CODE.BAD_REQUEST, 'items 须为非空数组')
  // 单次上限与员工导入的批量口径保持一致，防前端一次提交整月矩阵造成卡顿
  if (body.items.length > 200) return fail(CODE.BAD_REQUEST, '单次保存不超过 200 条')
  for (const item of body.items) {
    if (item.employeeId == null) return fail(CODE.BAD_REQUEST, '缺少 employeeId')
    if (!isDate(item.workDate)) return fail(CODE.BAD_REQUEST, 'workDate 格式须为 YYYY-MM-DD')
  }
  const result = saveSchedules({ stationId, items: body.items })
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/**
 * 按驿站批量排班（需求2）：一次铺满「整站员工 × 日期范围（可只排指定星期）」，减少逐格点击。
 * 只做参数校验与响应包装，唯一性/跳过逻辑收在 store，与单条批量保存共用一套排班真源。
 */
function saveScheduleByStation({ body }) {
  const stationId = toIdOrNull(body.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  if (!findStationById(stationId)) return fail(STATION_CODE.NOT_EXISTS)
  if (toIdOrNull(body.shiftId) == null) return fail(CODE.BAD_REQUEST, '缺少 shiftId')
  if (isBlank(body.startDate) || !isDate(body.startDate) || isBlank(body.endDate) || !isDate(body.endDate)) {
    return fail(CODE.BAD_REQUEST, 'startDate / endDate 格式须为 YYYY-MM-DD')
  }
  if (body.startDate > body.endDate) return fail(CODE.BAD_REQUEST, 'endDate 不能早于 startDate')
  if (body.employeeIds !== undefined && !Array.isArray(body.employeeIds))
    return fail(CODE.BAD_REQUEST, 'employeeIds 须为数组')
  // 0=周日 … 6=周六，与 JS getDay 一致；非法星期直接挡在入口，避免排班落到错误日期
  if (
    body.weekdays !== undefined &&
    (!Array.isArray(body.weekdays) ||
      body.weekdays.some((d) => !Number.isInteger(Number(d)) || Number(d) < 0 || Number(d) > 6))
  ) {
    return fail(CODE.BAD_REQUEST, 'weekdays 须为 0-6 的整数数组')
  }
  const result = saveSchedulesByStation({
    stationId,
    shiftId: Number(body.shiftId),
    startDate: body.startDate,
    endDate: body.endDate,
    employeeIds: body.employeeIds,
    skipExisting: body.skipExisting === undefined ? true : Boolean(body.skipExisting),
    weekdays: body.weekdays
  })
  return result.code === 200 ? ok(result.data) : fail(result.code)
}

/* ==================== 打卡记录与状态 ==================== */

const RECORD_STATUS = ['NORMAL', 'LATE', 'EARLY_LEAVE', 'ABNORMAL']

function records({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  if (!isBlank(params.status) && !RECORD_STATUS.includes(params.status))
    return fail(CODE.BAD_REQUEST, 'status 取值非法')
  if (!isBlank(params.startDate) && !isDate(params.startDate))
    return fail(CODE.BAD_REQUEST, 'startDate 格式须为 YYYY-MM-DD')
  if (!isBlank(params.endDate) && !isDate(params.endDate)) return fail(CODE.BAD_REQUEST, 'endDate 格式须为 YYYY-MM-DD')
  return ok(
    queryRecords({
      stationId: toIdOrNull(params.stationId),
      employeeId: params.employeeId,
      status: params.status,
      startDate: params.startDate,
      endDate: params.endDate,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

/** 导出列文案：字典值与页面标签保持一份来源，避免导出出现英文枚举值 */
const EXPORT_CHECK_TYPE = { ON: '上班卡', OFF: '下班卡' }
const EXPORT_STATUS = { NORMAL: '正常', LATE: '迟到', EARLY_LEAVE: '早退', ABNORMAL: '异常' }
const EXPORT_CHECK_MODE = { WIFI: 'WiFi', LOCATION: '定位', 'WIFI+LOCATION': 'WiFi+定位' }
const EXPORT_SOURCE = { NORMAL: '正常打卡', MAKEUP: '补卡' }

/**
 * 考勤记录导出（需求5）：CSV 文件流，筛选口径与 GET /attendance/records 完全一致。
 * 为什么不复用 records 的响应：records 走分页（pageSize 上限 100），导出必须全量，
 * 因此 store 侧抽出同一套 filterRecords，两边只共享筛选逻辑、各自决定「分页 or 全量」。
 */
function exportCsv({ params }) {
  if (!isBlank(params.status) && !RECORD_STATUS.includes(params.status))
    return fail(CODE.BAD_REQUEST, 'status 取值非法')
  if (!isBlank(params.startDate) && !isDate(params.startDate))
    return fail(CODE.BAD_REQUEST, 'startDate 格式须为 YYYY-MM-DD')
  if (!isBlank(params.endDate) && !isDate(params.endDate)) return fail(CODE.BAD_REQUEST, 'endDate 格式须为 YYYY-MM-DD')
  const rows = exportRecords({
    stationId: toIdOrNull(params.stationId),
    employeeId: params.employeeId,
    status: params.status,
    startDate: params.startDate,
    endDate: params.endDate
  })
  const header = [
    '员工姓名',
    '登录账号',
    '所属驿站',
    '日期',
    '时段名称',
    '卡类型',
    '打卡时间',
    '打卡方式',
    'WiFi',
    '距离(米)',
    '状态',
    '来源',
    '备注'
  ]
  const bodyRows = rows.map((r) => {
    const employee = db.employees.find((e) => e.id === r.employeeId)
    return [
      r.employeeName,
      employee ? employee.username : '',
      stationName(r.stationId) || '',
      r.workDate,
      r.periodName,
      EXPORT_CHECK_TYPE[r.checkType] || r.checkType,
      r.checkTime,
      EXPORT_CHECK_MODE[r.checkMode] || '',
      r.wifiSsid || '',
      r.distance == null ? '' : r.distance,
      EXPORT_STATUS[r.status] || r.status,
      EXPORT_SOURCE[r.source] || r.source,
      r.remark
    ]
  })
  return {
    code: CODE.SUCCESS,
    message: 'success',
    data: toCsvBlob([header, ...bodyRows]),
    headers: {
      'content-type': CSV_TYPE,
      'content-disposition': csvDisposition(`考勤记录_${formatDate(new Date()).replace(/-/g, '')}.csv`)
    }
  }
}

function summary({ params }) {
  if (!isBlank(params.date) && !isDate(params.date)) return fail(CODE.BAD_REQUEST, 'date 格式须为 YYYY-MM-DD')
  return ok(attendanceSummary(toIdOrNull(params.stationId), params.date))
}

/**
 * 考勤明细（Q1）：按维度给出「人 + 当天在该维度的事实」名单，口径与 summary 完全同源（共用同一个 attendanceScope）。
 * dim 必填且白名单严校验：非法/缺省一律 400，页面侧的「回落 SHOULD」只是体验兜底，不放松服务端约束。
 */
function detail({ params }) {
  if (!ATTENDANCE_DETAIL_DIMS.includes(params.dim)) return fail(CODE.BAD_REQUEST, 'dim 取值非法')
  if (!isBlank(params.date) && !isDate(params.date)) return fail(CODE.BAD_REQUEST, 'date 格式须为 YYYY-MM-DD')
  return ok(attendanceDetail({ dim: params.dim, stationId: toIdOrNull(params.stationId), date: params.date }))
}

function my({ params, user }) {
  if (!isBlank(params.month) && !/^\d{4}-\d{2}$/.test(String(params.month)))
    return fail(CODE.BAD_REQUEST, 'month 格式须为 YYYY-MM')
  return ok(myAttendance(user.id, params.month))
}

function status({ user }) {
  return ok(todayStatus(user, user.station_id))
}

function checkInHandler({ body, user }) {
  const checkType = String(body.checkType || '')
  if (!['ON', 'OFF'].includes(checkType)) return fail(CODE.BAD_REQUEST, 'checkType 仅支持 ON / OFF')
  // periodIndex 可缺省：缺省按历史单班次模型判定（排班班次为时间基准），传入则按规则时段判定；
  // 非整数索引与越界同处理，统一回 9107，前端不必区分「格式错」还是「时段没配够」
  const rawPeriod = body.periodIndex
  let periodIndex
  if (rawPeriod !== undefined && rawPeriod !== null && rawPeriod !== '') {
    periodIndex = Number(rawPeriod)
    if (!Number.isInteger(periodIndex) || periodIndex < 0) return fail(ATTENDANCE_CODE.PERIOD_NOT_FOUND)
  }
  const result = checkIn({
    employee: user,
    stationId: scopedStationId(user, body.stationId),
    checkType,
    wifiSsid: body.wifiSsid == null ? null : String(body.wifiSsid),
    longitude: toNumberOrNull(body.longitude),
    latitude: toNumberOrNull(body.latitude),
    periodIndex
  })
  return result.code === 200 ? ok(result.data) : fail(result.code)
}

/* ==================== 补卡申请与审批（T19） ==================== */

const MAKEUP_STATUS = ['PENDING', 'APPROVED', 'REJECTED']
// TODO(扩展): 补卡没有独立详情端点，操作级派生标志（如 canApprove）待契约定义后再由服务端下发，
//   当前由前端按状态+角色本地判定；不在 Mock 单方面臆造标志，避免与前端判定形成双口径

/** 列表与「我的」共用的筛选校验：状态取值与日期格式两处都要用，抽一次避免文案分叉 */
function makeupQueryError(params) {
  if (pageSizeInvalid(params.pageSize)) return { code: CODE.BAD_REQUEST, message: '每页条数须为 1-100' }
  if (!isBlank(params.status) && !MAKEUP_STATUS.includes(params.status))
    return { code: CODE.BAD_REQUEST, message: 'status 取值非法' }
  for (const key of ['startDate', 'endDate']) {
    if (!isBlank(params[key]) && !isDate(params[key]))
      return { code: CODE.BAD_REQUEST, message: `${key} 格式须为 YYYY-MM-DD` }
  }
  return null
}

/** 提交补卡：申请人只能是登录人本人，stationId 取登录人归属（不接受前端传参，防代他人申请） */
function makeupApplyHandler({ body, user }) {
  if (user.station_id == null) return fail(CODE.BAD_REQUEST, '当前账号未归属驿站，无法提交补卡')
  const workDate = String(body.workDate || '')
  if (!isDate(workDate)) return fail(CODE.BAD_REQUEST, 'workDate 格式须为 YYYY-MM-DD')
  // 契约未写「不能补未来日期」，但补未来日期在业务上不成立，按常识加护栏
  if (workDate > formatDate(new Date())) return fail(CODE.BAD_REQUEST, '补卡日期不能晚于今天')
  const checkType = String(body.checkType || '')
  if (!['ON', 'OFF'].includes(checkType)) return fail(CODE.BAD_REQUEST, 'checkType 仅支持 ON / OFF')
  if (!textLen(body.reason, 2, 200)) return fail(CODE.BAD_REQUEST, '补卡理由长度须为 2-200 字')
  // 非整数索引与越界同处理（与打卡判定口径一致）
  const periodIndex = Number(body.periodIndex)
  if (!Number.isInteger(periodIndex) || periodIndex < 0) return fail(ATTENDANCE_CODE.PERIOD_NOT_FOUND)

  const result = applyMakeup({
    employee: user,
    stationId: user.station_id,
    workDate,
    periodIndex,
    checkType,
    reason: String(body.reason).trim()
  })
  return result.code === 200 ? ok(result.data) : fail(result.code)
}

function makeupMine({ params, user }) {
  const error = makeupQueryError(params)
  if (error) return fail(error.code, error.message)
  return ok(
    queryMakeups({
      employeeId: user.id,
      status: params.status,
      startDate: params.startDate,
      endDate: params.endDate,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

/** 补卡列表（仅 ADMIN，全驿站）：stationId 缺省即全量，与记录/概况的 ADMIN 口径一致 */
function makeupList({ params }) {
  const error = makeupQueryError(params)
  if (error) return fail(error.code, error.message)
  return ok(
    queryMakeups({
      stationId: toIdOrNull(params.stationId),
      status: params.status,
      startDate: params.startDate,
      endDate: params.endDate,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function makeupApproveHandler({ body, pathParams, user }) {
  if (typeof body.approved !== 'boolean') return fail(CODE.BAD_REQUEST, 'approved 须为布尔值')
  if (body.approveRemark != null && !textLen(body.approveRemark, 0, 200))
    return fail(CODE.BAD_REQUEST, '审批备注不可超过 200 字')
  const result = approveMakeup({
    id: pathParams.id,
    approved: body.approved,
    approveRemark: isBlank(body.approveRemark) ? null : String(body.approveRemark).trim(),
    approver: user
  })
  return result.code === 200 ? ok(result.data) : fail(result.code, result.message)
}

/**
 * 路由注册顺序说明：engine 取首个命中，静态路径与 /:id 冲突时静态必须在前
 * （/attendance/rule 与 /attendance/rule/list 为不同路径，互不遮蔽）
 */
export const attendanceRoutes = [
  { method: 'get', path: '/attendance/rule', roles: ALL_ROLES, handler: getRule },
  { method: 'get', path: '/attendance/rule/list', roles: ['ADMIN'], handler: ruleList },
  { method: 'put', path: '/attendance/rule', roles: ['ADMIN'], handler: saveRuleHandler },
  { method: 'get', path: '/attendance/status', roles: ALL_ROLES, handler: status },
  { method: 'get', path: '/attendance/records', roles: ['ADMIN', 'STATION_ADMIN'], handler: records },
  { method: 'get', path: '/attendance/export', roles: ['ADMIN', 'STATION_ADMIN'], handler: exportCsv },
  { method: 'get', path: '/attendance/summary', roles: ['ADMIN', 'STATION_ADMIN'], handler: summary },
  { method: 'get', path: '/attendance/detail', roles: ['ADMIN', 'STATION_ADMIN'], handler: detail },
  { method: 'get', path: '/attendance/my', roles: ALL_ROLES, handler: my },
  // 补卡：提交与「我的」不限角色（员工本人），列表与审批只有管理员（ADMIN）可用，角色不符由 engine 统一回 403
  { method: 'get', path: '/attendance/makeup/my', roles: ALL_ROLES, handler: makeupMine },
  { method: 'get', path: '/attendance/makeup/list', roles: ['ADMIN'], handler: makeupList },
  { method: 'post', path: '/attendance/makeup', roles: ALL_ROLES, handler: makeupApplyHandler },
  { method: 'post', path: '/attendance/makeup/:id/approve', roles: ['ADMIN'], handler: makeupApproveHandler },
  { method: 'post', path: '/attendance/check-in', roles: ALL_ROLES, handler: checkInHandler },
  { method: 'get', path: '/schedules', roles: ['ADMIN', 'STATION_ADMIN'], handler: scheduleMatrix },
  { method: 'get', path: '/schedules/my', roles: ALL_ROLES, handler: mySchedule },
  { method: 'post', path: '/schedules/batch', roles: ['ADMIN'], handler: saveScheduleBatch },
  { method: 'post', path: '/schedules/batch-by-station', roles: ['ADMIN'], handler: saveScheduleByStation },
  { method: 'get', path: '/shifts', roles: ALL_ROLES, handler: shiftList },
  { method: 'post', path: '/shifts', roles: ['ADMIN'], handler: createShiftHandler },
  { method: 'put', path: '/shifts/:id', roles: ['ADMIN'], handler: updateShiftHandler },
  { method: 'delete', path: '/shifts/:id', roles: ['ADMIN'], handler: deleteShiftHandler }
]
