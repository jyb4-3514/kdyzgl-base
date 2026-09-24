package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;

/**
 * 批量发布结果（对齐 Mock {@code publishPayrolls} 出参）：发布数 + 跳过数 + 发布 id。
 */
@Data
public class PayrollPublishVO {

    /** 实际发布数（仅 APPROVED 被发布） */
    private int published;

    /** 因状态不符被跳过的数量 */
    private int skipped;

    private List<Long> payrollIds;
}
