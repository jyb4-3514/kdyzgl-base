package com.qiujie.dto.kpi;

import lombok.Data;

import java.math.BigDecimal;

/**
 * KPI 单项算分快照（落 {@code kpi_score.metric_detail} JSON，db.md §8.4.2）。
 * <p>
 * 为什么快照「评分规则副本」（{@code scoreMode}/{@code fullScore} 与 {@code targetValue}/{@code weight}）：
 * 指标后续被改配置或删除后，历史得分仍可逐项解释「这分怎么来的」，保证可解释与可审计（算法 §3.1 约束 2）。
 * 字段口径与 Mock {@code kpiStore.upsertScore} 逐项一致（仅把嵌套 scoreRule 拍平为两列，读侧再还原）。
 */
@Data
public class KpiMetricDetailItem {

    /** 指标 id 快照 */
    private Long metricId;

    /** 指标键快照 */
    private String metricKey;

    /** 指标名快照 */
    private String metricName;

    /** 指标类型快照 */
    private String metricType;

    /** 权重快照 */
    private Integer weight;

    /** 目标值快照 */
    private BigDecimal targetValue;

    /** 单位快照 */
    private String unit;

    /** 方向快照：UP / DOWN */
    private String direction;

    /** 实际业绩值（取数来源见 KpiActualValueResolver 注释） */
    private BigDecimal actualValue;

    /** 达成率 */
    private BigDecimal achievementRate;

    /** 单项得分（0..fullScore 的整数） */
    private Integer score;

    /** 加权分：score × weight / 100，保留 2 位小数 */
    private BigDecimal weightedScore;

    /** 评分规则快照：LINEAR / TIERED / BINARY */
    private String scoreMode;

    /** 单项满分快照 */
    private BigDecimal fullScore;
}
