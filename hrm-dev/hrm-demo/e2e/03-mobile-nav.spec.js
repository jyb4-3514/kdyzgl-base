import { test, expect } from '@playwright/test'
import { BOSS_QUICK_ENTRIES, STAFF_QUICK_ENTRIES } from '../src/mobile/constants/quickEntries.js'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  mobileLoginAs,
  recordMetric,
  shot,
  shimViteVirtualModules,
  timeIt
} from './utils/harness.js'

/**
 * A3 移动端导航：Tabbar（3 项）切换、hash 深链直达 + 刷新、演示身份切换。
 * Tabbar 配置来自 src/mobile/constants/tabs.js；宫格项数直接读 src/mobile/constants/quickEntries.js（避免硬编码漂移）。
 */

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/** 按角色可见的宫格项（与 HomeQuickGrid 的 canAccess 口径一致：roles 缺省=可见） */
const visibleEntries = (list, role) => list.filter((item) => !item.roles || item.roles.includes(role))
const BOSS_GRID = visibleEntries(BOSS_QUICK_ENTRIES, 'ADMIN')
const STAFF_GRID = visibleEntries(STAFF_QUICK_ENTRIES, 'STAFF')

test.describe('A3 移动端导航', () => {
  test('A3-1 员工端 Tabbar 恰好 3 项且切换路由正确', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)

    const items = page.locator('.van-tabbar-item')
    await expect(items).toHaveCount(3)
    await expect(items).toContainText(['首页', '消息', '我的'])

    const tab = (text) => page.locator('.van-tabbar-item', { hasText: text }).first()

    const toMessage = await timeIt(async () => {
      await tab('消息').click()
      await expect(page).toHaveURL(/#\/staff\/message/, { timeout: 20_000 })
    })
    // 消息页有两级 Tab（外：通知/待办，内：全部/未读），断言取第一组
    await expect(page.locator('.van-tabs__nav').first()).toContainText('通知')
    await expect(page.locator('.van-tabs__nav').first()).toContainText('待办')
    recordMetric({ case: 'A3-1', item: 'Tabbar 切换-消息', ms: toMessage })

    const toMe = await timeIt(async () => {
      await tab('我的').click()
      await expect(page).toHaveURL(/#\/staff\/me/, { timeout: 20_000 })
    })
    recordMetric({ case: 'A3-1', item: 'Tabbar 切换-我的', ms: toMe })

    const toHome = await timeIt(async () => {
      await tab('首页').click()
      await expect(page).toHaveURL(/#\/staff\/home/, { timeout: 20_000 })
    })
    recordMetric({ case: 'A3-1', item: 'Tabbar 切换-首页', ms: toHome })

    await expect(page.locator('.entry-grid .van-grid-item')).toHaveCount(STAFF_GRID.length)
    recordMetric({ case: 'A3-1', item: '员工端宫格项数（读配置）', value: STAFF_GRID.length })
    await shot(page, 'A3-1-员工端首页宫格')
    expectClean(collector, 'A3-1')
  })

  test('A3-2 管理端 Tabbar 3 项且首页宫格项数与配置一致', async ({ page }) => {
    await mobileLoginAs(page, ACCOUNT.boss)
    await expect(page.locator('.van-tabbar-item')).toHaveCount(3)
    await expect(page.locator('.entry-grid .van-grid-item')).toHaveCount(BOSS_GRID.length)
    recordMetric({ case: 'A3-2', item: '管理端宫格项数（读配置）', value: BOSS_GRID.length })
    await shot(page, 'A3-2-老板端首页')
  })

  test('A3-3 移动端 hash 深链直达（应用内跳转）三个页面均可达', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)

    // 登录后位于 #/staff/home，以下均只变更 hash（不触发整页加载）
    const deepLinks = [
      { url: '/mobile.html#/staff/attendance/makeup', marker: '.makeup-list' },
      { url: '/mobile.html#/staff/workorder/create', marker: '.create-page' },
      { url: '/mobile.html#/staff/attendance', marker: '.clock' }
    ]
    for (const link of deepLinks) {
      await page.goto(link.url, { waitUntil: 'domcontentloaded' })
      await expect(page.locator(link.marker)).toBeVisible({ timeout: 35_000 })
      expect(page.url()).toContain(link.url.split('#')[1])
    }
    await shot(page, 'A3-3-移动端深链-打卡页')
    expectClean(collector, 'A3-3')
  })

  test('A3-6【缺陷探针】移动端深链整页刷新后应仍停留在该页', async ({ page }) => {
    // 与 A2-5 同源：会话（db.sessions）已落盘，整页刷新后鉴权请求应正常通过，不会被踢回登录页
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/attendance/makeup', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.makeup-list')).toBeVisible({ timeout: 35_000 })

    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(8_000)
    const url = page.url()
    const stillOnPage = await page
      .locator('.makeup-list')
      .isVisible()
      .catch(() => false)
    recordMetric({ case: 'A3-6', item: '移动端刷新 8s 后 URL', value: url })
    recordMetric({ case: 'A3-6', item: '移动端刷新 8s 后仍在目标页', value: stillOnPage })
    expect(stillOnPage, `刷新后被踢出目标页，当前 URL=${url}`).toBeTruthy()
  })

  test('A3-4 未匹配 hash 路径进入移动端 404 而非静默回首页', async ({ page }) => {
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.goto('/mobile.html#/staff/no-such-page-9f8e', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('body')).toContainText('页面不存在', { timeout: 25_000 })
    await shot(page, 'A3-4-移动端404')
  })

  test('A3-5 演示身份切换：员工端切到管理端并跳经营总览', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.locator('.van-tabbar-item', { hasText: '我的' }).click()
    await expect(page).toHaveURL(/#\/staff\/me/, { timeout: 20_000 })

    const switcher = page.locator('.identity-switcher__item', { hasText: '管理员' }).first()
    await expect(switcher).toBeVisible({ timeout: 25_000 })
    const cost = await timeIt(async () => {
      await switcher.click()
      await expect(page).toHaveURL(/#\/boss\/home/, { timeout: 30_000 })
    })
    await shot(page, 'A3-5-身份切换-老板端')

    // 回到「我的」复核选中态已切到管理员（切换器只在「我的」页渲染）
    await page.locator('.van-tabbar-item', { hasText: '我的' }).click()
    await expect(page).toHaveURL(/#\/boss\/me/, { timeout: 25_000 })
    await expect(page.locator('.identity-switcher__item--active')).toContainText('管理员')
    recordMetric({ case: 'A3-5', item: '演示身份切换(员工→管理员)', ms: cost })
    expectClean(collector, 'A3-5')
  })

  test('A3-7 管理端考勤六卡全部可下钻明细页，维度二次切换不新增历史', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.boss)
    await page.goto('/mobile.html#/boss/attendance', { waitUntil: 'domcontentloaded' })

    // 六张指标卡必须是原生 button（有 @click 时 StatCard 自动渲染为 button），读屏附「可查看名单」
    const cards = page.locator('.stat-grid button.stat-card')
    await expect(cards).toHaveCount(6, { timeout: 30_000 })
    await expect(cards.first()).toHaveAttribute('aria-label', /可查看名单/)

    await page.locator('.stat-grid button.stat-card', { hasText: '应到' }).click()
    await expect(page).toHaveURL(/#\/boss\/attendance\/detail\?dim=SHOULD/, { timeout: 20_000 })
    await expect(page.locator('.chips')).toBeVisible()
    await expect(page.locator('.van-nav-bar__title')).toContainText('应到明细')

    // 维度二次切换走 router.replace：切到缺卡后回退一步应直接回概览页，而不是回上一个维度
    await page.locator('.chips .fchip', { hasText: '缺卡' }).click()
    await expect(page).toHaveURL(/dim=ABSENT/, { timeout: 20_000 })
    await expect(page.locator('.van-nav-bar__title')).toContainText('缺卡明细')
    await shot(page, 'A3-7-考勤明细-缺卡')

    await page.goBack()
    await expect(page).toHaveURL(/#\/boss\/attendance$/, { timeout: 20_000 })
    expectClean(collector, 'A3-7')
  })

  test('A3-8 员工端通知可打开阅读页：标题全量、公告无动作区、返回回到列表', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.locator('.van-tabbar-item', { hasText: '消息' }).click()
    await expect(page).toHaveURL(/#\/staff\/message/, { timeout: 20_000 })

    // 「公告」是手工发布通知独有的标签（系统公告通知的类型标签是「系统公告」，故用精确匹配区分）
    const row = page
      .locator('.list-item', { has: page.locator('.status-tag', { hasText: /^公告$/ }) })
      .first()
    await expect(row).toBeVisible({ timeout: 30_000 })
    const title = (await row.locator('.list-item__title > span:first-child').textContent()).trim()

    await row.click()
    await expect(page).toHaveURL(/#\/staff\/message\/notice\?id=\d+/, { timeout: 20_000 })

    // 阅读页标题不被列表的省略规则截断：h1 文本等于列表项标题全文
    await expect(page.locator('h1')).toHaveText(title, { timeout: 20_000 })
    // 公告没有业务对象：动作区整块不渲染（[role=toolbar] 计数为 0）
    await expect(page.locator('[role=toolbar]')).toHaveCount(0)
    await shot(page, 'A3-8-通知阅读页-公告')

    await page.goBack()
    await expect(page).toHaveURL(/#\/staff\/message$/, { timeout: 20_000 })
    await expect(page.locator('.list-item', { hasText: title }).first()).toBeVisible()
    expectClean(collector, 'A3-8')
  })
})
