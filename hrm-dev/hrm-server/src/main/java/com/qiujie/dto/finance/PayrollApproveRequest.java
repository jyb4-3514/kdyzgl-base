package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 审核入参（对齐 Mock {@code approve}）：{@code approved} 为布尔值，驳回时 {@code approveRemark} 记入意见。
 */
@Data
public class PayrollApproveRequest {

    /** true=通过，false=驳回 */
    private Boolean approved;

    /** 审核意见（≤200 字，可空） */
    private String approveRemark;
}
