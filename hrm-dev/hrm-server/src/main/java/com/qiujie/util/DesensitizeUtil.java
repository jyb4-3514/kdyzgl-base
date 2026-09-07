package com.qiujie.util;

/**
 * 敏感字段脱敏工具（决策 D5）：
 * - 11 位手机号：前 3 后 4（138****5678）；
 * - 非 11 位号码：前 3 位 + ****；
 * - 适用所有 JSON 出参；例外：Excel 导出输出完整手机号。
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /** 手机号脱敏（null/空串原样返回） */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        // 非 11 位：前 3 位 + ****（不足 3 位时取全部）
        int prefixLength = Math.min(3, phone.length());
        return phone.substring(0, prefixLength) + "****";
    }
}
