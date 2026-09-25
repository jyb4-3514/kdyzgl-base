package com.qiujie.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端类型严格归一单测（覆盖缺失 / 空白 / 未知 / 大小写混合）。
 * <p>
 * 关键变更（对齐 {@code security-client-admission-review.md} 必改 2）：<b>不再回落 {@code WEB}</b>——
 * 缺失 / 未知返回 {@code null}，由端准入策略按 fail-closed 拒绝（旧断言「回落 WEB」已显式变更）。
 * </p>
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class ClientTypeTest {

    @Test
    void parseReturnsNullForMissingOrBlank() {
        assertNull(ClientType.parse(null));
        assertNull(ClientType.parse(""));
        assertNull(ClientType.parse("   "));
    }

    @Test
    void parseIsTrimmedAndCaseInsensitive() {
        assertEquals(ClientType.ADMIN, ClientType.parse(" admin "));
        assertEquals(ClientType.STAFF, ClientType.parse("Staff"));
        assertEquals(ClientType.BOSS, ClientType.parse("boss"));
        assertEquals(ClientType.WEB, ClientType.parse("WEB"));
    }

    @Test
    void parseReturnsNullForUnknownValues() {
        // 未知 / 非法端类型一律返回 null（旧实现回落 WEB 的口子已删除）
        assertNull(ClientType.parse("H5"));
        assertNull(ClientType.parse("PC"));
        assertNull(ClientType.parse("android"));
        assertNull(ClientType.parse("123"));
    }

    @Test
    void isValidRecognisesFourKnownTypesOnly() {
        assertTrue(ClientType.isValid("ADMIN"));
        assertTrue(ClientType.isValid(" boss "));
        assertTrue(ClientType.isValid("STAFF"));
        assertTrue(ClientType.isValid("web"));
        assertFalse(ClientType.isValid("H5"));
        assertFalse(ClientType.isValid(null));
        assertFalse(ClientType.isValid(""));
        assertFalse(ClientType.isValid("   "));
    }
}
