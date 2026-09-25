import { ACCOUNT, mobileLoginAs } from '../../../e2e-utils/harness.js'

/**
 * 管理端 e2e 支撑（端专属的登录入口与导航封装；通用 infra 在 workspace 根 e2e-utils/）
 *
 * 端固定化（ADR §3.3）：本端入口恒上报 clientType=BOSS；登录页路径为 `/boss/#/login`
 * （应用 base='/boss/' + hash 路由），不再依赖 ?as 分流（?as=boss 仅作兼容读保留）。
 */
export const BOSS_ACCOUNT = ACCOUNT
export const BOSS_LOGIN_ENTRY = '/boss/#/login'

/** 管理端登录（管理员落 /boss/home） */
export async function bossLoginAs(page, username = ACCOUNT.boss) {
  await mobileLoginAs(page, username, { entry: BOSS_LOGIN_ENTRY })
}

/** 管理端 hash 深链直达（hashPath 形如 '/boss/home'） */
export async function bossGoto(page, hashPath) {
  await page.goto(`/boss/#${hashPath}`, { waitUntil: 'domcontentloaded' })
}

/** Tabbar 项定位器（点当前项 = 重复点击语义） */
export function bossTab(page, text) {
  return page.locator('.van-tabbar-item', { hasText: text }).first()
}
