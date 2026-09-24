package com.qiujie.service.parcel.support;

import lombok.Data;

/**
 * 排行按驿站聚合行（{@code ParcelMapper#selectStationStats} 的映射结果）。
 * <p>
 * {@code pending} 供 S7-2 容量/热力（{@code utilization = pendingPickup / shelfCapacity}）使用，
 * 即便开关关闭也算出（单次扫表的副产物，不额外增查询）。
 */
@Data
public class ParcelStationStat {

    /** 驿站 id */
    private Long stationId;

    /** 包裹总数 */
    private Long parcelTotal;

    /** 已取件（status=2） */
    private Long picked;

    /** 异常件（status=3） */
    private Long abnormal;

    /** 在库待取（status=1） */
    private Long pending;
}
