import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect } from '@playwright/test'

/** 演示账号（仅存在于 Mock 数据，非任何环境真实凭据） */
export const DEMO_PWD = 'demo1234'
/** 演示身份用户名：管理员 / 城东驿站站长 / 城东驿站员工 */
export const ACCOUNT = { boss: 'admin', station: 'st001_admin', staff: 'st001_staff' }

export const EVIDENCE_DIR = fileURLToPath(new URL('../evidence/', import.meta.url))
const METRICS_FILE = path.join(EVIDENCE_DIR, 'metrics.json')

fs.mkdirSync(EVIDENCE_DIR, { recursive: true })

/** 追加一条实测指标（断言之外的事实记录，报告性能章节直接引用，禁止手填） */
export function recordMetric(entry) {
  let all = []
  try {
    all = JSON.parse(fs.readFileSync(METRICS_FILE, 'utf8'))
  } catch {
    all = []
  }
  all.push({ at: new Date().toISOString(), ...entry })
  fs.writeFileSync(METRICS_FILE, JSON.stringify(all, null, 2), 'utf8')
}

/** 除禁止的噪声外，其余一律视为真实报错（宁多报不漏报） */
const NOISE = [
  /Download the Vue Devtools/i,
  /\[vite\] connect/i,
  /WebSocket connection to .* was interrupted/i // 离线用例会主动断网，非缺陷
]

/**
 * 测试前置桩：必须在 page.goto 之前调用
 *
 * 只剩 vConsole 一项 —— Vite 虚拟模块（/@vite/client、/@id/…）曾被 dev 中间件的 history 回退
 * 改写成 HTML 导致入口白屏，那两条规避路由已随 vite.config.js 的修复一并删除，
 * 契约由 `e2e/00-dev-server.spec.js`（不装任何桩）长期守护。
 */
export async function shimViteVirtualModules(page) {
  /**
   * vConsole 只在 DEV 动态引入，但其悬浮按钮固定在右下角，会拦截抽屉底部「发布」等按钮的点击
   * （实测 A4-4：`.vc-switch` intercepts pointer events）。它是开发调试工具、不属被测功能，
   * 这里替换为空实现（保持模块可加载、不产生 console error）。
   */
  await page.route('**/*vconsole*.js*', (route) =>
    route.fulfill({ status: 200, contentType: 'application/javascript', body: 'export default class VConsole {}\n' })
  )
}

const meaningful = (list) => list.filter((text) => !NOISE.some((re) => re.test(String(text))))

/**
 * 挂载采集器：页面 JS 错误 / console error+warning / 未捕获 Promise / 资源 4xx-5xx / 请求失败。
 * 返回的对象是「原始记录」，断言前统一走 count() 过滤噪声。
 */
export function attachCollector(page) {
  const c = { consoleErrors: [], consoleWarnings: [], pageErrors: [], httpErrors: [], requestFailures: [] }
  page.on('console', (msg) => {
    const text = msg.text()
    if (msg.type() === 'error') c.consoleErrors.push(text)
    else if (msg.type() === 'warning') c.consoleWarnings.push(text)
  })
  page.on('pageerror', (err) => c.pageErrors.push(err && err.message ? err.message : String(err)))
  page.on('response', (res) => {
    if (res.status() >= 400) c.httpErrors.push(`${res.status()} ${res.url()}`)
  })
  page.on('requestfailed', (req) => {
    const reason = (req.failure() && req.failure().errorText) || 'unknown'
    c.requestFailures.push(`${req.method()} ${req.url()} :: ${reason}`)
  })
  return {
    raw: c,
    fatal() {
      return {
        pageErrors: c.pageErrors,
        consoleErrors: meaningful(c.consoleErrors),
        httpErrors: c.httpErrors,
        requestFailures: c.requestFailures
      }
    },
    warnings: () => meaningful(c.consoleWarnings)
  }
}

/** 断言「无未捕获异常 + 无 console error + 无 4xx/5xx 资源」 */
export function expectClean(collector, label = '') {
  const { pageErrors, consoleErrors, httpErrors } = collector.fatal()
  expect(pageErrors, `${label} 未捕获异常：${JSON.stringify(pageErrors)}`).toEqual([])
  expect(consoleErrors, `${label} console error：${JSON.stringify(consoleErrors)}`).toEqual([])
  expect(httpErrors, `${label} 资源/接口异常：${JSON.stringify(httpErrors)}`).toEqual([])
}

/** 截图落盘（文件名带用例编号，天然唯一；返回相对仓库的路径供报告引用） */
let shotSeq = 0
export async function shot(page, name, opts = {}) {
  shotSeq += 1
  const safe = String(name)
    .replace(/[^\w\u4e00-\u9fa5-]+/g, '_')
    .slice(0, 70)
  // 落在 shots/ 子目录：globalSetup 只清顶层旧图与 artifacts，避免分文件跑用例时互相覆盖证据
  const dir = path.join(EVIDENCE_DIR, 'shots')
  fs.mkdirSync(dir, { recursive: true })
  const file = `${String(shotSeq).padStart(2, '0')}-${safe}.png`
  await page.screenshot({ path: path.join(dir, file), fullPage: false, ...opts })
  return `e2e/evidence/shots/${file}`
}

/** 计时包装：返回真实毫秒耗时，供性能断言与报告 */
export async function timeIt(fn) {
  const start = Date.now()
  await fn()
  return Date.now() - start
}

