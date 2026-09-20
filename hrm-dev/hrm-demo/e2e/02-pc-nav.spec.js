import { test, expect } from '@playwright/test'
import {
  ACCOUNT,
  attachCollector,
  expectClean,
  pcLoginAs,
  recordMetric,
  shot,
  shimViteVirtualModules,
  timeIt
} from './utils/harness.js'

test.beforeEach(async ({ page }) => {
  await shimViteVirtualModules(page)
})

/**
 * A2 网页端导航：侧边栏逐项点击、深链直达 + 刷新（history 路由回退，历史易错点）、404 兜底。
 * 菜单项与路径的对应关系来自 src/pc/config/menu.js（唯一真源），非臆造。
 */

const MENU = [
  { title: '数据看板', path: '/dashboard' },
  { title: '员工管理', path: '/employee' },
  { title: 'KPI 考核', path: '/employee/kpi' },
  { title: '人事管理', path: '/hr' },
  { title: '入离职', path: '/onboard' },
  { title: '部门管理', path: '/department' },
  { title: '驿站管理', path: '/station' },
  { title: '考勤管理', path: '/attendance' },
  { title: '排班管理', path: '/schedule' },
  { title: '财务管理', path: '/finance' },
  { title: '包裹管理', path: '/parcel' },
  { title: '同步任务', path: '/parcel/sync' },
  { title: '工单管理', path: '/work-order' },
  { title: '通知中心', path: '/notification' },
  { title: '个人中心', path: '/profile' }
]

test.describe('A2 网页端导航与深链', () => {
  test('A2-1 侧边栏 15 个菜单项逐项跳转，路由与标题正确且页面非空', async ({ page }) => {
    const collector = attachCollector(page)
    await pcLoginAs(page, ACCOUNT.boss)

    for (const item of MENU) {
      const menuItem = page.locator('.app-menu .el-menu-item', { hasText: item.title }).first()
      await expect(menuItem, `菜单项「${item.title}」未渲染`).toBeVisible()
      const cost = await timeIt(async () => {
        await menuItem.click()
        await expect(page).toHaveURL(new RegExp(`${item.path.replace(/\//g, '\\/')}$`), { timeout: 15_000 })
        // 等主内容区渲染出文本，排除「路由到了但页面白屏」
        await expect
          .poll(async () => (await page.locator('.app-main').innerText()).trim().length, { timeout: 15_000 })
          .toBeGreaterThan(10)
      })
      // 页面标题由路由 afterEach 写入，能反证路由 meta 生效
      await expect(page).toHaveTitle(new RegExp(item.title))
      recordMetric({ case: 'A2-1', item: `菜单切换-${item.title}`, ms: cost })

      if (item.path === '/parcel/sync' || item.path === '/finance') {
        await shot(page, `A2-1-菜单-${item.title}`)
      }
    }
    expectClean(collector, 'A2-1')
  })

  test('A2-2 PC 深链由 dev 中间件正确回退到网页端，不落到端选择页', async ({ page }) => {
    // 服务端层：无扩展名路径必须回退到 pc.html（三入口里只有网页端用 history 路由）
    for (const path of ['/dashboard', '/parcel/sync', '/work-order']) {
      const res = await page.request.get(path)
      const body = await res.text()
      expect(res.status(), `深链 ${path} 的 HTTP 状态`).toBe(200)
      expect(body, `深链 ${path} 未回退到 pc.html`).toContain('/src/pc/main.js')
      expect(body, `深链 ${path} 被回退到端选择页 index.html`).not.toContain('portal-cards')
      recordMetric({ case: 'A2-2', item: `深链 ${path} 回退目标`, value: 'pc.html' })
    }

    // 浏览器层：深链直开时页面上不得出现端选择页的入口卡片
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
    expect(await page.locator('#portal-cards').count(), '深链直开渲染出了端选择页').toBe(0)
    await shot(page, 'A2-2-深链直达')
  })

  test('A2-5【缺陷探针】PC 整页刷新后应保持登录态', async ({ page }) => {
    // 既有设计：token/user 持久化在 localStorage，「刷新页面不丢失」（hrm-admin/src/stores/auth.js 注释）。
    // 会话（db.sessions）已随之落盘（src/shared/mock/sessionStore.js），刷新后首个鉴权请求应正常通过
    await pcLoginAs(page, ACCOUNT.boss)
    await expect(page.locator('.app-menu')).toBeVisible()
    await page.reload({ waitUntil: 'domcontentloaded' })
    // 留足时间让首屏请求返回（Mock 延迟 120-350ms，这里给 10s 判定窗口）
    await page.waitForTimeout(10_000)
    const url = page.url()
    const stillInLayout = await page
      .locator('.app-menu')
      .isVisible()
      .catch(() => false)
    recordMetric({ case: 'A2-5', item: 'PC 刷新 10s 后 URL', value: url })
    recordMetric({ case: 'A2-5', item: 'PC 刷新 10s 后仍在后台布局', value: stillInLayout })
    expect(stillInLayout, `刷新后被踢出布局，当前 URL=${url}`).toBeTruthy()
    expect(url, '刷新后不应被踢回登录页').not.toContain('/login')
  })

  test('A2-3 未匹配路径进入 PC 404 页而非静默跳看板', async ({ page }) => {
    await pcLoginAs(page, ACCOUNT.boss)
    await page.goto('/no-such-page-9f8e', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.not-found')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.not-found')).toContainText('页面不存在')
    await expect(page).toHaveTitle(/页面不存在/)
    await shot(page, 'A2-3-PC404')
  })

  test('A2-4 侧边栏折叠按钮可用（aria-expanded 切换）', async ({ page }) => {
    await pcLoginAs(page, ACCOUNT.boss)
    const btn = page.locator('.collapse-btn')
    await expect(btn).toHaveAttribute('aria-expanded', 'true')
    await btn.click()
    await expect(btn).toHaveAttribute('aria-expanded', 'false')
    await btn.click()
    await expect(btn).toHaveAttribute('aria-expanded', 'true')
  })
})
