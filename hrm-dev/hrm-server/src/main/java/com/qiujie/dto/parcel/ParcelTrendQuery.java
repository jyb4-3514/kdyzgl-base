package com.qiujie.dto.parcel;

import lombok.Data;

/**
 * 包裹趋势查询（GET /parcels/trend，对齐 Mock {@code routes/parcel.js#trend}）。
 * <p>
 * 数据范围由角色决定（对齐 Mock {@code scopedStation}）：ADMIN 全站、非 ADMIN 本人驿站；
 * 与 summary/ranking 一致，<b>不消费 stationId 入参</b>（Mock 对这三者忽略 stationId，只按角色收敛）。
 */
@Data
public class ParcelTrendQuery {

    /** 统计天数（缺省 7，收敛到 [minDays, maxDays]，默认 [1,30]，对齐 Mock Math.min(30, Math.max(1, days||7))） */
    private Integer days;
}