/**
 * PC 端「客户端内导航」：点侧栏菜单项切页，避免整页刷新。
 * 为什么不用 page.goto：整页刷新会重跑入口与 Mock 初始化，除深链/刷新专项用例外，
 * 其余用例一律走客户端内导航，才能把「页面渲染/交互」与「会话与刷新」两类问题分开定位
 * （会话已落盘，刷新不再丢登录态，专项回归见 A2-5 / A3-6）。
 */
export const PC_MENU_TITLE_BY_PATH = {
  '/dashboard': '数据看板',
  '/employee': '员工管理',
  '/employee/kpi': 'KPI 考核',
  '/hr': '人事管理',
  '/onboard': '入离职',
  '/department': '部门管理',
  '/station': '驿站管理',
  '/attendance': '考勤管理',
  '/schedule': '排班管理',
  '/finance': '财务管理',
  '/parcel': '包裹管理',
  '/parcel/sync': '同步任务',
  '/work-order': '工单管理',
  '/notification': '通知中心',
  '/profile': '个人中心'
}

/** 各菜单项所属分组（窄屏自动折叠后再展开时，Element 不会自动展开分组，需要显式点开） */
export const PC_MENU_GROUP_BY_PATH = {
  '/employee': '组织人事',
  '/employee/kpi': '组织人事',
  '/hr': '组织人事',
  '/onboard': '组织人事',
  '/department': '组织人事',
  '/station': '组织人事',
  '/attendance': '考勤薪酬',
  '/schedule': '考勤薪酬',
  '/finance': '考勤薪酬',
  '/parcel': '包裹作业',
  '/parcel/sync': '包裹作业',
  '/work-order': '包裹作业',
  '/notification': '包裹作业',
  '/profile': '系统'
}

export async function pcNavigate(page, path) {
  const title = PC_MENU_TITLE_BY_PATH[path]
  if (!title) throw new Error(`pcNavigate 未登记该路径的菜单标题：${path}`)
  const item = page.locator('.app-menu .el-menu-item', { hasText: title }).first()
  if (!(await item.isVisible().catch(() => false))) {
    const group = PC_MENU_GROUP_BY_PATH[path]
    if (group) {
      await page.locator('.app-menu .el-sub-menu__title', { hasText: group }).first().click()
    }
    await expect(item).toBeVisible({ timeout: 10_000 })
  }
  await item.click()
  await expect(page).toHaveURL(new RegExp(`${path.replace(/\//g, '\\/')}$`), { timeout: 30_000 })
  await page.locator('.app-main').waitFor({ state: 'visible' })
}

/** PC 登录（history 路由）。走 /login 干净入口，与「从 pc.html 入口登录」分开断言（后者见 A1-7） */
export async function pcLoginAs(page, username = ACCOUNT.boss) {
  await page.goto('/login', { waitUntil: 'domcontentloaded' })
  await page.locator('.login-card').waitFor({ state: 'visible' })
  const inputs = page.locator('.login-card input')
  await inputs.nth(0).fill(username)
  await inputs.nth(1).fill(DEMO_PWD)
  await page.locator('.login-btn').click()
  // 登录成功即离开登录页并渲染后台布局（失败会停留登录页并给出错误提示）
  await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
  await page.locator('.app-menu').waitFor({ state: 'visible', timeout: 20_000 })
}

/** 移动端登录（hash 路由，入口 mobile.html）。按账号角色选择入口端：管理员走管理端 as=boss，站长/员工走员工端 as=station */
export async function mobileLoginAs(page, username = ACCOUNT.staff) {
  // 端准入互斥：管理员只能在管理端入口（as=boss）登录；缺省 as 一律按员工端，登录管理员会被 1110 拒
  const entry = username === ACCOUNT.boss ? '/mobile.html#/login?as=boss' : '/mobile.html#/login?as=station'
  await page.goto(entry, { waitUntil: 'domcontentloaded' })
  await page.locator('.login__card').waitFor({ state: 'visible' })
  const inputs = page.locator('.login__card input')
  await inputs.nth(0).fill(username)
  await inputs.nth(1).fill(DEMO_PWD)
  await page.locator('.login__submit button').click()
  await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
  await page.locator('.van-tabbar').waitFor({ state: 'visible', timeout: 20_000 })
}

/**
 * 视口横向溢出量：documentElement.scrollWidth - clientWidth（>1 视为出现横向滚动条）
 * 用 documentElement 而非 body，避免 body 高度撑开造成的误判
 */
export async function horizontalOverflow(page) {
  return page.evaluate(() => {
    const el = document.documentElement
    return el.scrollWidth - el.clientWidth
  })
}

/** 移动端每个固定/普通元素都不得越出视口左右边界（返回越界元素描述数组） */
export async function overflowingElements(page, selector) {
  return page.evaluate((sel) => {
    const vw = document.documentElement.clientWidth
    const out = []
    document.querySelectorAll(sel).forEach((el) => {
      const r = el.getBoundingClientRect()
      if (r.width === 0 || r.height === 0) return
      if (r.left < -1 || r.right > vw + 1) {
        out.push({
          tag: el.tagName.toLowerCase(),
          cls: String(el.className).slice(0, 60),
          left: Math.round(r.left),
          right: Math.round(r.right),
          vw
        })
      }
    })
    return out
  }, selector)
}
