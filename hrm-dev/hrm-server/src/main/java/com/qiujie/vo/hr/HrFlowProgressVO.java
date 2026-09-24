package com.qiujie.vo.hr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程进度出参（对齐 Mock {@code toFlowVO.progress}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HrFlowProgressVO {

    /** 已完成步骤数 */
    private int done;

    /** 步骤总数 */
    private int total;
}
