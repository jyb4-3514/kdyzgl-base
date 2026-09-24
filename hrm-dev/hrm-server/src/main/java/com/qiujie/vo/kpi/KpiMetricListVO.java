package com.qiujie.vo.kpi;

import lombok.Data;

import java.util.List;

/**
 * KPI 指标列表出参（对齐 Mock {@code listMetrics}）：列表 + 启用指标权重合计。
 * <p>
 * {@code weightSum} 供页面「权重合计条」提示；算分按适用权重归一，不依赖它等于 100。
 */
@Data
public class KpiMetricListVO {

    /** 指标列表（按 sortOrder、id 升序） */
    private List<KpiMetricVO> list;

    /** 启用指标权重合计 */
    private Integer weightSum;
}
