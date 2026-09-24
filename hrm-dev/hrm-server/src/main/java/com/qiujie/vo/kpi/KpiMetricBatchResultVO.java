package com.qiujie.vo.kpi;

import lombok.Data;

/**
 * KPI 指标批量保存结果（对齐 Mock {@code saveMetricBatch}）：保存后的权重合计 + 变更条数。
 */
@Data
public class KpiMetricBatchResultVO {

    /** 保存后启用指标权重合计 */
    private Integer weightSum;

    /** 实际变更的指标条数 */
    private Integer updated;
}
