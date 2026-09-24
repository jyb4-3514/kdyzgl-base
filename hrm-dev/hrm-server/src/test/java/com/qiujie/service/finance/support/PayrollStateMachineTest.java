package com.qiujie.service.finance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 六态状态机纯逻辑单测（对齐 Mock {@code PAYROLL_ACTIONS}）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollStateMachineTest {

    @Test
    @DisplayName("流转矩阵与 Mock 逐条一致")
    void actionsMatrix() {
        assertEquals(List.of("submit"), PayrollStateMachine.actionsOf("DRAFT"));
        assertEquals(List.of("submit"), PayrollStateMachine.actionsOf("REJECTED"));
        assertEquals(List.of("approve", "reject"), PayrollStateMachine.actionsOf("PENDING_APPROVAL"));
        assertEquals(List.of("publish"), PayrollStateMachine.actionsOf("APPROVED"));
        assertEquals(List.of("confirm", "objection"), PayrollStateMachine.actionsOf("PUBLISHED"));
        assertEquals(List.of(), PayrollStateMachine.actionsOf("CONFIRMED"));
        assertEquals(List.of(), PayrollStateMachine.actionsOf("UNKNOWN"));
    }

    @Test
    @DisplayName("非法流转被拒绝")
    void illegalTransitions() {
        assertFalse(PayrollStateMachine.allows("DRAFT", "approve"));
        assertFalse(PayrollStateMachine.allows("DRAFT", "publish"));
        assertFalse(PayrollStateMachine.allows("CONFIRMED", "objection"));
        assertFalse(PayrollStateMachine.allows("APPROVED", "submit"));
        assertTrue(PayrollStateMachine.allows("PUBLISHED", "objection"));
        assertTrue(PayrollStateMachine.allows("PENDING_APPROVAL", "reject"));
    }

    @Test
    @DisplayName("可编辑仅 DRAFT / REJECTED")
    void editable() {
        assertTrue(PayrollStateMachine.isEditable("DRAFT"));
        assertTrue(PayrollStateMachine.isEditable("REJECTED"));
        assertFalse(PayrollStateMachine.isEditable("PENDING_APPROVAL"));
        assertFalse(PayrollStateMachine.isEditable("APPROVED"));
        assertFalse(PayrollStateMachine.isEditable("PUBLISHED"));
        assertFalse(PayrollStateMachine.isEditable("CONFIRMED"));
    }
}
