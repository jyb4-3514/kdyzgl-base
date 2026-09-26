import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  horizontalOverflow,
  recordMetric,
  shimViteVirtualModules
} from '../../../e2e-utils/harness.js'
import { webLoginAs, webNavigate } from './support.js'

/**
 * B1 六档视口兼容性 · 网页端归属子集（对照表 §三：B1-1（PC 六档视口）→ apps/web）
 * 1920×1080 / 1440×900 / 1366×768 / 1024×768 / 768×1024 / 375×812
 * 记录并判断：横向滚动条、元素越界、侧栏折叠断点、表格是否溢出容器。
 *
 * 实现要点：只做一次整页加载（登录后落在 /dashboard），之后用 setViewportSize 触发响应式重排。
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

test.describe('B1 视口兼容性（网页端）', () => {
  test('B1-1 网页端六档视口：无白屏、侧栏折叠断点正确、表格不溢出视口', async ({ page }) => {
    const collector = attachCollector(page)
    await webLoginAs(page, ACCOUNT.boss)
    // 登录后已落在 /dashboard（客户端内导航），不再整页 goto —— 整页加载会重跑入口与 Mock 初始化
    await page.locator('.app-menu').waitFor({ state: 'visible', timeout: 40_000 })
    await page.locator('.app-main').waitFor({ state: 'visible', timeout: 40_000 })

    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.w, height: vp.h })
      await page.waitForTimeout(400)

      const overflow = await horizontalOverflow(page)
      recordMetric({ case: 'B1-1', item: `网页端 ${vp.tag} 横向溢出(px)`, value: overflow })

      // 断点：<1200px 自动折叠侧栏（layout/index.vue 的 NARROW_QUERY），是显式实现的行为
      const expanded = await page.locator('.collapse-btn').getAttribute('aria-expanded')
      recordMetric({ case: 'B1-1', item: `网页端 ${vp.tag} 侧栏展开态`, value: expanded })
      expect(expanded, `网页端 ${vp.tag}：窄屏应自动折叠侧栏`).toBe(vp.w < 1200 ? 'false' : 'true')

      const mainText = (await page.locator('.app-main').innerText()).trim()
      expect(mainText.length, `网页端 ${vp.tag} 内容区疑似白屏`).toBeGreaterThan(10)
    }

    // 表格页：逐档复测，表格右边界不得越过视口
    await page.setViewportSize({ width: 1440, height: 900 })
    await webNavigate(page, '/work-order')
    await page.locator('.el-table').first().waitFor({ state: 'visible', timeout: 40_000 })
    for (const vp of VIEWPORTS) {
      await page.setViewportSize({ width: vp.w, height: vp.h })
      await page.waitForTimeout(400)
      const box = await page.locator('.el-table').first().boundingBox()
      const over = box ? Math.round(box.x + box.width - vp.w) : null
      recordMetric({ case: 'B1-1', item: `网页端 ${vp.tag} 表格右越界(px)`, value: over })
      if (box) expect(over, `网页端 ${vp.tag} 表格越出视口 ${over}px`).toBeLessThanOrEqual(1)
    }
    expectClean(collector, 'B1-1')
  })
})
