package com.qiujie.vo.kpi;

import lombok.Data;

import java.math.BigDecimal;

/**
 * KPI 得分明细逐项（对齐 Mock {@code scoreDetail.items[]}）。
 * 由 {@code kpi_score.metric_detail} 快照还原，并补齐 {@code scoreRule} 嵌套结构与标签。
 */
@Data
public class KpiScoreItemVO {

    private Long metricId;
    private String metricKey;
    private String metricName;
    private String metricType;
    /** 类型中文标签 */
    private String metricTypeLabel;
    private Integer weight;
    private BigDecimal targetValue;
    private String unit;
    private String direction;
    /** 实际业绩值 */
    private BigDecimal actualValue;
    /** 达成率 */
    private BigDecimal achievementRate;
    /** 单项得分 */
    private Integer score;
    /** 加权分（2 位小数） */
    private BigDecimal weightedScore;
    /** 评分规则快照 */
    private KpiScoreRuleVO scoreRule;
    /** 评分模式中文标签 */
    private String scoreModeLabel;
}
