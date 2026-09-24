package com.qiujie.vo.workorder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建工单出参（对齐 Mock {@code create} 的 {@code ok({id, orderNo, source})}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderCreateVO {

    private Long id;

    private String orderNo;

    private String source;
}
