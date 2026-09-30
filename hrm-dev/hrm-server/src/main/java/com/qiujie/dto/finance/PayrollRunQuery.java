package com.qiujie.dto.finance;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自动算薪运行记录查询（I-5，api.md §4.12.18）。
 * <p>分页继承 {@link PageQuery}（越界 → 400）；{@code stationId/month/status/triggerType} 为可选筛选项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollRunQuery extends PageQuery {

    /** 按运行记录 id 精确查询（可空）；提供且未命中 → 9409（见 I-5 说明） */
    private Long id;

    /** 驿站筛选（可空） */
    private Long stationId;

    /** 账期筛选 yyyy-MM（可空） */
    private String month;

    /** 结果筛选：RUNNING/SUCCESS/FAILED/SKIPPED（可空） */
    private String status;

    /** 触发方式筛选：AUTO/CATCH_UP/MANUAL（可空） */
    private String triggerType;
}
