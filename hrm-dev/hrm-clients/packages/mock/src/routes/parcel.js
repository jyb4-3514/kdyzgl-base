import { getParcelById, parcelRanking, parcelSummary, parcelTrend, pickupParcel, queryParcels } from '../parcelStore.js'
import { CODE, DEMO_CODE } from '@kdyzgl/shared/constants/errorCode.js'
import { ALL_ROLES } from '@kdyzgl/shared/constants/role.js'
import { fail, ok } from '../util.js'
import { pageSizeInvalid } from '../validate.js'

/**
 * 包裹接口（T06，demo-design.md 7.4.2 契约草案）
 * 数据范围：非 ADMIN 的 station_id 由 engine 统一收敛为本人归属驿站（见 domain/applyDataScope.js），
 * 前端传别的驿站也不生效；取件写操作走 parcelStore 的覆盖层，统计口径随覆盖层实时变化。
 */

/** 非 ADMIN 的数据范围：本人驿站；ADMIN 返回 null 表示全量 */
const scopedStation = (user) => (user.role === 'ADMIN' ? null : user.station_id)

function list({ params }) {
  if (pageSizeInvalid(params.pageSize)) return fail(CODE.BAD_REQUEST, '每页条数须为 1-100')
  // stationId 已在 engine 按角色收敛，这里只做空值归一
  const stationId = params.stationId
  return ok(
    queryParcels({
      stationId: stationId == null || stationId === '' ? null : stationId,
      status: params.status,
      waybillNo: params.waybillNo,
      startTime: params.startTime,
      endTime: params.endTime,
      pageNum: params.pageNum,
      pageSize: params.pageSize
    })
  )
}

function detail({ pathParams, user }) {
  const parcel = getParcelById(pathParams.id)
  if (!parcel) return fail(CODE.NOT_FOUND, '包裹不存在')
  // 越权按不存在处理，不暴露其他驿站数据
  if (user.role !== 'ADMIN' && parcel.stationId !== user.station_id) return fail(CODE.NOT_FOUND, '包裹不存在')
  return ok(parcel)
}

function pickup({ pathParams, user }) {
  const parcel = getParcelById(pathParams.id)
  if (!parcel) return fail(DEMO_CODE.PARCEL_NOT_EXISTS)
  if (user.role !== 'ADMIN' && parcel.stationId !== user.station_id) return fail(DEMO_CODE.PARCEL_NOT_EXISTS)

  const result = pickupParcel(pathParams.id, user.id)
  if (result.code !== 200) return fail(result.code)
  return ok(result.vo)
}

function summary({ user }) {
  return ok(parcelSummary(scopedStation(user)))
}

function trend({ params, user }) {
  return ok(parcelTrend(scopedStation(user), params.days))
}

function ranking({ params, user }) {
  let list = parcelRanking()
  if (user.role !== 'ADMIN') list = list.filter((r) => r.stationId === user.station_id)
  // 排行支持按取件率/异常率重排（demo-design.md 7.4.7），默认按包裹量降序
  const sort = params.sort
  if (sort === 'pickupRate') list = list.slice().sort((a, b) => b.pickupRate - a.pickupRate)
  else if (sort === 'abnormalRate') list = list.slice().sort((a, b) => b.abnormalRate - a.abnormalRate)
  return ok(list)
}

export const parcelRoutes = [
  { method: 'get', path: '/parcels', roles: ALL_ROLES, handler: list },
  { method: 'get', path: '/parcels/summary', roles: ALL_ROLES, handler: summary },
  { method: 'get', path: '/parcels/trend', roles: ALL_ROLES, handler: trend },
  { method: 'get', path: '/parcels/ranking', roles: ALL_ROLES, handler: ranking },
  { method: 'get', path: '/parcels/:id', roles: ALL_ROLES, handler: detail },
  { method: 'put', path: '/parcels/:id/pickup', roles: ALL_ROLES, handler: pickup }
]
