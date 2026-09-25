import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  DEMO_PWD,
  attachCollector,
  expectClean,
  recordMetric,
  shimViteVirtualModules,
  timeIt
} from '../../../e2e-utils/harness.js'
import { WEB_ENTRY_FILE, WEB_LOGIN_ENTRY, completePcDeviceVerifyIfNeeded } from './support.js'

/**
 * A1 页面加载与登录 · 网页端归属子集
 * 对照表 §三：A1-2/A1-3/A1-7（PC）→ apps/web；A1-6（首屏）按端各持一份。
 * A1-1（端选择页）属演示站、A1-4/A1-5（移动端两端）不在本端。
 */

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

test.describe('A1 页面加载与登录（网页端）', () => {
  test('A1-2 网页端登录流程走通并进入数据看板', async ({ page }) => {
    const collector = attachCollector(page)
    // 走 /web/login 干净入口；从入口文件 index.html 登录的路径单列为 A1-7 探针
    await page.goto(WEB_LOGIN_ENTRY, { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login-card')).toBeVisible({ timeout: 15_000 })
    await expect(page).toHaveURL(/\/login/)

    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill(DEMO_PWD)
    const loginCost = await timeIt(async () => {
      await page.locator('.login-btn').click()
      // 新设备首次登录会切到卡片第 2 步「设备验证」：按真实用户路径补完短信二次验证后才会离开登录页。
      // 仅补交互，不改断言；也不预置「设备受信」（信任只能服务端签发，security-auth-review §4.2）。
      await completePcDeviceVerifyIfNeeded(page)
      await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
    })
    // 管理员（ADMIN）落地页 = /dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })
    await page.locator('.app-menu').waitFor({ state: 'visible' })
    await expect(page.locator('.app-menu')).toContainText('数据看板')
    // 登录身份正确：头部用户区渲染「姓名 + 角色标签」（ADMIN 角色标签为「超级管理员」）
    await expect(page.locator('.header-user')).toContainText('超级管理员')
    await expect(page.locator('.not-found')).toHaveCount(0)
    recordMetric({ case: 'A1-2', item: '网页端登录(点击登录→离开登录页)', ms: loginCost })
    expectClean(collector, 'A1-2')
  })

  test('A1-7【缺陷探针】从官方入口文件 index.html 登录后应落在有效页面而非 404', async ({ page }) => {
    // 官方入口 /web/index.html 不匹配任何业务路由，未登录时守卫会记录 redirect=/web/index.html；
    // 若把该路径写进 redirect，登录后回跳一个不存在的路径 → 直接停在 404（既有 e2e 探针发现的缺陷形态）。
    await page.goto(WEB_ENTRY_FILE, { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login-card')).toBeVisible({ timeout: 15_000 })
    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill(DEMO_PWD)
    await page.locator('.login-btn').click()
    await completePcDeviceVerifyIfNeeded(page)
    await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
    await expect(page.locator('.not-found'), '登录后不应落到 404 页').toHaveCount(0)
    await expect(page.locator('.app-menu')).toBeVisible({ timeout: 15_000 })
  })

  test('A1-3 网页端错误密码给出可见提示且停留登录页', async ({ page }) => {
    await page.goto(WEB_LOGIN_ENTRY, { waitUntil: 'domcontentloaded' })
    await page.locator('.login-card').waitFor()
    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill('wrong-password-000')
    await page.locator('.login-btn').click()
    // 1001 分支文案落在表单顶部 alert（Mock 层返回，非前端臆造）
    await expect(page.locator('.login-error')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.login-error')).toContainText('用户名或密码错误')
    await expect(page).toHaveURL(/\/login/)
  })

  test('A1-6 网页端入口首屏非白屏且 console 无 error', async ({ page }) => {
    const collector = attachCollector(page)
    const cost = await timeIt(async () => {
      await page.goto(WEB_LOGIN_ENTRY, { waitUntil: 'domcontentloaded' })
      await page.locator('.login-card').waitFor({ state: 'visible', timeout: 15_000 })
    })
    const text = (await page.locator('body').innerText()).trim()
    expect(text.length, `网页端入口疑似白屏（可见文本 ${text.length} 字）`).toBeGreaterThan(20)
    expectClean(collector, '首屏 /web/login')
    recordMetric({ case: 'A1-6', item: '首屏就绪 /web/login', ms: cost })
  })
})
