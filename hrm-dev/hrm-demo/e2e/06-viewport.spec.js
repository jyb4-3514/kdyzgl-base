import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  horizontalOverflow,
  mobileLoginAs,
  pcLoginAs,
  pcNavigate,
  recordMetric,
  shot,
  shimViteVirtualModules
} from './utils/harness.js'

/**
 * B1 六档视口兼容性：1920×1080 / 1440×900 / 1366×768 / 1024×768 / 768×1024 / 375×812
 * 记录并判断：横向滚动条、元素越界、侧栏折叠断点、宫格换行、表格是否溢出容器。
 *
 * 实现要点：每个入口只做一次整页加载，之后用 setViewportSize 触发响应式重排 ——
 * dev 未打包产物每次整页加载要拉大量模块，逐档重新 goto 会把耗时与噪声放大到无法归因。
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

test.describe('B1 视口兼容性', () => {
  test('B1-1 PC 网页端六档视口：无白屏、侧栏折叠断点正确、表格不溢出视口', async ({ page }) => {
    const collector = attachCollector(page)
    await pcLoginAs(page, ACCOUNT.boss)
    // 登录后已落在 /dashboard（客户端内导航），不再整页 goto —— 整页加载会重跑入口与 Mock 初始化
    // （会话已落盘，刷新不丢登录态，专项回归见 A2-5/A3-6）
    await page.locator('.app-menu').waitFor({ state: 'visible', timeout: 40_000 })
    await page.locator('.app-main').waitFor({ state: 'visible', timeout: 40_000 })

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.w, height: vp.h })
      await page.waitForTimeout(400)

      const overflow = await horizontalOverflow(page)
      recordMetric({ case: 'B1-1', item: `PC ${vp.tag} 横向溢出(px)`, value: overflow })

      // 断点：<1200px 自动折叠侧栏（layout/index.vue 的 NARROW_QUERY），是显式实现的行为
      const expanded = await page.locator('.collapse-btn').getAttribute('aria-expanded')
      recordMetric({ case: 'B1-1', item: `PC ${vp.tag} 侧栏展开态`, value: expanded })
      expect(expanded, `PC ${vp.tag}：窄屏应自动折叠侧栏`).toBe(vp.w < 1200 ? 'false' : 'true')

      const mainText = (await page.locator('.app-main').innerText()).trim()
      expect(mainText.length, `PC ${vp.tag} 内容区疑似白屏`).toBeGreaterThan(10)
      await shot(page, `B1-1-PC-${vp.tag}`)
    }

    // 表格页：逐档复测，表格右边界不得越过视口
    await page.setViewportSize({ width: 1440, height: 900 })
    await pcNavigate(page, '/parcel')
    await page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 40_000 })
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.w, height: vp.h })
      await page.waitForTimeout(400)
      const box = await page.locator('.el-table').first().boundingBox()
      const over = box ? Math.round(box.x + box.width - vp.w) : null
      recordMetric({ case: 'B1-1', item: `PC ${vp.tag} 表格右越界(px)`, value: over })
      if (box) expect(over, `PC ${vp.tag} 表格越出视口 ${over}px`).toBeLessThanOrEqual(1)
    }
    expectClean(collector, 'B1-1')
  })

  test('B1-2 移动端六档视口：无横向滚动条、宫格 4 列、固定栏不越界', async ({ page }) => {
    const collector = attachCollector(page)
    await page.setViewportSize({ width: 375, height: 812 })
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.locator('.entry-grid .van-grid-item').first().waitFor({ state: 'visible', timeout: 40_000 })

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.w, height: vp.h })
      await page.waitForTimeout(400)

      const overflow = await horizontalOverflow(page)
      recordMetric({ case: 'B1-2', item: `移动端 ${vp.tag} 横向溢出(px)`, value: overflow })
      expect(overflow, `移动端 ${vp.tag} 出现横向滚动条（溢出 ${overflow}px）`).toBeLessThanOrEqual(1)

      // 宫格必须是 4 列：第 4 项与第 1 项同排，第 5 项换行（Vant 用 flex 布局，读 grid 模板列数无意义）
      const boxes = await page
        .locator('.entry-grid .van-grid-item')
        .evaluateAll((els) => els.map((e) => e.getBoundingClientRect()))
      if (boxes.length >= 5) {
        const sameRow = Math.abs(boxes[3].top - boxes[0].top) < 2
        const wrapped = boxes[4].top > boxes[0].top + 2
        expect(
          sameRow && wrapped,
          `移动端 ${vp.tag} 宫格非 4 列（top: ${boxes[0].top}/${boxes[3].top}/${boxes[4].top}）`
        ).toBeTruthy()
      }

      // 底部固定 Tabbar 必须完整落在视口内
      const bar = await page.locator('.van-tabbar').boundingBox()
      expect(bar.x).toBeGreaterThanOrEqual(-1)
      expect(bar.x + bar.width).toBeLessThanOrEqual(vp.w + 1)

      await shot(page, `B1-2-移动端-${vp.tag}`)
    }
    expectClean(collector, 'B1-2')
  })

  test('B1-3 移动端 375×812 窄屏：宫格触控热区 ≥44×44', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await mobileLoginAs(page, ACCOUNT.staff)
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
