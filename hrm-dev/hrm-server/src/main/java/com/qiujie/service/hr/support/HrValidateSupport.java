package com.qiujie.service.hr.support;

import com.qiujie.util.FieldValidator;

/**
 * 人事域轻量校验助手（纯逻辑，供档案 / 定薪校验复用）。
 * <p>
 * 日期与手机号复用 P0 的 {@link FieldValidator}（口径不重造）；文本长度与空白判定在此收敛。
 */
public final class HrValidateSupport {

    private HrValidateSupport() {
    }

    /** 空白判定（null / 空串 / 全空白） */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 文本长度（去首尾空白后）落在 [min, max] 内 */
    public static boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }

    /** 日期格式（null / 空白视为合法=未填写） */
    public static boolean isDate(String value) {
        return FieldValidator.isValidDate(value);
    }

    /** 手机号格式 */
    public static boolean isPhone(String value) {
        return FieldValidator.isValidPhone(value);
    }
}
