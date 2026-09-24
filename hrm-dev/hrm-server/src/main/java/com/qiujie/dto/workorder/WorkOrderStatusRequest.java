package com.qiujie.dto.workorder;

import lombok.Data;

/**
 * 工单流转请求（对齐 Mock {@code workOrder.changeStatus} 的 body）。
 * <p>
 * 目标状态须落在状态机迁移矩阵内，否则回 8001；{@code remark} 写入时间线 content（缺省空串）。
 */
@Data
public class WorkOrderStatusRequest {

    /** 目标状态：1=处理中 2=已解决 3=已关闭 */
    private Integer status;

    /** 备注（写入时间线，可空） */
    private String remark;
}
