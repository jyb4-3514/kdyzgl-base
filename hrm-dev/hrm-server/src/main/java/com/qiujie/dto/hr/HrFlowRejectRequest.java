package com.qiujie.dto.hr;

import lombok.Data;

/**
 * 流程驳回入参（入职 / 离职共用）。
 */
@Data
public class HrFlowRejectRequest {

    /** 驳回原因（2-200 字） */
    private String reason;
}
