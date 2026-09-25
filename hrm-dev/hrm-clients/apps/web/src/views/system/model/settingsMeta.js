/* global __APP_VERSION__ */
import { ROLE } from '@kdyzgl/shared/constants/role'

/**
 * 系统设置页的纯逻辑（字段取值 + 降级规则）
 *
 * 为什么抽到 model：浏览器识别、角色→数据范围、字段缺失降级都是"怕边界不怕主路径"的映射，
 * 写在组件里无法覆盖解析失败分支；抽成纯函数后单测可直接灌非法输入（demo-pc-refactor.md §9）。
 *
 * 四态说明：本页唯一异步块是 S3 的「已预置包裹总数」；S1/S2/S4 的取值全部来自构建期常量、
 * navigator/screen 快照与登录态，同步即得，恒为 normal —— 不给它们补 loading，否则骨架屏会连静态内容一起闪。
 */

/**
 * 版本号：构建期由 vite.config.js 的 define 注入（取自 package.json 的 version）。
 * 未注入（单测、或将来换了构建方式）时为空串，页面据此不渲染「版本号」行，而不是把 undefined 渲染出来。
 */
export const APP_VERSION = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : ''

/**
 * 浏览器识别规则
 * 为什么是这个顺序：UA 里同时挂着多个内核特征（Edge 含 Chrome、Chrome 含 Safari），
 * 必须"更具体者在前"，否则一律被识别成 Chrome。
 * 说明：国产双核浏览器（360/QQ）UA 直接复用 Chrome 内核串，识别为 Chrome 属预期，不做额外嗅探。
 */
const BROWSER_RULES = [
  { name: 'Edge', re: /Edg\/(\d+)/ },
  { name: 'Firefox', re: /Firefox\/(\d+)/ },
  { name: 'Chrome', re: /Chrome\/(\d+)/ },
  { name: 'Safari', re: /Version\/(\d+)[\d.]* Safari/ }
]

/** 从 UA 取「名称 主版本」；识别不出时回退「未知浏览器」（不展示 UA 原文，噪声太大） */
export function parseBrowser(userAgent) {
  const ua = typeof userAgent === 'string' ? userAgent : ''
  for (const rule of BROWSER_RULES) {
    const hit = ua.match(rule.re)
    if (hit) return `${rule.name} ${hit[1]}`
  }
  return '未知浏览器'
}

/** 运行模式：由构建期常量 VITE_MOCK_ENABLED 推导，与 pc/main.js 装配 Mock 的判定口径保持一致 */
export function resolveRuntimeMode(mockEnabled) {
  const isDemo = mockEnabled === true || mockEnabled === 'true'
  return {
    isDemo,
    label: isDemo ? '演示态（Mock 数据）' : '生产态（真实接口）',
    // 交给 StatusTag：warning 取橙（演示态要显眼），info 收到中性（生产态不抢注意力）
    type: isDemo ? 'warning' : 'info'
  }
}

/** 数据范围：由角色派生，只回答"这个账号能看到哪些数据"，不额外演绎 */
const DATA_SCOPE_BY_ROLE = {
  [ROLE.ADMIN]: '全域',
  [ROLE.STATION_ADMIN]: '本站',
  [ROLE.STAFF]: '个人'
}

export function resolveDataScope(role) {
  return DATA_SCOPE_BY_ROLE[role] || '未知'
}

/** 时区：Intl 缺失、被裁剪或内核受限时会取不到，一律按空串处理，由页面决定不渲染该行 */
export function resolveTimezone(intl) {
  try {
    if (!intl || typeof intl.DateTimeFormat !== 'function') return ''
    const tz = intl.DateTimeFormat().resolvedOptions().timeZone
    return typeof tz === 'string' ? tz : ''
  } catch (e) {
    // 个别内核在受限环境下 resolvedOptions 会抛异常，不能让它带崩整页
    return ''
  }
}

/** 分辨率 / 视口统一格式：数字间用全角乘号，避免同页混用 x 与 × */
export function formatSize(width, height) {
  if (width == null || height == null) return ''
  return `${width} × ${height}`
}

/**
 * 预置规模（声明值）
 * 生产态没有"预置"这个概念，显式给「不适用」而不是留空 —— 留空会让人以为页面漏读了（设计规范 §3.3-5）。
 * 演示态但数量未配置 / 非法时返回空串，页面据此不渲染该行。
 */
export function resolvePresetScale(mockEnabled, presetCount) {
  if (!(mockEnabled === true || mockEnabled === 'true')) return '不适用'
  const size = Number(presetCount)
  return Number.isFinite(size) && size > 0 ? size.toLocaleString('zh-CN') : ''
}

/**
 * 字段行过滤：值为空（null / undefined / 空串 / 纯空白）的行整行不渲染。
 * 为什么不用 "—" 占位：占位会把「本机取不到」和「本来就为空」糊在一起，
 * 设计规范明确要求"拿不到就不展示该行"（demo-system-settings-ui.md §2.2）。
 */
export function filterRows(rows) {
  if (!Array.isArray(rows)) return []
  return rows.filter((row) => row && row.value != null && String(row.value).trim() !== '')
}

/** 权限说明：静态文案（不查接口），把三级角色的数据可见范围讲清楚 */
export const PERMISSION_NOTES = [
  '超级管理员：可查看全部驿站与全部员工的数据。',
  '站长：仅可查看所属驿站的包裹、同步、工单等业务数据。',
  '员工：仅可查看与本人相关的数据。'
]
