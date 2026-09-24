package com.qiujie.dto.kpi;

import lombok.Data;

import java.util.List;

/**
 * KPI 按月算分入参（api.md / Mock {@code routes/kpi.js#calculate}，仅 ADMIN）。
 * <p>
 * {@code month} 必填（算哪个月必须由调用方明确，避免默认到本月改脏数据）；
 * {@code stationId} / {@code employeeIds} 可选收窄范围（仅 ADMIN 会用到，故不做 L1 收敛）。
 */
@Data
public class KpiCalculateRequest {

    /** 考核月份 yyyy-MM（必填） */
    private String month;

    /** 驿站范围（可选；空=全部） */
    private Long stationId;

    /** 员工范围（可选；空=全部在职启用员工） */
    private List<Long> employeeIds;
}
