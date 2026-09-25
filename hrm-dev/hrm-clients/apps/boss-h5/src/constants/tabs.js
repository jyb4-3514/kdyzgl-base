/**
 * 底部 Tabbar 配置（demo-mobile-nav-redesign.md C2）· 管理端「驿站精灵」
 *
 * 端固定化（B4）：本端只保留管理端 3 项（首页 / 消息 / 我的），路径全在 /boss/* 内，
 * 不再携带员工端配置（员工端由 apps/staff-h5 各持一份）。
 * 图标名均在 node_modules/vant/es/icon/index.css 中核对存在（`chat-o` 为本轮新引入，已核实）。
 * key 用于角标判定：仅 message 项挂角标（C4），避免按文案匹配导致改文案就掉角标。
 */
export const BOSS_TABS = [
  { key: 'home', path: '/boss/home', text: '首页', icon: 'wap-home-o' },
  { key: 'message', path: '/boss/message', text: '消息', icon: 'chat-o' },
  { key: 'me', path: '/boss/me', text: '我的', icon: 'user-o' }
]
