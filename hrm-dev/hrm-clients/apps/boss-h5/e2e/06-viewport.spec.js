import { test, expect } from '@playwright/test'
import { attachCollector, expectClean, horizontalOverflow, recordMetric, shimViteVirtualModules, shot } from '../../../e2e-utils/harness.js'
import { BOSS_ACCOUNT, bossLoginAs } from './support.js'

/**
 * B1 视口兼容性 · 管理端归属子集（对照表 §三：B1-2 / B1-3（移动端）→ 两端各持一份；B1-1（PC）→ apps/web）
 * 每个视口切换用 setViewportSize 触发重排，不重复整页加载（放大未打包产物的耗时与噪声）。
 */

const VIEWPORTS = [
  { w: 1920, h: 1080, tag: '1920x1080' },
  { w: 1440, h: 900, tag: '1440x900' },
  { w: 1366, h: 768, tag: '1366x768' },
  { w: 1024, h: 768, tag: '1024x768' },
  { w: 768, h: 1024, tag: '768x1024' },
  { w: 375, h: 812, tag: '375x812' }
]

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

test.describe('B1 视口兼容性（管理端）', () => {
  test('B1-2 移动端六档视口：无横向滚动条、宫格 4 列、固定栏不越界', async ({ page }) => {
    const collector = attachCollector(page)
    await page.setViewportSize({ width: 375, height: 812 })
    await bossLoginAs(page, BOSS_ACCOUNT.boss)
    await page.locator('.entry-grid .van-grid-item').first().waitFor({ state: 'visible', timeout: 40_000 })

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.w, height: vp.h })
      await page.waitForTimeout(400)

      const overflow = await horizontalOverflow(page)
      recordMetric({ case: 'B1-2', item: `管理端 ${vp.tag} 横向溢出(px)`, value: overflow })
      expect(overflow, `管理端 ${vp.tag} 出现横向滚动条（溢出 ${overflow}px）`).toBeLessThanOrEqual(1)

      // 宫格必须是 4 列：第 4 项与第 1 项同排，第 5 项换行（Vant 用 flex 布局，读 grid 模板列数无意义）
      const boxes = await page
        .locator('.entry-grid .van-grid-item')
        .evaluateAll((els) => els.map((e) => e.getBoundingClientRect()))
      if (boxes.length >= 5) {
        const sameRow = Math.abs(boxes[3].top - boxes[0].top) < 2
        const wrapped = boxes[4].top > boxes[0].top + 2
        expect(
          sameRow && wrapped,
          `管理端 ${vp.tag} 宫格非 4 列（top: ${boxes[0].top}/${boxes[3].top}/${boxes[4].top}）`
        ).toBeTruthy()
      }

      // 底部固定 Tabbar 必须完整落在视口内
      const bar = await page.locator('.van-tabbar').boundingBox()
      expect(bar.x).toBeGreaterThanOrEqual(-1)
      expect(bar.x + bar.width).toBeLessThanOrEqual(vp.w + 1)

      await shot(page, `B1-2-管理端-${vp.tag}`)
    }
    expectClean(collector, 'B1-2')
  })

  test('B1-3 移动端 375×812 窄屏：宫格触控热区 ≥44×44', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await bossLoginAs(page, BOSS_ACCOUNT.boss)
    await expect(page.locator('.entry-grid .van-grid-item').first()).toBeVisible({ timeout: 40_000 })

    const boxes = await page
      .locator('.entry-grid .van-grid-item')
      .evaluateAll((els) => els.map((e) => e.getBoundingClientRect()))
    const tooSmall = boxes.filter((b) => b.height < 44 || b.width < 44)
    recordMetric({ case: 'B1-3', item: '375 视口下小于 44×44 的宫格数', value: tooSmall.length })
    expect(tooSmall.length, `存在 ${tooSmall.length} 个触控热区不足 44×44 的宫格项`).toBe(0)
    await shot(page, 'B1-3-375窄屏宫格')
  })
})
