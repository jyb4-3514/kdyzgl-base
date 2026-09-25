package com.qiujie.service.support.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试环境万能验证码判定单测：三条件（配置非空 / 非生产 / 提交值恒等）与各边界（空配置、生产、
 * 不相等、null/空串、大小写）。判定无副作用（不访问 Redis / 不读写计数）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SmsUniversalCodePolicyTest {

    @Test
    @DisplayName("配置为空/null/纯空白 → 一律不生效（完全按原有逻辑走）")
    void blankConfiguredNeverActive() {
        assertFalse(SmsUniversalCodePolicy.isActive("", false, "000000"));
        assertFalse(SmsUniversalCodePolicy.isActive(null, false, "000000"));
        assertFalse(SmsUniversalCodePolicy.isActive("   ", false, "000000"));
    }

    @Test
    @DisplayName("生产环境（production=true）→ 配置非空且 code 恒等也不生效")
    void productionNeverActive() {
        assertFalse(SmsUniversalCodePolicy.isActive("000000", true, "000000"));
        assertFalse(SmsUniversalCodePolicy.isActive("AbC123", true, "AbC123"));
    }

    @Test
    @DisplayName("命中：配置非空 + 非生产 + code 恒等 → 生效（前后空白容忍）")
    void activeWhenAllThreeMatch() {
        assertTrue(SmsUniversalCodePolicy.isActive("000000", false, "000000"));
        assertTrue(SmsUniversalCodePolicy.isActive("000000", false, " 000000 "));
        assertTrue(SmsUniversalCodePolicy.isActive(" 000000 ", false, "000000"));
    }

    @Test
    @DisplayName("不相等（含长度不同）→ 不生效")
    void mismatchNeverActive() {
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, "111111"));
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, "00000"));
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, "0000000"));
    }

    @Test
    @DisplayName("提交 code 为 null / 空串 / 纯空白 → 不生效且不抛 NPE")
    void nullOrBlankSubmittedNeverActive() {
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, null));
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, ""));
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, "   "));
    }

    @Test
    @DisplayName("区分大小写：恒定时间精确比较（AbC123 与 abc123 不等；0 与 O 不等）")
    void caseSensitive() {
        assertTrue(SmsUniversalCodePolicy.isActive("AbC123", false, "AbC123"));
        assertFalse(SmsUniversalCodePolicy.isActive("AbC123", false, "abc123"));
        assertFalse(SmsUniversalCodePolicy.isActive("000000", false, "00000O"));
    }
}
