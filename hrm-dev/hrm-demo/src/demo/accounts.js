import { DEMO_PASSWORD } from '@/shared/mock/db.js'

/**
 * 演示身份（demo-design.md 3.3 固定演示账号）
 * 为什么独立到 demo/：账号清单与演示密码只在 Mock 态出现，与假数据同源（密码取自 db.js 同一常量，
 * 避免仓库里出现第二份「演示密码」字符串），生产构建据此整块剔除。
 * `end`：该演示身份所属端（与登录页 `?as=` 同口径，且与后端端准入矩阵一致）——
 *   管理员只在 PC 端与管理端 H5（as=boss）可登，站长 / 员工只在员工端 H5（as=station）可登。
 *   演示入口预填 / 身份切换必须带上对应端，否则会被端准入 fail-closed 拒绝（1110）。
 */
export const DEMO_ACCOUNT_LIST = [
  { key: 'boss', label: '管理员', username: 'admin', end: 'boss', desc: '全局经营视角 · 8 个驿站汇总数据' },
  { key: 'station', label: '站长', username: 'st001_admin', end: 'station', desc: '城东驿站 · 本站数据与作业操作' },
  { key: 'staff', label: '员工', username: 'st001_staff', end: 'station', desc: '城东驿站 · 作业视角（无同步状态页）' }
]

export { DEMO_PASSWORD }
