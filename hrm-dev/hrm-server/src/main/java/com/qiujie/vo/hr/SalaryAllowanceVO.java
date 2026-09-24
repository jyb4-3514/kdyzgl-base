package com.qiujie.vo.hr;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 津贴项出参（{@code {key,name,amount}}，对齐 Mock {@code toSalaryVO.allowances}）。
 */
@Data
public class SalaryAllowanceVO {

    private String key;

    private String name;

    private BigDecimal amount;
}
