package com.qiujie.vo.kpi;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * KPI 某员工某月得分明细出参（对齐 Mock {@code scoreDetail}）。
 * <p>
 * 逐指标列出目标/实际/达成率/单项分/加权分；{@code rank}/{@code level} 取自同月全量汇总
 * （排名为全站口径，便于员工看到自己在全员中的位置）。
 */
@Data
public class KpiScoreDetailVO {

    private Long employeeId;
    private String employeeName;
    private Long stationId;
    private String stationName;
    private String month;
    /** 加权总分 */
    private BigDecimal totalScore;
    /** 平均达成率 */
    private BigDecimal achievementRate;
    /** 名次（全站口径；无汇总记录时为 null） */
    private Integer rank;
    /** 等级 */
    private String level;
    /** 等级中文标签 */
    private String levelLabel;
    /** 参与指标数 */
    private Integer metricCount;
    /** 参与权重合计 */
    private Integer weightSum;
    /** 算分时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime calculateTime;

    /** 逐指标明细（按权重倒序、指标 id 升序） */
    private List<KpiScoreItemVO> items;
}
