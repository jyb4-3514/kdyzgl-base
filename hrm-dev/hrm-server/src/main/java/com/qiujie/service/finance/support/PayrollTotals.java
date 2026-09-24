package com.qiujie.service.finance.support;

import java.math.BigDecimal;

/**
 * 工资单合计结果。
 *
 * @param additionTotal  增项合计
 * @param deductionTotal 扣项合计
 * @param grossAmount    应发合计（=增项合计）
 * @param netAmount      实发净额（应发 − 扣项）
 */
public record PayrollTotals(BigDecimal additionTotal,
                            BigDecimal deductionTotal,
                            BigDecimal grossAmount,
                            BigDecimal netAmount) {
}
