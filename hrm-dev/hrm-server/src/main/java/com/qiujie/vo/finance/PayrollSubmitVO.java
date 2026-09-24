package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;

/**
 * 批量提交结果（对齐 Mock {@code submitPayrolls} 出参）：{@code submitted} + {@code payrollIds}。
 */
@Data
public class PayrollSubmitVO {

    /** 提交成功的单据数 */
    private int submitted;

    private List<Long> payrollIds;
}
