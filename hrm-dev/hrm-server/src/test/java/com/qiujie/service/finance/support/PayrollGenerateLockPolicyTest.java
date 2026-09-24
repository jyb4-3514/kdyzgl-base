package com.qiujie.service.finance.support;

import com.qiujie.entity.Payroll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成幂等判定 + 账期锁判定纯逻辑单测。
 * <p>
 * 覆盖：同月已提交/已发布阻断（9405）、草稿/驳回可覆盖重建、SETTLEMENT 不参与月度阻断、
 * 账期锁（非 DRAFT/REJECTED 即锁）命中与未命中。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollGenerateLockPolicyTest {

    private static final String MONTH = "2026-09";

    // ==================== 生成幂等 ====================

    @Test
    @DisplayName("幂等：草稿/驳回不阻断，非可编辑状态阻断")
    void blocking() {
        assertNull(PayrollGenerateGuard.findBlockingMonthly(List.of(
                payroll(1L, MONTH, "MONTHLY", "DRAFT"),
                payroll(2L, MONTH, "MONTHLY", "REJECTED")), MONTH));

        Payroll blocking = PayrollGenerateGuard.findBlockingMonthly(List.of(
                payroll(1L, MONTH, "MONTHLY", "DRAFT"),
                payroll(2L, MONTH, "MONTHLY", "PENDING_APPROVAL")), MONTH);
        assertNotNull(blocking);
        assertEquals(2L, blocking.getId());

        assertNotNull(PayrollGenerateGuard.findBlockingMonthly(List.of(
                payroll(3L, MONTH, "MONTHLY", "PUBLISHED")), MONTH));
    }

    @Test
    @DisplayName("幂等：SETTLEMENT 不参与月度阻断；其它月份不阻断")
    void nonMonthlyIgnored() {
        assertNull(PayrollGenerateGuard.findBlockingMonthly(List.of(
                payroll(1L, MONTH, "SETTLEMENT", "CONFIRMED")), MONTH));
        assertNull(PayrollGenerateGuard.findBlockingMonthly(List.of(
                payroll(1L, "2026-08", "MONTHLY", "PUBLISHED")), MONTH));
    }

    @Test
    @DisplayName("幂等：可编辑状态集为 DRAFT / REJECTED")
    void editableStatuses() {
        assertEquals(List.of("DRAFT", "REJECTED"), PayrollGenerateGuard.editableStatuses());
        assertTrue(PayrollGenerateGuard.isEditable("DRAFT"));
        assertFalse(PayrollGenerateGuard.isEditable("APPROVED"));
    }

    // ==================== 账期锁（P7） ====================

    @Test
    @DisplayName("账期锁：非 DRAFT/REJECTED 即锁（不分单据类型）")
    void lockPolicy() {
        assertFalse(PayrollLockPolicy.isLocked("DRAFT", PayrollLockPolicy.POLICY_NON_DRAFT_REJECTED));
        assertFalse(PayrollLockPolicy.isLocked("REJECTED", PayrollLockPolicy.POLICY_NON_DRAFT_REJECTED));
        assertTrue(PayrollLockPolicy.isLocked("PENDING_APPROVAL", PayrollLockPolicy.POLICY_NON_DRAFT_REJECTED));
        assertTrue(PayrollLockPolicy.isLocked("PUBLISHED", PayrollLockPolicy.POLICY_NON_DRAFT_REJECTED));
        // 未知策略仍按默认口径，避免配置误写导致漏锁
        assertTrue(PayrollLockPolicy.isLocked("CONFIRMED", "SOMETHING_ELSE"));
    }

    @Test
    @DisplayName("账期锁：命中返回单据，未命中返回 null")
    void findLocking() {
        assertNull(PayrollLockPolicy.findLocking(List.of(
                payroll(1L, MONTH, "MONTHLY", "DRAFT"),
                payroll(2L, MONTH, "SETTLEMENT", "REJECTED")),
                PayrollLockPolicy.POLICY_NON_DRAFT_REJECTED));

        Payroll locking = PayrollLockPolicy.findLocking(List.of(
                payroll(1L, MONTH, "MONTHLY", "DRAFT"),
                payroll(2L, MONTH, "MONTHLY", "PUBLISHED")),
                PayrollLockPolicy.POLICY_NON_DRAFT_REJECTED);
        assertNotNull(locking);
        assertEquals(2L, locking.getId());
    }

    // ==================== 夹具 ====================

    private static Payroll payroll(Long id, String month, String billType, String status) {
        Payroll payroll = new Payroll();
        payroll.setId(id);
        payroll.setMonth(month);
        payroll.setBillType(billType);
        payroll.setStatus(status);
        payroll.setPayrollNo("PAY-" + month.replace("-", "") + "-000" + id);
        return payroll;
    }
}
