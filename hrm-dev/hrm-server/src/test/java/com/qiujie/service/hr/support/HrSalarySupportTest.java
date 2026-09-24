package com.qiujie.service.hr.support;

import com.qiujie.dto.hr.SalaryAllowanceItem;
import com.qiujie.entity.HrAllowance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 定薪合计与字段校验纯逻辑单测（对齐 Mock {@code saveSalary} / {@code validateSalary}）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrSalarySupportTest {

    // ==================== 合计口径 ====================

    @Test
    @DisplayName("津贴合计：null / 空列表 / 单项 null 金额 → 0")
    void allowancesTotalBoundary() {
        assertEquals(BigDecimal.ZERO, HrSalaryCalculator.allowancesTotal(null));
        assertEquals(BigDecimal.ZERO, HrSalaryCalculator.allowancesTotal(List.of()));
        assertEquals(BigDecimal.ZERO, HrSalaryCalculator.allowancesTotal(
                List.of(new HrAllowance("MEAL", "餐补", null))));
    }

    @Test
    @DisplayName("津贴合计：正常累加")
    void allowancesTotalSum() {
        assertEquals(new BigDecimal("500"), HrSalaryCalculator.allowancesTotal(List.of(
                new HrAllowance("MEAL", "餐补", new BigDecimal("300")),
                new HrAllowance("TRANSPORT", "交通补贴", new BigDecimal("200")))));
    }

    @Test
    @DisplayName("定薪合计 = 基本 + 岗位 + 绩效基数 + 津贴合计（含 null 兜底）")
    void totalSalary() {
        assertEquals(new BigDecimal("10500"), HrSalaryCalculator.total(
                new BigDecimal("6000"), new BigDecimal("2500"), new BigDecimal("1500"), new BigDecimal("500")));
        assertEquals(new BigDecimal("0"), HrSalaryCalculator.total(null, null, null, null));
    }

    // ==================== 字段校验 ====================

    @Test
    @DisplayName("保存定薪（isCreate=true）：金额缺失或负值 → 拒绝")
    void validateCreateRejectsNegative() {
        assertEquals("basicSalary 须为不小于 0 的数字",
                HrSalaryValidator.validate(null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, true));
        assertEquals("postSalary 须为不小于 0 的数字",
                HrSalaryValidator.validate(BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO,
                        null, null, null, true));
        assertNull(HrSalaryValidator.validate(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, true));
    }

    @Test
    @DisplayName("步骤办理（isCreate=false）：未传金额不校验；显式传入负值 → 拒绝")
    void validateStepSkipsAbsentFields() {
        assertNull(HrSalaryValidator.validate(null, null, null, null, null, null, false));
        assertEquals("performanceBase 须为不小于 0 的数字",
                HrSalaryValidator.validate(null, null, new BigDecimal("-5"), null, null, null, false));
    }

    @Test
    @DisplayName("津贴项名称/金额非法 → 拒绝")
    void validateAllowances() {
        SalaryAllowanceItem bad = new SalaryAllowanceItem();
        bad.setName("");
        bad.setAmount(new BigDecimal("100"));
        assertEquals("津贴项名称长度须为 1-20",
                HrSalaryValidator.validate(null, null, null, List.of(bad), null, null, false));

        SalaryAllowanceItem bad2 = new SalaryAllowanceItem();
        bad2.setName("餐补");
        bad2.setAmount(new BigDecimal("-1"));
        assertEquals("津贴金额须为不小于 0 的数字",
                HrSalaryValidator.validate(null, null, null, List.of(bad2), null, null, false));
    }

    @Test
    @DisplayName("生效日期/原因格式 → 拒绝")
    void validateDateAndReason() {
        assertEquals("effectiveDate 格式须为 YYYY-MM-DD",
                HrSalaryValidator.validate(null, null, null, null, "2026/01/01", null, false));
        assertEquals("调薪原因长度须为 2-50",
                HrSalaryValidator.validate(null, null, null, null, "2026-01-01", "x", false));
    }
}
