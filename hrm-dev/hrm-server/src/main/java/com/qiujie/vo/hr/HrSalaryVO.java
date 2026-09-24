package com.qiujie.vo.hr;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前定薪出参（对齐 Mock {@code toSalaryVO}）。
 */
@Data
public class HrSalaryVO {

    private Long employeeId;

    private String employeeName;

    private BigDecimal basicSalary;

    private BigDecimal postSalary;

    private BigDecimal performanceBase;

    private List<SalaryAllowanceVO> allowances;

    private BigDecimal allowancesTotal;

    private BigDecimal totalSalary;

    private LocalDate effectiveDate;

    private LocalDateTime updateTime;
}
