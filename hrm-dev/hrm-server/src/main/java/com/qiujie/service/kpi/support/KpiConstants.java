package com.qiujie.service.kpi.support;

import java.util.List;
import java.util.Map;

/**
 * KPI 域共享常量（枚举取值与展示标签）。
 * <p>
 * 为什么集中在此：指标类型/评分模式/方向/等级取值是「校验白名单—算分分支—出参标签」三处共用的字符串，
 * 散落为字面量时拼写漂移会让分支静默失配（如 TIERED 写成 TIER 后阶梯评分退化为线性）。
 * 标签与 Mock {@code kpiStore} / {@code dict.js} 逐条一致（中文文案同源）。
 * <p>
 * 注意：等级**阈值**（90/80/70）是可配参数，属 {@code hrm.algo.kpi.levels}，不在本类；
 * 本类只放「等级取值 → 中文标签」的固定映射（非阈值，不受 Q1 裁定影响）。
 */
public final class KpiConstants {

    private KpiConstants() {
    }

    // ==================== 指标类型 ====================

    public static final String TYPE_PARCEL = "PARCEL";
    public static final String TYPE_PICKUP = "PICKUP";
    public static final String TYPE_COMPLAINT = "COMPLAINT";
    public static final String TYPE_ATTENDANCE = "ATTENDANCE";
    public static final String TYPE_SERVICE = "SERVICE";
    public static final String TYPE_WORK_ORDER = "WORK_ORDER";
    public static final String TYPE_TRAINING = "TRAINING";
    public static final String TYPE_OTHER = "OTHER";

    /** 指标类型白名单（与 Mock {@code KPI_METRIC_TYPE_LABEL} 键集一致；顺序即校验提示顺序） */
    public static final List<String> METRIC_TYPES = List.of(
            TYPE_PARCEL, TYPE_PICKUP, TYPE_COMPLAINT, TYPE_ATTENDANCE,
            TYPE_SERVICE, TYPE_WORK_ORDER, TYPE_TRAINING, TYPE_OTHER);

    /** 类型中文标签 */
    public static final Map<String, String> METRIC_TYPE_LABEL = Map.of(
            TYPE_PARCEL, "派件量",
            TYPE_PICKUP, "取件及时率",
            TYPE_COMPLAINT, "客户投诉",
            TYPE_ATTENDANCE, "出勤打卡",
            TYPE_SERVICE, "服务评分",
            TYPE_WORK_ORDER, "工单处理",
            TYPE_TRAINING, "培训完成率",
            TYPE_OTHER, "其他");

    // ==================== 评分模式 ====================

    public static final String MODE_LINEAR = "LINEAR";
    public static final String MODE_TIERED = "TIERED";
    public static final String MODE_BINARY = "BINARY";

    /** 评分模式白名单（分位 QUANTILE 为全局开关，不作为单指标模式，与 db.md/V6 列枚举一致） */
    public static final List<String> SCORE_MODES = List.of(MODE_LINEAR, MODE_TIERED, MODE_BINARY);

    /** 评分模式中文标签 */
    public static final Map<String, String> SCORE_MODE_LABEL = Map.of(
            MODE_LINEAR, "线性折算",
            MODE_TIERED, "阶梯评分",
            MODE_BINARY, "达标即满分");

    // ==================== 方向 ====================

    public static final String DIRECTION_UP = "UP";
    public static final String DIRECTION_DOWN = "DOWN";

    /** 方向白名单 */
    public static final List<String> DIRECTIONS = List.of(DIRECTION_UP, DIRECTION_DOWN);

    /** 方向中文标签 */
    public static final Map<String, String> DIRECTION_LABEL = Map.of(
            DIRECTION_UP, "越高越好",
            DIRECTION_DOWN, "越低越好");

    // ==================== 适用角色 ====================

    /** role_scope 允许的角色取值（与 Mock {@code ROLE_SCOPES} 一致） */
    public static final List<String> ROLE_SCOPES = List.of("ADMIN", "STATION_ADMIN", "STAFF");

    // ==================== 等级标签 ====================

    /** 等级中文标签（阈值在 {@code hrm.algo.kpi.levels}，本表只做取值→文案映射） */
    public static final Map<String, String> LEVEL_LABEL = Map.of(
            "EXCELLENT", "优秀",
            "GOOD", "良好",
            "PASS", "合格",
            "IMPROVE", "待改进");

    /** 单位取值（用于取数分支判定：% 与 分走不同值域） */
    public static final String UNIT_PERCENT = "%";
    public static final String UNIT_SCORE = "分";

    // ==================== 只读工具 ====================

    /** 指标类型中文标签（未知取值回退原值，避免展示空白） */
    public static String metricTypeLabel(String type) {
        return METRIC_TYPE_LABEL.getOrDefault(type, type);
    }

    /** 评分模式中文标签（未知回退空串，与 Mock 一致） */
    public static String scoreModeLabel(String mode) {
        return SCORE_MODE_LABEL.getOrDefault(mode, "");
    }

    /** 方向中文标签（未知回退原值） */
    public static String directionLabel(String direction) {
        return DIRECTION_LABEL.getOrDefault(direction, direction);
    }

    /** 等级中文标签（未知回退 null，由调用方决定是否展示） */
    public static String levelLabel(String level) {
        return level == null ? null : LEVEL_LABEL.get(level);
    }
}
