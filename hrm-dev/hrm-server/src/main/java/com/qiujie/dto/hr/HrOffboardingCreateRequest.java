package com.qiujie.dto.hr;

import lombok.Data;

/**
 * 发起离职流程入参（对齐 Mock {@code createOffboarding} + routes/hr.js 校验）。
 */
@Data
public class HrOffboardingCreateRequest {

    /** 员工 id */
    private Long employeeId;

    /** 离职类型：RESIGN/DISMISS/RETIRE */
    private String type;

    /** 离职原因（2-200 字） */
    private String reason;

    /** 最后工作日（yyyy-MM-dd） */
    private String lastWorkDate;
}
