import { test, expect } from '@playwright/test'

/**
 * P0-1 环境阻塞项取证：本文件**不装载任何规避桩**，直接对 dev server 的响应契约做断言。
 *
 * 背景：vite.config.js 的 `hrm-demo-history-fallback` 中间件把「不带扩展名的 GET」一律改写成 /pc.html，
 * 而 Vite 的内部虚拟模块 `/@vite/client`、`/@id/__x00__plugin-vue:export-helper` 正好不带扩展名，
 * 于是浏览器拿到 HTML 而非 JS → 依赖它们的 SFC 导入失败 → 网页端与移动端入口整页白屏。
 * 中间件已加白名单跳过 /@vite、/@id、/@fs、/node_modules；本文件作为**不装任何桩**的契约回归长期保留。
 */
test.describe('P0-1 dev server 契约（不使用规避桩）', () => {
  test('P0-1a Vite 虚拟模块必须返回 JavaScript，而非被改写为 pc.html', async ({ request }) => {
    for (const path of ['/@vite/client', '/@id/__x00__plugin-vue:export-helper']) {
      const res = await request.get(path)
      const contentType = res.headers()['content-type'] || ''
      const body = await res.text()
      expect(contentType, `${path} 的 Content-Type=${contentType}，应为 application/javascript`).toContain('javascript')
      expect(body.startsWith('<!doctype html'), `${path} 返回了 HTML，说明被 history 回退中间件改写`).toBeFalsy()
    }
  })

  test('P0-1b 两个入口在不加规避桩时应能渲染首屏', async ({ page }) => {
    for (const [url, marker] of [
      ['/pc.html', '.login-card'],
      ['/mobile.html#/login', '.login__card']
    ]) {
      await page.goto(url, { waitUntil: 'domcontentloaded' })
      await expect(page.locator(marker), `${url} 未渲染出首屏（疑似白屏）`).toBeVisible({ timeout: 20_000 })
    }
  })
})
