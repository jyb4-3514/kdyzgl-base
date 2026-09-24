package com.qiujie.service.attendance.support;

import java.util.List;

/**
 * 考勤域共享常量（枚举取值 / 判定余量 / 维度白名单）。
 * <p>
 * 为什么集中在此：打卡状态、卡类型、来源、补卡状态是「判定链—出勤口径—导出文案」三处共用的字符串，
 * 散落为字面量时拼写漂移会让口径静默错位（如 ABNORMAL 写成 ABNORM 后「异常卡不计入实到」直接失效）。
 * <p>
 * TODO(扩展): {@code OPEN_AHEAD_MIN / CLOSE_DELAY_MIN} 为单班次模型的时间窗余量，当前为 Mock 等价常量；
 *   待算法参数表（algo-hrm-server.md §11）正式增列对应键后，改为 {@code hrm.algo.attendance.*} 外置（规则 §11.4）。
 */
public final class AttendanceConstants {

    private AttendanceConstants() {
    }

    // ==================== 卡类型 / 状态 / 来源 / 补卡状态 ====================

    /** 上班卡 */
    public static final String CHECK_TYPE_ON = "ON";
    /** 下班卡 */
    public static final String CHECK_TYPE_OFF = "OFF";

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_LATE = "LATE";
    public static final String STATUS_EARLY_LEAVE = "EARLY_LEAVE";
    public static final String STATUS_ABNORMAL = "ABNORMAL";

    public static final String SOURCE_NORMAL = "NORMAL";
    public static final String SOURCE_MAKEUP = "MAKEUP";

    public static final String MAKEUP_PENDING = "PENDING";
    public static final String MAKEUP_APPROVED = "APPROVED";
    public static final String MAKEUP_REJECTED = "REJECTED";

    // ==================== 校验项组合 / checkMode ====================

    public static final String MATCH_MODE_ALL = "ALL";
    public static final String MATCH_MODE_ANY = "ANY";

    public static final String CHECK_MODE_WIFI = "WIFI";
    public static final String CHECK_MODE_LOCATION = "LOCATION";
    public static final String CHECK_MODE_BOTH = "WIFI+LOCATION";

    // ==================== 时间窗余量（单班次模型） ====================

    /** 上班卡最早可打：班次开始前 30 分钟 */
    public static final int OPEN_AHEAD_MIN = 30;
    /** 下班卡最晚可打：班次结束后 60 分钟 */
    public static final int CLOSE_DELAY_MIN = 60;

    // ==================== 派生班次 / 时段兜底 ====================

    public static final String DEFAULT_SHIFT_NAME = "默认班次";
    public static final String DEFAULT_SHIFT_COLOR = "#0958D9";
    public static final String DEFAULT_PERIOD_NAME = "全天班";

    // ==================== 白名单 ====================

    /** 记录状态白名单（查询参数校验用） */
    public static final List<String> RECORD_STATUSES =
            List.of(STATUS_NORMAL, STATUS_LATE, STATUS_EARLY_LEAVE, STATUS_ABNORMAL);

    /** 补卡状态白名单 */
    public static final List<String> MAKEUP_STATUSES =
            List.of(MAKEUP_PENDING, MAKEUP_APPROVED, MAKEUP_REJECTED);

    /** 考勤明细维度白名单（与概况六个计数字段一一对应） */
    public static final List<String> DETAIL_DIMS =
            List.of("SHOULD", "ACTUAL", "NORMAL", "LATE", "EARLY_LEAVE", "ABSENT");

    /** 有效卡判定：校验未通过的异常卡不算已完成打卡 */
    public static boolean isValidCard(String status) {
        return !STATUS_ABNORMAL.equals(status);
    }
}
