package com.qiujie.service.hr.support;

import com.qiujie.entity.HrAllowance;

import java.math.BigDecimal;
import java.util.List;

/**
 * 定薪合计纯逻辑（对齐 Mock {@code saveSalary} 口径）。
 * <p>
 * {@code allowancesTotal = Σ amount}，{@code totalSalary = basic + post + performanceBase + allowancesTotal}。
 * <b>口径红线</b>：本公式源自 Mock，属计费口径；如需变更须停止实现并回报主智能体（由用户确认口径）。
 * 纯函数便于单测覆盖空津贴 / 零值 / null 等边界。
 */
public final class HrSalaryCalculator {

    private HrSalaryCalculator() {
    }

    /** 津贴合计（null / 空列表 → 0；单项 amount 为 null → 0） */
    public static BigDecimal allowancesTotal(List<HrAllowance> allowances) {
        if (allowances == null || allowances.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (HrAllowance item : allowances) {
            if (item != null && item.getAmount() != null) {
                sum = sum.add(item.getAmount());
            }
        }
        return sum;
    }

    /** 定薪合计 = 基本 + 岗位 + 绩效基数 + 津贴合计（各项 null → 0） */
    public static BigDecimal total(BigDecimal basicSalary, BigDecimal postSalary,
                                   BigDecimal performanceBase, BigDecimal allowancesTotal) {
        return nullToZero(basicSalary)
                .add(nullToZero(postSalary))
                .add(nullToZero(performanceBase))
                .add(nullToZero(allowancesTotal));
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
