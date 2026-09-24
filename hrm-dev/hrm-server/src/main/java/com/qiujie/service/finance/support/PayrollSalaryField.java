package com.qiujie.service.finance.support;

import com.qiujie.entity.HrSalary;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人事定薪字段映射（纯逻辑）：把规则项声明的 {@code field} 映射到 {@link HrSalary} 取值与中文标签。
 * <p>
 * 与 Mock {@code SALARY_FIELD_LABEL} 逐键一致（basicSalary/postSalary/performanceBase）；
 * 作为 FIXED 与 KPI 两类解析器的公共取数口，避免「同一字段两处各写一份 switch」。
 */
public final class PayrollSalaryField {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("basicSalary", "基本工资");
        LABELS.put("postSalary", "岗位工资");
        LABELS.put("performanceBase", "绩效基数");
    }

    private PayrollSalaryField() {
    }

    /**
     * 取定薪字段值（null / 未知字段 → 0，对齐 Mock {@code Number(salary[field]) || 0}）。
     */
    public static BigDecimal value(HrSalary salary, String field) {
        if (salary == null || field == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = switch (field) {
            case "basicSalary" -> salary.getBasicSalary();
            case "postSalary" -> salary.getPostSalary();
            case "performanceBase" -> salary.getPerformanceBase();
            case "allowancesTotal" -> salary.getAllowancesTotal();
            default -> null;
        };
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 取字段中文标签（未知字段 → 原字段名，对齐 Mock {@code SALARY_FIELD_LABEL[field] || field}） */
    public static String label(String field) {
        if (field == null) {
            return null;
        }
        return LABELS.getOrDefault(field, field);
    }
}
