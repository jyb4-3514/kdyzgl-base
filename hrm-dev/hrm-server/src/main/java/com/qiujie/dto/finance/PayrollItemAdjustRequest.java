package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 单项金额调整入参（对齐 Mock {@code updatePayrollItems} 的 item）。
 * <p>
 * {@code amount} 声明为 {@code Object} 而非 {@code BigDecimal}：Mock 用 {@code Number(item.amount)} 宽松判定，
 * 非法值要回「金额须为数字」的业务文案，而非反序列化失败；故由 Service 统一按 JS 语义强转。
 */
@Data
public class PayrollItemAdjustRequest {

    /** 规则项键（与工资单明细 items[].key 匹配） */
    private String key;

    /** 金额（数字；非数字 → 400 文案「金额须为数字」） */
    private Object amount;
}
