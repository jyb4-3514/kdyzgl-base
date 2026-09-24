package com.qiujie.vo.kpi;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KPI 得分列表行（对齐 Mock {@code summarize} 聚合行）。
 * 列表与排名榜共用本结构；{@code rank} 由竞赛排名法给出（同总分+同达成率共享名次）。
 */
@Data
public class KpiScoreVO {

    private Long employeeId;
    private String employeeName;
    private Long stationId;
    private String stationName;
    private String month;
    /** 加权总分（1 位小数） */
    private BigDecimal totalScore;
    /** 平均达成率（4 位小数） */
    private BigDecimal achievementRate;
    /** 等级：EXCELLENT / GOOD / PASS / IMPROVE */
    private String level;
    /** 等级中文标签 */
    private String levelLabel;
    /** 参与指标数 */
    private Integer metricCount;
    /** 算分时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime calculateTime;
    /** 名次（竞赛排名法） */
    private Integer rank;
}
