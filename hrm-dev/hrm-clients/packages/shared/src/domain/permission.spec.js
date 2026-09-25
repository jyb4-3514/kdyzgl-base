import { describe, expect, it } from 'vitest'
import { canAccess } from './permission.js'

describe('canAccess', () => {
  it('未声明 roles 视为不限制（公开页/演示页）', () => {
    expect(canAccess([], { role: 'STAFF' })).toBe(true)
    expect(canAccess(null, { role: 'STAFF' })).toBe(true)
    expect(canAccess(undefined, null)).toBe(true)
  })

  it('命中角色放行', () => {
    expect(canAccess(['ADMIN', 'STATION_ADMIN'], { role: 'STATION_ADMIN' })).toBe(true)
  })

  it('未命中角色拦截', () => {
    expect(canAccess(['ADMIN'], { role: 'STAFF' })).toBe(false)
  })

  it('user 为空时拦截（对应会话清空后的兜底语义）', () => {
    expect(canAccess(['ADMIN'], null)).toBe(false)
    expect(canAccess(['ADMIN'], {})).toBe(false)
  })
})
