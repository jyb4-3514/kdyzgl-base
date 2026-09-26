import { expect } from '@playwright/test'
import { ACCOUNT, DEMO_FIXED_SMS_CODE, DEMO_PWD } from '../../../e2e-utils/harness.js'

/**
 * 网页端 e2e 支撑（本端专属：官方入口、登录助手与菜单导航封装；通用 infra 在 workspace 根 e2e-utils/）
 *
 * 端固定化（ADR §3.3）：本端入口恒上报 clientType=WEB；应用 `base:'/web/'` + history 路由，
 * 页面路径形如 `/web/login`、`/web/dashboard`。
 */
export const WEB_BASE = '/web'
export const WEB_ACCOUNT = ACCOUNT
export const WEB_LOGIN_ENTRY = `${WEB_BASE}/login`

/** 官方入口（history 路由的端入口文件；A1-7 缺陷探针用它验证「入口登录后不落 404」） */
export const WEB_ENTRY_FILE = `${WEB_BASE}/index.html`

/** 菜单路径 → 菜单标题（pcNavigate 用；与 src/config/menu.js 同源，仅登记 e2e 访问到的项） */
const MENU_TITLE_BY_PATH = {
  '/dashboard': '数据看板',
  '/employee': '员工管理',
  '/hr': '人事管理',
  '/onboard': '入离职',
  '/department': '部门管理',
  '/station': '驿站管理',
  '/attendance': '考勤管理',
  '/schedule': '排班管理',
  '/finance': '财务管理',
  '/work-order': '工单管理',
  '/notification': '通知中心',
  '/profile': '个人中心'
}

/** 菜单路径 → 所属分组标题（分组默认展开，仅在项不可见时兜底点击分组） */
const MENU_GROUP_BY_PATH = {
  '/employee': '组织人事',
  '/hr': '组织人事',
  '/onboard': '组织人事',
  '/department': '组织人事',
  '/station': '组织人事',
  '/attendance': '考勤薪酬',
  '/schedule': '考勤薪酬',
  '/finance': '考勤薪酬',
  '/work-order': '作业管理',
  '/notification': '作业管理',
  '/profile': '系统'
}

/** 深链直达（history 路由，路径需带 /web 基址） */
export async function webGoto(page, path) {
  await page.goto(`${WEB_BASE}${path}`, { waitUntil: 'domcontentloaded' })
}

/**
 * 新设备短信二次验证（登录体系改造 S3）—— 按真实用户路径补完卡片第 2 步
 *
 * 为什么登录助手必须处理它：密码通道登录会随请求上报 deviceId（弱信号）；Mock（镜像后端
 * ClientAdmissionPolicy 同口径）对**未受信设备**返回 `needDeviceVerify`（1104 分流、无 token），
 * 登录页切到卡片第 2 步「设备验证」。Playwright 每条用例都是全新浏览器上下文 → deviceId 每次新生成
 * → 必然命中该分支；只点一次「登录」会停在登录页，用例必然失败。
 *
 * 本助手只补交互、**不改任何断言**；也**不预置「设备受信」状态**（前端不得自认受信，见 security-auth-review §4.2）。
 * @returns {Promise<boolean>} 是否命中了设备验证步
 */
export async function completePcDeviceVerifyIfNeeded(page, code = DEMO_FIXED_SMS_CODE) {
  const card = page.locator('.login-device')
  const appeared = await card
    .waitFor({ state: 'visible', timeout: 3_000 })
    .then(() => true)
    .catch(() => false)
  if (!appeared) return false
  await card.getByRole('button', { name: '获取验证码' }).click()
  await card.locator('input').first().fill(code)
  await card.locator('.login-btn').click()
  return true
}

/** 网页端登录（history 路由，官方入口 /web/login）：登录成功即离开登录页并渲染后台布局 */
export async function webLoginAs(page, username = ACCOUNT.boss) {
  await page.goto(WEB_LOGIN_ENTRY, { waitUntil: 'domcontentloaded' })
  await page.locator('.login-card').waitFor({ state: 'visible' })
  const inputs = page.locator('.login-card input')
  await inputs.nth(0).fill(username)
  await inputs.nth(1).fill(DEMO_PWD)
  await page.locator('.login-btn').click()
  await completePcDeviceVerifyIfNeeded(page)
  await expect(page).not.toHaveURL(/\/login/, { timeout: 20_000 })
  await page.locator('.app-menu').waitFor({ state: 'visible', timeout: 20_000 })
}

/** 客户端内菜单跳转（不整页 goto，避免重跑入口与 Mock 初始化）：按菜单标题点击并校验 URL */
export async function webNavigate(page, path) {
  const title = MENU_TITLE_BY_PATH[path]
  if (!title) throw new Error(`webNavigate 未登记该路径的菜单标题：${path}`)
  const item = page.locator('.app-menu .el-menu-item', { hasText: title }).first()
  if (!(await item.isVisible().catch(() => false))) {
    const group = MENU_GROUP_BY_PATH[path]
    if (group) await page.locator('.app-menu .el-sub-menu__title', { hasText: group }).first().click()
    await expect(item).toBeVisible({ timeout: 10_000 })
  }
  await item.click()
  await expect(page).toHaveURL(new RegExp(`${WEB_BASE}${path.replace(/\//g, '\\/')}$`), { timeout: 30_000 })
  await page.locator('.app-main').waitFor({ state: 'visible' })
}
