package com.qiujie.dto.workorder;

import lombok.Data;

/**
 * 工单转单请求（对齐 Mock {@code workOrder.transfer} 的 body）。
 */
@Data
public class WorkOrderTransferRequest {

    /** 新处理人（逻辑外键 employee.id）；须在职且不能为操作人本人 */
    private Long toEmployeeId;

    /** 转单理由（2-100 字） */
    private String reason;
}
