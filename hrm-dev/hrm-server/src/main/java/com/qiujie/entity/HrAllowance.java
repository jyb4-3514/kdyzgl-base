package com.qiujie.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 津贴项值对象（{@code hr_salary.allowances} / {@code hr_salary_log.allowances} JSON 数组元素）。
 * <p>
 * 为什么用 JSON 列而非子表：津贴项结构随业务多变、读取低频且永远整体读写（db.md §8.5 取舍），
 * 拆子表会引入无谓的连接与级联维护成本。元素字段与 Mock {@code {key,name,amount}} 逐字段一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HrAllowance {

    /** 津贴键（可选，内置项如 MEAL/TRANSPORT/HOUSING 有值；自定义项可为 null） */
    private String key;

    /** 津贴名称（1-20 字） */
    private String name;

    /** 津贴金额（≥0） */
    private BigDecimal amount;
}
