/**
 * 看板展示元数据（指标口径 / 排序项 / 排行换算）
 *
 * 为什么收口：环比推导与排行百分比属于"算法口径"（今日值 vs 近 N 天日均、相对最大值的百分比），
 * 散在模板里既改一处漏一处也无法单测；收进 model 后口径唯一、可离线验证。
 */

/** 首屏主指标：key 同时是 /parcel/summary 的字段名，to 为下钻目标（带状态预置筛选） */
export const HERO_META = [
  { key: 'todayInbound', label: '今日入库', icon: 'Box', tone: 'blue', to: '/parcel' },
  { key: 'todayPickup', label: '今日取件', icon: 'Checked', tone: 'green', to: '/parcel?status=2' },
  { key: 'pendingPickup', label: '在库待取', icon: 'Clock', tone: 'orange', to: '/parcel?status=1' },
  { key: 'abnormalCount', label: '异常件', icon: 'Warning', tone: 'red', to: '/parcel?status=3' }
]

/** 只有进出件量有环比基线，在库/异常是存量或状态量，算环比无业务意义 */
export const HERO_TREND_KEYS = ['todayInbound', 'todayPickup']

/** 一期口径指标：字段名与 GET /dashboard/summary 一致 */
export const BASE_CARDS = [
  { key: 'employeeTotal', label: '员工总数' },
  { key: 'stationTotal', label: '驿站数' },
  { key: 'departmentTotal', label: '部门数' },
  { key: 'todayLoginCount', label: '今日登录数' }
]

/** 排行口径 → 文案；键同时是排行接口的 sort 参数 */
export const RANK_LABELS = { parcelTotal: '包裹量', pickupRate: '取件率', abnormalRate: '异常率' }

/** 排行排序切换项（与 RANK_LABELS 同键，顺序即页面展示顺序） */
export const RANK_SORTS = [
  { value: 'parcelTotal', label: '按包裹量' },
  { value: 'pickupRate', label: '按取件率' },
  { value: 'abnormalRate', label: '按异常率' }
]

const num = (value) => (value == null ? null : Number(value))

/**
 * 环比：今日值 vs 近 N 天日均（排除最后一天）
 * 数据契约暂无同比字段，故前端本地推导（9.3 D-6）；基线为 0 时无意义，返回 null 由卡片不渲染。
 */
export function dayOverDay(list, key) {
  if (!list || list.length < 2) return null
  const today = num(list[list.length - 1][key]) || 0
  const history = list.slice(0, -1)
  const avg = history.reduce((sum, item) => sum + (num(item[key]) || 0), 0) / history.length
  if (!avg) return null
  const diff = (today - avg) / avg
  return { dir: diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat', text: `${Math.abs(diff * 100).toFixed(1)}%` }
}

/**
 * 排行行：主值取当前排序口径，副信息三端固定顺序（包裹 · 取件率 · 异常率）
 * percent 是相对当前列表最大值的百分比而非绝对值，空列表与全 0 时一律落 0。
 */
export function buildRankRows(list, sortKey) {
  const nums = (list || []).map((row) => num(row[sortKey]) || 0)
  const max = nums.length ? Math.max(...nums) : 0
  const isRate = sortKey !== 'parcelTotal'
  return (list || []).map((row, index) => {
    const value = num(row[sortKey]) || 0
    return {
      // stationId 优先，兼容只有 id 的旧数据；两者都缺时用下标兜底，保证 v-for key 稳定
      id: row.stationId ?? row.id ?? index,
      rank: index + 1,
      stationName: row.stationName,
      mainValue: isRate ? (value * 100).toFixed(1) : value.toLocaleString('zh-CN'),
      mainUnit: isRate ? '%' : '件',
      percent: max ? Math.round((value / max) * 100) : 0,
      sub: `包裹 ${(num(row.parcelTotal) || 0).toLocaleString('zh-CN')} · 取件率 ${(
        (num(row.pickupRate) || 0) * 100
      ).toFixed(1)}% · 异常率 ${((num(row.abnormalRate) || 0) * 100).toFixed(1)}%`
    }
  })
}

const percentText = (value) => `${(Number(value) * 100).toFixed(1)}%`

/** 首屏主指标卡：值取包裹总览字段，环比只在进出件量上给（基线见 dayOverDay） */
export function buildHeroCards(parcelSummary, trend) {
  return HERO_META.map((meta) => ({
    ...meta,
    value: parcelSummary ? parcelSummary[meta.key] : null,
    trend: HERO_TREND_KEYS.includes(meta.key) ? dayOverDay(trend, meta.key) : null,
    errorText: `${meta.label}加载失败`
  }))
}

/**
 * 次级指标条：包裹 / 同步 / 工单各出一项
 * 单项 loading/error 各自独立，一项失败只影响自己那一格（5.2 第 7 条）。
 */
export function buildInlineCards({
  parcelSummary,
  parcelLoading,
  parcelError,
  healthLoading,
  healthError,
  syncHealth,
  woLoading,
  woError,
  workOrder
}) {
  return [
    {
      key: 'parcelTotal',
      label: '包裹总量',
      value: parcelSummary ? parcelSummary.parcelTotal : null,
      unit: '件',
      tone: 'blue',
      loading: parcelLoading,
      error: parcelError,
      errorText: '包裹总量加载失败'
    },
    {
      key: 'pickupRate',
      label: '今日取件率',
      value: parcelSummary ? parcelSummary.pickupRate : null,
      format: percentText,
      tone: 'green',
      loading: parcelLoading,
      error: parcelError,
      errorText: '取件率加载失败'
    },
    {
      key: 'successRate',
      label: '同步成功率',
      value: healthLoading ? null : syncHealth.latestBatchSuccessRate,
      format: percentText,
      tone: 'neutral',
      loading: healthLoading,
      error: healthError,
      errorText: '同步成功率加载失败'
    },
    {
      key: 'overSlaCount',
      label: '超时未处理工单',
      value: woLoading ? null : workOrder.overSlaCount,
      unit: '条',
      tone: 'red',
      loading: woLoading,
      error: woError,
      errorText: '超时未处理工单加载失败'
    }
  ]
}

/** 工单指标形态：件数项 2×2 + 时长项独占整行（件数与时长不同量纲） */
export function buildWorkOrderMetrics(workOrder, formatMinutes) {
  return {
    primary: [
      { label: '待处理', value: workOrder.pendingCount },
      { label: '处理中', value: workOrder.processingCount },
      { label: '今日新增', value: workOrder.todayNewCount },
      { label: '超时未处理', value: workOrder.overSlaCount, danger: workOrder.overSlaCount > 0 }
    ],
    avgValue: workOrder.avgHandleMinutes == null ? '—' : formatMinutes(workOrder.avgHandleMinutes)
  }
}
