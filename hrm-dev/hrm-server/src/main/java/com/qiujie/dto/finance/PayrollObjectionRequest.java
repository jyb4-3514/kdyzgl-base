package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 员工提异议入参（对齐 Mock {@code objection}）：原因 2-200 字。
 */
@Data
public class PayrollObjectionRequest {

    /** 异议原因（2-200 字） */
    private String reason;
}
