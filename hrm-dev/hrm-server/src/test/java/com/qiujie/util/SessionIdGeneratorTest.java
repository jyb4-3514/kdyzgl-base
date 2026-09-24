package com.qiujie.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话标识生成单测（M1）：格式、高熵性、可枚举性防护。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SessionIdGeneratorTest {

    @Test
    @DisplayName("格式：64 位小写十六进制，且通过 isValid")
    void format() {
        String sid = SessionIdGenerator.newSid();
        assertEquals(SessionIdGenerator.SID_LENGTH, sid.length());
        assertTrue(sid.matches("[0-9a-f]{64}"), "实际=" + sid);
        assertTrue(SessionIdGenerator.isValid(sid));
    }

    @Test
    @DisplayName("高熵：连续 2000 次生成无重复（256 bit 空间碰撞概率可忽略）")
    void highEntropyNoCollision() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            assertTrue(seen.add(SessionIdGenerator.newSid()), "出现重复 sid，熵不足");
        }
    }

    @Test
    @DisplayName("不承载可推导信息：连续生成值之间无递增/时间戳前缀关系")
    void noPredictableSequence() {
        String a = SessionIdGenerator.newSid();
        String b = SessionIdGenerator.newSid();
        // 不相等即说明未使用可预测序列（如自增或时间戳计数）；前 8 位相同概率约 1/2^32，正常不应发生
        assertFalse(a.equals(b));
        assertFalse(a.substring(0, 8).equals(b.substring(0, 8)), "前 8 位相同，疑似时间戳/序列前缀");
    }

    @Test
    @DisplayName("isValid 边界：null / 空串 / 长度不符 / 非十六进制字符 / 大写一律判非法")
    void isValidBoundaries() {
        assertFalse(SessionIdGenerator.isValid(null));
        assertFalse(SessionIdGenerator.isValid(""));
        assertFalse(SessionIdGenerator.isValid("0123456789abcdef"));                       // 长度不足
        assertFalse(SessionIdGenerator.isValid("0".repeat(63)));                          // 差 1 位
        assertFalse(SessionIdGenerator.isValid("g".repeat(64)));                          // 非十六进制
        assertFalse(SessionIdGenerator.isValid("A".repeat(64)));                          // 大写不合法（统一小写口径）
        // 历史 token 的 UUID jti（36 字符）应判非法 → 自然落入旧 token 回退分支
        assertFalse(SessionIdGenerator.isValid("550e8400-e29b-41d4-a716-446655440000"));
        assertTrue(SessionIdGenerator.isValid("0".repeat(64)));
        assertTrue(SessionIdGenerator.isValid("f".repeat(64)));
    }

    @Test
    @DisplayName("toHex：字节到十六进制逐位正确（含 0x00/0xFF 边界）")
    void toHexBoundary() {
        assertEquals("00", SessionIdGenerator.toHex(new byte[]{(byte) 0x00}));
        assertEquals("ff", SessionIdGenerator.toHex(new byte[]{(byte) 0xFF}));
        assertEquals("0a1b", SessionIdGenerator.toHex(new byte[]{0x0A, 0x1B}));
        assertEquals("", SessionIdGenerator.toHex(new byte[0]));
    }
}
