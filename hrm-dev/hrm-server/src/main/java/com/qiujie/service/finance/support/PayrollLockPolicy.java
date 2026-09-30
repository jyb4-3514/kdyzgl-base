package com.qiujie.service.finance.support;

import com.qiujie.entity.Payroll;

import java.util.List;

/**
 * 账期锁判定（纯逻辑，对齐 Mock {@code findLockingPayroll}，供 P7 请假撤回使用）。
 * <p>
 * 口径：同员工同账期存在<b>非 DRAFT/REJECTED</b> 的工资单即视为「已出账」，锁定该账期；
 * 被锁定后撤回已批请假会让工资单与申请单对不上，故拒绝（P7 抛 9606）。
 * 锁策略走 {@code hrm.algo.leave.lockStatusPolicy}（默认 {@code NON_DRAFT_REJECTED}）。
 * <p>
 * 判据自 v1.1 起改绑 {@link PayrollStateMachine#isOverwritable}（方案 §2.3）——语义与旧 {@code isEditable}
 * 完全一致，仅换名以保证「审核中 / 异议退回可改明细」不误伤账期锁（行为零突变）。
 */
public final class PayrollLockPolicy {

    /** 默认锁策略：非 DRAFT/REJECTED 即锁 */
    public static final String POLICY_NON_DRAFT_REJECTED = "NON_DRAFT_REJECTED";

    private PayrollLockPolicy() {
    }

    /**
     * 单据状态是否锁定该账期。
     * <p>
     * 当前仅实现 {@code NON_DRAFT_REJECTED}；未知策略同样按该口径（保证与 Mock 等价，避免配置误写导致漏锁）。
     * TODO(扩展): 若后续新增锁策略（如「仅 CONFIRMED 锁」），在此按 policy 分支。
     */
    public static boolean isLocked(String status, String policy) {
        return !PayrollStateMachine.isOverwritable(status);
    }

    /** 从候选工资单中取第一条锁定单据，无则返回 null */
    public static Payroll findLocking(List<Payroll> payrolls, String policy) {
        if (payrolls == null) {
            return null;
        }
        for (Payroll payroll : payrolls) {
            if (payroll == null) {
                continue;
            }
            if (isLocked(payroll.getStatus(), policy)) {
                return payroll;
            }
        }
        return null;
    }
}
