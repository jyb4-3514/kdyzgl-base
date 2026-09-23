import { describe, expect, it } from 'vitest'
import { APP_NAME_BOSS, APP_NAME_STAFF, resolveAppName } from './appName.js'

/**
 * 命名命中矩阵（§10.1）：
 * 登录后以 role 为准，登录前以入口参数 as 为准，两者都无时回落老板端名（不预判角色）。
 */
describe('resolveAppName · 命名命中矩阵', () => {
  it('登录前 as=staff → 驿站助手', () => {
    expect(resolveAppName({ as: 'staff', role: '' })).toBe(APP_NAME_STAFF)
  })

  it('登录前 as=station → 驿站助手', () => {
    expect(resolveAppName({ as: 'station', role: '' })).toBe(APP_NAME_STAFF)
  })

  it('登录前 as=boss → 快递驿站智汇系统', () => {
    expect(resolveAppName({ as: 'boss', role: '' })).toBe(APP_NAME_BOSS)
  })

  it('无参数 → 快递驿站智汇系统（不预判角色）', () => {
    expect(resolveAppName()).toBe(APP_NAME_BOSS)
    expect(resolveAppName({})).toBe(APP_NAME_BOSS)
  })

  it('登录后 role 覆盖 as：ADMIN 且 as=staff 仍为老板端名', () => {
    expect(resolveAppName({ as: 'staff', role: 'ADMIN' })).toBe(APP_NAME_BOSS)
  })

  it('登录后非 ADMIN 角色一律员工端名（STATION_ADMIN / STAFF）', () => {
    expect(resolveAppName({ as: 'boss', role: 'STATION_ADMIN' })).toBe(APP_NAME_STAFF)
    expect(resolveAppName({ role: 'STAFF' })).toBe(APP_NAME_STAFF)
  })
})