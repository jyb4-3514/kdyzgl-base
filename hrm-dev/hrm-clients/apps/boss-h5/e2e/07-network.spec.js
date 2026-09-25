import { test, expect } from '@playwright/test'
import { ACCOUNT, attachCollector, recordMetric, shimViteVirtualModules, shot } from '../../../e2e-utils/harness.js'
import { bossLoginAs, bossTab } from './support.js'

/**
 * B2 网络条件 · 管理端归属子集（对照表 §三）
 * - B2-3（离线降级表现）→ 两端各持一份（本文件为管理端一份）
 * - B2-1（端选择页）/ B2-2（PC 登录页）经实测属演示站 / 网页端，须先按被测端归位（对照表 §七 第 3 项），
 *   本端不含；其归属见 apps/web 的 B2 用例（B5）。
 *
 * 说明：被测对象是 Vite dev server 提供的**未打包**产物，窄带下首屏耗时必然被放大；本用例记录实测数字，不理想化。
 */

const PROFILES = {
  offline: { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 }
}

async function applyNetwork(context, page, profile) {
  const client = await context.newCDPSession(page)
  await client.send('Network.enable')
  await client.send('Network.emulateNetworkConditions', profile)
  return client
}

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

test.describe('B2 网络条件（管理端）', () => {
  test.setTimeout(420_000)

  test('B2-3 离线（offline）降级表现：应用内跳转可用、整页刷新失败、无未捕获异常', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 } })
    const page = await context.newPage()
    await shimViteVirtualModules(page)
    const collector = attachCollector(page)

    // 1) 正常联网先加载管理端并登录（Mock 数据在内存，后续不依赖网络）
    await bossLoginAs(page, ACCOUNT.boss)
    await expect(page.locator('.van-tabbar')).toBeVisible()

    // 2) 预热目标路由：dev 下每个路由是独立模块请求，先走一遍「我的」再回首页，确保 chunk 已在内存
    await bossTab(page, '我的').click()
    await expect(page).toHaveURL(/#\/boss\/me/, { timeout: 15_000 })
    await expect(page.locator('.identity-switcher')).toBeVisible({ timeout: 15_000 })
    await bossTab(page, '首页').click()
    await expect(page).toHaveURL(/#\/boss\/home/, { timeout: 15_000 })

    // 3) 断网后 SPA 内跳转仍应可用（资源已在内存，Mock 不发真实请求）
    await applyNetwork(context, page, PROFILES.offline)
    const navStart = Date.now()
    let inAppNavOk = true
    let inAppNavError = ''
    try {
      await bossTab(page, '我的').click()
      await expect(page).toHaveURL(/#\/boss\/me/, { timeout: 15_000 })
      await expect(page.locator('.identity-switcher')).toBeVisible({ timeout: 15_000 })
    } catch (e) {
      inAppNavOk = false
      inAppNavError = String(e.message).split('\n')[0].slice(0, 200)
    }
    recordMetric({
      case: 'B2-3',
      item: '离线后 SPA 内跳转（已加载页面）',
      ms: Date.now() - navStart,
      ok: inAppNavOk,
      err: inAppNavError
    })
    await shot(page, 'B2-3-离线后应用内跳转')

    // 4) 离线刷新：整页资源不可达，浏览器层直接失败（无 Service Worker，无应用级兜底）
    let reloadOk = true
    let reloadError = ''
    try {
      await page.reload({ waitUntil: 'domcontentloaded', timeout: 20_000 })
    } catch (e) {
      reloadOk = false
      reloadError = String(e.message).split('\n')[0].slice(0, 200)
    }
    recordMetric({ case: 'B2-3', item: '离线整页刷新', ok: reloadOk, err: reloadError })
    expect(reloadOk, '离线状态下整页刷新不应成功（无 SW 缓存）').toBeFalsy()

    // 5) 离线新开页面首屏：同样应失败（CDP 网络仿真按 target 生效，新页面必须单独设置）
    const fresh = await context.newPage()
    await shimViteVirtualModules(fresh)
    await applyNetwork(context, fresh, PROFILES.offline)
    let freshOk = true
    try {
      await fresh.goto('/boss/#/login', { waitUntil: 'domcontentloaded', timeout: 20_000 })
      await fresh.locator('.login__card').waitFor({ state: 'visible', timeout: 20_000 })
    } catch (e) {
      freshOk = false
    }
    recordMetric({ case: 'B2-3', item: '离线新开管理端首屏', ok: freshOk })
    expect(freshOk, '离线状态下新开页面不应加载成功').toBeFalsy()

    expect(collector.fatal().pageErrors, '离线场景不应出现未捕获 JS 异常').toEqual([])
    await context.close()
  })
})
