package com.qiujie.vo.kpi;

import lombok.Data;

/**
 * KPI 按月算分结果（对齐 Mock {@code calculate} → {@code {month, employeeCount, metricCount, scoreCount}}）。
 */
@Data
public class KpiCalculateResultVO {

    /** 考核月份 */
    private String month;

    /** 参与算分的员工数（有适用指标的员工） */
    private Integer employeeCount;

    /** 启用指标数 */
    private Integer metricCount;

    /** 生成/更新的评分项数（员工 × 适用指标） */
    private Integer scoreCount;
}
