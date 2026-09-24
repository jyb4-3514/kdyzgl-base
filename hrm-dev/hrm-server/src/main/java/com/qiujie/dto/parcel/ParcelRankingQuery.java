package com.qiujie.dto.parcel;

import lombok.Data;

/**
 * 驿站排行查询（GET /parcels/ranking，对齐 Mock {@code routes/parcel.js#ranking}）。
 * <p>
 * 数据范围由角色决定（ADMIN 全站、非 ADMIN 本人驿站），不消费 stationId 入参。
 */
@Data
public class ParcelRankingQuery {

    /** 排序指标：缺省 / 非受支持值 → 包裹量降序；{@code pickupRate} / {@code abnormalRate} 触发重排 */
    private String sort;
}
