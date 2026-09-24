package com.qiujie.service.finance.support;

import com.qiujie.entity.HrSalary;

import java.math.BigDecimal;

/**
 * 算薪上下文（对齐 Mock {@code contextOf}）：定薪 / 考勤 / KPI 三处取数。
 * <p>
 * 为什么在批次前段只构造一次并复用：三处取数各只调用一次（非逐项查询），
 * 使单张工资单复杂度为 O(I log I + I)，整批为 O(E × I log I)，不随项数恶化（算法 S2 §4.2）。
 * 三者均可为 null，表示「无该数据源」——解析器按 0 计并写解释，不中断整批。
 *
 * @param salary     当前定薪（无档案 → null）
 * @param attendance 当月考勤统计（无数据 → null）
 * @param kpiScore   当月 KPI 总分（无评分 → null）
 */
public record PayrollCalcContext(HrSalary salary, AttendanceStat attendance, BigDecimal kpiScore) {

    /** 全空上下文（用于无档案/无考勤/无评分的退化场景） */
    public static PayrollCalcContext empty() {
        return new PayrollCalcContext(null, null, null);
    }
}
