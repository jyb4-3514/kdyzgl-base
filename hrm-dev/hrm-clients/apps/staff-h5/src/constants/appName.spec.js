import { describe, expect, it } from 'vitest'
import { APP_NAME_STAFF, APP_NAME_SYSTEM, resolveAppName } from './appName.js'

/**
 * 员工端命名命中矩阵（端固定化后，B3 迁移自 hrm-demo 同名 spec 的本端子集）：
 * 登录后一律「驿站助手」（本端无 ADMIN）；登录前以入口参数 as（staff / station）为准；
 * 其余（含缺失 / 异端取值如 boss）回落系统名，不预判角色、不透另一端品牌。
 */
describe('resolveAppName · 员工端命名命中矩阵', () => {
  it('登录前 as=staff → 驿站助手', () => {
    expect(resolveAppName({ as: 'staff', role: '' })).toBe(APP_NAME_STAFF)
  })

  it('登录前 as=station → 驿站助手（兼容读）', () => {
    expect(resolveAppName({ as: 'station', role: '' })).toBe(APP_NAME_STAFF)
  })

  it('登录前 as=boss 属异端 → 回落系统名（不越界透出另一端品牌）', () => {
    expect(resolveAppName({ as: 'boss', role: '' })).toBe(APP_NAME_SYSTEM)
  })

  it('无参数 → 快递驿站智汇系统（系统名，不预判角色）', () => {
    expect(resolveAppName()).toBe(APP_NAME_SYSTEM)
    expect(resolveAppName({})).toBe(APP_NAME_SYSTEM)
    expect(resolveAppName({ as: 'unknown' })).toBe(APP_NAME_SYSTEM)
  })

  it('登录后 role 覆盖 as：本端任意角色一律员工端名', () => {
    expect(resolveAppName({ as: 'boss', role: 'STATION_ADMIN' })).toBe(APP_NAME_STAFF)
    expect(resolveAppName({ as: 'staff', role: 'STAFF' })).toBe(APP_NAME_STAFF)
  })
})
