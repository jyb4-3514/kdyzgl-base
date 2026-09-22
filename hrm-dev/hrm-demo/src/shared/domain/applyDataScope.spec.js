import { describe, expect, it } from 'vitest'
import { applyDataScope } from './applyDataScope.js'

const admin = { id: 1, role: 'ADMIN', station_id: null }
const stationAdmin = { id: 10, role: 'STATION_ADMIN', station_id: 2 }
const staff = { id: 20, role: 'STAFF', station_id: 3 }

describe('applyDataScope', () => {
  it('ADMIN 不收敛：原样带回传入的 stationId', () => {
    expect(applyDataScope({ stationId: 9, pageNum: 1 }, admin)).toEqual({ stationId: 9, pageNum: 1 })
  })

  it('STATION_ADMIN 收敛到本站（覆盖前端传值）', () => {
    expect(applyDataScope({ stationId: 9, status: '1' }, stationAdmin)).toEqual({ stationId: 2, status: '1' })
  })

  it('STAFF 收敛到本站', () => {
    expect(applyDataScope({ stationId: 9 }, staff)).toEqual({ stationId: 3 })
  })

  it('无 user 时放行（公开端点语义）', () => {
    expect(applyDataScope({ stationId: 9 }, null)).toEqual({ stationId: 9 })
    expect(applyDataScope({ stationId: 9 }, undefined)).toEqual({ stationId: 9 })
  })

  it('非法角色按非 ADMIN 收敛（不放大可见范围）', () => {
    expect(applyDataScope({ stationId: 9 }, { id: 7, role: 'UNKNOWN', station_id: 5 })).toEqual({ stationId: 5 })
  })

  it('入参为空/非对象时返回安全空对象', () => {
    expect(applyDataScope(undefined, staff)).toEqual({ stationId: 3 })
    expect(applyDataScope(null, admin)).toEqual({})
    expect(applyDataScope('bad', admin)).toEqual({})
  })

  it('已存在同名字段被覆盖（非 ADMIN），且不改动入参对象', () => {
    const params = { stationId: 9, keyword: 'a' }
    const scoped = applyDataScope(params, staff)
    expect(scoped).toEqual({ stationId: 3, keyword: 'a' })
    expect(params).toEqual({ stationId: 9, keyword: 'a' })
    expect(scoped).not.toBe(params)
  })

  it('ADMIN 返回的是新对象（不改动入参）', () => {
    const params = { stationId: 9 }
    const scoped = applyDataScope(params, admin)
    expect(scoped).toEqual({ stationId: 9 })
    expect(scoped).not.toBe(params)
  })
})
