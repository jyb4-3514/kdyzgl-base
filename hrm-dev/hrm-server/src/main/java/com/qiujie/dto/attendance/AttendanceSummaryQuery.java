package com.qiujie.dto.attendance;

import com.qiujie.dto.support.StationScopeQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡概况查询（GET /attendance/summary）：{@code date} 可空（默认今天）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceSummaryQuery extends StationScopeQuery {

    /** 统计日期 yyyy-MM-dd，可空 */
    private String date;
}
