package com.qiujie.vo.finance;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资单明细出参（对齐 Mock {@code toPayrollVO} 内的 items 元素）。
 */
@Data
public class PayrollItemVO {

    private String key;

    private String name;

    private String type;

    private String typeLabel;

    private String source;

    private String sourceLabel;

    /** 金额（正数，增/扣由 type 承载） */
    private BigDecimal amount;

    /** 取数解释文案 */
    private String detail;
}
