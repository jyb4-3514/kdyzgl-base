package com.qiujie.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端类型归一单测（M1 端类型归一，覆盖缺失/非法/大小写混合/含空白）。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
 */
class ClientTypeTest {

    @Test
    void normalizeFallsBackToWebForMissingOrBlank() {
        assertEquals(ClientType.WEB.name(), ClientType.normalize(null));
        assertEquals(ClientType.WEB.name(), ClientType.normalize(""));
        assertEquals(ClientType.WEB.name(), ClientType.normalize("   "));
    }

    @Test
    void normalizeIsTrimmedAndCaseInsensitive() {
        assertEquals(ClientType.ADMIN.name(), ClientType.normalize(" admin "));
        assertEquals(ClientType.STAFF.name(), ClientType.normalize("Staff"));
        assertEquals(ClientType.BOSS.name(), ClientType.normalize("boss"));
        assertEquals(ClientType.WEB.name(), ClientType.normalize("WEB"));
    }

    @Test
    void normalizeFallsBackToWebForUnknownValue() {
        // 未知/非法端类型一律回落 WEB，防止任意串进入会话存储
        assertEquals(ClientType.WEB.name(), ClientType.normalize("H5"));
        assertEquals(ClientType.WEB.name(), ClientType.normalize("PC"));
        assertEquals(ClientType.WEB.name(), ClientType.normalize("android"));
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

    @Test
    void defaultIsWeb() {
        assertEquals(ClientType.WEB, ClientType.DEFAULT);
    }
}
