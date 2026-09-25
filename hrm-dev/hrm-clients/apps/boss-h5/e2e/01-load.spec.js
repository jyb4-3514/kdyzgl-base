import { test, expect } from '@playwright/test'
import {
  attachCollector,
  completeDeviceVerifyIfNeeded,
  expectClean,
  recordMetric,
  shimViteVirtualModules,
  timeIt
} from '../../../e2e-utils/harness.js'
import { BOSS_ACCOUNT } from './support.js'

/**
 * A1 页面加载与登录 · 管理端归属子集（对照表 §三：A1-5（移动端管理端）→ boss；A1-6（首屏）按端各持一份）
 * A1-1（端选择页，属演示站）、A1-2/A1-3/A1-7（PC，属 apps/web）、A1-4（员工端）不在本端。
 */

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

test.describe('A1 页面加载与登录（管理端）', () => {
  test('A1-5 管理端登录走通并落地经营总览', async ({ page }) => {
    const collector = attachCollector(page)
    await page.goto('/boss/#/login', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login__card')).toBeVisible({ timeout: 15_000 })
    const inputs = page.locator('.login__card input')
    await inputs.nth(0).fill(BOSS_ACCOUNT.boss)
    await inputs.nth(1).fill('demo1234')
    const cost = await timeIt(async () => {
      await page.locator('.login__submit button').click()
      // 新设备首次登录会切到卡片第 2 步「设备验证」：按真实用户路径补完短信二次验证后才会离开登录页。
      // 仅补交互，不改断言；也不预置「设备受信」（信任只能服务端签发，security-auth-review §4.2）。
      await completeDeviceVerifyIfNeeded(page)
      await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
    })
    // 管理员落地 = /boss/home
    await expect(page).toHaveURL(/#\/boss\/home/, { timeout: 15_000 })
    await expect(page.locator('.van-tabbar')).toBeVisible()
    await expect(page.locator('.van-tabbar')).toContainText('首页')
    await expect(page.locator('.van-tabbar')).toContainText('消息')
    await expect(page.locator('.van-tabbar')).toContainText('我的')
    recordMetric({ case: 'A1-5', item: '管理端登录(点击登录→离开登录页)', ms: cost })
    expectClean(collector, 'A1-5')
  })

  test('A1-6 管理端入口首屏非白屏且 console 无 error', async ({ page }) => {
    const collector = attachCollector(page)
    const cost = await timeIt(async () => {
      await page.goto('/boss/#/login', { waitUntil: 'domcontentloaded' })
      await page.locator('.login__card').waitFor({ state: 'visible', timeout: 15_000 })
    })
    const text = (await page.locator('body').innerText()).trim()
    expect(text.length, `管理端入口疑似白屏（可见文本 ${text.length} 字）`).toBeGreaterThan(20)
    expectClean(collector, '首屏 /boss/#/login')
    recordMetric({ case: 'A1-6', item: '首屏就绪 /boss/#/login', ms: cost })
  })
})
