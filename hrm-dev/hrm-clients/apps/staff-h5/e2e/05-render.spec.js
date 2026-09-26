import { test, expect } from '@playwright/test'
import { shimViteVirtualModules, shot } from '../../../e2e-utils/harness.js'
import { STAFF_ACCOUNT, staffLoginAs } from './support.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A5 多媒体与渲染 · 员工端归属子集（对照表 §三：A5-6 / A5-7（/staff/*）→ staff；
 * A5-1…A5-4 为 PC → apps/web；A5-5（/boss/trend）→ boss）。
 * MVP 裁剪：A5-7（员工端 KPI 环图 /staff/kpi）、A5-5（管理端趋势）、A5-1（看板趋势）均随包裹族 / KPI 下架删除，
 * 现仅保留 A5-6。
 * 断言口径：容器可见 + 关键子元素存在 + 不落错误态 + 全程无 console error / 未捕获异常。
 */
test.describe('A5 渲染与图表（员工端）', () => {
  test('A5-6 移动端 PageState 空状态 + Vant 图标渲染', async ({ page }) => {
    await staffLoginAs(page, STAFF_ACCOUNT.staff)
    await page.goto('/staff/#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible()

    // 演示员工无历史补卡 → 按状态筛选必为空，走 PageState 空态分支
    await page.locator('.filter-chips__chip', { hasText: '已通过' }).click()
    await expect(page.locator('.page-state__block')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.page-state__block')).toContainText('没有已通过的补卡申请')
    // 空态图标（Vant）确实渲染成 <i class="van-icon ...">
    expect(await page.locator('.page-state__block .van-icon').count()).toBeGreaterThan(0)
    await shot(page, 'A5-6-员工端空状态')
  })
})
