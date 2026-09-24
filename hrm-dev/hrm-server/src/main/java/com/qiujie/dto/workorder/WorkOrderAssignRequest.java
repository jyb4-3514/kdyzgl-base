package com.qiujie.dto.workorder;

import lombok.Data;

/**
 * 指派处理人请求（对齐 Mock {@code workOrder.assign} 的 body）。
 */
@Data
public class WorkOrderAssignRequest {

    /** 被指派人（逻辑外键 employee.id） */
    private Long assigneeId;
}
