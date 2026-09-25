import { DEMO_PASSWORD } from '@kdyzgl/mock/db.js'

/**
 * 员工端演示身份（demo-design.md 3.3 固定演示账号）
 * 为什么独立到 demo/：账号清单与演示密码只在 Mock 态出现，与假数据同源（密码取自 mock 包同一常量，
 * 避免仓库里出现第二份「演示密码」字符串），生产构建据此整块剔除。
 * `end`：该演示身份所属端。员工端入口只列 station（管理员账号属异端，端准入 fail-closed 必被 1110 拒）。
 */
export const DEMO_ACCOUNT_LIST = [
  { key: 'station', label: '站长', username: 'st001_admin', end: 'station', desc: '城东驿站 · 本站数据与作业操作' },
  { key: 'staff', label: '员工', username: 'st001_staff', end: 'station', desc: '城东驿站 · 作业视角（无同步状态页）' }
]

export { DEMO_PASSWORD }
