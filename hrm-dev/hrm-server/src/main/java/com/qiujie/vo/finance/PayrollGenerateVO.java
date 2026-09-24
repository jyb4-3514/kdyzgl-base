package com.qiujie.vo.finance;

import lombok.Data;

import java.util.List;

/**
 * 批量生成结果（对齐 Mock {@code generatePayrolls} 出参）。
 */
@Data
public class PayrollGenerateVO {

    private String month;

    private Long ruleId;

    private String ruleName;

    /** 生成（含覆盖重建）的单据数 */
    private int created;

    /** 生成/重建的工资单 id 列表 */
    private List<Long> payrollIds;
}
