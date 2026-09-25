import { test, expect } from '@playwright/test'

/**
 * P0-1 环境阻塞项取证（管理端单入口，不装载任何规避桩）
 *
 * 背景（迁移自 hrm-demo 同名 spec）：Vite 内部虚拟模块 `/@vite/client`、`/@id/...` 不带扩展名，
 * 若被中间件改写成 HTML，浏览器会拿 HTML 当 ES 模块解析 → 入口白屏。本文件对 dev server 响应契约直接断言。
 * 注意：本端应用 `base:'/boss/'`，内部模块路径带 base 前缀（实测 `/@vite/client` 为 404、`/boss/@vite/client` 为 JS）。
 */
test.describe('P0-1 dev server 契约（不使用规避桩）', () => {
  test('P0-1a Vite 虚拟模块必须返回 JavaScript，而非被改写为 HTML', async ({ request }) => {
    for (const path of ['/boss/@vite/client', '/boss/@id/__x00__plugin-vue:export-helper']) {
      const res = await request.get(path)
      const contentType = res.headers()['content-type'] || ''
      const body = await res.text()
      expect(contentType, `${path} 的 Content-Type=${contentType}，应为 application/javascript`).toContain('javascript')
      expect(body.startsWith('<!doctype html'), `${path} 返回了 HTML，说明被 history 回退中间件改写`).toBeFalsy()
    }
  })

  test('P0-1b 管理端入口在不加规避桩时应能渲染首屏（登录页）', async ({ page }) => {
    await page.goto('/boss/#/login', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.login__card'), '/boss/#/login 未渲染出首屏（疑似白屏）').toBeVisible({
      timeout: 20_000
    })
  })
})
