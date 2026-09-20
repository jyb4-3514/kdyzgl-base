import { CODE, STATION_CODE } from '../../constants/errorCode.js'
import { activeStations, db, findStationById, stationEmployeeCount } from '../db.js'
import { fail, formatDateTime, maskPhone, ok } from '../util.js'
import { isBlank, isPhone, isStationCode, textLen } from '../validate.js'

/**
 * 驿站接口（api.md 4.5，5 个，仅 ADMIN）
 * 列表全量不分页：驿站基数 < 100，供管理表格与员工表单下拉共用（api.md 4.5.1）
 */

/** 编码活跃唯一校验（编辑时排除自身） */
function codeExists(code, excludeId = null) {
  return activeStations().some((s) => s.code === code && s.id !== Number(excludeId))
}

/** 入参公共校验：返回错误文案或 null；新增/编辑共用，避免两处规则漂移 */
function validateStationBody(body, isCreate) {
  const code = String(body.code || '').trim()
  const stationName = String(body.stationName || '').trim()

  if (isCreate || body.code != null) {
    if (!isStationCode(code)) return '驿站编码须为 2-50 位字母/数字/下划线/中划线'
  }
  if (isCreate || body.stationName != null) {
    if (!textLen(stationName, 1, 50)) return '驿站名称长度须为 1-50 字符'
  }
  if (body.contactPerson != null && !textLen(body.contactPerson, 0, 50)) return '联系人长度不可超过 50 字符'
  if (body.contactPhone != null && !isBlank(body.contactPhone) && !isPhone(body.contactPhone))
    return '联系人电话格式不正确'
  if (body.address != null && !textLen(body.address, 0, 255)) return '地址长度不可超过 255 字符'
  if (body.remark != null && !textLen(body.remark, 0, 255)) return '备注长度不可超过 255 字符'
  return null
}

function toStationVO(station) {
  return {
    id: station.id,
    code: station.code,
    stationName: station.station_name,
    contactPerson: station.contact_person,
    contactPhone: maskPhone(station.contact_phone),
    address: station.address,
    status: station.status,
    employeeCount: stationEmployeeCount(station.id),
    remark: station.remark,
    createTime: station.create_time
  }
}

function list({ params }) {
  let rows = activeStations()
  if (params.status !== undefined && params.status !== '') {
    rows = rows.filter((s) => s.status === Number(params.status))
  }
  return ok(
    rows
      .slice()
      .sort((a, b) => a.id - b.id)
      .map(toStationVO)
  )
}

function create({ body }) {
  const invalid = validateStationBody(body, true)
  if (invalid) return fail(CODE.BAD_REQUEST, invalid)
  const code = String(body.code).trim()
  if (codeExists(code)) return fail(STATION_CODE.CODE_EXISTS)

  const id = (db.seq.station += 1)
  const now = formatDateTime(new Date())
  db.stations.push({
    id,
    code,
    station_name: String(body.stationName).trim(),
    contact_person: body.contactPerson || null,
    contact_phone: body.contactPhone || null,
    address: body.address || null,
    status: 1,
    remark: body.remark || null,
    is_deleted: 0,
    create_time: now,
    update_time: now
  })
  return ok({ id })
}

function update({ body, pathParams }) {
  const station = findStationById(pathParams.id)
  if (!station) return fail(CODE.NOT_FOUND, '驿站不存在')

  const invalid = validateStationBody(body, false)
  if (invalid) return fail(CODE.BAD_REQUEST, invalid)
  // 一期允许修改 code，唯一校验生效；二期爬虫上线后编码冻结（api.md 4.5.3）
  if (body.code != null && codeExists(String(body.code).trim(), station.id)) return fail(STATION_CODE.CODE_EXISTS)

  if (body.code != null) station.code = String(body.code).trim()
  if (body.stationName != null) station.station_name = String(body.stationName).trim()
  if (body.contactPerson !== undefined) station.contact_person = body.contactPerson || null
  if (body.contactPhone !== undefined) station.contact_phone = body.contactPhone || null
  if (body.address !== undefined) station.address = body.address || null
  if (body.remark !== undefined) station.remark = body.remark || null
  station.update_time = formatDateTime(new Date())
  return ok(null)
}

function updateStatus({ body, pathParams }) {
  const station = findStationById(pathParams.id)
  if (!station) return fail(CODE.NOT_FOUND, '驿站不存在')
  const status = Number(body.status)
  if (status !== 0 && status !== 1) return fail(CODE.BAD_REQUEST, '状态值非法')

  station.status = status
  station.update_time = formatDateTime(new Date())
  return ok(null)
}

function remove({ pathParams }) {
  const station = findStationById(pathParams.id)
  if (!station) return fail(CODE.NOT_FOUND, '驿站不存在')
  if (stationEmployeeCount(station.id) > 0) return fail(STATION_CODE.HAS_EMPLOYEE)

  station.is_deleted = 1
  station.update_time = formatDateTime(new Date())
  return ok(null)
}

export const stationRoutes = [
  { method: 'get', path: '/stations', roles: ['ADMIN'], handler: list },
  { method: 'post', path: '/stations', roles: ['ADMIN'], handler: create },
  { method: 'put', path: '/stations/:id/status', roles: ['ADMIN'], handler: updateStatus },
  { method: 'put', path: '/stations/:id', roles: ['ADMIN'], handler: update },
  { method: 'delete', path: '/stations/:id', roles: ['ADMIN'], handler: remove }
]
