package com.qiujie.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 员工字段格式校验（纯逻辑，供 DTO 之外的行级校验/单测复用）：
 * 规则与 api.md 4.3.3、第 5 章一致。
 */
public final class FieldValidator {

    /** 登录账号：以字母开头，4-30 位，仅字母/数字/下划线 */
    public static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{3,29}$");

    /** 手机号：1 开头，第二位 3-9，共 11 位数字 */
    public static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 密码强度：8-20 位，必须同时包含字母和数字 */
    public static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");

    private FieldValidator() {
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    public static boolean isStrongPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /** 日期校验：null/空白视为未填写（合法）；非空时须为严格 yyyy-MM-dd 且为真实日期 */
    public static boolean isValidDate(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        try {
            LocalDate.parse(text.trim());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** 解析 yyyy-MM-dd 日期；null/空白返回 null（调用方需先经 isValidDate 校验） */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text.trim());
    }
}
