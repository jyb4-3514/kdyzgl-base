package com.qiujie.service.parcel.support;

import lombok.Data;

import java.time.LocalDate;

/**
 * 趋势按天分桶行（{@code ParcelMapper#countInboundByDay / countPickupByDay} 的映射结果）。
 * <p>
 * {@code statDate} 为 {@code DATE(inbound_time) / DATE(pickup_time)} 计算列，MySQL 与 PostgreSQL 双库可用。
 */
@Data
public class ParcelDailyCount {

    /** 统计日 */
    private LocalDate statDate;

    /** 当日计数 */
    private Long cnt;
}
