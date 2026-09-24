package com.qiujie.vo.kpi;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * KPI 排名榜分页出参（对齐 Mock {@code queryRanking}）。
 * <p>
 * 除分页字段外，附榜单头部三行摘要（页面不必再拉全量自己算）：
 * {@code count}=参与人数、{@code avgScore}=均分（1 位小数）、{@code topScore}=榜首总分。
 */
@Data
public class KpiRankingPageVO {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<KpiScoreVO> list;
    /** 考核月份 */
    private String month;
    /** 参与人数（全量，非当前页） */
    private Integer count;
    /** 均分（全量，1 位小数） */
    private BigDecimal avgScore;
    /** 榜首总分（无数据为 0） */
    private BigDecimal topScore;
}
