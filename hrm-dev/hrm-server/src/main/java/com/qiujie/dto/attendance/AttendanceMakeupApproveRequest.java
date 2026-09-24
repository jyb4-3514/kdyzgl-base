package com.qiujie.dto.attendance;

import lombok.Data;

/**
 * 补卡审批入参（POST /attendance/makeup/{id}/approve，仅 ADMIN）。
 * {@code approved} 必须为布尔值（缺省/非布尔 → 400，对齐 Mock 校验）。
 */
@Data
public class AttendanceMakeupApproveRequest {

    private Boolean approved;

    /** 审批意见（≤200 字，可空） */
    private String approveRemark;
}
