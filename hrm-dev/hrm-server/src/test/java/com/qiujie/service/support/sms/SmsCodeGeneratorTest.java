package com.qiujie.service.support.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码生成与恒定时间比较单测（M2）：长度/字符集/熵/钳制边界、比较全分支。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SmsCodeGeneratorTest {

    @Test
    @DisplayName("generate：定长纯数字（含前导 0 允许）")
    void generateFormat() {
        for (int length : new int[]{4, 5, 6, 7, 8}) {
            String code = SmsCodeGenerator.generate(length);
            assertEquals(length, code.length());
            assertTrue(code.matches("\\d{" + length + "}"), "实际=" + code);
        }
    }

    @Test
    @DisplayName("generate 长度钳制：<4 → 4，>8 → 8（配置越界时的第二道防线）")
    void generateClampsLength() {
        assertEquals(SmsCodeGenerator.MIN_LENGTH, SmsCodeGenerator.generate(0).length());
        assertEquals(SmsCodeGenerator.MIN_LENGTH, SmsCodeGenerator.generate(1).length());
        assertEquals(SmsCodeGenerator.MAX_LENGTH, SmsCodeGenerator.generate(100).length());
        assertEquals(SmsCodeGenerator.MAX_LENGTH, SmsCodeGenerator.generate(Integer.MAX_VALUE).length());
    }

    @Test
    @DisplayName("熵：2000 次 6 位验证码几乎不重复（非固定值/非常量）")
    void generateHighEntropy() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(SmsCodeGenerator.generate(6));
        }
        // 6 位空间 10^6，2000 次抽取期望碰撞约 2 次；下线取 1980 留足余量
        assertTrue(seen.size() >= 1980, "重复过多，疑似低熵实现，distinct=" + seen.size());
    }

    @Test
    @DisplayName("constantTimeEquals：相等/不等/长度不同/null/空串全分支")
    void constantTimeEqualsBranches() {
        assertTrue(SmsCodeGenerator.constantTimeEquals("123456", "123456"));
        assertFalse(SmsCodeGenerator.constantTimeEquals("123456", "123457"));
        assertFalse(SmsCodeGenerator.constantTimeEquals("123456", "12345"));
        assertFalse(SmsCodeGenerator.constantTimeEquals("123456", "1234567"));
        assertFalse(SmsCodeGenerator.constantTimeEquals(null, "123456"));
        assertFalse(SmsCodeGenerator.constantTimeEquals("123456", null));
        assertFalse(SmsCodeGenerator.constantTimeEquals(null, null));
        assertTrue(SmsCodeGenerator.constantTimeEquals("", ""));
        assertFalse(SmsCodeGenerator.constantTimeEquals("", "1"));
    }
}
