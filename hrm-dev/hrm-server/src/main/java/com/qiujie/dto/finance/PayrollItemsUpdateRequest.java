package com.qiujie.dto.finance;

import lombok.Data;

import java.util.List;

/**
 * 修改人工项金额入参（api.md §4.12.11，C-3）。
 * <p>
 * 只有 MANUAL 来源项可改，且单据须处于 {@code isItemEditable} 状态；传入项按 {@code key} 匹配工资单明细。
 * <p>
 * {@code reason} 为金额变更事由（必填 2-200，M-3/REG-03）：任何金额变更都须可审计到「为什么改」，
 * 缺失或越界回 {@code 9412}。
 */
@Data
public class PayrollItemsUpdateRequest {

    /** 待调整项（非空数组） */
    private List<PayrollItemAdjustRequest> items;

    /** 变更事由（必填 2-200 字） */
    private String reason;
}
