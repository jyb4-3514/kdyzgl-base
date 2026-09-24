package com.qiujie.vo.parcel;

import lombok.Data;

/**
 * 包裹看板出参（对齐 Mock {@code parcelStore.js#parcelSummary}，字段集逐位一致）。
 * <p>
 * {@code pickupRate = todayPickup / todayInbound}（分母为 0 → 0），保留 4 位小数。
 */
@Data
public class ParcelSummaryVO {

    /** 包裹总数 */
    private long parcelTotal;

    /** 今日入库 */
    private long todayInbound;

    /** 今日取件 */
    private long todayPickup;

    /** 在库待取 */
    private long pendingPickup;

    /** 异常件 */
    private long abnormalCount;

    /** 今日取件率（todayPickup / todayInbound，4 位小数） */
    private double pickupRate;
}
