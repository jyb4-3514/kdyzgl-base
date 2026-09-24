package com.qiujie.dto.kpi;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * KPI 排名榜查询（{@code GET /kpi/scores/ranking}，ADMIN/STATION_ADMIN）。
 * <p>
 * {@code stationId} 继承 {@link StationScopedQuery}：非 ADMIN 静默收敛为本人驿站。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KpiRankingQuery extends StationScopedQuery {

    /** 考核月份 yyyy-MM（必填） */
    private String month;
}
