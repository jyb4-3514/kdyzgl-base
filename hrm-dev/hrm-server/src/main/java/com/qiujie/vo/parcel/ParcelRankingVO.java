package com.qiujie.vo.parcel;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 驿站排行出参（对齐 Mock {@code parcelStore.js#parcelRanking}，字段集 {@code {stationId, stationName, parcelTotal, pickupRate, abnormalRate}}）。
 * <p>
 * 后四个字段为 **S7-2 契约扩展**：仅当 {@code hrm.algo.parcel.capacity.appendToRanking=true} 时输出
 * （在库待取、利用率、预警等级、IQR 离群）；默认关闭，出参与 Mock 逐位一致。
 */
@Data
public class ParcelRankingVO {

    private Long stationId;

    private String stationName;

    /** 包裹总数 */
    private long parcelTotal;

    /** 取件率 = 已取件 / 总数（4 位小数） */
    private double pickupRate;

    /** 异常率 = 异常件 / 总数（4 位小数） */
    private double abnormalRate;

    /** 在库待取（扩展字段） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long pendingPickup;

    /** 容量利用率 = 在库待取 / 站均货架位（扩展字段） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double utilization;

    /** 预警等级 OK/WARN/CRITICAL（扩展字段） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String capacityLevel;

    /** 是否 IQR 离群（扩展字段） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean outlier;
}
