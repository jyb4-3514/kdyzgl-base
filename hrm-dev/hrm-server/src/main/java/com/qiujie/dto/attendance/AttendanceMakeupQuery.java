package com.qiujie.dto.attendance;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 补卡管理端查询（GET /attendance/makeup/list，仅 ADMIN）。
 * {@code stationId} 缺省即全量（对齐 Mock：ADMIN 可跨站）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceMakeupQuery extends StationScopedQuery {

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private String startDate;

    private String endDate;
}
