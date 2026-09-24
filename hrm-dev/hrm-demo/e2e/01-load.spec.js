import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  recordMetric,
  shot,
  shimViteVirtualModules,
  timeIt
} from './utils/harness.js'

/**
 * A1 页面加载：三端入口可加载、无白屏、无未捕获异常；PC 与移动端登录链路（Mock 演示账号）可走通。
 * 注：dev 中间件的虚拟模块契约由 00-dev-server.spec.js 单独守护（不装桩）；本文件只装 vConsole 空实现，见 harness.js。
 */

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

test.describe('A1 页面加载与登录', () => {
  test('A1-1 端选择页（index.html）加载成功且三张入口卡片渲染', async ({ page }) => {
    const collector = attachCollector(page)
    const cost = await timeIt(async () => {
      await page.goto('/', { waitUntil: 'domcontentloaded' })
      // 标题由 portal/main.js 覆写（初始为「加载中…」），出现即代表脚本执行成功
      await expect(page.locator('#portal-title')).not.toHaveText('加载中…', { timeout: 10_000 })
    })
    const cards = page.locator('#portal-cards .card')
    await expect(cards).toHaveCount(3)
    await expect(page.locator('#portal-cards')).toContainText('网页端（管理后台）')
    await expect(page.locator('#portal-cards')).toContainText('驿站精灵（经营视角）')
    await expect(page.locator('#portal-cards')).toContainText('员工端 · 驿站助手（作业视角）')
    // 剧本自检面板必须给出结果（不再是「校验中…」）
    await expect(page.locator('#portal-scenario')).not.toHaveText('校验中…', { timeout: 10_000 })
    await shot(page, 'A1-1-端选择页')
    recordMetric({ case: 'A1-1', item: '端选择页首屏(domContentLoaded→标题就绪)', ms: cost })
    expectClean(collector, 'A1-1')
  })

  test('A1-2 网页端登录流程走通并进入数据看板', async ({ page }) => {
    const collector = attachCollector(page)
    // 走 /login 干净入口；从入口文件 pc.html 登录的路径单列为 A1-7 探针（守卫的回跳地址缺陷已修）
    await page.goto('/login', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login-card')).toBeVisible({ timeout: 15_000 })
    await expect(page).toHaveURL(/\/login/)

    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill('demo1234')
    const loginCost = await timeIt(async () => {
      await page.locator('.login-btn').click()
      await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
    })
    // 管理员（ADMIN）落地页 = /dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 15_000 })
    await page.locator('.app-menu').waitFor({ state: 'visible' })
    await expect(page.locator('.app-menu')).toContainText('数据看板')
    // 登录身份正确：头部用户区渲染「姓名 + 角色标签」（admin 的 realName 为「系统管理员」，非用户名）
    await expect(page.locator('.header-user')).toContainText('超级管理员')
    await expect(page.locator('.not-found')).toHaveCount(0)
    await shot(page, 'A1-2-PC登录后看板')
    recordMetric({ case: 'A1-2', item: 'PC 登录(点击登录→离开登录页)', ms: loginCost })
    expectClean(collector, 'A1-2')
  })

  test('A1-7【缺陷探针】从官方入口 pc.html 登录后应落在有效页面而非 404', async ({ page }) => {
    // 端选择页的「网页端」卡片指向 pc.html，这是官方入口；未登录时守卫会记录 redirect=/pc.html，
    // 登录后路由回跳到 /pc.html —— 该路径不匹配任何业务路由，被 catch-all 兜成「页面不存在」。
    await page.goto('/pc.html', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login-card')).toBeVisible({ timeout: 15_000 })
    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill('demo1234')
    await page.locator('.login-btn').click()
    await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
    await shot(page, 'A1-7-pc.html入口登录后')
    await expect(page.locator('.not-found'), '登录后不应落到 404 页').toHaveCount(0)
    await expect(page.locator('.app-menu')).toBeVisible({ timeout: 15_000 })
  })

  test('A1-3 网页端错误密码给出可见提示且停留登录页', async ({ page }) => {
    await page.goto('/pc.html', { waitUntil: 'domcontentloaded' })
    await page.locator('.login-card').waitFor()
    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill('wrong-password-000')
    await page.locator('.login-btn').click()
    // 1001 分支文案落在表单顶部 alert（Mock 层返回，非前端臆造）
    await expect(page.locator('.login-error')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.login-error')).toContainText('用户名或密码错误')
    await expect(page).toHaveURL(/\/login/)
    await shot(page, 'A1-3-PC错误密码')
  })

  test('A1-4 移动端员工端登录走通并落地工作台', async ({ page }) => {
    const collector = attachCollector(page)
    await page.goto('/mobile.html#/login', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login__card')).toBeVisible({ timeout: 15_000 })
    const inputs = page.locator('.login__card input')
    await inputs.nth(0).fill(ACCOUNT.staff)
    await inputs.nth(1).fill('demo1234')
    const cost = await timeIt(async () => {
      await page.locator('.login__submit button').click()
      await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
    })
    // 员工（STAFF）落地 = /staff/home
    await expect(page).toHaveURL(/#\/staff\/home/, { timeout: 15_000 })
    await expect(page.locator('.van-tabbar')).toBeVisible()
    await expect(page.locator('.van-tabbar')).toContainText('首页')
    await expect(page.locator('.van-tabbar')).toContainText('消息')
    await expect(page.locator('.van-tabbar')).toContainText('我的')
    await shot(page, 'A1-4-移动端员工端工作台')
    recordMetric({ case: 'A1-4', item: '移动端员工端登录(点击登录→离开登录页)', ms: cost })
    expectClean(collector, 'A1-4')
  })

  test('A1-5 移动端管理端登录走通且落地经营总览', async ({ page }) => {
    await page.goto('/mobile.html#/login', { waitUntil: 'domcontentloaded' })
    await page.locator('.login__card').waitFor()
    const inputs = page.locator('.login__card input')
    await inputs.nth(0).fill(ACCOUNT.boss)
    await inputs.nth(1).fill('demo1234')
    await page.locator('.login__submit button').click()
    // 管理员（ADMIN）落地 = /boss/home
    await expect(page).toHaveURL(/#\/boss\/home/, { timeout: 15_000 })
    await expect(page.locator('.van-tabbar')).toBeVisible()
    await shot(page, 'A1-5-移动端老板端总览')
  })

  test('A1-6 三入口首屏均非白屏且 console 无 error', async ({ page }) => {
    const entries = [
      { url: '/', marker: '#portal-title' },
      { url: '/pc.html', marker: '.login-card' },
      { url: '/mobile.html#/login', marker: '.login__card' }
    ]
    for (const entry of entries) {
      const collector = attachCollector(page)
      const cost = await timeIt(async () => {
        await page.goto(entry.url, { waitUntil: 'domcontentloaded' })
        await page.locator(entry.marker).waitFor({ state: 'visible', timeout: 15_000 })
      })
      const text = (await page.locator('body').innerText()).trim()
      expect(text.length, `${entry.url} 疑似白屏（可见文本 ${text.length} 字）`).toBeGreaterThan(20)
      expectClean(collector, `首屏 ${entry.url}`)
      recordMetric({ case: 'A1-6', item: `首屏就绪 ${entry.url}`, ms: cost })
    }
  })
})
