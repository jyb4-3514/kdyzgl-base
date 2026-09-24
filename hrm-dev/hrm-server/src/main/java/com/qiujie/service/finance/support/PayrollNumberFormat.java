package com.qiujie.service.finance.support;

import java.math.BigDecimal;

/**
 * 金额/数值的「JS 风格」格式化（纯逻辑）。
 * <p>
 * 为什么需要：算薪解释文案里内插数字（如「迟到 3 次 × 20 元」「封顶 300 元」），
 * Mock 用 JS 模板串 {@code ${n}} 输出（整数不带小数、去尾零）。Java 侧 BigDecimal
 * 默认 toString 会带指数或尾零（如 {@code 2E+1} / {@code 20.00}），直接用会与 Mock 文案不一致。
 */
public final class PayrollNumberFormat {

    private PayrollNumberFormat() {
    }

    /** BigDecimal → 无指数、去尾零的普通字符串（null → "0"，对齐 JS 数值内插） */
    public static String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }
}
