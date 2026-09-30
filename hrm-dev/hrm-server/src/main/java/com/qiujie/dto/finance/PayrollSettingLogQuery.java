package com.qiujie.dto.finance;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 算薪配置变更历史查询（I-9，api.md §4.12.17）。
 * <p>{@code stationId} 由路径给定；分页继承 {@link PageQuery}（越界 → 400）。时间范围为可选筛选项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollSettingLogQuery extends PageQuery {

    /** 起始时间，形如 {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss}（可空） */
    private String startTime;

    /** 结束时间，形如 {@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss}（可空） */
    private String endTime;
}
