package com.qiujie.service.workorder.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单状态机迁移矩阵单测（对齐 Mock {@code TRANSITIONS = {0:[1,3], 1:[2], 2:[1,3], 3:[]}}）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class WorkOrderStateMachineTest {

    @Test
    @DisplayName("迁移矩阵：逐对合法/非法全覆盖（含终态不可再流转）")
    void transitionMatrixExhaustive() {
        // 合法
        assertTrue(WorkOrderStateMachine.canTransition(0, 1));
        assertTrue(WorkOrderStateMachine.canTransition(0, 3));
        assertTrue(WorkOrderStateMachine.canTransition(1, 2));
        assertTrue(WorkOrderStateMachine.canTransition(2, 1));
        assertTrue(WorkOrderStateMachine.canTransition(2, 3));
        // 非法（含自环、跳跃、终态出边）
        assertFalse(WorkOrderStateMachine.canTransition(0, 0));
        assertFalse(WorkOrderStateMachine.canTransition(0, 2));
        assertFalse(WorkOrderStateMachine.canTransition(1, 0));
        assertFalse(WorkOrderStateMachine.canTransition(1, 1));
        assertFalse(WorkOrderStateMachine.canTransition(1, 3));
        assertFalse(WorkOrderStateMachine.canTransition(2, 0));
        assertFalse(WorkOrderStateMachine.canTransition(2, 2));
        assertFalse(WorkOrderStateMachine.canTransition(3, 0));
        assertFalse(WorkOrderStateMachine.canTransition(3, 1));
        assertFalse(WorkOrderStateMachine.canTransition(3, 2));
        assertFalse(WorkOrderStateMachine.canTransition(3, 3));
        // 未知状态视作不可流转（空集）
        assertFalse(WorkOrderStateMachine.canTransition(9, 1));
    }

    @Test
    @DisplayName("终态判定：2/3 为终态，0/1 为未处理完")
    void terminalAndOpen() {
        assertTrue(WorkOrderStateMachine.isTerminal(2));
        assertTrue(WorkOrderStateMachine.isTerminal(3));
        assertFalse(WorkOrderStateMachine.isTerminal(0));
        assertFalse(WorkOrderStateMachine.isTerminal(1));

        assertTrue(WorkOrderStateMachine.isOpen(0));
        assertTrue(WorkOrderStateMachine.isOpen(1));
        assertFalse(WorkOrderStateMachine.isOpen(2));
        assertFalse(WorkOrderStateMachine.isOpen(3));
    }

    @Test
    @DisplayName("动作名：目标 3→close、2→resolve、1→accept（与 Mock 三元表达式逐字等价）")
    void actionOf() {
        assertEquals(WorkOrderConstants.ACTION_CLOSE, WorkOrderStateMachine.actionOf(3));
        assertEquals(WorkOrderConstants.ACTION_RESOLVE, WorkOrderStateMachine.actionOf(2));
        assertEquals(WorkOrderConstants.ACTION_ACCEPT, WorkOrderStateMachine.actionOf(1));
        // Mock 中因目标值域为 {1,2,3}，reopen 分支不可达；此处保留等价语义
        assertEquals(WorkOrderConstants.ACTION_REOPEN, WorkOrderStateMachine.actionOf(0));
    }
}
