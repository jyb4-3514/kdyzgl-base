/**
 * 看板展示元数据（一期口径指标 / 工单指标形态）
 *
 * MVP 裁剪：包裹（入库/取件/在库/异常）、同步成功率、驿站排行、包裹趋势相关的元数据
 * （HERO_META / RANK_* / dayOverDay / buildRankRows / buildHeroCards / buildInlineCards）随模块下架一并移除；
 * 本文件只保留一期组织规模与工单指标两块的口径。
 */

/** 一期口径指标：字段名与 GET /dashboard/summary 一致 */
export const BASE_CARDS = [
  { key: 'employeeTotal', label: '员工总数' },
  { key: 'stationTotal', label: '驿站数' },
  { key: 'departmentTotal', label: '部门数' },
  { key: 'todayLoginCount', label: '今日登录数' }
]

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
