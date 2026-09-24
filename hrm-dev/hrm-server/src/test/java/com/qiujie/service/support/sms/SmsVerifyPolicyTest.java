package com.qiujie.service.support.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证码校验决策单测（M4，逐条对齐前端 Mock {@code verifyCode} 判定顺序）：
 * 常量时间比较、尝试上限、达上限即作废、空值/非法值。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SmsVerifyPolicyTest {

    @Test
    @DisplayName("比对通过 → OK（调用方随即删除验证码）")
    void passReturnsOk() {
        assertEquals(SmsVerifyPolicy.Decision.OK, SmsVerifyPolicy.verify("123456", "123456", 0, 5));
        // 提交值前后空白应被容忍（前端可能带空白）
        assertEquals(SmsVerifyPolicy.Decision.OK, SmsVerifyPolicy.verify("123456", " 123456 ", 0, 5));
    }

    @Test
    @DisplayName("比对失败 → INVALID；第 5 次失败时（attempts=4，本次第 5 次）即达上限 → ATTEMPTS_EXCEEDED")
    void mismatchAndAttemptsBoundary() {
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify("123456", "000000", 0, 5));
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify("123456", "000000", 3, 5));
        // attempts=4：本次为第 5 次 → 达上限
        assertEquals(SmsVerifyPolicy.Decision.ATTEMPTS_EXCEEDED, SmsVerifyPolicy.verify("123456", "000000", 4, 5));
    }

    @Test
    @DisplayName("已达上限（attempts>=max）→ 直接 ATTEMPTS_EXCEEDED，不再比对")
    void alreadyExceeded() {
        assertEquals(SmsVerifyPolicy.Decision.ATTEMPTS_EXCEEDED, SmsVerifyPolicy.verify("123456", "123456", 5, 5));
        assertEquals(SmsVerifyPolicy.Decision.ATTEMPTS_EXCEEDED, SmsVerifyPolicy.verify("123456", "000000", 6, 5));
    }

    @Test
    @DisplayName("上限 <=0 视为不限：永不因次数拦截，但比对失败仍为 INVALID")
    void unlimitedAttempts() {
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify("123456", "000000", 99, 0));
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify("123456", "000000", 99, -1));
    }

    @Test
    @DisplayName("空值/非法值：期望码 null 或提交 null → INVALID（不抛错）")
    void nullInputs() {
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify(null, "123456", 0, 5));
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify("123456", null, 0, 5));
        assertEquals(SmsVerifyPolicy.Decision.INVALID, SmsVerifyPolicy.verify(null, null, 0, 5));
    }
}
