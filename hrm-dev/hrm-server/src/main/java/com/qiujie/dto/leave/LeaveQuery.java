package com.qiujie.dto.leave;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请假管理端列表查询（api.md §7.1 #4）。
 * <p>
 * {@code stationId} 由 L1 数据范围静默收敛（非 ADMIN 强制为本人驿站，见 {@code StationScopedQuery}）；
 * 仅 ADMIN / STATION_ADMIN 可达本端点（角色门槛在 Controller）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveQuery extends StationScopedQuery {

    /** 申请人筛选（可空） */
    private Long employeeId;

    /** 状态筛选；'PENDING' 为聚合虚拟值 */
    private String status;

    /** 假别筛选 */
    private String leaveType;

    private String startDate;

    private String endDate;
}
