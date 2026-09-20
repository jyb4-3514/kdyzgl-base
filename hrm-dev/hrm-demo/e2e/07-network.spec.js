import { test, expect } from '@playwright/test'
import { ACCOUNT, attachCollector, mobileLoginAs, recordMetric, shot, shimViteVirtualModules } from './utils/harness.js'

/**
 * B2 网络条件：CDP 模拟 Fast 3G / Slow 3G / 离线，记录首屏耗时变化与降级表现。
 *
 * 说明：被测对象是 Vite dev server 提供的**未打包**产物，PC/Mobile 入口会按模块粒度发起大量请求，
 * 因此在窄带下首屏耗时必然被放大；本用例记录的是实测数字（不理想化、不编造），并在报告中如实说明口径。
 */

const PROFILES = {
  fast3g: {
    offline: false,
    latency: 150,
    downloadThroughput: (1.6 * 1024 * 1024) / 8,
    uploadThroughput: (750 * 1024) / 8
  },
  slow3g: { offline: false, latency: 400, downloadThroughput: (400 * 1024) / 8, uploadThroughput: (400 * 1024) / 8 },
  offline: { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 }
}

async function applyNetwork(context, page, profile) {
  const client = await context.newCDPSession(page)
  await client.send('Network.enable')
  await client.send('Network.emulateNetworkConditions', profile)
  return client
}

/** 首屏测量：domContentLoaded 起算 → 关键标记可见；同时回读浏览器 navigation 计时 */
async function measureFirstScreen(page, url, marker, timeout) {
  const start = Date.now()
  let ok = true
  let error = ''
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout })
    await page.locator(marker).first().waitFor({ state: 'visible', timeout })
  } catch (e) {
    ok = false
    error = String(e.message).split('\n')[0].slice(0, 200)
  }
  const ms = Date.now() - start
  let nav = null
  if (ok) {
    nav = await page
      .evaluate(() => {
        const entry = performance.getEntriesByType('navigation')[0]
        if (!entry) return null
        return {
          domContentLoaded: Math.round(entry.domContentLoadedEventEnd),
          loadEventEnd: Math.round(entry.loadEventEnd),
          transferSize: entry.transferSize
        }
      })
      .catch(() => null)
  }
  return { ms, ok, error, nav }
}

test.describe('B2 网络条件', () => {
  test.setTimeout(420_000)

  test('B2-1 端选择页在 Fast 3G / Slow 3G 下的首屏耗时', async ({ browser }) => {
    for (const [name, profile] of [
      ['Fast 3G', PROFILES.fast3g],
      ['Slow 3G', PROFILES.slow3g]
    ]) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
      const page = await context.newPage()
      await shimViteVirtualModules(page)
      await applyNetwork(context, page, profile)
      const r = await measureFirstScreen(page, 'http://localhost:5188/', '#portal-cards .card', 180_000)
      recordMetric({ case: 'B2-1', item: `端选择页首屏 ${name}`, ms: r.ms, ok: r.ok, nav: r.nav, err: r.error })
      if (r.ok) {
        await expect(page.locator('#portal-cards .card')).toHaveCount(3)
        await shot(page, `B2-1-端选择页-${name}`)
      }
      expect(r.ok, `${name} 下端选择页未在预算内完成首屏：${r.error}`).toBeTruthy()
      await context.close()
    }
  })

  test('B2-2 网页端登录页在 Fast 3G / Slow 3G 下的首屏耗时', async ({ browser }) => {
    for (const [name, profile] of [
      ['Fast 3G', PROFILES.fast3g],
      ['Slow 3G', PROFILES.slow3g]
    ]) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
      const page = await context.newPage()
      await shimViteVirtualModules(page)
      await applyNetwork(context, page, profile)
      const r = await measureFirstScreen(page, 'http://localhost:5188/pc.html', '.login-card', 240_000)
      recordMetric({ case: 'B2-2', item: `PC 登录页首屏 ${name}`, ms: r.ms, ok: r.ok, nav: r.nav, err: r.error })
      if (r.ok) await shot(page, `B2-2-PC登录页-${name}`)
      expect(r.ok, `${name} 下 PC 登录页未在预算内完成首屏：${r.error}`).toBeTruthy()
      await context.close()
    }
  })

  test('B2-3 离线（offline）降级表现', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 } })
    const page = await context.newPage()
    await shimViteVirtualModules(page)
    const collector = attachCollector(page)

    // 1) 正常联网先加载移动端并登录（Mock 数据在内存，后续不依赖网络）
    await mobileLoginAs(page, ACCOUNT.staff)
    await expect(page.locator('.van-tabbar')).toBeVisible()

    // 2) 预热目标路由：dev 下每个路由是独立的模块请求，若从未访问过就断网，切换必然因取不到 chunk 失败。
    //    本用例要验的是「资源已在内存时应用内跳转仍可用」，不是「离线能否首次加载一条新路由」，
    //    故先把「我的」走一遍再回首页（原实现默认该 chunk 已加载，前提不成立 → 用例会随机失败）
    const tab = (text) => page.locator('.van-tabbar-item', { hasText: text }).first()
    await tab('我的').click()
    await expect(page).toHaveURL(/#\/staff\/me/, { timeout: 15_000 })
    await expect(page.locator('.identity-switcher')).toBeVisible({ timeout: 15_000 })
    await tab('首页').click()
    await expect(page).toHaveURL(/#\/staff\/home/, { timeout: 15_000 })

    // 3) 断网后 SPA 内跳转仍应可用（资源已在内存，Mock 不发真实请求）
    await applyNetwork(context, page, PROFILES.offline)
    const navStart = Date.now()
    let inAppNavOk = true
    let inAppNavError = ''
    try {
      await tab('我的').click()
      await expect(page).toHaveURL(/#\/staff\/me/, { timeout: 15_000 })
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

    // 5) 离线新开页面首屏：同样应失败，且失败信息可读（CDP 网络仿真按 target 生效，新页面必须单独设置）
    const fresh = await context.newPage()
    await shimViteVirtualModules(fresh)
    await applyNetwork(context, fresh, PROFILES.offline)
    const freshResult = await measureFirstScreen(fresh, 'http://localhost:5188/mobile.html', '.login__card', 20_000)
    recordMetric({ case: 'B2-3', item: '离线新开移动端首屏', ok: freshResult.ok, err: freshResult.error })
    expect(freshResult.ok, '离线状态下新开页面不应加载成功').toBeFalsy()

    expect(collector.fatal().pageErrors, '离线场景不应出现未捕获 JS 异常').toEqual([])
    await context.close()
  })
})
