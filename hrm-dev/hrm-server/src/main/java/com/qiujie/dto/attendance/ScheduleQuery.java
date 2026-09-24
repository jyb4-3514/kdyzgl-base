package com.qiujie.dto.attendance;

import com.qiujie.dto.support.StationScopeQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 排班周矩阵查询（GET /schedules）：{@code stationId} 必填，{@code weekStart} 可空（默认本周）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleQuery extends StationScopeQuery {

    /** 周起始日期 yyyy-MM-dd（可空；内部取该日期所在周的周一） */
    private String weekStart;
}
