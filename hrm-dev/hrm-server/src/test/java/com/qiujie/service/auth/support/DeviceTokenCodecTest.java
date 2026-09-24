package com.qiujie.service.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备信任令牌编解码单测（M4）：高熵、摘要确定性/长度、空值、恒定时间比较。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class DeviceTokenCodecTest {

    @Test
    @DisplayName("newToken：64 位 hex（256 bit）且高熵不重复")
    void newTokenFormatAndEntropy() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String token = DeviceTokenCodec.newToken();
            assertEquals(64, token.length(), "令牌长度应为 64 hex");
            assertTrue(token.matches("[0-9a-f]{64}"), "实际=" + token);
            seen.add(token);
        }
        assertEquals(500, seen.size(), "令牌出现重复，疑似低熵实现");
    }

    @Test
    @DisplayName("sha256Hex：确定性、64 位 hex；不同输入不同摘要；null/空白返回 null")
    void sha256Digest() {
        String digest = DeviceTokenCodec.sha256Hex("abc");
        assertEquals(64, digest.length());
        assertEquals(digest, DeviceTokenCodec.sha256Hex("abc"));
        assertNotEquals(digest, DeviceTokenCodec.sha256Hex("abd"));
        assertNull(DeviceTokenCodec.sha256Hex(null));
        assertNull(DeviceTokenCodec.sha256Hex(""));
        assertNull(DeviceTokenCodec.sha256Hex("   "));
    }

    @Test
    @DisplayName("digestEquals：相等/不等/null 全分支（恒定时间比较）")
    void digestEquals() {
        String a = DeviceTokenCodec.sha256Hex("abc");
        assertTrue(DeviceTokenCodec.digestEquals(a, DeviceTokenCodec.sha256Hex("abc")));
        assertFalse(DeviceTokenCodec.digestEquals(a, DeviceTokenCodec.sha256Hex("abd")));
        assertFalse(DeviceTokenCodec.digestEquals(a, a.substring(0, 63)));
        assertFalse(DeviceTokenCodec.digestEquals(null, a));
        assertFalse(DeviceTokenCodec.digestEquals(a, null));
        assertFalse(DeviceTokenCodec.digestEquals(null, null));
    }
}
