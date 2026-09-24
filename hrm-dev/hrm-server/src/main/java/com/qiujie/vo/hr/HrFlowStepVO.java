package com.qiujie.vo.hr;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程步骤出参（对齐 Mock {@code toFlowVO.steps} 元素）。
 */
@Data
public class HrFlowStepVO {

    private String key;

    private String name;

    private Integer order;

    private String status;

    private String statusLabel;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime operateTime;

    private String remark;
}
