package com.qiujie.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 员工字段格式校验单测（规则与 api.md 4.3.3 / 第 5 章一致）。
 */
class FieldValidatorTest {

    // ---------- 登录账号 ----------

    @Test
    void validUsernames() {
        assertTrue(FieldValidator.isValidUsername("admin"));
        assertTrue(FieldValidator.isValidUsername("a123"));
        assertTrue(FieldValidator.isValidUsername("zhang_san"));
        assertTrue(FieldValidator.isValidUsername("A" + "b".repeat(29)));
    }

    @Test
    void invalidUsernames() {
        assertFalse(FieldValidator.isValidUsername(null));
        assertFalse(FieldValidator.isValidUsername(""));           // 空
        assertFalse(FieldValidator.isValidUsername("1admin"));     // 数字开头
        assertFalse(FieldValidator.isValidUsername("_admin"));     // 下划线开头
        assertFalse(FieldValidator.isValidUsername("ab"));         // 不足 4 位
        assertFalse(FieldValidator.isValidUsername("ab1"));        // 不足 4 位
        assertFalse(FieldValidator.isValidUsername("a".repeat(31))); // 超 30 位
        assertFalse(FieldValidator.isValidUsername("张三"));        // 中文字符
        assertFalse(FieldValidator.isValidUsername("user-name"));  // 短横线非法
    }

    // ---------- 手机号 ----------

    @Test
    void validPhones() {
        assertTrue(FieldValidator.isValidPhone("13812345678"));
        assertTrue(FieldValidator.isValidPhone("19912345678"));
    }

    @Test
    void invalidPhones() {
        assertFalse(FieldValidator.isValidPhone(null));
        assertFalse(FieldValidator.isValidPhone("1381234567"));    // 10 位
        assertFalse(FieldValidator.isValidPhone("138123456789"));  // 12 位
        assertFalse(FieldValidator.isValidPhone("12812345678"));   // 第二位为 2
        assertFalse(FieldValidator.isValidPhone("1381234567a"));   // 含字母
        assertFalse(FieldValidator.isValidPhone("138 1234567"));   // 含空格
    }

    // ---------- 密码强度：8-20 位且同时包含字母和数字 ----------

    @Test
    void strongPasswords() {
        assertTrue(FieldValidator.isStrongPassword("Init1234"));
        assertTrue(FieldValidator.isStrongPassword("a1b2c3d4e5"));
        assertTrue(FieldValidator.isStrongPassword("A1".repeat(5) + "x")); // 11 位
    }

    @Test
    void weakPasswords() {
        assertFalse(FieldValidator.isStrongPassword(null));
        assertFalse(FieldValidator.isStrongPassword("abc123"));     // 6 位不足
        assertFalse(FieldValidator.isStrongPassword("abcdefgh"));   // 纯字母
        assertFalse(FieldValidator.isStrongPassword("12345678"));   // 纯数字
        assertFalse(FieldValidator.isStrongPassword("a1".repeat(11))); // 22 位超长
        assertFalse(FieldValidator.isStrongPassword(""));           // 空
    }

    // ---------- 日期（yyyy-MM-dd 严格） ----------

    @Test
    void validDates() {
        assertTrue(FieldValidator.isValidDate("2026-09-01"));
        assertTrue(FieldValidator.isValidDate(" 2026-09-01 ")); // 前后空白容忍
        assertTrue(FieldValidator.isValidDate(""));             // 留空合法（非必填）
        assertTrue(FieldValidator.isValidDate(null));
    }

    @Test
    void invalidDates() {
        assertFalse(FieldValidator.isValidDate("2026/09/01"));   // 分隔符错误
        assertFalse(FieldValidator.isValidDate("2026-9-1"));     // 非两位月/日
        assertFalse(FieldValidator.isValidDate("2026-02-30"));   // 非真实日期
        assertFalse(FieldValidator.isValidDate("2026-13-01"));   // 月越界
        assertFalse(FieldValidator.isValidDate("abcd-ef-gh"));
    }

    @Test
    void parseDateBehaviors() {
        assertEquals(LocalDate.of(2026, 9, 1), FieldValidator.parseDate("2026-09-01"));
        assertNull(FieldValidator.parseDate(""));
        assertNull(FieldValidator.parseDate(null));
    }
}
