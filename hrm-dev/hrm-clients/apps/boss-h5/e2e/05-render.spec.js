import { test, expect } from '@playwright/test'
import { attachCollector, expectClean, recordMetric, shimViteVirtualModules, shot } from '../../../e2e-utils/harness.js'
import { BOSS_ACCOUNT, bossGoto, bossLoginAs } from './support.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A5 多媒体与渲染 · 管理端归属子集（对照表 §三：A5-5（/boss/trend）→ boss；
 * A5-1…A5-4 为 PC → apps/web；A5-6/A5-7（/staff/*）→ staff）。
 * 断言口径：容器可见 + 关键子元素存在 + 不落错误态 + 全程无 console error / 未捕获异常。
 */
test.describe('A5 渲染与图表（管理端）', () => {
  test('A5-5 管理端包裹趋势 LineChart 渲染正常（/boss/trend）', async ({ page }) => {
    const collector = attachCollector(page)
    await bossLoginAs(page, BOSS_ACCOUNT.boss)
    await bossGoto(page, '/boss/trend')

    // 图表容器与折线：演示数据有趋势点，应落 ready 态（loading/empty 态不应出现）
    await expect(page.locator('.line-chart')).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.line-chart__plot')).toBeVisible()
    await expect(page.locator('.chart-line--inbound')).toHaveCount(1)
    await expect(page.locator('.chart-fallback--text')).toHaveCount(0)
    recordMetric({ case: 'A5-5', item: '管理端趋势折线点数', value: await page.locator('.chart-dot--inbound').count() })
    await shot(page, 'A5-5-管理端趋势折线')
    expectClean(collector, 'A5-5')
  })
})
