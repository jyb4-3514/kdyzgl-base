import { ACCOUNT, mobileLoginAs } from '../../../e2e-utils/harness.js'

/**
 * 员工端 e2e 支撑（端专属的登录入口与导航封装；通用 infra 在 workspace 根 e2e-utils/）
 *
 * 端固定化（ADR §3.3）：本端入口恒上报 clientType=STAFF；登录页路径为 `/staff/#/login`
 * （应用 base='/staff/' + hash 路由），不再依赖 ?as 分流（?as=station 仅作兼容读保留）。
 */
export const STAFF_ACCOUNT = ACCOUNT
export const STAFF_LOGIN_ENTRY = '/staff/#/login'

/** 员工端登录（站长 / 员工均落 /staff/home） */
export async function staffLoginAs(page, username = ACCOUNT.staff) {
  await mobileLoginAs(page, username, { entry: STAFF_LOGIN_ENTRY })
}

/** 员工端 hash 深链直达（hashPath 形如 '/staff/home'） */
export async function staffGoto(page, hashPath) {
  await page.goto(`/staff/#${hashPath}`, { waitUntil: 'domcontentloaded' })
}

/** Tabbar 项定位器（点当前项 = 重复点击语义） */
export function staffTab(page, text) {
  return page.locator('.van-tabbar-item', { hasText: text }).first()
}
