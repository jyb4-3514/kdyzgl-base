package com.qiujie.service.finance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 合计口径纯逻辑单测（对齐 Mock {@code buildPayroll} / {@code updatePayrollItems}）。
 * <p>
 * 覆盖：正常增扣、扣项合计大于加项（负净额 Q3）、allowNegativeNet 开关、
 * 规则项全停用（items 为空 → net=0）、金额为 0。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollTotalsPolicyTest {

    @Test
    @DisplayName("合计：gross=增项合计，net=增项−扣项")
    void totals() {
        PayrollTotals totals = PayrollTotalsPolicy.of(List.of(
                draft("BASIC", "ADDITION", "6000"),
                draft("ALLOWANCE", "ADDITION", "500"),
                draft("LATE_FINE", "DEDUCTION", "300")), true);
        assertEquals(0, totals.additionTotal().compareTo(new BigDecimal("6500")));
        assertEquals(0, totals.deductionTotal().compareTo(new BigDecimal("300")));
        assertEquals(0, totals.grossAmount().compareTo(new BigDecimal("6500")));
        assertEquals(0, totals.netAmount().compareTo(new BigDecimal("6200")));
    }

    @Test
    @DisplayName("合计：扣项超加项 → 允许负净额（Q3 现状，不钳制）")
    void negativeNetAllowed() {
        PayrollTotals totals = PayrollTotalsPolicy.of(List.of(
                draft("BASIC", "ADDITION", "0"),
                draft("ABSENT_FINE", "DEDUCTION", "360")), true);
        assertEquals(0, totals.netAmount().compareTo(new BigDecimal("-360")));
    }

    @Test
    @DisplayName("合计：allowNegativeNet=false 时净额下限置 0（Q3 备选口径开关）")
    void negativeNetClamped() {
        PayrollTotals totals = PayrollTotalsPolicy.of(List.of(
                draft("BASIC", "ADDITION", "0"),
                draft("ABSENT_FINE", "DEDUCTION", "360")), false);
        assertEquals(0, totals.netAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, totals.grossAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("合计：规则项全停用（items 为空）→ 全 0")
    void emptyItems() {
        PayrollTotals totals = PayrollTotalsPolicy.of(List.of(), true);
        assertEquals(0, totals.additionTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, totals.deductionTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, totals.netAmount().compareTo(BigDecimal.ZERO));
    }

    private static PayrollItemDraft draft(String key, String type, String amount) {
        return new PayrollItemDraft(key, key, type, "MANUAL", new BigDecimal(amount), "测试");
    }
}
