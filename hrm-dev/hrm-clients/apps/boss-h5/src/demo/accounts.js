import { DEMO_PASSWORD } from '@kdyzgl/mock/db.js'

/**
 * 管理端演示身份（demo-design.md 3.3 固定演示账号）· 驿站精灵
 * 为什么独立到 demo/：账号清单与演示密码只在 Mock 态出现，与假数据同源（密码取自 mock 包同一常量，
 * 避免仓库里出现第二份「演示密码」字符串），生产构建据此整块剔除。
 * `end`：该演示身份所属端。管理端入口只列 boss（站长 / 员工账号属异端，端准入 fail-closed 必被 1110 拒）。
 */
export const DEMO_ACCOUNT_LIST = [
  { key: 'boss', label: '管理员', username: 'admin', end: 'boss', desc: '全局经营视角 · 8 个驿站汇总数据' }
]

export { DEMO_PASSWORD }
