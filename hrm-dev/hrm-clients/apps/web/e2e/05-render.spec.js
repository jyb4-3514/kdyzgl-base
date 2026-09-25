import { test, expect } from '@playwright/test'
import { ACCOUNT, attachCollector, expectClean, recordMetric, shimViteVirtualModules, timeIt } from '../../../e2e-utils/harness.js'
import { webLoginAs, webNavigate } from './support.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A5 多媒体与渲染 · 网页端归属子集（对照表 §三：A5-1…A5-4（PC）→ apps/web）
 * 断言口径：容器可见 + 关键子元素存在 + 不落错误态 + 全程无 console error / 未捕获异常。
 */

test.describe('A5 渲染与图表（网页端）', () => {
  test('A5-1 看板 TrendChart 折线图渲染正常（双系列 + 图例 + 坐标轴）', async ({ page }) => {
    const collector = attachCollector(page)
    await webLoginAs(page, ACCOUNT.boss)
    await webNavigate(page, '/dashboard')

    const cost = await timeIt(async () => {
      await expect(page.locator('.trend-svg')).toBeVisible({ timeout: 30_000 })
    })
    await expect(page.locator('.trend-legend__btn')).toHaveCount(2)
    await expect(page.locator('.trend-legend')).toContainText('入库')
    await expect(page.locator('.trend-legend')).toContainText('取件')
    // 折线路径必须有实际坐标（非空 d），否则是「画了空图」
    const paths = await page
      .locator('.trend-svg path.trend-line')
      .evaluateAll((els) => els.map((e) => e.getAttribute('d') || ''))
    expect(paths.length).toBe(2)
    expect(paths.every((d) => d.length > 10)).toBeTruthy()
    // 可访问性：SVG 有 aria-label 摘要
    await expect(page.locator('.trend-svg')).toHaveAttribute('aria-label', /包裹入库与取件趋势/)
    recordMetric({ case: 'A5-1', item: '看板 TrendChart 就绪', ms: cost })
    expectClean(collector, 'A5-1')
  })

  test('A5-2 考勤页 MiniStats 指标条渲染（自适应列数 + 数值非空）', async ({ page }) => {
    const collector = attachCollector(page)
    await webLoginAs(page, ACCOUNT.boss)
    await webNavigate(page, '/attendance')
    await expect(page.locator('.mini-stats__grid')).toBeVisible({ timeout: 30_000 })
    const cards = page.locator('.mini-stats__grid .metric-card')
    await expect.poll(async () => cards.count(), { timeout: 30_000 }).toBeGreaterThanOrEqual(4)

    // 概况是异步请求（Mock 有 120-350ms 延迟），采样必须在数值落定之后：
    // 加载中不得出现「—」（那是失败语义），出数后至少一张卡含数字。
    const loadingTexts = await cards.allInnerTexts()
    expect(
      loadingTexts.every((t) => !t.includes('—')),
      `加载期间出现「—」假数据态：${loadingTexts.join(' / ')}`
    ).toBeTruthy()

    await expect
      .poll(async () => (await cards.allInnerTexts()).filter((t) => /\d/.test(t)).length, {
        timeout: 30_000,
        message: '指标条 30s 内未出现任何数值'
      })
      .toBeGreaterThan(0)

    const texts = await cards.allInnerTexts()
    expect(texts.every((t) => t.trim().length > 0)).toBeTruthy()
    recordMetric({ case: 'A5-2', item: '考勤指标条卡片文本', value: texts.join(' / ') })
    expect(
      texts.some((t) => /\d/.test(t)),
      `指标条无任何数值：${texts.join(' / ')}`
    ).toBeTruthy()
    expectClean(collector, 'A5-2')
  })

  test('A5-3 网页端图标渲染正常（侧栏与按钮内的 el-icon 均产出 svg）', async ({ page }) => {
    await webLoginAs(page, ACCOUNT.boss)
    await webNavigate(page, '/dashboard')
    const menuIcons = page.locator('.app-menu .el-icon svg')
    await expect.poll(async () => menuIcons.count(), { timeout: 15_000 }).toBeGreaterThan(10)
    const headerIcons = page.locator('.app-header .el-icon svg')
    expect(await headerIcons.count()).toBeGreaterThan(0)
  })

  test('A5-4 网页端空状态 StateBlock：无结果检索给出「暂无」而非空白', async ({ page }) => {
    const collector = attachCollector(page)
    await webLoginAs(page, ACCOUNT.boss)
    await webNavigate(page, '/work-order')
    await expect(page.locator('.app-main')).toContainText('工单')

    const search = page.getByPlaceholder('工单号 / 标题')
    await search.fill('zzz-not-exist-9f8e')
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.locator('.state-block--empty')).toBeVisible({ timeout: 30_000 })
    await expect(page.locator('.state-block--empty')).toContainText('没有工单')
    expectClean(collector, 'A5-4')
  })
})
