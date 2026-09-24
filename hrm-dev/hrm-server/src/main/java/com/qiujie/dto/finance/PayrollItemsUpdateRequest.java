package com.qiujie.dto.finance;

import lombok.Data;

import java.util.List;

/**
 * 修改人工项金额入参（对齐 Mock {@code updateItems}）。
 * <p>
 * 只有 MANUAL 来源项可改，且单据须处于草稿/已驳回；传入项按 {@code key} 匹配工资单明细。
 */
@Data
public class PayrollItemsUpdateRequest {

    /** 待调整项（非空数组） */
    private List<PayrollItemAdjustRequest> items;
}
