/**
 * 底部 Tabbar 配置（demo-mobile-nav-redesign.md C2）· 员工端「驿站助手」
 *
 * 3 项：首页 / 消息 / 我的。原来的 6 项一级页（考勤、趋势、排行、预警、打卡、包裹、工单、通知）
 * 已按 A2 迁移表下沉为首页宫格项与消息子视图，一级页只留最常用的三个意图。
 * 图标名均在 node_modules/vant/es/icon/index.css 中核对存在。
 * key 用于角标判定：仅 message 项挂角标（C4），避免按文案匹配导致改文案就掉角标。
 */
export const STAFF_TABS = [
  { key: 'home', path: '/staff/home', text: '首页', icon: 'wap-home-o' },
  { key: 'message', path: '/staff/message', text: '消息', icon: 'chat-o' },
  { key: 'me', path: '/staff/me', text: '我的', icon: 'user-o' }
]
