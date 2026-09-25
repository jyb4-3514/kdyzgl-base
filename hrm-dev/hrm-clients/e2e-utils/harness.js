import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect } from '@playwright/test'

/** 演示账号（仅存在于 Mock 数据，非任何环境真实凭据） */
export const DEMO_PWD = 'demo1234'
/** 演示身份用户名：管理员 / 城东驿站站长 / 城东驿站员工 */
export const ACCOUNT = { boss: 'admin', station: 'st001_admin', staff: 'st001_staff' }

export const EVIDENCE_DIR = fileURLToPath(new URL('../e2e-evidence/', import.meta.url))
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
 * 演示固定短信验证码（与 Mock 单一真源 `packages/mock/src/routes/auth.js#DEMO_FIXED_CODE` 同值）
 *
 * 为什么在此重复一个字面量：本工具层刻意不 import Mock 模块（Mock 是「被测假后端」，测试基建依赖它会
 * 形成自证循环，且会把 db.js 整条种子链拖进 Node 进程）。这是**演示态专有**的公开取值，非任何环境真实凭据。
 */
export const DEMO_FIXED_SMS_CODE = '000000'

/**
 * 新设备短信二次验证（登录体系改造 S3）—— 按真实用户路径补完卡片第 2 步
 *
 * 为什么登录助手必须处理它：密码通道登录会随请求上报 deviceId（弱信号）；Mock（镜像后端
 * `ClientAdmissionPolicy` 同口径）对**未受信设备**返回 `needDeviceVerify`（1104 分流、无 token），
 * 登录页切到卡片第 2 步「设备验证」。Playwright 每条用例都是全新浏览器上下文 → deviceId 每次新生成
 * → 必然命中该分支；只点一次「登录」会停在登录页，用例必然失败。
 *
 * 本助手只补交互、**不改任何断言**；也**不预置「设备受信」状态**（前端不得自认受信，
 * 见 security-auth-review §4.2 —— 信任状态只能由服务端签发）。
 *
 * @returns {Promise<boolean>} 是否命中了设备验证步（便于调用方按需断言）
 */
export async function completeDeviceVerifyIfNeeded(page, code = DEMO_FIXED_SMS_CODE) {
  const card = page.locator('.login__device')
  // 受信设备直接放行、不会出现该卡片；未命中等 3s 即返回 false，不让用例白等满超时
  const appeared = await card
    .waitFor({ state: 'visible', timeout: 3_000 })
    .then(() => true)
    .catch(() => false)
  if (!appeared) return false
  await card.locator('.login__code-btn').click()
  await card.locator('input').first().fill(code)
  await card.locator('.login__submit button').click()
  return true
}

/**
 * 移动端 / H5 端登录（通用）：登录入口与选择器由各端显式传入 —— 本工具层不假设任何端路径。
 * @param {import('@playwright/test').Page} page
 * @param {string} username 演示账号（仅存在于 Mock 数据，非任何环境真实凭据）
 * @param {{ entry: string }} options entry = 该端登录页完整路径（含 hash），如 '/staff/#/login'
 */
export async function mobileLoginAs(page, username, { entry } = {}) {
  if (!entry) throw new Error('mobileLoginAs 需显式传入 entry（各端登录页路径）；通用工具层不假设端路径')
  await page.goto(entry, { waitUntil: 'domcontentloaded' })
  await page.locator('.login__card').waitFor({ state: 'visible' })
  const inputs = page.locator('.login__card input')
  await inputs.nth(0).fill(username)
  await inputs.nth(1).fill(DEMO_PWD)
  await page.locator('.login__submit button').click()
  await completeDeviceVerifyIfNeeded(page)
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
