package com.qiujie.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 手机号脱敏规则单测（决策 D5）。
 */
class DesensitizeUtilTest {

    @Test
    void mask11DigitPhone() {
        assertEquals("138****5678", DesensitizeUtil.maskPhone("13812345678"));
    }

    @Test
    void maskNon11DigitNumberShowsFirst3PlusStars() {
        // 非 11 位：前 3 位 + ****
        assertEquals("138****", DesensitizeUtil.maskPhone("1381234567"));
    }

    @Test
    void maskShorterThan3Digits() {
        assertEquals("13****", DesensitizeUtil.maskPhone("13"));
    }

    @Test
    void maskNullReturnsNull() {
        assertNull(DesensitizeUtil.maskPhone(null));
    }

    @Test
    void maskEmptyReturnsEmpty() {
        assertEquals("", DesensitizeUtil.maskPhone(""));
    }
}
