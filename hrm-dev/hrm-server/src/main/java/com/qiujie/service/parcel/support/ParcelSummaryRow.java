package com.qiujie.service.parcel.support;

import lombok.Data;

/**
 * 看板聚合行（{@code ParcelMapper#selectSummary} 的映射结果）。
 * <p>
 * 为什么用行载体而非 {@code Map}：字段固定且需 null→0 归一，用强类型可避免键名拼写漂移。
 * 空表时 SQL 的 {@code SUM(...)} 返回 null，由 Service 归一为 0（对应 Mock 全零空态）。
 */
@Data
public class ParcelSummaryRow {

    /** 包裹总数 */
    private Long parcelTotal;

    /** 今日入库（inbound_time ≥ 今日 0 点） */
    private Long todayInbound;

    /** 今日取件（status=2 且 pickup_time ≥ 今日 0 点） */
    private Long todayPickup;

    /** 在库待取（status=1） */
    private Long pendingPickup;

    /** 异常件（status=3） */
    private Long abnormalCount;
}
