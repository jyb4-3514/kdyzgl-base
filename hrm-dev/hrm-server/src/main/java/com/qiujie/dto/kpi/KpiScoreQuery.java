package com.qiujie.dto.kpi;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * KPI 得分列表查询（{@code GET /kpi/scores}，ADMIN/STATION_ADMIN）。
 * <p>
 * {@code stationId} 继承 {@link StationScopedQuery}：非 ADMIN 静默收敛为本人驿站（传别的驿站不报错也不生效）；
 * {@code employeeId} 不做收敛（仅作为筛选条件）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KpiScoreQuery extends StationScopedQuery {

    /** 考核月份 yyyy-MM（必填） */
    private String month;

    /** 员工筛选（可选） */
    private Long employeeId;
}
