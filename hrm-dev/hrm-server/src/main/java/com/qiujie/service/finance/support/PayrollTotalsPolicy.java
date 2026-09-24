package com.qiujie.service.finance.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工资单合计口径（纯逻辑，与 Mock {@code buildPayroll} / {@code updatePayrollItems} 逐式一致）。
 * <p>
 * <pre>
 * additionTotal  = Σ(ADDITION.amount)
 * deductionTotal = Σ(DEDUCTION.amount)
 * grossAmount    = additionTotal
 * netAmount      = additionTotal − deductionTotal
 * </pre>
 * 为什么「是否允许负净额」必须外置：Q3 未裁定，Mock 现状<b>允许</b>负净额（实测最小 −360 元）；
 * 本方法按开关决定是否钳制到 0，默认与 Mock 一致（允许），<b>不自行钳制</b>。
 */
public final class PayrollTotalsPolicy {

    private PayrollTotalsPolicy() {
    }

    /**
     * 汇总工资单项。
     *
     * @param items            已解析的明细行（type 为 ADDITION / DEDUCTION）
     * @param allowNegativeNet 是否允许负净额（{@code hrm.algo.payroll.allowNegativeNet}）
     */
    public static PayrollTotals of(List<PayrollItemDraft> items, boolean allowNegativeNet) {
        BigDecimal additionTotal = BigDecimal.ZERO;
        BigDecimal deductionTotal = BigDecimal.ZERO;
        if (items != null) {
            for (PayrollItemDraft item : items) {
                if (item == null || item.amount() == null) {
                    continue;
                }
                if (PayrollItemType.ADDITION.name().equals(item.type())) {
                    additionTotal = additionTotal.add(item.amount());
                } else if (PayrollItemType.DEDUCTION.name().equals(item.type())) {
                    deductionTotal = deductionTotal.add(item.amount());
                }
            }
        }
        BigDecimal netAmount = additionTotal.subtract(deductionTotal);
        if (!allowNegativeNet && netAmount.signum() < 0) {
            netAmount = BigDecimal.ZERO;
        }
        return new PayrollTotals(additionTotal, deductionTotal, additionTotal, netAmount);
    }
}
