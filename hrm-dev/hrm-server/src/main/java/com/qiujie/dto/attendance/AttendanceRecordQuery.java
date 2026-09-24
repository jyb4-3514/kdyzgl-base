package com.qiujie.dto.attendance;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打卡记录查询（GET /attendance/records）。
 * <p>
 * 只收敛 {@code stationId}；{@code employeeId} 原样透传（对齐 Mock：非 ADMIN 未收敛 employeeId）。
 * 分页越界由 {@link StationScopedQuery} 的 {@code @Min/@Max} 映射 400（文案「每页条数须为 1-100」）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceRecordQuery extends StationScopedQuery {

    /** 员工筛选 */
    private Long employeeId;

    /** 状态筛选：NORMAL / LATE / EARLY_LEAVE / ABNORMAL */
    private String status;

    /** 起始日期 yyyy-MM-dd（含） */
    private String startDate;

    /** 结束日期 yyyy-MM-dd（含） */
    private String endDate;
}
