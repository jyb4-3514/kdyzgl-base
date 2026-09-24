package com.qiujie.vo.workorder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单转单留痕出参（对齐 Mock {@code pushWorkOrderTransfer} 的行结构）。
 */
@Data
public class WorkOrderTransferVO {

    private Long id;

    private Long workOrderId;

    private Long fromEmployeeId;

    private String fromEmployeeName;

    private Long toEmployeeId;

    private String toEmployeeName;

    private String reason;

    private Long operatorId;

    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime transferTime;
}
