package com.qiujie.dto.kpi;

import lombok.Data;

import java.math.BigDecimal;

/**
 * KPI 指标新增/编辑入参（api.md / Mock {@code routes/kpi.js#validateMetric}）。
 * <p>
 * 各字段校验在 Service 内显式完成（文案需与 Mock 逐字一致，以便前端按文案对齐）；
 * {@code scoreRule} 为嵌套结构（Mock 契约形态），落库时拆为 {@code score_mode} / {@code full_score} 两列。
 * 编辑时只更新「显式传入」的字段（{@code null} 表示不改），与 Mock {@code pickWritable} 一致。
 */
@Data
public class KpiMetricRequest {

    /** 指标键：2-50 位，^[A-Z][A-Z0-9_]*$（新增必填；编辑时可选） */
    private String metricKey;

    /** 指标名：1-50 字（新增必填） */
    private String metricName;

    /** 类型：PARCEL/PICKUP/COMPLAINT/ATTENDANCE/SERVICE/WORK_ORDER/TRAINING/OTHER（新增必填） */
    private String metricType;

    /** 权重：0-100 整数（新增必填） */
    private Integer weight;

    /** 目标值：≥0 的数字（新增必填；可显式为 0） */
    private BigDecimal targetValue;

    /** 单位：如 件 / % / 分 */
    private String unit;

    /** 方向：UP=越高越好 / DOWN=越低越好（新增必填） */
    private String direction;

    /** 评分规则（新增必填） */
    private ScoreRule scoreRule;

    /** 适用角色数组（ADMIN/STATION_ADMIN/STAFF）；null/空=全员适用 */
    private java.util.List<String> roleScope;

    /**
     * 启用：0/1（新增缺省 1）。用 Object 承载以同时接受 {@code 0/1} 与 {@code true/false}
     * （Mock {@code validateMetric} 放行 [0,1,true,false]），非法值由 Service 统一回 400「enabled 仅支持 0 / 1」。
     */
    private Object enabled;

    /** 排序（缺省 = 末尾） */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 评分规则入参（对齐 Mock {@code scoreRule{mode,fullScore}}） */
    @Data
    public static class ScoreRule {
        /** LINEAR / TIERED / BINARY */
        private String mode;
        /** 单项满分：(0,100] */
        private BigDecimal fullScore;
    }
}
