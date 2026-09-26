package com.qiujie.service.registration.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注册域常量（状态 / 来源 / 标签），单一真源（registration-design §1.2 / §2.2）。
 * <p>
 * 状态机与 {@code hr_flow.status}（三态，复用不改枚举）分离：申请侧独立四态。
 * 标签映射用于出参 {@code statusLabel}，与前端展示同源，禁止在 Service 内散落字面量。
 */
public final class RegistrationConstants {

    private RegistrationConstants() {
    }

    // ==================== 申请状态（申请侧权威） ====================

    /** 已提交，审批中 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 审批通过且联动成功（终态） */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 已驳回（终态；可另起新申请） */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 超时失效（终态；{@code now >= expire_time} 且仍 SUBMITTED，惰性判定或清理任务） */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 撤回（一期不产生，枚举保留不用，U-06/R-4 取消） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    // ==================== 注册渠道来源（审计） ====================

    /** 一期唯一渠道：员工端 H5 */
    public static final String SOURCE_STAFF_H5 = "STAFF_H5";

    /** 超时失效驳回原因快照（写入 hr_flow.reject_reason，口径 §1.2） */
    public static final String REASON_EXPIRED = "超时失效";

    private static final Map<String, String> STATUS_LABEL = new LinkedHashMap<>();

    static {
        STATUS_LABEL.put(STATUS_SUBMITTED, "审批中");
        STATUS_LABEL.put(STATUS_APPROVED, "已通过");
        STATUS_LABEL.put(STATUS_REJECTED, "已驳回");
        STATUS_LABEL.put(STATUS_EXPIRED, "已失效");
        STATUS_LABEL.put(STATUS_CANCELLED, "已撤回");
    }

    /** 状态中文标签（未知键返回 null，由出参原样透出 status） */
    public static String statusLabel(String status) {
        return STATUS_LABEL.get(status);
    }
}
