package com.qiujie.dto.hr;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 津贴项入参（{@code {key,name,amount}}）。
 */
@Data
public class SalaryAllowanceItem {

    /** 津贴键（可选） */
    private String key;

    /** 津贴名称（1-20 字） */
    private String name;

    /** 津贴金额（≥0） */
    private BigDecimal amount;
}
