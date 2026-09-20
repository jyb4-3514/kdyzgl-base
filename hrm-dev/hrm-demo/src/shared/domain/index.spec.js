import { describe, expect, it } from 'vitest'
import * as barrel from './index.js'

/**
 * barrel 存在的意义是「展示端只从这里取工具函数」，
 * 一旦某个模块漏了 re-export，页面会拿到 undefined 才报错，故把导出面固定下来。
 */
describe('shared/domain 公开入口', () => {
  it('各子模块能力均能从入口取到', () => {
    expect(typeof barrel.parseTime).toBe('function')
    expect(typeof barrel.monthRange).toBe('function')
    expect(typeof barrel.paginate).toBe('function')
    expect(typeof barrel.parseCsv).toBe('function')
    expect(typeof barrel.buildWorkOrderText).toBe('function')
    expect(typeof barrel.maskName).toBe('function')
    expect(typeof barrel.canAccess).toBe('function')
    expect(typeof barrel.copyText).toBe('function')
    expect(barrel.SLA_THRESHOLD_RATIO).toBe(0.25)
    expect(barrel.CSV_TYPE).toBe('text/csv;charset=utf-8')
  })
})
