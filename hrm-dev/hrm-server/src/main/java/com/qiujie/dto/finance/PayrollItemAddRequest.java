package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 手工加/扣款入参（api.md §4.12.12，I-6）。
 * <p>
 * {@code amount} 声明为 {@code Object}：与 {@link PayrollItemAdjustRequest} 同口径，用宽松强转把
 * 「非数字」判成业务文案而非反序列化失败。{@code reason} 为必填事由（2-200），落 {@code payroll_log.reason}
 * （审计真源）并展示于明细；{@code detail} 可空，缺省由服务端按「事由：xxx」生成展示文案。
 */
@Data
public class PayrollItemAddRequest {

    /** 类型：ADDITION=增项（计应发），DEDUCTION=扣项（计扣项） */
    private String itemType;

    /** 明细名（事由摘要 / 自定义名） */
    private String itemName;

    /** 金额（须 > 0，正数；增/扣由 itemType 承载） */
    private Object amount;

    /** 事由（必填 2-200 字） */
    private String reason;

    /** 展示文案（可空；缺省按「事由：xxx」生成） */
    private String detail;
}
