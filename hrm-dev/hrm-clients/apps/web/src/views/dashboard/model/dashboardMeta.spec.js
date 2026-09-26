import { describe, expect, it } from 'vitest'
import { BASE_CARDS, buildWorkOrderMetrics } from './dashboardMeta.js'

/**
 * 看板展示口径的回归网（MVP 裁剪后）
 *
 * 原 dayOverDay（环比）与 buildRankRows（包裹排行换算）两组用例随包裹族下架删除；
 * 现仅保留工单指标形态与一期口径指标清单两组断言。
 */
describe('buildWorkOrderMetrics · 工单指标形态', () => {
  const workOrder = {
    pendingCount: 3,
    processingCount: 2,
    todayNewCount: 5,
    overSlaCount: 1,
    avgHandleMinutes: 42
  }

  it('件数项 2×2 顺序固定，超时项超 0 时标 danger', () => {
    const out = buildWorkOrderMetrics(workOrder, (m) => `${m} 分钟`)
    expect(out.primary.map((item) => item.label)).toEqual(['待处理', '处理中', '今日新增', '超时未处理'])
    expect(out.primary[3]).toMatchObject({ value: 1, danger: true })
    expect(out.avgValue).toBe('42 分钟')
  })

  it('平均时长为空时给「—」占位，不冒充 0 分钟', () => {
    const out = buildWorkOrderMetrics({ ...workOrder, overSlaCount: 0, avgHandleMinutes: null }, (m) => `${m}`)
    expect(out.primary[3].danger).toBe(false)
    expect(out.avgValue).toBe('—')
  })
})

describe('BASE_CARDS · 一期口径指标', () => {
  it('四项指标键与 /dashboard/summary 字段名一致且顺序固定', () => {
    expect(BASE_CARDS.map((card) => card.key)).toEqual([
      'employeeTotal',
      'stationTotal',
      'departmentTotal',
      'todayLoginCount'
    ])
  })
})
