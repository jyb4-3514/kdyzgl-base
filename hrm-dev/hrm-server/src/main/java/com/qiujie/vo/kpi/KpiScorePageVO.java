package com.qiujie.vo.kpi;

import lombok.Data;

import java.util.List;

/**
 * KPI 得分列表分页出参（对齐 Mock {@code queryScores} → {@code paginate(...)} + {@code month}）。
 * 字段名与 {@code PageResult} 一致（total/pageNum/pageSize/list），另附 {@code month}。
 */
@Data
public class KpiScorePageVO {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<KpiScoreVO> list;
    /** 考核月份 */
    private String month;
}
