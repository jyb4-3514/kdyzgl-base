package com.qiujie.service.finance.support;

import com.qiujie.entity.Payroll;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 八态状态机纯逻辑单测（对齐方案 v1.5 §2.2 动作矩阵、§2.3 判据拆分、§2.9 终态守卫）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollStateMachineTest {

    @Test
    @DisplayName("动作级流转矩阵（8 态；item-add/item-update 为明细级不入 actions[]）")
    void actionsMatrix() {
        assertEquals(List.of("submit"), PayrollStateMachine.actionsOf("DRAFT"));
        assertEquals(List.of("submit"), PayrollStateMachine.actionsOf("REJECTED"));
        assertEquals(List.of("approve", "reject"), PayrollStateMachine.actionsOf("PENDING_APPROVAL"));
        assertEquals(List.of("publish"), PayrollStateMachine.actionsOf("APPROVED"));
        assertEquals(List.of("confirm", "objection"), PayrollStateMachine.actionsOf("PUBLISHED"));
        // CONFIRMED 自八态起允许 pay（I-8）；OBJECTED 允许再发布 + 可选二次审批；PAID 显式空集
        assertEquals(List.of("pay"), PayrollStateMachine.actionsOf("CONFIRMED"));
        assertEquals(List.of("publish", "submit"), PayrollStateMachine.actionsOf("OBJECTED"));
        assertEquals(List.of(), PayrollStateMachine.actionsOf("PAID"));
        assertEquals(List.of(), PayrollStateMachine.actionsOf("UNKNOWN"));
    }

    @Test
    @DisplayName("非法流转被拒绝")
    void illegalTransitions() {
        assertFalse(PayrollStateMachine.allows("DRAFT", "approve"));
        assertFalse(PayrollStateMachine.allows("DRAFT", "publish"));
        assertFalse(PayrollStateMachine.allows("CONFIRMED", "objection"));
        assertFalse(PayrollStateMachine.allows("APPROVED", "submit"));
        assertFalse(PayrollStateMachine.allows("OBJECTED", "approve"));
        assertFalse(PayrollStateMachine.allows("PAID", "publish"));
        assertFalse(PayrollStateMachine.allows("PAID", "pay"));
        assertTrue(PayrollStateMachine.allows("PUBLISHED", "objection"));
        assertTrue(PayrollStateMachine.allows("PENDING_APPROVAL", "reject"));
        assertTrue(PayrollStateMachine.allows("OBJECTED", "publish"));
        assertTrue(PayrollStateMachine.allows("CONFIRMED", "pay"));
    }

    @Test
    @DisplayName("旧判据 isEditable 语义 = isOverwritable（仅 DRAFT / REJECTED；行为零突变）")
    void editable() {
        assertTrue(PayrollStateMachine.isEditable("DRAFT"));
        assertTrue(PayrollStateMachine.isEditable("REJECTED"));
        assertFalse(PayrollStateMachine.isEditable("PENDING_APPROVAL"));
        assertFalse(PayrollStateMachine.isEditable("APPROVED"));
        assertFalse(PayrollStateMachine.isEditable("PUBLISHED"));
        assertFalse(PayrollStateMachine.isEditable("CONFIRMED"));
        assertFalse(PayrollStateMachine.isEditable("OBJECTED"));
        assertFalse(PayrollStateMachine.isEditable("PAID"));
    }

    @Test
    @DisplayName("isOverwritable：仅 DRAFT / REJECTED（PENDING_APPROVAL / OBJECTED 不可被覆盖）")
    void overwritable() {
        assertTrue(PayrollStateMachine.isOverwritable("DRAFT"));
        assertTrue(PayrollStateMachine.isOverwritable("REJECTED"));
        assertFalse(PayrollStateMachine.isOverwritable("PENDING_APPROVAL"));
        assertFalse(PayrollStateMachine.isOverwritable("OBJECTED"));
        assertFalse(PayrollStateMachine.isOverwritable("PAID"));
    }

    @Test
    @DisplayName("isItemEditable：DRAFT / REJECTED / PENDING_APPROVAL / OBJECTED（审核中与异议退回可改）")
    void itemEditable() {
        assertTrue(PayrollStateMachine.isItemEditable("DRAFT"));
        assertTrue(PayrollStateMachine.isItemEditable("REJECTED"));
        // Q6 新增：审核中可改明细
        assertTrue(PayrollStateMachine.isItemEditable("PENDING_APPROVAL"));
        // Q9 新增：异议退回可改后再次发布
        assertTrue(PayrollStateMachine.isItemEditable("OBJECTED"));
        assertFalse(PayrollStateMachine.isItemEditable("APPROVED"));
        assertFalse(PayrollStateMachine.isItemEditable("PUBLISHED"));
        assertFalse(PayrollStateMachine.isItemEditable("CONFIRMED"));
        assertFalse(PayrollStateMachine.isItemEditable("PAID"));
    }

    @Test
    @DisplayName("assertMutable：PAID 一律 9413；非 PAID 放行")
    void assertMutable() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PayrollStateMachine.assertMutable(payroll("PAID"), "submit"));
        assertEquals(ErrorCode.FINANCE_PAYROLL_ARCHIVED.getCode(), ex.getCode());
        assertEquals(9413, ex.getCode());

        // 与动作无关：换动作仍 9413
        assertThrows(BusinessException.class,
                () -> PayrollStateMachine.assertMutable(payroll("PAID"), "pay"));
        // null 单不在此处报错（交调用方 9402 判定）
        assertDoesNotThrow(() -> PayrollStateMachine.assertMutable(null, "submit"));
        for (String status : List.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED",
                "PUBLISHED", "CONFIRMED", "OBJECTED")) {
            assertDoesNotThrow(() -> PayrollStateMachine.assertMutable(payroll(status), "item-update"));
        }
    }

    private static Payroll payroll(String status) {
        Payroll payroll = new Payroll();
        payroll.setStatus(status);
        return payroll;
    }
}
