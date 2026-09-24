package com.qiujie.service.finance.support;

/**
 * 工资单项类型（对齐 db.md §8.6.2 与 Mock {@code PAYROLL_ITEM_TYPE_LABEL}）。
 * <p>
 * 为什么用枚举收敛：类型字符串散落为字面量时拼写漂移会让「增项/扣项」静默错位，
 * 合计公式按类型分流即失效。合法性校验与标签统一从本枚举取。
 */
public enum PayrollItemType {

    /** 增项 */
    ADDITION("增项"),
    /** 扣项 */
    DEDUCTION("扣项");

    private final String label;

    PayrollItemType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 是否为已知类型 */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (PayrollItemType type : values()) {
            if (type.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 取中文标签；未知类型回退原值（对齐 Mock {@code ... || code}） */
    public static String labelOf(String code) {
        for (PayrollItemType type : values()) {
            if (type.name().equals(code)) {
                return type.label;
            }
        }
        return code;
    }
}
