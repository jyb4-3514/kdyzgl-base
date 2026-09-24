import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  mobileLoginAs,
  pcLoginAs,
  pcNavigate,
  recordMetric,
  shot,
  shimViteVirtualModules,
  timeIt
} from './utils/harness.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A5 多媒体与渲染：图表（TrendChart / LineChart / MiniStats / KpiGauge）、图标、空状态组件。
 * 断言口径：容器可见 + 关键子元素存在 + 不落错误态 + 全程无 console error / 未捕获异常。
 */

test.describe('A5 渲染与图表', () => {
  test('A5-1 PC 看板 TrendChart 折线图渲染正常（双系列 + 图例 + 坐标轴）', async ({ page }) => {
    const collector = attachCollector(page)
    await pcLoginAs(page, ACCOUNT.boss)
    await pcNavigate(page, '/dashboard')

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
    await shot(page, 'A5-1-看板趋势图')
    recordMetric({ case: 'A5-1', item: '看板 TrendChart 就绪', ms: cost })
    expectClean(collector, 'A5-1')
  })

  test('A5-2 PC 考勤页 MiniStats 指标条渲染（自适应列数 + 数值非空）', async ({ page }) => {
    const collector = attachCollector(page)
    await pcLoginAs(page, ACCOUNT.boss)
    await pcNavigate(page, '/attendance')
    await expect(page.locator('.mini-stats__grid')).toBeVisible({ timeout: 30_000 })
    const cards = page.locator('.mini-stats__grid .metric-card')
    await expect.poll(async () => cards.count(), { timeout: 30_000 }).toBeGreaterThanOrEqual(4)

    // 概况是异步请求（Mock 有 120-350ms 延迟），采样必须在数值落定之后。
    // 原断言在卡片出现后立刻读文本，是在飞行中采样，读到的是加载态而非结果 —— 判据错在「采样时机」，不是数值。
    // 下面两步把「加载中」与「已出数」分开断言：加载中不得出现「—」（那是失败语义，混用即假渲染），
    // 出数后仍保留原来的「至少一张卡含数字」，断言强度不降反升。
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
    // 不得出现「全部为 —」的假渲染
    expect(
      texts.some((t) => /\d/.test(t)),
      `指标条无任何数值：${texts.join(' / ')}`
    ).toBeTruthy()
    await shot(page, 'A5-2-考勤指标条')
    expectClean(collector, 'A5-2')
  })

  test('A5-3 PC 图标渲染正常（侧栏与按钮内的 el-icon 均产出 svg）', async ({ page }) => {
    await pcLoginAs(page, ACCOUNT.boss)
    await pcNavigate(page, '/dashboard')
    const menuIcons = page.locator('.app-menu .el-icon svg')
    await expect.poll(async () => menuIcons.count(), { timeout: 15_000 }).toBeGreaterThan(10)
    const headerIcons = page.locator('.app-header .el-icon svg')
    expect(await headerIcons.count()).toBeGreaterThan(0)
    await shot(page, 'A5-3-PC图标')
  })

  test('A5-4 PC 空状态 StateBlock：无结果检索给出「暂无」而非空白', async ({ page }) => {
    const collector = attachCollector(page)
    await pcLoginAs(page, ACCOUNT.boss)
    await pcNavigate(page, '/work-order')
    await expect(page.locator('.app-main')).toContainText('工单')

    const search = page.getByPlaceholder('工单号 / 标题')
    await search.fill('zzz-not-exist-9f8e')
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.locator('.state-block--empty')).toBeVisible({ timeout: 30_000 })
    await expect(page.locator('.state-block--empty')).toContainText('没有工单')
    await shot(page, 'A5-4-PC空状态')
    expectClean(collector, 'A5-4')
  })

  test('A5-5 移动端 LineChart 趋势折线渲染正常', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.boss)
    await page.goto('/mobile.html#/boss/trend', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.line-chart')).toBeVisible({ timeout: 20_000 })
    // 不得落错误态
    expect(await page.locator('.chart-fallback__error').count()).toBe(0)
    await expect(page.locator('.line-chart__svg')).toBeVisible({ timeout: 20_000 })
    const lineCount = await page.locator('.line-chart__svg .chart-line').count()
    expect(lineCount).toBeGreaterThan(0)
    await shot(page, 'A5-5-移动端趋势图')
    expectClean(collector, 'A5-5')
  })

  test('A5-6 移动端 PageState 空状态 + Vant 图标渲染', async ({ page }) => {
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible()

    // 演示员工无历史补卡 → 按状态筛选必为空，走 PageState 空态分支
    await page.locator('.filter-chips__chip', { hasText: '已通过' }).click()
    await expect(page.locator('.page-state__block')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.page-state__block')).toContainText('没有已通过的补卡申请')
    // 空态图标（Vant）确实渲染成 <i class="van-icon ...">
    expect(await page.locator('.page-state__block .van-icon').count()).toBeGreaterThan(0)
    await shot(page, 'A5-6-移动端空状态')
  })

  test('A5-7 移动端 KPI 环形图 KpiGauge 渲染正常', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/kpi', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.gauge')).toBeVisible({ timeout: 20_000 })
    await expect(page.locator('.gauge__svg')).toBeVisible()
    const gaugeText = (await page.locator('.gauge__value').innerText()).trim()
    expect(gaugeText.length).toBeGreaterThan(0)
    recordMetric({ case: 'A5-7', item: '移动端 KPI 环图读数', value: gaugeText })
    await shot(page, 'A5-7-移动端KPI环图')
    expectClean(collector, 'A5-7')
  })
})
