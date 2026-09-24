package com.qiujie.service.support.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 短信频控与验证码生命周期判定单测（M2）：频控窗口/TTL/尝试上限边界。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SmsThrottlePolicyTest {

    @Test
    @DisplayName("发送间隔：从未发送不拦截；低于间隔拦截；恰好/超过间隔放行；间隔<=0 视为不限")
    void withinSendInterval() {
        assertFalse(SmsThrottlePolicy.withinSendInterval(null, 1000L, 60));
        assertTrue(SmsThrottlePolicy.withinSendInterval(970L, 1000L, 60));   // 差 30 < 60 → 拦截
        assertFalse(SmsThrottlePolicy.withinSendInterval(940L, 1000L, 60));  // 差 60 == 60 → 放行
        assertFalse(SmsThrottlePolicy.withinSendInterval(900L, 1000L, 60));  // 差 100 > 60 → 放行
        assertFalse(SmsThrottlePolicy.withinSendInterval(999L, 1000L, 0));   // 不限
        assertFalse(SmsThrottlePolicy.withinSendInterval(999L, 1000L, -1));  // 不限
    }

    @Test
    @DisplayName("日限：达到上限拦截，未达放行；上限<=0 视为不限")
    void dailyLimit() {
        assertFalse(SmsThrottlePolicy.dailyLimitExceeded(9, 10));
        assertTrue(SmsThrottlePolicy.dailyLimitExceeded(10, 10));
        assertTrue(SmsThrottlePolicy.dailyLimitExceeded(11, 10));
        assertFalse(SmsThrottlePolicy.dailyLimitExceeded(0, 0));
        assertFalse(SmsThrottlePolicy.dailyLimitExceeded(100, -1));
    }

    @Test
    @DisplayName("尝试上限：达到上限拦截；上限<=0 视为不限")
    void attemptsLimit() {
        assertFalse(SmsThrottlePolicy.attemptsExceeded(4, 5));
        assertTrue(SmsThrottlePolicy.attemptsExceeded(5, 5));
        assertTrue(SmsThrottlePolicy.attemptsExceeded(6, 5));
        assertFalse(SmsThrottlePolicy.attemptsExceeded(0, 0));
    }

    @Test
    @DisplayName("验证码 TTL：null 视为过期；恰好到期过期；未到期不过期；TTL<=0 立即过期")
    void codeExpiry() {
        assertTrue(SmsThrottlePolicy.expired(null, 1000L, 300));
        assertTrue(SmsThrottlePolicy.expired(700L, 1000L, 300));   // 差 300 == 300 → 过期
        assertFalse(SmsThrottlePolicy.expired(701L, 1000L, 300));  // 差 299 < 300 → 未过期
        assertTrue(SmsThrottlePolicy.expired(999L, 1000L, 0));     // 0 → 立即过期
        assertTrue(SmsThrottlePolicy.expired(999L, 1000L, -1));
    }
}
