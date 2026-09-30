package com.qiujie.service.finance.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工资单八态（对齐 db.md §8.6.3、V20 status COMMENT 与 Mock {@code PAYROLL_STATUS_LABEL}）。
 * <p>
 * 状态机流转矩阵见 {@link PayrollStateMachine}。「可编辑」自 v1.1 起拆为三判据（方案 §2.3）：
 * {@code isItemEditable}（可改/加明细）与 {@code isOverwritable}（可被 generate 覆盖重建）互不相同，
 * 账期锁走 {@code isOverwritable}。
 * <p>
 * 枚举顺序 = <b>末尾追加</b>（Q-DB-8）：既有 6 态相对顺序与名称不变，{@code OBJECTED}/{@code PAID} 追加在末尾，
 * 使 {@link #labels()} 与列表 {@code counts} 的键序稳定（仅尾部多 2 键）。
 */
public enum PayrollStatus {

    DRAFT("草稿"),
    PENDING_APPROVAL("待审核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    PUBLISHED("已发布"),
    CONFIRMED("已确认"),
    /** 员工提异议退回管理员处理（Q9 唯一允许的退回路径；内部处理态，不加入员工可见集） */
    OBJECTED("异议退回"),
    /** 管理员确认工资已发放（终态 = 归档冻结，任何写入口一律 9413） */
    PAID("已发放");

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
