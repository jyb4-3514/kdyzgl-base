import { DEMO_PASSWORD } from '@/shared/mock/db.js'

/**
 * 演示身份（demo-design.md 3.3 固定演示账号）
 * 为什么独立到 demo/：账号清单与演示密码只在 Mock 态出现，与假数据同源（密码取自 db.js 同一常量，
 * 避免仓库里出现第二份「演示密码」字符串），生产构建据此整块剔除。
 */
export const DEMO_ACCOUNT_LIST = [
  { key: 'boss', label: '管理员', username: 'admin', desc: '全局经营视角 · 8 个驿站汇总数据' },
  { key: 'station', label: '站长', username: 'st001_admin', desc: '城东驿站 · 本站数据与作业操作' },
  { key: 'staff', label: '员工', username: 'st001_staff', desc: '城东驿站 · 作业视角（无同步状态页）' }
]

export { DEMO_PASSWORD }
