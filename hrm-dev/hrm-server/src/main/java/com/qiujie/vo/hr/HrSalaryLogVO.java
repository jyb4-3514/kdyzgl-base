package com.qiujie.vo.hr;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调薪留痕出参（对齐 Mock {@code toSalaryLogVO}）。
 */
@Data
public class HrSalaryLogVO {

    private Long id;

    private LocalDate effectiveDate;

    private String changeType;

    private String changeTypeLabel;

    private BigDecimal basicSalary;

    private BigDecimal postSalary;

    private BigDecimal performanceBase;

    private List<SalaryAllowanceVO> allowances;

    private BigDecimal allowancesTotal;

    private BigDecimal totalSalary;

    private String reason;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
