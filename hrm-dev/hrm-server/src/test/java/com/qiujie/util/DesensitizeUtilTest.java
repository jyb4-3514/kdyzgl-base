package com.qiujie.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 脱敏规则单测（决策 D5 / C-07，口径对齐 Mock shared/domain/mask.js）。
 * 注意：本机无 JDK/Maven，无法执行；收敛到服务器阶段运行。
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

    // ---------- C-07 姓名脱敏 ----------

    @Test
    void maskNameKeepsFirstCharOnly() {
        assertEquals("张*", DesensitizeUtil.maskName("张三"));
        assertEquals("李**", DesensitizeUtil.maskName("李小明"));
    }

    @Test
    void maskNameSingleCharOrBlankReturnsAsIs() {
        assertEquals("王", DesensitizeUtil.maskName("王"));
        assertNull(DesensitizeUtil.maskName(null));
        assertEquals("", DesensitizeUtil.maskName(""));
    }

    // ---------- C-07 银行卡脱敏（保留末 4 位，其余按 4 位一组 *） ----------

    @Test
    void maskBankAccountKeepsLast4Grouped() {
        // 16 位 → 12 个 * 分 3 组 + 空格 + 末 4 位
        assertEquals("**** **** **** 7890", DesensitizeUtil.maskBankAccount("6222021234567890"));
        // 含空格的输入先剔除空白再计算位数
        assertEquals("**** **** **** 7890", DesensitizeUtil.maskBankAccount("6222 0212 3456 7890"));
    }

    @Test
    void maskBankAccountEdgeCases() {
        // 恰好 5 位：1 个 * + 空格 + 末 4 位
        assertEquals("* 2345", DesensitizeUtil.maskBankAccount("12345"));
        // ≤4 位：全部以 * 占位，不暴露任何真实位
        assertEquals("****", DesensitizeUtil.maskBankAccount("1234"));
        assertEquals("***", DesensitizeUtil.maskBankAccount("123"));
    }

    @Test
    void maskBankAccountNullAndEmpty() {
        assertNull(DesensitizeUtil.maskBankAccount(null));
        assertEquals("", DesensitizeUtil.maskBankAccount(""));
    }
}
