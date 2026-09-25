/**
 * 同步任务域展示元数据（Tab 枚举 / 日志级别配色 / 尺寸常量）
 *
 * 为什么收口：Tab 白名单与「?tab=config 仅 ADMIN」的回落规则是既有契约（B.1.1），
 * 散在页面里既无法单测也容易被后续新增 Tab 时漏掉；收进 model 后回落规则可被断言钉住。
 */

/** 页内三 Tab；config 仅 ADMIN 渲染（写操作后端也仅 ADMIN） */
export const SYNC_TABS = ['batch', 'collect', 'config']

/**
 * 日志级别 → 时间线轴点色（2.7）：改前借用 el-timeline 的语义 type，
 * 而 --el-color-info 已被收口成 #4B5563（深灰），当轴点用会过重，故显式给色值 Token。
 */
export const SYNC_LOG_DOT = { 0: 'var(--text-disabled)', 1: 'var(--color-warning-icon)', 2: 'var(--color-danger-icon)' }

/** 抽屉宽度统一到 --drawer-w，窄视口退化为 92vw（C-P9，修 P16） */
export const DRAWER_SIZE = 'min(var(--drawer-w), 92vw)'

export const PAGE_SIZES = [20, 50, 100]

/**
 * 初始 Tab：非法值回落批次流水；站长无「配置管理」权限，直达 ?tab=config 时同样回落，
 * 避免出现空 Tab（B.1.1）。
 */
export function resolveInitialTab(tab, isAdmin) {
  const next = SYNC_TABS.includes(tab) ? tab : 'batch'
  return next === 'config' && !isAdmin ? 'batch' : next
}

/** 仅失败行标记类名，配合样式只给失败行显示展开图标（A6-3）；非失败行保留占位宽度，表头列不错位 */
export function failedRowClassOf({ row }) {
  return row.status === 3 ? 'is-failed-row' : ''
}
