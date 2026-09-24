package com.qiujie.service.leave.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请假 6 态状态机纯逻辑单测（对齐 Mock {@code leaveStore.js} 的 T1–T9 迁移路径）。
 * <p>
 * 覆盖：初始态判定（站长跳级 / 无站长降级）、动作 × 状态迁移矩阵、拒绝非法流转（重复提交 / 重复撤回 /
 * 终态再操作）、目标态与驳回阶段映射。
 * <p>本机无 JDK/Maven，测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class LeaveStateMachineTest {

    private static final String PENDING_STATION = LeaveConstants.STATUS_PENDING_STATION;
    private static final String PENDING_BOSS = LeaveConstants.STATUS_PENDING_BOSS;
    private static final String APPROVED = LeaveConstants.STATUS_APPROVED;
    private static final String REJECTED = LeaveConstants.STATUS_REJECTED;
    private static final String CANCELLED = LeaveConstants.STATUS_CANCELLED;
    private static final String REVOKED = LeaveConstants.STATUS_REVOKED;

    @Test
    @DisplayName("初始态：STAFF 有站长 → 待初审；STAFF 无站长 → 待终审；站长本人 → 待终审（T1 + Q4）")
    void initialStatus() {
        assertEquals(LeaveStateMachine.Status.PENDING_STATION,
                LeaveStateMachine.initialStatus(false, true));
        assertEquals(LeaveStateMachine.Status.PENDING_BOSS,
                LeaveStateMachine.initialStatus(false, false));
        assertEquals(LeaveStateMachine.Status.PENDING_BOSS,
                LeaveStateMachine.initialStatus(true, true));
        assertEquals(LeaveStateMachine.Status.PENDING_BOSS,
                LeaveStateMachine.initialStatus(true, false));
    }

    @Test
    @DisplayName("编辑：仅待初审可改（T8）")
    void updateOnlyFromPendingStation() {
        assertTrue(LeaveStateMachine.Action.UPDATE.allows(LeaveStateMachine.Status.PENDING_STATION));
        assertFalse(LeaveStateMachine.Action.UPDATE.allows(LeaveStateMachine.Status.PENDING_BOSS));
        assertFalse(LeaveStateMachine.Action.UPDATE.allows(LeaveStateMachine.Status.APPROVED));
    }

    @Test
    @DisplayName("撤销：两种待审态可撤（T6）；已通过/终态不可撤")
    void cancelOnlyPending() {
        assertTrue(LeaveStateMachine.Action.CANCEL.allows(LeaveStateMachine.Status.PENDING_STATION));
        assertTrue(LeaveStateMachine.Action.CANCEL.allows(LeaveStateMachine.Status.PENDING_BOSS));
        assertFalse(LeaveStateMachine.Action.CANCEL.allows(LeaveStateMachine.Status.APPROVED));
        assertFalse(LeaveStateMachine.Action.CANCEL.allows(LeaveStateMachine.Status.CANCELLED));
    }

    @Test
    @DisplayName("站长初审：仅待初审（T2/T3）；目标态通过→待终审、驳回→已驳回，驳回阶段 STATION")
    void stationApproveMatrix() {
        assertTrue(LeaveStateMachine.Action.STATION_APPROVE.allows(LeaveStateMachine.Status.PENDING_STATION));
        assertFalse(LeaveStateMachine.Action.STATION_APPROVE.allows(LeaveStateMachine.Status.PENDING_BOSS));

        assertEquals(LeaveStateMachine.Status.PENDING_BOSS,
                LeaveStateMachine.approveTarget(LeaveStateMachine.Action.STATION_APPROVE, true));
        assertEquals(LeaveStateMachine.Status.REJECTED,
                LeaveStateMachine.approveTarget(LeaveStateMachine.Action.STATION_REJECT, false));
        assertEquals(LeaveConstants.STAGE_STATION,
                LeaveStateMachine.rejectStage(LeaveStateMachine.Action.STATION_REJECT, false));
        assertNull(LeaveStateMachine.rejectStage(LeaveStateMachine.Action.STATION_APPROVE, true));
    }

    @Test
    @DisplayName("老板终审：仅待终审（T4/T5）；目标态通过→已通过、驳回→已驳回，驳回阶段 BOSS")
    void finalApproveMatrix() {
        assertTrue(LeaveStateMachine.Action.FINAL_APPROVE.allows(LeaveStateMachine.Status.PENDING_BOSS));
        assertFalse(LeaveStateMachine.Action.FINAL_APPROVE.allows(LeaveStateMachine.Status.PENDING_STATION));
        // 重复终审：已通过/已驳回不再可办
        assertFalse(LeaveStateMachine.Action.FINAL_APPROVE.allows(LeaveStateMachine.Status.APPROVED));
        assertFalse(LeaveStateMachine.Action.FINAL_REJECT.allows(LeaveStateMachine.Status.REJECTED));

        assertEquals(LeaveStateMachine.Status.APPROVED,
                LeaveStateMachine.approveTarget(LeaveStateMachine.Action.FINAL_APPROVE, true));
        assertEquals(LeaveStateMachine.Status.REJECTED,
                LeaveStateMachine.approveTarget(LeaveStateMachine.Action.FINAL_REJECT, false));
        assertEquals(LeaveConstants.STAGE_BOSS,
                LeaveStateMachine.rejectStage(LeaveStateMachine.Action.FINAL_REJECT, false));
    }

    @Test
    @DisplayName("重提：仅已驳回可重提（T9）；重复撤回：仅已通过可撤（T7）")
    void resubmitAndRevokeMatrix() {
        assertTrue(LeaveStateMachine.Action.RESUBMIT.allows(LeaveStateMachine.Status.REJECTED));
        assertFalse(LeaveStateMachine.Action.RESUBMIT.allows(LeaveStateMachine.Status.APPROVED));

        assertTrue(LeaveStateMachine.Action.REVOKE.allows(LeaveStateMachine.Status.APPROVED));
        // 重复撤回：已撤回不再可办
        assertFalse(LeaveStateMachine.Action.REVOKE.allows(LeaveStateMachine.Status.REVOKED));
        assertEquals(LeaveStateMachine.Status.REVOKED,
                LeaveStateMachine.Action.REVOKE.target());
    }

    @Test
    @DisplayName("状态名映射：6 态可解析；未知/空 → null（越权判定按最小权限）")
    void statusOf() {
        assertEquals(LeaveStateMachine.Status.PENDING_STATION, LeaveStateMachine.Status.of(PENDING_STATION));
        assertEquals(LeaveStateMachine.Status.PENDING_BOSS, LeaveStateMachine.Status.of(PENDING_BOSS));
        assertEquals(LeaveStateMachine.Status.APPROVED, LeaveStateMachine.Status.of(APPROVED));
        assertEquals(LeaveStateMachine.Status.REJECTED, LeaveStateMachine.Status.of(REJECTED));
        assertEquals(LeaveStateMachine.Status.CANCELLED, LeaveStateMachine.Status.of(CANCELLED));
        assertEquals(LeaveStateMachine.Status.REVOKED, LeaveStateMachine.Status.of(REVOKED));
        assertNull(LeaveStateMachine.Status.of("GHOST"));
        assertNull(LeaveStateMachine.Status.of(null));
    }

    @Test
    @DisplayName("非审批动作调用 approveTarget → 抛异常（防误用）")
    void approveTargetRejectsNonApproveAction() {
        assertThrows(IllegalArgumentException.class,
                () -> LeaveStateMachine.approveTarget(LeaveStateMachine.Action.CANCEL, true));
    }
}
