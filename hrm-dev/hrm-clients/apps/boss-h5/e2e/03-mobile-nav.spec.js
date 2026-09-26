import { test, expect } from '@playwright/test'
import { BOSS_QUICK_ENTRIES } from '../src/constants/quickEntries.js'
import { attachCollector, expectClean, recordMetric, shimViteVirtualModules, shot, timeIt } from '../../../e2e-utils/harness.js'
import { BOSS_ACCOUNT, bossGoto, bossLoginAs, bossTab } from './support.js'

/**
 * A3 移动端导航 · 管理端归属子集（对照表 §三）
 * - 自有：A3-2 / A3-7（管理端）
 * - 两端各持一份：A3-4（404）、A3-5（身份切换，本端只在管理员身份内）
 * - 不属本端：A3-1 / A3-3 / A3-6 / A3-8（员工端）
 * Tabbar 配置来自 src/constants/tabs.js；宫格项数直接读 src/constants/quickEntries.js（避免硬编码漂移）。
 */

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/** 按角色可见的宫格项（与 HomeQuickGrid 的 canAccess 口径一致：roles 缺省=可见） */
const visibleEntries = (list, role) => list.filter((item) => !item.roles || item.roles.includes(role))
const BOSS_GRID = visibleEntries(BOSS_QUICK_ENTRIES, 'ADMIN')

test.describe('A3 移动端导航（管理端）', () => {
  test('A3-2 管理端 Tabbar 恰好 3 项且切换路由正确', async ({ page }) => {
    const collector = attachCollector(page)
    await bossLoginAs(page, BOSS_ACCOUNT.boss)

    const items = page.locator('.van-tabbar-item')
    await expect(items).toHaveCount(3)
    await expect(items).toContainText(['首页', '消息', '我的'])

    const toMessage = await timeIt(async () => {
      await bossTab(page, '消息').click()
      await expect(page).toHaveURL(/#\/boss\/message/, { timeout: 20_000 })
    })
    // 消息页有两级 Tab（外：通知/待办），断言取第一组
    await expect(page.locator('.van-tabs__nav').first()).toContainText('通知')
    await expect(page.locator('.van-tabs__nav').first()).toContainText('待办')
    recordMetric({ case: 'A3-2', item: 'Tabbar 切换-消息', ms: toMessage })

    const toMe = await timeIt(async () => {
      await bossTab(page, '我的').click()
      await expect(page).toHaveURL(/#\/boss\/me/, { timeout: 20_000 })
    })
    recordMetric({ case: 'A3-2', item: 'Tabbar 切换-我的', ms: toMe })

    const toHome = await timeIt(async () => {
      await bossTab(page, '首页').click()
      await expect(page).toHaveURL(/#\/boss\/home/, { timeout: 20_000 })
    })
    recordMetric({ case: 'A3-2', item: 'Tabbar 切换-首页', ms: toHome })

    await expect(page.locator('.entry-grid .van-grid-item')).toHaveCount(BOSS_GRID.length)
    recordMetric({ case: 'A3-2', item: '管理端宫格项数（读配置）', value: BOSS_GRID.length })
    await shot(page, 'A3-2-管理端首页宫格')
    expectClean(collector, 'A3-2')
  })

  test('A3-7 管理端 hash 深链直达（应用内跳转）三个二级页均可达', async ({ page }) => {
    const collector = attachCollector(page)
    await bossLoginAs(page, BOSS_ACCOUNT.boss)

    // MVP 裁剪：原深链的 /boss/trend、/boss/rank 已下架，改用保留的二级页（人事管理 / 打卡规则 / 异常预警）
    const deepLinks = [
      { url: '/boss/#/boss/hr', title: '人事管理' },
      { url: '/boss/#/boss/attendance/rule', title: '打卡规则' },
      { url: '/boss/#/boss/alerts', title: '异常预警' }
    ]
    for (const link of deepLinks) {
      await bossGoto(page, link.url.split('#')[1])
      await expect(page.locator('.van-nav-bar__title'), `${link.url} 未落到 NavBar`).toHaveText(link.title, {
        timeout: 35_000
      })
      expect(page.url()).toContain(link.url.split('#')[1])
    }
    await shot(page, 'A3-7-管理端深链-异常预警')
    expectClean(collector, 'A3-7')
  })

  test('A3-4 未匹配 hash 路径进入管理端 404 而非静默回首页', async ({ page }) => {
    await bossLoginAs(page, BOSS_ACCOUNT.boss)
    await bossGoto(page, '/boss/no-such-page-9f8e')
    await expect(page.locator('body')).toContainText('页面不存在', { timeout: 25_000 })
    await shot(page, 'A3-4-管理端404')
  })

  test('A3-5 演示身份切换：管理端只列管理员（不出现站长 / 员工账号）', async ({ page }) => {
    const collector = attachCollector(page)
    await bossLoginAs(page, BOSS_ACCOUNT.boss)
    await bossTab(page, '我的').click()
    await expect(page).toHaveURL(/#\/boss\/me/, { timeout: 20_000 })

    // 员工端账号不属于本端入口：切换器只列管理端可登身份（管理员）
    await expect(page.locator('.identity-switcher__item', { hasText: '站长' })).toHaveCount(0)
    await expect(page.locator('.identity-switcher__item', { hasText: '员工' })).toHaveCount(0)
    const switcher = page.locator('.identity-switcher__item', { hasText: '管理员' }).first()
    await expect(switcher).toBeVisible({ timeout: 25_000 })
    await expect(page.locator('.identity-switcher__item--active')).toContainText('管理员')
    await shot(page, 'A3-5-管理端身份切换')
    expectClean(collector, 'A3-5')
  })
})
