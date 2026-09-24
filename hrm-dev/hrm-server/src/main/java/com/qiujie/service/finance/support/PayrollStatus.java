package com.qiujie.service.finance.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工资单六态（对齐 db.md §8.6.3 与 Mock {@code PAYROLL_STATUS_LABEL}）。
 * <p>
 * 状态机流转矩阵见 {@link PayrollStateMachine}；「可编辑」（可改 MANUAL 项、可被重复生成覆盖）
 * 仅 DRAFT / REJECTED 两态。
 */
public enum PayrollStatus {

    DRAFT("草稿"),
    PENDING_APPROVAL("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    PUBLISHED("已发布"),
    CONFIRMED("已确认");

    private final String label;

    PayrollStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 全部状态 → 中文标签（顺序与 Mock 一致，列表计数键序稳定） */
    public static Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (PayrollStatus status : values()) {
            labels.put(status.name(), status.label);
        }
        return labels;
    }

    /** 是否为已知状态 */
    public static boolean isValid(String code) {
        for (PayrollStatus status : values()) {
            if (status.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 取中文标签；未知状态回退原值 */
    public static String labelOf(String code) {
        for (PayrollStatus status : values()) {
            if (status.name().equals(code)) {
                return status.label;
            }
        }
        return code;
    }
}
