package com.qiujie.dto.hr;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 定薪保存入参（对齐 Mock {@code saveSalary} 载荷）。
 * <p>
 * 显式传入的字段才覆盖（未传 = 保持现值）；{@code null} 在 Java 侧无法与「未传」区分，均按「不改」处理。
 */
@Data
public class HrSalaryUpdateRequest {

    /** 基本工资（≥0） */
    private BigDecimal basicSalary;

    /** 岗位工资（≥0） */
    private BigDecimal postSalary;

    /** 绩效基数（≥0） */
    private BigDecimal performanceBase;

    /** 津贴项（传数组则整表替换） */
    private List<SalaryAllowanceItem> allowances;

    /** 生效日期（yyyy-MM-dd），缺省为今天 */
    private String effectiveDate;

    /** 变更原因（2-50 字） */
    private String reason;
}
