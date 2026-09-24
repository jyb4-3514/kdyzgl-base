package com.qiujie.service.parcel.support;

import java.time.format.DateTimeFormatter;

/**
 * 包裹域常量（状态枚举与时间格式，对齐 Mock {@code parcelStore.js} / {@code dict.js}）。
 * <p>
 * 为什么集中定义：状态码 0~4 散落为字面量时，取件状态机一旦写错位即静默放过非法流转。
 */
public final class ParcelConstants {

    private ParcelConstants() {
    }

    /** 待入库 */
    public static final int STATUS_PENDING_INBOUND = 0;
    /** 在库待取（唯一可核销状态） */
    public static final int STATUS_IN_STOCK = 1;
    /** 已取件 */
    public static final int STATUS_PICKED = 2;
    /** 异常 */
    public static final int STATUS_ABNORMAL = 3;
    /** 已退回 */
    public static final int STATUS_RETURNED = 4;

    /** 合法状态集合（入参校验用；越界视为非法，不臆测） */
    public static final int STATUS_MIN = STATUS_PENDING_INBOUND;
    public static final int STATUS_MAX = STATUS_RETURNED;

    /** 出参时间格式（与 Mock {@code formatDateTime} 逐位一致：yyyy-MM-dd HH:mm:ss） */
    public static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 出参日期格式（与 Mock {@code formatDate} 一致：yyyy-MM-dd） */
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 容量预警等级：正常 */
    public static final String LEVEL_OK = "OK";
    /** 容量预警等级：告警 */
    public static final String LEVEL_WARN = "WARN";
    /** 容量预警等级：严重 */
    public static final String LEVEL_CRITICAL = "CRITICAL";

    /** 分页模式：游标（默认） */
    public static final String PAGE_MODE_CURSOR = "CURSOR";
    /** 分页模式：OFFSET 兜底 */
    public static final String PAGE_MODE_OFFSET = "OFFSET";

    /** 排行的默认排序指标（包裹量降序） */
    public static final String RANK_METRIC_TOTAL = "parcelTotal";
    /** 排行可切换排序指标：取件率 */
    public static final String RANK_METRIC_PICKUP_RATE = "pickupRate";
    /** 排行可切换排序指标：异常率 */
    public static final String RANK_METRIC_ABNORMAL_RATE = "abnormalRate";
}
