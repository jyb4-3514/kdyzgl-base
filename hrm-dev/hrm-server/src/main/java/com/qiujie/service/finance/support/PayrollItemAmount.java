package com.qiujie.service.finance.support;

import java.math.BigDecimal;

/**
 * 单项解析结果（金额 + 取数解释文案）。纯数据载体，供注册表统一返回。
 */
public record PayrollItemAmount(BigDecimal amount, String detail) {

    public static PayrollItemAmount zero(String detail) {
        return new PayrollItemAmount(BigDecimal.ZERO, detail);
    }
}
