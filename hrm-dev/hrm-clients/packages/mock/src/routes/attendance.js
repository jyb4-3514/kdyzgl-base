import { db, findStationById, stationName } from '../db.js'
import { ATTENDANCE_CODE, CODE, STATION_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { ALL_ROLES } from '@kdyzgl/shared/constants/role.js'
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
  updateShift,
  validateShiftSet
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

/** 组装白名单内的写入值：类型在此收口，避免字符串 '0'/'false' 之类被写进规则 */
function normalizeRule(body) {
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
  if (body.lateThresholdMin !== undefined) payload.lateThresholdMin = Number(body.lateThresholdMin)
  if (body.earlyLeaveThresholdMin !== undefined) payload.earlyLeaveThresholdMin = Number(body.earlyLeaveThresholdMin)
  if (body.allowEarlyMin !== undefined) payload.allowEarlyMin = Number(body.allowEarlyMin)
  if (body.allowLateMin !== undefined) payload.allowLateMin = Number(body.allowLateMin)
  if (body.status !== undefined) payload.status = Number(body.status)
  // checkPeriods / checkFrequency / workStartTime / workEndTime 不在白名单：时段与频次改为班次派生（U-4/U-5），
  // 即便请求体带上也一律忽略，避免「管理员以为改了时段、实际不生效」的静默失效
  return payload
}

function saveRuleHandler({ body }) {
  const stationId = toIdOrNull(body.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  if (!findStationById(stationId)) return fail(STATION_CODE.NOT_EXISTS)
  // U-5：时段已由驿站班次决定，规则侧不再接受时段入参——非空即拒（不静默忽略，否则管理员会以为时段改成功了）
  if (body.checkPeriods !== undefined && body.checkPeriods !== null) {
    const nonEmpty = Array.isArray(body.checkPeriods) ? body.checkPeriods.length > 0 : true
    if (nonEmpty) return fail(CODE.BAD_REQUEST, '打卡时段已由驿站班次决定，请到「排班管理」维护班次')
  }
  const error = validateRule(body)
  if (error) return fail(error.code, error.message)
  return ok(saveRule(stationId, normalizeRule(body)))
}

/* ==================== 班次 ==================== */

function shiftList({ params }) {
  const stationId = toIdOrNull(params.stationId)
  if (stationId == null) return fail(CODE.BAD_REQUEST, '缺少 stationId')
  return ok(listShifts(stationId))
}

/** 班次名保留字：= 后端 legacyPeriodSentinel / DEFAULT_PERIOD_NAME「全天班」（§6.4 M2/N2），撞名会让新记录被误判哨兵 */
const RESERVED_SHIFT_NAMES = ['全天班']

function validateShift(body) {
  // 禁用名比对取 trim 后的值：与落库值同源归一（shiftVOData 里也 trim），防「 全天班」前置/尾随空白绕过（N2）
  const name = body.shiftName == null ? null : String(body.shiftName).trim()
  if (RESERVED_SHIFT_NAMES.includes(name))
    return { code: ATTENDANCE_CODE.SHIFT_DEFINITION_INVALID, message: '班次名称不可使用保留名「全天班」' }
  if (!textLen(body.shiftName, 1, 20)) return { code: CODE.BAD_REQUEST, message: '班次名称长度须为 1-20' }
  if (!isClock(body.startTime)) return { code: CODE.BAD_REQUEST, message: '开始时间格式须为 HH:mm' }
  if (!isEndClock(body.endTime)) return { code: CODE.BAD_REQUEST, message: '结束时间格式须为 HH:mm' }
  if (minutesOfDay(body.startTime) >= minutesOfDay(body.endTime))
    return { code: CODE.BAD_REQUEST, message: '结束时间须晚于开始时间' }
  if (!isHexColor(body.color)) return { code: CODE.BAD_REQUEST, message: '班次颜色须为 #RRGGBB' }
  if (body.restMinutes !== undefined && !(Number(body.restMinutes) >= 0))
    return { code: CODE.BAD_REQUEST, message: '休息时长须不小于 0' }
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
  if (error) return fail(error.code, error.message)
  const next = { stationId, ...shiftVOData(body), status: body.status === undefined ? 1 : Number(body.status) }
  // 定义侧站点级校验（启用数 ≤2 / 一早一晚），与后端 AttendanceShiftServiceImpl 同口径
  const setError = validateShiftSet({ shift: next })
  if (setError) return fail(setError.code, setError.message)
  return ok(createShift(next))
}

function updateShiftHandler({ pathParams, body }) {
  const error = validateShift(body)
  if (error) return fail(error.code, error.message)
  const next = { ...shiftVOData(body), status: body.status === undefined ? 1 : Number(body.status) }
  // 编辑：排除被编辑自身后重新校验站点启用集合（停用可把该班次移出启用集，故先校验后写入）
  const setError = validateShiftSet({ shiftId: pathParams.id, shift: next })
  if (setError) return fail(setError.code, setError.message)
  const vo = updateShift(pathParams.id, next)
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
    // 多班次（架构 ARCH-C-2）：shiftIds 存在即为准；类型错误直接挡在入口，集合校验在 store
    if (item.shiftIds !== undefined && !Array.isArray(item.shiftIds))
      return fail(CODE.BAD_REQUEST, 'shiftIds 须为数组')
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
