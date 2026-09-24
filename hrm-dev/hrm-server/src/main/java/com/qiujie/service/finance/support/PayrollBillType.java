package com.qiujie.service.finance.support;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 工资单类型（对齐 db.md §8.6.3 与 Mock {@code PAYROLL_BILL_TYPE_LABEL}）。
 */
public enum PayrollBillType {

    /** 月度工资单 */
    MONTHLY("月度工资单"),
    /** 离职结算单 */
    SETTLEMENT("离职结算单");

    private final String label;

    PayrollBillType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Set<String> codes() {
        Set<String> codes = new LinkedHashSet<>();
        for (PayrollBillType type : values()) {
            codes.add(type.name());
        }
        return codes;
    }

    public static boolean isValid(String code) {
        return codes().contains(code);
    }

    public static String labelOf(String code) {
        for (PayrollBillType type : values()) {
            if (type.name().equals(code)) {
                return type.label;
            }
        }
        return code;
    }
}
