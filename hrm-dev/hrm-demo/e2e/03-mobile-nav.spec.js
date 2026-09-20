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

  test('A3-2 老板端 Tabbar 3 项且首页宫格项数与配置一致', async ({ page }) => {
    await mobileLoginAs(page, ACCOUNT.boss)
    await expect(page.locator('.van-tabbar-item')).toHaveCount(3)
    await expect(page.locator('.entry-grid .van-grid-item')).toHaveCount(BOSS_GRID.length)
    recordMetric({ case: 'A3-2', item: '老板端宫格项数（读配置）', value: BOSS_GRID.length })
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

  test('A3-5 演示身份切换：员工端切到老板端并跳经营总览', async ({ page }) => {
    const collector = attachCollector(page)
    await mobileLoginAs(page, ACCOUNT.staff)
    await page.locator('.van-tabbar-item', { hasText: '我的' }).click()
    await expect(page).toHaveURL(/#\/staff\/me/, { timeout: 20_000 })

    const switcher = page.locator('.identity-switcher__item', { hasText: '老板' }).first()
    await expect(switcher).toBeVisible({ timeout: 25_000 })
    const cost = await timeIt(async () => {
      await switcher.click()
      await expect(page).toHaveURL(/#\/boss\/home/, { timeout: 30_000 })
    })
    await shot(page, 'A3-5-身份切换-老板端')

    // 回到「我的」复核选中态已切到老板（切换器只在「我的」页渲染）
    await page.locator('.van-tabbar-item', { hasText: '我的' }).click()
    await expect(page).toHaveURL(/#\/boss\/me/, { timeout: 25_000 })
    await expect(page.locator('.identity-switcher__item--active')).toContainText('老板')
    recordMetric({ case: 'A3-5', item: '演示身份切换(员工→老板)', ms: cost })
    expectClean(collector, 'A3-5')
  })
})
