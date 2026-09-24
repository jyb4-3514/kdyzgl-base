package com.qiujie.dto.attendance;

import com.qiujie.dto.support.StationScopeQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考勤明细查询（GET /attendance/detail）：{@code dim} 必填且白名单严校验（非法/缺省 → 400）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceDetailQuery extends StationScopeQuery {

    /** 维度：SHOULD / ACTUAL / NORMAL / LATE / EARLY_LEAVE / ABSENT */
    private String dim;

    /** 统计日期 yyyy-MM-dd，可空 */
    private String date;
}
