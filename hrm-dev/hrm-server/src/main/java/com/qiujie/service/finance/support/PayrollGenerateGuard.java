package com.qiujie.service.finance.support;

import com.qiujie.entity.Payroll;

import java.util.List;

/**
 * 生成幂等判定（纯逻辑，对齐 Mock {@code generatePayrolls}）。
 * <p>
 * 口径：同月同员工同类型（MONTHLY）已存在 DRAFT / REJECTED（「可编辑」）→ <b>覆盖重建</b>；
 * 若该月已存在任何非 DRAFT/REJECTED 的月度单 → <b>整批拒绝（9405）</b>，
 * 避免把老板已审过、员工已看过的工资单悄悄改掉。
 */
public final class PayrollGenerateGuard {

    private PayrollGenerateGuard() {
    }

    /** 可编辑状态（覆盖重建范围）：DRAFT / REJECTED */
    public static List<String> editableStatuses() {
        return List.of(PayrollStatus.DRAFT.name(), PayrollStatus.REJECTED.name());
    }

    /** 单据状态是否可被重复生成覆盖 */
    public static boolean isEditable(String status) {
        return PayrollStateMachine.isEditable(status);
    }

    /**
     * 从全部工资单中找「阻断该月生成」的单据（同月 + MONTHLY + 非可编辑），无则返回 null。
     * 供 9405 文案回填单据号。测试与 Service 共用同一判定。
     */
    public static Payroll findBlockingMonthly(List<Payroll> payrolls, String month) {
        if (payrolls == null || month == null) {
            return null;
        }
        for (Payroll payroll : payrolls) {
            if (payroll == null) {
                continue;
            }
            if (!PayrollBillType.MONTHLY.name().equals(payroll.getBillType())) {
                continue;
            }
            if (!month.equals(payroll.getMonth())) {
                continue;
            }
            if (!isEditable(payroll.getStatus())) {
                return payroll;
            }
        }
        return null;
    }
}
